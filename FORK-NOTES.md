# FORK-NOTES.md — 跟随上游的检查单

这个 fork 相对 `upstream/main` 有两类改动：**平板适配**（本文档）和 **CI**（`manual-build.yaml`、
`release-build.yaml`、`fork-unit-tests.yaml`、`fork-auto-release.yaml`，纯新增文件，永不冲突）。

跟随上游时不要重读 diff。按下面的顺序做，每一步都有明确的"看什么、为什么"。

- **基线**：上游合并点 `d2b979cc`
- **本文档更新于**：2026-09-24（新增 **§9 上游友好度**、本次优化清单；§1 补挂载点；**§2.8 字体设置的两层结构**与 §9.6 的落盘教训；**§9.3 平板缩放与字体收尾**、§2.2/§2.3 的缩放倍率与恒等约束、§9.6 行尾结论按平台更正；**§9.8 触控目标与列表内边距**；**§9.9 界面字号（设置页 + 对话框）**，含 §2.2 的 `LocalDensity` 不能跨对话框这一条）

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
| "全部已读"按钮位置 | `FlowPage.kt` 的 `topBar.actions`（Top）/ `bottomBar`（Bottom），按钮本体共用 `MarkAsReadIconButton` | 由偏好 `markAsReadButtonPosition` 决定，**默认 Bottom** = 工具栏左侧 |
| "全部已读"条件条 | `FlowPage.kt` 的 `bottomBar` 里的 `Column` | 从 content 顶部移到 `FilterBar` **之上**，向上展开（见 §2.7） |
| "全部已读"的**设置行** | `ui/page/settings/color/flow/FlowPageStylePage.kt` 的「顶部栏」分区 | 位置 + 免确认**两行必须相邻**；曾经被拆到两个分区、相隔 140 行导致没人找得到（见 §2.7） |
| 新增偏好 | `ui/ext/DataStoreExt.kt` + `preference/{Settings,Preference,SettingsProvider}.kt` | 加一项要同时改 5 个地方，见 §2.6 |
| 字体设置（每页两行） | 信息流：`ListFonts.kt` 的 `withFlowTitleStyle()` + `ArticleItem.kt` 标题；阅读页：`reading/Metadata.kt` 的 `titleFontFamily` | 每页 = **基础行 + 标题覆盖行**，覆盖行的「跟随」默认值即回落到基础行（见 §2.8） |
| 打开文章自动拉全文 | `ArticleListReaderViewModel.kt` 的 `isFullContentOnOpen()` / `renderContent()` | 与订阅源自带的 `feed.isFullContent` 是**或**关系，但失败处理故意不同（见 §2.8） |
| 图标解码尺寸 | `ui/component/FeedIcon.kt` 的私有常量 `FEED_ICON_DECODE_SIZE` | 不传 `size` 会落到 `RYAsyncImage` 的默认 `Size.ORIGINAL`，即按原始分辨率解码 |
| 已读状态的重组范围 | `ui/page/home/flow/ArticleList.kt` 的 `rememberIsUnread()` | `diffMap` 是 `SnapshotStateMap`，直接读记录的是 map 级依赖；包一层 `derivedStateOf` |
| Widget 任务入队 | `domain/service/WidgetUpdateWorker.kt` | 一次性任务改 `enqueueUniqueWork(KEEP)`；预览守卫从实例字段改成进程级标记 |
| 抓取层（UA / 字符集） | `infrastructure/di/OkHttpClientModule.kt`、`infrastructure/rss/RssHelper.kt` | 来自上游 open PR，见 §9.2 |
| 平板缩放（图标 / 控件高度） | `ui/adaptive/AdaptiveSizing.kt` 的 `adaptiveSize()` + 15 个调用点 | **Compact 恒等**；只放大字形与承载它的栏高，见 §2.2 / §2.3 |
| FilterBar / 搜索栏跟随页面字号 | `ui/component/FilterBar.kt` 的 `labelStyle` 参数、`flow/SearchBar.kt` 的 `textStyle` 参数 | 默认值与上游逐字节等价，调用点可原样不传 |
| 两页**各自**的自定义 TTF | `ui/ext/ListExternalFonts.kt` 的 `Slot.Feeds` / `Slot.Flow` | 不新增偏好键，复用 `ListFontsPreference.External` |
| 平板触控目标 | `ui/adaptive/AdaptiveSizing.kt` 的 `Modifier.adaptiveIconButtonContainer()` + 2 个调用点 | **手机档一个尺寸都不传**；传了会把 48dp 压小，见 §2.2 / §9.8 |
| 列表行高与内边距 | `GroupItem.kt`(5) / `FeedItem.kt`(4) / `ArticleItem.kt`(10) 处内边距，各包一层 `adaptiveSize()` | 其中 `ArticleItem` 的 `start = 30.dp` 是给 `FeedIcon` 预留的缩进，属**正确性**而非美观，见 §9.8 |
| 界面字号（设置页） | `ui/page/nav3/SettingsNavEntry.kt` 的 `settingsNavEntry()` + `AppEntry.kt` 19 处调用点 | 只包设置类路由；`Feeds`/`Reading`/`Startup`/`else` 保持裸 `NavEntry`，见 §9.9 |
| 界面字号（对话框） | `ui/component/base/RYDialog.kt` 一处 `ProvideUiTextScale { … }` | 覆盖全部 22 处对话框；另有 5 处直调 `AlertDialog` 已改走 `RYDialog(visible = true, …)`，见 §9.9 |
| 界面字号的缩放算术 | `ui/theme/UiTextScale.kt` 的 `scaledTypography()` / `uiTextScaleSp()` | **100% 恒等**（返回同一个 `Typography` 实例）；`Typography.copy` 的 30 槽位必须显式传，见 §2.2 / §9.9 |
| 界面字号偏好 | `infrastructure/preference/UiTextScalePreference.kt` | 范围 **100–150**，默认 100。注册点同 §2.6 |
| 绕过 typography 的硬编码 sp | `SettingItem.kt` / `SelectableSettingGroupItem.kt` / `Banner.kt` 各 1 处 `.copy(fontSize = 20.sp)` → `uiTextScaleSp(20.sp)` | 这三处**不读** typography 槽位，重建 typography 覆盖不到，见 §9.9 |

**新增文件（永不冲突）**：`ui/adaptive/{AdaptiveLayout,AdaptiveContentPadding,AppSizeClass,AdaptiveSizing}.kt`、
`ui/component/ListFonts.kt`、`ui/ext/ListExternalFonts.kt`、
`ui/theme/UiTextScale.kt`、`ui/page/nav3/SettingsNavEntry.kt`、
`infrastructure/preference/{Feeds,Flow}{Fonts,TextFontSize}Preference.kt`、
`ListFontsPreference.kt`、`MarkAsReadButtonPositionPreference.kt`、
`MarkAllAsReadWithoutConfirmPreference.kt`、`TitleFontsPreference.kt`、
`{Flow,Reading}TitleFontsPreference.kt`、`ReadingAutoFullContentPreference.kt`、
`UiTextScalePreference.kt`、
`app/src/main/baseline-prof.txt`、四个 workflow、
6 个单测（`ListFontBaselineTest`、`AdaptiveContentWidthTest`、`AppSizeClassTest`、
`AdaptiveScaleTest`、`OkHttpClientModuleTest`、`UiTextScaleTest`）。

---

## 2. 同步上游后逐项核验（不要跳；merge 与 rebase 都适用）

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
      → 报警器是 `ListFontBaselineTest`，由自有的 `fork-unit-tests.yaml` 在**每次 push 时**运行
      （上游的 `testing.yml` 只在 PR 触发，本 fork 不开 PR，靠不住——见 §2.4）。
      测试**跳过**（不是通过）说明 guard 没生效，不等于"没问题"；该工作流会把跳过判成失败。
- [ ] `GroupItem.kt`（群组名）与 `ArticleItem.kt`（文章标题）是否仍用 `titleMedium`。
      若改成别的样式，baseline 要跟着改。
- [ ] `ui/component/ListFonts.kt` 的 `sizeSp / baselineSp` 是否仍是唯一缩放入口。
- [ ] `ui/component/reader/Styles.kt` 的 `MediumContentWidth`(600) / `ExpandedContentWidth`(768) 是否仍是 600/768。
      自有的 `AdaptiveContentMaxWidth = 640.dp` 是为了**夹在两者之间**才选的；上游若改成同一档，
      可以考虑统一，但**不要**顺手改成 600（列表行比正文段落耐受更宽）。
- [ ] **缩放倍率三档 `1f / 1.15f / 1.25f`**（`ui/adaptive/AdaptiveLayout.kt` 的 `AdaptiveScaleCompact/Medium/Expanded`）。
      `Compact` 必须**恰好是 `1f`** —— 这是「手机端与上游逐像素一致」的唯一保证，也是这套改动能被上游接受的前提。
      改成 `1.05f` 之类会**编译通过、发布、并静默改变所有手机端**，没有别的测试会报警。
      报警器是 `AdaptiveScaleTest`；`Medium` 档也**不能省**，8.8" 平板竖屏约 800dp 落的就是它。
