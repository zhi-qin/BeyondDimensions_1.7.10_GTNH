# Beyond Dimensions

> A mod provided some utilities about cross-dimensional storage network.

Minecraft 1.7.10 Forge 模组，建立跨维度统一存储网络。将分散在不同维度的物品、流体、能量通过网络接口、熔炉、漏斗、泵等机器远程访问与管理。

## 功能概览

- **维度网络（DimensionsNet）**：跨维度统一存储，物品/流体/能量/Mana/Gas 混合管理
- **网络机器**：网络接口（双向传输）、网络泵（抽取）、网络漏斗（收集）、网络熔炉/高炉/烟熏炉
- **便携设备**：网络磁铁、喂食器、补货器、便携终端
- **联动集成**：NEI（幽灵物品）、AE2（ME 存储元件）、Botania（Mana）、Mekanism（Gas）

## 构建

基于 [GTNH ExampleMod](https://github.com/GTNewHorizons/ExampleMod1.7.10) 模板，使用分层 Gradle 构建。

```powershell
# 编译
./gradlew build

# 启动客户端测试
./gradlew runClient

# 含联动模组（NEI/AE2/Botania/GTNHLib）的完整测试
./gradlew runClient --init-script=init-runtime.gradle

# 代码格式化
./gradlew spotlessApply
```

**环境要求**：JDK 25（Jabel 编译为 Java 8 字节码）、Minecraft 1.7.10、Forge 10.13.4.1614。

## 项目结构

```
src/main/java/com/wintercogs/beyonddimensions/
├── api/            # 核心 API（StackKey/StackHandler/DimensionsNet）
├── common/         # 方块(14) + TileEntity(13) + 物品(17) + Container + 机器逻辑
├── client/         # GUI(17) + TESR 渲染器 + 快捷键
├── network/        # 18 个网络包
├── integration/    # 联动模块（NEI/AE2/Botania/Mekanism）
├── config/         # 运行时配置
└── util/           # 工具类
```

## 技术要点

- 1.20.1 → 1.7.10 跨版本移植（Capability → instanceof、BlockState → metadata、DeferredRegister → GameRegistry）
- 现代 Java 语法（switch 表达式、var、record）通过 Jabel 编译为 Java 8 字节码
- Spotless 代码格式化、GTNH Maven 依赖管理

## 致谢与出处

本模组是 **Beyond Dimensions** 的 1.7.10 GTNH 移植版。

- **原作者 / Original author：** [Frostbite-time](https://github.com/Frostbite-time)
- **原项目 / Original project：** https://github.com/Frostbite-time/BeyondDimensions （1.20.1，MIT License，Copyright (c) 2025 Frostbite-time）
- **1.7.10 GTNH 移植：** zhi-qin
- **构建基建：** 基于 [GTNH ExampleMod](https://github.com/GTNewHorizons/ExampleMod1.7.10) 模板（MIT License，Copyright (c) 2021 Johann Bernhardt）




## 许可

见 [LICENSE](LICENSE)（MIT License）。

第三方组件声明（含打包进 jar 的 TinyPinyin，Apache-2.0）见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。



## 💬 补充说明

本模组仅为个人学习与 GTNH 整合包自用而
