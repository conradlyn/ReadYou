# 重制为华为鸿蒙 App — 工作量评估

- **评估对象**：`conradlyn/ReadYou` @ `dc68c1ff`
- **评估日期**：2026-09-23
- **状态**：**已结案 —— 不重制**。取舍理由见 §5：目标设备仍在鸿蒙 6.1（可跑 APK），
  重制成本是 Android 平板适配的 12~25 倍，且适配的设计成果能被将来的重制复用。
  本文档保留，不是因为它主张重制，而是因为它记录了「当时为什么不需要重制」以及那笔工时账——
  **前提「6.1 保留 AOSP 兼容层」会在 7.0 覆盖平板后失效**，届时需要重新确认（见附录时效提示）。
- **代码基线**：382 个 Kotlin 文件，**42,724 行**，357 个 `@Composable`，**100% Jetpack Compose**（`res/` 下无 `layout` 目录）

---

## 0. 先回答一个前置问题：你到底需不需要重制

这一步比工作量估算重要，因为答案可能是「0」。

### 鸿蒙当前的双轨状态（2026-09 实测核验）

| | HarmonyOS 6.1「兼容版」 | HarmonyOS 7.0 / NEXT「纯血」 |
|---|---|---|
| AOSP 兼容层 | **保留** | **彻底移除**（1.1 亿行全自研） |
| 可运行的包格式 | **HAP + APK 双格式** | 仅 HAP |
| 能否直接装 ReadYou 的 APK | **能** | 不能 |
| 发布 | 大规模推送给老机型，覆盖 2021 年后机型 | 2026-06-12 HDC 发布 Beta |
| 设备规模 | 鸿蒙 6 设备 > **7,000 万台**（2026-07-02 官方口径） | 推送中 |
| 平板覆盖 | **MatePad Pro 13.2 / 12.2 / 11.5、MatePad Air、MatePad Mini 全系在推送名单** | 首批名单以手机为主，平板未列入 |

**来源**：华为开发者联盟《鸿蒙最新手机适配与对接技术详解（2026）》、Huawei Central 6.1 机型路线图、HDC 2026 发布报道（腾讯新闻 2026-06-12）。

### 纯血 7.0 的推送节奏（HDC 2026 公布）

| 批次 | 时间 | 机型 |
|---|---|---|
| 第一批 | 2026-07 ~ 08 | Mate 80 / 70 / 60 系列、Pura 70 系列、折叠屏 |
| 第二批 | 2026-08 ~ 09 | Mate 50 系列、nova 12–15 系列、Pura 50 系列 |
| 第三批 | 2026-09 ~ 10 | Mate 40 系列及更早 |

Mate 90 预计 2026-10~11 月出厂预装 7.0。**注意**：2026-08-05 发布的 **MatePad Pro 2026 出厂仍是 6.1**，不是 7.0。

### 结论

> **如果你的目标只是「在自己的华为平板上用 ReadYou」—— 工作量是 0。**
> 在 HarmonyOS 6.1 的平板上直接安装你 CI 已经构建好的 APK 即可。
> 「平板 + 纯血鸿蒙」的硬需求，按目前的推送节奏和 MatePad 的装机覆盖，最早出现在 **2027 年**。

只有当目标是 **① 面向纯血鸿蒙用户分发，或 ② 上架华为应用市场** 时，重制才有必要。下文按「必要」来估算。

---

## 1. 为什么这是「重写」而不是「移植」

三个结构性障碍，逐个说明。

### 1.1 运行时不通：Kotlin 字节码无法在 ArkCompiler 上运行

- Android：Java/Kotlin → JVM 字节码 → ART（JIT + AOT）
- HarmonyOS NEXT：ArkTS → **ArkCompiler 严格 AOT 编译为机器码**

两者没有互操作层。ArkTS 是 **TypeScript 的静态类型超集**，且比 TypeScript 更严格：**禁用 `any`、禁止运行时修改对象结构**。所以即便逻辑一致，代码也是逐行重写，不是复制粘贴。鸿蒙自 API 8 起不再支持 Java 开发。

### 1.2 华为的迁移工具对本项目基本无效

