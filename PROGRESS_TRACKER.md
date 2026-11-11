# 进度跟踪 · 潮点 TideSpot（Progress Tracker）

> 更新节奏：每里程碑退出必更 + 每周一例行更新（下次例行：2026-11-09）
> 排期依据：`PROJECT_PLAN.md` v1.0 ｜ 需求基线：SRS v1.2 ｜ 工程：`tidespot-android/`

## 1. 状态总览

**当前阶段：v1.1 P0 全量完成（P0-1 凭据外部化 + P0-2 Top10/水质实测 真机冒烟通过）→ P1 批次可开工**
**整体进度：7 / 8（v1 交付验收待 12-04~12-08）｜ v1.1：P0-1 ✅ / P0-2 ✅ / P1 待开工 ｜ 健康度：🟢 正常**

| 里程碑 | 计划 | 状态 | 实际完成 | 验证结论 |
|---|---|---|---|---|
| M0 数据源 Spike | 11-02 ~ 11-03 | ✅ | 11-03 | 探针 9 PASS / 3 FAIL（均有降级决定），`DATA_SOURCE_PROBE.md`；M3.0 复测：Copernicus 凭据 PASS（A6-r1）、GLACIO 退役确认（A6-r2） |
| M1 算法核 | 11-04 ~ 11-09 | ✅ | 11-03（提前 6 天） | 64 测试全绿；与 fishscore.mjs 逐小时最大差 0 分（标准 ≤2）；B1–B7 黄金值命中 |
| M2 网络+存储 | 11-10 ~ 11-15 | ✅ | 11-03（提前 7 天） | 42 测试×2 variant：84 运行 0 失败 3 跳过/variant（Room DAO 留真机）；6 态 Mock 全过 |
| M3 地图与选点 | 11-10 ~ 11-15 | ✅ | 11-05（提前 5 天） | 全量 **260 运行 / 0 失败 / 8 跳过**（domain 74 + data 80×2 variant + map 26；跳过=Room 真机项）；红线扫描零命中；**F-1.1~F-1.5/F-1.7 部分闭环；F-1.6 未接线、F-1.4 拖动场景待修（CHG-09 修复中）**；集成验证 M6 补验；CHG-06/07/08/09 合入基线 |
| M4 详情 5 Tab | 11-16 ~ 11-21 | ⚠️ **有条件通过**（CHG-13） | 11-06（提前 10 天） | 五模块全量 **391 运行 / 0 失败 / 10 跳过**（domain 93 + data 113×2 + ui 27 + detail 14 + map 31；跳过=Room 真机项）；验收口径（CHG-13 更正）：**F-3.3/F-3.6 通过；F-3.1/3.2/3.4/3.8 部分达标；F-3.7 不达标转 M6；F-3.5 属 M5 计划内**；附加必修 1–6/8 全过；P0×3（D01/D02/D03）+ N15 已修复并锁测试；D07/D08 转 M5、D04~D14/N16~N20 转 M6 |
| M5 收藏 + Top10 | 11-22 ~ 11-27 | ✅ 通过 | 11-06（提前 16 天） | 六模块全量 **446 运行 / 0 失败 / 10 跳过**（domain 107 + data 116×2 + ui 27 + detail 38 + favorites 11 + map 31；跳过=Room 真机项）；A-15 前置 D07/D08/D03 全修+锁测试；FO-13/FO-14/OPT-06/OPT-08 并入必修全过；F-2.1~F-2.4/F-2.6 代码级+单测级通过，F-2.2 性能与 F-2.5 真机项挂交付验收；CHG-14 登记 |
| M6 打磨 | 11-28 ~ 12-03 | ✅ 通过 | 11-09（提前 19 天） | 八模块全量 **573 运行 / 0 失败 / 12 跳过**（domain 126 + data 134×2 + ui 34 + detail 45 + favorites 18 + map 31 + settings 21 + app 15；跳过=Room 真机 @Ignore 先例）；批次 0 契约（CHG-16）→ 批次 1 数据/算法（domain+data 394/0/12）→ 批次 2 UI → 批次 3 `:app`，每批次负责人独立复跑验证；**可安装 APK 产出**（app-release.apk 49.1MB，tidespot.jks 自签名，apksigner 实证）；红线扫描 main 源码零域名命中；F-3.7 元数据部分透出（DTO 级模型名/网格分辨率登记 v1.1）；A-14 Robolectric/BLK-04 挂网络与真机 |
| 交付验收 | 12-04 ~ 12-08 | ⬜ 未开始 | — | |
| V1.1 优化批次 A（验收前优化） | 11-09 | ✅ | 11-09 | 八模块 **561 运行 / 0 失败 / 0 跳过**（data 128×2，删 6 条 @Ignore 空壳后跳过清零）+ **真机仪器测试 6/6（AAK-AN00/Android 16，BLK-04 解锁）** + app-release.apk 复产出 + verify.ps1 全绿；**真机迁移锁抓出并修复 P0：MIGRATION_3_4 列名 veto_codes≠实体 vetoCodes，v3→v4 升级路径必崩**（CHG-18②）；A-2 弃用 API 迁移、A-3 verify.ps1、A-4 tools/ 清理、A-5 gradlew 首跑全清；CHG-18 登记 |
| V1.1 优化批次 C（环境解锁） | 11-09 | ✅ | 11-09 | 客户裁定三项全做（CHG-19）：**C-1 Robolectric Compose 冒烟四条锁落地**（:core:ui +robolectric 4.14.1 + ui-test，`ComposeSmokeRobolectricTest` 8 用例：Tab 标题矩阵四水体/切表 10 轮不崩/DaySelector 回调/FactorBreakdown 三区 + 同屏唯一附锁；test worker 代理透传解锁 android-all 自下载）；**C-3** LocalLifecycleOwner 迁 lifecycle-runtime-compose（FO-25 残留清零）；**C-2** AGP 8.5.2→8.7.3（独立提交可回退，全模块重构建 + lintVital 全跑通过）；八模块 **569 运行 / 0 失败 / 0 跳过**（ui +8）+ APK 复产出（49.1MB）；jj：C-1/C-3=`kkvmrokw`、C-2=`zyynnzws` |

## 2. 本期快照（2026-11-10，v1.1 P0-1 退出）

- **完成项**（CHG-25：客户提前启动 v1.1 + 三项裁定 D13–D15）：
  - **版本号**：`versionName=1.1` / `versionCode=2` 开工即升；APK `潮点TideSpot-v1.1.apk` 与 v1.0 在 E:\Trans 共存。
  - **OPT-V11-01 凭据外部化（批次 A）**：`:core:data` 新增 `CopernicusCredentialStore`/`FileCopernicusCredentialStore`（java.io 原子写，JVM 可测）+ `CopernicusCredentialValidator`（四态 Missing/Valid/Invalid/Unreachable，4xx→Invalid、429/5xx/网络→Unreachable）；`WaterBodyClassifier.setProbe` 尾增（@Volatile 运行期切换）；`:app` 移除 BuildConfig 凭据注入、AppGraph 运行期读凭据、`checkAndApply` 校验通过才落库并即时生效；Manifest `allowBackup=false`。锁测试 12 条（store 5 + validator 7）×2 variant。
  - **设置页凭据版块（批次 B）**：`CopernicusCredentialSection`（账号/密码/可见性切换/校验并保存/清除/四态反馈/隐私说明/免费注册链接经 TideSpotUrls 单点）；`CopernicusFormLogic` 纯函数（错误码枚举）×5 锁测试；`SettingsScreen` 尾增 `credentialSection` slot（null 零行为漂移）。
  - **首次引导卡（批次 B）**：`InlandCredentialGuideCard`（内陆点+水质Tab+未配置+未跳过；可跳过一次，标记持久化）；`DetailScreen` 尾增 `credentialGuideSlot` slot。
  - **i18n 框架（批次 C）**：`:core:ui/res/values/strings.xml`（中文默认）+ `values-en/strings.xml`（英文初稿）——17 条 GAP-05 定稿文案 + P0-1 新增串（凭据版块/引导卡/表单错误）；规范：新增可见文案必须进资源，禁 Screen 硬编码/新增 object 常量；存量 TideSpotStrings P2 外移（零行为漂移）。
  - **APP 图标（批次 C/GAP-01）**：design-handoff SVG → `ic_launcher_foreground/background/monochrome`（vector+gradient）+ adaptive `mipmap-anydpi-v26/ic_launcher(.round)`；Manifest icon/roundIcon 替换 `sym_def_app_icon`。
