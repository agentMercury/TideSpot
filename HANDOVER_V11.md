# 交接文档 · 潮点 TideSpot v1.1（HANDOVER_V11）

> ⚠️ **历史交接（2026-11-10）**：v1.1 已收官，**当前施工以 `HANDOVER_V115.md` + `OPT_REQUIREMENTS_V115.md` 为准**。本文档保留 v1.1 全量实现细节与环境雷区，供查阅。

> 交接日期：**2026-11-10** ｜ 交接方：v1.1 P1 批次开发会话（交付负责人） ｜ 接手方：**下一开发会话**
> 交接起点：**v1.1 P2 批次开工**（P0-1 / P0-2 / **P1 全部完成**；P1 真机冒烟待执行，见 §9）
> **必读顺序**：本文件 → `OPT_REQUIREMENTS_V11.md`（v1.3 需求规格，含 D1–D15 裁定）→ `REQUIREMENTS_REGISTER.md`（**CHG-17/19/20/21/23/24/25/26/27** 为 v1.1 相关）→ `tidespot-android/CONTRACT.md`（「v1.1 子契约 P0-1」）→ `PROGRESS_TRACKER.md`（§2/§2b）→ `design-handoff/INDEX.md` + `DECISIONS.md`（UI v2 设计包）→ 本仓库根 `HANDOVER.md`（v1 历史交接，环境须知仍有效）

---

## 1. 当前状态（2026-11-10 交接时）

| 批次 | 范围 | 状态 |
|---|---|---|
| **P0-1** | OPT-V11-01 凭据外部化 + 首次配置向导 + i18n 框架 + APP 图标 | ✅ 完成（真机冒烟通过，CHG-25/26） |
| **P0-2** | 02 水质实测录入、03 出发时间筛选、04 F-3.7 元数据、25 Top10 核心段、26 阈值 75+爆护 | ✅ 完成（真机冒烟通过，CHG-27） |
| **P1** | 05 真预取、06 相对时间、07 涨退潮观测、08 跨点缓存、09 校正提示、10 搜索筛选、11 陈旧标记、18~24 UX 七项 | ✅ 完成（2026-11-10，**CHG-28**；八模块 701/0/0 + APK 复产出；**真机冒烟待执行**） |
| **P2** | 12 批量操作、13 i18n 全量外移、14 智能排序、15 详情卡片、16 分组、17 缩略图 | ✅ **4/6 完成（2026-11-10，CHG-37）**：12/14/15/16 落地（真机冒烟通过）；**17 缩略图按 D21 移除**（CARTO 栅格瓦片需 key）；**13 i18n 存量外移未开工**（评估见 `PROGRESS_TRACKER.md` §9） |

- **基线**：八模块 **767 运行 / 0 失败 / 0 跳过**（`verify.ps1` 全绿）；APK `潮点TideSpot-v1.2.0.apk`（`versionCode=4 / versionName=1.2.0`，51,667,332 B，自签名，自动分发 `E:\Trans`；v1.0/v1.1/v1.1.1 三包仍保留可回退）
- **v1（M0–M6）交付验收窗口**：2026-12-04 ~ 12-08（原计划），v1.1 为提前启动（客户 2026-11-10 指令）
- 版本管理：jj + git colocated；当前 git `main` 与 jj `@` 同步（每次提交后需手动 `git update-ref`，见 §4.4）

## 2. 本次会话已完成（接手无需重复）

