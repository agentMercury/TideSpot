# 模块契约（冻结）· 并行开发对齐点

> 依据：SRS v1.2 ｜ M0 结论见 `../DATA_SOURCE_PROBE.md` ｜ 修订需经交付负责人并登记 REQUIREMENTS_REGISTER

## 模块与依赖方向

```
:app（M6）→ :feature:map(M3) / :feature:detail(M4) / :feature:favorites(M5)
feature:* → :core:data(M2) → :core:domain(M1)
:core:domain → 无任何依赖（纯 Kotlin JVM）
```

## 并行分工（当前进行中）

| Agent | 模块 | 范围 | 禁区 |
|---|---|---|---|
| 算法 Agent | `:core:domain` | §6 算法全量实现 + §10.1 单测（附录 B1–B7 黄金值） | 不碰 `:core:data`、不碰 build 文件（仅可在 domain 内加依赖测试所需） |
| 数据 Agent | `:core:data` | §5 契约网络层、DTO、Room、TTL、重试、fetch_log、6 态 Mock 测试 | 不碰 `:core:domain` 源码（Models.kt 只读） |

## 并行分工（M3 进行中，2026-11-05 起）

| Agent | 模块/范围 | 禁区 |
|---|---|---|
| 数据 Agent | `:core:data`：逆地理 RG-A/RG-B/RG-D 链、正地理搜索、水体判定数据侧（Copernicus token + OSTIA 探针）、Room v2（spot_reverse_cache + spot_water_body_cache + 迁移）、单测 | 不碰 `:feature:map`、不改 M2 既有类（Models.kt 只增）、不碰 build 根配置 |
| 地图 Agent | `:feature:map`：模块骨架 + MapLibre/CARTO 底图 + 准星状态机 + 信息条 + 定位双通道 + 权限状态机 + 搜索 UI | 不碰 `:core:data`/`:core:domain` 源码；对数据层只依赖下述接口签名；可改 `settings.gradle.kts`（仅加 include） |

### M3 子契约（两 Agent 共同遵守；Models.kt 新增项由数据 Agent 落地，地图 Agent 按此签名编码）

```
// Models.kt 新增（com.tidespot.core.domain.model）
enum class ReverseGeoSource { RG_A, RG_B, RG_D, CACHE }
data class NamedPlace(
    val lat: Double, val lon: Double,
    val displayName: String,          // 优先级：RG-B 聚落名 > RG-A(locality|city) > RG-A(省) > RG-D plusCode；后缀附 RG-A 市/省（有则附）；信息条永不空白
    val subLabel: String?,            // 市/省 或海区名
    val plusCode: String?, val waterName: String?,   // waterName 恒为 null（RG-C 不接入）
    val locality: String?, val city: String?, val admin1: String?,
    val source: ReverseGeoSource,
)
data class GeocodeCandidate(val name: String, val admin1: String?, val admin2: String?,
    val lat: Double, val lon: Double, val elevation: Double?, val timezone: String?)

// :core:data 公开 API（数据 Agent 实现；构造与 OpenMeteoClient 同风格：OkHttp、UA、ioDispatcher、clock 可注入）
class ReverseGeocoder(...) {
    suspend fun resolve(lat: Double, lon: Double): NamedPlace   // 缓存(0.005°,30d)→RG-A(6s)∥RG-B(4s)→RG-D；节流 ≤1req/2s；仅确认选点后由调用方触发
}
class GeocodingClient(...) {
    suspend fun search(query: String, count: Int = 5): List<GeocodeCandidate>   // Open-Meteo /v1/search, language=zh
}
class WaterBodyClassifier(...) {   // 判定树 §6.9；OSTIA 探针失败返回 null → 走降级
    suspend fun classify(lat: Double, lon: Double, marineAllNull: Boolean, elevation: Double?): WaterBodyType
}
```

- 频控：RG-A/RG-B 各自令牌桶 ≤1 req/2s；`features:[]` 不得重试；RG-A 失败不重试超 1 次。
- 红线：Overpass 域名零出现；UA 复用 `TideSpotUserAgent`；OSTIA/Copernicus 凭据从本地配置读取，禁止硬编码入库。

## 并行分工（M4 进行中，2026-11-06 起）

| Agent | 模块/范围 | 禁区 |
|---|---|---|
| 数据 Agent | `:core:data` + `:core:domain`（最小触碰）：M4 附加必修 1/2/3/4/5/6/8（六态 Mock 对齐 §10.2、黄金对拍走生产管线、刷新状态机三修、时区 ZoneId 化、rain24h 接线、MockFixtures 移 test、hourly_score 表 v2→v3） | 不碰 `:feature:*`、不碰根 build/settings.gradle.kts |
| UI Agent | `:core:ui`（新建：HourChart/DaySelector/FactorBreakdown/ConfidenceBadge/Band + WaterBodyTabTitles 下沉）、`:feature:detail`（新建：5 Tab + DaySelector + Top10 入口）、settings.gradle.kts 仅加 include、`:feature:map` 仅 WaterBodyTabTitles 改委托一行 | 不碰 `:core:data`/`:core:domain` 源码（Models.kt 只读）；不改 `:feature:map` 其他文件 |

### M4 子契约（两 Agent 共同遵守）