| 工具 | 实际能力 | 对本项目 |
|---|---|---|
| **A2H Converter** | LLM 多智能体，**Android XML layout → ArkUI**。论文（arXiv 2412.13693）报告 component/page/project 级成功率 90.1% / 89.3% / 89.2% | **零帮助**。本项目 `res/` 下**没有 `layout` 目录**，357 个界面全是 Compose 函数 |
| **ArkUI-X** | 让 ArkUI 代码跑到 Android / iOS 上 | **方向相反**。它不把 Android 代码搬到鸿蒙 |
| **迁移调试工具** | 导入 APK 的应用数据（偏好、DB）到鸿蒙应用 | **有用**，但只在最后一步降低用户迁移成本，不减少开发量 |

官方迁移指南明确把 **Jetpack Compose 列为「重难点」**，而原生 View / XML 相对好办。本项目是 **100% Compose + 0% XML**，属于最高难度档。

### 1.3 第三方库生态缺口

这是工作量估算里最容易被低估的部分。逐项列出：

| Android 依赖 | 用途 | 使用面 | 鸿蒙出路 | 难度 |
|---|---|---|---|---|
| `compose-html` + 自研 reader | **HTML → 声明式富文本排版**（`ui/component/reader`，**1,921 行**） | 阅读页核心 | **ArkUI 无等价物**。鸿蒙富文本是 `RichText` + `StyledString`，能力显著弱于 Compose 的 inline content / 自定义 AnnotatedString | ★★★ 最高 |
| `android.webkit` + JS 注入桥 | 阅读器 WebView 模式（`ui/component/webview`，**887 行**，4 个文件） | 阅读页另一种渲染模式 | ArkUI `Web` 组件（Chromium 内核）可用，但 `JavaScriptInterface` → 需重写为 `javaScriptProxy`，且注入脚本（`WebViewScript.kt`）、样式注入（`WebViewStyle.kt`）、双向通信全部要重做 | ★★★ 高 |
| **Glance** | 桌面小部件（`ui/widget`，**1,190 行**，6 文件） | 两个 widget | **ArkTS 服务卡片（Form）** —— 完全不同的声明式模型 + 独立编译目标 + 独立生命周期 | ★★★ 高 |
| Kotlin Coroutines / Flow | 贯穿全项目 | 面极广 | ArkTS 用 `async/await` + `TaskPool`/`Worker`。**`Flow` 没有等价物**，全部分享流要重构为状态观察模型 | ★★★ 高（面积大） |
| `Hilt` / `Dagger` | 依赖注入（Hilt 32 文件 + Dagger 46 文件引用） | 全项目 | **鸿蒙无官方 DI 框架**。需手写容器/工厂，或引入社区方案（成熟度低） | ★★☆ 中高 |
| `Room` | 本地库（9 组 `@Entity`/`@Dao`，23 文件） | 全项目数据层 | `@ohos.data.relationalStore`（SQLite）。概念对应但 **API 完全不同，注解生成的 DAO 要全部手写** | ★★☆ 中高 |
| `WorkManager` | 后台同步（17 文件） | 同步 / widget 刷新 / 通知 | `@ohos.resourceschedule.workScheduler` + `backgroundTaskManager`。**鸿蒙对后台任务的限制比 Android 更严**，RSS 定时同步可能需要重新设计 | ★★☆ 中高 |
| `rome` (com.rometools) | RSS / Atom / JSON Feed 解析（5 文件） | 数据层核心 | 无 JVM。改用 TS 库（`fast-xml-parser` 等）后自写 feed 适配层 | ★★☆ 中 |
| `readability4j` | 正文提取（2 文件） | 阅读页 | 移植 `readability` 算法到 ArkTS，或经 `Web` 组件跑 `@mozilla/readability` | ★★☆ 中 |
| `okhttp` + `retrofit` | 网络 + GReader/Fever API（11 文件） | 数据层核心 | `@ohos.net.http` / `rcp`。DTO 映射、重试（`RetryableTask`）、拦截器要重写 | ★★☆ 中 |
| `Coil`（含 SVG / GIF） | 图片加载（10 文件） | 全列表 | ArkUI `Image` + 自建三级缓存；SVG / GIF 需额外解码方案 | ★★☆ 中 |
| `DataStore` | 偏好存储（73 文件引用） | 全项目 | `@ohos.data.preferences`。**概念最接近，成本最低** | ★☆☆ 低 |
| `kotlinx.serialization` | DTO 序列化 | 数据层 | `@ohos.util.json` 或第三方 | ★☆☆ 低 |
| `androidx.browser` CustomTabs | 内嵌浏览器 | 少量 | 无直接等价物，改用 `Web` 组件或系统 Want | ★☆☆ 低 |
| `android.speech.tts` | 朗读 | 1 文件 | `@kit.CoreSpeechKit` 的 textToSpeech —— **需确认对普通第三方应用是否开放** | 待确认 |
| 自研色彩空间（`ui/theme/palette`，**1,991 行**） | Material You 动态取色（CIELAB / Jzazbz / Oklab / Zcam / HLG-PQ） | 主题系统 | **纯 Kotlin 数学，无 Android API 依赖** → 可逐行翻译成 ArkTS | ★☆☆ 低（本项最容易） |