### P1（CHG-28，2026-11-10，14/14）
- **OPT**：05 `DetailPrefetcher`（前 5 / TTL÷2 / 令牌不足放弃 / 并发 2 / 离线零请求）｜06 `RelativeTimeFormatter`（收藏摘要 + 详情 Top10）｜07 四类观测 + 时刻补记 + 模型对照 + 按类型 `fitManual` + 落库读回｜08 `TopWindowsLruCache`（100/LRU/不落库）｜09 `find(manualTideLagHours)` 注意事项校正句｜10 `FavoritesFilterLogic` + 搜索/标签/预设 chips｜11 `lastFetchedMillis` + `FavoritesStaleLogic`（复用 `Ttl`）
- **UX**：18 冷启动定位动画（不设准星）+ 返回地图定位｜19 默认 Voyager + 设置三选｜20 删底部 dock + 地图浮动三按钮 + insets｜21 `sourcePage` + `BackHandler`（详情→来源、收藏/设置→地图、地图 2s 双退）｜22 Tab 序 = 钓鱼指数→潮流→天气→水质→风浪（三处联动，默认 `SCORE`）｜23 `TidePhase` 月龄制 + 潮 Tab chip｜24 DaySelector 日期+星期
- **验证**：八模块 **701/0/0** + `assembleRelease` 成功；APK `潮点TideSpot-v1.1.apk` 已分发 `E:\Trans`；jj 提交 `3f1c9df`（**P1 当时基线；含 v1.1.1 修复后的当前基线见 §1**）
- **细节与口径**：见 `PROGRESS_TRACKER.md` §7、`REQUIREMENTS_REGISTER.md` CHG-28、`tidespot-android/CONTRACT.md`「v1.1 子契约 P1」、本文件 §9 末尾「P1 落地要点」

### v1.1.1 缺陷修复（CHG-35，2026-11-10，客户真机报障两例）
- **Bug A｜海域点显示「水位（无潮汐）」且无潮汛 chip**：根因＝确认选点恒传 `marineHasValue=null` → OSTIA（**全球 SST，海洋必然有值**）被当湖泊掩膜 → 误判 `INLAND_WATER` 并 30d 缓存落库。修复：`TideSpotRepository.probeMarineHasValue`（有海况→`NEARSHORE`，无→OSTIA 分湖/陆，失败→`UNKNOWN`）+ 判定缓存键 **v2**（旧错误判定失效）。
- **Bug B｜地图拖动信息条恒「未命名水域」**：根因＝`CrosshairStateMachine.onMapMoved` 忽略 `Idle`。修复：`Idle + 用户手势 → Preview`（`fromUserGesture`；`MapScreen` 用 `REASON_API_GESTURE` 区分手势/程序动画）。
- **存量自愈**：详情刷新若「海洋有值」且 spot 为 `INLAND/UNKNOWN` → 回写 `NEARSHORE`，并透出 `SpotDetailResult.effectiveWaterBody`（契约尾增）；`DetailScreen` 以此取标题与潮汛 chip → 老收藏点首屏即恢复。
- **版本/验证**：`versionCode=3`、`versionName=1.1.1`；八模块 **716/0/0**；`潮点TideSpot-v1.1.1.apk`（SHA256 `D66A3F18…3E77E`）已分发 `E:\Trans`；寄存器 CHG-35。
- **遗留风险**：OSTIA 图层本质是全球 SST，**不可再作湖泊掩膜**；内陆湖现依赖「marine 全 null/4xx → OSTIA」，后续如恢复该探针需换真湖泊产品。

### P0-1（CHG-25/26）
- **凭据外部化**：`:core:data/water/CopernicusCredentialStore.kt`（`CopernicusCredential` + `FileCopernicusCredentialStore`，java.io 原子写）、`CopernicusCredentialValidator.kt`（四态 `Missing/Valid/Invalid/Unreachable`）；`WaterBodyClassifier.setProbe`（@Volatile 运行期切换）；`:app` **移除 BuildConfig 凭据注入**、`AppGraph.checkAndApply/hasCopernicusCredential/clearCopernicusCredential`；Manifest `allowBackup=false`
- **设置页凭据版块**：`:feature:settings/CopernicusCredentialSection.kt` + `CopernicusFormLogic.kt`（错误码枚举）；`SettingsScreen` 尾增 `credentialSection` slot
- **首次引导卡**：`:feature:detail/InlandCredentialGuideCard.kt`；`DetailScreen` 尾增 `credentialGuideSlot` slot；`:app` 判定（内陆+未配置+未跳过），跳过标记 `guide.copernicus.skipped`
- **i18n 框架**：`:core/ui/src/main/res/values/strings.xml` + `values-en/strings.xml`（17 条 GAP-05 定稿 + v1.1 新增串，key 前缀 `ts_`）；**规范**：新增用户可见文案必须进资源（Screen 禁硬编码；存量 `TideSpotStrings` object P2 外移）
- **APP 图标**：`app/src/main/res/` `ic_launcher_{foreground,background,monochrome}.xml` + `mipmap-anydpi-v26/ic_launcher(_round).xml`（源稿 `design-handoff/icon/*.svg`）
- **CHG-26 口径修正（客户裁定）**：**水质数值从未接入卫星数据**（`Derivations.qualitySeries` 唯一生产点）；凭据仅影响**水体判定**；水质徽标旁注「模型估算 · Copernicus 卫星水质数据未接入」——**不要试图用配置凭据点亮「卫星」徽标**（如需真卫星数据须另立项）

