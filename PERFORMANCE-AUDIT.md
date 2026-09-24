# ReadYou fork 性能与稳定性审计

> 基线 commit `f40e6650`（`v0.16.2-tablet.7`），审计日期 2026-09-24。
> 本机**没有 JDK / Android SDK / gradle 缓存**，跑不了 `./gradlew`。
> 以下结论全部来自静态审读 + CI 产物核对，凡是需要真机才能定论的都标了「待真机验证」。
> 条目按「收益 ÷ 风险」排序，不是按收益绝对值排序——先从零风险的下手。

---

## 0. 结论速览

| 编号 | 条目 | 收益 | 风险 | 需要 DB migration |
|---|---|---|---|---|
| A1 | release 包里打进了 Compose UI Tooling | 中 | 无 | 否 |
| A2 | `MainActivity` 主线程磁盘 IO 调试残留 | 小 | 无 | 否 |
| A3 | WorkManager 生产环境按 DEBUG 打日志 | 小 | 无 | 否 |
| A4 | `onResume` 重复入队 Widget 任务，且守卫失效 | 中 | 低 | 否 |
| B1 | `FeedIcon` 按原始分辨率解码图标 | **大** | 低 | 否 |
| B2 | 标记已读触发整屏重组 | 中 | 低 | 否 |
| B3 | `Application` 里 11 个未使用的 eager 注入 | 中 | 中低 | 否 |
| B4 | 分页查询缺复合索引 | **大**（大库） | 中 | **是** |
| C1 | `-dontobfuscate` 关掉了全部重命名 | 中 | 中 | 否 |
| C2 | `-keep me.ash.reader.**` 保留全部自身类 | 中 | 中高 | 否 |
| C3 | 缺应用自身的 baseline profile（库的已有） | **大** | 中 | 否 |
| C4 | `enableR8.fullMode=false` | —— | —— | **建议不动** |
| D1 | `trustAllCerts=true` 全局信任所有证书 | 安全 | —— | 否 |
| D2 | `CrashHandler` 不委托默认处理器、不结束进程 | 稳定性 | —— | 否 |
| D3 | CursorWindow 上限 100 MB | 稳定性 | —— | 否 |
| D4 | `printStackTrace()` 被当字符串拼进日志 | —— | 无 | 否 |

---

## 0.1 实测基线（`f40e6650` 的 CI 产物）

后面的体积类判断都对着这组数字比，别凭感觉。

```
APK            ReadYou-0.16.2-f40e665.apk        10.6 MB
  classes.dex                                     10.18 MB   55,147 个方法
  classes2.dex                                     3.18 MB   18,324 个方法
  dex 合计                                        13.36 MB   73,471 个方法
  resources.arsc                                   2.01 MB
  files                                            1,793
  assets/dexopt/baseline.prof                      7,224 B   （zlib 解压后 77,605 B）
  assets/dexopt/baseline.profm                     1,105 B
```

`73,471` 个方法对一个 10.6 MB 的应用是偏多的（同类应用常见 3~5 万），这与 C1、C2 两条 keep 规则相互印证。

---

## A 级：零运行时风险

### A1 — release 包里打进了 Compose UI Tooling

`app/build.gradle.kts:136`

```kotlin
implementation(libs.compose.ui.tooling)      // ← 第 136 行
```

同文件第 151 行已经有 `debugImplementation(libs.compose.ui.tooling)`。第 136 行让 `androidx.compose.ui:ui-tooling`（`ComposeViewAdapter`、`PreviewActivity`、布局检查器相关类）进了 release dex。

**已实测确认**：在 `f40e6650` 的 CI 产物 `classes.dex` 的类型描述符表里，能搜到 `Landroidx/compose/ui/tooling/PreviewActivity;`。这条不是推测。

**改法**：删掉第 136 行，保留 151 行的 `debugImplementation`。

注意第 137 行的 `libs.compose.ui.tooling.preview` 要**保留** `implementation`——`ui-tooling-preview` 是 `@Preview` 注解本体，官方就是要求随 release 分发的。

