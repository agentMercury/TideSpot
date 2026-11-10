package com.tidespot.core.domain.model

/**
 * 模块间共享契约（冻结）。两个并行开发的模块（:core:domain 算法核 / :core:data 网络存储）
 * 均以本文件为对齐点。扩展规则：只能【新增】类型或字段，禁止重命名/删除既有公开符号。
 * 单位约定：内部一律 SI —— 温度 °C、风速 km/h（与 Open-Meteo 源一致）、距离 m、气压 hPa、
 * 时间 epoch 秒（UTC）。UI 层才做单位换算（SRS F-4.4）。
 */

data class Coordinate(val lat: Double, val lon: Double)

/** 水体类型（SRS §6.9 判定输出）。M0 结论：不依赖 Overpass，判定树以前两级为主。CHG-09②：追加 UNKNOWN（只增合规）= §6.9 第 4 级「未知」，探针失败/未注入时返回，UI 以「待判定」徽标呈现。 */
enum class WaterBodyType { OCEAN, NEARSHORE, INLAND_WATER, LAND, UNKNOWN }

/** 数值置信徽标（SRS §8.2，Q 来源分级）。 */
enum class Confidence { MEASURED, SATELLITE, MODEL, ESTIMATED, UNAVAILABLE }

/** 天气逐小时（SRS §5.1 契约映射；源字段缺失 = null，禁止当 0，SRS §5.0）。 */
data class HourlyWeather(
    val timeEpochSec: Long,
    val temperatureC: Double? = null,
    val dewPointC: Double? = null,
    val apparentC: Double? = null,
    val pressureMslHpa: Double? = null,
    val surfacePressureHpa: Double? = null,
    val cloudCoverPct: Double? = null,
    val cloudLowPct: Double? = null,
    val cloudMidPct: Double? = null,
    val cloudHighPct: Double? = null,
    val visibilityM: Double? = null,
    val precipMm: Double? = null,
    val rainMm: Double? = null,
    val showersMm: Double? = null,
    val snowMm: Double? = null,
    val weatherCode: Int? = null,
    val windSpeedKmh: Double? = null,
    val windDirDeg: Double? = null,
    val windGustKmh: Double? = null,
    val cape: Double? = null,
    val freezingLevelM: Double? = null,
)

/** 海洋逐小时（SRS §5.2 契约映射；内陆点全 null → 触发降级，SRS 附录 B8）。 */
data class HourlyMarine(
    val timeEpochSec: Long,
    val waveHeightM: Double? = null,
    val wavePeriodS: Double? = null,
    val waveDirectionDeg: Double? = null,
    val wavePeakPeriodS: Double? = null,
    val windWaveHeightM: Double? = null,
    val windWavePeriodS: Double? = null,
    val windWaveDirectionDeg: Double? = null,
    val swellWaveHeightM: Double? = null,
    val swellWavePeriodS: Double? = null,
    val swellWaveDirectionDeg: Double? = null,
    val secondarySwellHeightM: Double? = null,
    val tertiarySwellHeightM: Double? = null,
    val oceanCurrentVelocityKmh: Double? = null,
    val oceanCurrentDirectionDeg: Double? = null,
    val seaLevelHeightMslM: Double? = null,
    val seaSurfaceTempC: Double? = null,
) {
    /** 全 null 判定 = 内陆无海况（§6.9 降级触发条件，禁止显示"浪高 0 m"）。 */
    val isAllNull: Boolean
        get() = waveHeightM == null && wavePeriodS == null && seaLevelHeightMslM == null &&
            oceanCurrentVelocityKmh == null && seaSurfaceTempC == null
}

/** 水质点位样本（§5.3 分层来源 + §6.1.2/§6.2a/§6.2j 本地推导兜底，M0 结论：中国境内默认走推导）。 */
data class WaterQualitySample(
    val timeEpochSec: Long,
    val waterTempC: Double? = null,
    val doPercent: Double? = null,
    val doMgL: Double? = null,
    val turbidityProxy: Double? = null,
    val chlorophyllProxy: Double? = null,
    val confidence: Confidence = Confidence.ESTIMATED,
)

/** 单因子得分明细（F-3.4 因子分解视图）。 */
data class FactorScore(
    val id: String,
    val sub: Double,        // 0–1 子分
    val weight: Double,     // 权重
    val detail: String? = null,
)

/**
 * 机器可读否决码（M6/CHG-16①，OPT-M5-04/FO-01）：窗口筛选与 UI 判定只认 codes，
 * **禁止再 parse [FishScoreResult.vetoReasons] 显示文案**（文案随 i18n 可变，码恒定）。
 */
enum class VetoCode {
    /** 雷暴硬否决（wmoCode ≥95，VetoEvaluator ×0.05，§6.5 唯一安全否决）。 */
    THUNDERSTORM,
    /** 可作钓性衰减明细（VetoEvaluator V<0.75 时的合成衰减项）。 */
    FISHABILITY_DECAY,
}

