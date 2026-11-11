package com.tidespot.feature.detail.detail

import com.tidespot.core.data.dto.WeatherDailyDto
import com.tidespot.core.data.repo.DataSource
import com.tidespot.core.data.repo.HourSlice
import com.tidespot.core.data.repo.SpotDetailResult
import com.tidespot.core.data.repo.Ttl
import com.tidespot.core.domain.FishScoreCalculator
import com.tidespot.core.domain.algorithm.ScenePreset
import com.tidespot.core.domain.algorithm.SubScores
import com.tidespot.core.domain.algorithm.VetoEvaluator
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather
import com.tidespot.core.domain.model.WaterQualitySample
import com.tidespot.core.domain.water.DoPhysics
import com.tidespot.core.domain.water.WaterTempFusion
import com.tidespot.core.ui.breakdown.VetoRow
import com.tidespot.core.ui.format.Format
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.strings.ZhStrings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * :feature:detail 纯派生层（全部为纯 Kotlin 函数，可直接 JVM 单测）。
 *
 * 红线自我约束：
 * - 缺值 = null 禁止 0 填充（§5.0/§6.7）——所有派生对 null 的处理是「跳过/断开/—」；
 * - 单位换算仅在 UI 层（F-4.4），内部一律 SI（°C/km/h/hPa/m/epoch 秒）；
 * - 不引入任何网络请求（F-4.1 数据面只经 TideSpotRepository）。
 */
object Derivations {

    // ────────────────────────── F-3.8 当地墙钟与日切分 ──────────────────────────

