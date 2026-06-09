# video2gif 实施进度

> 配套 [实施计划.md](实施计划.md) / [技术方案.md](技术方案.md)。记录**已落地**的内容、关键文件与验收状态。
> 更新日期:2026-06-09

## 总览

| 阶段 | 状态 | 说明 |
|---|---|---|
| 脚手架 | ✅ 完成 | Android 工程 + Gradle wrapper + 项目配置 |
| P0 工程脚手架与版本锁定 | ✅ 完成 | Media3 全家桶锁 1.10.1;EditState / buildVideoEffects 骨架 |
| P1 导入与校验 + 本地可读路径 | ✅ 完成(待真机交互复核) | 选视频 + 时长校验 + sourceLocalPath |
| P2 截取区间(500ms–10s) | ✅ 完成(待真机交互复核) | 区间夹紧 + 内嵌预览 + 进度细条 + 松手重播 |
| P3 预览骨架(Effect 管线) | 🚧 部分(预览已接通) | 已在截取页接 ExoPlayer + setVideoEffects 骨架;尚缺 Presentation / 明显滤镜验证 |
| P4–P11 | ⬜ 未开始 | |

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

## 待真机交互验收(设备 PIN 锁,暂无法自动驱动)

以下需在解锁手机后手动确认:

1. **P1**:选 >500ms 视频进入截取页;`sourceLocalPath` 显示且 `exists()&&canRead()=true`;路径落在 `…/cache/imported_source.*`(复制兜底)。
2. **P2 预览**:视频自动循环播放、静音;细条在两滑竿间随播放滑动。
3. **P2 交互**:拖动滑块时区间长度始终在 `[500ms, 10s]`;**松手后从左滑竿重播**;滑竿不触发系统侧边手势。

---

## 下一步:P3 预览骨架(Effect 管线)

预览播放器已接通(`setVideoEffects` 在 `prepare()` 前调用,骨架已就位)。P3 剩余:

- `buildVideoEffects` 返回 `Presentation.createForHeight(targetHeight).copyWithUnsetSideRoundedTo(2)`。
- `EditState` 增 `targetHeight`。
- **故意加一个明显滤镜**肉眼验证管线真生效(验完删)。
- 确认 `setVideoEffects` 在 `prepare()` 前至少调一次(硬约束 —— 当前预览已满足)。