```
// 接口边界（冻结）：
// 数据 Agent 不得改 TideSpotRepository 公开签名；UI Agent 面向现有 API 编码：
TideSpotRepository.refreshSpotDetail(spot: FavoriteSpot, force: Boolean = false): SpotDetailResult
TideSpotRepository.cachedDetail(spot): SpotDetailResult
SpotDetailResult.slices: List<HourSlice>          // SeriesJoiner.kt:7-11，null 保真
FishScoreResult.vetoReasons: List<String>         // 非空，isEmpty() 判定（CHG-08④）
// 时区：Repository 按响应 IANA timezone 换算并写 spot.tzOffsetSec；
//       HourlyScoreInput.tzOffsetHours 字段保留，调用侧显式传值（禁再依赖默认 8.0）
// WaterBodyTabTitles：canonical 实现迁至 :core:ui；:feature:map 原对象一行委托，测试保持通过
// hourly_score 表（§7.3）：PK(spotId,hourEpoch,algoVersion)；vetoReason SQL 列可空，
//       落库领域空列表→NULL；Room MIGRATION_2_3 由数据 Agent 落地
```

- 双方共同红线：Models.kt 只增不改；全部外部 URL 只能在 `TideSpotUrls` 拼接；红线域名（overpass/cnemc/nmefc）零出现；缺值=null 禁 0 填充；SI 单位；RG 节流与 Open-Meteo 频控内置不得移除；Copernicus 凭据禁硬编码。
- 构建：`:core:ui`/`:feature:detail` 参照 `:feature:map/build.gradle.kts` 与 `libs.versions.toml`；Compose + Material3；`unitTests.isReturnDefaultValues = true`；UTF-8 test jvmArgs。
- 验证：`gradle.bat :core:domain:test :core:data:test :core:ui:testDebugUnitTest :feature:detail:testDebugUnitTest :feature:map:testDebugUnitTest --console=plain -p T:\`（JAVA_HOME 见 HANDOVER §3）。

## 并行分工（M5 进行中，2026-11-06 起）

| Agent | 模块/范围 | 禁区 |
|---|---|---|
| 数据/算法 Agent | `:core:domain`（FO-13/FO-14/OPT-06/OPT-08）、`:core:data`（A-15：D07 patch 语义/D08 COALESCE/D03 读侧收口）、Models.kt 尾增 FavoriteSpot.lastViewedAtEpochSec | 不碰 `:feature:*`、不碰根 build/settings.gradle.kts、不碰 `:core:ui` |
| UI Agent（第二批派发） | `:feature:favorites`（新建）、`:feature:detail`（Top10 F-3.5 + 现场校潮面板接线）、settings.gradle.kts 仅加 include(":feature:favorites") | 不碰 `:core:data`/`:core:domain` 源码；不改 `:feature:map` |

### M5 子契约（两 Agent 共同遵守；客户已裁决：全量执行 / D08=COALESCE / D03=接 Top10 读取）

```
// ── Models.kt（只增；登记 CHG-14）──
data class FavoriteSpot(..., val lastViewedAtEpochSec: Long = 0)   // 构造器尾部只增可选参数；toDomain/toEntity 同步映射 lastViewedAt 列

// ── BestWindowFinder（domain）──
// FO-13：合并间隔 = MERGE_GAP_HOURS * 3600L（≤1h；删除 +1800L 魔数，实际 5400s 违 §6.6 步骤 2）
// FO-14：rankAcrossSpots 改按 rank 全局降序（现 peakScore，BestWindowFinder.kt:100）
// Output 尾增：val fallbackBestEpochSec: Long? = null   // 兜底时最高分时刻（epoch 秒）；domain 文案不变，时刻渲染在 UI 层
// 锁测试：间隔 3600s 合并 / 5400s 不合并；非整点时区（±30min）下 hourEpoch 对齐去重边界；跨点按 rank 排序；fallbackBestEpochSec 命中

// ── TideCalibrator（domain）──
// OPT-06：fitManual 模型极值匹配限 ±6h 窗（abs(it-obs) <= 6*3600 先过滤）；
//         窗内无匹配的观测不计入 lag 中位数；有效观测 <2 → adopted=false + message 提示
// OPT-08：NOAA 附录 B7（SRS）序列归档 test 资源；回归断言 RMSE + 留出集（Holdout）校正后优于校正前

// ── D07 patch 语义（:core:data）──
// TideSpotRepository.upsertFavorite：favorite.id > 0 时先 spotDao.getById 读旧实体，
//   copy 合并派生列 elevation/tzOffsetSec/speciesId/lastViewedAt（toEntity 不携带，防 @Update 全列覆盖冲掉）；
//   createdAtEpochSec 保留旧值；新插入路径不变。Mappers.toEntity 签名不动。
// 锁测试：upsertFavorite(updateSpotWaterBody 写入派生列 → 编辑名称) → 派生列往返不丢（MapperTest/TideSpotRepositoryTest）

// ── D08 COALESCE（:core:data；客户裁决 null=保持原值）──
// SpotDao.updateWaterBody SQL：elevation = COALESCE(:elevation, elevation), tzOffsetSec = COALESCE(:tzOffsetSec, tzOffsetSec)
// KDoc 统一「传 null = 保持原值」；TideSpotRepository.updateSpotWaterBody KDoc 去掉「null 即置 NULL」矛盾句
// FakeDaos.SpotDao 覆写同步同语义；锁测试各 1 条

