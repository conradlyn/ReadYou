# ReadYou 平板适配分支 — 审计报告

- **审计对象**：`conradlyn/ReadYou`，`main` @ `dc68c1ff`
- **上游基线**：`d2b979cc`（上游 `ReadYouApp/ReadYou` 合并点）
- **自有改动**：5 个提交，28 个文件，+795 / −12
- **审计日期**：2026-09-23
- **审计范围**：仅评估「在**不改变现有功能**的前提下优化 Android 平板体验」，并附带「上游变动跟随成本」评估

---

## 执行状态（2026-09-23 更新）

本报告的建议**已执行**。逐项状态：

| 项 | 状态 | 落地位置 |
|---|---|---|
| A1 自适应 `fontScale` 与列表字号双重相乘 | ✅ 已修 | `AdaptiveLayout.kt`：删除 `fontScaleMultiplier`、`MaxAdaptiveFontScale`、`LocalDensity` 覆写 |
| A2 丢失 `DensityWithConverter` | ✅ 已消除（随 A1） | 不再覆写 `LocalDensity`，**因此不再需要真机实测**；「Compact 恒等」由结构保证 |
| A3 宽度取「窗口宽」而非「可用宽」 | ✅ 已修 | 新增 `LocalAvailableWidthDp`（`RYScaffold` 的 `BoxWithConstraints` 提供）+ 纯函数 `adaptiveContentGutter()` |
| A4 release notes 缩进 | ✅ 已修 | `release-build.yaml`：改为显式换行拼接，缩进不再是内容 |
| B1 `RYScaffold` 统一限宽 | ✅ 已做 | 一处挂载覆盖 21 个页面；`FeedsPage` / `SettingsPage` 的调用点**已删除**（改动面反而变小） |
| B2 顶部栏未随内容居中 | ⚠️ 部分 | 隐式 `TopAppBar`（20 个页面）+ `FeedsPage` 的两个图标已对齐；显式 topBar 里的标题与 FAB 未动（见 `FORK-NOTES.md` §4） |
| B3 `sizeClass` 无消费者 | ✅ 已解决 | `rememberAdaptiveContentGutter` 用它做 Compact 短路 |
| B4 两套宽度常量 | ✅ 已说明 | `AdaptiveContentMaxWidth` 的 KDoc 写清了为什么是 640 而不是 600/768 |
| B5 基线 `16` 是隐式耦合 | ✅ 已加报警器 | 两个 Preference 的 KDoc 写清来源 + `ListFontBaselineTest`（失败即上游 typography 漂移） |
| B6 键鼠无 hover 反馈 | ✅ 已做 | `Clickable.kt` 两个 modifier 都加了 `collectIsHoveredAsState`（触屏无影响） |
| B7 缺单测 | ✅ 已加 3 个（22 个用例全过、0 跳过） | `AppSizeClassTest`、`AdaptiveContentWidthTest`、`ListFontBaselineTest` |
| B7 附带：测试没进 CI | ✅ 已接 | 上游 `build_commit.yaml` 只 assemble、不编译 `app/src/test/`，`testing.yml` 只在 PR 触发。新增 `.github/workflows/fork-unit-tests.yaml`（`on: push`），并把「测试被跳过」判成失败 |
| 3.4 `FORK-NOTES.md` | ✅ 已建 | 仓库根，rebase 检查单 + 已知限制 + 不要做的事 |
| 3.5 分支重构（`tablet` 分支 + `main` 回上游） | ⏸ 未做 | 属方向性判断，留待决策 |

**三点必须说清楚**：

1. **编译与单测已验证，但验证发生在 CI、不在本机。** 本机没有 JDK、没有 Android SDK
   （`local.properties` 缺失、无 `ANDROID_HOME`），`./gradlew` 完全跑不了。替代验证是：
   `build_commit.yaml` 的 `assembleGithubRelease`（覆盖全部主源集）连续两次绿，
   `fork-unit-tests.yaml` 的 `testGithubReleaseUnitTest` 22 个用例全过、**0 skipped**。
2. **运行时行为仍未验证。** 以上只能证明「能编译、纯函数与基线假设成立」，
   证明不了布局结果。限宽、双栏退化、字号观感、hover 反馈都必须在真机上目视核验，
   清单见 `FORK-NOTES.md` §3。
3. **A2 的"待实测"已不存在**，因为产生它的代码被删掉了。取而代之的新回归项是
   「系统字体 200% 下文本不裁切」——它验证的是"移除覆写后回到平台默认"，见 `FORK-NOTES.md` §3。

---

## 0. 结论摘要

**做得对的部分（不要动）**

- 三层自适应抽象（`AppSizeClass` / `AdaptiveLayoutSpec` / `rememberAdaptiveContentPadding`）分文件、有注释说明动机，**Compact 被刻意设计成恒等变换**。这是本分支最有价值的设计决策——它让手机行为与上游逐字节一致，是改动能否被上游接受的前提。
- 新增 CI 走「纯新增文件」路线（`manual-build.yaml` / `release-build.yaml`），与上游三个 workflow 永不冲突。策略正确。
- 用 `.withFeedsListStyle()` / `.withFlowListStyle()` 扩展函数挂载字号，而不是改 `Text` 的调用形态 —— 挂载点极轻，rebase 时冲突面小。
- 复用 `ExternalFonts.loadReadingTypography` 的进程级缓存（`ExternalFonts.kt:52-53, 108-115`），列表项没有引入字体重复加载。已核验，非问题。

