package com.tidespot.core.ui.strings

/**
 * 用户可见文案唯一入口（FO-01 / OPT-V11-13 全量外移）。
 *
 * 三层结构：
 * - **本接口**：跨 Compose / 纯 Kotlin 逻辑共用的取词契约（[com.tidespot.core.ui.titles.WaterBodyTabTitles]、
 *   [com.tidespot.core.ui.format.RelativeTimeFormatter] 等纯函数依赖它）；
 * - **[ResStrings]**：Android 运行期实现，全部值来自 `values/strings.xml` / `values-en/strings.xml`
 *   （切系统语言即切文案，N-8；单测/纯函数用 [ZhStrings] / [EnStrings]）；
 * - **[ComposeTideSpotStrings]**：Composable 侧便捷取词（`LocalContext.current.resources` + 配置变更感知）。
 *
 * 红线：`src/main` 下除 `strings/`（文案提供者，资源镜像）与 `*DomainCopy*`（:core:domain 纯 Kotlin 文案提供者）
 * 外，**禁止出现中文字符串字面量**；KDoc/行注释除外。
 */
interface TideSpotStrings {

    // ────────────── Tab 标题（SRS §8.1；内陆/陆地降级 F-1.6 / F-1.7） ──────────────
    val tabWave: String
    val tabWeather: String
    val tabScore: String
    val tabWaterQuality: String
    val tabTide: String
    val tabWaveInland: String
    val tabTideInland: String
    val tabWaveLand: String
    val tabWaterQualityLand: String
    val tabTideLand: String
    val noticeLand: String
    val noticeUnknown: String

    // ────────────── 免责声明（SRS §9.6 / D11：恒 5 条） ──────────────
    val disclaimerTitle: String
    val disclaimerEntry: String
    val disclaimerWaveTide: String
    val disclaimerWeather: String
    val disclaimerScore: String
    val disclaimerWaterQuality: String
    val disclaimerTideCorrection: String

    /** 免责全文（Sheet 渲染顺序 1..5；D11 锁：恒 5 条）。 */
    fun disclaimerItems(): List<String> = listOf(
        disclaimerWaveTide,
        disclaimerWeather,
        disclaimerScore,
        disclaimerWaterQuality,
        disclaimerTideCorrection,
    )

    // ────────────── 置信徽标（SRS §8.2-4） ──────────────
    val badgeMeasured: String
    val badgeSatellite: String
    val badgeModel: String
    val badgeEstimated: String
    val badgeUncalibrated: String
    val badgeLowConfidence: String

    /** TalkBack 读法模板（N-7）。 */
    fun confidenceDescription(label: String): String

    // ────────────── 数据来源署名（F-3.7） ──────────────
    val modelAttribution: String

    // ────────────── 水体类型徽标（OPT-V11-15） ──────────────
    val waterBodyOcean: String
    val waterBodyNearshore: String
    val waterBodyInland: String
    val waterBodyLand: String
    val waterBodyUnknown: String

    // ────────────── 收藏信息卡片（OPT-V11-15；缺值 —） ──────────────
    val infoCoordinatePrefix: String
    val infoElevationPrefix: String
    val infoLastFetchedPrefix: String
    val infoGroupPrefix: String
    val infoWaterBodyPrefix: String
    val infoAccuracyNote: String

    // ────────────── 因子名（SRS §6.4） ──────────────
    val factorOxygen: String
    val factorPressure: String
    val factorSolunar: String
    val factorTide: String
    val factorWind: String
    val factorWeather: String
    val factorDaypart: String
    val factorTempMatch: String
    val factorComfort: String
    val factorWaterQuality: String

    // ────────────── 因子分解视图（F-3.4） ──────────────
    val factorBreakdownTitle: String
    val factorDecay: String
    val factorRawInput: String
    fun factorRow(name: String, subText: String, weightText: String): String
    fun factorMultiplier(multiplierText: String, reason: String): String

