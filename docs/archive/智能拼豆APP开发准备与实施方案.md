# 智能拼豆 APP 开发准备与实施方案

> **已弃用（2026-09-26）**：本方案包含灯板通信等已取消的功能。当前开发以[图片转拼豆图纸 APP 架构](图片转拼豆图纸APP架构.md)为准。

> 文档日期：2026-09-02  
> 项目目录：`D:\Codex\WorkSpace\智能拼豆`  
> 技术路线：Android 原生、Kotlin、Jetpack Compose

## 1. 结论与第一阶段目标

当前产品方向正确，但开发前还需要补齐以下关键内容：

- 固定 Android、JDK、SDK 和 Gradle 的版本基线。
- 明确拼豆颜色库的品牌、版本、来源和校准方式。
- 把颜色算法确定为正确的 sRGB → XYZ → Lab D65 → CIEDE2000 流程。
- 明确“最多 K 种颜色”的优化方法，不能简单删除低频颜色。
- 为大图片解码、Compose 网格绘制和导出设置内存与性能策略。
- 提前定义图纸工程文件格式、版本号和灯板通信协议。
- 选图、保存、导出和局域网通信采用最小权限策略。

第一个正式里程碑为：

> 在 Android 真机上导入固定测试图，按目标网格比例裁剪，生成 29×29、最多 K 色的 CIEDE2000 拼豆矩阵；图纸可以显示、缩放、编辑，颜色统计总数严格等于 841；此阶段不连接实体灯板。

## 2. 产品范围

### 2.1 图纸生成模块

完整流程：

```text
选择图片
→ 修正方向和透明背景
→ 按网格宽高比裁剪、平移和缩放
→ 分区采样生成原始颜色网格
→ 转换到 Lab 色彩空间
→ 从指定拼豆颜色库选择最多 K 种颜色
→ 生成颜色编号矩阵
→ 手动改色
→ 统计数量
→ 保存和导出
```

第一版功能：

- 图片选择、裁剪、旋转和缩放。
- 自定义网格行列数。
- 限制拼豆颜色数量。
- 显示颜色编号、名称和色块。
- 点击网格单元手动改色。
- 撤销和重做。
- 统计每种颜色需要的颗数。
- 保存工程并重新打开。
- 导出 PNG、PDF 和 UTF-8 CSV。
- 全程离线可用。

### 2.2 灯板控制模块

完整流程：

```text
图纸颜色编号矩阵
→ 转换为 RGB24 灯板帧
→ Android 发送
→ ESP32 接收和校验
→ LED 点阵显示
```

第一阶段只实现模拟发送。真实硬件联调放在图纸闭环稳定之后。

图纸模块与灯板模块的共享契约是颜色矩阵，但两边存储的数据应有所区别：

- 图纸工程保存拼豆颜色编号，便于统计、编辑和更换显示方式。
- 发给灯板时转换成 RGB24，避免 ESP32 与 APP 的拼豆色库版本不一致。

## 3. 专家审查后的主要改进

### 3.1 不需要安装全局 Gradle

Android Studio 创建工程时会生成 Gradle Wrapper：

```text
gradlew
gradlew.bat
gradle/wrapper/
```

以后使用项目自己的 `gradlew.bat` 构建。这样每台电脑使用同一 Gradle 版本，不需要额外安装全局 Gradle。

### 3.2 不覆盖现有 JDK 8

本机已有其他用途的 JDK 8。不要直接用 `setx JAVA_HOME` 把它永久覆盖。

Android 工程应优先使用：

- Android Studio 内置 JBR；或
- Android Studio 的 `GRADLE_LOCAL_JAVA_HOME`。

当前 Android Gradle Plugin 9.0 仍使用 JDK 17。若从 PowerShell 构建，可以只对当前终端临时设置 Android Studio 的 JBR。

### 3.3 固定初始 Android 基线

建议第一版使用：

```text
minSdk = 26
targetSdk = 36
compileSdk = 当前稳定工具链支持的最新版本，可安装 36 和 37
Java toolchain = 17
```

原因：

