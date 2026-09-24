# UPSTREAM-TRACKING.md — 上游可摘取内容核查

> 核查时间：**2026-09-24**　基线：本 fork `f40e6650`，上游 `upstream/main = d2b979cc`（2026-08-11）
> 本文回答一个问题：**上游有什么新东西，值不值得摘进本 fork。**

---

## 0. 一句话结论

| 来源 | 结果 |
|---|---|
| 上游 `main` 分支 | **0 个新提交**（本 fork = 上游 + 28 个自有提交，0 behind） |
| 上游 13 个分支 | **没有任何一个领先 main**；内容都已被 main 吸收，无可摘 |
| 上游正式 release | 最新仍是 **0.16.2（2026-06-03）**，本 fork 基线已在其之后 |
| 上游 open PR | **31 个，且 2026-09 仍有活跃提交** ← **真正的"更新"在这里** |

**所以：主干上没有东西要同步；值得考虑的是从 open PR 里主动摘取**（见 §3）。

---

## 1. 为什么分支列表会骗人

`git branch -r` 看到 13 个上游分支，其中 9 个 `ahead > 0`，看着像"有未合并的工作"。逐个核实后**全部已被 main 吸收**，原因是：

- 分支是 2024 年的实验分支，从**很老的 main** 分出去（`behind` 高达 400~567），
  上游后来以**不同的实现**在 main 上完成了同样的功能 → `git cherry` 的 patch-id 对不上，
  会误报成"缺失"。
- **所以判断"某功能在不在 main 里"不能只看 `git cherry` / `git diff`，必须落到代码内容上去核。**

---

## 2. 分支核查明细（结论已定，不必重查）

| 分支 | 最后提交 | ahead/behind | 判定 | 证据 |
|---|---|---|---|---|
| `release/0.14` | 2025-08-04 | 6 / 132 | **已全在 main** | `cherry-pick` 源头 `44aedb3a`、`593749f5` 经 `git branch --contains` 确认在 main；`MAXIMUM_ITEMS_LIMIT` main 已是 `"1000"`；`SyncWorker` 的 `setBackoffCriteria` + `CANCEL_AND_REENQUEUE` + `POST_SYNC_WORK` 链 main 都有；`GoogleReaderRssService` 的 `.map { it.shortId }` main 有 |
| `release/0.15` / `dev` / `feat/lazy_mark_as_read` | 2025-07~08 | **0** / — | **完全在 main** | ahead = 0 |
| `adaptive` | 2024-11-12 | 7 / 416 | **已被 main 更完整地实现** | main 有 `ui/page/adaptive/`（`ArticleListReadingPage` 220 行 + `ArticleListReaderViewModel` 490 行 + `PaneMotion` + `TopBar`）；分支用的是另一套 `ui/page/home/adaptive/`。swipe 迁移已成（main 有 `ui/component/swipe/` 包，分支里那个 `SwipeToDismissBox.kt` 已消失）；pull-to-load 已成（main 有 `PullToLoad.kt` 全套）；"scroll to currently reading" 已成（main `FlowPage.kt` 有 `scrollToItem`） |
| `concurrent-sync` | 2024-11-24 | 1 / 382 | **已在 main** | main 用 `Semaphore` 实现并发抓取，且分布更细：`LocalRssService` 16、`GoogleReaderRssService` 8、`ReaderWorker` 2 |
| `background-sync` | 2024-11-26 | 2 / 382 | **已在 main** | 含 `concurrent-sync` 那条，同判 |
| `fix-ua` | 2024-11-30 | 1 / 382 | **已修** | 分支修的是 `"\"ReadYou/${'$'}{versionName}(${versionCode})\""` 里多余的 `${'$'}` 转义；main 的 `app/build.gradle.kts` 已是正确插值 |
| `fix-webview-nestedscroll` | 2024-09-26 | 1 / 427 | **已在 main** | — |
| `feat-article-item-line-num` | 2024-02-07 | 1 / 532 | **已在 main** | main 的 `ArticleItem.kt` 已有 `LocalDensity` + 动态 `maxLines` |
| `refactor-ui` | 2024-01-21 | 1 / 567 | **无价值** | 全部改动 = `FeedsPage.kt` **1 行**，且对应 PR #555 仍是 `[RFC]` |
| `revert/nostr-sources` | 2025-04-07 | 1 / 365 | **已生效** | main 里已无 Nostr 源码（仅意大利语 strings 残留一个词） |
| `predictive-back` | 2024-11-12 | 2 / 401 | **唯一真未合并**，但不建议摘 | 65+/15-，改 `AndroidManifest.xml` + `NavGraphBuilderExt.kt`。2024 年的老代码，和本 fork 主题无关，价值低 |