### P0-2（CHG-27）
- **阈值 75 + 爆护**：`BestWindowFinder.CANDIDATE_MIN=75`（单点）；`Top10Formatter.burstBadge`（≥80 → `ScoreBand.EXPLOSIVE`）
- **核心段**：`BestWindow` 尾增 `coreStartEpochSec/coreEndEpochSec/truncatedUntilEpochSec`；rank=`0.62·peak+0.23·coreMean+1.4·min(coreLen,4)`（**系数不变**；原窗 ≤4h 恒等）；`durationHours` = **核心段小时数**；`Top10Formatter.sustainedLine`（「利好持续至 HH:mm」）
- **时段筛选**：`BestWindowFinder.find(..., windowStartEpochSec, windowEndEpochSec)`（0/0 恒等锁；仅输出过滤/原窗相交）；`Top10TimeFilter`（ALL/24h/本周末/自选）；`DetailStateHolder.top10Filtered` + `DetailViewModel.top10Filtered`；Top10 Sheet chips + 自选对话框
- **F-3.7**：`SourceMetaRegistry`（模型名/分辨率/粒度映射）→ `SourceFooter`
- **水质实测录入（OPT-V11-02 全链）**：Room **v4→v5**（`manual_observation.kind/value` + `MIGRATION_4_5`）；`ManualObservationDao.forSpotKind`（校潮/水质观测隔离）；`TideSpotRepository.addWaterQualityObservation/waterQualityObservations`；`WaterQualityEntryLogic`（范围/时刻校验）+ `WaterQualityEntrySheet`（**操作按钮置顶**）；融合 `Tw=0.6·实测+0.4·模型`（±6h），徽标 → `MEASURED`

### 真机冒烟（2026-11-10，AAK-AN00 / Android 16，全部通过）
安装升级（**v4→v5 迁移不丢数据**）→ 收藏保留 → 内陆点标题「湖面风与水温」→ F-3.7 元数据行（`ECMWF WAM / GFS Wave · 9/16 km · 逐小时 · 本机拉取`）→ 源更新「—」（禁编造）→ Top10（chips 四枚 + **爆护**徽标 + 核心段「时长 4 小时」）→ 时段筛选空态「所选时段无 ≥75 分窗口，时段内最高 71 分（时刻）」→ 水质录入（保存成功、已录入列表、**徽标转「实测」**、摘要温度随融合变化、重装后记录仍在）

### 冒烟中发现并已修复的 2 个真实缺陷（教训）
1. **Sheet 操作按钮贴底不可点**：ModalBottomSheet 半展开/底部手势条区域导致贴底按钮触摸失效（=design-handoff **GAP-03** edge-to-edge insets）。修复：`skipPartiallyExpanded=true` + **操作按钮置顶** + `navigationBarsPadding`。**P1/P2 新增 Sheet/底部操作时务必照此**
2. **徽标取首小时导致融合不体现**：`firstNotNullOfOrNull` 取当日 00:00（超出 ±6h）→ 恒为估算。修复：当日**任一小时** `MEASURED` 即显示「实测」

## 3. 剩余任务清单（按 OPT_REQUIREMENTS_V11 §3 逐条执行）

> 每项验收标准以 `OPT_REQUIREMENTS_V11.md` §3 原文为准（本表仅列要点与落点）；**开工前先登记台账（下一 CHG 号，当前最大 CHG-27）**。