- [ ] `ui/adaptive/AdaptiveSizing.kt` 的 `scaledDp(base, scale)` 是否仍是唯一的缩放算术入口。
      `adaptiveSize()` 只是它的 `@Composable` 包装 —— 纯函数才能被单测钉住，别把算术挪回包装里。
- [ ] **`Modifier.adaptiveIconButtonContainer()` 在手机档必须原样返回 `this`**（`ui/adaptive/AdaptiveSizing.kt`）。
      `IconButton` 的修饰符链是 `modifier.minimumInteractiveComponentSize().size(40.dp)`，**调用方传进来的
      `modifier` 落在最前面**，因此外部传的 `size` 会压住 `minimumInteractiveComponentSize()` 保留的
      **48dp**——即把触控目标从 48dp **缩小**到我们传的值。手机档传 `48.dp` 同样是错的（等于把平台的保留值
      写死成常量），必须什么都不传。平板档的 `maxOf(adaptiveSize(40.dp), 48.dp)` 也是被这一点逼出来的：
      40dp × 1.15 = 46dp 仍然小于 48dp。
      Material 自己的 KDoc 就写着这条约束（"it must come before any size modifiers on the element that
      might limit its constraints"）。**这是"改进"最容易变成退步的一处。**
- [ ] **`LocalDensity` 跨不过对话框边界，`LocalTypography` 可以。** 这是选「重建 typography」而不是
      「改 `LocalDensity.fontScale`」的**唯一原因**，也是最容易在重构中被"优化"掉的一条。
      `DialogLayout` 是个带 parent composition context 的 `AbstractComposeView`，它在自己的
      `ProvideAndroidCompositionLocals` 里**重新提供** `LocalDensity` / `LocalContext` /
      `LocalConfiguration`（值取自 dialog 自己的 window）→ 任何从外层传进来的 `fontScale`
      进不了对话框。而 `LocalTypography` **不在被重提供之列**；M3 的 `AlertDialog` 又用
      `ProvideContentColorTextStyle` 把 title / text 槽位接到 `typography.headlineSmall` / `bodyMedium`，
      所以对话框内的 `Text` 会跟随我们重建的 typography。
      → 报警器：**没有**。若哪天有人把 `ProvideUiTextScale` 的实现换成改 `LocalDensity`，
      编译通过、设置页正常、**对话框静默不跟随**。真机看一眼「关于」对话框就能发现。
- [ ] **`Typography.copy` 省略的槽位从 M3 默认值填充，不是从接收者。** 所以 `scaledTypography()`
      必须**显式列出全部 30 个槽位**（含 `*Emphasized` 那 8 个）。漏一个，那个槽位会**回落到 M3 默认值**，
      而不是保持上游/主题里覆写的值 —— 表现为"某个字号突然变小"，且只在非 100% 时出现。
      `ui/theme/Type.kt` 的 `applyFontFamily` 是同一份 30 槽位清单，**两处必须同步改**。
      → 报警器：`UiTextScaleTest` 里那条 `the emphasized slots grow too, so a dropped line cannot hide
      behind the plain ones`（专门钉 `*Emphasized`，因为最容易漏）。
- [ ] **`ProvideUiTextScale` 必须是幂等的（重算，不是相乘）。** 设置页路由与对话框是两个会**重叠**的
      作用域（对话框挂在某个设置页上）。实现靠私有的 `LocalUnscaledTypography`
      （`staticCompositionLocalOf<Typography?> { null }`）捕获**未缩放**的 typography，
      嵌套时从捕获值重建而非在上一次结果上再乘一遍。
      → 别用「标记位」实现幂等：对话框是独立 window，标记位在它里面不可见。
- [ ] **列表页 / 阅读页必须保持不缩放。** `ArticleItem` / `FeedItem` 的文字是
      `MaterialTheme.typography.<slot>.withFlowListStyle()`，而 `withListStyle` 读的就是 `fontSize` /
      `lineHeight` 再按 `sizeSp / baselineSp` 缩放。全局换 typography 会让列表字号**乘两遍**
      （一遍来自 typography，一遍来自列表偏好）。
      → 所以作用域**刻意**限定在 19 条设置路由 + `RYDialog`，不是包 `MaterialTheme`。
      同理 `ui/page/settings/color/{feeds,flow,reading}` 里 4 个预览调用点包了 `ProvideUnscaledUiText { … }`，
      否则预览会撒谎（预览里的列表/阅读样式本就不跟随界面字号）。

### 2.3 限宽的行为边界

- [ ] `AdaptiveLayoutSpec.Compact` 仍是「不做任何适配」：gutter 恒为 0、`scale` 恒为 `1f`
      （`scaledDp` 在 `scale == 1f` 时直接返回 `base` 本身，连乘法都不做）。
      **手机端必须与上游逐像素一致**，这是本改动能被上游接受、也是它不成为行为分叉的前提。
- [ ] `adaptiveContentGutter()` 对 `Dp.Unspecified` / `Dp.Infinity` 返回 `0.dp`。
- [ ] `AppSizeClass.fromWidthDp()` 仍从不抛异常（负数、0 都落 `Compact`）。
- [ ] pane 宽度 < 640dp，所以 `FlowPage` 在双栏里**不被缩进**。若上游把 pane 加宽到 ≥640，
      会出现额外内边距——那是设计如此，但要确认观感可接受。

---

### 2.4 单测在哪里跑（容易误判，别指望错地方）

| 触发 | 工作流 | 跑什么 | 跑不跑单测 |
|---|---|---|---|
| push | `fork-unit-tests.yaml`（自有） | `testGithubReleaseUnitTest` | ✅ **这是本 fork 的 guard** |
| push | `build_commit.yaml`（上游） | `assembleGithubRelease` | ❌ 只编译主源集 |
| push | `fork-auto-release.yaml`（自有） | assemble + 打 tag + 挂 release | ❌ |
| 开 PR | `testing.yml`（上游） | `testGithubReleaseUnitTest` | ✅ |
| 手动 | `manual-build.yaml`（自有） | assemble 四种 flavor | ❌ |

**上游的两个工作流都不够用**：`testing.yml` 只在 `pull_request` 触发，而本 fork 是**本地 rebase**，
不开 PR；`build_commit.yaml` 虽然跟着 push 跑，但只 assemble，`app/src/test/` 既不编译也不运行。
所以在 `fork-unit-tests.yaml` 之前，push 到 main 时单测一次都没跑过——3 个新测试连能不能编译都不知道。

`fork-unit-tests.yaml` 补的就是这个洞，而且多做了一件事：**把「测试被跳过」当成失败**。
`ListFontBaselineTest` 在纯 JVM 上加载不到 Compose typography 时会 `Assume` 跳过，而跳过的任务
是绿的——这正好是我最需要看见的状态，所以它不能是绿的。

**另一个理由**：本机没有 JDK / Android SDK，`./gradlew test` 手动也跑不了。CI 是唯一的执行环境。

**实测基线（2026-09-23，`1815cee8`）**：`fork-unit-tests.yaml` 首次运行即绿，报告里
**22 个单测、0 failures、0 skipped**，三个自有测试类全部执行：
`ListFontBaselineTest`(3) / `AdaptiveContentWidthTest`(5) / `AppSizeClassTest`(4)。
**重点是 `ListFontBaselineTest` 没有 skip** —— 说明在纯 JVM 单测里能加载 Material 3 的 `Typography`，
`titleMedium = 16sp` 这条假设在当时的 Compose 版本上被实测确认，guard 是活的。
以后 rebase 完看这个工作流：**它绿且没有 skip 步骤失败，才说明字号基线没被动过。**

---

### 2.5 改顶部栏时必须知道的陷阱（会让按钮凭空消失）

`FeedbackIconButton(modifier = ...)` 的 `modifier` **不是加在按钮上的**，是加在里面的 `Icon` 上的，
而那个 `Icon` 位于一个固定 40dp 的 `IconButton` 内。往这个 `modifier` 上加 `padding`，只要 padding
宽过 40dp，`Icon` 收到的约束就被压到 0，图标宽度即 0 —— **看不见，但仍然占位、仍然可点**。

`v0.16.2-tablet.3` 就是这样丢掉了 Feeds 的齿轮按钮和"订阅"按钮：gutter 在 Compact 下恒为 0，
所以手机上一切正常，而窗口宽过约 720dp 之后图标宽度归零。**这类 bug 只在平板上出现，手机回归测不出来。**

正确写法是**给外面的容器加内边距**，不要碰图标自己的 `modifier`：