- **验证留证**：八模块 **598 运行 / 0 失败 / 0 跳过**（v1.0 基线 569 + 29 新增锁）+ verify.ps1 全绿 + **APK 解包 dex 零凭据字段**（`COPERNICUS_USERNAME`/`copernicus.username` findstr 零命中）+ aapt 实证 `versionCode=2/versionName=1.1` + adaptive icon 生效。
- **真机冒烟（2026-11-10，AAK-AN00/Android 16）**：✅ **自动验证项全过**——`adb install -r` 覆盖安装成功（versionCode 2>1，v1.0 数据留存）；启动 MainActivity 前台无崩溃；`dumpsys package` 实证 `versionName=1.1/versionCode=2`；设置页凭据版块渲染正确（「数据源凭据 · Copernicus」+「当前未配置：内陆点水质按模型估算显示（估算徽标）…」+ 用户名/密码/校验并保存/清除凭据/显示切换全可见）；地图页/底部导航正常；crash buffer 无潮点记录（唯一记录为 10-26 其他应用旧日志）。<br>⏳ **待用户手工确认**（需真人交互与自有 Copernicus 账号，adb 自动化与用户用机冲突已停止；CHG-26 已修正徽标口径）：① 内陆点详情 → 水质 Tab 引导卡显示且「暂不配置」跳过后不再出现；② 设置页填写有效凭据 → 提示「校验通过…」→ **水体判定点亮**（内陆点标题「湖面风与水温」/「无潮汐」），水质徽标**维持「估算」并附旁注**「模型估算 · Copernicus 卫星水质数据未接入」（卫星水质数据未接入，CHG-26 客户裁定仅修文案）；③ 填写错误密码 → 提示「凭据无效…」且不落库；④ 清除凭据 → 提示已清除且重启后不残留。<br>✅ **用户反馈：冒烟通过（2026-11-10）**，P0-1 关闭。
- **下一步**：~~P0-2 待真机手工确认后开工~~ → **P0-2 已完成并冒烟通过**（见 §2b）；下一批次 **P1**（OPT-V11-05/06/07/08/09/10/11 + UX 18~24），交接见 `HANDOVER_V11.md`。

---

## 2b. P0-2 第一批（2026-11-10，已合入）

- **完成项**（CHG-27）：
  - **OPT-V11-26**：候选阈值 `CANDIDATE_MIN 60→75`（单点常量，现算/读侧共用）+ 兜底文案 ≥75 + **爆护徽标**（`peakScore ≥80`，`ScoreBand.EXPLOSIVE` 档位色）。
  - **OPT-V11-25**：**核心段派生**（峰值 ±2h 夹原窗内，跨度 ≤4h）；`BestWindow` 尾增 core 三字段（只增）；rank 改用核心段（**系数不变**；原窗 ≤4h 恒等旧公式）；去重中点仍用原窗；UI「利好持续至 HH:mm」（null 不显示）；`durationHours` = 核心段小时数（口径见台账）。
  - **OPT-V11-03**：`find` 尾增时段参数（0/0 恒等锁）；仅输出过滤（原窗相交）；`Top10TimeFilter` 纯函数（全部/24h/本周末/自选）；Top10 Sheet chips + 自选对话框；空结果兜底含「所选时段」+时段内最高分。
  - **OPT-V11-04**：`SourceMetaRegistry` 静态映射表 → SourceFooter「模型 · 分辨率 · 粒度 · 本机拉取 HH:mm」（禁编造源更新时间）。
  - **OPT-V11-02 数据层**：Room **v4→v5**（`manual_observation.kind/value`）+ 迁移锁；`forSpotKind` 隔离校潮/水质观测；Repository 只增 API（`addWaterQualityObservation`/`waterQualityObservations`）。
- **验证**：八模块 **625 运行 / 0 失败 / 0 跳过**（domain +6、data +v4→v5 4、detail +14）+ verify.ps1 全绿 + APK v1.1 重建（49.2MB，14:28）。
- **OPT-V11-02 UI 已随本批完成**：`WaterQualityEntryLogic`（空=跳过 / 非数字 / 超范围 −5~45℃·0~20·0~1000 错误码）+ `WaterQualityEntrySheet`（三项可选填、时刻 ±24h、离线落库、已录入列表）；水温 ±6h 融合 `0.6·实测+0.4·模型`，该小时徽标显示 `MEASURED`（设计冻结六枚徽标，不新增第七枚）。
- **剩余**：~~本批真机冒烟~~ → ✅ **已完成（2026-11-10 真机冒烟通过）**：v4→v5 升级不丢数据（收藏保留）→ 内陆点标题「湖面风与水温」→ F-3.7 行「ECMWF WAM / GFS Wave · 9 / 16 km · 逐小时 · 本机拉取」→ 源更新「—」→ Top10 chips 四枚 + **爆护徽标** + 时长 4 小时（核心段）→ 24h 筛空态「所选时段无 ≥75 分窗口，时段内最高 71 分（11月10日 18:00）」→ 水质录入保存成功 + **徽标转「实测」** + 摘要温度随融合变化 + 重装后记录仍在。<br>**冒烟修复 2 缺陷**：① ModalBottomSheet 半展开/手势条区域致贴底按钮不可点（=GAP-03 insets）→ `skipPartiallyExpanded=true` + 操作按钮置顶 + `navigationBarsPadding`；② 徽标取当日首小时（00:00 超 ±6h）致融合后仍「估算」→ 当日任一小时 MEASURED 即显示实测。回归复跑 **625/0/0** + verify 全绿；APK 重建。

---

### 上期快照（2026-11-09，V1.1 优化批次 A+C 退出，历史存档）

- **完成项**（HANDOVER §6 A 类清单 A-1~A-5 全清，逐项详见台账 CHG-18）：
  - **A-1 BLK-04 解锁**：`:core:data` androidTest 基建（androidx.test 三件套 + room-testing；依赖经 maven 可达性确认后引入，runner 经本机代理下载）；`RoomDaoTest`/`RoomMigrationTest` @Ignore 空壳 6 用例落地为真机仪器测试（in-memory CRUD/REPLACE/lastSuccess + MigrationTestHelper 三段迁移强校验 + 文件库「重启不丢」）；**真机 6/6 通过**。
  - **P0 缺陷修复（A-1 抓出，当日闭环）**：`MIGRATION_3_4` ALTER 列名 `veto_codes` → `vetoCodes`（v3→v4 升级路径 schema 校验崩溃缺陷）；`RoomMigration34JvmTest` 增「ALTER 列名与实体属性名逐字一致」锁。
  - **A-2**：`LocationController.kt` 弃用 `requestSingleUpdate` → `requestLocationUpdates`（首回调 removeUpdates，单次语义不变；`:feature:map` 禁改约束单点放行）。
  - **A-3**：`verify.ps1` 一键验证脚本（subst 自愈 + JAVA_HOME + 红线扫描 URL 口径 + 八模块 + assembleRelease + 代理注入），本机实测全绿（OPT-05 CI 门禁本地形态）。
  - **A-4**：`tools/` 204MB 重复目录删除（用户确认授权；与 `.tools` 逐目录核实一致）。
  - **A-5**：本机正式 `gradlew` 首跑验证通过（发行包下载 + domain 测试全绿）；subst T: 维持标准构建路径（R-09 口径不变）。
  - **V1.1 优化批次 C（同日续批，客户裁定三项全做，CHG-19）**：
    - **C-1（=A-14 解锁）**：`:core:ui` 引 robolectric 4.14.1 + ui-test（compose-bom）；`ComposeSmokeRobolectricTest` 8 用例（5 Tab 标题矩阵四水体类型/切表 10 轮不崩/DaySelector 回调/FactorBreakdown 三区/同屏唯一附锁）；test worker 代理+信任库系统属性透传（Robolectric 自下载 android-all 独立 JVM）。
    - **C-3**：`MapScreen` LocalLifecycleOwner → `androidx.lifecycle.compose`（FO-25 残留清零；:feature:map 单点放行）。
    - **C-2**：AGP 8.5.2→8.7.3（tested-SDK 覆盖 compileSdk 35；Gradle 8.9 不动；独立提交 `zyynnzws` 便于回退；首全量重构建 22m43s）。
    - 口径留证：LazyRow 视口裁剪下 Robolectric 触摸注入不驱动滚动（环境限制），远端 chip 断言收敛为视口内；物理中文路径 D:\ test worker 类加载不可靠再证（R-09）。
- **进行中**：无。
- **阻塞项**：无阻塞。挂起项更新：
  1. **A-05**：本机正式 gradle 双确认——**本机 gradlew 首跑已验证（11-09）**，subst 口径维持；正式收官挂客户确认。
  2. **A-07**：OSTIA 恢复后复测（不阻塞，CHG-09 兜底）。
  3. **A-14/C-1**：Robolectric Compose 冒烟四条锁——✅ **已完成（11-09，CHG-19/C-1）**：Robolectric 形态 8 用例落地 :core:ui（真机形态另行覆盖）；C-2（AGP 8.7.3）/C-3（lifecycle-runtime-compose）同批完成。
  4. **交付验收运行时项（12-04~12-08 真机）**：F-2.2、首屏 <300ms、跨时区双点、校潮观测持久化真机、APK 安装冒烟 + §13 清单逐项留证——Room v3→v4 真机迁移已由本批次仪器测试先行覆盖（6/6）。
