# 移植为 Windows 桌面版 — 可行性评估

- **评估对象**：`conradlyn/ReadYou` @ `65ac6410`
- **评估日期**：2026-09-24
- **代码基线**：388 个 Kotlin 文件，**43,956 行**，365 个 `@Composable`，**100% Jetpack Compose**（`res/` 下无 `layout` 目录）
- **定量依据**：技能 `android-to-desktop-port-assessment` 的 `scan_android_coupling.py`（本文件所有计数均为其实测输出）
- **上游参考**：`HARMONYOS-PORT-ASSESSMENT.md`（同一项目的另一目标平台，可对比阅读）

---

## 0. 结论

**可行。而且比鸿蒙方案容易一个量级 —— 但「移植」这个词不准确，实际是「以现有 Compose 代码为基础，新增一个 Windows 桌面 target」。**

三句话：

1. **Windows 桌面 = JVM。** `rome`（RSS/Atom/JSON Feed）、`readability4j`（正文提取）、`opml-parser`、`jsoup`（阅读页 HTML 解析）、`okhttp` + `retrofit`、`javax.crypto` 这些 **JVM 库原样可用**，不必像鸿蒙那样逐行重写；Compose UI 是**同一套 API**，代码可复制过来改，不是翻译成另一种语言。
2. **三个真正的硬点**：`Hilt`（无 KMP 且不会支持）、`WorkManager`（无 KMP，且承担核心同步）、**阅读页 WebView 模式的 JS 注入桥**（`android.webkit` → KCEF）。
3. **成本 19–29 周 ≈ 4.5–6.5 人月**（单人 + AI 辅助）。

**前置提醒**：WSA 已于 **2025-03-05 终止支持**，Windows 上**没有「直接跑 APK」的 0 成本路径**。这点与鸿蒙 6.1（仍可装 APK）**相反**。若目标只是「在 Windows 上读 RSS」，第三方模拟器（BlueStacks / MuMu）当天可用，但它产出的是跑在窗口里的 Android 平板应用，不是 Windows 应用。

---

## 1. 先定义「Windows 版」的三种含义

不同含义的成本差是 0 与 4.5–6.5 人月，必须先对齐。

| 含义 | 具体做法 | 开发成本 | 是不是「Windows 版」 |
|---|---|---|---|
| 在 Windows 上跑现有 APK | **WSA —— 已死** | — | 曾经是，现已不可用 |
| 用模拟器跑现有 APK | BlueStacks / MuMu / LDPlayer | **0** | 不是。Android 应用跑在模拟器窗口里 |
| 原生 Windows 桌面应用 | **Compose Multiplatform desktop** | **4.5–6.5 人月** | 是 |

### 1.1 「原生 Windows 应用」到底指什么（最常见的疑问）

**疑问**：「移植过去，难道还跟着 Android 的底层走？不应该重写成原生的 Windows 应用吗？」

**先把两件事分开 —— 它们是正交的：**

| | 说明 | 复用? |
|---|---|---|
| **源码与 UI 框架** | 388 个 Kotlin 文件、Compose 的 API 形状 | ✅ 复用 |
| **平台底层** | Android Framework / ART / Linux 内核 | ❌ **一点都不复用** |

**Compose Multiplatform 桌面版里没有任何 Android 运行时**：没有 `android.*`、没有 `androidx.activity`（`androidx.compose.*` 的 KMP 制品是纯 JVM 代码）、没有 ART/Dalvik、没有 dex。它跑在 **JVM** 上，用 **Skiko（Skia）** 做 GPU 合成渲染 —— 与 Chrome 同一个渲染引擎，绘制进一个真实的 **Win32 窗口**（不是 Swing 控件）。

之所以能复用，是因为 **Compose 本身就不是 Android 的库**：它是 JetBrains 的纯 Kotlin Multiplatform 框架，Google 只是拿它做 Android 的新 UI 工具包。真正 Android 专属的是 `androidx.activity`、`Hilt`、`WorkManager`、`android.webkit` 这一类 —— §3.3 那 21% 「必须重写」正是在处理它们。

**「原生」不是一个是 / 否，而是六个可分别满足的层次：**

