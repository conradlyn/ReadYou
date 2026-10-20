# 阅读页快速滚动卡顿 —— app 侧可优化项

> 分析日期 2026-10-12，基线 commit `d4d09b47`（`v0.16.2-tablet.10`）。
> 目标设备：华为 MatePad Mini / 鸿蒙（APK 兼容方式运行）。
> 现象：阅读页**快速**滚动不够流畅，慢速正常；功能无异常。
>
> **本文的写法约定**：标「**事实**」的是从代码直接读出来的、可复核的结论（给文件:行号）；
> 标「**推断**」的是据此推出的因果，**必须真机验证后才能当结论用**。
> 本机没有 JDK / Android SDK / gradle 缓存，跑不了 `./gradlew`，也无法采样真机帧时间——
> 所以本文的定位是「把假设收敛到少数几条、并给出可执行的验证方法」，不是「已经证明的结论」。
>
> **归属先说清楚**：以下问题**几乎全部来自上游**，不是本 fork 的平板适配引入的。
> 与 `upstream/main` 逐文件对比结果：`WebViewLayout.kt` / `PullToLoad.kt` / `Image.kt` /
> `VerticalScrollIndicator.kt` **完全相同**；`RYWebView.kt` 只差 +2 行（`paragraphIndent` 传参）、
> `Content.kt` 差 +14 行（滚动条、缩进）、`HtmlToComposable.kt` 差 +13/−1。

---

## 0. 结论速览

**有优化空间。但最大的一块不是"调参"，而是「谁在滚动」这个结构选择。**

阅读页有两条渲染链路，默认走的是 WebView：

| | 路径 | 谁负责滚动 | 默认值 |
|---|---|---|---|
| ① | **WebView** | **Compose**（`verticalScroll`），WebView 被撑成整篇高度 | ✅ `ReadingRendererPreference.default = WebView` |
| ② | **原生组件** | Compose `LazyColumn`（真虚拟化） | 需手动切换 |

### 0.1 请先做这一件事：5 分钟零成本 A/B

**设置 → 阅读样式 → 渲染器 → 切到「原生组件」**，然后用同样的手势快速滚动同一篇文章。

它能把问题一刀切成两种完全不同的走向：

- **切过去明显顺了** → 主因是 ① 的结构问题（§B），顺手把 ② 自身的小毛病按 §C 收掉即可。
- **切过去一样不顺** → 主因在设备/兼容层（§D），或两条链路共有的东西（§A）。先做 §A。

**这一步的价值高于本文其它任何一条**：它用 5 分钟替代了对渲染管线的猜测。

> **已做（2026-10-12）**：结果是「原生明显更顺」，且 §A 的 A1/A2/A3 修复后 **WebView 默认链路也已流畅**。
> 详见 **§0.4**；它同时**推翻了本文对 B1 的建议**。

### 0.2 条目总表

| 编号 | 条目 | 收益 | 风险 | 置信度 |
|---|---|---|---|---|
| A1 | 每次重组重建整篇 HTML 并整体重载 WebView | **大（已实测兑现）** | 低 | **事实 + 已实测** | ✅ 已修（`e0afb490`），**收益已确认** |
| A2 | 整篇正文被 `Log.i` 写进 logcat，release 也没剥 | 中 | 无 | **事实** | ✅ 已修（`e0afb490`） |
| A3 | WebView 从不 `destroy()`，换主题还换新实例 | 中（内存） | 无 | **事实** | ✅ 已修（`e0afb490`） |
| B1 | WebView 撑满整篇高度、滚动交给 Compose | 中（**A1 修完后已非瓶颈**） | 中高 | 机制成立，**但实测已不需要** | ❌ **建议不做**（见 §0.4） |
| C1 | ② 链路：`SelectionContainer` 直接包 `LazyColumn` | 待测 | 低 | 推断 |
| C2 | ② 链路：`Jsoup.parse` 在 LazyColumn 的 content lambda 内 | 中 | 低 | 事实；触发频率待测 |
| C3 | ② 链路：图片子组合 + 组合期读 `painter.state` 导致高度跳变 | 中 | 低 | 事实；放大程度待测 |
| C4 | ② 链路：长图按「宽 × 10 倍宽」的目标尺寸解码 | 小 | 低 | 事实 |
| D | 鸿蒙 APK 兼容层的渲染上限 | —— | 改不动 | **事实** |

### 0.3 A1/A2/A3 已实施（提交 `e0afb490`，2026-10-12）