顺带：第 130 行和第 150 行重复声明了 `androidTestImplementation(platform(libs.compose.bom.stable))`，删一条即可（无害，纯整洁）。

### A2 — `MainActivity` 在主线程做磁盘 IO

`MainActivity.kt:65`

```kotlin
Log.i("RLog", "onCreate: ${ProfileInstallerInitializer().create(this)}")
```

`ProfileInstallerInitializer.create()` 会读写 profile 安装标记文件，是磁盘 IO，被直接放在 `onCreate`（主线程），而且 release 里也执行。这行是调试残留。

**改法**：整行删除。`profileinstaller` 的实际安装由 `androidx.startup` 的 ContentProvider 在后台完成，不需要手动调 `create()`。

### A3 — WorkManager 生产环境按 DEBUG 级别打日志

`AndroidApp.kt:117`

```kotlin
.setMinimumLoggingLevel(android.util.Log.DEBUG)
```

每次 worker 调度、重试、状态变更都会写 logcat。后台同步频繁时有额外开销，且会污染用户抓到的日志。

**改法**：`setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.WARN)`。

### A4 — `onResume` 重复入队 Widget 任务，而且守卫是失效的

两处问题叠在一起。

**其一**，`MainActivity.kt:215` 每次 `onResume` 都调 `WidgetUpdateWorker.enqueueOneTimeWork`，而 `WidgetUpdateWorker.kt:63` 用的是：

```kotlin
workManager.enqueue(OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build())
```

没有 unique name、没有 `ExistingWorkPolicy`。切前后台、从设置页返回、分屏切换，都会入队一个新任务，快速切换时会堆积。

**改法**：`enqueueUniqueWork("WidgetUpdateWorkerOneTime", ExistingWorkPolicy.KEEP, request)`。

**其二**（更值得修），`WidgetUpdateWorker.kt:38`：

```kotlin
var haveSetPreviews = false
...
private suspend fun generatePreviews() {
    if (haveSetPreviews) return
    ...
}
```

这是 Worker 的**实例字段**，而 Worker 每次执行都是**新实例**——所以 `haveSetPreviews` 每次都是初始 `false`，这个「只设置一次预览」的守卫**从来没起过作用**。Android 15+ 上每次都会重新调 `setWidgetPreviews`（跨进程 IPC）。

**改法**：把标记持久化（DataStore / SharedPreferences），或者干脆接受每次都设、但把它从 `onResume` 的路径上摘掉，只放在周期性 Worker 里。

---

## B 级：低风险，收益明显

### B1 — `FeedIcon` 按原始分辨率解码图标（本次收益最大的一项）

`FeedIcon.kt:50-57`：

```kotlin
RYAsyncImage(
    modifier = modifier.size(size).clip(CircleShape),
    contentDescription = feedName ?: "",
    data = iconUrl,
    placeholder = null,
)
```

**没有传 `size`**，于是用 `RYAsyncImage` 的默认值 `Size.ORIGINAL`（`RYAsyncImage.kt:29`）。

`Size.ORIGINAL` 的意义是**按图片原始分辨率解码**。显示尺寸只有 20dp，但很多站点的订阅图标是 512×512 的 PWA icon，或者 1200×630 的 og-image。后者按原尺寸解码约 `1200 × 630 × 4 ≈ 3 MB` 位图——**为一个 20dp 的圆点**。

影响面比想象中大：
- 信息流列表每一项都有 feed 图标；
- 订阅管理页一次列出全部订阅源（几百个很常见）；
- 平板上双栏布局同时渲染的 item 数更多。

代价是内存峰值和解码时间，低内存设备会表现为滚动掉帧、GC 抖动，极端情况 OOM。

**改法**：给这处调用传固定 size。项目里已有 `SIZE_1000`，图标场景建议再加一个更小的常量（`192×192` 对 20~32dp 足够，即便考虑 3x 屏也是 96px 需求）。`precision` 保持默认 `AUTOMATIC` 即可走 downsampling。

