# FORK-NOTES.md — 跟随上游的检查单

这个 fork 相对 `upstream/main` 有两类改动：**平板适配**（本文档）和 **CI**（`manual-build.yaml`、
`release-build.yaml`，纯新增文件，永不冲突）。

跟随上游时不要重读 diff。按下面的顺序做，每一步都有明确的"看什么、为什么"。

- **基线**：上游合并点 `d2b979cc`
- **本文档更新于**：2026-09-23（平板优化第二轮）

---

## 1. 改动都挂在哪

| 能力 | 挂载点 | 说明 |
|---|---|---|
| 内容限宽（21 个页面） | `ui/component/base/RYScaffold.kt` 一处 | 全部页面经由 `RYScaffold`，限宽与顶部栏图标对齐都在这里 |
| 尺寸类 | `ui/theme/Theme.kt`（`ProvideAdaptiveLayout { content() }`） | 只提供 `LocalAdaptiveLayout`，**不碰 `LocalDensity`** |
| 可用宽度 | `RYScaffold` 的 `BoxWithConstraints` → `LocalAvailableWidthDp` | pane 内自动退化为 0 |
| 底部栏对齐 | `FeedsPage.kt` 的 `FilterBar(contentPadding = ...)` | 只有 Feeds 用这个槽位；FilterBar 的背景保持通栏 |
| 顶部栏图标对齐 | `FeedsPage.kt` 的 `navigationIcon` / `actions` | 只挪图标，不缩整条 bar |
| 列表字号 | `ui/component/ListFonts.kt` + 6 处 `.withFeeds/FlowListStyle()`（每个 1 行） | 挂载点刻意保持最轻 |
| 悬停反馈 | `ui/interaction/Clickable.kt` | 触屏无影响，接鼠标才有 |

**新增文件（永不冲突）**：`ui/adaptive/{AdaptiveLayout,AdaptiveContentPadding,AppSizeClass}.kt`、
`ui/component/ListFonts.kt`、`infrastructure/preference/{Feeds,Flow}{Fonts,TextFontSize}Preference.kt`、
`ListFontsPreference.kt`、两个 workflow、3 个单测。

---

## 2. rebase 后逐项核验（不要跳）

### 2.1 结构是否还在

- [ ] `RYScaffold.kt` 仍被 ~22 处调用，且 `BoxWithConstraints` + `rememberAdaptiveContentGutter(maxWidth)`
      这一圈还在。**如果上游把调用点迁到 m3 `Scaffold`，限宽会逐个失效**——这是本 fork 唯一有保质期的挂载点。
      上游迁移时，把 `BoxWithConstraints` + gutter 那几行一起搬进 m3 `Scaffold` 的 `content`。
- [ ] `Theme.kt` 里 `content = { ProvideAdaptiveLayout { content() } }` 仍在 `MaterialTheme` **之内**
      （在之内才能读到主题；将来若要在 typography 层面放大字号，依赖这一点）。
- [ ] `ui/page/adaptive/`（上游的目录，别和自己的 `ui/adaptive/` 搞混）仍是 list-detail 双栏的唯一实现。

### 2.2 会静默失效的耦合（重点）

- [ ] **字号基线 `16`**：`FeedsTextFontSizePreference.baseline` / `FlowTextFontSizePreference.baseline`
      假定 `MaterialTheme.typography.titleMedium.fontSize == 16.sp`（M3 默认值）。上游升 Compose BOM、
      或某个 `Typography(...)` 覆写了 `titleMedium`，都会让所有列表字号静默偏移。
      → **`./gradlew :app:testDebugUnitTest` 里的 `ListFontBaselineTest` 是这条的报警器。**
      测试跳过（不是通过）说明 guard 没生效，不是"没问题"。
- [ ] `GroupItem.kt`（群组名）与 `ArticleItem.kt`（文章标题）是否仍用 `titleMedium`。
      若改成别的样式，baseline 要跟着改。
- [ ] `ui/component/ListFonts.kt` 的 `sizeSp / baselineSp` 是否仍是唯一缩放入口。
- [ ] `ui/component/reader/Styles.kt` 的 `MediumContentWidth`(600) / `ExpandedContentWidth`(768) 是否仍是 600/768。
      自有的 `AdaptiveContentMaxWidth = 640.dp` 是为了**夹在两者之间**才选的；上游若改成同一档，
      可以考虑统一，但**不要**顺手改成 600（列表行比正文段落耐受更宽）。

### 2.3 限宽的行为边界

- [ ] `AdaptiveLayoutSpec.Compact` 仍是「不做任何适配」：gutter 恒为 0。
      **手机端必须与上游逐像素一致**，这是本改动能被上游接受、也是它不成为行为分叉的前提。