---

## 2. 代码复用率分析

按「逻辑是否可逐行翻译」把 42,724 行分三类。

### ① 可逐行翻译（语言变、算法不变）— 约 **5,000 ~ 6,000 行（12~14%）**

| 模块 | 行数 | 说明 |
|---|---|---|
| `ui/theme/palette/` | 1,991 | 色彩空间数学。**零 Android 依赖**，纯 Kotlin 运算 → 可逐行译成 ArkTS |
| `ui/motion/` + `ui/graphics/` + `ui/svg/` | 543 | 缓动函数、多边形 morph |
| `domain/model/account/security/` | — | 密钥派生（DES、Fever/GReader key）。**但依赖 `javax.crypto`** → 需换 `@ohos.security.cryptoFramework` |
| `ui/ext/` 中的纯函数 | 部分（共 1,766） | 日期、字符串、数字、Bidi 处理 |
| RSS/OPML/正文提取的**规则**（非实现） | — | feed 合并规则、`BestIconFinder` 图标候选排序、正文清洗规则 —— 规则可搬，实现要重写 |

**注意**：这一类的「可翻译」指的是**算法可以照抄**，编码仍需人手（Kotlin → ArkTS），大约 1.2~1.5 倍工时。不是零成本。

### ② 必须重写（平台耦合）— 约 **37,000 行（86~88%）**

| 层 | 行数 | 重写原因 |
|---|---|---|
| `ui/page/` | 15,997 | 全 Compose → ArkUI 声明式 |
| `ui/component/` | 6,763 | 全 Compose（含 1,921 行 reader 排版、887 行 webview） |
| `infrastructure/` | 8,461 | Android API：Context、Uri、DataStore、Room、Retrofit、WorkManager、Hilt |
| `ui/widget/` | 1,190 | Glance → Form 服务卡片 |
| `ui/theme/`（除 palette） | 309 | `LocalDensity`、`LocalView`、动态取色接入 |
| `ui/adaptive/` | 197 | 你的自适应层（**设计可复映射，代码不能复用**，见 §4） |
| `domain/` | 5,436 | 逻辑本身平台无关，但深度耦合 Kotlin Flow / Room Paging / `javax.crypto` → 需重架构 |

### ③ 资源与本地化

- **54 个 `values-*` 语言目录** → 鸿蒙的 `resources/base/element/string.json` + `resources/<qualifier>/...` 结构不同，**可写脚本转换**（半天到一天）
- 字体（Google Sans Flex 可变字体）、图标、启动图 → 需按鸿蒙资源规范重新组织

---

## 3. 工作量估算

### 单人（同时具备 Android 与 ArkTS 经验）+ AI 辅助