| # | 层次 | CMP 桌面版 | 说明 |
|---|---|---|---|
| 1 | 不跑在模拟器 / 兼容层里 | ✅ | 真实 Win32 进程 |
| 2 | 产出真正的 `.exe` / `.msi` | ✅ | Compose Gradle 插件封装 `jpackage` |
| 3 | 用户不需要另装运行时 | ✅ | `jlink` 裁剪的 JRE 随包分发 |
| 4 | 托盘 / 通知 / 文件关联 / 深链 | ✅ | Compose 桌面 API（`Tray`、`MenuBar`、`FileDialog`） |
| 5 | 用微软官方 UI 技术栈 | ❌ | **这条只有 WinUI 3 满足** |
| 6 | 体积接近 .NET AOT（约 9–30 MB） | ❌ | 约 50–80 MB（含裁剪 JRE） |

**→ 若你要的「原生」指第 1–4 条：Compose Multiplatform 就是原生 Windows 应用，不必重写成 C#**，且代码可复用 79%（§4）。这是唯一现实路径。

**→ 若指第 5 条**（微软官方技术栈、Fluent Design 外观、MSIX 上架），则必须 **WinUI 3 + C# 全量重写**。这条路的隐性成本常被低估：

| 全量重写会丢掉什么 | 原因 |
|---|---|
| 整个数据层要重做 | `rome`（RSS/Atom/JSON Feed 解析）、`readability4j`（正文提取）、`jsoup`（阅读页 HTML 解析）**全是 JVM 库**。JS / Rust 侧替代品成熟（`@mozilla/readability` 比 `readability4j` 更权威、`feed-rs` 已有生产先例，见 §7.1），但 **C# 侧要换 AngleSharp / SmartReader，生态更薄、行为不等价** |
| 阅读页排版逻辑重做 | `ui/component/reader`（1,930 行）的 `jsoup` → `AnnotatedString` 链路要整体翻译成 XAML `RichTextBlock` |
| 你这个 fork 的全部自定义功能重做 | 平板自适应、全部已读按钮位置、标题字体分项、自动拉全文 —— 都是 Compose 代码 |
| 工作量 | **≥ 10 人月**（vs 4.5–6.5），且全程在陌生技术栈里 |

折中方案「Kotlin/JVM 后端（Ktor）+ C#/WinUI 3 前端」把一套应用拆成两个技术栈、两次进程间通信，**没有理由优于方案 A**（同 §7 方案 C 结论）。

> 「原生」在桌面语境里被滥用了。**Electron / Tauri / Flutter 用的也不是微软官方技术栈**，但没人会说它们「不是 Windows 应用」。判断标准是「能否作为普通程序安装、运行、与系统交互」，而不是「用的是不是微软的框架」。

**WSA 的结局（已核验）**：微软 2024-03-05 宣布废弃，**2025-03-05 从 Microsoft Store 下架并终止支持**；运行时组件被停用，**装得上但应用启动不了**，官方明确不再修复，无注册表 / PowerShell 可恢复。Amazon Appstore 同步停止。→ 这条路彻底关闭。

---

## 2. 为什么 Windows 比鸿蒙容易一个量级

| 维度 | 鸿蒙（见 `HARMONYOS-PORT-ASSESSMENT.md`） | **Windows 桌面** |
|---|---|---|
| 运行时 | ArkCompiler（**非 JVM**，严格 AOT） | **JVM** |
| 语言 | ArkTS（TypeScript 超集，**需重写**） | **Kotlin 不变** |
| UI 框架 | ArkUI（**需重学**，Compose 被官方列为重难点） | **Compose（同一 API，可复制）** |
| 现有 JVM 库 | 全部重写（无 JVM） | **原样可用** |
| 富文本排版 | ArkUI `StyledString` 能力弱于 Compose | Compose 的 `AnnotatedString` / inline content **本身就是强项** |
| 能直接跑 APK | **6.1 能（成本 0）** | **不能（WSA 已终止）** |

一句话：**鸿蒙的问题是「换掉整个技术栈」，Windows 的问题是「换平台 API + 换 DI + 换调度」**。差距不在总时长（26–35 周 vs 19–29 周），而在**风险**：鸿蒙要在一个陌生框架里踩坑，Windows 全程留在 Kotlin / Compose 里。

---

## 3. 依赖逐项核验

### 3.1 官方支持 KMP 的 Jetpack 库（可直接用）

来源：`developer.android.com/kotlin/multiplatform` 官方 KMP 库清单，**2026-09-09**