- **下一步**：交付验收（12-04~12-08）；C 类解锁批次（A-14/C-2/C-3）按需开工；v1.1 P0-1 凭据外部化挂正式验收后（OPT_REQUIREMENTS_V11 §2）。

---

### 上期快照（2026-11-09，M6 退出，历史存档）

- **完成项**（四批次串行执行，每批次负责人独立复跑验证；契约见 CONTRACT「M6 子契约」/台账 CHG-16）：
  - **批次 0（负责人契约）**：Models.kt 只增 `VetoCode`（THUNDERSTORM/FISHABILITY_DECAY）/`FishScoreResult.vetoCodes`/`FavoriteSpot.tzOffsetSec`；M6 子契约 20 项任务书落盘。
  - **批次 1（数据/算法）**：① OPT-M5-04/FO-01 雷暴判定结构化——`BestWindowFinder` 筛选改 `vetoCodes.contains(THUNDERSTORM)`，主源码 `contains("雷暴")` 清零，文案无关性锁测试；② D09 月龄/照明/中天三项原始输入进 FactorScore.detail（黄金对拍 B1–B7 保持）；③ FO-15 死代码删除（`blendManualObservation`/`fromLakeDaily`/`displayScore` 全仓零引用留证）；④ OPT-19 `species_presets.json` 8 物种 + `SpeciesPresets.load()/byId()`（零新依赖）；⑤ OPT-05/FO-21 基准锁测试（240h×全因子 3 次中位 <40ms）；⑥ Room v3→v4（`manual_observation`+`manual_tide_fit` 两新表 + `hourly_score.veto_codes` 列 + `MIGRATION_3_4` + JVM 迁移锁）；⑦ OPT-M5-02 校潮持久化与应用闭环（四只增 API + `applyManualTideCorrection` + `SpotDetailResult.tideCorrection`，落库即校正后分数无双口径）；⑧ D13（`scoringDispatcher` 注入 + CancellationException 重抛 + 双次全量重算消除）；⑨ `tzOffsetSec` 落库映射 + D07 patch 合并扩展；⑩ ReverseGeocoder URL 单点收敛。domain 126/0/0 + data 134×2/0/6（负责人复跑 394/0/12）。
  - **批次 2（UI）**：① D04 `niceStep` 补 10 负幂 + `barYAxis` 固定 0–100 轴；② OPT-M5-05/D10 徽标动态接线（`ScoreBadgeDerivations` + `fromConfidence`，禁硬编码 ESTIMATED 已修 `DetailScreen:198`）；③ D11 免责第 5 条「潮汐校正」+ 常驻入口；④ D12 dh/dt 副轴 + 高低潮标记 + 校正前后对照纯函数；⑤ D14 跨零点「今天」自愈/`localHours` 当地小时注入/色值收敛；⑥ OPT-M5-03 `SpotZones` 单源时区渲染（JST/UTC 同列表各归其区锁测试）；⑦ OPT-M5-07 UI 侧收口（`saveFavorite` 尾增 lat/lon + `FavoritesSaveRules.canCreate` 0.0/0.0 防御 + 占位移除）；⑧ `:feature:settings` 新建（SettingsLogic/SettingsStore/SettingsScreen + 隐私逐源披露 + 许可清单）；⑨ `TideSpotTheme` 明暗 token；⑩ `TideSpotStrings` 文案层首批（零行为漂移）。ui 34 + detail 45 + favorites 18 + settings 21 全绿。
  - **批次 3（负责人 `:app`）**：导航四页（`AppNav` 纯函数 reducer + 8 锁，不变量 `DETAIL ⇔ detailSpot!=null`）；手工 DI（Room MIGRATION_1_2/2_3/3_4 全量 DAO 注入 Repository）；**OPT-M5-07 准星一键收藏主路径**（确认选点 → `ConfirmFavoriteDialog` 预填逆地理名 → `canCreate` 防御 → 真实 lat/lon 落库 → `updateSpotWaterBody` 回写）；CHG-12④b ZoneId 注入；CHG-12⑤ CARTO URL 收敛（TideSpotUrls 尾增 1 const + MapScreen 单行）；F-3.7 部分透出（模型署名 + 本机拉取时刻）；N16 逆地理总开关接线；**自签名 APK 产出**（`tidespot.jks` RSA2048/10950d，`app-release.apk` 49.1MB，apksigner 实证）。app 15/0/0。
- **进行中**：无（交付验收可提前真机预验）。
- **阻塞项**：无阻塞。挂起项：
  1. **A-05**：本机正式 gradle 双确认（沙箱持续稳定；`gradlew` 首跑需联网）。
  2. **A-07**：OSTIA 恢复后复测千岛湖掩膜（CHG-09 UNKNOWN 兜底，不阻塞）。
  3. **A-14**：Robolectric/ui-test Compose 冒烟四条锁（待网络可用；纯函数锁已覆盖）。
  4. **交付验收运行时项（12-04~12-08 真机）**：F-2.2「30 条收藏重启不丢」、首屏 <300ms、跨时区双点同开、Room v3→v4 真机迁移（旧库升级 + veto_codes DEFAULT 回填）、校潮观测持久化真机、APK 安装冒烟 + §13 清单逐项留证。
- **已知偏差（负责人裁决留证）**：Copernicus 凭据构建期 BuildConfig 注入（CHG-02 不上架 + CHG-11 单机自用口径下接受；运行期外部文件读取登记 v1.1 备选）；F-3.7 DTO 级模型名/网格分辨率透出登记 v1.1；预取开关为空操作语义（真预取登记 v1.1）；旧库 v3 行 `veto_codes='[]'` 读侧不再按文案排除（升级后首刷重算即闭合，:app 首刷路径天然覆盖）。
- **下一步**：交付验收（真机回归 §10.2/§10.3 + §13 清单留证）；M6 快照历史见下节。

---

### 历史快照（2026-11-06，M5 退出，存档）

- **完成项**：
  - **M5 开工前置 A-15（CHG-13 转 M5 三项，客户三项裁决后实施，CHG-14 登记）**：① D07 `upsertFavorite` patch 语义（先读旧实体合并 waterBodyType/elevation/tzOffsetSec/speciesId/lastViewedAt/createdAt 后 @Update，编辑不再冲掉派生列；集成验收补全 waterBodyType——首轮实现遗漏致编辑回退 LAND）+ 往返锁测试；② D08 `updateWaterBody` 改 COALESCE（客户裁决 null=保持原值）+ KDoc 统一 + Fake/Room 双侧锁测试；③ D03 读侧收口（客户裁决接 Top10 读取）：`Repository.topWindows` 新增，hourly_score 重建 FishScoreResult → find，**恒等对拍锁测试**（读侧重建 find 与现算 find 输出逐字段一致，满足「Top10 与详情页/库内恒等」退出标准）。
  - **M5 算法并入必修（CHG-07 池）**：FO-13 合并间隔 5400s→3600s（SRS §6.6 步骤 2）+ 非整点时区边界单测；FO-14 `rankAcrossSpots` 按 rank 全局降序（原 peakScore）；`Output.fallbackBestEpochSec` 尾增（兜底文案含「（时刻…）」，SRS §6.6 边界）；OPT-06 `fitManual` 极值匹配 ±6h 窗（失配观测不计中位数、有效 <2 条不启用）；OPT-08 NOAA 附录 B7 序列归档 test 资源 + RMSE/留出集回归断言。
  - **M5 `:feature:favorites` 新建（F-2.1~F-2.4/F-2.6 代码级+单测级）**：收藏列表/编辑/删除二次确认（全离线可用）、三序排序纯函数 `FavoritesSortLogic`（名称/最近查看/分数，`FavoriteSpot` 尾增 `lastViewedAtEpochSec`）、F-2.5 摘要纯函数 `FavoritesSummaryFormatter`、F-2.6 跨点 Top10（各点 `topWindows` 无网读取 → `rankAcrossSpots` 全局 rank 降序）。
  - **M5 `:feature:detail` Top10 + 现场校潮接线（F-3.5）**：本点 Top10 派生层现算（与详情页 scores 同源恒等；dh=潮位一阶差分纯函数，缺测跳过不插值）、Top10 Sheet（起始/峰值时刻、时长、峰值分+档位、Top2 主因、注意事项、兜底文案含时刻；`Top10Formatter`/`TopWindowDerivations` 纯函数 + 24 条 JVM 锁）、现场校潮 Sheet（观测记录 Session 级 → `fitManual` ≥2 条启用，UI 只透传；持久化登记 M6）。UI 批次集成裁决：Sheet 组装层与 favorites/detail 测试由负责人补齐（UI Agent 未交付，重演 M4「组装层零覆盖」教训，CHG-14⑥）。
  - **M4（历史）附加必修 1–6/8（CHG-07 登记）**：① 六态 Mock 对齐 SRS §10.2；② 黄金对拍走生产管线（B3–B6 ±2 保持）；③ 刷新状态机三修（FO-17）；④ 时区 ZoneId 化（FO-18）；⑤ rain24h 接线（FO-15）；⑥ MockFixtures 移出 main 源集（FO-26）；⑧ `hourly_score` 表落地（Room v2→v3 `MIGRATION_2_3`，CHG-08④）。
  - **M4（历史）详情 5 Tab**：新建 `:core:ui`（ChartMath/HourChart/DaySelector/FactorBreakdown/ConfidenceBadge/ScoreBands/WaterBodyTabTitles canonical）+ `:feature:detail`（5 Tab + 数值表 + 降级卡 + 来源底栏 + TalkBack）。
  - **M4（历史）必修 7 工程化**：gradle wrapper 8.9 入库；根目录黄金资产/探针文件归档 `archive/`；根 `.gitignore` 缺陷修正（CHG-12）。