三项都已改完并推送，**渲染结果与改前逐位相同**（依据见下面的等价性论证）：

| 编号 | 改法 |
|---|---|
| A1 | `loadDataWithBaseURL` 加了守卫 —— 只在文档的四个输入（`stylesheet`/`script`/`content`/WebView 自己的 `url`）任一变化时才重载。同时把 `stylesheet` 与 `script` 用 `remember` 按其来源设置缓存，**快速路径上这两个大字符串也不再重建** |
| A2 | 删掉三处 `Log.i`，其中一处把**整篇正文**插值进 logcat |
| A3 | 加 `onRelease = { it.destroy() }`；同时去掉 `remember(backgroundColor)` 的 key —— 那个 key 每次换主题都新建一个 WebView，而 `factory` 只跑一次，新实例必被丢弃，等于纯泄漏 |

**A1 的等价性论证**（这是本次唯一有语义风险的一处，故留档）：
`loadDataWithBaseURL` 的产物只由这四个值决定，而 `WebViewStyle.get` 的 **20 个具名实参全部保留**、
其中每一个都是 `remember` 的 key —— 唯一例外是 `tableMargin`，它传的就是 `textMargin`（已在 key 中）。
因此"跳过重载"与"照旧重载"渲染出的文档完全相同。

**仍未做**：B1/C1–C4 需要先做 §0.1 的 A/B 才能决定是否值得动；D 改不动。

### 0.4 真机实测结果（2026-10-12，`v0.16.2-tablet.22`）

设备：华为 MatePad Mini / 鸿蒙。用户在同一篇文章上对比两条链路与两个版本：

| 场景 | 结果 |
|---|---|
| 改前（`v0.16.2-tablet.10`），WebView（默认） | 快速滚动不顺，慢速正常 |
| 改后（`v0.16.2-tablet.22`），**WebView（默认）** | **流畅** ✅ |
| 改后，切到原生组件 | 比 WebView 流畅得多 |

**两条结论，一正一反。**

**① A1 是真凶，且收益已兑现 —— 这条原来是「推断」，现在证实了。**
改动只有 A1/A2/A3 三项，渲染结果逐位相同（§0.3 的等价性论证），**唯一的解释就是 A1**：
原先每次重组都在重建整篇 HTML 并让 WebView 整体重载。修复后 WebView 链路直接变流畅，
说明这个重载**确实在快速滚动时被大量触发**——与"工具栏自动隐藏依赖滚动方向、方向一变就重组"
这条触发路径吻合（快速滚动频繁反向，慢速滚动几乎不反向，这正好解释"快才卡、慢不卡"）。

**② B1 从"建议做"降级为"建议不做" —— 这一条推翻了我上一轮的建议。**
我此前认为 B1（大收益、中高风险）是下一个该动的结构性改动。**实测表明它已不再值得做**：
A1 修完后 WebView 链路已经流畅，而 B1 要把滚动交回 WebView 内部，
属于改动渲染链路本身的结构 surgery，风险中等偏高。**为一个已解决的问题承担结构风险不划算。**

原生组件仍明显更快，说明 B1 的机制（无 tile 预取 / 每帧搬一个真实 View）是**真实存在的剩余差距**，
但它现在只是"天花板更低"，不是"当前痛点"。**若日后仍嫌 WebView 慢，再考虑切默认渲染器，
而不是去改 B1。**

**③ 尚未定论的部分**：这次是主观手感对比，没有帧时间数据。
§E 的第 2–3 步（GPU 呈现模式条形图、`gfxinfo framestats` 第 95 百分位）仍值得做一次，
用于确认"流畅"是否已到 60fps 而非"从卡变成轻微顿挫"。

**仍未做（已按 §0.4 更新）**：B1 **建议不做**；C1–C4 只在把原生组件切成主力时才值得动；D 改不动。


---

## A. 零风险，先做（不论走哪条链路都受益）

### A1 —— `RYWebView` 每次重组都重建整篇 HTML，并让 WebView 整体重新加载

`app/src/main/java/me/ash/reader/ui/component/webview/RYWebView.kt:99-143`

```kotlin
AndroidView(
    modifier = modifier,
    factory = { webView },
    update = {
        it.apply {
            settings.defaultFontSize = fontSize
            loadDataWithBaseURL(                        // ← 整篇重新加载
                null,
                WebViewHtml.HTML.format(
                    WebViewStyle.get(...),    // ~340 行 CSS 模板，每次重新 format
                    url,
                    content,                  // ← 整篇正文
                    WebViewScript.get(...),   // JS 模板
                ),
                "text/HTML", "UTF-8", null,
            )
        }
    },
)
```