| 库 | 本项目用途 | 实测使用面 | KMP 最新版 |
|---|---|---|---|
| **room** | 本地数据库 | 23 文件 / 51 处 | 2.8.5 |
| **sqlite** | Room 的驱动 | — | 2.7.1 |
| **datastore** | 偏好存储 | **78 文件 / 97 处** | 1.2.1 |
| **paging** | 列表分页 | 7 文件 / 18 处 | 3.5.1 |
| lifecycle / viewModel / viewModel-compose | ViewModel | — | 2.11.0 |
| navigation / navigation3 / navigationevent | 路由 | — | 2.10.1 / 1.1.7 |
| compose | UI 框架 | 全项目 | 1.12.1 |
| annotation / collection / savedstate | 基础 | — | — |

**Room 的桌面细节**：推荐 `BundledSQLiteDriver`（自带源码编译的 SQLite，保证 Windows / Android 行为一致），JVM 侧底层是 `sqlite-jdbc`。**KSP 必须按 target 分别声明**（`add("kspJvm", ...)`），不能只在 `commonMain` 加一次；数据库 builder 仍需平台各写一份（Android 用 `Context.getDatabasePath()`，JVM 用普通文件 API）。

**DataStore 是 78 文件引用的大头**，但概念不变，是**成本最低**的一项。

### 3.2 第三方库现状

| 库 | 现状 | 处理 |
|---|---|---|
| **Coil 2.5.0** | Coil 2 **仅 Android** | 升 **Coil 3**（`io.coil-kt.coil3`，KMP，桌面走 Skiko）。`coil-svg` 有 KMP 制品；**`coil-gif` 仍仅 Android** → GIF 需另找方案或砍 |
| kotlinx.serialization | KMP ✓ | 直接用 |
| **jsoup** | 纯 Java | **桌面原样可用** ✓（阅读页 HTML 解析就靠它） |
| rome / readability4j / opml-parser | JVM | **原样可用** ✓ |
| **material3.adaptive** | **CMP 提供**：`org.jetbrains.compose.material3.adaptive:adaptive:1.3.0-beta02`（含 adaptive-layout / adaptive-navigation） | **平板自适应设计可保留** |
| **compose-html** (ireward) | **仅 Android，最后提交 2022-03-08** | 影响面**很小**，见 §3.3 ④ |
| me.saket.swipe / telephoto / android-svg | 仅 Android 的 Compose 库（是否有 KMP 制品**待确认**） | 自实现或换库 |

### 3.3 硬点（决定估算是成立还是崩掉）

#### ① Hilt —— 无 KMP，且作者明确表示不会支持

- **实测使用面**：46 文件 / 95 处，外加 `@HiltAndroidApp`（1 处）、`@AndroidEntryPoint`（**6 处**：MainActivity、CrashReportActivity、ArticleCardWidget、ArticleListWidget、WidgetConfigActivity、WidgetRepository）
- **原因**：依赖 Android 的 `Context` / `Application` / lifecycle 组件，桌面 JVM 上不存在
- **出路**：**Koin**（KMP 原生，CMP 项目事实标准）/ kotlin-inject / Metro
- **性质**：机械但**面积最大** —— 每个 `@Inject` 构造、`@Module`、`@HiltViewModel`、`@AndroidEntryPoint` 都要改写

#### ② WorkManager —— 无 KMP

- **实测使用面**：17 文件 / 65 处，承担 **RSS 定时同步、widget 刷新、通知**
- 它**不在**官方 KMP 库清单里
- **出路**：桌面自建调度（应用内协程定时器，可选与 Windows 任务计划程序集成）。桌面没有 Doze / 后台限制，实现**更简单**，但要自己写；社区有 KMP 封装库，成熟度需自评
- **注意**：定时同步是 RSS 阅读器的**核心能力**，不是边角功能

#### ③ 阅读页 WebView 模式的 JS 注入桥

- `ui/component/webview`（**8 文件 / 895 行**），依赖 `android.webkit`（4 文件 / 10 处）
- 含 `WebViewScript.kt`（注入脚本）、`WebViewStyle.kt`（样式注入）、`JavaScriptInterface` 双向通信
- **出路**：桌面用 **KCEF**（Chromium）。有 `compose-webview-multiplatform` 提供 desktop 实现，但需 JVM `--add-opens` 参数与 ProGuard 规则；**注入脚本与双向通信必须重写**

#### ④ 关于 `compose-html` —— 影响面比直觉小得多，且**不在阅读页**