- `minSdk 26` 覆盖 Android 8 及以上设备，同时降低旧版本兼容复杂度。
- 从 2026-08-31 起，提交到 Google Play 的手机新应用和更新需要以 API 36 或更高版本为目标。
- 若以后直接把 `targetSdk` 提升到 37，连接 ESP32 局域网时需要处理 Android 17 的 `ACCESS_LOCAL_NETWORK` 运行时权限。

具体版本仍应在创建工程当天以 Android Studio 最新稳定模板为准，不手工拼接未经验证的插件版本。

### 3.4 先使用单一 `app` 模块

第一版不要提前搭建复杂多模块 Clean Architecture。先建立一个 `app` Gradle 模块，并通过 Kotlin 包进行职责分层。只有在代码确实需要跨模块复用或构建明显变慢后再拆分。

### 3.5 裁剪必须锁定网格宽高比

如果目标网格是 29×29，裁剪框应保持 1:1；如果目标网格是 32×48，裁剪框应保持 2:3。

不能先任意裁剪，再把结果强行拉伸到网格尺寸，否则人物和图案会变形。

### 3.6 颜色数量限制需要优化算法

“最多 K 色”不能通过删除低频颜色完成。正确目标是：

> 在当前拼豆品牌的离散颜色库中，选择最多 K 个颜色，使全部网格单元的总 CIEDE2000 色差尽可能小。

第一版可使用确定性的贪心前向选择，并在最后增加一次候选交换优化。

### 3.7 项目文件必须有版本

工程 JSON 从第一版开始就应保存：

```text
schemaVersion
appVersion
paletteId
paletteRevision
```

这样颜色库或文件格式升级后，旧图纸仍有迁移依据。

## 4. 当前电脑与项目环境核验

核验日期：2026-09-02。

### 4.1 电脑条件

```text
操作系统：Windows 10 家庭中文版 64 位
系统版本：10.0.19045
处理器：Intel Core i5-10300H
逻辑处理器：8
内存：约 23.8 GB
固件虚拟化：已开启
C 盘可用空间：约 23.4 GB
D 盘可用空间：约 63.2 GB
```

结论：电脑性能足以运行 Android Studio 和一个 Android 模拟器。

由于 C 盘空间较少，建议将 Android SDK 和模拟器数据优先放到 D 盘。

### 4.2 当前工具状态

```text
项目内容：只有 .codex 专家目录入口，尚无 Android 工程
Java：D:\software\jdk8\bin\java.exe
javac：D:\software\jdk8\bin\javac.exe
Android Studio：未检测到
Android SDK：未检测到
ADB：未检测到
全局 Gradle：未检测到，但不需要单独安装
```

当前不能创建并验证现代 Compose 工程。下一步是安装 Android Studio、SDK 和 JDK 17 工具链。

## 5. 开发前需要准备的材料

### 5.1 必须的软件

- Android Studio 最新稳定版。
- Android SDK Platform 36。
- Android SDK Platform 37，用于后续兼容测试。
- Android SDK Build-Tools 当前稳定版。
- Android SDK Platform-Tools，包含 ADB。
- Android SDK Command-line Tools（latest）。
- Android Emulator。
- API 36 Google APIs x86_64 模拟器镜像。
- Android Studio 内置 JBR/JDK 17。
- Git。
- Android 实体手机，建议 Android 8 或更高。
- 支持数据传输的 USB 数据线。

暂时不需要安装：

- NDK。
- CMake。
- ESP-IDF。
- PlatformIO。
- Arduino IDE。
- 全局 Gradle。

ESP32 固件开发工具等真正进入灯板阶段再安装。

### 5.2 必须的测试图片

至少准备以下测试集：

- 红、绿、蓝、黑、白、灰纯色图片。
- 人工可验证的 2×2 或 4×4 已知色块图。
- 从黑到白的明暗渐变图。
- 高饱和度图片。
- 低饱和度图片。
- 包含肤色的照片。
- 天空、植物等自然照片。
- 卡通或像素画图片。
- 透明 PNG。
- 带 EXIF 旋转信息的手机照片。
- 横长图、竖长图、正方形图。
- 6000×4000 或更大的高分辨率图片。
- JPEG、PNG、WebP。
- HEIF、AVIF 作为兼容性扩展测试。

