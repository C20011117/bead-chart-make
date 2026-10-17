# 智能拼豆

Android 拼豆图纸应用，使用 Kotlin 和 Jetpack Compose，在本地完成图片转拼豆图纸、已有图纸导入、网格校准、色号校对和 PNG 导出。

## 项目结构

- `android-app/`：Android Studio / Gradle 工程、源码和测试。
- `docs/`：当前架构、算法约束和导入流程说明。
- `docs/archive/`：历史方案文档，仅用于追溯。

## 开始构建

1. 使用 Android Studio 打开 `android-app/`。
2. 让 Android Studio 在本机生成 `android-app/local.properties`，或设置本机 Android SDK 路径。该文件不会提交到 Git。
3. 在 `android-app/` 目录执行：

```bat
gradlew.bat testDebugUnitTest lintDebug
```

构建产物和报告只保留在本机的 `build/`、`artifacts/` 目录中，不纳入仓库。详细说明见 [Android 项目说明](android-app/README.md)。

## 当前范围

当前版本只聚焦 Android 本地图纸处理，不包含灯板、蓝牙、账号和云端同步功能。实际行为以源码、测试和 [当前架构说明](docs/mcp-current-architecture-v071.md) 为准。