```kotlin
navigationIcon = {
    Box(modifier = Modifier.padding(start = adaptiveGutter)) {
        FeedbackIconButton(...)  // 自己的 modifier 只放 .size()
    }
}
```

`Box` 拿到的约束是松的，内边距只占宽度、不压内容。`RYScaffold` 里的隐式顶部栏本来就是这么写的
（所以那 20 多个页面没这个 bug），出问题的只有 `FeedsPage`——它是唯一自己传 `topBar` 的页面。

**同理适用于任何"modifier 被转交给固定尺寸子节点"的组件**：加布局类 modifier 前先看它在哪一层生效。

---

### 2.6 新增一个设置项要改的 5 个文件（实际 6 处位置；漏一个就静默失效）

加一个偏好（布尔 / 整数）**必须**同时改下面 5 个文件。少任何一处，要么编译不过，要么"设置能点、
但怎么点都不生效"，而且**没有任何报错**。

> **数清楚：第 2 行的 `DataStoreExt.kt` 一个文件里有 4 处要改**，所以物理位置是 **6 处**
> （字符串常量 ×2 + `PreferencesKey.keyList` ×1 + `DataStoreKey.keys` ×1 + 其余 4 个文件各 1 处）。
> 2026-09-24 的界面字号就是这么数的 —— 只按"5 个文件"去核对，会漏掉那 4 处里的 3 处。

| # | 文件 | 改什么 | 漏掉的后果 |
|---|---|---|---|
| 1 | `infrastructure/preference/XxxPreference.kt` | 新文件：`sealed class` + `Local` + `put()` + `fromPreferences()` | 编译不过 |
| 2 | `ui/ext/DataStoreExt.kt` | **两个** companion 各加一个 `const val`，`PreferencesKey.keyList` 加一项，`DataStoreKey.keys` 加一条 | 写入被静默丢弃（见下） |
| 3 | `preference/Settings.kt` | 加字段 | 编译不过 |
| 4 | `preference/Preference.kt` | `toSettings()` 里加 `= XxxPreference.fromPreferences(this)` | 编译不过 |
| 5 | `preference/SettingsProvider.kt` | 加 `LocalXxx provides settings.xxx` | **编译通过、设置页正常、界面永远读到 `default`** |

- 第 5 条最阴：漏了它没有任何编译错误，`LocalXxx.current` 一直返回默认值——表现为"开关能拨、
  退出去再进来还是关着、功能也不变"。
- 第 2 条次之：`DataStore<Preferences>.put(key: String, value: Any)` 第一行就是
  `DataStoreKey.keys[dataStoreKeys]?.key ?: return`。键不在那张表里 = 写入变空操作，**不抛异常**。
  而 `DataStoreExt.kt` 里有两份 companion（老的 `DataStoreKey` 一份、新的 `PreferencesKey` 一份），
  两份都要加：`put()` 与 `fromPreferences()` 走老的那份，设置**导出/导入**走新的那份。
- **布尔偏好还要一个 `operator fun XxxPreference.not()`**（同文件末尾，参照
  `FlowArticleListFeedIconPreference.kt`），否则设置页里那句典型写法 `(!xxx).put(context, scope)`
  编译不过。这是本仓库的既有约定，每个布尔偏好都有。

---

### 2.7 "全部已读"按钮：位置是偏好，两处渲染

按钮画在哪个栏里由 `markAsReadButtonPosition` 决定，**两处的 `if` 互斥**：

- `Top` → `topBar.actions` 里的 `MarkAsReadIconButton(...)`
- `Bottom`（默认）→ `bottomBar` 里 `FilterBar(leading = markAsReadButtonLeading)`

两处共用同一个 `MarkAsReadIconButton`，所以外观和点击行为不会分叉。点击行为也只有一个
`onMarkAsReadClick`：已展开 → 收起；`markAllAsReadWithoutConfirm` 打开 → 直接全部已读；
否则 → 展开条件条（7 / 3 / 1 天 + 全部）。

上游合并时要看的两点：

- [ ] `ui/component/FilterBar.kt` 的 `leading` 是**自有可选参数**（默认 `null`，其余调用点不传）。
      它必须在 Row 内、`Spacer(filterBarPadding)` **之后**，并且用 `Modifier.weight(1f)` 的 `Box`
      裹住、内容居中。放到 Spacer 之前，按钮会跑到自适应 gutter 外侧，平板上与内容列错位。
      上游若重写 `FilterBar`，把 `leading?.let { Box(Modifier.weight(1f), Center) { it() } }`
      这一整段搬回去即可。
- [ ] **每个 `NavigationBarItem` 上的 `Modifier.weight(1f)` 不能删。** `NavigationBarItem` 只有在
      `NavigationBar`（它本身就是个会给兄弟项分配权重的 Row）里才与同级等分；放进本 fork 这个普通
      `Row` 时它按自身内容宽度排布。少了这个权重，4 项会退化成"左端一个 40dp 小图标 + 三个被拉到
      很开的过滤项"—— 这正是它看起来难看的原因。这条是 fork 特有的排布前提，与上游无关。
- [ ] **左槽在「收藏」筛选下返回 `null`，不是"渲染但不可见"。** 归零的是槽位本身，三项才会重新
      均分；若改成隐藏，那一格会一直空着。
- [ ] 上游的 `mark_as_read_button_position` 字符串还在（那个设置项原本是 `enabled = false` 的占位，
      没有对应的 DataStore 键）。本 fork 用**自己的键** `markAsReadButtonPosition` 实现了它。
      若上游将来真做同一项，两个键会语义重叠——先看上游的取值与默认值，再决定保留哪一个，
      别让两份偏好同时生效。

条件条（`MarkAsReadBar`）从 content 顶部移到了 `bottomBar` 的 `Column` 里、`FilterBar` **之上**。
注意 Scaffold 的 `bottomBar` 槽位**不是浮层**：展开时它会把内容区**推高**，而不是盖在列表上。

**设置项的位置（2026-09-24 调整过，别改回去）**：`markAsReadButtonPosition` 与
`markAllAsReadWithoutConfirm` 这两行**必须相邻**，都放在 `FlowPageStylePage` 的「顶部栏」
分区里。原来 `markAllAsReadWithoutConfirm` 被落在「筛选栏」分区的**最后一行**，
与它的姊妹设置相隔约 140 行 —— 用户根本找不到（有人直接来提"请加这个设置"，
而功能其实早就有了）。两行都只配置**同一个按钮**，拆开就没有可发现性。

**没有做、但值得知道的取舍**：`markAllAsReadWithoutConfirm` 打开后走的是
`MarkAsReadConditions.All`，且条件条永远不会展开 → **7 天 / 3 天 / 1 天这三个选项变得不可达**。
目前是二元开关（要确认 / 不要确认），不是三态。若将来有人想要"不确认但要按 3 天"，需要把它
改成三态偏好，别在 `onMarkAsReadClick` 里堆 `if`。

---

### 2.8 字体设置是两层：基础行 + 标题覆盖行（2026-09-24 加）

信息流和阅读页各有**两行**字体设置，**不是两个平级的独立设置**：

| 页面 | 基础行（原有） | 覆盖行（新增，默认「跟随页面字体」） |
|---|---|---|
| 信息流 | 「列表字体」`flowFonts` → 摘要 + 订阅名 + 时间 | 「标题字体」`flowTitleFonts` → 文章标题 |
| 阅读页 | 「阅读字体」`readingFonts` → 正文 + 各级小标题 + 日期/作者/订阅名 | 「标题字体」`readingTitleFonts` → 文章大标题 |

**为什么是"覆盖"而不是"两个独立设置"**：`TitleFontsPreference.Follow` 的语义是*回落到基础行*，
所以任意 (标题字体, 正文字体) 组合都可达 —— 把基础行设成正文那个、覆盖行设成标题那个即可，
反过来也行。收益是**默认值就是零变化**：不动这项的老用户，观感与升级前一致，也不需要数据迁移。
代价只是 UI 上多了一层"跟随"的概念。

渲染接线只有三处，都很浅：

- **信息流标题**：`ui/component/ListFonts.kt` 的 `withFlowTitleStyle()`（唯一新增的样式入口），
  `ArticleItem.kt` 标题那一行从 `.withFlowListStyle()` 改成它。
  **它内部照样调 `withListStyle`，所以字号缩放与基线完全不变** —— 别在这里另算一遍 scale，
  否则 §2.2 的 16sp 基线守卫与它不一致。
- **阅读页标题**：`ui/page/home/reading/Metadata.kt` 的 `titleFontFamily`（`?:` 回落到
  `fontFamily`），**只作用于 `headlineLarge` 那一处**；同文件的日期 / 作者 / 订阅名是正文，
  继续跟 `readingFonts`。WebView 渲染器不受影响 —— 标题始终由 Compose 的 `Metadata` 画，
  `RYWebView` 只注入正文。