**必须先修的 4 个问题**

| # | 问题 | 位置 | 性质 |
|---|---|---|---|
| A1 | 自适应 `fontScale` 与「列表字号」双重相乘，设置页显示值与实际渲染值不符 | `AdaptiveLayout.kt:95-103` × `ListFonts.kt:45-51` | 行为不一致 |
| A2 | 替换 `LocalDensity` 丢失 `DensityWithConverter`，Android 14+ 非线性字体缩放可能失效 | `AdaptiveLayout.kt:95-103` | 无障碍回归（**待实测**） |
| A3 | 自适应宽度取「窗口宽」而非「可用宽」，在 list-detail 的 pane 内会算错 —— 这是现在无法把限宽推广到其余 21 个页面的阻塞点 | `AdaptiveContentPadding.kt:19-30` | 设计缺陷（尚未触发） |
| A4 | release 说明的多行字符串带 4 空格缩进，GitHub 会渲染成代码块 | `release-build.yaml:70-76` | 显示错误 |

**最高性价比的优化**

把 `rememberAdaptiveContentPadding()` 的宽度来源从 `LocalWindowInfo.containerSize`（窗口宽）改成 `BoxWithConstraints`（**实际可用宽**），然后在 `RYScaffold` 挂载一次 —— 一处改动覆盖全部 21 个未限宽页面（其中 18 个是设置子页），且在 pane 内自动退化为 0。详见 B1。

**上游跟随成本的关键判断**

你的改动触及 **17 个上游文件**，其中 `FeedsPage.kt`、`ArticleItem.kt`、`FilterBar.kt`、`DataStoreExt.kt` 是上游高频改动文件。本报告第 3 节给出把冲突面从 17 收敛到 ~6 的具体路径，以及**把成本降到 0 的唯一路径**（回馈上游）。

---

## 1. 现状盘点

### 1.1 你的 5 个提交

| 提交 | 主题 | 文件 | 增减 | 触及上游文件 |
|---|---|---|---|---|
| `2c0841d0` | 大屏自适应层 | 7 | +267/−3 | 3（`FeedsPage`/`SettingsPage`/`Theme`） |
| `61f1be6d` | FilterBar 与内容列对齐 | 2 | +12/−0 | 2（`FilterBar`/`FeedsPage`） |
| `d1e41ca7` | 发布 APK 为 release asset | 1 | +77/−0 | 0（新文件） |
| `7368ad1b` | 可调列表字体（feeds/flow） | 19 | +434/−9 | 14 |
| `dc68c1ff` | 修复编译 | 8 | +13/−8 | 6 |

**改动性质分类**

- **纯新增**（永不冲突）：`ui/adaptive/*`(3)、`ui/component/ListFonts.kt`、`infrastructure/preference/{Feeds,Flow}{Fonts,TextFontSize}Preference.kt`(4)、`ListFontsPreference.kt`、两个 workflow 文件 = 11 个文件
- **改上游**：`DataStoreExt.kt`、`Preference.kt`、`Settings.kt`、`SettingsProvider.kt`、`Theme.kt`、`FilterBar.kt`、`FeedsPage.kt`、`SettingsPage.kt`、`FeedItem.kt`、`GroupItem.kt`、`ArticleItem.kt`、`StickyHeader.kt`、`FeedsPageStylePage.kt`、`FlowPageStylePage.kt`、`strings.xml`×3 = 17 个文件

**这是一个健康的比例**：新增 11 / 修改 17。修改集中在「既有列表页文本样式」这一类，都是加一行 `.withXxxListStyle()`。

### 1.2 上游已有的平板能力 —— 你尚未利用，且不应该重复建设

上游 **已经**做了 reading 路由的 list-detail 双栏，代码在 `ui/page/adaptive/`（**这个目录名与你的 `ui/adaptive/` 几乎同名，容易被误认为是自己的代码，注意区分**）：

- `AppEntry.kt:72-78` —— `calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())` + `rememberListDetailPaneScaffoldNavigator`
- `AppEntry.kt:127-153` —— `Route.Reading` 内把 `ArticleListReaderPage` 挂上 scaffold
- `ArticleListReadingPage.kt:82-86` —— `isTwoPane` 判定；`:110-118` —— 依据 `NavigationAction` 在 `MediumContentWidth(600.dp)` / `ExpandedContentWidth(768.dp)` 间切换
- 依赖已声明：`material3.adaptive.navigation`、`material3.adaptive.layout`、`navigation3`（`app/build.gradle.kts`）

**上游的宽度体系**（`ui/component/reader/Styles.kt:44-48`）：

```kotlin
val MediumContentWidth = 600.dp
val ExpandedContentWidth = 768.dp
val LocalTextContentWidth = compositionLocalOf { MediumContentWidth }
val LocalImageContentWidth = compositionLocalOf { MediumContentWidth }
```

**结论**：上游的架构是 `Feeds`（单页）→ `[Flow | Reading]`（双栏）。所以当前平板上真正的缺口只有两处：**① 全窗口的单页页面（Feeds / 全设置树）没有限宽；② 文本在平板上偏小。** 你的两个改动正好各自对准一个缺口，方向是对的。剩下的工作是把这两件事做完整、做干净。

---

## 2. 分级问题清单

### A 级 — 正确性 / 风险

#### A1. 自适应 `fontScale` 与「列表字号」双重相乘

