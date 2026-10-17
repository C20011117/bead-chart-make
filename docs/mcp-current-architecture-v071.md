# v0.7.1 当前架构与保留依据

本文整合当前仍有效的色卡、数据与算法约束，取代旧版本实施记录中的过时交付路径。`docs/archive/` 中的原始架构设计稿仅用于追溯，不代表全部规划功能已实现；实际功能以本文件及 `android-app/README.md` 为准。

## 范围与工作流

只处理普通图片转换、已有图纸的网格对齐、图例/格子色号校对、工程和 PNG 导出；不开发灯板、蓝牙、计时和拼豆完成进度。

已有图纸：本地图片/公开链接候选 → 自动网格检测及同屏审核 → 人工框选底部图例 → OCR/手工核对 → 逐格识别与原始小格比对 → 单格/选中批量修正、撤销重做 → 待确认清零 → 直接导出 PNG。保存工程独立于导出。

- 网格：8–128 独立行列，独立横纵格距；正视、轴对齐图纸，不做透视矫正。算法与已测样本见 `docs/mcp-auto-grid-v071.md`。
- 预览最长边 1024、工作图 2048、小格细节 512；EXIF 校正后统一坐标；支持处理进度与取消。
- MARD 图例及人工校对优先，不仅依靠最近色卡颜色猜色号。Q4/R11 等同 RGB 不当作可自动分辨。
- 格子值：待确认 -2、空格 -1、色卡索引 ≥0。白豆 H2 与空格不同，待确认阻止最终导出。
- 确认后的不可变 Pattern 驱动预览、用量和 PNG；快照防御性复制。PNG 每格 32px、格内色号、四边坐标及动态用量图例，限制 25MP。
- 工程 schema1、magic 0x42454144，CRC 与色卡身份校验、AtomicFile、私有复制原图（≤32MB）；重开保留校对矩阵，网格需再次人工确认。卸载会删除私有工程。
- 原图照片模式面积平均；插画模式忠实保留源图细线、主色，不添加强制黑轮廓，不恢复已移除的增强功能。Lab(D65)/CIEDE2000 使用全部 291 色，默认优化 64×64，但不保证细线完整恢复。

## MARD 来源与许可证

- 来源：https://github.com/maxcleme/beadcolors ，提交 `f97ff4283d03cef5cd7e1071a86f5892e0c0c61b`。
- `raw/mard.csv` SHA256：`108a0118bc209e4a74fbfc2433c20a3b8ac6e8b91be0bbd412a1636ee05fb12b`；版本 `mard-291-beadcolors-f97ff42-v1`。
- 291 唯一色号、290 RGB、15 系列；Q4/R11 均 #FFEBFA，H2 #FFFFFF，H7 #000000。普通图片纯色距离并列时自然色号顺序选择；已有图纸有同 RGB 色号时必须依据原图人工分辨。
- 第三方屏显参考，非厂家实测；透明/珠光等材质不能仅以 HEX 表达，库存和实物需核对。
- 原数据与校验信息：`android-app/app/src/main/assets/palettes/`；MIT：`android-app/app/src/main/assets/licenses/beadcolors-MIT.txt`。这些文件属于当前运行依赖，清理时全部保留。

## 测试素材与隐私边界

- 旧照片原件移到 `android-app/test-inputs/original-photo.jpeg`，SHA256 `bb458e298ef60750ac51b6135d3aa3c65c60266a4bbf2e4c4f68cb34129a0e9b`，不能被后来同名上传的网格图替换。
- 实际网格夹具 `android-app/app/src/test/resources/charts/mard-51x52.jpeg`，SHA256 `307e9a966d97ddc22631a6a9201e6da6cca2a0ff775f84978262639a80fb73b5`，仅测试使用。
- 普通照片回归工具 `android-app/tools/IllustrationProbe.java` 的两个参数分别为输入图路径与输出目录；回归输出属于本机生成物，不纳入仓库。不要再使用已删除的 regression/v3 输入路径。
- ML Kit 在设备上处理输入/结果，但可能联网发送 SDK 使用和性能指标；OCR 在用户告知确认后初始化，不将 bundled 误写成绝对无网络。
- 公开网页候选提取不保证绕过第三方平台登录/保护。实机 UI、SAF、OCR、链接成功率和性能尚未全面验收。

构建与交付以 `android-app/README.md`、`docs/mcp-export-fix-v071.md` 为准；构建产物和验证报告不纳入仓库。