- **设置页预览**：`TitleAndTextPreview.kt` 的标题预览也用覆盖行，否则改完设置看到的预览是假的。

上游合并时要看的两点：

- [ ] `ArticleItem.kt` 的标题是否仍走 `.merge(lineHeight = 22.sp).withFlowTitleStyle()`。
      上游若调整标题样式，只把最后那个扩展函数换掉，**别把 `withFlowListStyle` 也一起换回来**。
- [ ] `Metadata.kt` 里 `headlineLarge` 是否仍是标题、`labelMedium` 是否仍是元数据。
      上游若换掉，`titleFontFamily` 的作用位置要跟着挪。

**「打开文章自动拉取全文」**（同批加的 `readingAutoFullContent`，默认关）只落在一个地方：
`ArticleListReaderViewModel.renderContent()`。它与订阅源自带的 `feed.isFullContent` 是**或**关系，
但两者**故意区别对待失败**：

- 订阅源自己开了全文 → 失败仍显示错误（**保持上游行为不变**）。
- 只有全局设置开了（`bySetting`）→ 失败**回落到 RSS 描述**（`renderFullContent(fallbackDescription)`）。
  这只是"省一次点击"，不该把数据库里已经有的正文换成一条报错。

工具栏那个全文按钮**没有动**，它本来就是切换：设置开着时，第一下点击就是切回描述。

---

## 3. 真机核验清单

模拟器用 Android Studio 的 Resizable 设备，手动拖宽度跨越 600 / 840dp。

- [ ] **手机竖屏**：Feeds / Flow / 阅读 / 设置 —— 与改动前逐一比对（**核心回归项**）
- [ ] **平板竖屏（~800dp）**：Feeds 内容居中，两侧约 80dp；顶部两个图标与内容列同宽**且确实可见**（§2.5）
- [ ] **平板（任何 > 720dp 的宽度）**：Feeds 顶部齿轮与"订阅"图标都在 —— `tablet.3` 在这两个位置是空白
- [ ] **平板横屏（~1280dp）**：两侧约 320dp；**逐个**打开设置子页（accounts ×3、color ×10、
      interaction、languages、tips ×2、troubleshooting、startup）确认都居中
- [ ] **双栏（关键）**：Flow 列**不被额外缩进**，阅读列按上游逻辑居中
- [ ] **拖动窗口跨断点**：无跳变、无崩溃
- [ ] **列表字号** 10 / 16 / 32sp：单调变化；16sp 时与上游外观一致
- [ ] **列表字体**：External 与阅读页一致；Default 无变化
- [ ] **界面字号 = 100%（默认）**：设置页与对话框与改动前**逐像素一致**（`scaledTypography` 返回同一实例，见 §2.3 同款约束）
- [ ] **界面字号 = 150%**：**逐个**设置子页无文字裁切 / 无重叠；标题与说明都变大；那 3 处硬编码 sp
      （`SettingItem` / `SelectableSettingGroupItem` / `Banner`）跟着变大
- [ ] **界面字号 = 150% 下的对话框（关键）**：随便开一个对话框（如「关于」或分组配置）确认标题与正文都变大。
      **对话框不跟随 = `LocalDensity` 方案被误用的信号**，见 §2.2
- [ ] **界面字号 = 150% 时列表页不受影响（关键）**：Feeds / Flow 的列表字号应与 100% 时**完全相同**。
      若变大，说明缩放作用域泄漏出了设置页，列表字号乘了两遍，见 §2.2
- [ ] **拖滑块**：读数实时变化；拖动中上方控件不"从手指下滑走"（偏好只在松手时落盘）
- [ ] **切出去再进来**：字号保持
- [ ] **无障碍：系统字体 200%** —— 确认文本不被裁切（旧版曾覆写 `LocalDensity`，已移除；这是回归验证）
- [ ] **外接鼠标**：列表项、图标按钮悬停有反馈
- [ ] 深色 / AMOLED 主题、阿拉伯语（RTL）：内边距方向正确

---

## 4. 已知限制（不是 bug，是取舍）

1. **横向系统内边距会让居中偏半个内边距**。gutter 由 `RYScaffold` 拿到的约束宽算出，
   而该约束包含系统栏内边距；再往内才是 Scaffold 自己的 `calculateStartPadding`。
   横屏 + 三键导航（侧边导航栏）时会两侧不等，内容列宽 640 会变成 ~592 且中心偏 ~24dp。
   **要修的话**：在 `RYScaffold` 里减掉 `WindowInsets.systemBars` 的左右内边距再算 gutter。
2. **显式 `topBar`（Feeds / Flow / Startup）不参与内容限宽**。Feeds 的两个图标已单独对齐
   （写法见 §2.5，不要改成给图标加 padding）；Flow 的 `LargeTopAppBar` 标题、Startup 页的 FAB
   仍是窗口对齐。原因：整条 bar 可点击（回顶），缩窄会拿走点击区域。
3. **`FlowPage` 的 `FilterBar` 未接 `contentPadding`**，因为它在 pane 里（gutter 恒为 0）。
   只有"Expanded 且单栏"这种少见形态下，底部栏会是通栏而内容被限宽。
4. **Feeds ↔ Flow 的 `filterBar` sharedElement 过渡**：两侧 gutter 不同（窗口宽 vs pane 宽），
   过渡时宽度会插值。这是上游共享元素设计的既有行为，本改动没有改变它。
5. **`DataStoreExt.kt` 里 4 个偏好键共 24 行样板**，上游每加一个偏好都会在同一区域产生相邻冲突。
   合并为 2 个键可让冲突面减半，代价是老用户偏好需要迁移；**有真实用户前不要动**。
6. **「界面字号」刻意不覆盖列表页与阅读页**（`Feeds` / `Reading` / `Startup` 三条路由保持裸 `NavEntry`）。
   那两处的字号已各有自己的偏好（列表字号 / 阅读字号），全局再乘一遍会**乘两遍**，
   而且会让"我设了 150% 但列表没变"看起来像 bug。**这是设计，不是遗漏** —— 详见 §2.2 / §9.9。
7. **「界面字号」不作用于 `StartupPage` 与 `else` 兜底路由**。启动页是一次性过渡页，
   改动它收益为零、却要多一处挂载点。

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

---

## 7. 发布流程：push 即出 release（全自动）

**约定：每一次 push 到 `main`，都要产出 APK、打 tag、挂 release，用户直接在 release 页面下载。**
由自有的 `fork-auto-release.yaml` 完成，不需要任何手工操作。

### 7.1 它做了什么

一个 run 内顺序完成：`assembleGithubRelease` → 算序号 → 建 annotated tag → `gh release create` 上传 APK。

| 项 | 取值 | 怎么来的 |
|---|---|---|
| tag | `v<versionName>-tablet.<N>` | `versionName` 从 `app/build.gradle.kts` 现读（**不硬编码**，永不与 APK 名漂移）；`N` = 已有同前缀 tag 的最大值 + 1 |
| release | `--prerelease`，title = tag | 与 `tablet.1/.2/.3` 既有风格一致 |
| notes | 「Built from `<sha>`」+ 上一个 tag 以来的 commit 列表 + 签名说明 | `git log <prev-tag>..<sha>` |
| asset | `app/build/outputs/apk/github/release/*.apk` | 名形如 `ReadYou-<versionName>-<7位sha>.apk` |

### 7.2 为什么不能只给 `release-build.yaml` 加 `push` 触发器（关键）

那是最省事的写法，但**行不通**：`release-build.yaml` 靠 `on: push: tags: "v*"` 触发，而本工作流要用
**自己的 `GITHUB_TOKEN` 去推那个 tag** —— GitHub 明确规定，
**用 `GITHUB_TOKEN` 产生的事件不会再触发新的 workflow run**（防递归）。于是 tag 推上去了、
**什么都不会发生**，release 页面空空如也，而且每一步看起来都"成功"。

所以整套链路（build + tag + release）必须由同一个 run 自己走完。

顺带的好处：既然 GITHUB_TOKEN 推 tag 不触发 workflow，也就**不会**形成"push → 打 tag → 又触发 push → 又打 tag"的死循环。

### 7.3 防重与防竞态

- **防自触发**：`on: push: branches: [main]` 之外，job 上还有 `if: github.ref_type == 'branch'`。
  这是第二道锁 —— 万一有人把 `branches:` 改掉，也不会退化成无限发版循环。
- **防竞态**：`concurrency` 固定 group + `cancel-in-progress: false`，两次连续 push 的 run **排队**而不是并行。
  同时序号计算带重试（每轮先 `git fetch --tags --force` 再取 max+1），
  万一并行算出同一个号，`git push` 被拒后重算，最多 5 次。
- **不留半成品**：先 build，成功后才建 tag、才建 release。构建失败 → 不产生 tag、不产生空 release。

