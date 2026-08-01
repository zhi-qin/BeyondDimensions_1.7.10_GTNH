# Third-Party Notices

本模组（Beyond Dimensions 1.7.10 GTNH 移植版）包含或引用了以下第三方开源软件。
本文件列举了各组件及其适用许可；对应许可全文见 `licenses/` 目录。

This project includes third-party open-source software. The full text of the
applicable licenses is included in the `licenses/` directory.

## 打包进发布 jar（Bundled in the built artifact）

### TinyPinyin

- **Name:** TinyPinyin
- **Version:** 2.0.3.RELEASE
- **Repository:** https://github.com/promeG/TinyPinyin
- **License:** Apache License 2.0
- **Usage in this project:** 通过 shadow 插件重新打包进模组 jar（包名已重定位）。

The full text of the applicable license is included in:

- `licenses/Apache-2.0.txt`

## 编译期存根（Compile-time stubs，不随 jar 分发 / excluded from the jar）

以下 API 存根仅用于在缺少对应模组依赖时编译通过（`addon.gradle.kts` 已对
`mekanism/**`、`cofh/**` 执行 jar exclude，运行时由真实模组提供同名类）：

### Mekanism API

- **Original author:** aidancbrady / Mekanism
- **License:** MIT License (Mekanism 1.7.10 API)
- **Location:** `src/main/java/mekanism/api/`（仅保留公共方法签名的存根）

### CoFH RF API

- **Original author:** CoFH team
- **License:** CoFH RF API 许可（编译时存根，仅保留公共接口签名）
- **Location:** `src/main/java/cofh/api/energy/`

## 仓库内参考源码（Reference source in repo，未编译、未随 jar 分发）

### Applied Energistics 2

- **Original author:** AlgorithmX2
- **License:** MIT License (API files) / GNU Lesser General Public License v3 (implementation files)
- **Location:** `appeng/`（本地参考/排查用副本，不在构建源集内，不进入发布产物）