// ── D03 读侧收口（:core:data；客户裁决接 Top10 读取）──
// 新增（尾部只增，不改既有签名）：
data class TopWindowsResult(
    val windows: List<BestWindow>,
    val fallbackMessage: String?,      // BestWindowFinder.Output.fallbackMessage 透传
    val fallbackBestEpochSec: Long?,   // 兜底最高分时刻（UI 渲染「（时刻…）」）
    val validHoursPresent: Int,        // =0 表示未算过/无库内行 → UI 显示「未加载」
)
suspend fun TideSpotRepository.topWindows(spot: FavoriteSpot, horizonDays: Int = 7): TopWindowsResult
// 实现：hourlyScoreDao.forSpot(spot.id, FishScoreCalculator.ALGO_VERSION) → 重建 List<FishScoreResult>：
//   timeEpochSec=hourEpoch；total=score；factors=factorsJson 解析（与 HourlyScoreStore.toEntities 对称，手工 JSON）；
//   vetoReasons=columnToVetoReasons(vetoReason)；confidence=Confidence.ESTIMATED
//   （仅窗口计算内部使用，禁止透出展示——D10 禁硬编码约束不变，KDoc 注明）
// → BestWindowFinder.find(rebuilt, tideRates=null, horizonDays, validHoursPresent=rebuilt.size)
//   （读侧无 dh 序列，tideRates=null = 内陆语义跳过同周期去重，§6.6 步骤 4 口径；跨点聚合同此，登记已知口径）
// 恒等对拍锁测试：同一输入现算落库 → 读侧重建 → find 输出逐字段相等（满足「Top10 与详情页/库内恒等」退出标准）
```

- UI Agent 编码边界（面向上述冻结签名）：本点 Top10（F-3.5）在 `:feature:detail` 派生层**现算**（cachedDetail slices 同源 → 恒等天然成立）；dh 序列 = `seaLevelHeightMslM` 一阶差分（纯函数放派生层，JVM 可测）。跨点 Top10（F-2.6）与 F-2.5 列表摘要走 `topWindows`（无网可读）。
- UI 红线沿用：Tab 标题只走 `WaterBodyTabTitles.titlesInTabOrder()`；禁嵌套同向 LazyColumn；预设解析只走 `resolvePreset`；UI 逻辑先抽纯函数再进 Composable。
- 现场校潮面板（§6.2b 规则 5）：潮 Tab Sheet 记录观测时刻 → `TideCalibrator.fitManual`（≥2 条才启用，±6h 匹配窗）；观测记录 Session 级存 ViewModel，持久化（schema 决策）登记 M6。
- 验证命令同 §2（JAVA_HOME/`.tools`/subst T:）；沙箱禁新增依赖（maven 不可达）。

## 并行分工（M6 进行中，2026-11-09 起）

> 执行模式沿用：负责人批次 0 落契约（Models.kt 只增已由负责人落地）→ 批次 1（数据/算法）→ 负责人全量验证 → 批次 2（UI）→ 验证 → 批次 3（`:app` 组装，负责人）。串行执行避免 Gradle 锁冲突。

| Agent | 模块/范围 | 禁区 |
|---|---|---|
| 负责人（批次 0/3） | Models.kt 只增、CONTRACT/台账、`:app`（新建：导航宿主/DI/准星收藏 OPT-M5-07/签名打包/CHG-12④⑤ 接线清单/FO-25/README）、集成验收 | — |
| 数据/算法 Agent（批次 1） | `:core:domain`（OPT-M5-04/D09/FO-15/OPT-19/OPT-05）、`:core:data`（OPT-M5-02/Room v3→v4/D13/tzOffsetSec 落库映射/URL 单点收敛） | 不碰 `:core:ui`/`:feature:*`/根 build 与 settings.gradle.kts；Models.kt 只读 |
| UI Agent（批次 2） | `:core:ui`（D04/TideSpotTheme 深色/TideSpotStrings 文案层）、`:feature:detail`（OPT-M5-05/D11/D12/D14）、`:feature:favorites`（OPT-M5-03/OPT-M5-07 UI 侧收口）、`:feature:settings`（新建，settings.gradle.kts 仅加 include） | 不碰 `:core:domain`/`:core:data` 源码（Models.kt 只读）；不改 `:feature:map` |

### M6 子契约（两 Agent 共同遵守；台账 CHG-16）

```
// ── Models.kt（只增；批次 0 已由负责人落地，Agent 只读）──
enum class VetoCode { THUNDERSTORM, FISHABILITY_DECAY }        // 机器可读否决码（FO-01/OPT-M5-04）
FishScoreResult 尾增 val vetoCodes: List<VetoCode> = emptyList()
FavoriteSpot 尾增 val tzOffsetSec: Int? = null                 // 选点时区偏移秒（OPT-M5-03/CHG-12④b）

// ── :core:domain（数据/算法 Agent）──
1) OPT-M5-04/FO-01：VetoEvaluator.Outcome 构造器尾增 codes: List<VetoCode> = emptyList()
   （THUNDERSTORM_REASON 触发处同步 add VetoCode.THUNDERSTORM；V<0.75 衰减明细处 add FISHABILITY_DECAY）。
   FishScoreCalculator 组装 FishScoreResult 透传 codes。
   BestWindowFinder.kt:132 改 r.vetoCodes.contains(VetoCode.THUNDERSTORM)（禁 contains("雷暴")——文案改后筛选不得漂移）。
   锁测试：① codes 与 reasons 同步产生/同步为空；② finder 只认 codes——把 THUNDERSTORM_REASON 文案改任意串，窗口筛选行为不变。