### 7.4 什么时候还会用到 `release-build.yaml`（手动）

保留它，用于自动化覆盖不了的场景：**重发/更新某个已存在的 tag**（`gh release upload --clobber` 语义）、
换 flavor（`assembleFdroidRelease` 等）、或某次 CI 失败后手工补一个包。它按 tag 触发，与本工作流互不干涉。

### 7.5 成本与已知取舍

- 仓库是 **public** → Actions 分钟数不计费，`build_commit.yaml` 与 `fork-auto-release.yaml` 各构建一次
  只是多花几分钟墙钟时间，不产生费用。
- **纯文档提交也会发一个 release**（本工作流的触发条件就是"push 到 main"）。
  若某天觉得噪音大，给 `on.push` 加 `paths-ignore: ['**/*.md', '.workbuddy/**']` 即可 —— 但那样文档类提交
  就没有可下载的包了，属取舍。
- tag 序号来自仓库**已有 tag**，不来自 `versionCode`（`47` 目前不变）。所以升级 versionName 时
  序号会从头开始（`v0.16.3-tablet.1`），这是刻意的：序号只表示"本 version 内的第几个预览包"。
- **半成品排查**：若某次 run 建了 tag 但没挂上 release，重跑该 run 不会复用旧 tag（序号已 +1），
  会多出一个空 tag —— 需要手工清理，或直接用 `release-build.yaml` 对着那个 tag 补发。

---

## 8. 上游更新后怎么同步（别点网页那个 Sync fork）

### 8.1 结论

**GitHub 网页的 `Sync fork` → `Update branch` 在本 fork 上是不能用的**，因为它只做 fast-forward。
本 fork 的 main 有自己的 21 个提交，一旦上游也有新提交，分支就处于分叉状态，
GitHub 会**拒绝同步**，只给两个出口：**`Discard N commits`**（灾难）或者让你去开 PR。

**`Discard` 等于 `git reset --hard upstream/main` + force push —— 本 fork 的提交会从 main 上被抹掉。**
（社区里就有人点了它，然后撞上 `Cannot force-push to this branch`。）

官方文档的原文（[Syncing a fork](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/working-with-forks/syncing-a-fork)）
其实写得很清楚：推荐的同步方式是本地的 `git fetch upstream` → `git merge upstream/main` → `git push`，
它 "syncs your fork's default branch with the upstream repository **without losing your local changes**"；
而网页版在上游改动有冲突时只会 "prompt you to create a pull request to resolve the conflicts"。
`gh repo sync` 同理 —— 冲突时它自己也同步不了，只能 `--force` 覆盖，那也是放弃自己的改动。

### 8.2 为什么是 merge，不是 rebase

本 fork 已经发过 `v0.16.2-tablet.1~4`，**release tag 指向具体提交**：

- **merge**：不改写历史，tag 指向的提交仍在 main 的祖先链上，`git log v0.16.2-tablet.4` 一路可读；不需要 force push。
- **rebase**：重写这些提交 → tag 变悬空引用、必须 force push 才能推上去，已发布的 APK 与提交的对应关系也会错位。

代价只是多一个 merge commit。**这个 fork 选 merge。**

### 8.3 操作步骤

```bash
cd /d/VibeCoding/Git/ReadYou

# 0) 一次性：加远端（已完成，git remote -v 应能看到 upstream）
git remote add upstream https://github.com/ReadYouApp/ReadYou.git

# 1) 拉上游。本机必须清空代理环境变量走直连，否则报 CONNECT tunnel failed, response 502
NOPROXY="env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY"
$NOPROXY git -c http.schannelCheckRevoke=false -c http.version=HTTP/1.1 fetch upstream

# 2) 补写跟踪引用。本机 git 写不进 refs/remotes/upstream/*，fetch 完 git branch -r 是空的
SHA=$($NOPROXY git -c http.schannelCheckRevoke=false ls-remote upstream refs/heads/main | cut -f1)
python -c "import os;d='.git/refs/remotes/upstream';os.makedirs(d,exist_ok=True);open(d+'/main','w',newline='\n').write('$SHA\n')"
git rev-parse upstream/main          # 验证：应等于远端 main

# 3) 先看要合什么，再决定
git rev-list --left-right --count HEAD...upstream/main      # 左=本地独有，右=上游独有
git log --oneline HEAD..upstream/main
git diff --stat HEAD...upstream/main
git diff --name-only HEAD...upstream/main | grep -E 'RYScaffold\.kt|FeedsPage\.kt|theme/Theme\.kt|Clickable\.kt|build\.gradle\.kts'
#   ↑ 有输出 = 上游动了本 fork 的挂载点，merge 时按 §2 逐项核验

# 4) 合并（用 --no-verify 避开本机卡死的 husky 钩子）
git merge upstream/main --no-verify

# 5) 有冲突只可能在本 fork 改过的文件（§1 的挂载点）。
#    改完 → git add <files> → git merge --continue --no-verify

# 6) 核验：§2 那份清单逐项过（不要跳），尤其是单测与字号基线

# 7) 推送。会自动触发 fork-auto-release，产出下一个 v0.16.2-tablet.N
git -c http.schannelCheckRevoke=false -c http.version=HTTP/1.1 push origin main
```

### 8.4 实测基线（2026-09-23）

- **上游 `ReadYouApp/ReadYou` 的 main 停在 `d2b979cc`（2026-08-11），自那以后没有任何新提交。**
  GitHub compare API 对 `d2b979cc...main` 返回 `identical`（ahead_by = 0）。
- 也就是说 **本 fork 目前 28 ahead / 0 behind —— 现在没有任何东西需要同步**，
  这份流程是给上游下次发版时用的。
- **2026-09-24 复查：结论不变**（main 仍是 `d2b979cc`，13 个上游分支无一领先 main）。
  那次复查的重点转向了 **open PR**——主干上没有可同步的，但 31 个 open PR 里有值得主动摘的。
  明细与冲突面分析见 `UPSTREAM-TRACKING.md`。
- 上游的更新节奏是**低频**：2026-08-11 之后停滞；再往前是 7 月初、6 月初、5 月中各几个提交，
  其中相当一部分是 Weblate 翻译和 docs/CI 改动。所以"跟着上游跑"的成本本身就不高。

### 8.5 不要做的

- ❌ 点网页 `Sync fork` 里的 **`Discard N commits`** —— 等于放弃本 fork 的全部提交。
- ❌ `git rebase upstream/main` + `git push --force` —— 会打乱已发布 tag 与提交的对应关系。
- ❌ 试图让 workflow 全自动 merge 上游 —— 冲突时它只会失败，而 §2 那份核验清单
  （字号基线、pane 内边距、顶部栏图标）**本来就必须人工过一遍**，自动合进来反而是隐患。
  可选的是"自动**检测**上游是否更新"，但合并要人来做。

---

## 9. 上游友好度：新改动该落在哪里（2026-09-24 定）

做任何优化前先问一句：**这次改动会不会在下一次 `git merge upstream/main` 时变成额外的手工活？**

### 9.1 优先级从高到低

| 改动形态 | 未来的合并成本 | 例子 |
|---|---|---|
| **纯新增文件** | 零 | `baseline-prof.txt`、新的 Preference 文件、新的 workflow |
| **删掉上游某个独立的行 / 块** | 低（内容减少最容易被自动合并） | 删掉重复的 `implementation(ui-tooling)`、删掉遗留的 `ProfileInstallerInitializer` 调用 |
| **在独立位置追加一小段** | 低 | `FeedIcon.kt` 加私有常量并传参 |
| **改上游热点文件的核心逻辑** | 高，需逐行对 | `DataStoreExt.kt` 的 keys map、`FlowPage.kt` 的布局树 |
| **重构上游既有结构** | 最高，基本每次都要重做 | 换偏好注册机制、换 DI 结构 |

⚠️ `ArticleList.kt`、`FlowPage.kt`、`FeedsPage.kt`、`ArticleItem.kt`、`DataStoreExt.kt`
是上游的**热点文件**，能不动就不动。本次唯一新增的热点挂载点是 `ArticleList.kt`
（`rememberIsUnread`：两处调用 + 一个私有函数）。冲突时按"保留 `derivedStateOf` 包法"手工合即可。

### 9.2 摘上游 open PR 反而比自己写更"上游友好"

本次最关键的一条结论。同样是修一个问题，有两条路：

- **自己实现一遍** —— 上游将来合并它自己的 PR 时，两边是**不同实现**，git 只能报冲突，得手工挑拣。
- **把那个 PR 的补丁摘过来** —— 上游将来合并的是**同样的改动**，base 相同、diff 相同，
  git 能识别成"两边做了同一件事"从而自动合并。

所以只要该 PR 碰的文件本 fork 没改过，**摘 PR 是更省事的选择**。冲突面一算就知道：