**位置**：`ui/adaptive/AdaptiveLayout.kt:95-103`（Expanded 下 `fontScale × 1.2`）× `ui/component/ListFonts.kt:45-51`（按 sp 缩放字号）

`withListStyle` 以「绝对 sp」为输入：

```kotlin
val scale = sizeSp.toFloat() / baselineSp          // baselineSp = 16
fontSize = fontSize.scaledBy(scale)
```

而 `scaledBy` 产出的仍然是 `sp`，最终渲染时会**再乘一次 `LocalDensity.fontScale`**。所以在 Expanded 平板上：

```
实际渲染字号 = 用户设置值 × 1.2 × 系统 fontScale
```

用户在设置页看到「32sp」，实际渲染是 `32 × 1.2 = 38.4sp`（系统 fontScale = 1 时）。**设置项的显示值与真实值不符**，且这个偏差随窗口宽度变化——把平板从横屏（Expanded）转竖屏或缩小分屏（Medium/Compact），字号会跳变。

**建议（推荐方案）**：删掉 `fontScaleMultiplier`，只保留列表字号。

理由不止「消除双重相乘」：

- 用户已经拿到了**显式、可见、可预期**的字号旋钮。一个用户能看见的数字 > 一个他看不见的隐式全局放大。
- 删除后 `LocalDensity` 不再被替换 → 同时消除 A2 的无障碍风险。
- `ProvideAdaptiveLayout` 只剩「提供 spec」一件事 → 抽象变薄，上游审查者更容易接受。
- 补偿：可以在 Expanded 下把 `AdaptiveContentMaxWidth` 放宽（例如 640 → 720dp），让大字有足够行长，这不引入任何行为歧义。

**若坚持要自动放大**：改用 `MaterialTheme(typography = typography.scaledBy(multiplier))`，**不要动 `LocalDensity`**。注意这条路径无法覆盖 `ArticleItem.kt:239` 的硬编码 `.merge(lineHeight = 22.sp)` 等散落的 sp 字面量，需要一并处理。

**改动量**：A 方案 `AdaptiveLayout.kt` 删 ~12 行，`Theme.kt:78` 回滚 1 行。

---

#### A2. 替换 `LocalDensity` 丢失 `DensityWithConverter`（**待实测**）

**位置**：`ui/adaptive/AdaptiveLayout.kt:95-103`

```kotlin
Density(density = density.density, fontScale = (density.fontScale * spec.fontScaleMultiplier)...)
```

`androidx.compose.ui.unit.Density(density, fontScale)` 这个工厂函数返回的是 **`DensityImpl`**，不是 `DensityWithConverter`。Compose 在需要非线性字号换算时（Android 14+ 的非线性字体缩放）会让 `LocalDensity` 持有 `DensityWithConverter`。用 `Density(...)` 覆写会把转换器丢掉。

**可能后果**：系统字体调到很大时，非线性缩放曲线失效，文本尺寸与系统其他应用不一致；极端档位下可能溢出容器。

**我无法在本机验证**（需要 Android 14+ 真机 + 大字号设置）。**请按以下步骤实测**：

1. 系统设置里把字体调到最大档（200%）；
2. 分别在手机（Compact，`fontScaleMultiplier = 1f`）和平板（Expanded）打开 Feeds 页；
3. 与系统「设置」应用的正文大小对比；
4. 若手机上也出现偏差 —— 说明 Compact 并非恒等变换，这直接推翻了「手机行为不变」的前提，属最高优先级。

**这就是 A1 推荐「不要动 LocalDensity」的第二个理由**：即便实测无差异，它也把「Compact 是否真的恒等」变成了一个依赖系统版本的条件命题，而你的整个分叉策略建立在「Compact = 恒等」之上。

---

#### A3. 自适应宽度取「窗口宽」而非「可用宽」—— 推广限宽的阻塞点

**位置**：`ui/adaptive/AdaptiveContentPadding.kt:19-30`

```kotlin
val containerSize = LocalWindowInfo.current.containerSize   // ← 窗口尺寸，不是可用尺寸
return remember(containerSize, density.density) {
    if (containerSize.width > 0) with(density) { containerSize.width.toDp().value.toInt() } else 0
}
```

`LocalWindowInfo.containerSize` 报告的是**窗口**大小。这一点在 `ProvideAdaptiveLayout` 里被正确识别（注释 `AdaptiveLayout.kt:10-18` 明确说「用 `LocalWindowInfo` 而非 `screenWidthDp`，因为后者报告的是屏幕」）——但对 **list-detail 的 pane** 就反过来了：pane 的宽度**小于**窗口宽度，而 `containerSize` 仍然返回窗口宽。

**当前没有触发**，因为只有两个调用点，都不在 pane 内：

- `FeedsPage.kt:236`（LazyColumn）、`FeedsPage.kt:352`（FilterBar）—— `Route.Feeds`，全窗口
- `SettingsPage.kt:74` —— `Route.Settings`，全窗口

**但它是一个明确的陷阱**，也是「为什么现在不能简单地把限宽挂到 `RYScaffold`」的原因。`RYScaffold` 的使用点里包含 **`ui/page/home/flow/FlowPage.kt`**，而 `FlowPage` 正是 `ArticleListReadingPage.kt:142` 挂在 **list pane** 里的那个页面。若直接把 `rememberAdaptiveContentPadding()` 加进 `RYScaffold`：

- 全窗口页面（Feeds / 设置树）：正确
- 双栏模式的 Flow 列：**按 1280dp 窗口算出 320dp 左右内边距，而 pane 实际只有 ~600dp → 内容被压扁**

