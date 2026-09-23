# FORK-NOTES.md — 跟随上游的检查单

这个 fork 相对 `upstream/main` 有两类改动：**平板适配**（本文档）和 **CI**（`manual-build.yaml`、
`release-build.yaml`、`fork-unit-tests.yaml`、`fork-auto-release.yaml`，纯新增文件，永不冲突）。

跟随上游时不要重读 diff。按下面的顺序做，每一步都有明确的"看什么、为什么"。

- **基线**：上游合并点 `d2b979cc`
- **本文档更新于**：2026-09-23（发布改为 push 全自动 → §7；上游同步流程 + 别点网页 Sync fork → §8）

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
| 新增偏好 | `ui/ext/DataStoreExt.kt` + `preference/{Settings,Preference,SettingsProvider}.kt` | 加一项要同时改 5 个地方，见 §2.6 |

**新增文件（永不冲突）**：`ui/adaptive/{AdaptiveLayout,AdaptiveContentPadding,AppSizeClass}.kt`、
`ui/component/ListFonts.kt`、`infrastructure/preference/{Feeds,Flow}{Fonts,TextFontSize}Preference.kt`、
`ListFontsPreference.kt`、`MarkAsReadButtonPositionPreference.kt`、
`MarkAllAsReadWithoutConfirmPreference.kt`、四个 workflow、3 个单测。

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

### 2.3 限宽的行为边界

- [ ] `AdaptiveLayoutSpec.Compact` 仍是「不做任何适配」：gutter 恒为 0。
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

### 2.6 新增一个设置项要改的 5 个地方（漏一个就静默失效）

加一个偏好（布尔 / 整数）**必须**同时改下面 5 处。少任何一处，要么编译不过，要么"设置能点、
但怎么点都不生效"，而且**没有任何报错**。

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
- 也就是说 **本 fork 目前 21 ahead / 0 behind —— 现在没有任何东西需要同步**，
  这份流程是给上游下次发版时用的。
- 上游的更新节奏是**低频**：2026-08-11 之后停滞；再往前是 7 月初、6 月初、5 月中各几个提交，
  其中相当一部分是 Weblate 翻译和 docs/CI 改动。所以"跟着上游跑"的成本本身就不高。

### 8.5 不要做的

- ❌ 点网页 `Sync fork` 里的 **`Discard N commits`** —— 等于放弃本 fork 的全部提交。
- ❌ `git rebase upstream/main` + `git push --force` —— 会打乱已发布 tag 与提交的对应关系。
- ❌ 试图让 workflow 全自动 merge 上游 —— 冲突时它只会失败，而 §2 那份核验清单
  （字号基线、pane 内边距、顶部栏图标）**本来就必须人工过一遍**，自动合进来反而是隐患。
  可选的是"自动**检测**上游是否更新"，但合并要人来做。