- **进行中**：无（M6 可立即开工）。
- **阻塞项**：无阻塞。挂起项：
  1. **A-07**：OSTIA 千岛湖掩膜复测（服务器端故障暂缓；CHG-09 UNKNOWN 兜底，不阻塞）。
  2. **A-05**：本机正式 gradle 双确认（沙箱持续稳定）。
  3. **A-14**：Robolectric Compose 冒烟四条锁（待网络可用；纯函数锁已覆盖 D01/D03/D07/D08 语义）。
  4. **M5 运行时验收项（挂交付验收 12-04~12-08）**：F-2.2「30 条收藏重启不丢」（Room DAO 真机项）与首屏 <300ms（单机实测）；现场校潮观测记录持久化（schema 决策随 M6）。
  5. **M6 `:app` 接线清单（CHG-12④⑤）**：F-3.7 来源元数据透出、F-3.8 `spot.tzOffsetSec` → ZoneId 注入、`hourlyScoreDao` Room 实例注入、URL 单点收敛复核、`cachedDetail` 不落评分复核。
- **下一步**：派发 M6 打磨 + `:app` 组装（HANDOVER §6 M6 清单：设置页/i18n/`:app` 接线/签名打包/性能基准 + D04~D14/N16~N20 遗留批次）。

## 3. 待办行动项

| ID | 行动 | 责任人 | 截止 | 状态 |
|---|---|---|---|---|
| A-01 | ~~提供/确认 Copernicus Marine 账号（GAP-02，R-01）~~ | 客户 | 11-03 | ✅ **2026-11-05 凭据到位，token 探针 PASS，GAP-02 关闭** |
| A-02 | ~~执行附录 A 探针~~ | 交付负责人 | 11-03 | ✅ 完成 |
| A-03 | ~~确认真机清单（含无 GMS 中档机，R-02/R-07）~~ | 客户 | 11-10 | ✅ **关闭（11-05，CHG-11）**：验收设备=荣耀 Win RT / Android 16 / 无 GMS 单台（开发者模式 USB 可接）；F-1.2 GMS 通道代码级+书面豁免；性能以该机实测为准 |
| A-04 | ~~冻结 M2/M3 数据层接口契约~~ | 交付负责人 | 11-09 | ✅ 完成（CONTRACT.md + M3 子契约） |
| A-05 | 中文路径决策（subst/迁移/CI）+ 在可用环境跑一次正式 `gradle.bat test` | 客户 + 交付负责人 | 11-09 | 部分缓解：沙箱内 Gradle 本期可正常运行；本机双确认仍待办 |
| A-06 | ~~派发 M3 地图与选点~~ | 交付负责人 | 11-04 | ✅ 完成（11-05 退出） |
| A-07 | **新增**：OSTIA 图层恢复后复测千岛湖掩膜（一条 curl，见 DATA_SOURCE_PROBE A6-r4） | 交付负责人 | 11-09（M3.4 前置） | 待办（服务器端故障，非阻塞） |
| A-08 | ~~派发 M4 详情 5 Tab~~ | 交付负责人 | 11-06 | ✅ **完成（11-06）**：双 subagent 并行（数据/UI）+ 负责人契约（CONTRACT.md M4 子契约）与集成验收；五模块 377/0/10；附加必修 1–6/8 全过；CHG-12 登记 |
| A-09 | **新增**：P1 缺陷修复批次（CHG-07）：vetoReasons null 语义 / ΔP3 缺测降权 / VetoEvaluator 缺测保守 / N-4 异常兜底；M4 附加必修清单并入 HANDOVER §5 | 交付负责人 | 11-06 | ✅ **完成（11-05）**：258 运行 0 失败 8 跳过；黄金对拍保持 ≤2；负责人验收修正 subagent 3 处错误（测试断言×2 + 误删 import） |
| A-10 | ~~规格冲突裁决 4 项~~ | 客户 | 11-09 | ✅ **已裁决（11-05）→ CHG-08 定案并回写 SRS、代码落地**（①Tw=谐波 ②维持 OkHttp+TideSpotUrls 单点收敛 ③§10.1-4 新规格 ④vetoReasons 回退非空） |
| A-11 | **新增**：M5/M6 任务书并入改进池项（跨点 rank/±6h 窗口/NOAA 回归台账/物种库/性能基准/i18n 雷暴隐患/MockFixtures 移位等，见 HANDOVER §5） | 交付负责人 | 随里程碑派发 | **部分完成**：MockFixtures 移位（FO-26）、hourly_score 落库映射（CHG-08④）已随 M4 完成；其余随 M5/M6 |
| A-12 | **新增**：M6 `:app` 接线清单执行（CHG-12④⑤：来源元数据透出 / `spot.tzOffsetSec`→ZoneId 注入 / `hourlyScoreDao` 注入 / `ReverseGeocoder` URL 单点收敛复核 / `cachedDetail` 不落评分复核） | 交付负责人 | 随 M6 派发 | ✅ **完成（11-09，随 M6 退出）**：批次 1 收敛 ReverseGeocoder 默认值 + 批次 3 收敛 CARTO（TideSpotUrls 单点）；`tideCorrection`/ZoneId 注入/全 DAO 注入落地；F-3.7 部分透出（DTO 级登记 v1.1）；`cachedDetail` 零评分计算（批次 1 D13 锁测试固化） |
| A-13 | **新增**：M4 独立验收（第四/五轮）P0 缺陷修复：D01/D02/D03 + N15；台账口径更正；登记 CHG-13 | 交付负责人 | 11-07（M5 派发前） | ✅ **完成（11-06）**：四项全修 + 10 条锁测试；391/0/10；详见 CHG-13 |
| A-14 | **新增**：Robolectric + ui-test Compose 冒烟四条锁（5 Tab 标题矩阵 / 切表 10 次不崩 / DaySelector 24 行 / FactorBreakdown 可见；OPT-45/54）——原沙箱 maven 不可达 | 交付负责人 | 网络可用时（随 A-05 本机环境） | **环境已解锁（11-09：本机网络经代理可用）→ 转下一批次开工**（纯函数锁已先行覆盖 D01/D03/N15） |
| A-16 | **新增**：真机冒烟预验（ACCEPTANCE_PRECHECK 执行单） | 客户 | 12-04 前 | ✅ **通过（2026-11-09）**：APK 安装成功 + 冒烟测试通过；其余问题待使用过程反馈（反馈处理循环见 HANDOVER §6） |
| A-17 | **新增**：V1.1 优化批次 A（HANDOVER §6 A 类 A-1~A-5）：A-1 BLK-04 真机解锁 + P0 迁移缺陷修复、A-2 弃用 API 迁移、A-3 verify.ps1、A-4 tools/ 清理、A-5 gradlew 首跑 | 交付负责人（接手会话） | 11-09 | ✅ **完成（11-09）**：全清 + 八模块 561/0/0 + 真机仪器 6/6 + verify.ps1 全绿；CHG-18 登记；C 类三项（A-14/C-2/C-3）环境解锁转下批 |
| A-15 | **新增**：M5 开发内必修——D07（收藏编辑 patch 语义，防 `@Update` 冲掉派生字段）+ D08（`updateWaterBody` null 语义 + KDoc 统一），**必须先于收藏编辑功能**；D03 读侧收口（`HourlyScoreDao.forSpot` 接 Top10 或删表） | 交付负责人 | 随 M5 派发 | ✅ **完成（11-06，随 M5 退出）**：D07 patch 合并（集成验收补全 waterBodyType）+ D08 COALESCE（客户裁决 null=保持原值）+ D03 接 Top10 读取（恒等对拍锁测试）；详见 CHG-14 |

