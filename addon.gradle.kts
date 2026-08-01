import java.net.URI
import java.util.zip.ZipFile
import org.gradle.api.tasks.bundling.Jar

// Mekanism gas API 存根（src/main/java/mekanism/api/gas/）是 compileOnly 性质：仅供编译期类型解析，
// 绝不能打进发布 jar。否则运行时这些精简存根类会排在真 Mekanism 之前被类加载器选中，遮蔽真 API
// （例如存根 Gas 缺少 registerFluid()），导致 Mekanism preInit 抛 NoSuchMethodError 崩溃。
// 运行时 API 类一律由真正的 Mekanism 模组提供。
// 同理排除 Mekanism 能量 API 存根（mekanism/api/energy/、mekanism/api/MekanismConfig）
// 与 CoFH RF API 存根（cofh/api/energy/）：运行时由真实来源提供
// （Mekanism 9.x 内嵌了 cofh.api.energy.*）。
tasks.withType<Jar>().configureEach {
    exclude("mekanism/**")
    exclude("cofh/**")
}


// Fix LWJGL2 OpenAL crash on Windows 11 25H2:
// Use OpenAL Soft (drop-in replacement) + Adoptium Temurin JDK 8
val openAlSoftVersion = "1.24.2"
val openAlSoftZip = layout.buildDirectory.file("openal-soft/openal-soft-$openAlSoftVersion-bin.zip")
val nativesDir = file("run/natives/lwjgl2")

val downloadOpenAlSoft by tasks.registering {
    // doLast 动作闭包内只引用配置期捕获的可序列化局部变量（File/String），
    // 不直接引用顶层脚本属性，否则会捕获脚本对象导致 configuration cache 序列化失败。
    val zipFile = openAlSoftZip.get().asFile
    val url = "https://github.com/kcat/openal-soft/releases/download/$openAlSoftVersion/openal-soft-$openAlSoftVersion-bin.zip"
    outputs.file(zipFile)
    onlyIf { !zipFile.exists() }
    doLast {
        zipFile.parentFile.mkdirs()
        logger.lifecycle("Downloading OpenAL Soft from $url ...")
        URI(url).toURL().openStream().use { input ->
            zipFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        logger.lifecycle("Downloaded OpenAL Soft to ${zipFile.absolutePath}")
    }
}

val installOpenAlSoft by tasks.registering {
    dependsOn(downloadOpenAlSoft)
    // 同理：nativesDir 是顶层脚本属性，doLast 内直接引用会捕获脚本对象 → 配置缓存不可序列化，
    // 故在配置期拷贝为局部 File 变量 natives。
    val zipFile = openAlSoftZip.get().asFile
    val natives = nativesDir
    val targetDll = File(natives, "OpenAL64.dll")
    outputs.file(targetDll)
    // Re-install if the DLL is missing or is the old broken LWJGL one (382464 bytes)
    onlyIf {
        !targetDll.exists() || targetDll.length() == 382464L
    }
    doLast {
        natives.mkdirs()
        ZipFile(zipFile).use { zip ->
            val entry = zip.entries().asSequence().firstOrNull {
                it.name.replace('\\', '/').let { n ->
                    n.contains("bin/Win64/soft_oal.dll", ignoreCase = true)
                }
            } ?: throw GradleException("soft_oal.dll not found in OpenAL Soft zip!")

            zip.getInputStream(entry).use { input ->
                targetDll.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        logger.lifecycle("Installed OpenAL Soft as ${targetDll.absolutePath} (${targetDll.length()} bytes)")

        // Remove 32-bit DLL to avoid conflicts (we run 64-bit Java)
        val dll32 = File(natives, "OpenAL32.dll")
        if (dll32.exists()) {
            dll32.delete()
            logger.lifecycle("Removed OpenAL32.dll (not needed for 64-bit JVM)")
        }
    }
}

// Use Eclipse Adoptium (Temurin) JDK 8 for runClient - Azul Zulu has LWJGL2 native binding issues
val adoptiumJdk8 = file("C:/Program Files/Eclipse Adoptium/jdk-8.0.472.8-hotspot")
val toolchainService = extensions.getByType<JavaToolchainService>()

// Hook into runClient tasks
tasks.matching { it.name.startsWith("runClient") }.configureEach {
    dependsOn(installOpenAlSoft)
    if (this is JavaExec) {
        if (adoptiumJdk8.exists()) {
            javaLauncher.set(
                toolchainService.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(8))
                    vendor.set(JvmVendorSpec.ADOPTIUM)
                }
            )
            logger.lifecycle("runClient using JDK: Adoptium Temurin 8")
        }
    }
}
