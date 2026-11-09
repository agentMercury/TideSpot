# 开发交接文档 · 潮点 TideSpot（HANDOVER）

> 交接日期：2026-11-09 ｜ 交接方：M6 开发会话（AI agent，已完成 M6 全量 + APK 产出 + 真机冒烟通过）
> 接手方：**下一开发会话（AI agent，继续开发）** ｜ 接手起点：**验收反馈迭代 + 已裁定优化池**（§6；无遗留必修阻塞）
> **接手前必读顺序**：本文档 → `CONTRACT.md`（含 M5/M6 子契约）→ SRS 对应章节（F-2/§6.6/§6.2b/§10/§13）→ `PROGRESS_TRACKER.md` → `M5改进意见01/02.md`（口径背景）→ `ACCEPTANCE_PRECHECK.md`（验收执行单）
> （历史交接版归档：`archive/HANDOVER_m3_m4_交接版.md`、`archive/HANDOVER_m4_m5_交接版.md`）

---

## 1. 项目概况

| 项 | 内容 |
|---|---|
| 产品 | 「潮点 TideSpot」Android 纯客户端（无自有服务端）：地图选点 → 5 维逐小时（风浪/天气/钓鱼指数/水质/潮流）×240h + 收藏 + Top10 最佳窗口 |
| 需求基线 | `TIDESPORT_REQUIREMENTS-修改.md`（SRS v1.3；CHG-06~15 均已回写/登记） |
| 交付物 | **可安装 APK（自签名）**，不上架（CHG-02）；目标交付日 **2026-12-08** |
| 排期基线 | `PROJECT_PLAN.md` v1.0：M0–M6 + 交付验收，当前**领先约 16 天** |
| 状态台账 | `PROGRESS_TRACKER.md`（每里程碑退出必更）｜ 变更台账：`REQUIREMENTS_REGISTER.md`（至 **CHG-16**）｜ 数据源结论：`DATA_SOURCE_PROBE.md` ｜ **版本管理**：jj+git colocated（工作区根），基线 tag `m6-exit`=`3c018c9`，修复批次每批一次修订提交 |

## 2. 当前状态（2026-11-09 M6 交接时）

- **进度**：M0–M6 全部完成。M6 = **通过**（批次 0 契约 CHG-16 → 批次 1 数据/算法 → 批次 2 UI → 批次 3 `:app`；四批次串行 + 每批次负责人独立复跑验证）。
- **交付物就绪**：`tidespot-android/app/build/outputs/apk/release/app-release.apk`（49.1MB，`keystore/tidespot.jks` 自签名，apksigner 实证；签名密码在 `local-secrets.properties` 的 `signing.*`，不入库）。
- **基线**：八模块全量 **573 运行 / 0 失败 / 12 跳过**（domain 126 + data 134×2 + ui 34 + detail 45 + favorites 18 + map 31 + settings 21 + app 15；跳过 = Room 真机 @Ignore 先例）。黄金对拍 B1–B7 保持；OPT-05 基准锁 <40ms 命中；红线扫描 main 源码零域名命中。
- **验证命令**（subst 后）：
  ```powershell
  $env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
  D:\workspace\天气鱼情\.tools\gradle-8.9\bin\gradle.bat :app:testDebugUnitTest :app:assembleRelease :core:domain:test :core:data:test :core:ui:testDebugUnitTest :feature:detail:testDebugUnitTest :feature:favorites:testDebugUnitTest :feature:map:testDebugUnitTest :feature:settings:testDebugUnitTest --console=plain -p T:\
  ```
- **M6 负责人偏差裁决（留证，勿重复决策）**：① Copernicus 凭据构建期 BuildConfig 注入（CHG-02 不上架 + CHG-11 单机自用下接受；运行期外部文件读取登记 v1.1）；② F-3.7 DTO 级模型名/网格分辨率透出登记 v1.1；③ 预取开关空操作语义（真预取 v1.1）；④ v3 旧行 `veto_codes='[]'` 读侧不再按文案排除（升级后首刷重算即闭合）。

## 3. 工程结构（`tidespot-android/`）