## 4. 里程碑评审记录

| 日期 | 里程碑 | 结论 | 记录 |
|---|---|---|---|
| 2026-11-03 | M0 | ✅ 通过 | `DATA_SOURCE_PROBE.md`；A6/A7 FAIL→兜底、A9 FAIL→判定收缩、A11b FAIL→Photon 单源 |
| 2026-11-03 | M1 | ✅ 通过（有 2 项已声明偏差） | 对拍最大差 0 分；青岛 11-04 阵风序列形状偏差（机制断言覆盖）；日综合 57/57 等依赖录制时刻预报值，以机制断言替代；详见 GoldenParityTest 注释 |
| 2026-11-03 | M2 | ✅ 通过（3 @Ignore） | Room DAO 留真机验证（JVM 等价覆盖已闭环）；B9 五种错误文案识别全过 |
| 2026-11-05 | M3 | ✅ 通过（真机项挂 A-03） | 242 运行 / 0 失败 / 8 跳过（双 variant）；B7b 逆地理黄金值命中（湖心/青岛繁简混排）；307 跟随、`features:[]` 不重试、2s 节流、缓存命中 0 请求、全链失败落 RG-D 全过；权限无死循环、准星 F-1.4 语义、信息条永不空白断言全过；红线扫描（overpass/cnemc/nmefc）81 文件零命中；执行模式：双 subagent 并行（数据/地图）+ 交付负责人契约与集成验收，2 处测试断言错误由负责人验收修正 |
| 2026-11-06 | M4 | ⚠️ **有条件通过**（CHG-13 改签；原「F-3.1~F-3.8 全过」口径不实，已更正） | 第四/五轮独立复核 + 负责人逐条取证：数据/算法层达标（附加必修 1–6/8 无虚报）；UI 组装层 D01/D02/D03 三个 P0 + N15 漏网于纯 JVM 测试（组装层零覆盖），**本轮已修**并锁测试（titlesInTabOrder/resolvePreset/Ttl 三态）；五模块 **391 运行 / 0 失败 / 10 跳过**（domain 93 + data 113×2 + ui 27 + detail 14 + map 31）；F-3 口径：3.3/3.6 通过、3.1/3.2/3.4/3.8 部分达标、3.7 转 M6、3.5 属 M5；D07/D08 转 M5（A-15）、D04~D14/N16~N20 转 M6；N17 误判裁定不成立；结构性根因=组装层零测试，OPT-45/54 冒烟锁登记 A-14 |
| 2026-11-06 | M5 | ✅ **通过** | 双批次执行（数据/算法 → UI，UI 编译依赖 topWindows API 无法并行）+ 负责人集成验收。**验收修正 subagent 4 处交付缺口**：数据 Agent 3 处测试失败（OPT-06 语义下旧用例未适配 ×1、稀疏样本误入 <24h 禁用分支 ×2）+ D07/D08/D03 锁测试零交付；UI Agent 语法错误（`as` 作标识符）+ 缺 imports + Screen 层 Top10/校潮 Sheet 未实现 + 测试零交付——负责人补齐 Sheet 组装与 35 条纯函数锁测试（组装层零覆盖教训第二次重演，CHG-14⑥）。客户三项开工裁决：全量执行 / D08=COALESCE / D03=接 Top10 读取。六模块 **446 运行 / 0 失败 / 10 跳过**（domain 107 + data 116×2 + ui 27 + detail 38 + favorites 11 + map 31）；F-2.1~F-2.4/F-2.6 代码级+单测级通过；F-2.2 性能项/F-2.5 真机项挂交付验收；CHG-14 登记 |
| 2026-11-09 | M6 | ✅ **通过** | 四批次串行（负责人契约 → 数据/算法 → UI → 负责人 `:app`）+ 每批次负责人独立复跑验证（`--rerun-tasks`）。**验收修正 sub-agent 2 处缺口**：批次 1 首轮 3 个测试失败（迁移断言×2 + 测试漏改注入×1，agent 自修后复验）；批次 2 收尾 favorites 测试编译错误（`epoch` 助手跨类引用，负责人修复）。八模块 **573 运行 / 0 失败 / 12 跳过**（domain 126 + data 134×2 + ui 34 + detail 45 + favorites 18 + map 31 + settings 21 + app 15；跳过=Room 真机 @Ignore 先例）；黄金对拍 B1–B7 保持；基准锁 <40ms 命中；红线扫描 main 源码零域名命中；**`app-release.apk` 49.1MB 自签名产出（apksigner 实证 DN）**；CHG-16 登记 + 三项偏差负责人裁决留证；M4 遗留 D04/D05部分/D09/D10/D11/D12/D13/D14 与 N16 部分闭环，N18~N20 随 D14/组装覆盖（KDoc/色值/自愈） |
| 2026-11-09 | V1.1 优化批次 A | ✅ 通过 | A 类五项全清（CHG-18）：真机迁移锁抓出 **P0 升级缺陷**（MIGRATION_3_4 列名与实体不一致 → v3→v4 升级崩溃）当日修复并双锁（真机 MigrationTestHelper + JVM 列名逐字一致断言）；androidTest 基建 + 真机 6/6（AAK-AN00）；八模块 561/0/0 跳过清零 + APK 复产出 + verify.ps1 门禁脚本就位。教训留证：**Room 迁移 SQL 列名必须与实体属性名逐字一致**（无 @ColumnInfo name 时默认列名=属性名）；「SQL 文本锁 ≠ 迁移行为锁」——JVM 文本对齐断言抓不住升级路径 schema 校验，必须真机 MigrationTestHelper |

## 5. 决策与变更记录