    // ────────────── 日期 / 相对时间（N-8） ──────────────
    val timePattern: String
    val weekdayMon: String
    val weekdayTue: String
    val weekdayWed: String
    val weekdayThu: String
    val weekdayFri: String
    val weekdaySat: String
    val weekdaySun: String
    fun today(clock: String): String
    fun tomorrow(clock: String): String

    // ────────────── 逐小时图表 / 日选择器 TalkBack ──────────────
    fun chartHourTooltipSuffix(hour: Int, tooltip: String): String
    fun chartHourDescription(unit: String, points: Int): String
    fun chartMissingSuffix(missing: Int): String
    val chartScoreDescription: String
    fun chartWindowSuffix(hours: Int): String
    fun chartHint(base: String, selected: String): String
    fun dayToday(weekday: String, date: String): String
    fun dayFuture(offsetDays: Int, weekday: String, date: String): String
    fun dayShortToday(weekday: String): String
    val noDataHour: String
    val deviceZoneNote: String

    /** 地图信息条：无名称兜底（F-1.3 永不空白）与拖动预览提示。 */
    val unnamedWater: String
    val previewHint: String

    /** 水质实测指标名（OPT-V11-02 Sheet / 校验提示）。 */
    val waterTemp: String
    val dissolvedOxygen: String
    val turbidity: String

    /** 批次二：明细 / 无障碍 / 时间格式化（纯逻辑层取词）。 */
    fun durationHours(hours: Int): String
    fun windowLine(start: String, peak: String, duration: String): String
    fun peakScoreLine(score: Int, tier: String): String
    val burstBadge: String
    fun sustainedUntil(time: String): String
    fun calibrationEnabled(lagHours: Int): String
    val calibrationDisabled: String
    val rawWind: String
    val rawGust: String
    val rawRain: String
    val rawCloud: String
    val rawWave: String
    val rawTideLevel: String
    val rawCurrent: String
    val rawTurbidity: String
    val rawChlorophyll: String
    fun hourWave(hour: Int, wave: String, period: String, direction: String): String
    fun hourWeather(hour: Int, temp: String, wind: String, pressure: String): String
    fun hourScore(hour: Int, score: Int): String
    fun hourScoreMissing(hour: Int): String
    fun hourWater(hour: Int, temp: String, doPotential: String): String
    fun hourTide(hour: Int, level: String, rate: String, current: String): String
    val hourMissing: String
    val sourceUnavailable: String
    fun dataUnusable(ageHours: Int): String
    val manualSame: String
    fun manualLater(delta: String): String
    fun manualEarlier(delta: String): String
    fun manualCompare(type: String, model: String, delta: String): String
    val manualAnyType: String
    fun manualLine(type: String, time: String): String
    val manualTimeFormatError: String
    fun manualOutOfRange(maxHours: Int): String

    /** 潮汐图派生（高低潮 + 校正对照）。 */
    fun tideBefore(before: String): String
    fun tideAfter(lagHours: Int, after: String): String
    fun tideHigh(times: String): String
    fun tideLow(times: String): String

    /** 明细缺省：本点无来源 / 评分单位。 */
    val noSourceData: String
    val unitScore: String

    /** 收藏页纯逻辑文案（批次二）。 */
    val ungrouped: String
    val summaryNotLoaded: String
    val staleLabel: String
    val expiredLabel: String
    fun dataPrefix(date: String, time: String): String
    val presetFreshLure: String
    val presetSeaShore: String
    val presetFreshBait: String
    val errBatchDelete: String
    val errBatchTag: String
    val errBatchGroup: String
    val errNoCoordinate: String
    fun summaryLine(time: String, score: Int, tier: String): String
    fun batchDeleteConfirm(count: Int): String

    // ────────────── 钓鱼指数 5 档色带文字（SRS §6.3；色值为冻结规格） ──────────────
    val bandExplosive: String
    val bandGreat: String
    val bandOk: String
    val bandPoor: String
    val bandBad: String
}