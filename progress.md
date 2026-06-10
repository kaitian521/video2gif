# video2gif 实施进度

> 配套 [实施计划.md](实施计划.md) / [技术方案.md](技术方案.md)。记录**已落地**的内容、关键文件与验收状态。
> 更新日期:2026-06-11

## 总览

| 阶段 | 状态 | 说明 |
|---|---|---|
| 脚手架 | ✅ 完成 | Android 工程 + Gradle wrapper + 项目配置 |
| P0 工程脚手架与版本锁定 | ✅ 完成 | Media3 全家桶锁 1.10.1;EditState / buildVideoEffects 骨架 |
| P1 导入与校验 + 本地可读路径 | ✅ 完成(待真机交互复核) | 选视频 + 时长校验 + sourceLocalPath |
| P2 截取区间(500ms–10s) | ✅ 完成(待真机交互复核) | 区间夹紧 + 内嵌预览 + 进度细条 + 松手重播 |
| P3 预览骨架(Effect 管线) | ✅ 完成 | Presentation 进管线 + 预览页独立 + 最小导出 harness 真机验尺寸 |
| P4 比例(中心裁剪) | ✅ 完成 | AspectRatio + centerCropHalfExtents + cropEffect;预览裁切窗口严格按比例、无黑边 |
| P5 缩放 / 旋转 | 🟡 部分 | 缩放完成(双指 + 双击复位,纯 Crop 实现);**旋转未做** |
| P8 导出 | 🟡 雏形 | VideoExporter 一遍出无音轨 mp4;缺变速 / 码率 / 帧率 / 进度 / WYSIWYG 对拍 |
| P10 保存 | 🟡 雏形 | MediaStoreSaver 落相册 + 日期元数据;缺取消清理 / 分享 |
| P6 / P7 / P9 / P11 | ⬜ 未开始 | 下一步:P6 拖动(或先补 P5 旋转) |

技术栈:Kotlin 2.2.10 / Compose(BOM 2026.02.01)/ AGP 9.2.1 / compileSdk 37 / minSdk 24 / Java·Kotlin 21 / Media3 **1.10.1**。

---

## 脚手架

- 单 Activity + Compose(`MainActivity` → `Video2gifApp`)。
- compileSdk 37、minSdk 24、targetSdk 36;Java/Kotlin 21 工具链。
- Gradle wrapper、`settings.gradle.kts`、根 `build.gradle.kts`、`gradle.properties` 齐全,fresh clone 可直接构建。
- 提交:`b1a045c`、`4b9c8e1`。

---

## P0 工程脚手架与版本锁定

**目标**:能装能跑的空 app,Media3 全家桶同版本锁死。

**已实现**
- `gradle/libs.versions.toml`:`media3 = "1.10.1"`,5 个 artifact(`exoplayer` / `effect` / `transformer` / `common` / `ui`)全部 `version.ref = "media3"` → 同版本死锁,无动态范围。
- `app/build.gradle.kts`:接入 5 条 media3 依赖;全局 `compilerOptions.optIn += androidx.media3.common.util.UnstableApi`。
- `EditState`(`EditState.kt`):单一数据源(P0 起空壳,逐阶段加字段)。
- `buildVideoEffects(state): List<Effect>`(`VideoEffects.kt`):预览/导出共用的像素变换真值入口,当前返回 `emptyList()`;**变速不进此处**。
- ffmpeg-kit 暂不引入(留 P9)。

**验收(DoD)**
- ✅ 真机(Pixel 10 / Android 16)安装启动,空白页不崩。
- ✅ `debugRuntimeClasspath` 下 media3 各 artifact(含传递的 container/database/datasource/decoder/extractor/muxer)解析一致为 1.10.1。

**提交**:`7911677`。

> 备注:Kotlin 编译期偶发 `…UnstableApi is not an opt-in requirement marker` 警告 —— 已确认 `Effect` 确为 `@UnstableApi` 且编译通过,该警告为 androidx opt-in marker 的**已知良性**提示,不影响功能。

---

## P1 导入与校验 + 本地可读路径

**目标**:从相册选视频,校验时长 >500ms,拿到**确认可读**的 `sourceLocalPath`。

**已实现**
- 选视频:`PickVisualMedia`(`VideoOnly`)照片选择器,**无需存储权限**。
- 时长校验:`MediaMetadataRetriever` 取总时长,**≤500ms 拒绝**(`MIN_DURATION_MS = 500`)。
- 本地路径(§5.1):best-effort 查 `_data` 绝对路径 → 验证 `exists() && canRead()`;拿不到 / 读不了 / 仅 `content://` → `openInputStream` **复制到 `cacheDir`** 兜底。`content://` **绝不**直接拼进 ffmpeg。
- `EditState` 增 `sourceUri` / `sourceLocalPath` / `durationMs`(转为 `data class`)。
- 关键文件:`VideoImporter.kt`(导入器)、`ui/ImportScreen.kt`(导入页)。

