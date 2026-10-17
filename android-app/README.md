# 拼豆图纸 · v0.7.1

Kotlin / Compose，包名 `dev.pindou.pattern`，versionCode 8，最低 Android 12（API 31）。

## 安装与使用

应用需要在本机通过 Android Studio 或 Gradle 构建。仓库不提交 APK；构建完成后可在 `app/build/outputs/apk/` 找到本机生成的安装包。

- **已有图纸**：导入图片 → 自动分析行列与对齐 → 同屏人工确认 → 框选并核对底部图例 → 按原网格逐格识别 → 校对待确认格 → 底部「导出 PNG 图纸」→ 系统窗口选择目录并保存。
- **保存工程**仅保存可继续编辑的草稿，不会产生 PNG；导出无需先保存工程。白豆和空格必须分清，待确认未清零不能导出。
- **普通图片转换**：默认 64×64；8–128 独立行列，照片/插画模式、完整补白或裁剪；全部 291 个 MARD 色号参与匹配，无增强开关、无强制黑色轮廓。
- 原图、预览、计数和 PNG 以不可变 Pattern 为依据。图纸导出含格内色号、坐标和用量图例，空格不印色号、不计豆数。

详细操作：[导出修复与交付](../docs/mcp-export-fix-v071.md)；[自动网格](../docs/mcp-auto-grid-v071.md)；[当前架构与色卡](../docs/mcp-current-architecture-v071.md)。

## 数据、权限与边界

MARD 291 色为第三方屏显参考，不是官方实测。Q4/R11 相同 RGB，导入已有图纸时以人工确认的源图图例及色号为准，不把最近颜色匹配当正确保证。

自动网格为本地几何视觉分析，不是训练模型或云 AI；支持正视、轴对齐图纸。白底区域可能是白豆或空格，需要核对。

支持本地图片和公开网页候选图片提取；受保护的抖音/小红书链接不保证成功。使用 Photo Picker 和 SAF，包含 INTERNET / ACCESS_NETWORK_STATE 权限。ML Kit 在设备上处理文字，但 SDK 可能联网发送性能/使用指标，需启用前告知。不开发灯板、蓝牙、计时和完成进度。

## 本机构建

首次使用时，让 Android Studio 生成 `local.properties`，或准备好本机 Android SDK。`local.properties` 只包含本机路径，已被 Git 忽略。

双击 `build-local.bat`，或在本目录执行：

```bat
gradlew.bat testDebugUnitTest lintDebug
```

项目使用 Java 17 字节码目标；Gradle 9.4.1、AGP 9.2.1、Kotlin/Compose 2.2.10。默认 compile/target36、min31。如果本机只有 Android 37 SDK，可执行 `gradlew.bat "-Pbead.compileSdk=37" testDebugUnitTest lintDebug`。

构建中间产物由 Gradle 写入本机缓存或 `build/`，不应提交。不要把签名文件、SDK 路径或其他本机配置加入仓库。

stage 任务会在本机重新生成 `artifacts/` 下的暂存 APK 和报告；这些文件已被 Git 忽略，不应提交到仓库。

## 当前验证记录

- 105 项 JVM 测试通过；Lint 0 错误 / 12 警告。
- 测试输入：`test-inputs/original-photo.jpeg`；真实 51×52 图纸：`app/src/test/resources/charts/mard-51x52.jpeg`，只用于测试。
- 设备测试源码保留，可用 `connectedDebugAndroidTest` 在连接设备后重新编译运行。

没有连接手机，Android UI、系统文件保存、OCR、手势、内存和性能仍需实机验证。桌面测试不是手机验收。

历史构建产物和清理记录不纳入仓库；需要验证时重新运行测试和 lint。