### P1（建议顺序：先纯函数后 UI）
| 项 | 要点 | 落点 |
|---|---|---|
| OPT-V11-05 真预取 | 进收藏列表后台刷新 `lastViewedAt` 前 5 条；TTL/2 + 令牌桶 6 req/min；并发 ≤2；失败静默（fetch_log）；**不引 WorkManager** | `:app`、`:core:data` |
| OPT-V11-06 相对时间 | `今天/明天/周X/M月d日 HH:mm`，按选点当地时区（复用 `SpotZones`） | `:core:ui`（新纯函数）+ favorites/detail |
| OPT-V11-07 涨退潮观测 | 四类观测（高潮/低潮/开始涨/开始退）+ 时刻可改（补记）+ 模型对照；`fitManual` 按类型匹配；≥2 条 | `:core:domain`、`:feature:detail`、`:core:data`（kind 枚举**只增**） |
| OPT-V11-08 跨点 Top10 缓存 | 内存 LRU（容量 100）；失效=该点刷新/排序切换/algoVersion 变化/进程重启；**不落库** | `:feature:favorites` |
| OPT-V11-09 校正状态提示 | 有有效校正时 Top10「注意事项」附「已按现场校潮校正：提前/延后 Xh」 | `:core:domain`（warningsOf 尾增）+ detail |
| OPT-V11-10 搜索筛选 | 列表搜索框 + 标签/预设 chips；`FavoritesFilterLogic` 纯函数（先筛后排） | `:feature:favorites` |
| OPT-V11-11 陈旧标记 | 副标题附数据时间；TTL 超 → 陈旧徽标；≥48h → 已过期（复用 `Ttl.unusableAgeMillis`，禁新阈值） | favorites + `:core:data` 透出 |
| **UX-18 地图定位** | 冷启动已授权→相机到当前位置（世界视图过渡、无误设准星）；返回地图→上一查看点/lastViewed 最新；动画 ≤800ms；不改准星唯一真源 | `:feature:map`（**禁改需台账单点放行**）、`:app`（AppNav） |
| **UX-19 地图样式** | 默认 **CARTO Voyager**；设置三选（Voyager/Positron/Dark Matter）持久化；深色系统下不覆盖用户选择；URL 收敛 `TideSpotUrls` | `:core:data`（URL）、map、settings |
| **UX-20 移除底栏+浮动按钮** | 删 `MainActivity` NavigationBar；地图页右下浮动三按钮（收藏/Top10/设置）；edge-to-edge insets（**GAP-03**）；AppNav 8 条不变量锁改写 | `:app`、`feature:map` |
| **UX-21 返回手势** | `BackHandler`：详情→来源页（AppNav 加 sourcePage）；收藏/设置→地图；地图 2s 双返回退出；predictive back 不卡顿 | `:app` |
| **UX-22 Tab 顺序** | 仅改 `WaterBodyTabTitles.titlesInTabOrder()` 顺序 = 钓鱼指数/潮流/天气/水质/风浪；既有互异锁保持 | `:core:ui` |
| **UX-23 潮汐术语** | `TidePhase.of(ageDays)` 纯函数（**D13 口径：大潮期=活汛/小潮期=死汛/过渡按潮差增减**）；海水点潮流 Tab 顶部 chip「大潮 · 活汛」；内陆不显示；**不进评分** | `:core:domain`、detail |
| **UX-24 日期选择器** | DaySelector 改「今天 周二」「11/12 周三」（当地时区）；跨零点自愈保持 | `:core:ui`（`DaySelectorLogic`） |

### P2