```
tidespot-android/
├── CONTRACT.md                      # 模块契约 + M3/M4/M5 子契约（M6 沿用"负责人契约→subagent 批次→负责人集成验收"）
├── gradlew / gradle/wrapper/        # wrapper 8.9 入库（沙箱内用 .tools 直跑；本机首次 gradlew 需联网）
├── local-secrets.properties         # Copernicus 凭据（本机专用，不入库不打包；:app 接线时读取）
├── core/domain/                     # ✅ M1+M4+M5：算法核（111 测试）；M5 修 FO-13/14、OPT-06/08、find 尾增 nowEpochSec
│   └── window/BestWindowFinder.kt   # Top10 算法（时间域过滤 + rank + 潮汐去重 + 兜底）；跨点聚合 rankAcrossSpots
├── core/data/                       # ✅ M2/M3/M4/M5：网络+存储+地理+水体（117×2）
│   ├── repo/TideSpotRepository.kt   # D07 patch 合并 / D08 COALESCE / topWindows（读侧收口 + 时间域下一整点）
│   ├── repo/HourlyScoreStore.kt     # resolvePreset（唯一预设解析，CHG-13/D03）+ tz 解析 + vetoReasons NULL 映射
│   ├── repo/Ttl.kt                  # 3h/6h TTL + unusableAgeMillis（48h 不可用，CHG-13/N15）
│   ├── local/                       # Room v4：spot/hourly_metric/hourly_score(MIGRATION_2_3, v4 veto_codes)/fetch_log + 双缓存表 + manual_observation/manual_tide_fit(MIGRATION_3_4)
├── core/ui/                         # ✅ M4：HourChart(null 断点)/DaySelector/FactorBreakdown/ConfidenceBadge/ScoreBands
│   └── titles/WaterBodyTabTitles.kt # canonical Tab 标题 + titlesInTabOrder()（CHG-13/D01 唯一入口）
├── feature/map/                     # ✅ M3：地图选点（:app 已接线；CARTO URL 收敛 TideSpotUrls）
├── feature/favorites/               # ✅ M5+M6：收藏列表/编辑/删除/排序 + FavoritesSaveRules 坐标防御 + SpotZones 时区渲染（占位已移除）
├── feature/settings/                # ✅ M6 新建：SettingsLogic/SettingsStore/SettingsScreen（隐私逐源披露/许可清单/单位与天数）
├── feature/detail/                  # ✅ M4+M5+M6：5 Tab + Top10 Sheet + 现场校潮 Sheet + D04/D10/D11/D12/D14 修复 + TideSpotStrings 接线
└── app/                             # ✅ M6 新建：MainActivity 四页导航（AppNav reducer）+ AppGraph 手工 DI + ConfirmFavoriteDialog 准星收藏主路径 + 自签名打包
```

**后续待建**：无（v1 开发收尾；v1.1 需求池见 §6）。

## 4. 环境须知