2) D09：月龄/照明/中天三项原始输入进 FactorScore.detail（§6.2d/e/f；sub/weight 不变，纯增 detail 文本）。
3) FO-15 死代码消化：fromLakeDaily/blendManualObservation/displayScore 等全仓 grep 零引用后删除（删除即契约变更，台账 CHG-16 留证）；有引用则保留并注明。
4) OPT-19：8 物种预设库 `core/domain/src/main/resources/species_presets.json`（speciesId/名称/权重覆盖/适用水体）；
   ScenePreset 尾增加载入口；resolvePreset 唯一解析入口不变（硬约束 6）；物种预设不改变既有场景预设行为。
5) OPT-05/FO-21：JVM 基准锁测试：240h×全因子（含 vetoes）全量计算取 3 次中位 <40ms（SRS 硬指标）；沙箱波动余量用预热吸收，断言仍按 <40ms。

// ── :core:data（数据/算法 Agent）──
6) Room v3→v4（MIGRATION_3_4；迁移锁测试对齐 MIGRATION_2_3 先例）：
   - 新表 manual_observation(id PK autoGenerate, spotId, observedAtEpochSec, createdAtEpochMs; Index(spotId), Index(spotId, observedAtEpochSec))
   - 新表 manual_tide_fit(spotId PK, lagHours, offsetM, gain, adopted, updatedAtEpochMs)
   - hourly_score 尾增列 veto_codes TEXT NOT NULL DEFAULT '[]'（VetoCode.name JSON 数组；读回映射空数组语义与 vetoReason NULL 对称）
   - FakeDaos 同步同语义
7) TideSpotRepository 只增 API（构造器签名不动）：
   - addTideObservation(spotId, observedAtEpochSec) / tideObservations(spotId): List<TideObservation>
   - saveManualTideFit(spotId, fit: TideCalibrator.ManualTideFit)（adopted=false 也登记，D12 对照需要） / manualTideFit(spotId): TideCalibrator.ManualTideFit?
   - OPT-M5-02 应用闭环：refreshSpotDetail/cachedDetail 命中 adopted fit（海洋点）→ 评分与 dh 派生管线使用校正序列
     M'(h)=gain·M(h−lag)+offset（现场校潮 gain=1.0）；SpotDetailResult.marine 保持**原始未校正**序列（UI 对照基准），
     SpotDetailResult 尾增 tideCorrection: TideCorrectionSummary?（lagHours/offsetM/gain/adopted；null=未应用）。
     落库 hourly_score 即校正后分数（读侧重建天然一致，无双口径）。
8) D13：评分计算切 Dispatchers.Default（构造器尾增 scoringDispatcher 可选参数，默认 Dispatchers.Default）；
   CancellationException 捕获后必须重抛；消除 refresh/cached 双次全量重算（单点最小刷新路径只算一次）。
9) FavoriteSpot.tzOffsetSec 落库映射：Mappers.toDomain/toEntity 映射 spot.tzOffsetSec（列已存在）；
   D07 patch 合并清单尾增 tzOffsetSec（编辑不冲掉派生列语义扩展，锁测试补一条）。
10) URL 单点收敛：ReverseGeocoder 构造器默认值改引用 TideSpotUrls.BIG_DATA_CLOUD_BASE/PHOTON_BASE（ReverseGeocoder.kt:43-44），
    MapScreen CARTO URL 同步复核收敛到 TideSpotUrls（若在 :feature:map 则只报告不改动，登记后由负责人批次 3 处理）。

// ── :core:ui / :feature:detail / :feature:favorites / :feature:settings（UI Agent）──
11) D04：ChartMath.niceStep 补 10 的负幂（0.1/0.01…）候选 + BAR 型图固定 0–100 轴；锁测试。
12) OPT-M5-05/D10：详情页数值区徽标接 FishScoreResult.confidence（Kind.fromConfidence）+ §6.7 权重降级「低置信」标注；
    禁硬编码 ESTIMATED；纯函数（confidence→Kind 映射已存在）+ 组装层接线 + 锁测试。
13) D11：免责声明补第 5 条「潮汐校正」+ 详情页常驻免责入口（Sheet 复用既有模式）；文案进 TideSpotStrings。
14) D12：潮 Tab dh/dt 副轴 + 高低潮标记（dh=一阶差分已有纯函数；高低潮 = 序列局部极值纯函数，JVM 可测）；
    校正对照：tideCorrection 非 null 时渲染「校正前/后」高低潮对照（lag 平移派生，纯函数）。
15) D14：跨零点「今天」自愈（DaySelector 以 spot 时区当天窗口而非固定偏移）/mapIndexed 误用清理/x 轴当地小时
    （tzOffsetSec 注入）/硬编码色值收敛主题 token/过时 KDoc 清理；锁测试。
16) OPT-M5-03：FavoritesSummaryFormatter/FavoritesScreen 渲染按 spot.tzOffsetSec → ZoneOffset.ofTotalSeconds
    （null 落系统默认并在渲染处注明口径）；跨点 Top10 时刻渲染同口径；锁测试。
17) OPT-M5-07 UI 侧收口：FavoritesViewModel.saveFavorite 尾增可选参数 lat/lon（existing 编辑路径不变）；
    新建路径必须携带坐标——lat==0.0&&lon==0.0 时拒绝落库（防御性，:app 准星主路径接线在批次 3）；
    FavoritesViewModel.kt:106 占位注释与占位值一并移除；锁测试。
18) :feature:settings 新建（build 脚本照抄 :feature:detail）：SettingsState/SettingsLogic 纯函数
    （单位制 SI/英制、详情天数 7|10 接 DaySelectorLogic.coerceDaysRange、用例/物种/权重入口、可选 API Key 占位、
    预取开关、许可清单数据、逆地理总开关、隐私逐源披露文案）+ SettingsScreen（薄组装）+ SettingsStore 接口
    （仅声明键值读写，:app 批次 3 以 SharedPreferences 实现）+ JVM 锁测试；不改任何既有模块源码。