| 日期 | 决策/变更 | 影响 |
|---|---|---|
| 2026-11-02 | 执行模式确认：AI agent 团队独立开发 | 排期按并行压缩，目标交付 12-08 |
| 2026-11-02 | 项目计划 v1.0 发布 | 跟踪基线建立 |
| 2026-11-03 | M0 降级决策三项（水质兜底/无 Overpass/Photon 单源） | M3 实现范围收缩，不阻塞 |
| 2026-11-03 | gradle.properties 增加 `android.overridePathCheck=true`；新增 `local.properties`（不入库） | 中文路径构建放行 |
| 2026-11-03 | **角色分工变更**：后续开发由新开发会话执行（接手入口 `HANDOVER.md`）；本会话转为纯项目管理（跟踪/评审/风险/客户协调） | 交付模式变更 |
| 2026-11-05 | **CHG-06 合入基线**：Copernicus 凭据生效（GAP-02 关闭）；§6.9 第 3 级 GLACIO→OSTIA（GLACIO 已退役）；逆地理 RG-A/B/D 三级 + 节流 ≤1 req/2s；SRS §5.8/§6.9 已回写 | M3 实现口径固化 |
| 2026-11-05 | **执行模式**：M3 起采用"交付负责人定义契约 + 双 subagent 并行开发 + 负责人集成验收"模式（客户指定，节省上下文） | M4+ 沿用 |
| 2026-11-05 | **CHG-07 P1 修复批次完成**：① VetoEvaluator 缺测输入安全侧保守（wind→34/gust→45/dP3→−2.5/rain→6.0 档位，理由入 KDoc）；② §6.7 ΔP3 缺 past 权重 0.14→0.08 + "趋势数据不足"标注；③ `vetoReasons: List<String>?`（无理由 null，§6.5 修正 3）；④ N-4 兜底（脏 JSON/脏时间串/空序列/样本不足全部结构化降级）。新增 ResilienceTest；domain 64→72、data 42→80×2。夹具 BASE_EPOCH_SEC 注释日期错误已修正。留证：全量 258 运行 / 0 失败 / 8 跳过，黄金对拍 ≤2 不变 | M1/M2 验收签署条件（P1）闭环；裁决项 C1–C4 仍待客户（A-10） |
| 2026-11-06 | **CHG-12 合入基线**：M4 契约微调与集成裁决——① `TideSpotRepository` 构造器尾部只增可选参数 `hourlyScoreDao`（源兼容）；② `:feature:map` 增 1 行依赖 `:core:ui`（WaterBodyTabTitles 下沉委托所需）；③ `hourly_score` 落库窗口含 past 7d 前置小时（≈408h）的口径澄清（CHG-03 约束网络拉取口径，非落库行数）；④ M6 `:app` 接线清单登记（F-3.7 元数据/F-3.8 ZoneId 注入/DAO 注入/URL 单点复核）；⑤ 根 `.gitignore` 源码排除缺陷修正 | M4 口径固化；详见 REQUIREMENTS_REGISTER CHG-12 |
| 2026-11-06 | **CHG-13 合入基线**：M4 第四/五轮独立验收——22 条意见 20 属实/1 误判（N17）；M4 改签「有条件通过」；P0×3（D01 tabTitles 错位 / D02 嵌套滚动崩溃 / D03 评分双管线）+ N15（>48h 不可用规则）当日修复并锁测试；验收口径更正（F-3 分条）+ 组装层零覆盖根因确认 | M4 缺陷批次闭环；D07/D08 转 M5（A-15）、其余转 M6；详见 REQUIREMENTS_REGISTER CHG-13 |
| 2026-11-06 | **CHG-14 合入基线**：M5 契约变更与集成裁决——客户三项开工裁决定案（全量执行 / D08=COALESCE null=保持原值 / D03=hourly_score 接 Top10 读取）；D07 patch 合并（含 waterBodyType 补全）；Models.kt 只增三处（FavoriteSpot.lastViewedAtEpochSec / BestWindow.rank / Output.fallbackBestEpochSec）；FO-13/FO-14/OPT-06/OPT-08 落地；UI 批次 Sheet 组装层与测试由负责人补齐 | M5 口径固化；六模块 446/0/10；详见 REQUIREMENTS_REGISTER CHG-14 |
| 2026-11-08 | **CHG-15 合入基线**：M5 独立验收双意见（M5改进意见01/02）逐条取证裁定——**意见01 偏差①「Top10 混入历史窗口」为唯一硬正确性缺陷，转 M6 必修首批**（`find` 尾增 `nowEpochSec` 过滤 + 双调用方 + 锁测试）；偏差②③④⑤ 与 F-2.1 主路径占位/默认名称均属实但已登记（归并 M6 既有 D10/D12/FO-01/CHG-12④b/OPT-M5-02/07 清单）；OPT-M5-06~12 与意见02 P1/P2 入 v1.1 需求池；**意见02 五处误判驳回留证**（校准面板已实现 / 迁移测试与跨点聚合测试已存在 / 乱码系验收工具显示问题 / 时区测试已覆盖） | M5 验收闭环；M6 任务书按此并入；详见 REQUIREMENTS_REGISTER CHG-15 |
| 2026-11-08 | **CHG-15① 偏差①当日修复完成（M6 必修项提前清零）**：`BestWindowFinder.find` 尾增 `nowEpochSec: Long = 0`（0=不过滤向后兼容；>0 剔除历史小时且有效小时数同步收缩，兜底最高分只在未来序列内取）；`TopWindowDerivations.top10` 尾增透传；`DetailStateHolder.onResult` 与 `TideSpotRepository.topWindows` 均传「当前小时的下一整点」（SRS §6.6）；`TopWindowsResult.validHoursPresent` 语义收紧为未来有效行数；`FavoritesSummaryFormatter.line` 兜底文案优先于「未加载」（全过期显示禁用文案）。锁测试 +8（domain 历史剔除/兜底未来域/未来不足24h禁用/now=0向后兼容 ×4、detail ×1、data 对拍同参+全过期 ×2、favorites 文案优先级 ×1）。全量 **454 运行 / 0 失败 / 10 跳过** | 偏差①闭环；M6 剩余必修清零，接手即从打磨项开工 |
| 2026-11-09 | **CHG-16 合入基线 + M6 三批次执行完毕**：批次 0 契约（Models.kt 只增 VetoCode/vetoCodes/tzOffsetSec）；批次 1 数据/算法（OPT-M5-04/02、D09/D13、FO-15、OPT-19、OPT-05、Room v3→v4、URL 收敛）；批次 2 UI（D04/D10/D11/D12/D14、OPT-M5-03/05/07、settings 新建、主题/文案层）；批次 3 `:app` 组装 + 自签名 APK。**负责人偏差裁决**：① Copernicus 凭据构建期 BuildConfig 注入接受（CHG-02 不上架 + CHG-11 单机自用；运行期外部文件读取登记 v1.1）；② F-3.7 DTO 级模型元数据透出登记 v1.1（需扩展 M2 网络层 DTO，超 M6 打磨边界）；③ 预取开关空操作语义（真预取 v1.1）。验收修正 sub-agent 缺口：批次 2 收尾 favorites 测试编译错误（`epoch` 助手跨类引用）由负责人修复 | M6 口径固化；八模块 573/0/12；APK 产出 |
| 2026-11-09 | **版本管理建立（jj + git colocated）**：客户选择 Jujutsu；`jj git init --colocate` 于工作区根，单提交基线 `main`/`m6-exit` = `3c018c9`（204 文件：185 工程 + 19 根目录文档/规则；磁盘/库内 .kt 145=145 核验一致）。**建库过程教训（留证）**：① `.gitignore` 必须在首次快照前生效——jj 快照的忽略规则语义随父提交树上被跟踪的 .gitignore 走，规则后补救不了已跟踪文件；② `check-ignore` 对已跟踪文件不报"被忽略"，勿以此判定入库状态，以 `git ls-tree` 为准；③ 首轮基线混入 `tools/`(286)/`.m4tmp/`(26)/`.opencode/`(2) 垃圾与 `cm账号.txt`（Copernicus 账号明文，已入 .gitignore 且从未外发——本地对象库已随重建销毁）；④ Git for Windows 默认 autocrlf 会在 jj side 产生全量假差异——已加 `.gitattributes`（`* -text`）+ 仓库级 autocrlf=false 根治。失败 checkout 曾误删磁盘 .gitignore 与根目录文档，已从 tag 指向的完好树恢复。真机修复批次起每批次一次 jj 修订提交，验收期全程可审计 diff | 交付基线可审计；APK↔tag 绑定就绪 |
| 2026-11-09 | **CHG-17 登记后 CHG-18 合入（V1.1 优化批次 A）**：A-1~A-5 全清 + 真机迁移锁抓出 P0 升级缺陷（`MIGRATION_3_4` 列名 `veto_codes` ≠ 实体属性 `vetoCodes`，v3→v4 升级 schema 校验必崩）当日修复双锁；androidTest 基建引入 androidx.test 三件套（maven 可达性确认 + 本机代理 MITM 证书以 `trustStoreType=WINDOWS-ROOT` 过验）；verify.ps1 本地 CI 门禁就位；**环境预警转解锁**：本机网络经代理可用 → C 类（A-14 Robolectric/C-2 AGP 升级/C-3 lifecycle-runtime-compose）下一批次可开工 | 八模块 561/0/0（跳过清零）+ 真机 6/6；详见 REQUIREMENTS_REGISTER CHG-18 |
| 2026-11-10 | **CHG-36 已裁定**：高德地图接入（`AMAP_FEASIBILITY.md`）→ **单 APK 应用内开关 + 发行 BYOK + 个人非商用 + 境外自动回退开源 + 排入 V1.2 backlog**；登记 `OPT_REQUIREMENTS_V11.md` §7 **OPT-V12-05（D-AM）**；工程量 8–12 人日（含 1–2 天 spike），**不触碰 v1.1 交付，不动代码** | 范围变更定案；开工前置=客户提供开发测试 Key |

## 6. 风险快照（Top 3）

| ID | 风险 | 状态 | 近期动作 |
|---|---|---|---|
| R-09 | 中文路径导致 Gradle 构建不稳定 | 🟢 缓解 | subst T: + `.tools` Gradle + UTF-8 jvmArgs 已稳定跑通；**A-5 本机正式 gradlew 首跑验证通过（11-09）**——物理中文路径下 favorites 等未配 jvmArgs 的模块仍有类加载失败，**subst T: 维持标准构建路径** |
| ~~R-02/R-07~~ | 真机资源不足 → 验收顺延 | 🟢 **关闭（CHG-11）** | 客户提供单台荣耀机（Android 16/无 GMS）作验收设备；GMS 通道书面豁免 |
| OSTIA 图层故障 | 千岛湖掩膜复测暂缓（服务器端） | 🟡 关注中 | A-07：恢复后一条 curl 复测；CHG-09 UNKNOWN 方案已兜底 |
## 7. v1.1 P1 批次（2026-11-10，CHG-28）