- [ ] `adaptiveContentGutter()` 对 `Dp.Unspecified` / `Dp.Infinity` 返回 `0.dp`。
- [ ] `AppSizeClass.fromWidthDp()` 仍从不抛异常（负数、0 都落 `Compact`）。
- [ ] pane 宽度 < 640dp，所以 `FlowPage` 在双栏里**不被缩进**。若上游把 pane 加宽到 ≥640，
      会出现额外内边距——那是设计如此，但要确认观感可接受。

---

## 3. 真机核验清单

模拟器用 Android Studio 的 Resizable 设备，手动拖宽度跨越 600 / 840dp。

- [ ] **手机竖屏**：Feeds / Flow / 阅读 / 设置 —— 与改动前逐一比对（**核心回归项**）
- [ ] **平板竖屏（~800dp）**：Feeds 内容居中，两侧约 80dp；顶部两个图标与内容列同宽
- [ ] **平板横屏（~1280dp）**：两侧约 320dp；**逐个**打开设置子页（accounts ×3、color ×10、
      interaction、languages、tips ×2、troubleshooting、startup）确认都居中
- [ ] **双栏（关键）**：Flow 列**不被额外缩进**，阅读列按上游逻辑居中
- [ ] **拖动窗口跨断点**：无跳变、无崩溃
- [ ] **列表字号** 10 / 16 / 32sp：单调变化；16sp 时与上游外观一致
- [ ] **列表字体**：External 与阅读页一致；Default 无变化
- [ ] **无障碍：系统字体 200%** —— 确认文本不被裁切（旧版曾覆写 `LocalDensity`，已移除；这是回归验证）
- [ ] **外接鼠标**：列表项、图标按钮悬停有反馈
- [ ] 深色 / AMOLED 主题、阿拉伯语（RTL）：内边距方向正确

---

## 4. 已知限制（不是 bug，是取舍）

1. **横向系统内边距会让居中偏半个内边距**。gutter 由 `RYScaffold` 拿到的约束宽算出，
   而该约束包含系统栏内边距；再往内才是 Scaffold 自己的 `calculateStartPadding`。
   横屏 + 三键导航（侧边导航栏）时会两侧不等，内容列宽 640 会变成 ~592 且中心偏 ~24dp。
   **要修的话**：在 `RYScaffold` 里减掉 `WindowInsets.systemBars` 的左右内边距再算 gutter。
2. **显式 `topBar`（Feeds / Flow / Startup）不参与内容限宽**。Feeds 的两个图标已单独对齐；
   Flow 的 `LargeTopAppBar` 标题、Startup 页的 FAB 仍是窗口对齐。原因：整条 bar 可点击（回顶），
   缩窄会拿走点击区域。
3. **`FlowPage` 的 `FilterBar` 未接 `contentPadding`**，因为它在 pane 里（gutter 恒为 0）。
   只有"Expanded 且单栏"这种少见形态下，底部栏会是通栏而内容被限宽。
4. **Feeds ↔ Flow 的 `filterBar` sharedElement 过渡**：两侧 gutter 不同（窗口宽 vs pane 宽），
   过渡时宽度会插值。这是上游共享元素设计的既有行为，本改动没有改变它。
5. **`DataStoreExt.kt` 里 4 个偏好键共 24 行样板**，上游每加一个偏好都会在同一区域产生相邻冲突。
   合并为 2 个键可让冲突面减半，代价是老用户偏好需要迁移；**有真实用户前不要动**。

---

## 5. 千万别做（会与"不改变原有功能"冲突）

- `AndroidManifest.xml` 的 `launchMode="singleInstance"` → `singleTop`：与多窗口/PC 模式冲突，
  但 `MainActivity.kt` 的 `NewIntentHandlerEffect` 依赖"intent 回送到已有实例"，**需要先在真机验证
  widget 点击与分享进入**，否则不要改。
- 加 `configChanges`：会改变 Activity 重建行为，上游依赖重建恢复 back stack。
- Feeds 多列网格、三栏 `[Feeds | Flow | Reading]`：都在和上游高频文件（`FeedsPage.kt`、
  `ui/page/adaptive/`）赛跑，冲突成本远大于收益。

---

## 6. 把成本降到 0 的唯一路径

自有的自适应层有个罕见性质：**Compact 恒等**（手机端零变化）。这意味着对上游是零风险改动，
而上游自己在投入 `ui/page/adaptive/`，说明维护者在意平板体验，只是还没覆盖"全窗口单页"和"列表字号"。
另外 Android 16（`targetSdk 36`）起系统强制大屏适配，上游**迟早必须做**这件事。

建议拆成 2–3 个小 PR 回馈上游，而不是一个大 PR：

1. `ui/adaptive/` 基础设施 + `RYScaffold` 挂载限宽（纯新增 + 1 处改动）
2. `ListFonts` 列表字号体系（含 2 个 StylePage 的新设置项）
3. `Clickable` 悬停反馈 + 顶部栏图标对齐（可选）

**上游合并 = fork 冲突面归零。** 提 PR 前先想清楚 `ListFontsPreference` 用 `-1` 当哨兵这点，
上游可能更倾向 `Int?` 或单独的布尔开关。