1. **JDK 17 / Android SDK / Gradle 8.9**：JDK `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`；SDK `C:\Users\gamer\AppData\Local\Android\Sdk`；Gradle 用 `.tools\gradle-8.9\`（不在 PATH）。
2. **subst T:**（R-09）：`subst T: "D:\workspace\天气鱼情\tidespot-android"` 后在 `T:\` 下构建；**重启后失效，接手先查**（`subst` 无输出即重建）。
3. **沙箱网络选择性放行**：`repo1.maven.org` / `services.gradle.org` **不可达** → **禁止新增任何依赖/插件**（Robolectric/ui-test 不可用，A-14 Compose 冒烟锁等网络可用）。新模块 build 脚本照抄 `:feature:detail`。
4. **Copernicus 凭据**：有效，存 `local-secrets.properties`；禁硬编码；`:app` 接线时读取。
5. **黄金资产**：`archive/m0-m3-assets/`（只归档不删；测试从 classpath `/golden/` 资源加载，与归档无关）；NOAA B7 校潮回归数据在 `core/domain/src/test/resources/`（OPT-08，M5 入库）。

## 5. 已完成工作与关键决策（M5 增量；M0–M4 见 `archive/HANDOVER_m4_m5_交接版.md`）

### M6 交付（2026-11-09，提前 19 天退出；增量见 PROGRESS_TRACKER 本期快照）
- **批次 0 契约（CHG-16）**：Models.kt 只增 `VetoCode`/`FishScoreResult.vetoCodes`/`FavoriteSpot.tzOffsetSec`。
- **批次 1 数据/算法**：OPT-M5-04/FO-01 雷暴判定结构化（主源码 `contains("雷暴")` 清零 + 文案无关锁）；D09 天文三项 detail；FO-15 死代码删除；OPT-19 8 物种预设 JSON；OPT-05/FO-21 <40ms 基准锁；Room v3→v4（`manual_observation`/`manual_tide_fit`/`veto_codes` 列 + `MIGRATION_3_4`）；OPT-M5-02 校潮持久化与应用闭环（`tideCorrection`）；D13（`scoringDispatcher`/取消重抛/双算消除）；`tzOffsetSec` 落库 + D07 patch 扩展；ReverseGeocoder URL 收敛。
- **批次 2 UI**：D04（niceStep 10 负幂 + BAR 0–100 轴）、OPT-M5-05/D10（徽标动态接线）、D11（免责第 5 条 + 常驻入口）、D12（dh/dt 副轴 + 高低潮 + 校正对照）、D14（跨零点自愈/当地小时/色值收敛）、OPT-M5-03（`SpotZones` 时区渲染）、OPT-M5-07 UI 侧收口（`FavoritesSaveRules.canCreate` 防御）、`:feature:settings` 新建、`TideSpotTheme` 明暗、`TideSpotStrings` 文案层首批。
- **批次 3 `:app`**：四页导航（`AppNav` reducer 纯函数 + 8 锁）、手工 DI（Room 全迁移 + 全 DAO 注入）、OPT-M5-07 准星收藏主路径、CHG-12④⑤ 接线、F-3.7 部分透出、N16 逆地理总开关、自签名 keystore + APK。
- **验收修正**：批次 1 首轮 3 个测试失败（agent 自修）；批次 2 收尾 favorites 测试编译错误（`epoch` 助手跨类引用）由负责人修复——组装层零覆盖教训持续有效。

### M5 交付（2026-11-06，历史；详见下文与 archive）
- **开工前置 A-15（客户三项裁决，CHG-14）**：① D07 `upsertFavorite` patch 合并（waterBodyType/elevation/tzOffsetSec/speciesId/lastViewedAt/createdAt，编辑不冲派生列）；② D08 `updateWaterBody` COALESCE（**null=保持原值**）；③ D03 读侧收口（**接 Top10 读取**，不删表）：`Repository.topWindows` + hourly_score 重建 + **恒等对拍锁测试**。
- **算法必修**：FO-13 合并间隔 5400s→3600s；FO-14 跨点按 `rank` 排序；`Output.fallbackBestEpochSec`（兜底含时刻）；OPT-06 `fitManual` ±6h 匹配窗；OPT-08 NOAA B7 回归 + RMSE/留出集断言。
- **`:feature:favorites`**：F-2.1~F-2.4/F-2.6 代码级+单测级（排序/摘要均纯函数 JVM 锁）。
- **`:feature:detail`**：Top10 Sheet（本点现算，与详情页同源恒等）+ 现场校潮 Sheet（观测 Session 级，持久化登记 M6）。
- **Models.kt 只增三处（CHG-14）**：`FavoriteSpot.lastViewedAtEpochSec`、`BestWindow.rank`、`Output.fallbackBestEpochSec`。

### M5 验收后处置（2026-11-08，CHG-15）
- **必修项偏差① 已修（当日闭环，勿重复执行）**：`BestWindowFinder.find` 尾增 `nowEpochSec: Long = 0`（>0 剔除历史小时 + 有效小时数收缩 + 兜底只在未来域）；`TopWindowDerivations.top10` 透传；`DetailStateHolder.onResult` 与 `Repository.topWindows` 均传下一整点；`validHoursPresent` 收紧为未来有效行数；`FavoritesSummaryFormatter.line` 兜底优先于「未加载」。锁测试 +8。
- **意见02 误判已驳回**（校准面板已实现/迁移测试与跨点聚合测试已存在/乱码系验收工具显示问题/时区测试已覆盖）——**勿按其"立即执行"清单操作**。

### 结构性教训（务必带入 M6）
1. **组装层（Screen/Composable）是纯 JVM 测试盲区**：M4 的 D01/D02、M5 的"Sheet 未实现/测试零交付"两次漏网——**新 UI 逻辑一律先抽纯函数再进 Composable**；subagent 报告必须经负责人跑全量验证（M5 两个 agent 均自称完成实际留有编译失败/测试缺失）。
2. **稀疏样本测试注意**：`find` 的 `validHoursPresent` <24 会走禁用分支——构造用例要么 ≥24 行要么显式传参。
3. **契约只增不改**：构造器/函数尾部只增可选参数 + 台账登记（CHG-14 三处先例）。

## 6. 未完成工作（接手任务书，按序执行）

### 交付验收（正式窗口 12-04 ~ 12-08；**冒烟预验已于 2026-11-09 通过**）
> **客户反馈（2026-11-09）**：APK 安装成功，冒烟测试通过；其余问题待使用过程反馈。
> **反馈处理循环（接手会话的任务书）**：用户反馈（截图/现象）→ 负责人按 ACCEPTANCE_PRECHECK §4 格式取证裁定 → 属实项登记台账 → 修复批次（负责人契约 → subagent → 集成验收，同 M6 模式）→ 八模块全量回归 → jj 修订提交 → 用户复测确认。
> **执行单**：`../ACCEPTANCE_PRECHECK.md`（P0/P1/P2 共 25 条；正式验收按同清单复跑留证）。
- **验收设备**：荣耀 Win RT / Android 16 / 无 GMS 单台（CHG-11；F-1.2 GMS 通道代码级+书面豁免）。
- **APK 安装冒烟**：`app-release.apk`（自签名；Android 16 开发者模式 USB 安装步骤注意 edge-to-edge 与未知来源授权）。
- **M5/M6 运行时项（挂此执行）**：F-2.2「30 条收藏重启不丢」+ 首屏 <300ms（CHG-11 单机实测）；跨时区双点同开（F-3.8）；**Room v3→v4 真机迁移**（旧库升级 + `veto_codes` DEFAULT 回填，RoomMigrationTest @Ignore 用例解锁）；校潮观测持久化（`manual_observation`/`manual_tide_fit` 重启不丢）；M3+M4+M5+M6 端到端补验（选点→准星收藏→详情→Top10→校潮 Sheet→断网回看→设置翻转）。
- **§13 验收清单逐项勾选留证**；证据三分法（代码级/单测级/运行时）记录于 PROGRESS_TRACKER。
- **挂起项随验收处置**：A-05（本机正式 gradle 双确认）、A-07（OSTIA 恢复后一条 curl 复测）、A-14（Robolectric Compose 冒烟四条锁，网络可用时补建）。
- **已知偏差知情确认（客户）**：Copernicus 凭据 BuildConfig 注入（v1.1 可改运行期外部文件）；F-3.7 DTO 级元数据 v1.1；预取开关 v1.1 真实现。

### v1.1 需求池（CHG-15③/CHG-16，不入 v1 验收，勿顺手做）
OPT-M5-06（相对时间）/08（观测涨退潮标注+补记）/09（水质实测录入 UI）/10（跨点 Top10 缓存）/11（出发时间筛选）/12（校正状态提示）；意见02 P1/P2（搜索筛选/批量操作/分组/缩略图/提醒/列表级陈旧标记）；全量 i18n 文案外移；F-3.7 DTO 级模型元数据；真预取；Copernicus 运行期外部凭据文件。

### 已知可优化项清单（负责人裁定，2026-11-09；接手会话按此表执行，勿超裁）

**A 类·可直接开工**（前提：不新增依赖、不放宽契约、不改变用户可见行为；走「台账登记 →（涉签名则 CONTRACT 尾增）→ 批次执行 → 八模块全量回归 → jj 修订提交」流程）——**✅ 2026-11-09 全清（CHG-18；含真机迁移锁抓出的 P0 缺陷修复，详见台账）**：

| # | 项 | 说明 | 前置 |
|---|---|---|---|
| ✅ A-1 | BLK-04 RoomDaoTest / RoomMigrationTest v4 真机解锁 | 空壳 6 用例落地 androidTest，真机 6/6（AAK-AN00）；抓出并修复 MIGRATION_3_4 列名 P0 缺陷 | ~~真机 USB 连接~~ 已就绪 |
| ✅ A-2 | `:feature:map` `requestSingleUpdate` 弃用 API 迁移 | `requestLocationUpdates` 单次语义等价迁移（已放行） | ~~台账登记放行范围~~ 已登记 |
| ✅ A-3 | 本地验证脚本 `verify.ps1` | 已落盘并实测全绿（subst 自愈+JAVA_HOME+红线+八模块+assembleRelease+代理注入） | ~~无~~ — |
| ✅ A-4 | `tools/` 204MB 重复目录清理 | 与 `.tools/` 逐目录核实一致后删除 | ~~用户确认~~ 已授权 |
| ✅ A-5 | `settings.gradle.kts`/`gradle.properties` 中文路径决策收尾（A-05） | 本机正式 `gradlew` 首跑验证通过；subst T: 维持标准构建路径 | ~~网络可用~~ 已验证 |

**B 类·需需求环节介入**（用户可见行为变化 / 算法与数据语义变化 / 契约放宽，须客户拍板排期后按 CHG 登记）：

| # | 项 | 裁定理由 |
|---|---|---|
| B-1 | OPT-M5-06/08/09/10/11/12（相对时间/观测标注补记/水质实测录入/跨点 Top10 缓存/出发时间筛选/校正状态提示） | 全部为功能增强；10/11 涉及读取语义与窗口算法，09 涉及 §5.3 数据语义 |
| B-2 | 意见02 P1/P2（搜索筛选/批量操作/分组/缩略图/提醒/列表级陈旧标记） | 功能增强 |
| B-3 | 全量 i18n 文案外移（FO-01 残量） | 用户可见文案全面变动，需范围与语言集裁定 |
| B-4 | F-3.7 DTO 级模型名/网格分辨率元数据透出 | 需扩展 M2 网络层 DTO 与 Repository（CHG-12④a 完整形态） |
| B-5 | 真预取（进详情后台预刷新） | 需 `:feature:detail` API 扩展 + 行为变化 |
| B-6 | Copernicus 凭据改运行期外部文件读取 | 安全加固方向已裁决接受 BuildConfig 方案；改动需重新裁定 |

**C 类·环境阻塞（✅ 2026-11-09 全清，CHG-19：C-1 Robolectric 冒烟锁 8 用例 + C-2 AGP 8.7.3（独立提交可回退）+ C-3 LocalLifecycleOwner 迁移；verify.ps1 已内置代理注入）**：

| # | 项 | 阻塞点 |
|---|---|---|
| C-1 | A-14 Robolectric/ui-test Compose 冒烟四条锁 | 需 maven 拉取 android-all 与 ui-test 依赖 |
| C-2 | FO-25 AGP/compileSdk 升级消除告警 | 需拉取新版 AGP artifact |
| C-3 | `:feature:map` `LocalLifecycleOwner` 迁移 | 需新依赖 `lifecycle-runtime-compose`（违反沙箱禁新依赖） |

### ~~M6 打磨 + `:app` 组装~~（✅ 2026-11-09 完成，见 §2/§5；原清单归档至 PROGRESS_TRACKER 本期快照）
- **~~首批必修~~**：CHG-15 偏差① 已修复 ✅（见 §5）。
- **M5 验收衔接缝顺手修（CHG-15②，成本极低，与下列既有清单合并实施）**：
  - OPT-M5-02 校潮应用闭环：`TideCalibrator.apply` 接 `ManualTideFit.lag` + `manual_observation` 表（Room schema 决策）+ D12 校正前后对照；至少持久化观测与拟合参数。
  - OPT-M5-03 收藏列表/Top10 按选点时区渲染：`FavoriteSpot` 尾增 `tzOffsetSec`（或 spot 表 join），`FavoritesSummaryFormatter`/`FavoritesScreen` 改 `ZoneOffset.ofTotalSeconds`（随 CHG-12④b 注入链顺手修）。
  - OPT-M5-04 vetoReasons 结构化（FO-01/D09：雷暴判定禁 `contains("雷暴")`，用机器码/枚举）。
  - OPT-M5-05 ConfidenceBadge 动态接线（=D10，禁硬编码 ESTIMATED）。
  - OPT-M5-07 地图准星一键收藏主路径：`:app` 导航 + 长按/确认 → 收藏对话框预填逆地理名称 + `lat/lon/waterBody/elevation/tzOffsetSec`（修 `FavoritesViewModel.kt:106` 占位；D07 patch 已保证编辑不冲掉）。
- **M6 原定范围（HANDOVER_m4_m5 §6 继承）**：设置页（单位/天数 7|10 接 `DaySelectorLogic.coerceDaysRange`，N16 开注入点+用例/物种/权重/可选 Key/预取/许可清单/逆地理总开关）、i18n（FO-01）、深色、隐私逐源披露、**`:app` 组装（CHG-12④⑤ + CHG-13 D06：F-3.7 元数据透出、`spot.tzOffsetSec`→ZoneId 注入、`hourlyScoreDao` Room 实例注入、URL 单点收敛复核、`cachedDetail` 不落评分复核）**、签名打包（自签名 APK）、性能基准 + CI 门禁（OPT-05/FO-21：240h×全因子 <40ms JVM 基准）、8 物种预设库 JSON（OPT-19）、死代码消化（FO-15：`fromLakeDaily`/`blendManualObservation`/`displayScore` 等）、AGP/compileSdk 告警（FO-25）、RoomDaoTest 真机/Robolectric（BLK-04）。
- **M4 验收遗留（CHG-13）**：D04（`ChartMath.niceStep` 补 10 负幂 + BAR 固定 0–100 轴）、D05（F-3.7 元数据）、D09（ΠV 逐项乘子 + 月龄/照明/中天三项原始输入）、D11（免责补第 5 条「潮汐校正」+ 常驻 Sheet）、D12（潮 Tab dh/dt 副轴 + 高低潮标记）、D13（评分切 `Dispatchers.Default` + 重抛 CancellationException + 避免双次全量重算）、D14（跨零点「今天」自愈/mapIndexed/x 轴当地小时/硬编码色值/过时 KDoc）、N16/N18/N19/N20、URL 单点 3 处收敛（MapScreen CARTO + ReverseGeocoder 默认值）。
- **v1.1 需求池（CHG-15③，不入 M6，勿顺手做）**：OPT-M5-06（相对时间）/08（观测涨退潮标注+补记）/09（水质实测录入 UI）/10（跨点 Top10 缓存）/11（出发时间筛选）/12（校正状态提示）；意见02 P1/P2（搜索筛选/批量操作/分组/缩略图/提醒/列表级陈旧标记）。

### 交付验收（12-04 ~ 12-08）
真机回归（荣耀 Win RT/Android 16/无 GMS，CHG-11 单机口径；§10.2/§10.3）：**M5 运行时项挂此**——F-2.2「30 条收藏重启不丢」+ 首屏 <300ms、跨时区双点同开、M3+M4+M5 端到端补验（选点→收藏→详情→Top10→断网回看）、§13 清单逐项勾选留证、自签名 APK。

## 7. 不得回退点（新增硬约束加粗）

1. `vetoReasons` 非空 `List<String>` + `isEmpty()` 判定（CHG-08④）。
2. 评分缺测输入安全侧保守（CHG-07①）。
3. 全部外部 URL 只能在 `TideSpotUrls.kt` 拼接（CHG-08②）。
4. 水体判定探针失败判 UNKNOWN 短 TTL（CHG-09，不得改回"失败落 LAND"）。
5. Tab 标题只能取自 `WaterBodyTabTitles.titlesInTabOrder()`（CHG-13/D01）。
6. 预设解析只能走 `HourlyScoreStore.resolvePreset`（CHG-13/D03）。
7. §6.7 48h 不可用语义（`Ttl.unusableAgeMillis`）不得移除。
8. 外层 LazyColumn 内禁止嵌套同向 LazyColumn（CHG-13/D02）。
9. **收藏编辑必须保持 patch 语义**（CHG-14：`upsertFavorite` 合并 waterBodyType/elevation/tzOffsetSec/speciesId/lastViewedAt/createdAt，禁回退 `@Update` 裸覆盖）。
10. **`updateWaterBody` COALESCE 语义**（CHG-14：null=保持原值）。
11. **Top10 时间域**（CHG-15①：`find` 的 `nowEpochSec` 过滤不得移除，生产调用方必须传下一整点；历史窗口不得出现在「未来最佳窗口」）。

## 8. 开发规范（必须遵守）

1. **契约冻结**：`CONTRACT.md` + `Models.kt` 只增不改；单位 SI、缺值=null 禁止 0 填充；构造器/签名变更走"尾部只增可选参数"并登记台账。
2. **红线**（SRS §12）：cnemc/NMEFC/Overpass 域名禁现于代码；Open-Meteo 频控内置不得移除；RG 节流 ≤1 req/2s、`features:[]` 不重试；Copernicus 凭据禁硬编码；Copernicus/NASA/NOAA/USGS 需署名。
3. **每里程碑退出**：更新 `PROGRESS_TRACKER.md`；变更先登记 `REQUIREMENTS_REGISTER.md`；验收证据按三分法留证（代码级/单测级/运行时）。
4. **测试即验收**：JUnit4 + kotlin.test + MockWebServer + runTest；**UI 逻辑先抽纯函数再进 Composable**；禁用分支注意 `validHoursPresent` 语义。
5. **执行模式（客户指定）**：交付负责人定义接口契约 → 可并行任务分 subagent → 负责人集成与验收。**独立复核证明负责人验收环节必要**（M4 D01/D02、M5 语法错误+Sheet 未实现+测试零交付均靠负责人跑全量抓出）。
6. **验证**：沙箱 gradle 直跑（命令见 §2）；遇锁等 60s 重试；**新增依赖前先确认 maven 可达性，否则禁止引入**。

## 9. 待客户/挂起事项

| ID | 事项 | 状态 |
|---|---|---|
| A-05 | 中文路径决策 + 本机正式 `gradle.bat` 双确认 | 待办（沙箱持续稳定；本机首跑 `gradlew` 需联网下载发行包） |
| A-07 | OSTIA 恢复后复测千岛湖掩膜（命令见 DATA_SOURCE_PROBE A6-r4） | 服务器端故障暂缓；CHG-09 UNKNOWN 兜底，不阻塞 |
| A-14 | Robolectric/ui-test Compose 冒烟四条锁（5 Tab 标题矩阵/切表不崩/DaySelector/FactorBreakdown） | 待网络可用（随 A-05 本机环境）；纯函数锁已先行覆盖 |
| M5 运行时项 | F-2.2「30 条收藏重启不丢」+ 首屏 <300ms、跨时区双点、校潮观测持久化 | 挂交付验收（12-04~12-08 真机） |
| OPT-36~64 + OPT-M5-06~12 | 完工后 v1.1 需求池 | APP 完工后启动 |

## 10. 沟通与汇报约定

- 开发会话：只做开发，每完成一个里程碑在 `PROGRESS_TRACKER.md` 留评审记录（含 XML 统计：运行数/失败数/跳过数）。
- 预警规则：预计延期 ≥1 天、外部依赖逾期、契约需变更 → 当日上报。
- 已知捷径：SRS 优先级最高；与 HANDOVER/台账冲突时以 SRS v1.3 + 台账最新记录（CHG-15）为准。
