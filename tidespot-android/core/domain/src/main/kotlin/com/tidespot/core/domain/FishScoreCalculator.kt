package com.tidespot.core.domain

import com.tidespot.core.domain.algorithm.FactorId
import com.tidespot.core.domain.algorithm.MathKit
import com.tidespot.core.domain.algorithm.ScenePreset
import com.tidespot.core.domain.algorithm.Solunar
import com.tidespot.core.domain.algorithm.SubScores
import com.tidespot.core.domain.algorithm.VetoEvaluator
import com.tidespot.core.domain.model.Confidence
import com.tidespot.core.domain.model.FactorScore
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather
import com.tidespot.core.domain.strings.DomainCopy
import com.tidespot.core.domain.strings.ZhDomainCopy
import com.tidespot.core.domain.water.DoPhysics
import com.tidespot.core.domain.water.WaterTempFusion
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * 钓鱼指数逐小时计算管线（SRS §6；与参照实现 fishscore.mjs 第 6 节逐行等价）。
 *
 * 输入契约（CONTRACT.md）：内部 SI 单位；源缺值=null 禁止 0 填充；
 * null 因子从加权分母剔除（§6.4），剔除后有效权重 <0.6 → 低置信徽标（§6.7）。
 */
class FishScoreCalculator {

    companion object {
        /**
         * 算法版本（§7.3 hourly_score.algoVersion / N-10）：入库与缓存键组成项。
         * v1 = CHG-08 定案口径（Tw 谐波滞后 α=0.70 / 缺测保守化 / vetoReasons 空列表）。
         */
        const val ALGO_VERSION: Int = 1
    }

    /** 缺 past 小时（无法计算 ΔP3）的 pressure 降级权重（§6.7：0.14→0.08）。 */
    private val MISSING_TREND_PRESSURE_WEIGHT = 0.08

    /** 单行评分输入（由 :core:data 从 §5 DTO 映射；sr/ss 由 daily 数据按日期填入）。 */
    data class HourlyScoreInput(
        val timeEpochSec: Long,
        val weather: HourlyWeather,
        val marine: HourlyMarine? = null,
        val sunriseLocalHour: Double? = null,
        val sunsetLocalHour: Double? = null,
        /**
         * F-3.8 / FO-18（M4 必修 4）：选点当地时区偏移（小时）。**无默认值**——
         * 调用侧必须按响应 IANA `timezone` / `utc_offset_seconds` 换算后显式传值，
         * 禁止再依赖隐式 +8（中国点黄金对拍以显式 8.0 传值保持不变）。
         */
        val tzOffsetHours: Double,
    )

    /**
     * 主入口。[inputs] 必须按时间升序（可在 START 之前多带前置小时，供 Tw 滞后窗口与 dP24 用）。
     * [lonDeg] 用于月上中天换算（与参照实现一致，tz 默认 +8）。
     */
    fun calculate(
        inputs: List<HourlyScoreInput>,
        preset: ScenePreset,
        lonDeg: Double,
        // OPT-V11-13：文案源（默认中文，零行为漂移；Android 侧注入 ResDomainCopy）
        copy: DomainCopy = ZhDomainCopy,
    ): List<FishScoreResult> {
        // N-4/BLK-06（CHG-07 修复 1）：空序列返回空结果 + 合法空综合，不得抛异常
        if (inputs.isEmpty()) return emptyList()
        val sorted = inputs.sortedBy { it.timeEpochSec }

        // 潮位序列准备：amp = 全窗潮差；dh = 相邻非 null 潮位差（§6.2b）
        val slSeries = sorted.map { it.marine?.seaLevelHeightMslM }
        val slValues = slSeries.filterNotNull()
        val amp = if (slValues.isNotEmpty()) slValues.max() - slValues.min() else 0.0
        var prevSl: Double? = null
        val dhByIndex = HashMap<Int, Double>()
        slSeries.forEachIndexed { i, sl ->
            if (sl != null) {
                dhByIndex[i] = prevSl?.let { sl - it } ?: 0.0
                prevSl = sl
            }
        }

        // 气温/气压序列（Tw 滞后窗口、dP3/dP24 用）
        val airTemps = sorted.map { it.weather.temperatureC }
        val pressures = sorted.map { it.weather.pressureMslHpa }

        return sorted.mapIndexed { i, input ->
            scoreOne(input, i, preset, lonDeg, amp, dhByIndex[i] ?: 0.0, airTemps, pressures, sorted, copy)
        }
    }

    private fun scoreOne(
        input: HourlyScoreInput,
        index: Int,
        preset: ScenePreset,
        lonDeg: Double,
        amp: Double,
        dh: Double,
        airTemps: List<Double?>,
        pressures: List<Double?>,
        all: List<HourlyScoreInput>,
        copy: DomainCopy,
    ): FishScoreResult {
        val w = input.weather
        val m = input.marine

        // 当地墙钟（小时/日期）
        val zone = ZoneOffset.ofTotalSeconds((input.tzOffsetHours * 3600).roundToInt())
        val local: LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(input.timeEpochSec), zone)
        val hourLocal = local.hour + local.minute / 60.0
        val dateStr = local.toLocalDate().toString()