**注意**：`ArticleItem.kt:286` 的配图**已经**传了 `size = SIZE_1000`，不用动。要动的只有 `FeedIcon`。

### B2 — 标记已读会让整屏列表项重组

`ArticleList.kt:47` 与 `:81`：

```kotlin
isUnread = diffMap[article.id]?.isUnread ?: article.isUnread,
```

`diffMap` 是 `DiffMapHolder.diffMap`，类型 `SnapshotStateMap`（`DiffMapHolder.kt:43`）。

Compose 的 `SnapshotStateMap` **读记录是 map 级别的**：`get(key)` 记录的是「读过这个 map」，任何一次 `put` 都会让**所有**读过它的组合作用域失效。所以标记一条已读，当前所有可见 item 都会重新执行一次组合 lambda。

这在我 fork 里被放大了——「全部已读」按钮和滚屏自动标记都会短时间连续 `put` 很多次。

Kotlin 2.2 的 Compose 编译器默认开启 Strong Skipping，会把大部分重组降级成「参数比较后跳过」，但代价仍是 N 次 lambda 执行 + N 次参数比较。

**改法**：把这次读包进 `derivedStateOf`，让「值没变」的项不通知下游：

```kotlin
@Composable
private fun rememberIsUnread(diffMap: Map<String, Diff>, article: Article): Boolean {
    val state = remember(article.id, article.isUnread) {
        derivedStateOf { diffMap[article.id]?.isUnread ?: article.isUnread }
    }
    return state.value
}
```

`derivedStateOf` 的契约就是「计算结果与上次相同则不通知读取者」，正好把重组的范围收敛到真正变化的那一项。语义完全等价（同一个 map、同一个 key）。

### B3 — `Application` 里 12 个未使用的 eager 注入

`AndroidApp.kt:50-88` 声明了 20 个 `@Inject lateinit var`。Hilt 在注入时会把它们**全部**实例化，这些全部发生在冷启动路径上、主线程。

逐个核对本文件内的实际引用，**只在 `AndroidApp` 里真正用到的**是 8 个：

| 字段 | 用途 |
|---|---|
| `workerFactory` | `workManagerConfiguration` |
| `appService` | `checkUpdate()` |
| `accountService` | `accountInit()` |
| `rssService` | `accountInit()` / `workerInit()` |
| `applicationScope` | `onCreate` 里 launch |
| `ioDispatcher` | 同上 |
| `imageLoader` | `Coil.setImageLoader()` |
| `diffMapHolder` | **不直接引用，但必须保留**——它的 `init{}` 订阅了 `currentAccountFlow`，靠注入产生副作用 |

其余 12 个在本文件里没有任何引用（用 `grep -c "\b<字段名>\b"` 逐个核对过，计数为 1 表示只有声明）：

`androidDatabase`、`workManager`、`networkDataSource`、`OPMLDataSource`、`rssHelper`、`notificationHelper`、`androidStringsHelper`、`localRssService`、`opmlService`、`okHttpClient`、`imageDownloader`、`settingsProvider`。

> `OPMLDataSource` 的引用计数是 2，但两处都来自同一行声明（变量名与类型名同名、都是 `OPMLDataSource`），实际引用为 0。

其中两个尤其值得摘掉：
- `imageLoader` 之外的 `okHttpClient` 会建连接池与线程池；
- `imageLoader` 本身是必须的，但 Coil 的 `DiskCache.Builder().build()` 会**同步打开磁盘缓存 journal**（实质磁盘 IO），已经压在冷启动上了。

**改法**：逐个删除未使用字段，**每删一个确认该单例没有"靠构造产生副作用"**（`diffMapHolder` 就是反例）。这一步必须逐个人工核对，不能批量删。

### B4 — 分页查询缺复合索引

`Article` 实体只有两个单列索引（`domain/model/article/Article.kt:40-43`）：`feedId` 与 `accountId`。