选出一组固定“金样图”。以后每次修改颜色算法，都用同一组图片生成结果并对比，防止准确度倒退。

### 5.3 拼豆颜色数据

每套颜色库至少包含：

```text
schemaVersion
paletteId
brand
paletteName
revision
source
code
nameZh
nameEn
hex
sRgbR
sRgbG
sRgbB
labL
labA
labB
available
notes
```

还需要准备：

- 明确品牌和版本的官方色卡或可靠数据表。
- 对应品牌的实体拼豆样品。
- 中性灰背景。
- 固定光源，最好接近 D65。
- 固定相机白平衡。
- 如果追求颜色准确度，准备色度计。
- CIEDE2000 标准测试对，用来验证算法实现。
- 中文名称、英文名称和编号的唯一性检查表。

需要特别说明：网络上的 Hex 值不一定等于实体拼豆在真实光照下的颜色。最终准确度取决于可信厂商数据或实物测色。

### 5.4 后续可选灯板硬件

图纸开发阶段不需要购买硬件。进入真实联调时，建议从 8×8 原型开始：

- ESP32 开发板；或者带电平转换、保险和接线端子的成品 ESP32/WLED 控制器。
- 8×8 WS2812B 5V LED 点阵，优先购买带 JST-SM 接头的成品。
- 5V 5A 稳压电源。
- 匹配的 DC、JST 或螺丝端子转接线。
- 330Ω 左右的数据线串联电阻。
- 约 1000µF 电解电容。
- 74AHCT125 或 74HCT245 电平转换器；若成品控制器已包含则不必另购。
- 带保险的电源分配模块。
- 万用表。
- 机械固定件、扩散层和拼豆孔位板。

购买前核对：

- LED 点阵实际工作电压。
- 像素数量、行列方向和蛇形走线方式。
- 控制器是否提供可靠的 5V 数据电平。
- 所有接头公母是否匹配。
- 电源额定电流和线材承载能力。
- 是否需要多点供电。
- LED 像素间距是否与拼豆孔距一致。

按每颗 RGB LED 峰值约 60mA 粗略估算：

```text
8×8：64 颗，约 3.84A
16×16：256 颗，约 15.36A
29×29：841 颗，约 50.46A
```

因此第一块实体原型建议使用 8×8。16×16 需要限制最大亮度并认真设计供电；29×29 不适合作为第一块原型。

## 6. Windows 环境安装步骤

### 6.1 安装 Android Studio

1. 从 Android 官方页面下载最新稳定版。
2. 运行 Windows 安装程序。
3. 程序本体可以保留默认安装位置。
4. 首次启动选择 `Standard` 配置。
5. 在 SDK 设置中将 Android SDK 路径改为纯英文目录：

```text
D:\Android\Sdk
```

6. 不卸载现有 JDK 8，也暂时不修改全局 `JAVA_HOME`。

官方安装文档：<https://developer.android.com/studio/install>

### 6.2 安装 SDK 组件

打开：

```text
Android Studio
→ Tools
→ SDK Manager
```

安装：

- Android SDK Platform 36。
- Android SDK Platform 37。
- 最新稳定 Android SDK Build-Tools。
- Android SDK Platform-Tools。
- Android SDK Command-line Tools（latest）。
- Android Emulator。
- API 36 Google APIs x86_64 系统镜像。
- Google USB Driver，可选但建议安装。

第一阶段不要安装 NDK 和 CMake。

### 6.3 配置 Gradle JDK

打开：

```text
File
→ Settings
→ Build, Execution, Deployment
→ Build Tools
→ Gradle
```

Gradle JDK 选择：

```text
GRADLE_LOCAL_JAVA_HOME
```

或者选择 Android Studio 自带的 JBR 17。

不要选择当前的 `D:\software\jdk8`。

官方 JDK 配置说明：<https://developer.android.com/build/jdks>

### 6.4 配置命令行工具

可建立用户环境变量：

```text
ANDROID_HOME=D:\Android\Sdk
```

在用户 PATH 中加入：

```text
%ANDROID_HOME%\platform-tools
%ANDROID_HOME%\cmdline-tools\latest\bin
```