```bash
# 本 fork 改过哪些文件 —— 摘 PR 的"冲突面"基准
git diff --name-only upstream/main HEAD
# 某个 PR 改了哪些文件
curl -s --ssl-no-revoke -H "Authorization: token $TOKEN" \
  "https://api.github.com/repos/ReadYouApp/ReadYou/pulls/<N>/files?per_page=100" \
  -o "C:/Users/lfk/AppData/Local/Temp/files<N>.json"
# 拿 patch 并试应用（干净通过 = 可摘）
git apply --check "C:/Users/lfk/AppData/Local/Temp/forkup/p<N>.diff"
```

**摘之前必须看三样东西**（本次三样全踩到了）：

1. **`commits` 列表** —— 标题常骗人。`#1305` 叫 "Fix sync worker stall"，实际是 **23 个提交的杂烩**，
   夹带 version bump、DB schema 升到 8 和新功能，整体摘会把版本号与数据库版本一起顶掉。
2. **新增的测试** —— `#1322` 带了两个**真实联网**测试（去请求 phoronix.com）。单测依赖外部站点
   的可用性与反爬策略会让 CI 偶发红灯，**必须剔除**。
3. **多个 PR 之间是否互相冲突** —— `#1322` 与 `#1323` 都从同一 base 改 `RssHelper.kt` 与
   `RssHelperTest.kt`，**不能顺序 apply**，要手工合。

### 9.3 本次（2026-09-24）落地的改动

**摘上游 open PR（全部零冲突，碰的文件本 fork 从未改过）**

| PR | 内容 |
|---|---|
| `#1322` | 浏览器风格 UA 绕过 WAF 拦爬；`BestIconFinder` 容错、无协议域名改走 https、图标候选限 4 个；`searchFeed` 的错误里带上 HTTP 状态码。**剔除了它自带的 2 个联网测试** |
| `#1323` | 完整内容解析的正确字符集探测（HTTP header → BOM → `<meta>` → UTF-8），带 4 个本地测试 |
| `#1324` | 超长图片文件名截断，防 `ENAMETOOLONG` |
| `#1320` | 打开链接失败时重置 intent 的包名 |
| `#1317` | 保存 greader / FreshRSS 服务器地址时补回结尾斜杠 |

**启动与构建**

- 删掉 `app/build.gradle.kts` 里重复的 `implementation(ui-tooling)`（同文件 151 行已有
  `debugImplementation`）—— 这条让 ui-tooling 进了 release dex，已用 dex 的类型描述符表坐实。
- 删掉 `MainActivity` 里遗留的 `ProfileInstallerInitializer().create()`（主线程磁盘 IO，release 也跑）。
- `AndroidApp` 的 WorkManager 日志级别按 `BuildConfig.DEBUG` 分档。
- `AndroidApp` 删掉 12 个未被引用的 `@Inject lateinit`（Hilt 会在 Application 创建时把所有字段实例化，
  全在主线程）。**`diffMapHolder` 虽然也没被引用，但必须保留** —— 它靠注入触发 `init{}` 里的副作用。
- `WidgetUpdateWorker` 的一次性任务改 `enqueueUniqueWork(..., KEEP)`（原来无 unique name，
  每次 `onResume` 都堆一个）；`haveSetPreviews` 从 Worker 实例字段改成进程级 `@Volatile`
  —— Worker 每次执行都是新实例，**原来那个守卫从未生效过**。

**渲染与解码**

- `ArticleList` 用 `derivedStateOf` 收敛 `diffMap` 的重组范围（见 §1 表格）。
- `FeedIcon` 传 `size = 192px`，不再按原始分辨率解码订阅源 logo（logo 常是 512×512 或 1200×630，
  为一个 20dp 的圆点解码约 3 MB 位图）。
- 新增 `app/src/main/baseline-prof.txt`（启动入口 + 首屏类）。**注意**：AndroidX AAR 自带的 profile
  早已由 AGP 合并，缺的只是应用自身的那一半。
- `MainActivity` 的 `"${e.printStackTrace()}"` 改成把异常作为参数传入（原写法日志永远是 `kotlin.Unit`）。

**设置项（2026-09-24 追加）**

- 信息流与阅读页各加一行「标题字体」，把原来"一页一种字体"拆成「基础行 + 标题覆盖行」两层。
  语义、理由与上游合并注意点全部写在 **§2.8**。
- 3 个新偏好 `flowTitleFonts` / `readingTitleFonts` / `readingAutoFullContent`
  都按 §2.6 的**5 处清单**注册 —— 本轮实测漏了其中 3 处，见 §9.6。
- 阅读页新增「自动拉取全文」开关（默认关）：进入文章即拉全文，不用再点工具栏的全文解析。
  **工具栏按钮保留、行为不变。**
- 4 个新文件：`TitleFontsPreference.kt`（两个页面共用同一套选项）、
  `{Flow,Reading}TitleFontsPreference.kt`、`ReadingAutoFullContentPreference.kt`。
- 4 条新字符串，`values/` 与 `values-zh-rCN/` 各一份，**都追加在 `</resources>` 之前**
  （上游友好度最高的位置）。

**平板缩放与字体收尾（2026-09-23 → 09-24，本轮）**

用户原话：「字体设置再收尾（FilterBar / 搜索栏跟随字号、两页各自的自定义 TTF 导入）…然后把图标也调整一下，现在的太小了，可能是针对手机的，我需要针对平板的（我的平板 8.8 英寸）。」

| 能力 | 落点 | 上游友好度 |
|---|---|---|
| 图标与控件高度按尺寸类放大 | 新文件 `ui/adaptive/AdaptiveSizing.kt`（`adaptiveSize()` / `scaledDp()` / `adaptiveScale()`）+ 15 个调用点各 1 行 | 高：Compact 恒等，手机端逐 dp 不变 |
| FilterBar 标签跟随页面字号 | `ui/component/FilterBar.kt` 新增 `labelStyle: TextStyle? = null`，默认 `LocalTextStyle.current` | 高：默认值与「完全不传 style」逐字节等价 |
| 搜索栏跟随页面字号 | `ui/page/home/flow/SearchBar.kt` 新增 `textStyle: TextStyle = MaterialTheme.typography.bodyLarge` | 高：同上 |
| 两页**各自独立**的自定义 TTF | 新文件 `ui/ext/ListExternalFonts.kt`（`Slot.Feeds` → `feeds_font.ttf`、`Slot.Flow` → `flow_font.ttf`） | 高：纯新增文件 |
| 缩放倍率守卫 | 新文件 `app/src/test/.../AdaptiveScaleTest.kt` | 纯新增 |

**三个关键设计决定（改动前先读这段）**

1. **不放大文本**。用户已有显式可见的字号旋钮；再叠一层隐式全局倍率会让设置页显示的数字变成谎言。
   这个错误这一层犯过并已回退，完整理由写在 `AdaptiveLayout.kt` 的 KDoc 里。
2. ~~**不放大触控目标与列表行高**。~~ **已作废，不要按这条判断。** 当时用户只勾了「顶栏与 FilterBar 高度」，
   没勾「触控目标」和「列表行高与内边距」。**下一轮用户补勾了这两项，已落地，见 §9.8。**
   保留此条只为记录当时的范围。图标字形始终包含在勾选项内。
3. **两页各自导入不新增偏好键**。靠「文件路径 + 槽位参数」区分，`ListFontsPreference` 复用现有
   `External(5)` 值 —— 避开了 §2.6 的「漏一处静默失效」陷阱，偏好注册面零增长。

**与阅读页字体导入的一处刻意差异**：阅读页（`ExternalFonts`）导入后 `context.restart()`；
列表页（`ListExternalFonts`）**不重启**，靠 `mutableStateMapOf` 做 generation 计数器，在 `remember` 的 key 里读它。
原因是「重复导入同一槽位不改变任何偏好值」—— `External` 存的值前后一样，静态缓存的 `FontFamily` 不会失效，只有 generation 能触发失效。

**MIME 类型**：两页都用 `MimeType.FONT`（`font/ttf`），与 `ReadingStylePage.kt:333`、
`ColorAndStylePage.kt:249` 一致。若真机上某些设备筛不出 `.ttf` 文件，三处一起放宽。

**`FeedbackIconButton` 的尺寸必须走参数而不是 `modifier`**（§2.5 的同一条陷阱）：本轮把 `iconSize: Dp = 24.dp` 提成参数，
因为经 `modifier` 传入的尺寸会被原样应用、静默绕过缩放。

### 9.6 本轮的一个教训：改动"报成功"不等于已落盘

这批改动里，**有 3 处 Edit 回报成功但文件没变**（IDE 同时开着这些文件，先出现
`EBUSY: resource busy or locked`，之后若干条成功消息未落盘）。漏掉的恰好是**声明行**：
`var titleFontsDialogVisible`、`val titleFonts`、以及 `keyList` 里的两行 —— 引用处都在，
`grep` 看着"改好了"，实际是**编译错误**。