19) 深色主题：:core:ui 尾增 TideSpotTheme（Material3 深色配色 token；硬编码色值收敛）；feature screens 接入。
20) FO-01 文案层（部分）：:core:ui 尾增 TideSpotStrings（Tab 标题/免责/否决/徽标等核心用户可见串唯一入口）；
    本期只迁核心串并保证零行为漂移，全量文案外移登记为 v1.1（雷暴判定已由 VetoCode 结构化兜底）。

// ── 批次 3（负责人）：:app ──
导航宿主（无 navigation 库，state 切换四页：地图/收藏/详情/设置）+ DI 手工组装（Room 含 MIGRATION_1_2/2_3/3_4、
OkHttp、各 Client、Repository 注入 hourlyScoreDao、Copernicus 凭据构建期从 local-secrets.properties 注入缺失降级）+
OPT-M5-07 准星一键收藏主路径 + CHG-12④⑤ 接线清单 + 自签名 keystore（keytool 本地生成）+ assembleDebug/release。
```

- 共同红线沿用 §冻结点 + HANDOVER §7 全部 11 条硬约束；UI 逻辑先抽纯函数再进 Composable；沙箱禁新增依赖（maplibre/compose 等已有 catalog 项可用）。
- 验证命令：同 HANDOVER §2（subst T: + `.tools\gradle-8.9`）；新增模块 include 行追加后全量跑。

## 并行分工（v1.1 P0-1 进行中，2026-11-10 起）

> 执行模式：负责人批次 0 契约（本节，已落盘）→ 批次 A 数据/凭据链 → 批次 B settings/detail UI → 批次 C i18n 骨架 + 图标 → 负责人集成验收。**串行执行**（同一 Gradle root 并行会锁冲突）。台账 CHG-25。

| 批次 | 范围 | 禁区 |
|---|---|---|
| 负责人（0/验收） | 本节契约、版本号 1.1/2、Manifest 安全项、i18n 规范、集成验收、APK 解包零凭据红线 | — |
| A 数据/凭据链 | `:core:data`（water/ 凭据存储+校验、`WaterBodyClassifier.setProbe`）、`:app`（AppGraph 运行期凭据接线、移除 BuildConfig 注入、`allowBackup=false`） | 不碰 `:feature:*`、不碰 `:core:domain`、不碰 i18n 资源 |
| B settings/detail UI | `:feature:settings`（凭据版块）、`:feature:detail`（首次引导卡） | 不碰 `:core:data`/`:core:domain` 源码；新文案一律进资源（批次 C 骨架 id） |
| C i18n + 图标 | `:core:ui`（`res/values{-en}/strings.xml` 骨架 + 17 条 GAP-05 文案 + bridge 规范）、`:app`（adaptive/vector 图标替换 `sym_def_app_icon`） | 不碰任何业务逻辑与主题色值（`ScoreBands`/`ConfidenceBadge`/`TideSpotTheme` 取值禁止修改） |

### v1.1 子契约 P0-1（批次 A/B/C 共同遵守）

```
// ── :core:data 新增（批次 A；新文件，不改既有签名）──
data class CopernicusCredential(val username: String, val password: String)

/** 运行期凭据存储（CHG-25①）：java.io 实现，纯 JVM 可测；文件 = Properties(username/password) */
interface CopernicusCredentialStore {
    suspend fun load(): CopernicusCredential?   // 不存在/损坏/空值 → null（不得抛）
    suspend fun save(credential: CopernicusCredential)
    suspend fun clear()                          // 幂等
}
class FileCopernicusCredentialStore(private val file: java.io.File) : CopernicusCredentialStore
// :app 接线：FileCopernicusCredentialStore(File(context.filesDir, "copernicus.properties"))
// 写入后尽力设为「仅属主可读」；Manifest allowBackup=false 防云备份带出

/** 凭据校验（批次 A）：一次 token 校验，四态分类（禁抛给 UI） */
sealed interface CredentialCheck {
    data object Missing : CredentialCheck                       // 本地未配置
    data object Valid : CredentialCheck                         // token 成功
    data class Invalid(val reason: String) : CredentialCheck    // 凭据错误（401/403 等）
    data class Unreachable(val reason: String) : CredentialCheck // 网络/超时/5xx
}
class CopernicusCredentialValidator(
    private val authFactory: (CopernicusCredential) -> CopernicusAuthClient =
        { CopernicusAuthClient(it.username, it.password) },
) {
    suspend fun check(credential: CopernicusCredential?): CredentialCheck
}

// ── :core:data WaterBodyClassifier（批次 A；只增，构造签名不动）──
fun setProbe(probe: InlandWaterProbe?)   // 内部 @Volatile 线程安全；null=探针禁用（UNKNOWN 降级语义不变，CHG-09）

// ── :app（批次 A）──
// 1) build.gradle.kts 删除 COPERNICUS_USERNAME/PASSWORD 两处 buildConfigField（BuildConfig 零凭据）；
// 2) AppGraph：运行期 store.load() → 非空则 OstiaLakeProbe(CopernicusAuthClient(u,p)) 注入 classifier.setProbe(...)；
//    暴露 suspend fun checkAndApply(credential, persist: Boolean): CredentialCheck
//    （校验通过 → save/clear + setProbe 即时生效，无需重启，GWT「改密码重启生效且旧值不残留」）；
// 3) AndroidManifest：android:allowBackup="false"。

