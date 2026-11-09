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