> **状态（2026-11-10，CHG-37）**：OPT-V11-12 / 14 / 15 / 16 **已完成**（纯函数 + JVM 锁 + UI 接线 + 真机冒烟）；**OPT-V11-17 缩略图按 D21 移除**（CARTO 栅格瓦片已需 API key，无 key 返回鉴权占位图）；
> **OPT-V11-13 i18n 存量全量外移未开工**（需专项批次，评估见 `PROGRESS_TRACKER.md` §9）。下表为原始要点，备查。
>
> 本批落地补充裁定（D16–D20）：**D16** 长按 = 多选（信息卡片改「信息」按钮展开）；**D17** 缩略图同底图瓦片口径（不占令牌桶、不写 fetch_log）；**D18** 预设改稳定 id + 旧中文值兼容；**D19** 删除级联清理 6 张表；**D20** 版本 `1.2.0`（versionCode=4）。Room **v5→v6**（`spot.groupName`）；新增 `Format.coordinate`（6 位小数）。

| 项 | 要点 | 落点 |
|---|---|---|
| OPT-V11-12 批量操作 | 多选/全选/批量删除（事务）/批量改标签；**不做导出**（D3） | favorites + data |
| OPT-V11-13 i18n 全量外移 | 存量 `TideSpotStrings` object 全面迁 res（零行为漂移枚举锁）；英文复核 | 全模块 |
| OPT-V11-14 智能排序 | 「综合推荐」= `rank` 降序（同 rank 按 name） | favorites |
| OPT-V11-15 详情卡片 | 坐标 6 位小数/精度来源/海拔/水体徽标/最近拉取（只读库） | favorites |
| OPT-V11-16 收藏分组 | `spot.groupName`（**Room v5→v6 迁移**）；分组折叠；默认「未分组」 | data + favorites |
| OPT-V11-17 缩略图 | 128px 静态图；MapLibre Snapshotter 可用则用，否则 CARTO 静态瓦片（缓存 30d/点，守频控）；**仅装饰** | favorites + network |

### 其它挂起（非阻塞）
- **A-07**：OSTIA 恢复后复测千岛湖掩膜（DATA_SOURCE_PROBE A6-r4）
- **真卫星水质数据**：如需「卫星」徽标数据，需新立项（CMEMS 客户端/DTO/频控，~2-3 天）
- v1 正式验收（12-04~12-08）：§13 清单逐项留证 + F-2.2/首屏 <300ms/跨时区双点

## 4. 环境须知（关键，避免踩坑）

### 4.1 构建
```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:GRADLE_OPTS='-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=31181 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=31181 -Djavax.net.ssl.trustStoreType=WINDOWS-ROOT'
subst T: "D:\workspace\天气鱼情\tidespot-android"   # 重启后失效，先查 subst
D:\workspace\天气鱼情\.tools\gradle-8.9\bin\gradle.bat :app:assembleRelease --console=plain -p 'T:\'
```
- **必须用 `-p T:\`**（物理中文路径 D:\ 下 test worker 类加载不可靠，R-09）
- **代理**：系统代理 `127.0.0.1:31181`（user 注册表）；Gradle 不继承 → `GRADLE_OPTS` 注入；MITM 证书靠 `trustStoreType=WINDOWS-ROOT`
- 一键验证：`powershell -ExecutionPolicy Bypass -File "D:\workspace\天气鱼情\tidespot-android\verify.ps1"`（自动注入代理 + subst 自愈 + 红线扫描 + 八模块 + assembleRelease）
- 新依赖：**先确认 maven 可达**（本机经代理可用）；Robolectric 运行期自下载 android-all 走独立 JVM → `core/ui/build.gradle.kts` 已透传代理属性

### 4.2 APK 产物
- 命名 `潮点TideSpot-v{versionName}.apk`（`versionName` 变更自动跟随）；debug 带 `-debug`
- **编译完成自动复制 release 到 `E:\Trans`**（`copyApkToTrans` + `finalizedBy`）；当前 `versionCode=3 / versionName=1.1.1`（v1.1.1 缺陷修复包；v1.1 包仍保留于 `E:\Trans` 便于回退）
- 签名：`keystore/tidespot.jks`（自签名，密码在 `local-secrets.properties` 的 `signing.*`，不入库）

### 4.3 真机
- 设备：AAK-AN00（荣耀 Win RT / Android 16 / 无 GMS）；`adb` 在 `C:\Users\gamer\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- **UI 自动化经验**（本次冒烟沉淀）：`uiautomator dump` + 按 bounds 中心 tap；**键盘会改变布局**→先 `keyevent 4` 关键盘再点；Sheet 底部按钮不可点（见 §2 缺陷 1）；用户正在用机时自动化会落到其他 App，**先 `dumpsys window | findstr mCurrentFocus` 确认前台**