        // —— 6.1 水温融合 Tw ——
        val sst = m?.seaSurfaceTempC
        val tw: Double? = WaterTempFusion.fuse(airTemps, index, w.temperatureC, sst)

        // —— 6.2d 气压趋势（CHG-07 修复 2：缺 past 无法计算 ΔP3 → dP3=null + 权重降级 0.14→0.08）——
        val p = w.pressureMslHpa
        val prevP3 = if (index >= 3) pressures[index - 3] else null
        // 缺 past = 序列前置不足（index<3）或 3h 前气压缺测：dP3 保持 null 语义（不得当 0 参与 SubScores，
        // 由 SubScores.pressure 内部按 §6.5 修正 2 平台值处理；ΠV 侧由 VetoEvaluator 按保守档处理）
        val trendMissing = p != null && (index < 3 || prevP3 == null)
        val dP3: Double? = if (p == null || trendMissing) null else p - prevP3!!
        val dP24 = if (p != null && index >= 24) p - (pressures[index - 24] ?: p) else 0.0

        // —— 6.2a 溶氧 ——
        val salt = preset.salinityPpt
        val doP: Double? = tw?.let { t -> p?.let { pp -> DoPhysics.doPotential(t, pp, salt) } }
        val sOxygen: Double? = doP?.let { d -> DoPhysics.oxygenScore(d, DoPhysics.doRef(tw!!, preset.doBase)) }

        // —— 6.2c Solunar ——
        val age = Solunar.lunarAge(input.timeEpochSec * 1000)
        val transit = Solunar.lunarTransitClock(age, lonDeg, input.tzOffsetHours)
        val sSolunar = Solunar.score(hourLocal, transit, age)
        // D09（M6/CHG-16）：月龄/照明/中天三项原始输入进 FactorScore.detail（§6.2d/e/f；
        // 纯增明细文本，sub/weight 不变——黄金对拍 B1–B7 数值路径零触碰）
        val illum = Solunar.illumination(age)
        val solunarDetail = copy.solunarDetail(
            String.format(java.util.Locale.US, "%.1f", age),
            String.format(java.util.Locale.US, "%.0f%%", illum * 100),
            String.format(java.util.Locale.US, "%.2f", transit),
        )

        // —— 其余子分（null → 从分母剔除，§6.4/§6.7）——
        // rain（CHG-07 修复 1）：各雨量分量全缺测 → 未知(null)，不得当 0（会误加小雨 bonus、漏安全衰减）；
        // 任一分量有值 → 取已知分量之和（缺测分量不计入，保真可用信息）
        val rainParts = listOfNotNull(w.rainMm, w.showersMm)
        val rain: Double? = if (rainParts.isEmpty()) null else rainParts.sum()
        val sPressure: Double? = p?.let { SubScores.pressure(it, dP3, dP24) }
        val sWind: Double? = w.windSpeedKmh?.let { SubScores.wind(it, w.windGustKmh) }
        val sWeather: Double? = w.weatherCode?.let { code ->
            SubScores.weather(code, rain, w.cloudCoverPct?.let { it })
        }
        val sDaypart = SubScores.daypart(hourLocal, input.sunriseLocalHour, input.sunsetLocalHour, preset.nightTarget)
        val sTempMatch: Double? = tw?.let { SubScores.tempMatch(it, preset.tempOptC, preset.tempSigmaC) }
        val sComfort: Double? = w.apparentC?.let { SubScores.comfort(it) }

        // —— 6.2b 潮汐子分（海水专用；sl=null → 因子剔除）——
        val sl = m?.seaLevelHeightMslM
        val sTide: Double? = sl?.let { SubScores.tide(dh, amp) }

        // —— 合成（§6.3）：null 因子剔除分母 ——
        val subById = mutableMapOf<FactorId, Double>()
        subById[FactorId.OXYGEN] = sOxygen ?: Double.NaN
        subById[FactorId.PRESSURE] = sPressure ?: Double.NaN
        subById[FactorId.SOLUNAR] = sSolunar
        if (sTide != null) subById[FactorId.TIDE] = sTide
        subById[FactorId.WIND] = sWind ?: Double.NaN
        subById[FactorId.WEATHER] = sWeather ?: Double.NaN
        subById[FactorId.DAYPART] = sDaypart
        subById[FactorId.TEMP_MATCH] = sTempMatch ?: Double.NaN
        subById[FactorId.COMFORT] = sComfort ?: Double.NaN