    /** epoch 秒 → 选点当地 LocalDate（「今天」边界按 zone 判定，N-9：比较/排序用 epoch）。 */
    fun localDate(epochSec: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochSecond(epochSec).atZone(zone).toLocalDate()

    /** epoch 秒 → 选点当地小时 0–23（小时轴标签）。 */
    fun hourLocal(epochSec: Long, zone: ZoneId): Int =
        Instant.ofEpochSecond(epochSec).atZone(zone).hour

    /**
     * 某日 24 点切片（F-3.1：选"第 N 天"→ 该日 00:00–23:00 当地小时；null 保真；小时轴用当地墙钟）。
     * 按**当地 LocalDate 匹配**（非下标——天气/海洋窗口起点不同，§5.0）。
     */
    fun slicesForDay(slices: List<HourSlice>, dayIndex: Int, zone: ZoneId, today: LocalDate): List<HourSlice> {
        val target = today.plusDays(dayIndex.toLong())
        return slices.filter { localDate(it.timeEpochSec, zone) == target }.sortedBy { it.timeEpochSec }
    }

    /** 显式 tzOffsetHours（CONTRACT：调用侧显式传值，禁再依赖默认 8.0；随 epoch 逐小时取该刻度偏移）。 */
    fun tzOffsetHours(epochSec: Long, zone: ZoneId): Double =
        zone.rules.getOffset(Instant.ofEpochSecond(epochSec)).totalSeconds / 3600.0

    // ────────────────────────── F-3.2③ 当日摘要（极值 + 时刻） ──────────────────────────

    data class Summary(
        val max: Double,
        val maxEpochSec: Long,
        val min: Double,
        val minEpochSec: Long,
    )

    /** 当日摘要：非 null 值的极值与时刻；全 null（或空）→ null（禁补 0 假摘要）。 */
    fun summary(pairs: List<Pair<Long, Double?>>): Summary? {
        val valid = pairs.mapNotNull { (t, v) -> v?.let { t to it } }
        if (valid.isEmpty()) return null
        val max = valid.maxBy { it.second }
        val min = valid.minBy { it.second }
        return Summary(max.second, max.first, min.second, min.first)
    }

    // ────────────────────────── 潮流派生（§5.4） ──────────────────────────

    /**
     * 涨落率 dh/dt（m/h）：相邻整点潮位差分（§5.4「派生」在展示层做，不改 :core:data/domain）。
     * 任一侧缺测或间隔非整点 → null（禁补 0）；序列尾部最后一小时无下一时刻 → null。
     */
    fun tideRates(marine: List<HourlyMarine>): Map<Long, Double?> {
        val sorted = marine.sortedBy { it.timeEpochSec }
        val out = LinkedHashMap<Long, Double?>(sorted.size)
        for (i in sorted.indices) {
            val cur = sorted[i]
            val prev = sorted.getOrNull(i - 1)
            val rate = if (prev != null &&
                prev.seaLevelHeightMslM != null && cur.seaLevelHeightMslM != null &&
                cur.timeEpochSec - prev.timeEpochSec == 3600L
            ) {
                (cur.seaLevelHeightMslM!! - prev.seaLevelHeightMslM!!) / 1.0 // Δt=3600s=1h → m/h
            } else {
                null
            }
            out[cur.timeEpochSec] = rate
        }
        return out
    }

    // ────────────────────────── 气压趋势差分 / rain24h ──────────────────────────

    /** ΔP3 / ΔP24（hPa）：t 与 t−hours 的海平面气压差；任一侧缺测 → null（§6.7 不硬填）。 */
    fun deltaPressure(byEpoch: Map<Long, HourlyWeather>, epochSec: Long, hours: Int): Double? {
        val now = byEpoch[epochSec]?.pressureMslHpa ?: return null
        val past = byEpoch[epochSec - hours * 3600L]?.pressureMslHpa ?: return null
        return now - past
    }

    /** 前 24h 降水累计（FO-15 rain24h 接线）；缺测小时按中性 0（WaterQualityProxy 缺失语义）。 */
    fun rain24h(byEpoch: Map<Long, HourlyWeather>, epochSec: Long): Double? {
        if (byEpoch.isEmpty()) return null
        var anyRow = false
        var sum = 0.0
        for (h in 1..24) {
            val w = byEpoch[epochSec - h * 3600L] ?: continue
            anyRow = true
            sum += w.precipMm ?: 0.0
        }
        return if (anyRow) sum else null
    }

    // ────────────────────────── 水质（§5.3 Q-兜底本地推导） ──────────────────────────

    /**
     * 水质逐小时本地推导（Repository 契约：M0 无 Copernicus 凭据 → 不发网络请求，由天气序列推导，
     * 徽标恒「估算」）：水温 Tw（§6.1 融合，海点 SST 优先）+ 溶氧潜力 DO%（§6.2a）+ 浊度代理 +
     * 叶绿素代理（§6.2j）。缺测项 null 保真（不补 0；pressure 缺 → DO 缺）。
     */
    fun qualitySeries(
        slices: List<HourSlice>,
        weatherByEpoch: Map<Long, HourlyWeather>,
        zone: ZoneId,
        salinityPpt: Double,
        /**
         * OPT-V11-02（CHG-27）：水温实测观测（epoch 秒 → 实测 ℃）。
         * ±6h 内命中的小时按 §6.1.3 融合 `Tw = 0.6·实测 + 0.4·模型`（模型缺值则取实测），
         * 且该小时 confidence = [com.tidespot.core.domain.model.Confidence.MEASURED]（徽标「实测」）。
         */
        waterTempObservations: List<Pair<Long, Double>> = emptyList(),
    ): List<WaterQualitySample> {
        if (weatherByEpoch.isEmpty()) return emptyList()
        val sortedWeather = weatherByEpoch.toSortedMap().values.toList()
        val airTemps = sortedWeather.map { it.temperatureC }
        val indexOfEpoch = sortedWeather.withIndex().associate { (i, w) -> w.timeEpochSec to i }
        return sortedWeather.map { w ->
            val i = indexOfEpoch.getValue(w.timeEpochSec)
            val sst = slices.firstOrNull { it.timeEpochSec == w.timeEpochSec }?.marine?.seaSurfaceTempC
            val twModel = WaterTempFusion.fuse(airTemps, i, w.temperatureC, sst)
            // OPT-V11-02：±6h 内最近一条实测参与融合（§6.1.3）
            val obs = nearestObservation(w.timeEpochSec, waterTempObservations)
            val tw = when {
                obs == null -> twModel
                twModel == null -> obs
                else -> 0.6 * obs + 0.4 * twModel
            }
            val doPotential = if (tw != null && w.pressureMslHpa != null) {
                DoPhysics.doPotential(tw, w.pressureMslHpa!!, salinityPpt)
            } else {
                null
            }
            val doSat = tw?.let { DoPhysics.doSaturation(it, salinityPpt) }
            val doPercent = if (doPotential != null && doSat != null && doSat > 0) doPotential / doSat * 100.0 else null
            val rain24 = rain24h(weatherByEpoch, w.timeEpochSec)
            WaterQualitySample(
                timeEpochSec = w.timeEpochSec,
                waterTempC = tw,
                doPercent = doPercent,
                doMgL = doPotential,
                turbidityProxy = SubScores.turbidityIndex(w.cloudCoverPct, rain24 ?: 0.0),
                chlorophyllProxy = SubScores.chlorophyllIndex(null, tw, w.cloudCoverPct),
                confidence = if (obs != null) {
                    com.tidespot.core.domain.model.Confidence.MEASURED
                } else {
                    com.tidespot.core.domain.model.Confidence.ESTIMATED
                },
            )
        }
    }

    /** OPT-V11-02：±6h 内最近的实测水温（无命中 → null；多条取时间最近）。 */
    private fun nearestObservation(epochSec: Long, observations: List<Pair<Long, Double>>): Double? {
        val window = 6 * 3600L
        return observations
            .filter { kotlin.math.abs(it.first - epochSec) <= window }
            .minByOrNull { kotlin.math.abs(it.first - epochSec) }
            ?.second
    }

    // ────────────────────────── F-3.4 钓鱼指数 ──────────────────────────

    /** daily.sunrise/sunset（当地串，无偏移）→ 当地日 → (日出h, 日落h)；缺 → null。 */
    fun sunHoursByDate(daily: WeatherDailyDto?): Map<LocalDate, Pair<Double?, Double?>> {
        if (daily == null) return emptyMap()
        val out = LinkedHashMap<LocalDate, Pair<Double?, Double?>>()
        val n = daily.time.size
        for (i in 0 until n) {
            val date = runCatching { LocalDate.parse(daily.time[i]) }.getOrNull() ?: continue
            val sr = daily.sunrise?.getOrNull(i)?.let(::parseLocalHour)
            val ss = daily.sunset?.getOrNull(i)?.let(::parseLocalHour)
            out[date] = sr to ss
        }
        return out
    }

    private fun parseLocalHour(iso: String): Double? = runCatching {
        val t = LocalTime.parse(iso.takeLast(5))
        t.hour + t.minute / 60.0
    }.getOrNull()

    /** 评分输入组装（tzOffsetHours 显式传值；weather 非空的小时才参与——Input.weather 非空契约）。 */
    fun buildScoreInputs(
        slices: List<HourSlice>,
        daily: WeatherDailyDto?,
        zone: ZoneId,
    ): List<FishScoreCalculator.HourlyScoreInput> {
        val sun = sunHoursByDate(daily)
        return slices.filter { it.weather != null }.map { s ->
            val date = localDate(s.timeEpochSec, zone)
            val (sr, ss) = sun[date] ?: (null to null)
            FishScoreCalculator.HourlyScoreInput(
                timeEpochSec = s.timeEpochSec,
                weather = s.weather!!,
                marine = s.marine,
                sunriseLocalHour = sr,
                sunsetLocalHour = ss,
                tzOffsetHours = tzOffsetHours(s.timeEpochSec, zone),
            )
        }
    }

    fun computeScores(
        inputs: List<FishScoreCalculator.HourlyScoreInput>,
        preset: ScenePreset,
        lonDeg: Double,
    ): List<FishScoreResult> =
        if (inputs.isEmpty()) emptyList() else FishScoreCalculator().calculate(inputs, preset, lonDeg)

    /**
     * ΠV 乘子重算（F-3.4：`V=0.95（阵风 40 km/h）`）。FishScoreResult 只透出 vetoReasons 文本，
     * 乘子值用 domain 公开的 VetoEvaluator.evaluate 对该小时重算得到（不改 :core:domain）。
     * 无触发 → 空列表（isEmpty() 判定，CHG-08④）。
     */
    fun vetoRows(
        epochSec: Long,
        weatherByEpoch: Map<Long, HourlyWeather>,
        marineByEpoch: Map<Long, HourlyMarine>,
        doPotentialByEpoch: Map<Long, Double?>,
    ): List<VetoRow> {
        val w = weatherByEpoch[epochSec]
        val m = marineByEpoch[epochSec]
        val outcome = VetoEvaluator.evaluate(
            wmoCode = w?.weatherCode,
            windSpeedKmh = w?.windSpeedKmh,
            gustKmh = w?.windGustKmh,
            dP3 = deltaPressure(weatherByEpoch, epochSec, 3),
            rainMmPerH = w?.precipMm,
            waveHeightM = m?.waveHeightM,
            doPotentialMgL = doPotentialByEpoch[epochSec],
        )
        return outcome.reasons.map { VetoRow(reason = it, multiplier = outcome.value) }
    }

    // ────────────────────────── F-3.6 分维降级 ──────────────────────────

    /**
     * 每 Tab 可用性（互不影响）：
     * - WAVE/TIDE ← 海洋源（含内陆 200+全 null → 「本点无该来源数据」，§6.7 整维度 null）；
     * - WEATHER ← 天气源；
     * - SCORE/WATER_QUALITY ← 天气序列（本地推导/评分兜底链）——内陆海洋全 null 不影响（浪项跳过）。
     * 降级时仍渲染旧缓存数据（F-4.5 重试期保留旧数据），并显示 reason + 最近缓存时间 + 重试。
     */
    fun tabStatus(
        tab: DetailTab,
        result: SpotDetailResult,
        nowMillis: Long = System.currentTimeMillis(),
        strings: TideSpotStrings = ZhStrings,
    ): DimensionStatus {
        // CHG-13/N15（§6.7 第 4 行）：缓存年龄 ≥48h → 「数据不可用（不再据此评分）」
        val weatherUnusable = Ttl.unusableAgeMillis(result.fetchedAtMillis[DataSource.WEATHER] ?: 0L, nowMillis)
        val marineUnusable = Ttl.unusableAgeMillis(result.fetchedAtMillis[DataSource.MARINE] ?: 0L, nowMillis)
        val weatherOk = result.weather.isNotEmpty() && weatherUnusable == null
        val marineOk = result.marine.isNotEmpty() && !result.marineAllNullInland && marineUnusable == null
        return when (tab) {
            DetailTab.WEATHER ->
                if (!weatherOk) degraded(result, DataSource.WEATHER, result.weatherDegraded, weatherUnusable, strings)
                else DimensionStatus.Available
            DetailTab.WAVE, DetailTab.TIDE ->
                if (!marineOk) degraded(result, DataSource.MARINE, !result.marineAllNullInland || result.marineDegraded, marineUnusable, strings)
                else DimensionStatus.Available
            DetailTab.SCORE, DetailTab.WATER_QUALITY ->
                if (!weatherOk) degraded(result, DataSource.WEATHER, result.weatherDegraded, weatherUnusable, strings)
                else DimensionStatus.Available
        }
    }

    private fun degraded(
        result: SpotDetailResult,
        source: DataSource,
        sourceFailed: Boolean,
        unusableAgeMillis: Long? = null,
        strings: TideSpotStrings = ZhStrings,
    ): DimensionStatus.Degraded {
        val reason = when {
            // 内陆全 null 属「本点无该来源数据」（数据集本身不存在，与缓存年龄无关，优先于不可用判定）
            source == DataSource.MARINE && result.marineAllNullInland -> strings.noSourceData
            // §6.7 第 4 行：>48h 不可用
            unusableAgeMillis != null -> strings.dataUnusable(((unusableAgeMillis + 3_599_999) / 3_600_000).toInt())
            sourceFailed -> strings.sourceUnavailable
            else -> strings.noSourceData
        }
        return DimensionStatus.Degraded(
            reason = reason,
            lastFetchedMillis = result.fetchedAtMillis[source]?.takeIf { it > 0 },
            lastFetchedSource = source,
        )
    }

    // ────────────────────────── 图表值 / 表格行 ──────────────────────────

    /** 当日图表值序列（null 保真——null 断线/无柱由 HourChart 处理，禁止 0 填充）。 */
    fun chartValues(
        tab: DetailTab,
        daySlices: List<HourSlice>,
        scoresByEpoch: Map<Long, FishScoreResult>,
        qualityByEpoch: Map<Long, WaterQualitySample>,
        tideRates: Map<Long, Double?>,
    ): List<Double?> = daySlices.map { s ->
        when (tab) {
            DetailTab.WAVE -> s.marine?.waveHeightM
            DetailTab.WEATHER -> s.weather?.temperatureC
            DetailTab.SCORE -> scoresByEpoch[s.timeEpochSec]?.total?.toDouble()
            DetailTab.WATER_QUALITY -> qualityByEpoch[s.timeEpochSec]?.waterTempC
            DetailTab.TIDE -> s.marine?.seaLevelHeightMslM
        }
    }

    /** 逐小时表行（F-3.2：24 行可滚；风浪行 = `时:00 浪高 m / 周期 s / 方向 °`）。 */
    data class HourRow(val hourLabel: String, val valueText: String, val description: String)

    fun waveRows(daySlices: List<HourSlice>, zone: ZoneId, strings: TideSpotStrings = ZhStrings): List<HourRow> = daySlices.map { s ->
        val h = hourLocal(s.timeEpochSec, zone)
        val m = s.marine
        val text = "${Format.hourLocal(s.timeEpochSec, zone)}  ${Format.valueUnit(m?.waveHeightM, "m")} / " +
            "${Format.valueUnit(m?.wavePeriodS, "s")} / ${Format.deg(m?.waveDirectionDeg)}"
        HourRow(
            hourLabel = "$h:00",
            valueText = text,
            description = strings.hourWave(
                h,
                Format.valueUnit(m?.waveHeightM, "m"),
                Format.valueUnit(m?.wavePeriodS, "s"),
                Format.deg(m?.waveDirectionDeg),
            ),
        )
    }

    fun weatherRows(daySlices: List<HourSlice>, zone: ZoneId, strings: TideSpotStrings = ZhStrings): List<HourRow> = daySlices.map { s ->
        val h = hourLocal(s.timeEpochSec, zone)
        val w = s.weather
        val text = "${Format.hourLocal(s.timeEpochSec, zone)}  ${Format.valueUnit(w?.temperatureC, "°C")} / " +
            "${Format.valueUnit(w?.windSpeedKmh, "km/h")} / ${Format.valueUnit(w?.pressureMslHpa, "hPa")}"
        HourRow(
            hourLabel = "$h:00",
            valueText = text,
            description = strings.hourWeather(
                h,
                Format.valueUnit(w?.temperatureC, "°C"),
                Format.valueUnit(w?.windSpeedKmh, "km/h"),
                Format.valueUnit(w?.pressureMslHpa, "hPa"),
            ),
        )
    }

    fun scoreRows(
        daySlices: List<HourSlice>,
        scoresByEpoch: Map<Long, FishScoreResult>,
        zone: ZoneId,
        strings: TideSpotStrings = ZhStrings,
    ): List<HourRow> = daySlices.map { s ->
        val h = hourLocal(s.timeEpochSec, zone)
        val score = scoresByEpoch[s.timeEpochSec]
        val text = "${Format.hourLocal(s.timeEpochSec, zone)}  " +
            (score?.let { "${it.total} ${strings.unitScore}" } ?: Format.MISSING)
        HourRow(
            hourLabel = "$h:00",
            valueText = text,
            description = score?.let { strings.hourScore(h, it.total) } ?: strings.hourScoreMissing(h),
        )
    }

    fun qualityRows(
        daySlices: List<HourSlice>,
        qualityByEpoch: Map<Long, WaterQualitySample>,
        zone: ZoneId,
        strings: TideSpotStrings = ZhStrings,
    ): List<HourRow> = daySlices.map { s ->
        val h = hourLocal(s.timeEpochSec, zone)
        val q = qualityByEpoch[s.timeEpochSec]
        val text = "${Format.hourLocal(s.timeEpochSec, zone)}  ${Format.valueUnit(q?.waterTempC, "°C")} / " +
            (q?.doPercent?.let { "${Format.value(it)} %" } ?: Format.MISSING) + " / " +
            Format.value(q?.turbidityProxy, 2)
        HourRow(
            hourLabel = "$h:00",
            valueText = text,
            description = strings.hourWater(
                h,
                Format.valueUnit(q?.waterTempC, "°C"),
                Format.valueUnit(q?.doMgL, "mg/L"),
            ),
        )
    }

    fun tideRows(
        daySlices: List<HourSlice>,
        rates: Map<Long, Double?>,
        zone: ZoneId,
        strings: TideSpotStrings = ZhStrings,
    ): List<HourRow> = daySlices.map { s ->
        val h = hourLocal(s.timeEpochSec, zone)
        val m = s.marine
        val rate = rates[s.timeEpochSec]
        val text = "${Format.hourLocal(s.timeEpochSec, zone)}  ${Format.valueUnit(m?.seaLevelHeightMslM, "m")} / " +
            (rate?.let { "${Format.value(it)} m/h" } ?: Format.MISSING) + " / " +
            Format.valueUnit(m?.oceanCurrentVelocityKmh, "km/h")
        val rateText = rate?.let { "${Format.value(it)} m/h" } ?: strings.hourMissing
        HourRow(
            hourLabel = "$h:00",
            valueText = text,
            description = strings.hourTide(
                h,
                Format.valueUnit(m?.seaLevelHeightMslM, "m"),
                rateText,
                Format.valueUnit(m?.oceanCurrentVelocityKmh, "km/h"),
            ),
        )
    }

    fun rowsFor(tab: DetailTab, daySlices: List<HourSlice>, zone: ZoneId, state: DetailUiState, strings: TideSpotStrings = ZhStrings): List<HourRow> {
        val scoresByEpoch = state.scores.associateBy { it.timeEpochSec }
        val qualityByEpoch = state.quality.associateBy { it.timeEpochSec }
        return when (tab) {
            DetailTab.WAVE -> waveRows(daySlices, zone, strings)
            DetailTab.WEATHER -> weatherRows(daySlices, zone, strings)
            DetailTab.SCORE -> scoreRows(daySlices, scoresByEpoch, zone, strings)
            DetailTab.WATER_QUALITY -> qualityRows(daySlices, qualityByEpoch, zone, strings)
            DetailTab.TIDE -> tideRows(daySlices, state.tideRates, zone, strings)
        }
    }
}