而 DAO 的主查询长这样（`ArticleDao.kt:88` 起，多个变体同构）：

```sql
SELECT * FROM article
WHERE accountId = :accountId
  AND feedId = :feedId            -- 可选
  AND isUnread = :isUnread        -- 可选
ORDER BY date DESC
```

SQLite 用单列索引只能做过滤，`ORDER BY date` 需要**临时 B-tree 排序全部命中行**，再配合 Paging 的 `LIMIT/OFFSET`——深翻页时代价随命中行数上升。长期同步的本地账户攒到几万篇后，切分组、翻页会明显变钝。

**改法**：加两个复合索引（SQLite 索引可双向扫描，`ASC`/`DESC` 都能用）：

```sql
CREATE INDEX index_article_accountId_date ON article(accountId, date)
CREATE INDEX index_article_feedId_date    ON article(feedId, date)
```

**代价**（这是全表里唯一需要 DB migration 的一项）：
- 需要 `AppDatabase` version 7 → 8 并写 `Migration`；
- `app/schemas/me.ash.reader.infrastructure.db.AndroidDatabase/8.json` 必须一并提交；
- 索引让同步时的批量插入略慢（多了两棵索引要维护）；
- **已发过 release 的用户必须能平滑升级**——migration 写错会让他们开不了 App。

风险与收益都写在这里了，建议单独做一次、单独发版。

---

## C 级：收益大，但要接受权衡

### C1 — `-dontobfuscate` 关掉了全部重命名

`app/proguard-rules.pro:23`

```proguard
-dontobfuscate
```

R8 的优化（内联、类合并、字符串去重、二次裁剪）与重命名强耦合。关掉重命名会留下大量长类名/方法名字符串，dex 体积、类加载、反射查找都变差。

**保留可调试性的前提下开启**：文件里已经有 `-keepattributes SourceFile,LineNumberTable`（第 17 行）。可以**去掉第 21 行的 `-renamesourcefileattribute SourceFile`**——这样崩溃堆栈里保留原始文件名 + 行号，绝大多数问题不查 mapping 文件也能定位。

**风险**：中。必须真机冒烟一遍，尤其关注 Gson 反序列化的同步接口、Rome 的 feed 解析、以及 `signature/reader.keystore` 签名后的覆盖安装。

**收益要先量**：用 `fetch_ci_apk.py` 取改前/改后的包比大小与 `unzip -l` 的 dex 条目，别凭感觉。

### C2 — `-keep class me.ash.reader.** { *; }` 保留了自己的全部类

`app/proguard-rules.pro:38`，注释写的是「Provider API」，但 `me.ash.reader.**` 覆盖的是整个应用包。

等于放弃了对自己代码的绝大部分 R8 优化。Compose 尤其依赖 R8 裁剪未使用的 composable——这条规则会让裁剪基本失效。

**改法**：收窄到真正需要反射的入口。manifest 里的四大组件（Activity/Service/Receiver/Provider）AGP 会自动 keep，Widget/Worker 同理，不需要手写。如果担心 Gson 反射字段，用 `-keepclassmembers` 只保字段名而不保整个类。

**风险**：中高，必须真机验证同步、OPML 导入导出、Widget。

### C3 — 没有应用自身的 baseline profile（库的那一半已经有了）

依赖里有 `libs.profileinstaller`，但仓库内**没有任何 `baseline-prof.txt`**（全仓 `find -iname "*baseline*"` 只命中一个无关的测试文件 `ListFontBaselineTest.kt`）。

需要把两件事分开看，本次已经**实测核对**过：

- **AndroidX 库自带的 profile —— 有。**
  `f40e6650` 的 APK 里存在 `assets/dexopt/baseline.prof`（7,224 字节，zlib 解压后 77,605 字节）和 `baseline.profm`（1,105 字节）。说明 Compose runtime 等 AAR 内置的 `baseline-prof.txt` 已被 AGP 正常合并。**这一半不用管。**