若需要从 PowerShell 构建，可以只在当前终端临时切换 JDK：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

这不会永久覆盖现有 JDK 8。

### 6.5 配置 Android 模拟器

1. 打开 Windows“启用或关闭 Windows 功能”。
2. 启用 `Windows Hypervisor Platform`。
3. 重启电脑。
4. 在 Android Studio 的 Device Manager 中创建普通 Pixel 手机。
5. 使用 API 36、Google APIs、x86_64 镜像。
6. 模拟器内存设置为 2–4 GB。

验证硬件加速：

```powershell
& 'D:\Android\Sdk\emulator\emulator.exe' -accel-check
```

应看到 WHPX 已安装且可用。

官方目前建议 Windows 使用 WHPX；Android Emulator hypervisor driver 将在 2026 年末停止支持：

<https://developer.android.com/studio/run/emulator-acceleration>

### 6.6 配置实体手机

1. 在手机设置中连续点击版本号，开启开发者选项。
2. 开启 USB 调试。
3. 使用支持数据传输的 USB 线连接电脑。
4. 接受手机弹出的调试指纹授权。
5. 如果不能识别，安装手机厂商 ADB 驱动。

验证：

```powershell
adb version
adb devices
```

正确设备状态应为：

```text
device
```

如果显示 `unauthorized`，需要在手机上重新授权。

官方真机运行指南：<https://developer.android.com/studio/run/device>

## 7. 创建 Android 工程

建议工程位置：

```text
D:\Codex\WorkSpace\智能拼豆\android-app
```

创建参数：

```text
Name: SmartBead
Template: Empty Activity
Language: Kotlin
UI: Jetpack Compose
Minimum SDK: API 26
Build configuration: Kotlin DSL
```

包名在发布前必须固定，不建议最终使用 `com.example.*`。开发初期可以暂定一个反向域名式包名，正式发布前统一确认。

创建后在 PowerShell 验证：