/** 钓鱼指数逐小时结果（§6 算法输出；total 0–100 整数）。 */
data class FishScoreResult(
    val timeEpochSec: Long,
    val total: Int,
    val factors: List<FactorScore>,
    /**
     * 硬否决/衰减原因（雷暴/VetoContinuity 等，§6.5）；无否决 = **空列表**，判定用 `isEmpty()`。
     * CHG-08 ④（反向裁决，取代 CHG-07 的可空修改）：非空 `List<String>` + `isEmpty()`
     * 是 Kotlin 明确安全的惯用法（:379 的"判真"坑是 JS truthiness 问题，不会在 Kotlin 复现）；
     * 禁止把本字段改成可空或用 `== null` 判定——如需区分「无否决 vs 未计算」走新增字段
     * （冻结点契约允许只增）。本文件仅此一处类型修改。
     */
    val vetoReasons: List<String>,
    val confidence: Confidence,
    /**
     * M6（CHG-16①/OPT-M5-04）：机器可读否决码，与 [vetoReasons] 同步产生/同步为空；
     * 消费侧（BestWindowFinder/组装层）禁再 contains 文案（FO-01：文案 i18n 后子串判定失效）。
     */
    val vetoCodes: List<VetoCode> = emptyList(),
)

/** 最佳窗口（§6.6 Top10 条目）。 */
data class BestWindow(
    val startEpochSec: Long,
    val peakEpochSec: Long,
    val durationHours: Int,
    val peakScore: Int,
    val tier: String,
    val topFactors: List<String>,
    val warnings: List<String>,
    /** FO-14（M5）：窗口 rank（0.62·peak+0.23·mean+1.4·min(len,4)），跨点聚合全局排序依据（默认 0 = 未计算，登记 CHG-14 允许只增）。 */
    val rank: Double = 0.0,
    /**
     * OPT-V11-25（D11/CHG-27）：核心段起点（峰值 −2h，夹在原窗内）；原窗跨度 ≤4h 时 = [startEpochSec]。
     * 0 = 未派生（向后兼容；跨点聚合/时段筛选一律用原窗 [startEpochSec]/[peakEpochSec] 相交判定）。
     */
    val coreStartEpochSec: Long = 0,
    /** OPT-V11-25：核心段终点（峰值 +2h，夹在原窗内）；核心段跨度恒 ≤4h。 */
    val coreEndEpochSec: Long = 0,
    /** OPT-V11-25：利好延续时刻（= 原窗尾，仅当 coreEnd < 原窗尾；UI 附注「利好持续至 HH:mm」）；null = 核心即原窗。 */
    val truncatedUntilEpochSec: Long? = null,
)

/** 收藏点（F-2）。 */
data class FavoriteSpot(
    val id: Long,
    val name: String,
    val lat: Double,
    val lon: Double,
    val memo: String? = null,
    val preset: String? = null,      // 淡水路亚/海水岸边/淡水台钓
    val tags: List<String> = emptyList(),
    val waterBody: WaterBodyType = WaterBodyType.LAND,
    val createdAtEpochSec: Long = 0,
    /** M5（CHG-14）：最近查看时刻（epoch 秒；F-2.5 列表摘要排序依据，映射 spot 表 lastViewedAt 列）。 */
    val lastViewedAtEpochSec: Long = 0,
    /**
     * M6（CHG-16①/OPT-M5-03/CHG-12④b）：选点时区偏移秒（Open-Meteo timezone=auto 回写 spot.tzOffsetSec）。
     * 收藏列表/跨点 Top10 渲染按此换算 ZoneOffset；null = 未回写（落系统默认，UI 注明口径）。
     */
    val tzOffsetSec: Int? = null,
)

/** 逆地理名称（§5.8；M0 结论：Photon 唯一源 + 坐标文本兜底，Nominatim 移除）。 */
data class ReverseGeocode(
    val lat: Double,
    val lon: Double,
    val name: String,        // 可为 "29.600000, 119.038000" 兜底
    val locality: String? = null,
    val district: String? = null,
    val city: String? = null,
    val state: String? = null,
)

// ───────────────────────── M3 新增（CONTRACT「M3 子契约」；只增不改） ─────────────────────────

/** 逆地理结果来源（§5.8 四级链；RG-C 不接入 CHG-06；CACHE = spot_reverse_cache 命中）。 */
enum class ReverseGeoSource { RG_A, RG_B, RG_D, CACHE }

/**
 * 逆地理名称（§5.8 链产出）。信息条永不空白：全链失败落 RG-D（plusCode + 格式化坐标）。
 * 显示名优先级：RG-B 聚落名 > RG-A(locality|city) > RG-A(省) > RG-D plusCode；
 * 市/省后缀由 [subLabel] 承载（有则附）；全部名称经 T2S 归一，不混排简繁。
 */
data class NamedPlace(
    val lat: Double,
    val lon: Double,
    val displayName: String,
    /** 市/省 或海区名（信息条第二行）。 */
    val subLabel: String? = null,
    val plusCode: String? = null,
    /** 恒为 null（RG-C Overpass 不接入，CHG-06，保留契约备 v1.x）。 */
    val waterName: String? = null,
    val locality: String? = null,
    val city: String? = null,
    val admin1: String? = null,
    val source: ReverseGeoSource,
)

/** 正地理搜索候选（§5.9：Open-Meteo /v1/search language=zh；admin2+admin1 作副标题、字段保持分开）。 */
data class GeocodeCandidate(
    val name: String,
    val admin1: String? = null,
    val admin2: String? = null,
    val lat: Double,
    val lon: Double,
    val elevation: Double? = null,
    val timezone: String? = null,
)