### 4.4 jj（易踩坑）
- jj 在 `C:\Users\gamer\AppData\Local\Microsoft\WinGet\Links\jj.exe`（**不在 PATH**）
- 中文提交描述：**PowerShell 管道会转码损坏** → 用 `cmd /c "type file | jj describe -r @ --stdin"`（文件为 UTF-8 无 BOM）
- `jj log -r @` 在 PowerShell 中 `@` 需加引号 `'@'`
- 提交后需 `git update-ref refs/heads/main <hash>` + `git symbolic-ref HEAD refs/heads/main` 让 git 侧可见（jj `git export` 对 @ 报 "Nothing changed"）
- **`design-handoff/` 两个大文件**（`TideSpot-UIv2-design-handoff.zip` 2.2MB、`assets/dark-adaptation.svg` 1.1MB）超出 jj 快照 1MB 上限 → 一直无法入库；需决定 `.gitignore` 排除或 `jj config set --repo snapshot.max-new-file-size 2304382`
- **可能并行会话共用同一工作区**（本次遇到过：另一环节的文档变更与提交混入）；提交前 `jj status` 核对，描述注明来源；大型并行任务建议 `jj workspace add` 隔离

## 5. 契约与红线（必须遵守）

- **契约只增不改**：`Models.kt`/`CONTRACT.md` 只增（尾增可选参数 + 台账登记）；Room 迁移只增表/列
- **不得回退点**（HANDOVER.md §7 十一条）+ v1.1 新增：
  - 凭据禁入 BuildConfig/源码/资源/日志（APK 解包零命中为退出标准）；`allowBackup=false`
  - **`WaterBodyTabTitles.titlesInTabOrder()` 唯一 Tab 标题入口**（UX-22 只改顺序）
  - **预设解析只走 `HourlyScoreStore.resolvePreset`**；Top10 时间域 `nowEpochSec` 过滤不得移除
  - 设计包冻结：`ScoreBands` / `ConfidenceBadge` / `TideSpotTheme` 取值**禁改**；六枚徽标不得新增
  - 全部外部 URL 只在 `TideSpotUrls` 拼接（新增注册页 URL 已收敛）
  - 新增文案进资源（中/英），禁 Screen 硬编码
- **测试**：UI 逻辑先抽纯函数 + JVM 锁；每条 GWT 至少一条自动化断言；改完跑 `verify.ps1`

## 6. 已知偏差与口径（已裁决，勿重复决策）

| 项 | 口径 |
|---|---|
| 水质徽标融合态 | 显示既有 `MEASURED`（实测），**不新增第七枚「实测+模型」**（设计冻结） |
| 水质卫星数据 | 未接入（CHG-26 客户裁定仅修文案）；凭据只影响**水体判定** |
| `BestWindow.durationHours` | = **核心段小时数**（≤5 整点/跨度 ≤4h）；原窗尾由 `truncatedUntilEpochSec` 表达 |
| 峰值在窗首/尾 | 核心段被窗界截断（如 4h 窗峰在首 → core 3h），rank 随之为实际截断值 |
| 徽标/时长口径 | Top10 条目「爆护」徽标 ≥80（`ScoreBand.EXPLOSIVE`） |
| 源更新时间 | 数据层未透出 → 显示「—」，**禁编造**（CONFLICT-03） |
| 校潮 vs 水质观测 | 同表 `manual_observation`，靠 `kind` 隔离；校潮拟合**只读** `TIDE_OBSERVATION` |
| **OSTIA 图层口径（CHG-35 起）** | 该图层是**全球 SST**（海洋必然有值），**不可再作湖泊掩膜**；水体判定主判据＝`probeMarineHasValue` 的真实海况（true→NEARSHORE / false→OSTIA 分湖陆 / null→UNKNOWN）；判定缓存键带 `JUDGE_VERSION`，改判据必须升版本号 |