**建议**：把宽度来源换成 `BoxWithConstraints` 的 `maxWidth`（**真实约束**），而不是 `LocalWindowInfo`（窗口）。

```kotlin
@Composable
fun rememberAdaptiveContentPadding(vertical: Dp = 0.dp): PaddingValues =
    // 读真实可用宽，而非窗口宽：pane 内自动退化为 0
    // 需要一个 Local 来表达 maxWidth，见下方 B1
```

`BoxWithConstraints` 自带一个 `Local`：`androidx.compose.foundation.layout.LocalBoxWithConstraintsScope` 只在其子树内有效，不能跨层读取。所以实际实现是两种之一：

1. **改 `rememberAdaptiveContentPadding` 的调用形态**：改成 `Modifier.adaptiveContentWidth()`（一个 `BoxWithConstraints` 包装的 Modifier），让每个页面自己包一层。
2. **引入自有的 `LocalAvailableWidthDp`**：在每处需要的地方用 `BoxWithConstraints` 采一次，再 provide 下去。

推荐方案 1，因为它不需要新增 CompositionLocal，且「谁用谁包」的语义最清楚。

**验证方式**（务必做）：Expanded 平板 → 进入 Flow 页 → 此时应是双栏 → 确认 Flow 列内容**不加**额外边距，Reading 列内容按 640dp 居中。

---

#### A4. release 说明会渲染成代码块

**位置**：`.github/workflows/release-build.yaml`

```yaml
              --notes "Built from \`${GITHUB_SHA}\` by the **${GITHUB_WORKFLOW}** workflow.

          Signed with the debug keystore committed at \`signature/reader.keystore\`, so it will not
          install over an F-Droid or Google Play build. Uninstall those first."
```

第二段在 shell 双引号内有 10 个前导空格，Markdown 里 4 个空格 = 代码块 → release 页面上这段说明会显示成等宽代码，`**${GITHUB_WORKFLOW}**` 的加粗也不生效，且缩进会被保留。

**修复**：把 notes 拆成单行，或写进临时文件用 `--notes-file`：

```bash
          NOTES="Built from \`${GITHUB_SHA}\` by the **${GITHUB_WORKFLOW}** workflow."$'\n\n'"Signed with..."
          gh release create "$TAG" "${apks[@]}" --title "$TAG" --prerelease --notes "$NOTES"
```

**改动量**：3 行。

---

### B 级 — 平板体验优化（高性价比、低冲突）

#### B1. ★ 统一限宽：一次改动覆盖 21 个页面（**最高优先级优化**）

**核验**：`RYScaffold` 有 **22 个调用点**（`ui/component/base/RYScaffold.kt` 是定义本身）。其中只有 `SettingsPage.kt:74` 加了限宽，**其余 21 个全是全宽**：

```
settings 子页 18 个：
ui/page/settings/{accounts/AccountDetailsPage, accounts/AccountsPage,
  accounts/AddAccountsPage, color/ColorAndStylePage, color/DarkThemePage,
  color/feeds/FeedsPageStylePage, color/flow/FlowPageStylePage,
  color/reading/{BoldCharactersPage, ReadingImagePage, ReadingStylePage,
  ReadingTextPage, ReadingTitlePage, ReadingVideoPage},
  interaction/InteractionPage, languages/LanguagesPage,
  tips/{LicenseListPage, TipsAndSupportPage}, troubleshooting/TroubleshootingPage}
其它 3 个：
ui/page/settings/SettingsPage.kt   ← 已加（唯一的例外）
ui/page/startup/StartupPage.kt
ui/page/home/flow/FlowPage.kt      ← 特殊：它在 list-detail 的 pane 里，见 A3
```

在 1280dp 平板上，这些页面的一行设置项标题在左、描述/开关在右，两端相距超过 1200dp。这是目前**用户最容易感知到、且最没被覆盖**的缺陷。

**建议**：把 `RYScaffold` 的 `content` 包一层自适应宽度约束。**前置条件：先做 A3**（否则 Flow 列会被压扁）。

`RYScaffold.kt:67-79` 现在的结构：

```kotlin
content = {
    val layoutDirection = LocalLayoutDirection.current
    Column(modifier = Modifier.padding(start = ..., end = ...)) {
        Spacer(modifier = Modifier.height(it.calculateTopPadding()))
        content()
    }
}
```

在 `Column` 上叠加水平约束即可。**注意两点**：

1. `RYScaffold` 标注了 `@Deprecated("Use m3 Scaffold instead")`（`RYScaffold.kt:22`），但上游 22 个调用点仍在用它，所以它是**当前有效的收敛点**。改动它等于一次性覆盖所有页面。
2. 上游迟早会把调用点迁到 m3 `Scaffold`。届时这个挂载点会逐个失效。**这是本报告里唯一一个「挂载点有保质期」的改动**，需要在 fork 备注里标注（见 C 节 3.4）。

**风险**：`RYScaffold` 是共享组件，改它影响所有调用点 —— 但**只要 A3 做完**，pane 内自动退化为 0 边距，行为与现在一致。仍建议逐个页面目视验证（附录 A 有清单）。

**收益**：21 个页面一次性覆盖。**成本**：`RYScaffold.kt` ~5 行 + `AdaptiveContentPadding.kt` 重构。

---

#### B2. TopAppBar 未随内容居中

**位置**：`FeedsPage.kt:185-230`