这一条是本次核实中**修正掉的一个误判**，值得单独说明：

- `compose-html`（`com.ireward.htmlcompose.HtmlText`）**只在 `ui/page/startup/StartupPage.kt` 使用**（2 处调用），即**启动/关于页**，不是阅读页
- 该库确为 Android-only（内部用 `android.text.Html` + `Span/Spannable`）且 2022-03-08 后停更，但**替换成本很小** —— 换掉这 2 处调用即可
- **阅读页不是它的用户**：`ui/component/reader`（**9 文件 / 1,930 行**）是本项目**自研的渲染器**，链路是
  **`jsoup`（纯 Java，桌面原样可用）→ Compose `AnnotatedString` / `LinkAnnotation` / `SpanStyle`（CMP 提供）**
- 该模块的 Android 耦合**只集中在两处**：
  - `Extensions.kt`（**73 行**）：`android.text.Annotation`、`android.text.SpannedString`、`androidx.core.text.getSpans`、`android.content.res.Resources`
  - `Reader.kt`：`android.content.Context`、`android.util.Log`
- → **阅读页主渲染器基本可移植**，改写量以「一个 73 行文件 + 零星几行」计，不是 1,930 行

> 这条修正同时说明：`HARMONYOS-PORT-ASSESSMENT.md` §1.3 把该模块记作「`compose-html` + 自研 reader」**归因不准**（compose-html 属启动页）。但**鸿蒙那篇的结论不受影响** —— 鸿蒙上没有 JVM，`jsoup` 同样不可用，阅读页在鸿蒙侧仍需按 ArkUI 重写。详见 §8。

### 3.4 其他平台耦合（实测）

| 项 | 文件 / 命中 | 处理 |
|---|---|---|
| `stringResource` / `R.string` | **109 文件 / 1,340 处** | 迁到 Compose Multiplatform Resources（`Res.string.*`）；**54 个 `values-*` 目录**要转结构。机械但量大，**单独排期** |
| `LocalContext` | 62 文件 / **138 处** | KMP 无 Context → 需平台抽象层 |
| `android.content.Context` | **128 文件 / 129 处** | 同上。**这是改动面最广的一项** |
| `android.os.*`（Bundle/Handler） | 21 文件 / 28 处 | 逐个替换 |
| `android.app.*`（Service/Notification） | 10 文件 / 12 处 | 桌面通知用 CMP 桌面扩展 |
| `android.content.Intent` | 9 文件 / 9 处 | 分享/打开链接 → 剪贴板 / 文件对话框 / 默认浏览器 |
| `android.net.Uri` | 6 文件 / 6 处 | Coil 3 已提供 `coil3.Uri` |
| `android.text.*` | 4 文件 / 5 处 | 见 §3.3 ④；其余为 TTS / 日期格式化，逐个替换 |
| CustomTabs | 2 文件 / 3 处 | 用默认浏览器 |
| TTS（`android.speech.tts`） | 1 文件 / 2 处 | 桌面 TTS（**待确认**），或砍 |
| **Glance 小部件** | 5 文件 / **69 处** / 1,196 行 | **桌面无等价物** → 砍掉，或改系统托盘 |
| `javax.crypto` | 1 文件 / 3 处 | **JVM 可用 ✓** |
| Coroutines / Flow | 150 文件 / 459 处 | KMP ✓，**原样可用** |

### 3.5 CMP 桌面的事实基线

- **desktop 为 Stable**（Android / iOS / desktop 均 Stable；Web 为 Beta）
- 支持 **Windows 10+（x86-64, arm64）**、macOS 13+、Ubuntu 20.04+
- **只支持 64 位**
- 运行需 **JDK 11+**；**jpackage 打包原生分发包需 JDK 17+**

---

## 4. 代码复用率

| 类别 | 行数（估） | 占比 | 内容 |
|---|---|---|---|
| ① **原样 / 近乎原样** | ~10,500 | 24% | `domain/` 逻辑、`jsoup`/`rome`/`readability4j`/`opml` 的用法、`ui/theme/palette` **1,991 行纯数学**、**`ui/component/reader` 主体（1,930 行，仅 `Extensions.kt` 73 行要改）** |
| ② **复制 + 改造（机械）** | ~24,000 | 55% | Compose UI（`ui/page` 16,326 + `ui/component` 6,905，扣除 reader）：同 API，主要改 `Context`→平台抽象、`stringResource`→`Res`、DI 改写 |
| ③ **必须重写** | ~9,500 | 21% | `component/webview` 895、`ui/widget` 1,196、`infrastructure/android` 1,080、`infrastructure/di` 651，以及调度 / 通知 / 分享 |