抓出来的办法是**收尾跑一遍断言**，而不是重新读代码。已经把方法沉淀进技能
`android-edit-verify-offline` §1.4b + `scripts/patch_text.py`（CRLF 感知、幂等、原子写入、
`apply` 与 `check` 两个模式）。**同步上游后改本 fork 也照此办理。**

顺带一个同源的坑：**行尾随平台，不是本仓库的固定属性**。那台 Windows 机器上工作区是 **CRLF**，
用 `\n` 写多行锚点会匹配 0 次，报错长得像"锚点写错了"，实际是行尾不对；
而这台 Mac 上检出的**全部是 LF**（实测 5 个关键文件 `crlf=0`）。别把平台结论当成仓库属性。

**2026-09-24 追加 —— 本节引用的技能当时并不存在，同一类坑又踩了一次。**

`android-edit-verify-offline` 在本机 `~/.workbuddy-ai/skills/` 下**没有**（只有 `probe-selfhosted-app-settings` 与 `verify-browser-assertions-cdp`），`scripts/patch_text.py` 同样找不到。
于是补 `MimeType` 的 import 时又犯了一次：**两条 Edit 放进同一条消息** → 同文件并发写，
**后一条覆盖了前一条**，import 静默丢失。而引用处（`MimeType.FONT`）在，`grep` 看着"改好了"，
实际是编译错误 —— 和本节开头那 3 处一模一样的形态。

**两条硬规则（已写进技能）**：

1. **同一个文件的多处改动，永远不要放进同一条消息**（不同文件之间并行是安全的）。
2. 收尾必须跑一遍**断言**而不是重读代码：括号平衡、import 可达性、XML 良构、关键声明存在。
   本机没有 JDK / Android SDK，编译只能在 CI 跑，所以这四支离线脚本是唯一的低成本拦截点。

### 9.4 明确没做（省得将来重复考虑）

- **Room 复合索引**（`article` 加 `(accountId, date)` / `(feedId, date)`）：需要 DB migration，
  且上游 `#1305` 正在把 schema 推到 8 —— 两边会抢同一个版本号。**单独发版，且先确认上游动向。**
- **`proguard-rules.pro` 的 `-dontobfuscate` 与 `-keep class me.ash.reader.** { *; }`**：
  上游几乎不碰这个文件，所以"上游友好度"最高，但风险在**运行时**（Gson 反射、Rome 解析、Widget），
  必须真机冒烟一遍。适合单独做、单独发版。
- **`OkHttpClientModule` 的 `trustAllCerts = true`**：上游 `#1322` 重写了这个文件却**保留**了它。
  收紧会影响自建 FreshRSS 的同步，改动面大，继续记录在案、不动。
- **`CrashHandler` 不委托默认处理器也不结束进程**：要动崩溃路径，单独验证。
- **`ArticleList` 里 sticky header 的 O(n) 全量遍历**：`default = OFF`，默认根本不走；
  真收到反馈应该加提示文案，而不是重写那段。

### 9.5 这一版的核验结果（2026-09-24）

提交：`db37b454` → `bce0ca13` → `27edf7bc` → `3d826fb6`（最后一个是纯文档）。

| 项 | 结果 |
|---|---|
| `Build Commit`（`27edf7bc`） | success |
| `Fork Auto Release`（`27edf7bc`） | success，发布 **`v0.16.2-tablet.8`** |
| `Unit Tests`（`27edf7bc`） | `cancelled` —— **不是失败**，是被后推的文档提交按 `cancel-in-progress` 取消了 |
| `Unit Tests`（`3d826fb6`） | **success**，且它就是含全部新测试的工作树 |
| 测试报告实测 | 9 个测试类 / **30 用例，0 失败 0 错误 0 跳过** |
| APK | `ReadYou-0.16.2-27edf7bc.apk` **11,078,008 B**（tablet.7 是 11,118,559 B，**-40,551 B**） |

**两个此前存疑的点都清掉了：**

1. **OkHttp `5.0.0-alpha.12` 的 `Interceptor.Chain` 接口形状**——上游 `#1322` 的测试是在上游
   自己的依赖下写的，不保证在本仓库成立。实测 `OkHttpClientModuleTest` 3 个用例**全过**。
2. **字体基线守卫**——`ListFontBaselineTest` 3 用例 **0 skip**（`fork-unit-tests.yaml` 里那步
   「skip 也算失败」的守卫生效），说明 Compose BOM 的 `titleMedium` 仍是 16sp。

**`baseline-prof.txt` 到底进包了没有：进了，而且内容真的变了。**

用 HTTP Range 只取 APK 尾部读出中央目录（不下整包，见 `android-edit-verify-offline` §1.7）：

| 条目 | `f40e6650`（tablet.7，无源集 profile） | `27edf7bc`（tablet.8，有源集 profile） | Δ |
|---|---|---|---|
| `assets/dexopt/baseline.prof`（zip 内 STORE） | 7,224 B | **7,266 B** | **+42** |
| 同一条目**内层** zlib 解压后 | 77,605 B | **76,826 B** | **−779** |
| `assets/dexopt/baseline.profm` | 1,105 B | 1,101 B | −4 |

**怎么读这三行：**

1. **链路是通的。** 源集里的 `baseline-prof.txt` 真的被 AGP 合并、编译、打进包了。
   原来这一步只是"看起来应该生效"，现在有数字。文件自己写的验收标准（>7,224 B）达成。
2. **收益很小（+42 B），别高估。** 这份 profile 用的是**类级规则**，在 ART 的二进制
   profile 里类级 hot 标记非常紧凑（每类约 1~3 字节），16 个类就是几十字节。
   真正能改变启动耗时的还是**真机采集的、带方法级 hot/startup 标记的 profile**
   （要 macrobenchmark + 一台设备，CI 上还得有 KVM 的 Linux runner）。
   **+42 B 是「把管道接通」的证据，不是「启动变快了」的证据** —— 别对外说成后者。
3. **`−779 B`（解压后反而变小）是意外收获。** 包内变大、解压后变小，说明**合并进来的
   profile 内容确实变了**，不是"文件被原样带上"。一个合理（**但未证实**）的解释是：
   删掉 `implementation(libs.compose.ui.tooling)` 后，ui-tooling 那份库自带 profile
   不再参与合并，抵消并超过了我们新增的部分。**若要坐实这一条，需要去 ui-tooling AAR
   里确认它到底有没有带 `baseline-prof.txt`** —— 不要仅凭这个数字就断言。

> 反面教材：`Build Commit` 日志里的 `:mergeGithubReleaseArtProfile` /
> `:expandGithubReleaseArtProfileWildcards` / `:compileGithubReleaseArtProfile` **不能**当作
> 「源集 profile 被读取了」的判据——只有 library 自带的 profile 时这些任务照样会跑、照样零告警。

---

### 9.7 平板缩放与字体收尾的核验结果（2026-09-24）

提交：`0430da91`（feat）→ `e5e90e95`（docs）→ `2a4c5871`（chore），一次 push。

| 项 | 结果 |
|---|---|
| `Build Commit`（`2a4c5871`） | success |
| `Unit Tests`（`2a4c5871`） | **success**，且「字体基线守卫是否真的跑了」那一步也是 success |
| `Fork Auto Release`（`2a4c5871`） | success，发布 **`v0.16.2-tablet.11`** |
| APK | `ReadYou-0.16.2-2a4c5871.apk` **11,081,872 B**（tablet.8 是 11,078,008 B，**+3,864 B**） |

**为什么 `Unit Tests` 绿就等于「主源集也编译通过」**：`testGithubReleaseUnitTest` 会先编译
`GithubRelease` 变体的主源集，再编译并运行单测。所以这一个 job 同时覆盖了
「生产代码能编译」与「单测全过」两件事。

**为什么能确定 `AdaptiveScaleTest` 真的被执行了**（而不是被放进了一个不参与编译的源集）：
`app/build.gradle.kts` **没有** `sourceSets` 覆写，所以 `app/src/test/java/...` 是全部 flavor
共享的单测源集。`fork-unit-tests.yaml` 里那步「断言 `TEST-*ListFontBaselineTest.xml` 存在」绿了，
就证明这个源集确实被编译并运行了 —— 同一个源集里的 `AdaptiveScaleTest` 若编译不过，整个 job 会红。

**本轮没能取到确切的测试用例数**：报告产物与作业日志在公开仓库上仍需鉴权，而本机既没有 `gh`
也没有 token。可用的间接证据是 job 全绿 + 守卫步骤绿。将来若要拿到数字，不必去解决鉴权 ——
在 CI 里把 `test-results/*.xml` 的摘要 `cat` 进 `$GITHUB_STEP_SUMMARY` 即可。