        // Σw 同步变化（CHG-07 修复 2）：缺 past 小时 PRESSURE 权重 0.14 → 0.08（§6.7）
        val effWeights = if (trendMissing && sPressure != null) {
            preset.weights + (FactorId.PRESSURE to MISSING_TREND_PRESSURE_WEIGHT)
        } else preset.weights
        var acc = 0.0
        var wSum = 0.0
        for ((factor, weight) in effWeights) {
            val s = subById[factor]
            if (s != null && s.isFinite()) {
                acc += s * weight
                wSum += weight
            }
        }
        val baseScore = if (wSum > 0) MathKit.clip01(acc / wSum) else 0.0

        // —— 6.5 安全衰减 ΠV ——
        val wave = m?.waveHeightM
        val veto = VetoEvaluator.evaluate(
            wmoCode = w.weatherCode,
            windSpeedKmh = w.windSpeedKmh,
            gustKmh = w.windGustKmh,
            dP3 = dP3,
            rainMmPerH = rain,
            waveHeightM = wave,
            doPotentialMgL = doP,
            copy = copy,
        )
        val total = (baseScore * veto.value * 100.0).roundToInt()

        // 缺 past 小时的 pressure 明细：权重 0.08 + 「趋势数据不足」标注（中文，M6 i18n 时再机器码化）
        val effectivePressureWeight =
            if (trendMissing) MISSING_TREND_PRESSURE_WEIGHT else preset.weights[FactorId.PRESSURE] ?: 0.0
        val detailForPressure = if (trendMissing) copy.trendInsufficient else null

        // —— 因子明细（F-3.4 分解视图；只收录有效因子，缺失 = 不出现 → UI 显示"本点无该来源数据"§6.7；
        //     水质代理仅入水质 Tab、不计入指数（§6.2j），故 weight=0）——
        val factors = buildList {
            sOxygen?.let { add(FactorScore("oxygen", it, preset.weights[FactorId.OXYGEN] ?: 0.0)) }
            sPressure?.let { add(FactorScore("pressure", it, effectivePressureWeight, detailForPressure)) }
            add(FactorScore("solunar", sSolunar, preset.weights[FactorId.SOLUNAR] ?: 0.0, solunarDetail))
            sTide?.let { add(FactorScore("tide", it, preset.weights[FactorId.TIDE] ?: 0.0)) }
            sWind?.let { add(FactorScore("wind", it, preset.weights[FactorId.WIND] ?: 0.0, w.windGustKmh?.toString())) }
            sWeather?.let { add(FactorScore("weather", it, preset.weights[FactorId.WEATHER] ?: 0.0)) }
            add(FactorScore("daypart", sDaypart, preset.weights[FactorId.DAYPART] ?: 0.0))
            sTempMatch?.let { add(FactorScore("tempMatch", it, preset.weights[FactorId.TEMP_MATCH] ?: 0.0)) }
            sComfort?.let { add(FactorScore("comfort", it, preset.weights[FactorId.COMFORT] ?: 0.0)) }
            // 水质代理（可选计入指数，v1 默认仅展示）。
            // FO-15（M4 必修 5）：§6.2j rain24h 接线——前 24h 降水累计（§6.7：已有样本累计，
            // 不足 24h 按已有样本；窗口内无任何样本 → null，由 WaterQualityProxy 按中性处理）。
            val sWaterQ = sOxygen?.let {
                com.tidespot.core.domain.water.WaterQualityProxy.score(
                    it, w.cloudCoverPct, rain24hSum(index, all), null, tw,
                )
            }
            sWaterQ?.let { add(FactorScore("waterQuality", it, 0.0, copy.waterQualityProxyNote)) }
        }

        // 低置信：有效权重 < 0.6（§6.7）
        val confidence = if (wSum / preset.weightSum < 0.6) Confidence.ESTIMATED else Confidence.MODEL

        return FishScoreResult(
            timeEpochSec = input.timeEpochSec,
            total = total,
            factors = factors,
            vetoReasons = veto.reasons,
            confidence = confidence,
            // CHG-16①/OPT-M5-04：机器可读否决码透传（与 vetoReasons 同步产生/同步为空）
            vetoCodes = veto.codes,
        )
    }

    /**
     * §6.2j / FO-15（M4 必修 5）：当前小时【前 24h】降水累计（mm）。
     * 缺失策略（§6.7）：窗口内已有样本累计（该小时 rain/showers 全缺测则不计入）；
     * 窗口内无任何样本 → null（禁 0 填充语义，水质代理按中性处理）。
     */
    private fun rain24hSum(index: Int, all: List<HourlyScoreInput>): Double? {
        var sum = 0.0
        var any = false
        for (k in maxOf(0, index - 24) until index) {
            val parts = listOfNotNull(all[k].weather.rainMm, all[k].weather.showersMm)
            if (parts.isNotEmpty()) {
                any = true
                sum += parts.sum()
            }
        }
        return if (any) sum else null
    }
}