**关键提醒**：桌上的 ② 是「**同语言、同 API 的搬迁**」，鸿蒙的对应类别是「**Kotlin → ArkTS 逐行翻译**」，每行成本明显更低 —— 但**不能当零**：1,340 处资源引用 + 138 处 `LocalContext` 都要人手过一遍，且桌面没有 Activity 上下文，语义要逐处确认。

---

## 5. 工作量估算

| 阶段 | 内容 | 估时 |
|---|---|---|
| **0 技术验证** | Room KMP + 桌面驱动、资源迁移样例、Koin 替换 Hilt 的样例、KCEF 嵌入 PoC。**这一步决定后续估算是否成立** | **1–2 周** |
| **1 工程骨架** | KMP 多模块（`commonMain` / `androidMain` / `jvmMain`）、窗口·菜单·快捷键、Koin、Room KMP、DataStore KMP | 2–3 周 |
| **2 数据与同步层** | GReader / Fever / Local 三 provider、RSS 解析、OPML、**同步调度重设计**、通知（JVM 库原样搬，主要成本在调度） | 3–4 周 |
| **3 核心页面** | Feeds + Flow，含 **1,340 处资源迁移** | 4–6 周 |
| **4 阅读页** | `reader` 移植（`Extensions.kt` + Coil 3 适配）+ WebView/KCEF 桥重写 | **2–4 周** |
| **5 设置全树** | 20+ 页面 + palette 移植 + 54 语言资源 | 3–4 周 |
| **6 桌面集成** | 托盘 / 通知 / 文件对话框 / 深链 / 剪贴板；砍 Glance | 2–3 周 |
| **7 自适应 · 测试 · 打包** | 自适应（有官方制品）、测试、jpackage / MSIX 打包与签名 | 2–3 周 |
| **合计** | | **19–29 周 ≈ 4.5–6.5 人月** |

3 人并行（1 UI / 1 数据 / 1 平台）约 **3 ~ 4 个月**。

**为什么不更乐观**：改动面最广的不是某个硬点，而是 **`Context` 128 文件 + `LocalContext` 138 处 + 资源引用 1,340 处**这三项「机械但极广」的搬迁；以及**阶段 3 与阶段 5 都在等阶段 1 的骨架稳定**。

### 不包含

Windows 代码签名证书（EV 证书费用与审核）、Microsoft Store 上架流程、CI 增加 Windows 构建矩阵。

---

## 6. 降本路径（MVP 约 7–9 周）

1. **砍掉** Glance 小部件（1,196 行）、TTS、动态取色、多账号 → 先只做本地账号
2. **阅读页先走自研渲染器**（`component/reader`），把 **WebView / KCEF 模式推到第二期** —— 见下方「与鸿蒙相反的一条」
3. **资源先只做中英两语**，其余 52 个 `values-*` 后补（可半自动转换）
4. **自适应放最后做**（CMP 有 `material3.adaptive` 官方制品，成本低，且可复用你现有的断点设计）

→ 第一期「最小可用」= **Feeds + Flow + 阅读页（自研渲染器）+ 本地账号**，约 7–9 周。

### ⚠️ 与鸿蒙相反的一条

鸿蒙篇建议「**阅读页优先走 WebView 模式**，把自研排版推到第二期」；**桌面版必须反过来**：

| | 鸿蒙 | **桌面** |
|---|---|---|
| 自研 `reader`（jsoup + AnnotatedString） | **要重写**（无 JVM，ArkUI 富文本能力弱） | **基本可移植**（jsoup 是 Java，AnnotatedString 是 Compose） |
| WebView / Chromium 模式 | **先跑通**（ArkUI `Web` 组件现成） | **要重写**（`android.webkit` → KCEF + 注入桥） |

**别把两份文档的建议互相套用。**

---

## 7. 方案对比