```powershell
cd 'D:\Codex\WorkSpace\智能拼豆\android-app'
.\gradlew.bat --version
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

环境阶段验收标准：

- Gradle Sync 成功。
- `gradlew.bat --version` 显示使用 JDK 17。
- 单元测试成功。
- Lint 没有阻断错误。
- 成功生成 `app-debug.apk`。
- 首页可以在模拟器和实体手机打开。

## 8. 推荐工程结构

第一版只使用一个 `app` 模块：

```text
app/src/main/java/.../
├─ model/
│  ├─ BeadColor.kt
│  ├─ Palette.kt
│  ├─ PatternProject.kt
│  └─ CropSpec.kt
├─ color/
│  ├─ SrgbLabConverter.kt
│  ├─ DeltaE2000.kt
│  ├─ GridSampler.kt
│  └─ PaletteReducer.kt
├─ data/
│  ├─ PaletteRepository.kt
│  └─ ProjectStore.kt
├─ feature/
│  ├─ importimage/
│  ├─ editor/
│  ├─ summary/
│  ├─ export/
│  └─ board/
├─ board/
│  ├─ BoardTransport.kt
│  ├─ MockBoardTransport.kt
│  └─ HttpBoardTransport.kt
└─ MainActivity.kt
```

颜色库放在：

```text
app/src/main/assets/palettes/
```

## 9. 依赖策略

第一版建议只引入：

- Compose 稳定 BOM。
- Material 3。
- Activity Compose。
- Lifecycle Runtime Compose。
- ViewModel Compose。
- Navigation Compose。
- Kotlin Coroutines。
- Kotlin Serialization JSON。
- AndroidX ExifInterface。

Compose 依赖通过 BOM 统一管理版本：

<https://developer.android.com/develop/ui/compose/bom>

第一版暂不引入：

- Hilt。
- Room。
- Retrofit。
- OpenCV。
- 多模块 Clean Architecture。
- 复杂第三方图片裁剪 SDK。
- 第三方 BLE 框架。

项目数量少时，先在应用私有目录保存 JSON。等确实需要搜索、排序、分页和迁移大量工程时再考虑 Room。

## 10. 核心数据模型

### 10.1 拼豆颜色

```kotlin
data class BeadColor(
    val code: String,
    val nameZh: String,
    val nameEn: String,
    val rgb: Int,
    val labL: Double,
    val labA: Double,
    val labB: Double,
    val available: Boolean
)
```

### 10.2 图纸工程

```kotlin
data class PatternProject(
    val schemaVersion: Int,
    val paletteId: String,
    val paletteRevision: String,
    val rows: Int,
    val columns: Int,
    val cells: List<String>,
    val cropSpec: CropSpec,
    val brightnessAdjustment: Float,
    val chromaAdjustment: Float,
    val createdAt: Long,
    val updatedAt: Long
)
```

`cells` 保存颜色编号，数量必须严格等于：

```text
rows × columns
```

### 10.3 灯板帧

```kotlin
data class LedFrame(
    val protocolVersion: Int,
    val frameId: String,
    val width: Int,
    val height: Int,
    val pixelOrder: String,
    val brightness: Int,
    val rgb24: ByteArray,
    val checksum: Long
)
```

## 11. 颜色算法详细步骤

### 11.1 图片输入统一化

1. 读取图片尺寸，不能直接完整解码超大图片。
2. 根据预览尺寸和网格需求进行缩采样解码。
3. 读取并修正 EXIF 方向。
4. 将宽色域图片统一转换到 sRGB。
5. 透明像素与用户选定背景色合成，默认可使用白色。

### 11.2 裁剪与采样

1. 裁剪框锁定目标网格宽高比。
2. 用户通过平移和缩放决定图片内容。
3. 每个网格格子对应裁剪图中的一个矩形区域。
4. 使用区域平均或多点采样，不能只取中心像素。
5. 采样输出原始 sRGB 网格。

### 11.3 Lab 转换

正确顺序：

```text
sRGB 数值
→ gamma 解码得到线性 RGB
→ XYZ（D65）
→ Lab（D65）
```

亮度和饱和度修正建议在 Lab/LCh 中完成：

- 亮度调整 L。
- 饱和度调整 Chroma。
- 保持色相尽可能稳定。

### 11.4 CIEDE2000 匹配

拼豆颜色库的 Lab 值应预先缓存。每个网格颜色与可用拼豆颜色计算 ΔE00，使用感知色差而不是 RGB 欧氏距离。

单元测试必须覆盖：

- 相同颜色距离为 0。
- 对称性在容差内成立。
- CIEDE2000 标准测试对全部通过。
- 同一输入每次得到相同结果。

### 11.5 最多 K 色优化

第一版可采用：

1. 为每个格子计算到全部拼豆颜色的 ΔE00。
2. 从空候选集合开始。
3. 每轮加入一个能最大幅度降低总体误差的拼豆颜色。
4. 重复直到达到 K 色。
5. 将全部格子映射到选中的 K 色。
6. 尝试一次候选颜色交换，进一步降低误差。
7. 用户锁定的颜色必须计入 K。

验收：

- 输出颜色全部来自当前拼豆色库。
- 实际使用颜色数不超过 K。
- 相同输入结果一致。
- K 增大时总体色差不应明显变差。
- 用户手动锁定的颜色不会被自动替换。

默认不使用抖动。抖动会产生大量散点拼豆，降低实际可拼性。以后可以作为高级选项单独加入。

## 12. 图片内存与性能策略

ARGB_8888 每像素需要 4 字节。约 1200 万像素的图片，仅像素数据就接近 48 MB，还不包括复制和中间结果。

必须执行：

- 先读取图片尺寸，再按需要缩采样。
- 算法位图使用可读取像素的软件 ARGB_8888 位图。
- 图片解码、Lab 转换和匹配不在主线程执行。
- ViewModel 不长期同时保存原图、裁剪图、预览图和导出图。
- 超大 PNG 导出限制最大像素或采用分块绘制。
- 算法操作提供进度和取消能力。

官方 Bitmap 内存说明：

<https://developer.android.com/topic/performance/memory/guide/bitmaps>

## 13. Compose 图纸编辑器策略

网格应使用单个 Compose `Canvas` 批量绘制，不要为每个格子创建一个 Composable。

实现要求：

- Canvas 一次绘制全部格子。
- 手势支持缩放和平移。
- 点击坐标直接换算为行列。
- 只有格子显示尺寸足够大时才绘制编号。
- Lab、统计和颜色优化不能在重组过程中执行。
- 缓存颜色值和距离矩阵。
- 修改单格时只增减两个颜色的计数。
- 撤销栈保存修改前后值，不复制整个大矩阵。

第一版网格范围建议：

```text
最小：8×8
建议常用：16×16、29×29、32×32、64×64
最大：128×128
```

性能验收：

- 29×29 和 64×64 在普通真机上缩放、平移流畅。
- 128×128 可以打开、缩放和点击。
- 单格改色后统计立即更新。
- 放大后显示编号，缩小时自动隐藏。
- 撤销和重做结果正确。

Compose Canvas 官方文档：

<https://developer.android.com/develop/ui/compose/graphics/draw/overview>

## 14. 保存与导出策略

### 14.1 工程保存

第一版工程保存在应用私有目录，采用 JSON。

项目保存内容至少包括：

```text
schemaVersion
appVersion
paletteId
paletteRevision
rows
columns
cells
cropSpec
brightnessAdjustment
chromaAdjustment
createdAt
updatedAt
```

保存时先写临时文件，成功后再替换正式文件，避免中途崩溃损坏工程。

如果工程需要长期重新编辑原图：

- 将原图复制到项目私有目录；或
- 正确持久化系统 URI 权限。

不要长期依赖一次性的相册 URI。

### 14.2 导出

支持：

- PNG 图纸。
- PDF 图纸和颜色图例。
- UTF-8 CSV。

使用 Android Storage Access Framework 的 `ACTION_CREATE_DOCUMENT` 让用户选择保存位置，不直接写任意文件路径，也不申请传统存储权限。

官方文件创建指南：

<https://developer.android.com/training/data-storage/shared/documents-files>

验收：

- 退出 APP 后能够重新打开工程。
- 图纸、统计、裁剪参数和调整参数完整恢复。
- PNG/PDF 与 APP 预览一致。
- CSV 格子数量等于行数乘列数。
- 中文颜色名称使用 UTF-8 后能正常打开。

## 15. Android 权限策略

遵循“用到时再申请、能不申请就不申请”。

### 15.1 图片选择

优先使用 Android Photo Picker，避免申请整个相册读取权限。

官方说明：

<https://developer.android.com/training/data-storage/shared/photo-picker>

### 15.2 文件导出

使用 Storage Access Framework，无需传统存储权限。

### 15.3 ESP32 网络

HTTP 网络通常需要：

```text
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
```

它们是普通权限。

如果将来 `targetSdk = 37`，直接访问局域网设备需要按 Android 17 规则处理 `ACCESS_LOCAL_NETWORK` 运行时权限，或者使用系统提供的设备选择机制。

官方说明：

<https://developer.android.com/privacy-and-security/local-network-permission>

### 15.4 蓝牙

第一版不使用 BLE，因此不要提前申请蓝牙或位置权限。

如果以后改为 BLE，Android 12 及以上需要按具体用途处理 `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT` 等权限。

## 16. 灯板协议与 ESP32 通信

### 16.1 第一版通信方式

建议首版真实联调使用：

```text
Wi-Fi + HTTP POST + 完整帧
```

原因：

- 单张图纸数据很小。
- 易于抓包和调试。
- 不需要一开始处理 BLE 扫描、配对和权限差异。
- WebSocket 留到实时连续刷新时再加入。

ESP-IDF HTTP Server：

<https://docs.espressif.com/projects/esp-idf/en/latest/esp32/api-reference/protocols/esp_http_server.html>

### 16.2 协议字段

发送帧至少包含：

```text
protocolVersion
frameId
width
height
pixelOrder
brightness
rgb24
payloadLength
checksum 或 CRC
```

必须明确：

- 坐标原点在左上角还是其他位置。
- 行优先还是列优先。
- LED 是否蛇形排列。
- RGB、GRB 或其他颜色通道顺序。
- 最大亮度。
- 超时时间。
- 重试规则。

完整帧请求应设计成幂等操作，重复发送同一个 `frameId` 不产生错误叠加。

### 16.3 模拟发送

先定义统一接口：

```kotlin
interface BoardTransport {
    suspend fun send(frame: LedFrame): SendResult
}
```

实现：

```text
MockBoardTransport
HttpBoardTransport
```

Mock 和真实传输必须使用同一种序列化帧。模拟器应支持：

- 成功。
- 连接超时。
- 校验失败。
- 设备拒绝。
- 发送过程中断网。

### 16.4 网络安全

原型阶段可以在本地网络使用 HTTP，但正式版本至少需要一次性配对码或设备令牌，防止同一局域网内任意设备控制灯板。

Android 9 以上默认限制明文流量。调试和生产网络策略应分开，不应给正式 APP 全局开放明文通信。

官方网络安全配置：

<https://developer.android.com/privacy-and-security/security-config>

## 17. 分阶段开发步骤与验收标准

### 阶段 0：环境基线

工作内容：

- 安装 Android Studio 和 SDK。
- 配置 JDK 17。
- 配置模拟器。
- 配置实体手机。
- 创建空 Compose 工程。

验收：

- `gradlew.bat --version` 使用 JDK 17。
- `adb devices` 能看到实体设备或模拟器。
- 空 APP 在模拟器和真机运行。
- `testDebugUnitTest`、`lintDebug` 和 `assembleDebug` 成功。

### 阶段 1：数据契约和颜色算法

工作内容：

- 建立 `BeadColor`、`Palette`、`PatternProject`、`CropSpec`。
- 实现 sRGB → XYZ → Lab D65。
- 实现 CIEDE2000。
- 实现颜色数量统计。
- 实现项目 JSON 序列化。

验收：

- CIEDE2000 标准测试对在容差内全部通过。
- 相同颜色距离为 0。
- 保存后重新读取，矩阵完全一致。
- 所有颜色数量之和等于 `rows × columns`。

### 阶段 2：导入、裁剪和网格采样

工作内容：

- Photo Picker 导入。
- EXIF 方向修正。
- 透明背景合成。
- 锁定网格比例的裁剪、平移和缩放。
- 分区面积采样。
- 生成原始 Lab 网格。

验收：

- 已知 2×2 色块生成正确的 2×2 矩阵。
- 横图、竖图和旋转图方向正确。
- 图片不被拉伸。
- 6000×4000 测试图不会内存溢出。
- 处理过程不阻塞界面。

### 阶段 3：拼豆颜色匹配和 K 色限制

工作内容：

- 加载带版本号的拼豆颜色库。
- 缓存调色板 Lab。
- 建立 ΔE00 距离矩阵。
- 执行最多 K 色优化。
- 加入亮度和饱和度调整。
- 支持锁定指定颜色。

验收：

- 所有结果颜色来自当前颜色库。
- 使用颜色数不超过 K。
- 相同输入输出一致。
- 用户锁定颜色得到保留。
- 亮度和饱和度调整可撤销和重新生成。

### 阶段 4：Compose 图纸编辑器

工作内容：

- Canvas 批量绘制网格。
- 缩放和平移。
- 点击改色。
- 撤销和重做。
- 颜色编号显示。
- 颜色图例和数量同步。

验收：

- 29×29、64×64 操作流畅。
- 128×128 能打开、缩放和点击。
- 修改一格只更新对应两种颜色的统计。
- 编号按缩放级别显示或隐藏。
- 撤销和重做结果正确。

### 阶段 5：保存和导出

工作内容：

- 私有目录 JSON 工程保存。
- 原图保留策略。
- PNG 导出。
- PDF 图纸与图例导出。
- UTF-8 CSV 导出。
- SAF 文件选择。

验收：

- 重启 APP 后图纸能完整恢复。
- 图纸、统计和裁剪参数一致。
- PNG/PDF 与预览一致。
- CSV 颜色数量正确。
- 不申请不必要的存储权限。

### 阶段 6：模拟灯板

工作内容：

- 定义 `BoardTransport`。
- 定义 `LedFrame`。
- 实现 Mock 发送。
- 模拟成功、失败、超时和校验错误。

验收：

- Mock 与真实传输使用相同帧格式。
- 非法尺寸、空矩阵和校验失败会被拒绝。
- UI 能正确显示发送状态。
- 旧帧不会覆盖新帧。

### 阶段 7：ESP32 HTTP 联调

工作内容：

- APP 输入或发现 ESP32 地址。
- HTTP POST 发送完整 RGB 帧。
- ESP32 校验尺寸、长度和 checksum。
- ESP32 返回 `frameId` 和应用状态。
- 加入超时、断网和重连提示。

验收：

- 8×8 色块位置和方向正确。
- 蛇形映射正确。
- 颜色通道顺序正确。
- 错误 IP、断网和超时有清晰提示。
- 最大亮度被限制在安全值。
- 另一台同局域网手机不能绕过配对直接控制正式设备。

## 18. 完整发布前验收清单

- 可以导入图片。
- 可以按目标比例裁剪、平移和缩放。
- 可以设置网格行列数。
- 可以设置最大颜色数量。
- 使用正确的 Lab/CIEDE2000 匹配。
- 可以显示颜色编号、名称和色块。
- 可以点击单格手动改色。
- 可以撤销和重做。
- 每种颜色数量统计正确。
- 总颜色颗数等于网格总格数。
- 可以保存并重新打开图纸。
- 工程文件包含格式版本和色库版本。
- 可以导出 PNG、PDF 和 CSV。
- 大图片不会导致内存溢出。
- 64×64 图纸编辑流畅。
- 没有 ESP32 时可以模拟发送。
- 灯板帧有协议版本、frameId、长度和校验值。
- 实际 ESP32 原型能正确显示 8×8 测试色块。
- 权限只在功能真正需要时申请。

## 19. 下一步执行顺序

现在按以下顺序推进：

1. 安装 Android Studio 最新稳定版。
2. 将 Android SDK 安装到 `D:\Android\Sdk`。
3. 安装 Platform 36、37、Platform-Tools、Build-Tools、Command-line Tools、Emulator 和 API 36 镜像。
4. 让 Android Studio Gradle 使用内置 JBR/JDK 17。
5. 配置 WHPX 模拟器。
6. 配置一台 Android 实体手机。
7. 在 `D:\Codex\WorkSpace\智能拼豆\android-app` 创建 Compose 工程。
8. 完成空工程构建、Lint、单元测试和 APK 验证。
9. 收集第一套真实拼豆颜色数据和固定测试图片。
10. 从数据模型、Lab/CIEDE2000 单元测试开始编码。

在阶段 0 完成前，不开始 ESP32、复杂导出或最终 UI 美化。

## 20. 参考资料

- Android Studio 安装：<https://developer.android.com/studio/install>
- Android 构建 JDK：<https://developer.android.com/build/jdks>
- AGP 9.0 兼容要求：<https://developer.android.com/build/releases/agp-9-0-0-release-notes>
- Google Play 目标 API 要求：<https://support.google.com/googleplay/android-developer/answer/11926878?hl=zh-Hans>
- Compose BOM：<https://developer.android.com/develop/ui/compose/bom>
- Android 模拟器加速：<https://developer.android.com/studio/run/emulator-acceleration>
- Android 真机运行：<https://developer.android.com/studio/run/device>
- Photo Picker：<https://developer.android.com/training/data-storage/shared/photo-picker>
- Storage Access Framework：<https://developer.android.com/training/data-storage/shared/documents-files>
- Compose Canvas：<https://developer.android.com/develop/ui/compose/graphics/draw/overview>
- Bitmap 内存：<https://developer.android.com/topic/performance/memory/guide/bitmaps>
- Android 局域网权限：<https://developer.android.com/privacy-and-security/local-network-permission>
- Android 蓝牙权限：<https://developer.android.com/develop/connectivity/bluetooth/bt-permissions>
- Android 网络安全配置：<https://developer.android.com/privacy-and-security/security-config>
- ESP-IDF HTTP Server：<https://docs.espressif.com/projects/esp-idf/en/latest/esp32/api-reference/protocols/esp_http_server.html>

## 21. 审查来源

本方案由 `agency` 中的 `engineering-mobile-app-builder` 移动应用专家审查，并结合 2026-09-02 对当前 Windows 电脑、项目目录和开发工具的重新核验结果整理。