**验收(DoD)**
- ⏳ >500ms 进入下一步 / ≤500ms 被拒 —— **待真机交互复核**。
- ⏳ `sourceLocalPath` 的 `exists() && canRead()` 为真 —— 成功页已实时显示,待目测。
- ⏳ scoped-storage(`_data` 拿不到)走复制兜底 —— 照片选择器 URI 即此场景,待目测。

**提交**:`0575c33`。

---

## P2 截取区间(500ms–10s)

**目标**:UI 选 `[clipStartMs, clipEndMs]`,长度约束 `500ms ≤ end-start ≤ 10s`,默认从 0;区间**只写进 state**,导出时才折进 `ClippingConfiguration`(§3)。

**已实现**
- `EditState` 增 `clipStartMs` / `clipEndMs`;默认区间 `[0, min(duration, 10s)]`(导入成功时设置)。
- 区间约束纯函数(`ClipConstraints.kt`):`defaultClipEndMs` / `clampStartMs` / `clampEndMs` / `isValidClip` —— 长度恒夹 `[500ms, 10s]` 且落在 `[0, duration]`。
- 截取页(`ui/TrimScreen.kt`):Material3 `RangeSlider` 双滑块接夹紧逻辑;区间写回 state,不做任何裁剪。
- 顶层导航(`ui/Video2gifApp.kt`):导入页 ↔ 截取页,持有唯一 `EditState`。
- 单测(`ClipConstraintsTest.kt`):**9 例全绿**(最小/最大长度、duration 封顶、不越负、边界合法性)。

### 截取页增强(本期叠加)

- **内嵌视频预览**(`ui/VideoPreview.kt`):ExoPlayer + PlayerView;**prepare 前**先 `setVideoEffects(buildVideoEffects)` 接通效果管线骨架(P3 地基,当前空列表=直通);**循环播放当前选区**(seek 到 start、到 end 回跳的近似);**静音**(与导出无音轨一致);`onDispose` 释放播放器。
- **滑块避让侧边手势**:`RangeSlider` 左右各 `+15dp` padding(叠加在 Column 的 24dp 之上)。
- **选区内播放进度细条**:在两滑竿之间叠加一根细竖条,按 `pos/duration` 定位并夹在 `[clipStart, clipEnd]` 内,随播放左右滑动。
- **滑块松手重播**:`onValueChangeFinished` 触发预览从左滑竿(`clipStartMs`)重新播放。

**验收(DoD)**
- ✅ 区间正确夹在 `[500ms, 10s]` —— 由 `ClipConstraintsTest` 9 例**单测覆盖通过**(无需真机)。
- ⏳ 区间值写入 state 且 UI 复现、预览/细条/松手重播表现 —— **待真机交互复核**。

**提交**:`7e3b875`、`2d29e21`、`166d875`、`596780d`。

> 待微调:进度细条与滑竿对齐用了估计值 `thumbRadius ≈ 10dp`;若 Compose BOM 2026.02 采用 expressive 滑块(条形 thumb),极端位置或需微调内缩量,待目测确认。

---

## P3 预览骨架(Effect 管线)+ 最小导出 harness

**目标**:`buildVideoEffects` 真正生效(Presentation 定高),预览页独立;提前搭最小导出地基(P8/P10 雏形)。

**已实现**
- `EditState` 增 `targetHeight`(默认 720,取偶);`buildVideoEffects` 返回 `Presentation.createForHeight`(1.10.1 无 `copyWithUnsetSideRoundedTo(2)`,偶数维度由偶数 targetHeight + 导出端 encoder 兜底)。
- 预览页独立(`ui/PreviewScreen.kt`):导航 导入 → 截取 → 预览;`BackHandler` 接各页返回。
- 10s 上限改**软约束**:滑动可超,点「下一步」时才拦(`exceedsMaxClip`)。
- **最小导出 harness**(`VideoExporter.kt`):Transformer 一遍出无音轨 mp4(Clipping + removeAudio + **同一条** `buildVideoEffects` + H264 + `trimOptimization(false)`),导出后读回编码尺寸验证。
- **相册落盘**(`MediaStoreSaver.kt`,P10 雏形):产物发布到 `Movies/Video2gif`(API 29+ IS_PENDING 免权限,≤28 走 DATA + WRITE 权限);补 `DATE_TAKEN/DATE_ADDED/DATE_MODIFIED`,保证图库**主时间线**可见。

**验收(DoD)**
- ✅ 真机导出编码高 = 720(`targetHeight` 真实生效)、产物相册可见。

**提交**:`1695738`、`438b443`、`8937f2a`。