| 方案 | 成本 | 代码复用 | 产物体积 | 结论 |
|---|---|---|---|---|
| **A. Compose Multiplatform desktop** | **4.5–6.5 人月** | **79%** | 约 50–80 MB（含裁剪 JRE） | **Kotlin / Compose 团队的唯一现实路径** |
| B. 第三方 Android 模拟器 | **0 开发** | — | — | 只是自用读 RSS 的话，当天可用 |
| C. Tauri 2（Rust + WebView2） | 8–12 人月 | 0% | **5–10 MB** | Web 团队可选，见 §7.1 |
| D. Electron（Node + Chromium） | 8–12 人月 | 0% | 90–150 MB | Web 团队可选，见 §7.1 |
| E. Flutter（Dart） | 8–12 人月 | 0% | 约 25–45 MB | 见 §7.1 |
| F. WinUI 3（C#） | ≥ 10 人月 | 0% | 9–30 MB | 见 §1.1 |

### 7.1 Flutter / Tauri / Electron 能行吗？优势在哪

**能行，而且都不是坏方案。** 但先要更正一个**初版说错了的结论**：

> ❌ 初版写「Electron / Tauri 重写前端 + Ktor 后端 …… **没有理由优于 A**」。这句话下得太快。

**错在哪：高估了数据层的搬迁成本。** 逐项核实后：

| 原 JVM 库 | 用途 | Web / Rust 侧的替代 | 成熟度 |
|---|---|---|---|
| `rome`（RSS/Atom/JSON Feed） | 订阅解析 | **JS**：`rss-parser`（2026-02 仍有提交）、`feedparser`；**Rust**：**`feed-rs` 2.4.0**（2026-07 发布，17.7 万次下载/月，94 个 crate 依赖） | 都成熟 |
| `readability4j`（正文提取） | 拉取全文 | **JS**：**`@mozilla/readability`** —— **Firefox 阅读模式的原始实现**，Mozilla 维护，比 `readability4j`（Java 移植版）**更权威**；Rust 侧有 readability 移植 | 成熟 |
| `jsoup`（HTML 解析） | 阅读页渲染 | WebView 里**根本用不上** —— 内容本来就是 HTML | — |

**并且有真实生产先例**：**Lettura** 就是一个 Tauri 版 RSS 阅读器（Tauri + `feed-rs` + SQLite + Diesel）。**有人已经走通这条路。**

**→ 所以：数据层不是障碍，真正的代价只有「UI 全量重写」**（约 23,000 行 Compose + 1,340 处资源）。而且在某些方面，这三个方案对**这个特定产品**反而更顺手：

| 真实优势 | 说明 |
|---|---|
| **阅读页天然更简单** | RSS 内容本来就是 HTML。`@mozilla/readability` + WebView 直接渲染，**那 1,930 行自研 `jsoup` → `AnnotatedString` 渲染器可以直接扔掉** |
| **Tauri 体积小一个量级** | 5–10 MB vs CMP 约 50–80 MB；空闲内存约 30–50 MB vs Electron 的 120–400 MB |
| **人才池大得多** | Web 技术栈的开发者远多于 Kotlin/Compose；Web 团队写 React 比学 Compose 快 |
| **可同时产出 web 版** | 同一套前端直接就是网页版 |
| **Electron 生态最成熟** | 自动更新（含差分）、代码签名、托盘、深链全有现成方案；VS Code / Slack / Figma 都跑在上面 |
| **Flutter 渲染最一致** | 3.47（**2026-08-12**）起 **Impeller 成为 Windows/macOS/Linux 默认渲染器**，直连 Vulkan、编译期预编译 shader，消除首次动画卡顿 |

**代价同样真实：**

| 代价 | 说明 |
|---|---|
| **零代码复用** | 388 个 Kotlin 文件、23,000 行 Compose UI、1,340 处资源、**本 fork 的全部自定义功能**都要在另一门语言里重做 |
| **Tauri 要写 Rust** | 后端全在 Rust；冷编译慢（实测首次 `tauri build` 7 分 40 秒，拉 600+ crate）；跨平台 WebView 渲染不一致（WebKit vs WebView2 要分别 QA）；**企业版 Windows N SKU 不预装 WebView2** |
| **Electron 体积与内存** | 安装包 90–150 MB、空闲内存 120–400 MB。对一个 RSS 阅读器，这个体量很难解释 |
| **Flutter 桌面生态最薄** | 渲染一致性最强，但桌面系统集成与第三方库丰富度不如前两者；且要学 Dart |
| **要维护两套代码** | 选它们 = 放弃「Android 与 Windows 同源」，以后每个功能写两遍 |

### 决策准则：不看框架，看**做这件事的人**