---

## 3. 开放 PR：按"与本 fork 的冲突面"分组

判断冲突面的方法：把每个 PR 的改动文件与本 fork 相对上游改过的 38 个文件求交集
（本 fork 的改动集中在 `DataStoreExt.kt` / `Settings.kt` / `SettingsProvider.kt` /
`Preference.kt`（偏好注册 4 件套）、`FlowPage.kt` / `FeedsPage.kt` / `ArticleItem.kt`（UI）、
`values*/strings.xml`）。

### A 组 — 零冲突，且属于"抓取稳定性 / 防崩溃"（**建议优先摘**）

| PR | 规模 | 内容 | 备注 |
|---|---|---|---|
| **#1322** | 7 文件 +364/−195 | **用浏览器风格 UA 绕过 WAF 拦爬**。新增 `UserAgentInterceptor`：请求无 UA 或为 okhttp 默认值时替换成 `Mozilla/5.0 … Mobile …`（**不含 "ReadYou"**）。**自带 3 个单测** | ⚠️ 它**没有**处理 `trustAllCerts = true`（PERFORMANCE-AUDIT.md 的 D 级问题原样保留）。+187/−177 看着大，主要是 `setupSsl` 从嵌套位置提到顶层导致的缩进变化 |
| **#1323** | 2 文件 +105/−33 | 正确探测并解码字符集（修非 UTF-8 语言乱码）。**自带单测** | 只碰 `RssHelper.kt` |
| **#1324** | 1 文件 +17/−2 | 保存图片时截断过长文件名，防 `ENAMETOOLONG` 崩溃 | 最小改动 |
| **#1320** | 2 文件 +2/−2 | 打开链接失败时重置 intent 包名 | 最小改动 |
| **#1317** | 2 文件 +2/−2 | 编辑 greader 账号时保留 URL 尾部斜杠 | 最小改动 |

### B 组 — 低冲突（只撞 `strings.xml`，手工解冲突容易）

| PR | 规模 | 内容 |
|---|---|---|
| **#1325** | 8 文件 +280/−5 | 订阅失败时给出可读的错误信息（而非笼统提示） |

### C 组 — 撞"偏好注册 4 件套"，需要手工解冲突

本 fork 在那 4 个文件里加了 2 个自有偏好 key，而这些 PR 都要加自己的 key。
**注意：冲突点在同一个 `keys` map / `Settings` 数据类附近，不会自动合并，但都是"各加各的"，按 FORK-NOTES §2.6 对照补就行。**

| PR | 规模 | 内容 | 与本 fork 的关系 |
|---|---|---|---|
| **#1319** | 10 文件 +214/−3 | **自适应列表的单栏开关**（`FlowSingleColumnPreference`） | **与本 fork 平板主题直接相关**。⚠️ PR 里含一个无关的 `CLAUDE.md`（+96，AI 提示词文件）**必须剔除** |
| **#1330** | 13 文件 +465/−120 | 隐藏同源重复文章的开关 | 功能新增 |
| **#1326** | 17 文件 +1057/−77 | 组 / 订阅的手动排序 | 功能新增，体量较大 |

### D 组 — 不建议整体摘

| PR | 规模 | 为什么不建议 |
|---|---|---|
| **#1305** | 44 文件 +1694/−210 | **标题骗人**。叫 "Fix sync worker stall on persistent errors"，实际是作者长期分支的 **23 个提交**杂烩：真修复只有前 2 个（`prevent sync worker from stalling`、`reset lastEnqueueTime on app restart`），其余含 **version bump 到 0.16.3**、**DB schema 升到 8（+421 行的 `8.json`）**、新功能（阅读标题显示控制）、一堆 read-overlay 修复、`.gitignore` chore。整体摘会把本 fork 的版本号和 DB 版本一起顶掉 —— **若要用，只能单挑前 2 个提交**，且要确认它们与后续提交的依赖关系 |
| `predictive-back`（分支） | 2 提交 65+/15- | 见 §2 |

---

## 4. 其余 21 个 open PR（未逐项评估，供扫读）