**事实**：`AndroidView` 的 `update` 语义是「视图创建后调用，**并在该 composable 后续重组时再次调用**」。
这里 `update` 里做的事是**重建整篇 HTML 文档 + `loadDataWithBaseURL` 全量重载**——WebView 会重新解析
HTML、重新布局、重新发起图片请求。

**事实**：`WebViewStyle.get()` 返回的是一段 ~340 行 CSS 模板（`WebViewStyle.kt:64-404`）经 `format`
拼出的字符串，`WebViewScript.get()` 是另一段 JS 模板，`WebViewHtml.HTML.format(...)` 再把它们与整篇
正文拼成完整文档。即：**每次 `update` 都要在主线程拼出一个几百 KB 的字符串，然后交给 WebView 全量重解析。**

**推断（本报告最需要实测的一条）**：滚动过程中确实存在**由滚动触发**的重组源。

`ReadingPage.kt:79, 84-89, 249-252`

```kotlin
var isReaderScrollingDown by remember { mutableStateOf(false) }

val isShowToolBar =
    if (LocalReadingAutoHideToolbar.current.value) {        // 默认 ON（事实）
        readerState.articleId != null && !isReaderScrollingDown
    } else true

// Modifier.pullToLoad(..., onScroll = { f -> if (abs(f) > 2f) isReaderScrollingDown = f < 0f })
```

`ReadingAutoHideToolbarPreference.default = ON`（**事实**，`ReadingAutoHideToolbarPreference.kt:28`），
所以**用户每改变一次滚动方向，`isReaderScrollingDown` 就翻转一次 → `ReadingPage` 重组**。
`ReadingPage` 重组会重新执行 `AnimatedContent` 的内容 lambda，`RYWebView` 正在这条链上
（`ReadingPage.kt:256` → `Content.kt:110`）。

**如果 `RYWebView` 在这一步没有被 Compose 跳过，后果就是：每次改变滚动方向 = 整篇文章重新加载。**
用户感受到的正是「滚到一半突然卡一下」甚至滚动位置跳动。

> 为什么标「待实测」：Strong Skipping 会按参数 `equals` 跳过子树。`RYWebView` 的参数看起来都可能相等
> （`content` 是同一个 String、`modifier` 结构性相等），**理论上会被跳过**。但这条链上还挂着
> `pullToLoad` 的嵌套滚动回调、`scrollIndicator` 的 modifier、以及十几个 CompositionLocal，
> 靠读代码判定「一定跳过」不可靠。**用 Layout Inspector 看 `RYWebView` 的重组次数即可定论。**

**改法（语义等价、不改变外观）**：让「重建 HTML」只在输入真的变了时发生——在 `update` 里比较一个
由 `content / fontSize / 字体 / 颜色 / ...` 组成的 key，相同就直接 `return`；或把
`loadDataWithBaseURL` 移到 `LaunchedEffect(content, …)` 内。`factory` 不动，WebView 实例继续复用。

### A2 —— 整篇正文被写进 logcat，且 release 包没有剥掉

`RYWebView.kt:104-106`

```kotlin
Log.i("RLog", "maxWidth: ${maxWidth}")
Log.i("RLog", "readingFont: ${context.filesDir.absolutePath}")
Log.i("RLog", "CustomWebView: ${content}")     // ← 整篇正文
```

**事实**：`app/proguard-rules.pro` 里**没有** `-assumenosideeffects class android.util.Log { ... }`
一类的规则（全文件只有 `-keep` / `-dontwarn`），所以这些 `Log.i` **在 release 包里原样存在**。
对照依据：`PERFORMANCE-AUDIT.md` C1/C2 记录的两条 keep 规则会让 R8 优化基本失效，日志自然也不会被剥。

**后果**：每次 `update` 都要把整篇正文拼成字符串并跨越 logd 进程写入。正文常见几十 KB，长文上百 KB。
与 A1 是同一个触发频率，属于纯浪费。旁证：这三行是调试残留——`readingFont` 实际打的是 `filesDir`，
`maxWidth` 没有任何消费方。

**改法**：直接删这三行。若要保留诊断能力，改为仅 `BuildConfig.DEBUG` 下输出，且**不要打正文**。