---

## P4 比例(中心裁剪,不留黑边)

**已实现**
- `AspectRatio` 枚举(原始 / 1:1 / 3:4 / 4:3 / 16:9 / 9:16)+ 纯函数 `centerCropHalfExtents`(`CropGeometry.kt`):NDC 半宽/半高,`r = targetAR/srcAR` 逐轴缩窗口。
- `cropEffect` 插进 `buildVideoEffects`(Crop → Presentation),预览/导出同一真值,导出真裁。
- 预览页比例 chips,切换即时生效。
- 单测(`CropGeometryTest.kt`):各源 × 各比例断言半宽/半高 ∈ (0,1]、至少一轴贴满、裁后比例 == 目标。

**提交**:`0693efb`。

---

## P5 缩放(剪映式)—— 旋转未做

**已实现**
- `EditState.scale`(≥1);`centerCropHalfExtents` 叠加缩放 = 窗口逐轴 `/s`(比例不变、窗口更小 = 放大取景),**纯 Crop 实现 zoom**,导出自动跟上;单测覆盖 scale 1/2/4、<1 夹到 1。
- 预览:双指缩放、双击复位(graphicsLayer 表现,播放表面不重建、不抖不闪)。

**未做**:旋转(90° 步进 + 任意角内接矩形,全计划最高风险点,见实施计划 P5)。

**提交**:`877d2c3`。

---

## 预览交互重构:去取景框 + 比例外框 + 无黑边(2026-06-10/11)

P4/P5 最初为「固定取景框 + 框外压暗」(剪映式)。按新交互改为**无取景框**:

- **裁切窗口(外框)严格等于所选比例**:`outAR ≤ 9/15` → 定高(占满可用高)、宽自适应;否则定宽(占满可用宽)、高自适应。切比例时外框直接变形。
- **无黑边(只裁切)**:PlayerView 始终保持**源视频比例**并 cover 外框(`requiredWidth/Height` 突破父约束、自动居中),溢出由 `clipToBounds` 裁掉 → 中心裁切。可见区域与导出 `cropEffect` 同一几何真值(横向可见比例 = `outAR/srcAR`,正是 `centerCropHalfExtents` 的 `r`),WYSIWYG 保持。
- 删除 `ui/CropOverlay.kt`(取景框遮罩组件);双指缩放/双击复位保留。

**为什么不能用 `resize_mode=zoom` 让 PlayerView 自己裁**(media3 1.10.1 源码实锤,踩坑记录):

1. 开 `setVideoEffects` 后,`MediaCodecVideoRenderer` 的 `VideoSink.Listener.onVideoSizeChanged` 是**空实现**(源码挂 TODO **b/292111083**),普通上报路径也被 `videoSink == null` 条件挡掉 → `Player.getVideoSize()` 恒为 0×0、`Player.Listener.onVideoSizeChanged` 永不触发。
2. `PlayerView` 靠 `getVideoSize()` 驱动 `AspectRatioFrameLayout`,拿到 0 恒不调整 → 任何 `resize_mode` 形同虚设。
3. 效果管线末端(`FinalShaderProgramWrapper`)强制把画面 `SCALE_TO_FIT`(letterbox)进输出 surface → surface 比例 ≠ 内容比例时**黑边画在 surface 里**,view 层无解。唯一稳妥解:让 surface 比例**恒等于**内容比例,letterbox 恒为 no-op。
4. 推论:`onVideoDisplaySize`「播放器真实尺寸校正源宽高比」回调在 effects 下**不会触发**,实际几何依赖导入时 MMR 读的 `displayWidth/Height`(已应用旋转);回调留作升级 media3 后的兜底。

---

## 待真机交互验收(设备 PIN 锁,暂无法自动驱动)

以下需在解锁手机后手动确认:

1. **P1**:选 >500ms 视频进入截取页;`sourceLocalPath` 显示且 `exists()&&canRead()=true`;路径落在 `…/cache/imported_source.*`(复制兜底)。
2. **P2 预览**:视频自动循环播放、静音;细条在两滑竿间随播放滑动。
3. **P2 交互**:拖动滑块时区间长度始终在 `[500ms, 10s]`;**松手后从左滑竿重播**;滑竿不触发系统侧边手势。
4. **预览页(重构后)**:切各比例外框按 9/15 规则变形(≤9/15 定高,否则定宽);**画面无黑边、只裁切**;双指缩放/双击复位正常;导出尺寸与预览可见区域一致。

---

## 下一步

- **P5 剩余**:旋转(90° 步进 + 任意角内接矩形去黑边,先纯函数单测兜底再上真机)。
- **P6**:拖动改变位置(Crop 窗口平移 + 夹紧,任何方向不露黑边)。
- 真机回归上节验收清单。