内容被限到 640dp 居中后（`FeedsPage.kt:236`），顶部栏没有跟随：`navigationIcon`（设置图标，`:200-210`）贴窗口左边，`actions`（订阅 `+`，`:211-221`）贴窗口右边。1280dp 平板上两个图标相距 >1200dp，而它们功能上属于同一条 640dp 宽的内容列。

**同类问题**：`SettingsPage`、各设置子页的 `RYScaffold` 顶部返回按钮。

**建议**：把 `TopAppBar` 的**背景保持全宽、内容施加同样的水平内边距**。实现上要包一层 `Box`（`TopAppBar` 自带 `containerColor`，直接加 `padding` 会让背景一起缩进——注意你的 `FilterBar` 注释里已经正确识别了这一点，`FilterBar.kt:33-38`，做法可以照搬：**padding 加在内层，背景留在外层**）。

**若做了 B1**：在 `RYScaffold` 的 `topBar` 槽位统一处理，一次性覆盖所有页面。**改动量**：~10 行。

---

#### B3. `AdaptiveLayoutSpec.sizeClass` 从未被读取

**核验结果**（全仓 grep）：`LocalAdaptiveLayout.current` 只被读取过一个字段：

```
AdaptiveContentPadding.kt:47   LocalAdaptiveLayout.current.contentMaxWidth
```

而 `contentMaxWidth` 在三个 size class 上是**同一个常量**（`AdaptiveLayout.kt:48-58` 都传 `AdaptiveContentMaxWidth`）。所以：

- `AdaptiveLayoutSpec.sizeClass` —— **零消费者**
- `AdaptiveLayoutSpec.contentMaxWidth` —— 消费者存在，但值是常量，等效于直接读 `AdaptiveContentMaxWidth`
- `AppSizeClass` —— 只在 `AdaptiveLayout.kt` 内部用于选 multiplier

**这不一定是错的**（预留接缝是合理设计），但要诚实对待：**一个「每个字段都有人读」的抽象才守得住**。目前这个 spec 有 3 个字段，只有 1 个半在用。

**建议**：不要删 —— 因为 B1（按 size class 决定是否限宽 / 限多宽）正好会用到 `sizeClass`。但请在 B1 落地时让 `sizeClass` 真正参与决策（例如 Compact 直接返回 `PaddingValues(0.dp)` 而不做宽度计算，省掉 `LocalWindowInfo` 读取）。**这样抽象与用法重新对齐。**

---

#### B4. 两套内容宽度常量并存

- 你的：`AdaptiveContentMaxWidth = 640.dp`（`AdaptiveLayout.kt:19`）—— 用于列表页
- 上游：`MediumContentWidth = 600.dp` / `ExpandedContentWidth = 768.dp`（`Styles.kt:44-45`）—— 用于阅读页

同一个 app 里，列表内容列 640dp、阅读正文列 600 或 768dp。切换页面时内容宽度会变。

**建议**：在 `AdaptiveLayout.kt` 里把 640 的来源写清（或者干脆改成 600 与上游 `MediumContentWidth` 对齐）。**零冲突**（只改自己的文件），但需要你在平板上目视确认 640 这个值是不是刻意选的——如果是，就在注释里写明为什么与上游不同，避免将来自己或上游 reviewer 困惑。

---

#### B5. 字号基线 `16` 是硬编码常量，依赖 Material 3 默认值

**位置**：`FeedsTextFontSizePreference.kt:22`、`FlowTextFontSizePreference.kt:22`

```kotlin
const val baseline = 16
```

**已核验基线今天是对的**：`GroupItem.kt:70` 和 `ArticleItem.kt:236` 用的都是 `MaterialTheme.typography.titleMedium`；`Type.kt:28-38` 的 `SystemTypography` **只**覆写了 `bodySmallEmphasized`（11.sp），其余全部走 Material 3 默认，而 M3 默认 `titleMedium.fontSize = 16.sp`。✓

**但这是隐式耦合**：基线 16 是从库的默认值反推出来的，代码里没有任何东西把它绑住。上游升 Compose BOM（现在是 `composeBomAlpha = "2025.10.01"`）或 Material3 调整默认 typography 时，`titleMedium` 可能不再是 16sp → **所有列表字号整体偏移，且没有任何编译错误或测试失败**。这正是你担心的那类「上游变动静默破坏我的改动」。

**建议**：把基线与实际样式显式绑定，任选一种：

1. 在 `ListFonts.kt` 里改为运行时读取真实基线（需要放弃 `const`，改用 `Composable` 内读取 `MaterialTheme.typography.titleMedium.fontSize`）；
2. 至少加一条注释，把 `baseline = 16` 与 `GroupItem.kt:70` / `ArticleItem.kt:236` 的双向引用写清楚（现在 `FeedsTextFontSizePreference.kt:24-27` 的注释只说了「baseline 是恒等变换」，没说 16 从哪来）;
3. 加一个**断言测试**（见 B7）——成本最低，收益最大。

**改动量**：注释 2 行；或测试 1 个文件。

---

#### B6. 键鼠 / 触控笔没有 hover 反馈

**位置**：`ui/interaction/Clickable.kt`

`alphaIndicationClickable` / `alphaIndicationSelectable` 只采集 `collectIsPressedAsState`，没有 `collectIsHoveredAsState`。

平板外接键鼠（含华为平板的磁吸键盘 / M-Pencil）时，鼠标悬停在列表项、图标按钮上**没有任何视觉反馈**。这是「大屏 = 更接近桌面」场景里最基础的可用性缺失。