### A3 —— WebView 从不 `destroy()`，而且换主题会再建一个

`RYWebView.kt:73-90, 99`

```kotlin
val webView by
    remember(backgroundColor) {                        // ← key 里含 backgroundColor
        mutableStateOf(WebViewLayout.get(context = context, ...))
    }
...
AndroidView(
    modifier = modifier,
    factory = { webView },
    update = { ... },
)                                                      // ← 没有 onRelease
```

**事实**：
1. `AndroidView` **没有传 `onRelease`** → WebView 离开组合时不会被 `destroy()`。`WebView` 持有
   Chromium 侧的渲染资源与 Activity context，不 destroy 就继续留着。
2. `remember` 的 key 含 `backgroundColor`，而它由 `MaterialTheme.colorScheme.surfaceColorAtElevation(...)`
   算出（`:50-51`）。**换主题 / 换阅读配色就会生成一个新 WebView**，旧实例因为 (1) 永远不会被回收。
3. 另外 `ReadingPage` 外层的 `AnimatedContent(targetState = readerState)` 意味着**切换文章也会重建这棵子树**，
   于是**每读一篇文章就多一个不会被销毁的 WebView**。

**后果**：读的文章越多、换过的主题越多，驻留的 WebView 越多 → 内存压力与 GC 抖动 → 滚动掉帧。
这条在内存较小的设备上更明显。

**改法**：给 `AndroidView` 加 `onRelease = { it.destroy() }`；去掉 `remember(backgroundColor)` 这个 key，
背景色改用 `update` 里的 `setBackgroundColor` 更新，不需要重建实例。

---

## B. 结构性：WebView 撑满整篇高度，滚动交给 Compose

`Content.kt:93-123`

```kotlin
Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState), ...) {
    Column(modifier = Modifier.then(maxWidthModifier)) {
        Spacer(Modifier.height(64.dp))
        headline()
        RYWebView(modifier = Modifier.fillMaxSize(), content = content, ...)   // ← 撑成整篇高度
        Spacer(Modifier.height(128.dp))
        ...
    }
}
```

**事实**：`verticalScroll` 传给子项的约束是 `maxHeight = Infinity`，因此这个位置上的
`Modifier.fillMaxSize()` 等价于「按内容高度撑满」——**WebView 的 View 高度 = 整篇文章的高度**
（长文可以到几万 px）。滚动由外层这个 Compose `Column` 完成，WebView 自己不滚
（`WebViewLayout.kt:23` 虽然设了 `isVerticalScrollBarEnabled = true`，但它已经撑满，内部没有可滚动空间）。

**推断（机制清楚，幅度待实测）**：这条**最贴合你描述的现象**——「**快速**滚动才不流畅，慢速没问题」，
因为代价随滚动速度增长：

1. **每帧都在搬一个真实 Android View。** Compose 滚动 `AndroidView` 靠重新摆放这个 View，
   而不是像纯 Compose 内容那样只改绘制偏移，两者不是一个量级。
2. **Chromium 收不到滚动信号。** WebView 认为自己一直停在滚动位置 0，而实际可见区域在变。
   浏览器内核的分块栅格化（tiled rasterization）依赖**自己的**滚动位置来预取/回收 tile；
   拿不到滚动信号就只能被动重画新露出的区域 → **快速滚动时新 tile 的栅格化赶不上帧**。
   这一条天然是「越快越明显」。
3. **GPU 层面两套渲染在争资源**：WebView 是硬件加速的独立 surface/functor，Compose 是 Skia 层。

**两条改法：**

- **B1（正确解，改动大）**：让 WebView 自己滚——把它约束到视口高度、打开其内部滚动，滚动交给 Chromium。
  这样走的正是浏览器为快速滚动优化过的那条路径。代价是现在所有依赖 Compose 滚动的功能都要改读
  `webView.scrollY`：

  | 受影响的功能 | 现在读什么 | 位置 |
  |---|---|---|
  | 右侧滚动条 | `scrollState` | `Content.kt:98` |
  | 顶部「已滚动」分割线 | `scrollState.value >= 120` | `ReadingPage.kt:221-226` |
  | 工具栏自动隐藏 | 嵌套滚动回调 `onScroll` | `ReadingPage.kt:249-252` |
  | **上/下篇拉拽切换** | `Modifier.pullToLoad` 的 `NestedScrollConnection` | `PullToLoad.kt:56-96` |

  最后一条是硬骨头：**自滚的 WebView 不会向 Compose 派发嵌套滚动事件**，`PullToLoad` 的整条机制要换成
  监听 `WebView.onScrollChanged`（或自定义 `OverScroller` 边界回弹）。**这是必须真机反复调的改动，不能盲改。**