## 7. 关键文件索引

| 文件 | 说明 |
|---|---|
| `OPT_REQUIREMENTS_V11.md` | v1.1 需求规格 v1.3（26 条 + D1–D15 裁定）|
| `REQUIREMENTS_REGISTER.md` | 台账（CHG-17~27 为 v1.1）|
| `tidespot-android/CONTRACT.md` | 「v1.1 子契约 P0-1」（P0-2 契约在台账 CHG-27）|
| `design-handoff/` | UI v2 设计包（INDEX/DECISIONS/tokens/screens/components/copy/mapping/icon）|
| `core/domain/window/BestWindowFinder.kt` | Top10 核心段/阈值/时段筛选（P0-2 全部逻辑）|
| `core/data/water/CopernicusCredential*.kt` | 凭据存储/校验 |
| `core/data/repo/SourceMetaRegistry.kt` | F-3.7 元数据映射 |
| `core/data/local/TideSpotDatabase.kt` | Room v5 + 全量迁移（1_2/2_3/3_4/4_5）|
| `feature/detail/detail/WaterQualityEntrySheet.kt` | 水质录入面板（按钮置顶范式）|
| `feature/detail/detail/Top10TimeFilter.kt` | 时段筛选纯函数 |
| `feature/settings/CopernicusCredentialSection.kt` | 凭据设置版块 |
| `tidespot-android/verify.ps1` | 一键验证（subst/代理/红线/八模块/APK）|

## 8. 风险预警

| 风险 | 影响 | 概率 | 应对 |
|---|---|---|---|
| P1 UX-20 移除底栏 + insets | 导航回归/内容遮挡 | 中 | 逐屏补 `navigationBarsPadding`；AppNav 8 条不变量锁改写后必须全绿；真机逐屏冒烟 |
| P1 UX-19 地图样式 | attribution/缓存/断网 | 低 | URL 只进 `TideSpotUrls`；断网回上一帧不 crash |
| P2 OPT-V11-16 Room v6 | 升级路径（CHG-18② 教训） | 中 | 迁移 SQL 列名与实体逐字一致 + 真机 MigrationTestHelper |
| 并行会话共用工作区 | 提交混入/互相覆盖 | 中 | `jj status` 先核对；必要时 `jj workspace add` |
| 用户用机干扰自动化 | 冒烟结果误判 | 中 | 每次操作前确认前台 Activity；关键结论用 coredump/日志双证 |

## 9. 下一步（接手第一件事）

1. 跑 `verify.ps1` 确认基线 **767/0/0**
2. **真机冒烟 P2 五项**（APK `潮点TideSpot-v1.2.0.apk` 已分发）：① 分组：新建/编辑点设分组 → 组头折叠/展开 → 组内顺序随排序 → 「未分组」恒在最后；② 长按卡片进多选 → 全选/反选 → 批量删除二次确认（条数）→ 列表与缓存同步；批量改标签（追加/替换）、批量改分组；③ 点按（非多选态）进详情、多选态点按 = 选择；④ 卡片「信息」展开：坐标 6 位、海拔/最近拉取缺值显示 `—`；⑤ 缩略图：滚动帧率、断网 → 占位不破图；⑥ **Room v5→v6 升级不丢数据**（老收藏全部进「未分组」）
3. **真机迁移测试**：✅ 已完成（2026-11-11，AAK-AN00/Android 16）——`connectedDebugAndroidTest` **8/8 通过**，含 `v4→v5`（kind/value 默认值）与 `v5→v6`（groupName 归未分组）用例；另经 v1.2.0 覆盖安装实证「5 条收藏保留 + 分组列生效」。**注意**：AGP 8.7.3 需 `com.android.tools.utp:*-31.7.3`，本机代理关闭时无法下载 → 可清空 `GRADLE_OPTS` 走 Google Maven 直连（实测可达）
4. **OPT-V11-13 i18n 存量全量外移**：**移交后续对话执行**（专项批次；评估见 `PROGRESS_TRACKER.md` §9：468 处/47 文件、domain 注入层、30+ 测试改造、注释感知红线扫描；预估 2–3 天）。接手起点：`core:ui/strings/TideSpotStrings.kt`（33+ 常量）、`:core:ui/res/values{-en}/strings.xml`（现有 30 条 `ts_`）、`HourlyScoreStore.resolvePreset`（预设按 D18 改稳定 id + 旧中文值兼容）
5. 每完成一项：登记台账 CHG → 纯函数锁测试 → 全量回归 → 真机冒烟（可行时）→ jj 提交（描述用 cmd 管道）
6. P2 收口后更新本文件或再交接