- **应用自身启动 + 首屏路径的 profile —— 没有。**
  上面那份完全来自 AAR 里静态写死的文本，不含这台设备上真实的启动与首屏热路径。`MainActivity` → `AppEntry` → `FlowPage` 这条首屏必经链，以及列表滚动涉及的组合路径，都只能靠真机跑出来。

**收益**：减少首屏与滚动路径的 JIT 编译抖动，是 Compose 应用最有效的流畅度手段之一。

**代价**：真机 profile 需要 macrobenchmark 模块 + 一台设备/模拟器；CI 上要加 `android-emulator-runner`（且必须是有 KVM 的 Linux runner）。这是全表里工作量最大的一项。

**折中**：先手写一份只覆盖入口类与 Compose runtime 的 `baseline-prof.txt` 放进 `src/main/`。收益小于真机 profile，但零设备依赖，可以立刻验证格式是否正确 —— 具体做法是看合并后的 `assets/dexopt/baseline.prof` 是否变大（对比现在的 7,224 字节）。

### C4 — 不建议动 `enableR8.fullMode`

`gradle.properties:15` 的 `android.enableR8.fullMode=false` **保持现状**。

full mode 假设「程序里没有反射」，而本项目用 Gson 反序列化远程 API 响应。开了要补齐一整套 keep 规则，收益与风险不成比例，还会让 C1/C2 的验证工作翻倍。

---

## D 级：稳定性隐患

这一组大多不影响流畅度，但都是「某天会突然变成线上问题」的类型。

### D1 — `trustAllCerts = true` 让全部 HTTPS 连接不做证书校验

`OkHttpClientModule.kt:73` 的默认参数 `trustAllCerts: Boolean = true`，配合第 132-148 行：

```kotlin
hostnameVerifier { _, _ -> true }
object : X509TrustManager {
    override fun checkServerTrusted(...) = Unit   // 空实现
    override fun getAcceptedIssuers() = emptyArray()
}
```

这个 `OkHttpClient` 是**全局单例**，新闻源抓取和 Coil 图片加载共用它。任何网络中间人都能替换文章内容和配图，而不触发任何警告。

这是上游为兼容自建 FreshRSS 的自签证书有意为之，属于取舍。但「全局放开」比「只对用户显式信任的站点放开」风险大得多。另外 `getAcceptedIssuers()` 返回空数组在个别 ROM 上会让 TLS 协商走异常分支。

**建议不改**（改动面大、直接影响同步，且用户可能正依赖它连自建服务），**但要记录在案**。若将来要收紧，方向是「单独的 client 只用于用户开启了自签证书信任的那个账户」。

### D2 — `CrashHandler` 不委托默认处理器，也不结束进程

`CrashHandler.kt:14-42`。它在 `init{}` 里直接替换掉 `Thread.setDefaultUncaughtExceptionHandler`，然后在 `uncaughtException` 里：

1. 只用 `startActivity` 打开报告页；
2. **不调用被替换掉的旧 handler**；
3. **不 kill 进程**。

进程会停在一个未定义状态。而且 `uncaughtException` 可能发生在任意线程，Android 12+ 对后台启动 Activity 有严格限制，`startActivity` 可能被静默丢弃——用户看到的现象就是「应用突然无响应 / 黑屏，但没退出也没重启」。

另外 `when (p1)` 的两个分支（`BusinessException` / `else`）代码**完全相同**，是死代码。

**改法**：崩溃路径改成「写日志 → 委托原 handler（或 `Process.killProcess(Process.myPid())`）」，报告页改为读独立崩溃日志文件。这是要动崩溃路径的改动，建议单独验证。

### D3 — CursorWindow 上限被反射拉到 100 MB

`MainActivity.kt:76-82`，为规避上游 issue #312 设的。`field.set(null, 100 * 1024 * 1024)`。

100 MB 的 CursorWindow 在低内存设备上是 OOM 的诱因，平板上分屏 + 大图列表时更危险。属于「用内存换不崩」的取舍。