| 阶段 | 内容 | 估时 |
|---|---|---|
| **0 技术验证** | 阅读页 HTML 排版可行性、服务卡片、TTS 可用性、后台同步限制 —— **这一阶段必须先做，它决定后续估算是否成立** | 2–3 周 |
| **1 工程骨架** | UIAbility、路由（`Navigation` + `NavPathStack`）、偏好、DB、网络、DI 容器 | 2–3 周 |
| **2 数据与同步层** | GReader / Fever / Local 三个 provider、RSS 解析、OPML、后台同步、通知 | 4–5 周 |
| **3 核心页面** | Feeds（分组/feed 列表/抽屉/订阅）+ Flow（文章列表/搜索/滑动操作/标记已读） | 5–7 周 |
| **4 阅读页** | 正文排版重写 + WebView 模式 + JS 桥 + 图片/视频 + TTS | **5–7 周** |
| **5 设置全树** | 20+ 页面（7,105 行）+ palette 移植 + 54 语言资源 | 4–5 周 |
| **6 服务卡片与集成** | Glance → Form、分享/深链、桌面入口 | 2–3 周 |
| **7 平板/折叠自适应 + 测试 + 上架** | `Navigation` Split / `SideBarContainer` / `GridRow` 断点 | 2–3 周 |
| **合计** | | **26–35 周 ≈ 6–8 人月** |

**为什么取这个上限**：本项目的**每一类难度都在高的一侧** —— 100% Compose（官方认定最难）、自研动态取色、WebView 双向注入桥、Glance 组件、54 语言。没有一项是「官方说相对好办」的原生 View。

### 3 人并行（1 UI / 1 数据 / 1 平台）

**3 ~ 4.5 个月**。但压缩有限，因为：

- 阶段 4（阅读页）是硬瓶颈，无法并行拆分（排版与 WebView 桥共享同一套语义）
- 阶段 5（设置树）虽可拆，但依赖阶段 1 的骨架稳定
- ArkUI 的生态资料远少于 Compose，踩坑与返工时间不好压缩

### 一个重要的省略项

估算**不包含**：鸿蒙上架审核周期、华为开发者账号与资质、HMS 服务接入（推送 `Push Kit`、`Account Kit` 登录）、灰度与稳定性测试。如果要做社交/推送功能，另加 2–3 周。

---

## 4. 一个反直觉的好消息：你在 Android 侧的投资不会浪费

你的 5 个提交里，`ui/adaptive/`（197 行）的**代码**在鸿蒙上不能复用，但**设计可以一对一映射**，而且鸿蒙侧实现起来更省力 —— 因为分栏和断点是鸿蒙官方的一等公民：

| 你的 Android 实现 | 鸿蒙对应 | 工作量对比 |
|---|---|---|
| `AppSizeClass`（600 / 840dp 断点，`AppSizeClass.kt:10-21`） | **官方断点体系**：sm < 600vp / md 600–840vp / lg ≥ 840vp | **更低**，无需自建 |
| `AdaptiveContentMaxWidth = 640.dp` 限宽（`AdaptiveLayout.kt:19`） | `GridRow`/`GridCol` 的 `span`，或 `maxWidth` 约束 + `Blank` | 相当 |
| `rememberAdaptiveContentPadding` 居中（`AdaptiveContentPadding.kt:45-53`） | `GridRow` 栅格居中 | 相当 |
| 上游的 `[Flow \| Reading]` 双栏（`ArticleListReadingPage.kt`） | **`Navigation.mode(Split / Auto)`** —— `Auto` 自带 600vp 阈值，`navBarWidth` / `navBarWidthRange` 支持拖拽调宽 | **明显更低**，官方内建 |
| 三栏 `[Feeds \| Flow \| Reading]` | `SideBarContainer`（`Overlay` / `Embed`）+ `Navigation` Split 组合 | 更低 |
| `Feeds` 页多列 | `GridRow` 断点 + `GridCol` | 更低 |

**结论**：FORK-AUDIT 里那 1–2 周的 Android 平板适配工作，**等于把设计先定稿**。重制时这部分设计不用重新考虑，只换实现。而且 `AdaptiveLayoutSpec.Compact = 恒等变换` 这条原则（手机零变化）在鸿蒙侧同样适用，可以直接复用。

---

## 5. 三个方案的对比与建议