- **B2（省事解，推荐优先）**：**直接用 ② 原生组件渲染器**。它本来就是纯 Compose——`LazyColumn` 真虚拟化，
  滚动走 Compose 最擅长的那条路，没有 interop 层每帧搬 View 的开销。§0.1 的 A/B 就是在验证这一点。
  若 ② 顺滑，那么「优化」就变成「把 ② 的小毛病修掉、并考虑把它设为默认」，而**不必去动 ① 的滚动机制**。

  > 注意：把默认渲染器从 WebView 改成 NativeComponent 是**产品决策**，不只是技术决策——
  > 两条链路的排版能力不同（例如 CSS 能力 vs Compose 富文本能力）。

---

## C. ② 原生组件链路的问题（若它成为主力，这些要一起修）

### C1 —— `SelectionContainer` 直接包住 `LazyColumn`

`Content.kt:127-128`。文字选择需要注册所有可选中的文本并跟踪其位置；`LazyColumn` 的卖点恰恰是
「只组合可见项」。这两个机制的交互在 Compose 里是需要实测的经典场景。**本文不做断言**，
但它是 ② 链路实测时应该第一个观察的对象。

### C2 —— `Jsoup.parse` 在 `LazyColumn` 的 content lambda 里同步执行

`ui/component/reader/Reader.kt:39-47` → `ui/component/reader/HtmlToComposable.kt:63-83`

```kotlin
fun LazyListScope.htmlFormattedText(inputStream: InputStream, ...) {
    Jsoup.parse(inputStream, null, baseUrl)?.body()?.let { body -> formatBody(...) }
}
```

**事实**：整篇 HTML 的解析、以及「HTML → item 列表」的转换，是在 `LazyColumn` 的 content lambda 里
同步（主线程）完成的。这个 lambda 的作用是构造 item provider；`LazyColumn` 重建 provider 时就会重跑。

**推断（待实测）**：如果该 lambda 的实例在重组之间不稳定——它捕获了 `context` 等值
（`Content.kt:141-149`）——那么 `Content` 每次重组都会重建 provider → **整篇 HTML 重新解析**。
长文解析是毫秒到几十毫秒级，足够掉一帧。

**改法**：把解析结果提到 `remember(content, …)` 里（先解析成中间数据结构，再交给 content lambda 消费），
让解析只与 `content` 绑定。

### C3 —— 图片：`BoxWithConstraints` 子组合 + 组合期读 `painter.state` 造成高度跳变

`ui/component/reader/Image.kt:44, 69, 138-152`

**事实**：
- `ArticleImage` 外层是 `BoxWithConstraints`（第 44、69 行）。它走的是**子组合（subcomposition）**，
  在懒加载列表里按图片数量堆叠，是实打实的开销。
- 第 138 行在**组合期**读 `painter.state`：加载中/失败时渲染一个 `aspectRatio(16 / 9f)` 的占位块
  （第 141-148 行）。图片加载完成后占位块消失、改按真实宽高比布局 → **该 item 的高度发生变化**。

**推断**：`LazyColumn` 中 item 高度在滚动过程中变化 → 需要重新测量；若变化发生在视口上方，
滚动位置会跳动。快速滚动 = 单位时间内更多图片进入视口、更多次高度变化——
**这又是一个「越快越明显」的机制**，与 B1 的第 2 点共同解释了「快速才卡」。

**改法**：占位块用更接近真实比例的比例（或按 `ImageCandidates` 里 `srcset` 的宽度描述符推断），
把占位与真实的尺寸差压到最小；`BoxWithConstraints` 换成普通的 `Box` + 已知的内容宽度。

### C4 —— 长图会按很大的目标尺寸解码

`Image.kt:156-164`：`maxImageSize()` 把高度限制在「宽度的 10 倍」。在 `LazyColumn` 的 item 里
`maxHeight` 是无界的，`maxHeight.toPx().roundToInt()` 会得到 `Int.MAX_VALUE`，再被
`coerceAtMost(10 * maxWidthPx)` 收住 → 传给 Coil 的目标尺寸是「**宽 × 10 倍宽**」。
对漫画/长图类内容，这意味着按非常大的尺寸解码。