// ── :feature:settings（批次 B；只增）──
// 注入接口（:app 实现，避免 settings→data 直连扩散）：
interface CopernicusCredentialActions {
    suspend fun check(username: String, password: String): CredentialCheck
    suspend fun save(username: String, password: String): CredentialCheck  // 内部先 validate 再落库
    suspend fun clear()
    suspend fun isConfigured(): Boolean
}
// SettingsState 尾增 copernicusConfigured: Boolean = false；
// SettingsLogic 尾增纯函数：表单校验（非空/trim/长度上限）+ 状态机 Idle→Checking→(Valid|Invalid|Unreachable)；
// SettingsScreen 增版块「数据源凭据 · Copernicus」：账号/密码（密码可见性切换）、校验并保存、清除、注册链接、隐私说明
//   （仅存本机私有目录、不上传、未配置时水质走模型估算）。

// ── :feature:detail（批次 B；只增）──
// 首次引导卡（内陆点 + 水质 Tab + !isConfigured + 未跳过）：InlandCredentialGuideCard，
// 含注册链接/3 步说明/免责，「跳过」标记经注入接口持久化（一次后不再显示）。

// ── :core:ui + :app（批次 C）──
// 1) i18n 骨架：core/ui/src/main/res/values/strings.xml（中文默认）+ values-en/strings.xml（英文初稿），
//    key 前缀 ts_；17 条 GAP-05 定稿文案 + P0-1 新增串（引导卡/凭据版块）全部入资源；
//    规范：新增用户可见文案禁新增 object 常量、禁 Screen 硬编码；Composable 侧 stringResource(R.string.ts_*)；
//    存量 TideSpotStrings object（中文）本期不动（P2 全量外移，零行为漂移）。
// 2) APP 图标（GAP-01）：design-handoff/icon/*.svg → app/src/main/res：
//    drawable/ic_launcher_foreground.xml（vector）+ values/ic_launcher_background.xml +
//    mipmap-anydpi-v26/ic_launcher.xml(adaptive, foreground/background) + ic_launcher_round.xml +
//    drawable/ic_launcher_monochrome.xml（Android 13 主题图标，纯白剪影）；Manifest android:icon/roundIcon=@mipmap/ic_launcher。
```

- 共同红线沿用 §冻结点 + HANDOVER §7 十一条；**新增**：凭据禁入 BuildConfig/源码/资源/日志（APK 解包零命中为退出标准）；`allowBackup=false`；图标不改主题色值单源。
- 验证命令同 HANDOVER §2（subst T: + `.tools\gradle-8.9` + verify.ps1）；批次串行。

## 冻结点

1. `core/domain/src/main/kotlin/com/tidespot/core/domain/model/Models.kt`：共享模型。扩展=只新增，禁改名/删除。
2. 包名根：`com.tidespot.core.domain` / `com.tidespot.core.data`。
3. 单位：内部 SI（°C / km/h / hPa / m / epoch 秒），换算只在 UI 层（F-4.4）。
4. null 语义：源缺值=null，禁止 0 填充（§5.0）。
5. 网络并发 ≤4、整窗 240h 一次入库（CHG-03）、UA=`TideSpot/{version} (Android; single-user device; manual tap only)`。

## 构建

- Gradle 未入 PATH：使用 `D:\tools\gradle-8.9\bin\gradle.bat`
- 验证命令：`:core:domain` → `gradle.bat :core:domain:test`；`:core:data` → `gradle.bat :core:data:test`
- 同一 root 并行构建遇锁（Lock timeout）请等待 60 s 重试；最终验证由交付负责人统一执行。


## v1.1 子契约 P1（2026-11-10，CHG-28）

> 全部为「尾部可选参数 / 新增方法」，既有调用零改动（**契约只增不改**）；`Models.kt` 本期未改动。
> 同步登记见 `REQUIREMENTS_REGISTER.md` CHG-28 与 `PROGRESS_TRACKER.md` §7。

1. `BestWindowFinder.find(...)` 尾增 `manualTideLagHours: Int? = null`（null = 无校正；仅「注意事项」追加一句，不改候选/rank/去重/时间域）。
2. `BestWindowFinder.manualCalibrationWarning(lagHours: Int): String` 新增（校潮文案唯一入口）。
3. `TideCalibrator.fitManual(...)` 尾增 `observationTypes: List<TideObservationType?> = emptyList()`、`modelMarkersByType: Map<TideObservationType, List<Long>> = emptyMap()`；新增枚举 `TideObservationType`（HIGH / LOW / RISING_START / FALLING_START，只增）。
4. `TideSpotRepository`：`addTideObservation(..., type: TideObservationType? = null)` 尾增；`tideObservations` 读回 `TideObservation.kind/type`（历史 `TIDE_OBSERVATION` 兼容）；新增 `lastFetchedMillis(spotId): Map<DataSource, Long>`。
5. `TopWindowDerivations`：新增 `tideMarkers(marine)`、`manualFitTyped(observations, marine)`；`top10(...)` 尾增 `manualTideLagHours`。
6. `TideSpotUrls`：新增 `CARTO_VOYAGER_STYLE_URL`（默认彩色）/ `CARTO_DARK_MATTER_STYLE_URL`（URL 仍单点收敛）。
7. UI 组装面（均有默认值，旧调用零改动）：`MapScreen` 尾增 `styleUrl` / `initialCameraTarget` / `floatingActions`；`FavoritesScreen` 尾增 `openTop10OnStart` / `onTop10Opened`；`FavoritesViewModel.load(showLoading)` 尾增；`SettingsScreen` 尾增 `onMapStyleChange`；`AppNavState` 尾增 `sourcePage`、`AppNavEvent.OpenDetail` 尾增 `source`（默认 FAVORITES）、`AppNavEvent.BackFromDetail` 语义改为「回来源页」。
8. `TideObservation`（data 层门面类型，非 `Models.kt` 冻结点）尾增 `kind: String = TIDE_OBSERVATION` 与派生 `type`。
9. `MapStyle` 枚举 + `SettingsKeys.MAP_STYLE` 新增（设置持久化键，新增不覆盖）。
10. Manifest 新增 `ACCESS_NETWORK_STATE`（预取联网判定；飞行模式零请求）。

## v1.1.1 子契约（2026-11-10，CHG-35）

> 全部为「尾部可选参数 / 新增方法 / 新增只读字段」，既有调用零改动（**契约只增不改**）；`Models.kt` 本期未改动。

1. `TideSpotRepository.probeMarineHasValue(lat: Double, lon: Double): Boolean?` 新增——海况探测（`true` 有海况 / `false` 无 / `null` 未知；失败不编造）。
2. `SpotDetailResult`（data 层门面，非 `Models.kt` 冻结点）尾增 `effectiveWaterBody: WaterBodyType? = null`——自愈后的真实水体；UI 取 `result.effectiveWaterBody ?: spot.waterBody` 作为标题与潮汛 chip 的类型来源。
3. `WaterBodyClassifier.JUDGE_VERSION: Int = 2` 新增；判定缓存键 = `CacheKeys.spotKey(lat, lon) + "#v${JUDGE_VERSION}"`（**改判据必须升版本号**；旧行不复用、不迁移）。
4. `DataWaterBodyResolver` 构造器尾增可选 `marineHasValue: (suspend (Double, Double) -> Boolean?)? = null`（默认 null = 旧行为走探针路径）。
5. `CrosshairStateMachine.onMapMoved(point: GeoPoint, fromUserGesture: Boolean = false)` 尾增参数：`Idle + true → Preview`；默认 false 保持旧语义（程序动画不建预览）。
6. 版本：`versionCode=3 / versionName=1.1.1`（非契约项，随 CHG-35 出包；APK `潮点TideSpot-v1.1.1.apk`）。
## v1.1 子契约 P2（2026-11-10，CHG-37）

> 口径遵循 **契约只增不改**：`Models.kt` 仅尾增可选字段；Room **v5→v6** 只加列；既有方法签名零改动；
> 新增 DAO 方法/仓储方法均为新增（不复用旧语义）。客户裁定：**D16** 长按=多选、**D17** 缩略图同底图口径、
> **D18** 预设稳定 id、**D19** 删除级联清理、**D20** 版本升 1.2.0。

1. `Models.kt` `FavoriteSpot` 尾增：
   - `groupName: String? = null`（OPT-V11-16；NULL = 未分组，禁空串语义）；
   - `elevation: Double? = null`（OPT-V11-15 信息卡片只读透出；null = 未回写 → `—`）。
2. Room **v5→v6**（`MIGRATION_5_6`）：`ALTER TABLE spot ADD COLUMN groupName TEXT`（可空、无 DEFAULT；列名与实体属性逐字一致）。
3. `SpotDao` 新增（批量操作支撑；定向 UPDATE 不冲派生列，D07 patch 语义保持）：
   `getByIds` / `deleteByIds` / `updateGroupName(ids, groupName?)` / `updateTagsJson(id, tagsJson)`。
4. `HourlyMetricDao` / `HourlyScoreDao` / `FetchLogDao` / `ManualObservationDao` / `ManualTideFitDao` 各新增 `deleteForSpots(spotIds)`（批量级联清理）。
5. `TideSpotRepository` 构造器尾增可选 `db: TideSpotDatabase? = null`（单事务入口；null = JVM 测试顺序执行）；新增方法：
   - `deleteFavorites(ids: List<Long>)`（单事务，级联 6 张表；`deleteFavorite(id)` 改为复用本方法——**行为变更登记 D19**）；
   - `updateFavoritesGroup(ids, groupName: String?)`（空白归一为 NULL）；
   - `updateFavoritesTags(tagsBySpotId: Map<Long, List<String>>)`（只改 `tagsJson`，合并语义在 feature 层纯函数）。
6. `TideSpotUrls` 新增 `CARTO_RASTER_TILES_BASE` / `THUMBNAIL_ZOOM` / `cartoStaticTile(lat, lon, zoom)`（URL 单点不变）；
   `TileMath`（core:data）为 Web Mercator 纯函数；`MapThumbnailStore : ThumbnailSource`（Android 侧，30 d 磁盘 + 内存 LRU + 并发 3）。
7. Feature 层新增纯函数（JVM 锁）：`FavoritesSortLogic.SortOrder.RECOMMENDED`（尾增 `rankBySpotId` 参数）、
   `FavoritesBatchLogic`、`FavoritesGroupLogic`、`FavoritesInfoCardLogic`；
   `FavoritesViewModel` 构造器尾增可选 `thumbnailSource: ThumbnailSource? = null`，并新增多选/批量/分组/信息卡方法。
8. `core:ui` `Format.coordinate(v)` 新增（6 位小数；`value()` 的 digits 上限 3 不变，避免既有行为漂移）。
9. 版本：`versionCode=4 / versionName=1.2.0`（APK `潮点TideSpot-v1.2.0.apk`）。

## V1.2 需求池子契约（2026-11-11，CHG-57，v1.16.0）

> 口径：**既有公共签名零改动**，新增一律为「尾增可选参数 / 新增方法 / 新增只读字段」；
> 唯一需同步改动的类型是 `TideSpotStrings`（接口新增成员，3 个实现同加）与 `DayChip`（新增两字段，
> 仅 `DaySelectorLogic.chips` 一个构造点）。
> 需求依据：`OPT_REQUIREMENTS_V11.md` §7 OPT-V12-01/02/03/04/07。

1. `:core:data` `TokenBucket`：
   - 新增 `enum class Priority { USER, BACKGROUND }`；
   - 尾增可选参数 `acquire(priority: Priority = Priority.BACKGROUND)`（默认 = 旧行为，零漂移）；
   - `tryAcquire()` 语义扩展：存在 `USER` 等待者时返回 false（后台预取主动让出，**不排队**）；
   - **总速率不变**（capacity/period），仅改变分配顺序。
2. `:feature:detail`：
   - `DetailViewModel` 构造器尾增可选 `networkAvailable: () -> Boolean = { true }`；
   - 新增 `userRefresh()`（`force=true` + 去重）、`clearRefreshFeedback()`；`retry()` 收口到同一语义；
   - `DetailUiState` 尾增 `refreshFeedback: RefreshFeedback? = null`、`lastRefreshAtMillis: Long? = null`；
   - 新增 `enum class RefreshFeedback { UPDATED, FAILED, OFFLINE }`（纯层不带文案）；
   - 新增 `internal class UserRefreshGate`（同点去重整任务门）。
3. `:feature:favorites`：
   - `FavoritesViewModel` 构造器尾增可选 `networkAvailable: () -> Boolean = { true }`；
   - 新增 `refreshAll(spots: List<FavoriteSpot>)`（非 force、并发 ≤2）、`cancelRefresh()`、`clearRefreshMessage()`；
   - `FavoritesUiState` 尾增 `refreshing / refreshDone / refreshTotal / refreshSummary / refreshOffline`；
   - 新增纯函数 `FavoritesRefreshLogic`（`Outcome` / `Summary` / `classify` / `summarize` / `targets`）
     与 `TagPresets`（`DEFAULT` / `isSelected` / `toggleRaw` / `selectedInRaw`）。
4. `:core:ui`：
   - `TideSpotStrings` 新增 `val dayTodayLabel: String`（`ZhStrings`/`EnStrings`/`ResStrings` 同步；资源键 `ts_day_today_label`）；
   - `DaySelectorLogic.DayChip` 新增 `topLabel: String` / `weekdayLabel: String`（`chipLabel` 保留为单行合并态）；
   - `DaySelector` 组件：chip 改两行（52dp/56dp），`infoSlot` 渲染在 chip 行**下方**（不再占同一行）。
5. `:feature:map`：
   - `MapScreen` 尾增可选参数：`favoriteStars: List<FavoriteStar> = emptyList()`、
     `onOpenFavorite: ((Long) -> Unit)? = null`、`onCameraTargetChanged: (GeoPoint) -> Unit = {}`；
   - 新增 `FavoriteStar(id, name, lat, lon, summary?)` 与 `FavoriteStarLayer`（`geoJson` / `starIdOf` /
     `coordinate` / `escape`，纯函数）；图层 id：`tidespot-favorite-stars` / `tidespot-favorite-stars-layer`；
   - **不改**既有手势契约：点击星标只消费命中（命中时返回 true），**不移动准星、不发起选点请求**；
     长按（F-1.4）与拖动 idle 逻辑零改动。
6. 版本：`versionCode=20 / versionName=1.16.0`（APK `潮点TideSpot-v1.16.0.apk`）。

---

## 底图源变更（2026-11-13，CHG-91，应急切换）

**背景**：CARTO（`basemaps.cartocdn.com` / `tiles.basemaps.cartocdn.com`，Fastly）在实测网络下 TCP 443 全部 10s 超时；
样式 JSON 尚可命中缓存，但矢量瓦片与 sprite 全失败 → **底图整片空白，定位/潮汐/收藏等于瓦片无关的功能正常**。
同网络 `tiles.openfreemap.org` 1.5s 可达、HTTP 200。

**变更**：`TideSpotUrls` 三个样式常量改指 OpenFreeMap（OpenStreetMap 数据、WGS84 与定位同坐标、免费无 Key）：

| 常量 | 值 |
|---|---|
| `BASEMAP_COLOR_STYLE_URL` | `https://tiles.openfreemap.org/styles/liberty` |
| `BASEMAP_PLAIN_STYLE_URL` | `https://tiles.openfreemap.org/styles/positron` |
| `BASEMAP_DARK_STYLE_URL` | `https://tiles.openfreemap.org/styles/dark` |

**不变（红线）**：
- `MapStyle` 三个**枚举名**（`VOYAGER` / `POSITRON` / `DARK_MATTER`）是持久化值（`settings.mapStyle` 存 `name`），
  **禁止改名**，否则老用户配置失配回落默认；
- 三选语义、「未改过跟随明暗自动切、改过则用户选择优先」的 `resolveMapStyle` 逻辑、attribution 控件行为均不变；
- 坐标系仍 WGS84（OpenFreeMap 为 OSM 数据，无 GCJ-02 偏移，鱼情点位不漂移）。

**署名**：`ts_map_attribution` 改为「OpenStreetMap 贡献者 · OpenFreeMap」。

**回滚**：只把三个常量改回 CARTO URL 即可，枚举与所有调用方零改动。