**建议**：增加 hover 到这两个 Modifier ——

```kotlin
val isHovered by interactionSource.collectIsHoveredAsState()
val animatedAlpha by animateFloatAsState(
    when { isPressed -> .5f; isHovered -> .85f; else -> 1f },
    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
)
```

**风险**：这是**共享组件**，所有调用点的观感都会变（触屏设备上 hover 恒为 false，不受影响；只有接鼠标时才有效果）。属「不改变功能、只增加反馈」的改动，符合你的约束。**改动量**：~6 行。

上游的其它交互组件（`FeedbackIconButton`、`RYSwitch`、`SwipeableActionsBox`）也值得单独检查是否有 hover 态。

---

#### B7. 缺单元测试 —— 加一个测试是本次审计里 ROI 最高的动作

上游测试目录只有 5 个测试文件（`app/src/test` + `app/src/androidTest`）。`AppSizeClass.fromWidthDp` 是**纯函数、无依赖、零冲突**（新增文件），加一个测试的边际成本接近零，但能守住两件事：

```kotlin
// app/src/test/java/me/ash/reader/ui/adaptive/AppSizeClassTest.kt
class AppSizeClassTest {
    @Test fun `width 0 resolves to Compact so unknown size never adapts`() {
        assertEquals(AppSizeClass.Compact, AppSizeClass.fromWidthDp(0))
    }
    @Test fun `breakpoints follow material window size classes`() {
        assertEquals(AppSizeClass.Compact, AppSizeClass.fromWidthDp(599))
        assertEquals(AppSizeClass.Medium,  AppSizeClass.fromWidthDp(600))
        assertEquals(AppSizeClass.Medium,  AppSizeClass.fromWidthDp(839))
        assertEquals(AppSizeClass.Expanded, AppSizeClass.fromWidthDp(840))
    }
    @Test fun `every enum entry is reachable, so no bucket is dead`() {
        val reached = listOf(0, 600, 840).map(AppSizeClass::fromWidthDp).toSet()
        assertEquals(AppSizeClass.entries.toSet(), reached)
    }
}
```

再加一个**基线守卫**测试（对应 B5）：

```kotlin
@Test fun `list font baseline matches material titleMedium, so upstream bumps surface here`() {
    val titleMedium = Typography().titleMedium.fontSize
    assertEquals(FeedsTextFontSizePreference.baseline.sp, titleMedium)
    assertEquals(FlowTextFontSizePreference.baseline.sp, titleMedium)
}
```

第二个测试的价值超出「验证」本身：**它把「上游改了 typography 默认值」从一次静默的显示错误，变成一次 CI 红灯。** 这正是你需要的「上游变动时能被通知」机制。

**改动量**：2 个文件（都是新增）。

---

### C 级 — 可选 / 高风险（需你决策）

以下三项都**会改变现有行为**，与「不修改原有功能」的前提冲突，因此不作为建议，仅列出供判断。

#### C1. `MainActivity` 的 `launchMode="singleInstance"`（中风险）

**位置**：`app/src/main/AndroidManifest.xml`

`singleInstance` 让 `MainActivity` 独占一个 task。在多窗口 / 分屏 / 桌面模式（Samsung DeX、华为平板 PC 模式）下，这种行为与系统的大屏多任务模型相冲突；从其它应用点击 RSS 链接或分享进入时，可能把整个 task 切进分屏，表现不自然。Android 官方对大屏应用的建议是避免 `singleInstance`。

**改成 `singleTop`** 通常能保留「分享/深链回到同一实例」的原始意图，同时改善多窗口行为。**但需要验证**：`MainActivity.kt:150-212` 的 `NewIntentHandlerEffect` 逻辑建立在「intent 会回送到已有实例」上，`singleTop` 是否总能满足需要实测（尤其是从 widget 点击进入的场景）。

**请评估**：上游选 `singleInstance` 是否有必然原因。如果有 PR/issue 记录，沿用更安全。

#### C2. 显式大屏声明（中风险）

```xml
<activity android:name=".infrastructure.android.MainActivity"
    android:resizeableActivity="true"          <!-- 新增 -->
    android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|density|uiMode"  <!-- 新增 -->
    ... />
```

- `resizeableActivity="true"`：`targetSdk = 34` 下默认已是 true，显式声明只是明确表态（当前 `targetSdk = 34`、`compileSdk = 36`）。
- `configChanges`：**这会改变 Activity 的重建行为**。现在是旋转/分屏变化 → Activity 重建 → `rememberNavBackStack`（`MainActivity.kt:139`）恢复 back stack，但 `remember { isFirstLaunch }` / `remember { initialPage }` / `remember { startDestination }`（`:105-137`）会重新求值。加 `configChanges` 后不重建，状态更稳，但也意味着**上游依赖重建的假设可能失效**。属实质性行为改动，不建议作为平板优化的一部分顺手做。

**前瞻提示**：Android 16（API 36）起，`targetSdk = 36` 的应用**必须**支持大屏自适应，系统会忽略 `screenOrientation` 限制、强制应用可缩放。你目前 `targetSdk = 34` 不受影响，但上游升到 36 时，**大屏适配从「可选优化」变成「硬性要求」，否则会被 letterbox 或被系统强制拉伸**。这反而说明你现在做的这件事是对的方向，且应该争取进上游。

#### C3. Feeds 页多列网格（高风险）