| 项 | 状态 | 说明 |
|---|---|---|
| OPT-V11-05 真预取 | ✅ 代码+锁测试 | 前 5 / TTL/2 / 令牌不足放弃剩余 / 并发 2 / 离线零请求 / 失败静默；收藏页触发，受预取开关 |
| OPT-V11-06 相对时间 | ✅ | `RelativeTimeFormatter`（今天/明天/周X/日期，选点当地时区）；收藏摘要 + 详情 Top10 起始 |
| OPT-V11-07 涨退潮观测 | ✅ | 四类观测 + 时刻补记（±72h）+ 模型对照 + 按类型 `fitManual` + 落库读回 |
| OPT-V11-08 跨点 Top10 缓存 | ✅ | `TopWindowsLruCache`（100/LRU/不落库；二次进入 0 次读库） |
| OPT-V11-09 校正提示 | ✅ | `find(manualTideLagHours)` → 注意事项「已按现场校潮校正：提前/延后 Xh」；现算/读侧同口径 |
| OPT-V11-10 搜索筛选 | ✅ | `FavoritesFilterLogic` + 搜索框/标签/预设 chips + 空态清除；先筛后排 |
| OPT-V11-11 陈旧标记 | ✅ | 逐源 `lastFetchedMillis` + `FavoritesStaleLogic`（复用 Ttl）→ 陈旧/已过期 + 数据时间 |
| UX-18 地图定位 | ✅ | 冷启动定位动画 800ms（不设准星）+ 返回地图回上一查看点/lastViewed |
| UX-19 地图样式 | ✅ | 默认 Voyager + 设置三选（Voyager/Positron/Dark Matter，URL 单点） |
| UX-20 移除底栏 + 浮动按钮 | ✅ | 删 NavigationBar；地图浮动收藏/Top10/设置 + insets |
| UX-21 返回手势 | ✅ | 详情→来源页（sourcePage）、收藏/设置→地图、地图 2s 双返回退出 |
| UX-22 Tab 顺序 | ✅ | 钓鱼指数→潮流→天气→水质→风浪（枚举/列表/标题三处联动，默认落地钓鱼指数） |
| UX-23 潮汐术语 | ✅ | `TidePhase`（月龄制）+ 海水点潮 Tab chip；不入评分 |
| UX-24 日期选择器 | ✅ | DaySelector → 「今天 周二」/「11/12 周三」 |

- **验证**：八模块 **701 运行 / 0 失败 / 0 跳过** + `BUILD SUCCESSFUL`（八模块测试 + assembleRelease，退出码 0）+ APK 复产出（`潮点TideSpot-v1.1.apk`，E:\Trans 已同步）。
- **遗留**：P2（OPT-V11-12 批量 / 13 i18n 存量外移 / 14 智能排序 / 15 详情卡片 / 16 分组 Room v5→v6 / 17 缩略图）未开工；地图「本点 Top10」未做（浮动 Top10 落为跨点 Top10）。
- **风险**：UX-18/20/21 属组装层改动，必须真机逐屏冒烟（本次仅 JVM 锁 + 编译/打包验证）；P2-16 Room v5→v6 需真机 `MigrationTestHelper`。
- **下一步**：① 真机冒烟 P1（重点：浮动按钮 insets、返回手势、地图样式切换、冷启动定位）；② P2 按 14/15/12/16/17/13 顺序开工；③ **已回写（CHG-30，2026-11-10）**：`OPT_REQUIREMENTS_V11.md` §3 OPT-V11-22 已补「三处联动」实现口径 + 默认选中「钓鱼指数」+ 顺序锁 GWT；SRS v1.4 §8.1 同步（CHG-31）。

## 8. v1.1.1 缺陷修复（2026-11-10，CHG-35）

| 项 | 状态 | 说明 |
|---|---|---|
| Bug A：海域显示「水位（无潮汐）」 | ✅ 已修 | 根因＝用全球 SST 当湖泊掩膜 + 确认时从不传真实海况；改：`probeMarineHasValue`（true→NEARSHORE，false→OSTIA，失败→UNKNOWN）+ 判定缓存 v2 |
| Bug B：拖动地图信息条不更新 | ✅ 已修 | `onMapMoved` Idle + 用户手势 → Preview；MapScreen 以 `REASON_API_GESTURE` 区分手势/程序动画 |
| 存量自愈 | ✅ | 详情刷新把 `INLAND/UNKNOWN`（海洋有值）回写 `NEARSHORE`；`effectiveWaterBody` 首屏纠正标题与潮汛 chip |
| 版本 | v1.1.1 | `versionCode=3` / `versionName=1.1.1`；APK 51,618,176 B 已分发 `E:\Trans`（SHA256 与构建产物一致） |

- **验证**：八模块 **716 / 0 / 0** + `assembleRelease` 成功。
- **已知风险**：OSTIA 图层本质是全球 SST，**不能再当湖泊掩膜**；内陆湖判定现依赖「marine 全 null/4xx → OSTIA」，后续如恢复该探针需换真湖泊产品。
- **下一步**：真机复测两 bug（南海点标题/潮汛 chip；冷启动与返回地图后拖动信息条跟随），并确认老收藏点首屏自愈。

## 9. v1.1 P2 批次（2026-11-10，CHG-37）

| 项 | 状态 | 说明 |
|---|---|---|
| OPT-V11-14 智能排序 | ✅ 代码+锁测试 | `SortOrder.RECOMMENDED` = `BestWindow.rank` 降序、同 rank 按 name、未加载排最后 |
| OPT-V11-15 收藏详情卡片 | ✅ | `FavoritesInfoCardLogic`（坐标 6 位/精度说明/海拔/水体徽标/最近拉取/分组；读库、离线完整、缺值 `—`）；卡片内「信息」按钮展开（D16） |
| OPT-V11-16 收藏分组 | ✅ | Room **v5→v6** `groupName`（可空无 DEFAULT）；默认组「未分组」置末、折叠、编辑/批量可改组 |
| OPT-V11-12 批量操作 | ✅ | 长按进多选、全选/反选、批量删除（**单事务 + 级联 6 表**，D19）、批量改标签（追加/替换）、批量改分组；**不做导出**（D3） |
| OPT-V11-17 缩略图 | ⛔ **已移除（客户裁定 D21）** | 实现期实证：CARTO `rastertiles/*` 已要求 API key，无 key 返回「API KEY REQUIRED」占位图（v1.2.0 真机截图留证）→ 客户裁定删除该功能；`MapThumbnailStore`/`ThumbnailCachePolicy`/`TileMath`/`cartoStaticTile` 全量移除，本轮不做 |
| OPT-V11-13 i18n 存量全量外移 | ✅ **技术完成（CHG-40/41/42/43/44）**；GAP-05 定稿接线 **10/17**，剩余 7 条转 **V1.15** | 见下「i18n 实施进展」 |

- **验证**：`core:ui` / `core:data` / `feature:favorites` 单测全绿；`assembleRelease` 产出 `潮点TideSpot-v1.2.0.apk`（`versionCode=4`，自动分发 `E:\Trans`）；**真机 AAK-AN00/Android 16：`connectedDebugAndroidTest` 8/8 通过**（含新增 `v4→v5`、`v5→v6` 迁移用例；真实升级路径另由 v1.2.0 覆盖安装验证：5 条收藏全保留、`groupName` 列生效）。
- **开发期发现并修正的既有缺陷**：① `Format.value(v, digits)` 的 digits 上限 3 → 收藏列表「坐标 6 位小数」实际只有 3 位 → 新增 `Format.coordinate`（6 位专用，`value()` 上限不变以免行为漂移）；② `deleteFavorite` 只删 `spot` + `hourly_score`，遗留 4 张表孤儿行 → 按 D19 一并级联清理。
- **风险**：① 缩略图在列表滚动时的帧率与磁盘缓存增长（30 d × 每点 1 张 ≈ 数十 KB/点，可接受；真机滚动冒烟验证）；② Room v5→v6 真机迁移锁 **已补齐并真机通过**（`RoomMigrationInstrumentedTest` v4→v5/v5→v6，8/8）；③ 长按/点按手势区分需真机确认（多选态点按 = 选择、非多选态点按 = 进详情）。
- **下一步（2026-11-11 更新）**：① P2 真机冒烟五项**待复测**（批量操作/排序/信息卡/分组/长按手势）；② OPT-V11-13 **已完成技术目标**（扫描 FAIL=0、中英逐键锁、真机英文界面无残留）；③ **GAP-05 剩余 7 条 → V1.15 需求池 `OPT-V115-01`**（见 §10）。

### i18n（OPT-V11-13）实施进展（CHG-40，2026-11-11）