**离线核验（push 前，本机无 JDK）**：括号平衡 22 个文件、import 可达性 21 个文件、
`strings.xml` 三个 locale 良构且无重名（386 / 355 / 349 条）、关键声明断言 —— 全过。
方法与脚本见技能 `android-edit-verify-offline`。

**规模参考**：本轮 APK 只涨 3,864 B —— 缩放层是纯参数改动，没有新增依赖、没有新增资源，
连新增的 3 条字符串 × 3 个 locale 也只是几 KB 的 XML。

---

### 9.8 平板触控目标与列表内边距（2026-09-24，第二轮）

用户原话：「刚才还有哪些你建议要调整但是我没选的？再让我多选。」→ 补勾了「触控目标」与
「列表行高与内边距」两项（第三项「设置页与对话框字号」另开一轮）。提交 `95f715c3`。

| 项 | 落点 | 上游友好度 |
|---|---|---|
| 触控目标随尺寸类放大 | `AdaptiveSizing.kt` 新增 `Modifier.adaptiveIconButtonContainer()`；`FeedbackIconButton.kt` / `CanBeDisabledIconButton.kt` 各 1 行 | 高：手机档返回 `this`，修饰符链与上游完全一致 |
| 列表行高与内边距 | `GroupItem.kt` 5 处、`FeedItem.kt` 4 处、`ArticleItem.kt` 10 处，每处只包一层 `adaptiveSize()` | 高：全部是「替换一个参数」，无缩进重排 |

**必须记住的一件事：触控目标原本就是 48dp，不是 40dp。**
`IconButton` 的 40dp 只是**可见容器**（`SmallIconButtonTokens.ContainerHeight`，实测自 material3 1.4.0 源码），
它同时调用 `minimumInteractiveComponentSize()` 把**可点区域保留到至少 48dp**（`InteractiveComponentSize` 实测 48dp）。
我此前告诉用户"可点区域是 40dp"，**是错的**。若按那个前提加 `Modifier.size(46.dp)`，会把 48dp **压成 46dp**，
是**退步**。所以 `adaptiveIconButtonContainer()` 是双条件的：手机档什么都不传，平板档才 `maxOf(..., 48.dp)`。
详见 §2.2 的核验项。

**`ArticleItem` 的 `start = 30.dp` 属正确性问题。** 那是给已放大的 `FeedIcon` 预留的缩进；
不同步放大，图标会压住标题文字。

**一并放大的还有滑动操作图标**（`ArticleItem.kt` 两处 `padding(horizontal = 24.dp)` 各加
`.size(adaptiveSize(24.dp))`）：行变高、滑动背景变大之后，固定 24dp 的图标会显小。

**刻意没动**：`SubscribeDialog` / `FeedOptionDrawer` 里的 `FeedIcon` 保持原尺寸（不在本轮范围）。

**核验状态（不完整，如实记录）**：`v0.16.2-tablet.12` 已发布（releases 页面确认），因此
`Fork Auto Release` 至少是成功的 —— 它依赖 `assembleGithubRelease`，即**编译通过**已间接成立。
但 `Build Commit` / `Unit Tests` 两个 job 的结论**没取到**：GitHub API 匿名配额用尽（`403 rate limit`），
`/actions` 页面被安全策略拦截（`SENSITIVE_CONTENT_UNAVAILABLE`）。下次 push 后一并补读。

---

### 9.9 界面字号：设置页与对话框（2026-09-24，第三轮）

用户对上一轮列出的第三项（「设置页与对话框字号」）拍板三件事：**所有对话框**都要跟随、
默认值 **100%（与上游一致）**、控件用 **百分比滑块**。

| 项 | 落点 | 上游友好度 |
|---|---|---|
| 缩放算术 | 新文件 `ui/theme/UiTextScale.kt`：`scaledTypography()` / `uiTextScaleSp()` / `uiTextScaleFactor()` / `ProvideUiTextScale` / `ProvideUnscaledUiText` | 高：整层是新增文件 |
| 设置页挂载 | 新文件 `ui/page/nav3/SettingsNavEntry.kt` 的 `settingsNavEntry()`；`AppEntry.kt` **19 处** `NavEntry(key)` → `settingsNavEntry(key)` | 高：一处一词替换，无缩进重排 |
| 对话框挂载 | `RYDialog.kt` 一处 `if (visible) { ProvideUiTextScale { AlertDialog(...) } }` | 高：22 处对话框的**唯一**注入点 |
| 5 处直调 `AlertDialog` | `TextFieldDialog.kt`(第 3 个重载) / `SubscribeDialog.kt` / `GroupConfigurationDialogs.kt`(3) → 改走 `RYDialog(visible = true, …)` | 中：三者的参数都是 `RYDialog` 的子集，替换后删掉 `AlertDialog` import |
| 绕过 typography 的 3 处硬编码 sp | `SettingItem.kt` / `SelectableSettingGroupItem.kt` / `Banner.kt`：`.copy(fontSize = 20.sp)` → `.copy(fontSize = uiTextScaleSp(20.sp))` | 高：单参数替换 |
| 设置项本体 | `ColorAndStylePage.kt` 新增 `UiTextScaleItem()`（「外观」分区 `basic_fonts` 之后） | 高：新增一个私有 `@Composable` + 1 行调用 |
| 偏好注册 | 6 处（见 §2.6）：`UiTextScalePreference.kt` / `DataStoreExt.kt` ×4 / `Settings.kt` / `Preference.kt` / `SettingsProvider.kt` | — |

**为什么作用域是「19 条设置路由 + `RYDialog`」而不是包住 `MaterialTheme`。**
`ArticleItem` / `FeedItem` 的文字是 `MaterialTheme.typography.<slot>.withFlowListStyle()`，
基数取自 typography 槽位 → 全局换 typography 会让列表字号**乘两遍**（一遍 typography、
一遍列表偏好）。所以刻意只覆盖设置类页面与对话框，`Feeds` / `Reading` / `Startup` / `else`
四条路由保持裸 `NavEntry`。详见 §2.2。

**为什么包 `entryProvider` 或 `NavDisplay` 都不行。**
`NavEntry` 是 **class**（不是函数），签名
`NavEntry(key, contentKey = …, metadata = …, content: @Composable (T) -> Unit)`；
`entryProvider` 的 lambda 只是**捕获**了 content，真正执行它的是 `NavDisplay` 自己的 composition。
composition local 在**运行处**解析，因此作用域必须按路由给（`settingsNavEntry`），
在外面包一层是无效的 —— 这处很容易想当然。

**滑块为什么只在 `onValueChangeFinished` 写偏好，不在每帧写。**
该页面本身正被这个设置改变大小 —— 拖动中若即时落盘，上方所有行会跟着重排，
控件会从手指下滑走。所以 `onValueChange` 只更新本地 `value` 用于显示读数，
`onValueChangeFinished` 才 `UiTextScalePreference.put(...)`。

**范围为什么从 100 起、不向下。** 这个功能的命题是「大屏上界面太小」，不是「比原生更小」。
`min = 100` / `max = 150` / `default = 100`。默认值 = 100 且 `scale == 1f` 时
`scaledTypography()` 直接 `return base`（**同一个实例**，连 `copy` 都不做），
所以手机端与上游逐像素一致 —— 与缩放层 `Compact` 恒等是同一个约束，见 §2.3。

**离线核验（push 前，本机无 JDK）**：22 个 Kotlin 文件括号平衡全过；import 可达性 10 个 watched 符号全过；
`strings.xml` 三个 locale（`ui_text_scale` / `ui_text_scale_desc`）XML 良构且键存在；
关键声明断言（`ProvideUiTextScale` / `settingsNavEntry` / `uiTextScaleSp` / `scaledTypography` /
`uiTextScaleFactor` / `LocalUiTextScale` / `coerceToRange`）全过；
偏好注册 6 处逐点计数正确（`const val uiTextScale` 恰好 2 处、其余各 1 处）；
`AppEntry.kt` 计数 19 处 `settingsNavEntry(key)` + 4 处裸 `NavEntry`（另 1 处是注释）；
5 处 `RYDialog(visible = true,` 且无残留直接 `AlertDialog(` 调用。
方法与脚本见技能 `android-edit-verify-offline`。

**本轮修掉的两个校验脚本缺陷**（假阳性会掩盖真问题，所以一并修）：
1. `cmd_imports` 原来在**原文**里找符号使用 → KDoc 散文与 KDoc 链接里的符号名被当成真实使用，
   一次报了 5 个 `FAIL` 全是假阳性。改为先 `strip_kotlin(src)` 再匹配。
2. `declared` 原来只认 `class|object|interface|fun|val|var` 后的裸标识符 → 扩展函数
   `fun Int.coerceToRange()` 被捕获成 `Int`，导致该符号被判为"未声明"。补上扩展函数模式
   `\bfun\s+[\w.<>?, ]*\.\s*(\w+)\s*\(`。