Expanded 下把 `FeedsPage` 的 `LazyColumn` 换成多列网格，进一步利用横向空间。**不建议**：需要重构列表结构，而 `FeedsPage.kt` 是上游高频改动文件（`cf8515c0`、`eca65050` 都改过它），冲突风险远超收益。

#### C4. 三栏 `[Feeds | Flow | Reading]`（高风险）

最贴近平板语义的形态（对应 Material 3 的三窗格）。但需要重写 `AppEntry.kt:110-278` 的 nav3 路由组织 —— 这是上游**正在演进**的核心文件（`ui/page/adaptive/` 整个目录都是新的），改动等于和上游赛跑。**不建议**。

---

## 3. Fork 维护策略 —— 降低跟随上游的成本

### 3.1 当前冲突面盘点

你触及的 17 个上游文件中，**冲突风险分级**：

| 风险 | 文件 | 依据 |
|---|---|---|
| **高** | `FeedsPage.kt`、`ArticleItem.kt` | 上游近期改过（`cf8515c0` 加滚动指示器、`eca65050` 首页 RSS 发现）；`FeedsPage.kt` 你改了 2 处 |
| **中** | `FilterBar.kt`、`DataStoreExt.kt`、`Theme.kt`、`FlowPageStylePage.kt`、`FeedsPageStylePage.kt` | 上游改过 `FilterBar`（`cf8515c0` style tweaks）；`DataStoreExt.kt` 是集中式注册表，上游加任何偏好都会碰它 |
| **低** | `FeedItem.kt`、`GroupItem.kt`、`StickyHeader.kt`、`Settings.kt`、`SettingsProvider.kt`、`Preference.kt`、`strings.xml`×3 | 单行改动、文件稳定 |

**`DataStoreExt.kt` 是结构性风险点**：每个新偏好要在**三处**重复注册（`:146-170` 的常量、`:316-340` 的常量、`:416-460` 的两个 map 条目）。你加了 4 个键 → 引入了 24 行样板，且上游每加一个新偏好都会在同一区域产生相邻冲突。

**可选的收敛**：把 4 个键合并为 2 个（`listFonts` / `listTextFontSize`，用页面维度区分），样板减半，冲突面减半。**代价**：已发布版本的用户偏好数据需要迁移（旧键值 → 新键值），且已发布的 `tablet-preview` release 里的用户会丢设置。如果还没有真实用户，就合并；如果有，保留现状。

### 3.2 收敛挂载点

目标是：**上游改动发生时，需要人工解冲突的文件尽可能少**。

| 现在的挂载方式 | 分散在 | 建议收敛到 |
|---|---|---|
| 字号样式：`.withFeedsListStyle()` / `.withFlowListStyle()` | 6 个上游文件（`FeedItem`/`GroupItem`/`ArticleItem`/`StickyHeader` + 2 个 StylePage） | 已经足够轻（每次 1 行），**保持现状**。这是「最小侵入」的正确形态，不要为了「集中」而去重构列表项 |
| 限宽：`rememberAdaptiveContentPadding()` | `FeedsPage`(2 处)、`SettingsPage`(1 处) | **`RYScaffold` 一处**（B1）—— 从 3 处收敛到 1 处，且覆盖 21 个页面 |
| 顶部栏居中 | `FeedsPage` | 同 B1，随 `RYScaffold` 收敛 |
| 偏好注册 | `DataStoreExt.kt` 三处 × 4 键 | 见 3.1 的可选合并 |

做完 B1 后，上游文件改动数可以从 **17 → 约 12**，且剩下的都是单行改动。

### 3.3 把改动回馈上游 —— 把成本降到 0 的唯一路径

**这是本报告最重要的战略建议。**

你的自适应层有一个罕见的性质：**`Compact` 被设计成恒等变换**（`AdaptiveLayout.kt:43-48` 的注释明确写了这一点：「A phone must render identically to upstream, otherwise this whole layer becomes a behavioural fork that upstream has a reason to reject」）。这意味着：

- 手机用户看不到任何变化 → 对上游而言是**零风险改动**
- 平板/桌面用户获得明确改善 → 符合上游正在投入的方向（上游自己已经在做 `ui/page/adaptive/`）
- Android 16 起系统强制大屏适配（见 C2）→ 上游**迟早必须做这件事**

同时，上游 `ui/page/adaptive/` 的存在说明**维护者是在意平板体验的**，只是还没覆盖到「全窗口单页页面」和「列表字号」。

**建议动作**：

1. 先完成 A1、A2、A3、A4（把问题修掉）
2. 再做 B1、B2（把覆盖做完整）
3. 把「自适应限宽 + 列表字号」拆成 **2–3 个独立的小 PR** 提交上游（不要一个大 PR）：
   - PR 1：`ui/adaptive/` 基础设施 + 在 `RYScaffold` 挂载限宽（纯新增 + 1 处改动）
   - PR 2：`ListFonts` 列表字号体系（含 2 个 `StylePage` 的新设置项）
   - PR 3：`TopAppBar` 居中 + CI（可选）

**如果上游合并了，你的 fork 冲突面直接归零**，剩下只需要维护 `strings.xml` 的本地化差异。这条路的价值远高于任何代码层面的「优化」。

**注意**：`ListFontsPreference` 用 `-1` 作 `Default` 的哨兵值（`ListFontsPreference.kt:18`）。提 PR 时上游可能倾向于不引入哨兵（改用 `Int?` 或单独的 `Boolean` 开关），这个设计分歧要在提 PR 前想清楚，否则会被要求返工。