- **架构已落地**：`TideSpotStrings`（core:ui 接口 + `ZhStrings`/`EnStrings`/`ResStrings` 三实现 + CompositionLocal 注入）与 `DomainCopy`（domain 接口 + `ZhDomainCopy`/`ResDomainCopy`）；domain 四个计算入口全部以**默认参数**接入，不注入时中文逐字不变（700+ 既有单测零改动通过）。
- **已全量外移**：`core:ui` 79、`core:domain` 58、`feature:settings` 73、`feature:map` 10（界面文案）。
- **扫描门禁**：`.tools/i18n_scan.mjs`（注释感知 + 豁免白名单），基线 **538 → 0**（**FAIL=0**，豁免 13→12：`SourceMetaRegistry.granularity` 改用 `DomainCopy.hourlyGranularity` 后移出白名单）。
- **完成（批次二，CHG-44，2026-11-11）**：`feature:detail` 135、`feature:favorites` 117、`app` 21、`core:data` 3 **全部外移**；新增/补齐 `ts_detail_*`/`ts_fav_*`/`ts_app_*` 中英资源。
- **验收现状**：八模块 `gradlew test` **EXIT=0**；`assembleRelease` 产出 `潮点TideSpot-v1.2.0.apk`（51,915,708 B，已分发 `E:\Trans`）；真机切英文逐屏核对**无残留中文**（客户报障的详情页 4 类残留已修，见下「英文残留修复」）。
- **GAP-05 接线现状**：**10/17**（已接线：`ts_resolving`、`ts_location_denied`、`ts_favorite_dialog_title`、`ts_favorite_name_hint`、`ts_top10_this_spot`、`ts_top10_all`、`ts_top10_empty`、`ts_confirm_favorite`、`ts_placeholder_v11`、`ts_cancel`）；**剩余 7 条转 V1.15 `OPT-V115-01`**（需新增 UI 落点，见 §10 与 `OPT_REQUIREMENTS_V115.md`）。
- **英文残留修复（CHG-44 关键教训）**：根因＝纯函数默认参数回落 `ZhStrings`/`ZhDomainCopy`，而调用点未注入。已修 `WaterBodyTabTitles.titlesFor`、`DaySelectorLogic.chips`、`Derivations.tabStatus/rowsFor`、`SourceMetaRegistry.granularity`，连带 `chipText`/`extremesLine`/`correctionCompare`/`Top10Formatter`/`SourceFooter`。**红线：新增纯逻辑必须用 `strings/copy` 参数，调用点显式注入**。
- **环境红线**：Gradle 必须走 `subst T:`（`verify.ps1` 同口径）；直接以中文路径运行会导致 test worker `ClassNotFoundException`。

- **批次二执行口径（已定，不需再评估）**：`DetailScreen`/`FavoritesScreen`/`MainActivity` 直接 `stringResource(R.string.*)`（资源已建/按同规范补齐）；纯逻辑（`Derivations`/`Top10Formatter`/`FavoritesSummaryFormatter` 等）加 `strings: TideSpotStrings = ZhStrings` / `copy: DomainCopy = ZhDomainCopy` 参数；测试断言保持中文默认实现（零漂移），英文由 `EnStrings` 独立锁定。
- **风险—影响—概率—应对**：① detail/favorites 大文件逐条替换遗漏 → 影响：切英文残留中文 → 概率中 → 应对：`.tools/i18n_scan.mjs` 门禁 + 真机切换语言逐屏核对；② 纯逻辑注入参数遗漏 → 影响：英文环境下该处仍中文 → 概率中 → 应对：扫描 FAIL 即拦截；③ 资源键漏翻 → 概率低 → 应对：`EnStringsParityTest` 逐键相等锁。

## 10. V1.15 需求池（2026-11-11 建档，CHG-45）

> 规格：`OPT_REQUIREMENTS_V115.md` ｜ 交接：`HANDOVER_V115.md` ｜ 目标发布：`v1.15.0`（`versionCode=5`）
> 来源：V1.1 收官遗留（GAP-05 剩余 7 条）+ 客户指令「按 design-handoff UI 设计做 UI 更新」（设计包已评估可用）

| 项 | 状态 | 说明 / 落点 |
|---|---|---|
| OPT-V115-01 GAP-05 剩余 7 条接线 | ✅ 已交付（2026-11-11，CHG-46） | 7 条全部接线：`ts_reverse_geo_off`（地图信息条上方 5s 提示）｜`ts_window_bar`（HourChart BAR 段内文字标签，段宽不足降级细条）｜`ts_record_high_tide`（校潮 Sheet 空态引导）｜`ts_offline`（`:core:ui` 新增 `OfflineBanner`，详情/收藏页顶部）｜`ts_stale`（黄条=通用句+数据时间，新增 `ts_stale_with_time` 中英双语）｜`ts_dimension_degraded`（低置信徽标同行说明）｜`ts_chart_table_switch`（图表/数值表→单 Switch）。GAP-05 **17/17** 每 key ≥1 处引用；`i18n_scan` FAIL=0；八模块 `test` 全绿 |
| OPT-V115-02 UI v2 设计包落地 | 🔶 主体已落地（CHG-46 骨架 + **CHG-47 视觉重排**）；剩余见下 | **已落地**：S01 C02 顶部工具浮层（定位/搜索/收藏/设置）+ C19 Top10 入口 + 移除浮动三按钮；S02 TOPBAR + C10 今日指数主卡 + C09 图表卡 + C17 位置修正 + C06 底部免责条；S09 TOPBAR + C06 底部免责条；Theme 画布/卡片/描边 token（冻结色不动）。**剩余**：C03 信息条 236×48 浮条版式、C16 六版块卡片化、C21/C22 地图署名与来源行、9 屏真机中英+深色截图与对比度 ≥4.5:1（`adb devices` 为空）。产物 `E:\Trans\潮点TideSpot-v1.15.1.apk` |

- **开工顺序建议**：01 内先做 `ts_record_high_tide`/`ts_stale`/`ts_dimension_degraded`/`ts_chart_table_switch`/`ts_reverse_geo_off`（改动小），再做 `ts_window_bar`（Canvas 绘制）与 `ts_offline`（需连通性探测）；随后 02。
- **完成判据（GAP-05 闭环）**：17/17 每 key ≥1 处 `R.string.` 引用 + `DECISIONS.md §6` 七项复核全绿 + `i18n_scan.mjs` FAIL=0。
- **V1.1 收官口径（2026-11-11）**：V1.1 池 26 项 = **25 项已交付 + 1 项移除（OPT-V11-17/D21）**；功能目标全部达成，**剩余为验收性收尾**（P2 真机冒烟五项、v1.1.1 两 bug 复测、v1（M0–M6）交付验收 12-04~12-08），及 GAP-05 7 条（已转入 V1.15，不在 V1.1 记未完成）。

### V1.15 视觉校准（2026-11-11 / CHG-48，真机取图比对）

- 方法：db 截图（AAK-AN00 1272×2800）+ 设计 SVG 几何提取，替代只看层坐标。
- S01：白色搜索框 206×46 + 3 个 44×46 方块按钮（定位实心）+ Top10 深蓝横幅 366×48。
- S02：分段 Tab（容器 358×40 + 白胶囊）、日期胶囊 rx18、**深蓝主卡 #0D47A1 + 档位胶囊 + 五段色带**、图表卡 210dp。
- 文字色：onSurface #10243A / onSurfaceVariant #8A9DB0（深色 #E6EDF5 / #9FB0C0）。
- 产物：E:\\Trans\\潮点TideSpot-v1.15.2.apk（versionCode 7）；已重装真机验证。
- 剩余：图表切换版式、日期行拥挤、S09 卡片化、C03 信息条、C21/C22 底部行、S03–S08 未对齐。

### V1.15 DoD 收官状态（2026-11-11，CHG-46~54）

| DoD 项 | 状态 | 证据 |
|---|---|---|
| 1. `i18n_scan` FAIL=0 且 GAP-05 17/17 | ✅ | 1.15.11 修复 15/17 回归（CHG-54），复核 17/17 |
| 2. 八模块 `gradlew test` EXIT=0 | ✅ | 每次批次均跑，BUILD SUCCESSFUL |
| 3. `assembleRelease` 产出并分发 `E:\Trans` | ✅ | `潮点TideSpot-v1.15.11.apk`（versionCode 16） |
| 4. 真机九屏中英双语 + 深色冒烟截图 | ❌ **未完成** | 中文九屏与深色（S02/S06）已留证；**英文未验**（ROM 不支持 `adb` 切应用语言，需人工在系统「应用语言」切换） |
| 5. 台账回写 | ✅ | CHG-46~54；本节即 §10 收官回写 |

**结论**：功能与视觉主体已交付；**第 4 项（英文真机）是唯一未闭环的验收项**，需客户手工切系统语言后补截图。

### 英文验收补记（2026-11-11，CHG-55）

- **App locale**：`en`（`adb shell cmd locale get-app-locales com.tidespot.app` → `[en]`）。
- **扫描结果**：S01/S02/S08/S09 + Water Quality/Weather/Lake Wind/Water Level 四个 Tab，`uiautomator dump` 中文残留 **0**（点位名为用户数据，保留中文属正常）。
- **修复**：地图兜底名未注入 strings、收藏档位透传 domain 中文、五维 Tab 英文等分截断（改横滑）。
- **深色英文**：详情页截图留证。
- **DoD 第 4 项状态：✅ 闭环**。