| 情形 | 推荐 |
|---|---|
| **Kotlin / Compose 背景，想复用现有投资** | **A（CMP）** —— 4.5–6.5 人月，79% 复用，一套代码两个平台 |
| **Web 团队，要做新的跨平台 RSS 阅读器** | **C（Tauri）** —— 体积小，WebView 天然适配 HTML 排版，有 Lettura 先例 |
| **渲染重、UI 一致性要求高** | **E（Flutter）** |
| **只要 Windows，且必须用微软官方技术栈** | **F（WinUI 3）** |

**一句话：这三个方案的优势不在这个项目上，而在「谁来做」上。** 对本项目而言，它们是**用「多花 4–6 人月全量重写」换「Web 生态与 HTML 排版优势」** —— 只有当 Web 侧的生产力显著高于 Kotlin 侧时，这笔交易才划算。

> **一个重要反驳**：即使用 Tauri/Electron，**「HTML 排版优势」也不是它们的独占** —— CMP 桌面版可以嵌 **KCEF（Chromium）**，同样拿到浏览器级 HTML 渲染，代价只是重写注入桥（§3.3 ③）。所以这条优势只值「少写一个桥」。

### 推荐路径

1. **先确认是否真的需要「Windows 原生版」**，以及**谁来做**。只是自用读 RSS，方案 B 当天可用、0 成本；Web 团队做同一件事，§7.1 的 C/E 也成立。
2. 若需要原生版且团队是 Kotlin/Compose 背景：**先做阶段 0（1–2 周）**，只验证三件事 —— Room KMP + 桌面驱动跑通、资源迁移样例可行、Koin 替换 Hilt 无障碍。**花 1–2 周买这个确定性很划算**。
3. 通过后按 §6 的 MVP 路径推进。
4. 若走 C/E/D：跳过阶段 0，直接按「新建一个应用」排期，**不要试图半途迁移**（Electron ↔ Tauri 之间的迁移实测也等同重写）。

---

## 8. 你在 fork 上的投资不会浪费

| 你的成果 | 在 Windows 版上的去向 |
|---|---|
| 平板自适应（断点、限宽、双栏/三栏） | CMP 有 `material3.adaptive` 官方制品 → **设计可保留**，实现成本比 Android 侧还低 |
| 「全部已读」按钮位置偏好 | 纯 Compose + DataStore → 随骨架一起迁，**零额外成本** |
| 标题字体分项（`TitleFontsPreference` 等 4 个文件） | 纯 Compose + DataStore + `FontFamily` → **可直接复制** |
| 打开即自动拉全文 | 走 `settingsProvider` + `readerCacheHelper`（DI / 数据层）→ 随骨架迁移 |
| 性能审计结论（keep 规则、baseline profile） | 桌面不适用（无 ART / dex），但**审计方法**可复用于桌面包体积 |

即：**你这个 fork 的自有功能全部是「纯 Compose + DataStore + ViewModel」，没有一处依赖 Android 独有 API。** 这是它相对上游更容易跨平台的一个意外优势。

### 对 `HARMONYOS-PORT-ASSESSMENT.md` 的一处归因修正

该文 §1.3 将阅读页排版记为「`compose-html` + 自研 reader，1,921 行」，**归因不准**：`compose-html` 只用于启动页（2 处调用），阅读页是自研渲染器（jsoup + AnnotatedString）。

**但该文结论不变**：鸿蒙上没有 JVM，`jsoup`（Java 库）同样不可用，Compose 的 `AnnotatedString` 也不能直接搬 —— 阅读页在鸿蒙侧仍属最高难度档。**只是「难在哪」要说准**：难在「把 jsoup + AnnotatedString 的排版逻辑按 ArkUI 重写」，不是「替换一个停更的第三方库」。

---

## 附录：核验来源与时效