### 3.4 建 `FORK-NOTES.md` —— 一次投入，长期受益

现在你的 5 个提交里，**为什么这样做**的理由散落在代码注释里（而且写得不错：`AdaptiveLayout.kt:43-48`、`AdaptiveContentPadding.kt:32-43`、`FilterBar.kt:33-38`、`release-build.yaml` 顶部注释）。但没有一个地方汇总：

- 每个自有改动挂在哪个文件、为什么挂在那里
- rebase 上游后**必须人工核验**的清单
- 哪些挂载点有保质期（例如 `RYScaffold` 被弃用后 B1 会失效）

建议在仓库根建 `FORK-NOTES.md`（纯新增文件，永不冲突），内容就是本报告第 2、3 节 + 附录 A，改造成「rebase 检查单」。这样每次跟随上游时，你不需要重新读一遍 795 行 diff。

### 3.5 rebase 流程建议

当前 5 个提交直接落在 `main` 上（`main` = `dc68c1ff`，跟踪 `origin/main`），与上游历史线性混合。建议：

```bash
# 一次性把自有改动收拢成独立分支
git branch tablet dc68c1ff
git branch -f main d2b979cc          # main 回到纯上游状态

# 之后跟随上游
git fetch upstream main
git rebase --onto upstream/main d2b979cc tablet
# 或交互式挑拣：git rebase -i --onto upstream/main d2b979cc tablet
```

好处：`main` 干净地跟踪上游，自有改动集中在 `tablet` 分支，冲突只会在 rebase 时出现一次，而不是散落在每次 `pull` 里。**这一步不影响工作区内容**（不动任何文件），可以放心做。

---

## 4. 建议的执行顺序

| 顺序 | 动作 | 类型 | 改动量 | 风险 |
|---|---|---|---|---|
| 1 | A2 实测：系统最大字号下的非线性缩放是否失效 | 验证 | 0 | — |
| 2 | A1：删除自适应 `fontScale`（或改为 typography 缩放） | 修 bug | ~15 行 | 低 |
| 3 | A3：宽度来源改为 `BoxWithConstraints` | 重构 | ~30 行 | 中 |
| 4 | A4：修 release notes 的缩进 | 修 bug | 3 行 | 无 |
| 5 | B7：加 2 个单测（含 typography 基线守卫） | 测试 | 2 个新文件 | 无 |
| 6 | B1：在 `RYScaffold` 挂载限宽（**依赖 3**） | 优化 | ~5 行 | 中 |
| 7 | B2：顶部栏随内容居中 | 优化 | ~10 行 | 低 |
| 8 | B3/B4/B5：抽象对齐、宽度常量、基线注释 | 整理 | ~10 行 | 无 |
| 9 | B6：hover 反馈 | 优化 | ~6 行 | 低 |
| 10 | 3.3：拆 PR 提交上游 | 战略 | — | — |

第 1–5 项是**必做**，合计不到 60 行改动。第 6–7 项是**用户可感知的体验提升**。第 8–9 项是**可做可不做**。

C1–C4 不在建议范围内，需要你单独判断。

---

## 附录 A：验证清单

改完 A3 + B1 后，逐项在真机/模拟器上核验（模拟器可用 Android Studio 的 Resizable 设备，手动拖动窗口宽度跨越 600 / 840dp 断点）：

- [ ] **手机竖屏（Compact）**：Feeds 页、Flow 页、阅读页、设置页 —— 与改动前逐像素一致（这是「不改变原有功能」的核心验证项）
- [ ] **平板竖屏（Medium，约 800dp）**：Feeds 页内容居中，左右边距约 (800−640)/2 = 80dp；顶部栏图标位置合理
- [ ] **平板横屏（Expanded，约 1280dp）**：同上，边距约 320dp；设置页各级子页内容均居中
- [ ] **平板 + Flow 页双栏（关键）**：Flow 列内容**不额外缩进**（pane 内退化为 0），Reading 列按 640dp 居中
- [ ] **拖动窗口跨越断点**：无跳变异常、无崩溃
- [ ] **列表字号**：设为 10sp / 16sp / 32sp 三档，观感单调变化；设为 16sp 时与上游外观一致（恒等验证）
- [ ] **列表字体**：选 `External` 时与阅读页外部字体一致；选 `Default` 时无变化
- [ ] **系统最大字号 + 无障碍**：系统字体 200% 时无文本裁切（重点验证 A2）
- [ ] **外接鼠标**：列表项悬停有反馈（B6 之后）
- [ ] **深色 / AMOLED 主题、RTL 语言（阿拉伯语）**：限宽的内边距方向正确

## 附录 B：本次审计用到的基线事实

| 项 | 值 |
|---|---|
| `compileSdk` / `targetSdk` / `minSdk` | 36 / 34 / 26 |
| Kotlin / AGP / Compose BOM | 2.2.0 / 8.13.0 / 2025.10.01（alpha） |
| 代码规模 | 382 个 `.kt`，42,724 行；357 个 `@Composable` |
| 布局方式 | **100% Compose**（`res/` 下无 `layout` 目录） |
| 本地化 | 54 个 `values-*` 目录 |
| 单元测试 | 5 个文件（上游） |
| 上游已有的平板能力 | `ui/page/adaptive/` + `material3.adaptive.*` + `navigation3`；仅覆盖 `Route.Reading` |
| 上游大屏相关依赖版本 | `material3Adaptive = 1.3.0-alpha06`、`nav3 = 1.0.0` |