**建议保留**（改小了可能复现 #312），但记录在此。若要做，方向是降到 10~20 MB 并观察是否复现。

### D4 — `printStackTrace()` 被当字符串拼进日志

`MainActivity.kt:81`：

```kotlin
Log.e("RLog", "Unable to increase cursor window size: ${e.printStackTrace()}")
```

`printStackTrace()` 返回 `Unit`，所以日志内容永远是 `... kotlin.Unit`，真正的堆栈被写到 stdout 而不是 logcat。属于「看起来记了日志、实际什么都没记」。

**改法**：`Log.e("RLog", "Unable to increase cursor window size", e)`。同文件 `CrashHandler.kt:25` 就是这么写的，照抄即可。

---

## E. 明确不做的事（省下无效工作）

**不要补 `@Immutable` / `@Stable`。**
Kotlin 2.2 的 Compose 编译器已在 Strong Skipping 模式下默认按 `equals` 比较参数、并自动记忆化 lambda。本仓库只在少数枚举/sealed class 上加了注解，这不是瓶颈。要判断某个类到底稳不稳定，应该打开编译器报告看数据，而不是凭感觉加注解。

> 需要数据时，在 `app/build.gradle.kts` 的 `composeCompiler {}` 里加
> `reportsDestination` 与 `metricsDestination`，构建后读生成的
> `*-composables.txt` / `*-classes.txt`。这是纯诊断，不影响产物。

**不要开 R8 full mode**（见 C4）。

**不要重写 sticky header 那段全量遍历。**
`ArticleList.kt:74-109` 的 `for (index in 0 until pagingItems.itemCount) { pagingItems.peek(index) }` 是 O(n) 的，上游已用 FIXME 标注（引用 issuetracker 193785330）。但 `FlowArticleListDateStickyHeaderPreference.default = OFF`——**默认根本不走这条路径**，只有用户手动打开「日期吸顶」才会踩到。真收到反馈时，正确的做法是给设置项加提示文案，而不是重写这段。

**不要把 `DiffMapHolder.diffMapSnapshotFlow` 的 `toMap()` 改掉。**
`DiffMapHolder.kt:48` 的 `snapshotFlow { diffMap.toMap() }` 每次变更都全量复制，理论上可以优化。但下游（`GroupWithFeedsListUseCase.kt:135`、`FeedsViewModel.kt:143`）已经把 `values.filter{}` 提到循环外了，量级可控。改它的收益远小于风险。

---

## F. 验证方式（本机无 JDK/SDK 的前提下）

本机跑不了 `./gradlew`，所以每一项都必须走下面的组合，不能凭"看着对"就发版。

1. **静态结构**：用技能 `android-edit-verify-offline` 的 `kotlin_balance_check.py` 扫改过的文件，确认括号与结构没破；按该技能的四步替代验证法走一遍。
2. **编译**：push 到 `main` 后由 `fork-auto-release.yaml` 出包，`fork-unit-tests.yaml` 保证 26 个单测不回归。
3. **体积前后对比**：`~/.workbuddy/skills/git-remote-ops-sandboxed-windows/scripts/fetch_ci_apk.py` 取改前/改后的 APK，比文件大小与 `unzip -l` 里的 dex 条目。
4. **profile 是否生效**：`unzip -l <apk> | grep dexopt`，看 `assets/dexopt/baseline.prof` 在不在、多大。
5. **真机冒烟清单**（B / C 级改动**必须**过一遍）：
   - 冷启动到信息流可交互的体感；
   - 列表快速滚动、长按菜单、左右滑动手势；
   - 标记已读 / 全部已读（含「无需确认」开与关两种）；
   - 切换分组 / 切换筛选 / 搜索；
   - 同步（本地 + 远程账户各一次）；
   - Widget 刷新、OPML 导入导出；
   - 覆盖安装（验证 migration 与签名）。

**发布纪律**：B4（DB migration）单独发一次版，不要和其他改动混在一个 commit 里——出问题时能立刻定位到是哪一项。