| 方案 | 成本 | 适用前提 | 结论 |
|---|---|---|---|
| **A. 鸿蒙 6.1 平板直接装 APK** | **0** | 已具备（你的 CI 已经产出可安装 APK） | 6.1 保留 AOSP 兼容层，MatePad 全系可装。**当前最省力的答案** |
| **B. 完成 Android 平板适配** | **1–2 周** | 想在任何 Android/兼容版平板上都获得好体验 | 见 `FORK-AUDIT.md`。且设计可复映射到鸿蒙，**没有沉没成本** |
| **C. ArkTS / ArkUI 重制** | **6–8 人月** | 上架华为应用市场，或面向纯血鸿蒙用户分发 | 仅在你确实需要这个分发渠道时做 |

### 推荐路径

**先做 B，暂不做 C。** 理由：

1. 目标设备目前仍在鸿蒙 6.1（可跑 APK），纯血平板的装机量到 2027 年才会形成规模
2. B 的成本是 C 的 **1/12 到 1/25**，且 B 的设计成果能被 C 复用
3. 上游 `ReadYouApp/ReadYou` 已经在投入平板（`ui/page/adaptive/`）。如果 FORK-AUDIT §3.3 的回馈上游建议成功，**你的平板适配成本归零**，而且这件事会成为上游的能力，未来如果社区做鸿蒙版也能受益
4. 纯血鸿蒙上的 RSS 阅读器目前是空白。等生态更成熟（ArkUI 富文本能力、社区库），C 的实际成本会下降

### 如果确定要做 C，建议的降本路径

**不要整体重制。** 按以下顺序做 MVP：

1. **砍掉** 动态取色（palette 1,991 行）、Glance 卡片（1,190 行）、TTS、多账号 —— 先只做本地账号
2. **阅读页优先走 WebView 模式**。`ReadingRendererPreference` 已有该分支，先把 WebView 模式跑通，把 `component/reader`（1,921 行 ArkUI 富文本排版）**推到第二期**。这是最大的一块风险，能推迟就推迟
3. **用鸿蒙的「迁移调试」工具**导入 APK 的偏好与 DB，降低现有用户的迁移成本
4. **平板自适应放最后做**，因为鸿蒙侧最容易（见 §4），而且可以借 FORK-AUDIT 的设计结论

这样第一期「最小可用」（Feeds + Flow + 阅读页 WebView + 本地账号）约 **8–10 周**，可以先验证 ArkTS 生态是否够用，再决定是否投入剩下的 4–6 人月。

---

## 附录：核验来源与时效

| 事实 | 来源 | 日期 |
|---|---|---|
| 6.1 保留 AOSP 兼容层，支持 HAP + APK 双格式 | 华为开发者联盟《鸿蒙最新手机适配与对接技术详解（2026）》 | 2026 |
| 6.1 机型路线图（含 MatePad 全系） | Huawei Central, HarmonyOS 6.1 Eligible Devices | 2026 |
| 7.0 彻底移除 AOSP，仅 HAP；三批推送路线图 | HDC 2026 发布报道（腾讯新闻） | 2026-06-12 |
| 鸿蒙 6 设备 > 7,000 万台 | 华为终端 BG CEO 公开口径 | 2026-07-02 |
| MatePad Pro 2026 / nova 16 SE 出厂装 6.1 而非 7.0 | 2026-08-05 发布会报道 | 2026-08 |
| A2H Converter 是 XML → ArkUI，成功率 90% 级 | arXiv 2412.13693（A2H: A UI Converter from Android to HarmonyOS Platform） | 2024-12 |
| Compose 是迁移重难点；ArkUI-X 方向相反 | 华为官方迁移指南、多家迁移实践文档 | 2026 |
| ArkUI 平板布局：Navigation Split / SideBarContainer / GridRow 断点 | 华为开发者联盟《页面布局场景》官方最佳实践 | 2026 |

**时效提示**：鸿蒙的版本节奏很快（6.1 与 7.0 双轨并行），本文的「6.1 可跑 APK」这一前提**会在 7.0 覆盖平板后失效**。建议在决定是否重制前，重新确认当时的 MatePad 出厂系统版本。