> **P2 落地要点（CHG-37 摘要，接手先读）**
> - **长按 = 多选**（`FavoritesViewModel.toggleSelection`）；卡片 `combinedClickable`：非多选态点按=进详情、多选态点按=切换选择。信息卡片由卡片内「信息」按钮（`toggleInfo`）展开——**不要改回长按**（与批量手势冲突，客户裁定 D16）。
> - **删除级联（D19）**：`TideSpotRepository.deleteFavorites(ids)` 单事务清 6 表（`db: TideSpotDatabase?` 注入才走 `withTransaction`；JVM 测试为顺序执行）。`deleteFavorite(id)` 已复用该路径，**不得**恢复只删 spot+hourly_score 的旧实现。
> - **分组（v6）**：`spot.groupName` 可空无 DEFAULT（NULL = 未分组，**禁空串**）；`FavoritesGroupLogic` 组间自定义升序 + 「未分组」置末；组内沿用排序结果。
> - **综合推荐排序（14）**：`SortOrder.RECOMMENDED` 用 `rankBySpotId`（`BestWindow.rank` 降序，同 rank 按 name，未加载排最后）；排序切换仍清空 `TopWindowsLruCache`（P1 口径）。
> - **缩略图（17）已按 D21 移除**：CARTO `rastertiles/*` 实测需 API key（返回「API KEY REQUIRED」占位图）→ 客户裁定删除该功能，相关类/URL/测试已全量删除。**若后续重启该需求**：须先验证图片源可用（MapLibre Snapshotter 本地渲染 / 自备 key / 免 key 合法源），并同步修订 SRS §12 瓦片口径。
> - **坐标 6 位**：用 `Format.coordinate`（`Format.value` 的 digits 上限仍是 3——直接传 6 会静默截成 3 位，P2 开发期实证）。

> **P1 落地要点（CHG-28 摘要，接手先读）**
> - 详情 Tab 顺序已改为 **钓鱼指数→潮流→天气→水质→风浪**（`DetailTab` 枚举序 + `DetailScreen` TabRow + `titlesInTabOrder` **三处联动**，默认落地 Tab = `SCORE`）；改任一必须三处同改并跑 `WaterBodyTabTitlesTest`/`ComposeSmokeRobolectricTest`。
> - 地图为**唯一首页**：无底部 dock，浮动按钮由 `MapScreen.floatingActions` 注入（`:app` 组装）；地图「Top10」落为**进收藏并展开跨点 Top10 Sheet**（`openTop10OnStart`），地图本点 Top10 未做。
> - 地图相机程序驱动动画（冷启动定位/返回定位）必须置 `suppressNextIdle`，否则 idle 回调会把坐标喂给准星状态机（破坏 F-1.4 唯一真源）。
> - 真预取 = `DetailPrefetcher`（前 5 / TTL/2 / `tryAcquire` 不足放弃剩余 / 并发 2 / 离线零请求），触发点在 `MainActivity` 收藏页 `LaunchedEffect`，受「后台预取」开关；Manifest 已加 `ACCESS_NETWORK_STATE`。
> - 收藏 Top10 用 `TopWindowsLruCache`（不落库；**排序切换会清空**——规格口径）；列表摘要/陈旧判定都依赖 `FavoritesUiState.nowEpochSec`。
> - 校潮观测四类落 `manual_observation.kind`（`HIGH/LOW/RISING_START/FALLING_START`，历史 `TIDE_OBSERVATION` 兼容），**无需 Room 迁移**；水质观测读回已按「非校潮 kind」隔离。