不是本次卡顿的主因（除非你订阅的源多长图），但属于同一类问题的储备项。

---

## D. 天花板：鸿蒙的 APK 兼容层

必须说清楚：**这个 APK 跑的不是 AOSP，是鸿蒙的 Android 兼容层。** 这意味着

- 这台机器上 WebView 背后是否是可用的 Chromium，以及 GPU 加速是否真的生效，取决于该机型兼容层的实现；
- Compose 的 Skia → GPU 合成路径与 AOSP 的 HWUI 路径不同；
- 兼容层通常拿不到与原生应用同级的调度 / GPU 优先级。

**所以有一部分卡顿是 app 侧改不动的。** 判断方法见 §E：若两条渲染链路都慢、且 GPU 呈现条形图普遍
超 16 ms 并随滚动速度升高，主因就在这一层。

最直接的对照方法：**同一个 APK 装到原生 Android 平板上跑同一篇文章**。这一比较能干净地把
「app 的问题」与「兼容层的问题」分开。

---

## E. 验证方式（本机没有 JDK/SDK，只能靠设备）

按顺序做，**每步只改一个变量**：

1. **§0.1 的 A/B 切换渲染器** —— 免费，先做。
2. **开发者选项 → GPU 呈现模式 → 条形图**：看绿线（GPU）/ 蓝线（绘制）是否普遍超 16 ms，
   关键是看它**是否随滚动速度升高**。这是区分「渲染带宽瓶颈」与「CPU 重组瓶颈」最快的办法。
3. **`adb shell dumpsys gfxinfo <pkg> framestats`**：取一段滚动过程的帧时间分布，看第 95 百分位。
4. **Layout Inspector 的重组计数**：滚动时看 `RYWebView` / `Content` 各重组了多少次——
   **这直接决定 A1 是不是真问题**，是本文最值得先测的一项。
5. **`adb shell dumpsys meminfo <pkg>`**：若 A3 成立，反复开关文章后 WebView 相关对象数会单调上升。
6. **改代码后的离线核查**：技能 `android-edit-verify-offline` 的括号平衡扫描 + 断言脚本；
   再由 CI 的 Unit Tests + Build Commit 兜底。

---

## F. 明确不做

- **不要指望 baseline profile 治好这个。** 它减少的是首次执行的 JIT 抖动，属于启动/首屏收益
  （见 `PERFORMANCE-AUDIT.md` C3）。本问题看起来是**渲染带宽**问题，不是 JIT 问题。
  若 §A/§B/§C 做完仍不够，再考虑——而且治滚动路径必须用**真机** profile，手写那份额覆盖不到滚动。
- **不要动 B1（把滚动交回 WebView）** —— 这是 §0.4 实测后的结论：A1 修完 WebView 链路已流畅，
  为已解决的问题做结构 surgery 不划算。原生组件更快是**真实存在的天花板差距**，
  要提升它应该**切默认渲染器**，而不是改滚动结构。
- **不要为了这个重写自研 reader 的解析/排版算法本身。** 解析是 O(文章长度) 的，问题不在算法复杂度，
  而在**它被执行了多少次**（见 C2）。要修的是「别重复执行」，不是「换算法」。

---

## G. 来源与时效

| 事实 | 来源 | 日期 |
|---|---|---|
| 默认渲染器 = WebView | 代码 `infrastructure/preference/ReadingRendererPreference.kt:37` | 2026-10-12 |
| 工具栏自动隐藏默认 ON | 代码 `ReadingAutoHideToolbarPreference.kt:28` | 2026-10-12 |
| 图片最大化默认 ON / 段落缩进默认 ON | `ReadingImageMaximizePreference.kt:28`、`ReadingParagraphIndentPreference.kt:31` | 2026-10-12 |
| `Log` 未被 R8 剥离 | `app/proguard-rules.pro`（无 `assumenosideeffects` 规则） | 2026-10-12 |
| AndroidManifest 未关闭硬件加速 | `app/src/main/AndroidManifest.xml`（无 `hardwareAccelerated` 属性 → 默认 true） | 2026-10-12 |
| 与上游的差异逐文件对比 | `git diff upstream/main -- <file>` | 2026-10-12 |
| 全部行号 | 本机静态审读 | 2026-10-12 |

**待真机确认、暂不可当结论用的推断**：A1 的实际触发频率、B1 在总代价中的占比、C1/C2/C3 的实际影响幅度。