`#1310` Weblate 翻译 · `#1320`（已评估）· `#1316` 新增 Miniflux provider · `#1304` 自定义请求头 ·
`#1301` 订阅框用 URL 键盘 · `#1268` 编码 referer · `#1261` README 签名证书 · `#1251` 新建分组首字母大写 ·
`#1210` AI 摘要 · `#1238` 翻译功能 · `#1228` 非法日期兜底 · `#1183` 标题关键词过滤 · `#1179` Substack 解析 ·
`#1134` (WIP) 类型安全偏好重构 · `#555` 键位视觉重设计 [RFC] · `#593` 文章行数自适应 [RFC] ·
`#688` 条目消失动画 · `#997` 用 kotlinx.serialization 替换 Gson · `#967` launchMode 改 singleTop ·
`#873` RYScaffold 改用 TopAppBar 滚动行为 · `#162` flow 页图片 URL 修复

> 注意 `#1134`（类型安全偏好重构）如果上游真做了，会**整个换掉偏好注册机制** ——
> 那是本 fork 挂载点最密集的地方，届时 FORK-NOTES §2.6 要重写。

---

## 5. 摘取的风险（先想清楚再动手）

1. **摘 PR = 主动走在上游前面。** 上游若最终合并了同一个 PR，本 fork 会有一份"同内容不同 sha"的
   重复改动，届时 merge 会出现大量冲突 —— 必须手工判断"这段我已经有了"。
2. **摘取不改变"0 behind"的性质**，但会让 `ahead` 数字变大，与上游的差异面变宽。
   **零冲突的 A 组风险最低**（碰的都是上游 PR 自己的文件，将来上游合并时 git 能认出是同一改动）。
3. **C 组的真正成本不在代码，在偏好注册。** 每加一个 key 要按 FORK-NOTES §2.6 走完 5 个地方，
   漏一个**不报错、只静默失效**。
4. **#1305 的 DB schema 8**：若同时做 PERFORMANCE-AUDIT.md 里的 B4（加复合索引），
   两边都要占一个 Room 版本号 —— **不要并行做**。

---

## 6. 复现这套核查（命令备忘）

```bash
export PATH="/c/Users/lfk/.workbuddy/binaries/PortableGit/versions/1.2.0/usr/bin:/c/Users/lfk/.workbuddy/binaries/PortableGit/versions/1.2.0/bin:$PATH"

# 1) 拉上游全部分支（只 fetch main 会漏掉分支）
git -c http.schannelCheckRevoke=false -c http.version=HTTP/1.1 fetch upstream "+refs/heads/*:refs/remotes/upstream/*"

# 2) 每个分支是否领先 main
for b in $(git for-each-ref --format='%(refname:short)' refs/remotes/upstream/); do
  printf "%-42s ahead=%-4s behind=%-4s\n" "$b" \
    "$(git rev-list --count upstream/main..$b)" "$(git rev-list --count $b..upstream/main)"
done

# 3) patch-id 层面判断（会有误报，必须再用代码内容复核）
git cherry -v upstream/main upstream/<branch>

# 4) 本 fork 改过哪些文件（冲突面计算的基准）
git diff --name-only upstream/main HEAD

# 5) 查 open PR。注意本机 curl 是 Windows 版，输出路径必须写 C:/... 不能写 /tmp
TOKEN=$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null | sed -n 's/^password=//p' | tr -d '\r')
curl -s --ssl-no-revoke -H "Authorization: token $TOKEN" \
  "https://api.github.com/repos/ReadYouApp/ReadYou/pulls?state=open&per_page=40" -o "C:/Users/lfk/AppData/Local/Temp/prs.json"

# 6) 某个 PR 改了哪些文件（用于算冲突面）
curl -s --ssl-no-revoke -H "Authorization: token $TOKEN" \
  "https://api.github.com/repos/ReadYouApp/ReadYou/pulls/<N>/files?per_page=100" -o "C:/Users/lfk/AppData/Local/Temp/files<N>.json"

# 7) 拿某个 PR 的完整 diff（文件列表不够时）
curl -s --ssl-no-revoke -H "Authorization: token $TOKEN" -H "Accept: application/vnd.github.v3.diff" \
  "https://api.github.com/repos/ReadYouApp/ReadYou/pulls/<N>" -o "C:/Users/lfk/AppData/Local/Temp/p<N>.diff"
```

**必须做的陷阱规避**：`git diff A...B`（三点）比较的是 `merge-base(A,B)` 与 B，
在"两边各自实现了同样功能"的场景下会**显示一堆其实两边都有的差异**。
判断"main 缺什么"要用**两点 diff**（`git diff A B`）或直接读代码。