| 事实 | 来源 | 日期 |
|---|---|---|
| WSA 终止支持、运行时被停用、无支持的恢复路径 | Microsoft Q&A 官方答复；BetaWiki（废弃 2024-03-05 / 下架 2025-03-05）；2026 年回顾文 | 2024-03 / 2025-03 |
| CMP desktop 为 **Stable**，Windows 10+（x86-64, arm64） | Kotlin 官方 *Compatibility and versioning* | 2026-09 查 |
| CMP 桌面运行需 JDK 11+，jpackage 打包需 JDK 17+ | 同上 | 2026-09 |
| room / sqlite / datastore / paging / lifecycle / navigation / compose 等支持 KMP | `developer.android.com/kotlin/multiplatform` 官方 KMP 库清单 | **2026-09-09** |
| Room KMP 推荐 `BundledSQLiteDriver`；KSP 按 target 配置；桌面底层 `sqlite-jdbc` | Google 官方 Room KMP 文档；KMP Weekly | 2026 |
| **WorkManager 不在官方 KMP 库清单内** | 同上官方清单 | 2026-09-09 |
| **Hilt 仅 Android**，依赖 Android lifecycle，无 KMP 计划（替代：Koin / kotlin-inject / Metro） | Metro vs Hilt 对比文；Koin vs Hilt 2026 | 2026 |
| **compose-html 为 Android 库**，用 `android.text.Html` + Spannable，最后提交 2022-03-08 | 项目 README + 提交历史（ireward/compose-html） | 2022-03-08 |
| CMP 提供 `material3.adaptive` 制品 | JetBrains *Compose Multiplatform 1.7.0 Released*；Maven Central（1.3.0-beta02） | 2024-10 / 2026 |
| Coil 3 为 KMP（含 JVM，走 Skiko）；`coil-gif` 仍仅 Android | Coil 官方 *Upgrading to Coil 3.x* | 2026 |
| 桌面 WebView 可用 KCEF（Chromium） | `compose-webview-multiplatform` README.desktop.md | 2026 |
| CMP 桌面打包走 `jpackage`，产出 `.exe` / `.msi`，`jlink` 裁剪 JRE，JRE 随包分发 | JetBrains *Native distributions* 官方文档；DeepWiki `compose-multiplatform` §3.4 | 2026-09 查 |
| Windows 桌面框架横向对比（WinUI 3 / WPF / Avalonia / MAUI / CMP / Electron / Tauri） | dev.to *Windows Native App Development Is a Mess: A Practical Guide for 2026*（2026-03）；essenn.associates *Desktop App Frameworks Compared (2026)* | 2026-03 |
| Flutter 3.47（2026-08-12）起 **Impeller 为 Windows/macOS/Linux 默认渲染器**（Vulkan / Metal，编译期预编译 shader） | Flutter 3.47.0 发行说明与多篇发行分析；`startdebugging.net`、`blog.redlinesoft.net` | 2026-08 |
| Tauri 2 实测体积 5–10 MB、空闲内存 30–50 MB、冷启动约 0.4 s；Electron 对应 90–150 MB / 120–400 MB / 1.4 s | `technewsdaily.com`（Tauri v2 常见坑，2026）；`dev.to` Tauri vs Electron 2026；`pikvue.com` 三框架实测对比 | 2026 |
| Tauri 首次冷编译慢（实测 7 分 40 秒，600+ crate）；企业版 Windows N SKU 不预装 WebView2 | `toolvs.co` Tauri vs Electron 实测（2026-04） | 2026-04 |
| `feed-rs` 2.4.0（2026-07-07）支持 Atom / RSS 2.0 / 1.0 / 0.x / JSON Feed；17.7 万次下载/月；`news-flash`（feed reader 基础库）等 94 个 crate 依赖 | crates.io / lib.rs / docs.rs `feed-rs` | 2026-07 |
| **Lettura**：Tauri 版 RSS 阅读器，技术栈 Tauri + `feed-rs` + SQLite + Diesel | 项目架构分析文（2026） | 2026 |
| `@mozilla/readability` 为 Firefox 阅读模式的原始实现，Mozilla 维护 | mozilla/readability 官方仓库 | 2026-09 查 |
| `rss-parser` 仍在维护（2026-02 提交） | npm / 仓库提交历史 | 2026-02 |
| 全部使用面计数（文件数 / 命中数 / 行数） | 本机实测：`android-to-desktop-port-assessment/scripts/scan_android_coupling.py` | 2026-09-24 |

**时效提示**：KMP 生态推进很快 —— 官方 KMP 库清单每季度扩充，CMP 1.11 已稳定、1.12 在 RC。**`WorkManager` 与 `Hilt` 是最可能在未来一年内出现 KMP 支持的两项**，届时本估算可下调。正式开工前建议重新确认这两项的当时状态。

**未核实项（如实标注）**：`me.saket.swipe`、`telephoto`、`android-svg` 是否已有 KMP 制品 —— 本次未逐一确认；三者均为小型库，无论结论如何都不改变整体估算。
