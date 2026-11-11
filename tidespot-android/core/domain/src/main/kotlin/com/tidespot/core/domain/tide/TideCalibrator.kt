package com.tidespot.core.domain.tide

import com.tidespot.core.domain.strings.DomainCopy
import com.tidespot.core.domain.strings.ZhDomainCopy
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 潮位校正层（SRS §6.2b，纯客户端自校准；附录 B7 黄金结论）。
 *
 * 流程：去趋势（每窗口剔除线性漂移）→ 互相关找 lag ∈ [−8,+8] → OLS 拟合 (gain, offset)
 * → 采纳规则：r≥0.6 且 |lag|≤5 且 gain∈[0.7,1.4] 且 offset∈[−0.5,0.5] 且对齐样本 ≥150h。
 * 实测结论：该规则可同时得到"该修就修"（圣弗朗西斯科 lag=+1, gain≈0.97, RMSE −54%）
 * 与"不必修就不动"（纽波特 lag=0, gain≈0.99, 基本不变）。
 */
object TideCalibrator {

    /** 校正参数与诊断量（[adopted]=false 时 [rejectReason] 必非空）。 */
    data class Calibration(
        val lagHours: Int,
        val gain: Double,
        val offsetM: Double,
        val r: Double,
        val alignedSamples: Int,
        val rawRmseM: Double,
        val correctedRmseM: Double,
        val adopted: Boolean,
        val rejectReason: String?,
        /** 半样本外（fit 60% / test 40%）诊断：null = 样本不足以做留出验证。 */
        val holdout: Holdout?,
    )

    data class Holdout(
        val baselineRmseM: Double,
        val correctedRmseM: Double,
    ) {
        /** 留出集改善比例（正=更好）。 */
        val improvementRatio: Double get() = (baselineRmseM - correctedRmseM) / baselineRmseM
    }

    // ---- 采纳规则阈值（SRS §6.2b，勿改；改动必须同步单测并说明理由）----
    private const val MIN_R = 0.6
    private const val MAX_ABS_LAG = 5
    private const val GAIN_MIN = 0.7
    private const val GAIN_MAX = 1.4
    private const val OFFSET_MAX_M = 0.5
    private const val MIN_SAMPLES = 150
    private const val LAG_SEARCH_RADIUS = 8

    /**
     * 校正主入口。[modelMsl] 为模型潮位序列（Open-Meteo sea_level_height_msl），
     * [refMsl] 为参考实测序列（NOAA water_level 小时均值），两者逐小时等长对齐（缺值=null）。
     */
    fun calibrate(modelMsl: List<Double?>, refMsl: List<Double?>): Calibration {
        // 1) 对齐：仅取两序列都非 null 的公共下标
        val pairs = modelMsl.indices.mapNotNull { i ->
            val m = modelMsl[i]
            val r = refMsl.getOrNull(i)
            if (m != null && r != null) i to (m to r) else null
        }
        val n = pairs.size
        // BLK-06/N-4（CHG-07 修复 4）：样本不足不抛异常，按本类既有风格返回「未校正」失败结果，
        // 调用方得到 adopted=false + rejectReason 而非 CalibrationException
        if (n < 2) {
            val raw = if (n == 1) rmse(listOf(pairs[0].second.first - pairs[0].second.second)) else 0.0
            return Calibration(
                lagHours = 0, gain = 1.0, offsetM = 0.0, r = 0.0,
                alignedSamples = n, rawRmseM = raw, correctedRmseM = raw,
                adopted = false, rejectReason = "对齐样本 $n < 2，样本不足，未校正", holdout = null,
            )
        }

        val rawRmse = rmse(pairs.map { it.second.first - it.second.second })

        if (n < MIN_SAMPLES) {
            return Calibration(0, 1.0, 0.0, 0.0, n, rawRmse, rawRmse, false, "对齐样本 $n < $MIN_SAMPLES h，禁用校正层", null)
        }

        // 2) 去趋势：dt(x) = x − OLS(x ~ t)，t 用小时序号
        val times = pairs.map { it.first.toDouble() }
        val mDet = detrend(pairs.map { it.second.first }, times)
        val rDet = detrend(pairs.map { it.second.second }, times)

        // 3) 互相关找 lag* = argmax_{L∈[−8,+8]} corr(shift(dt(M),L), dt(R))
        //    shift(dt(M), L)[i] = dtM[i − L]：L>0 表示模型滞后参考 L 小时（M(h−L) 对齐 R(h)）。
        var bestLag = 0
        var bestR = Double.NEGATIVE_INFINITY
        for (lag in -LAG_SEARCH_RADIUS..LAG_SEARCH_RADIUS) {
            val r = crossCorrelation(mDet, rDet, lag)
            if (r.isFinite() && r > bestR) {
                bestR = r
                bestLag = lag
            }
        }

        // 4) (gain, offset) = OLS(dt(R) ~ shift(dt(M), lag*))
        val (gain, offset) = olsFit(mDet, rDet, bestLag)

        // 5) 采纳规则
        val reject = when {
            bestR < MIN_R -> "相关系数 ${"%.3f".format(bestR)} < $MIN_R，不校正并标注\"未校正\""
            abs(bestLag) > MAX_ABS_LAG -> "lag $bestLag h 超出 ±$MAX_ABS_LAG h，不校正"
            gain !in GAIN_MIN..GAIN_MAX -> "gain ${"%.3f".format(gain)} 超出 [$GAIN_MIN,$GAIN_MAX]，拒绝采用"
            abs(offset) > OFFSET_MAX_M -> "offset ${"%.3f".format(offset)} 超出 ±$OFFSET_MAX_M m，拒绝采用"
            else -> null
        }

        // 6) 校正后 RMSE：M'(h)=gain·M(h−lag)+offset（用原始序列，不用去趋势序列）
        val correctedRmse = correctedRmseOn(modelMsl, refMsl, bestLag, gain, offset)

        // 7) 半样本外验证（fit 前 60% / test 后 40%），证明非过拟合
        val holdout = holdoutValidate(pairs, rawRmse)

        return Calibration(
            lagHours = bestLag, gain = gain, offsetM = offset, r = bestR,
            alignedSamples = n, rawRmseM = rawRmse, correctedRmseM = correctedRmse,
            adopted = reject == null, rejectReason = reject, holdout = holdout,
        )
    }

    /** 应用校正：M'(h) = gain·M(h − lag) + offset（未采纳时返回原值）。 */
    fun apply(modelMsl: List<Double?>, c: Calibration): List<Double?> =
        if (!c.adopted) modelMsl
        else modelMsl.mapIndexed { i, _ ->
            val src = modelMsl.getOrNull(i - c.lagHours)
            src?.let { c.gain * it + c.offsetM }
        }

    // ---------- 内部工具 ----------

    /** OLS 一元线性回归 y ~ x，返回 (slope, intercept)。 */
    private fun ols(x: List<Double>, y: List<Double>): Pair<Double, Double> {
        val n = x.size
        val mx = x.sum() / n
        val my = y.sum() / n
        var sxy = 0.0
        var sxx = 0.0
        for (i in 0 until n) {
            sxy += (x[i] - mx) * (y[i] - my)
            sxx += (x[i] - mx) * (x[i] - mx)
        }
        if (sxx == 0.0) return 1.0 to 0.0
        val slope = sxy / sxx
        return slope to my - slope * mx
    }

    /** 去趋势：x − OLS(x ~ t)。 */
    private fun detrend(x: List<Double>, t: List<Double>): List<Double> {
        val (slope, intercept) = ols(t, x)
        return x.mapIndexed { i, v -> v - (slope * t[i] + intercept) }
    }

    /** Pearson 相关 corr(shift(x, lag), y)：lag>0 表示 x 滞后 y。 */
    private fun crossCorrelation(x: List<Double>, y: List<Double>, lag: Int): Double {
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        for (i in y.indices) {
            val j = i - lag
            if (j in x.indices) {
                xs.add(x[j])
                ys.add(y[i])
            }
        }
        if (xs.size < 10) return Double.NEGATIVE_INFINITY
        return pearson(xs, ys)
    }

    private fun pearson(x: List<Double>, y: List<Double>): Double {
        val n = x.size
        val mx = x.sum() / n
        val my = y.sum() / n
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in 0 until n) {
            val dx = x[i] - mx
            val dy = y[i] - my
            sxy += dx * dy
            sxx += dx * dx
            syy += dy * dy
        }
        if (sxx == 0.0 || syy == 0.0) return 0.0
        return sxy / sqrt(sxx * syy)
    }

    /** 在去趋势序列上做 y ~ shift(x, lag) 的 OLS，返回 (gain, offset)。 */
    private fun olsFit(xDet: List<Double>, yDet: List<Double>, lag: Int): Pair<Double, Double> {
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        for (i in yDet.indices) {
            val j = i - lag
            if (j in xDet.indices) {
                xs.add(xDet[j])
                ys.add(yDet[i])
            }
        }
        return ols(xs, ys)
    }

    /** 校正后 RMSE（原始序列上 M'(h)=gain·M(h−lag)+offset vs R(h)）。 */
    private fun correctedRmseOn(
        modelMsl: List<Double?>,
        refMsl: List<Double?>,
        lag: Int,
        gain: Double,
        offset: Double,
    ): Double {
        val diffs = ArrayList<Double>()
        for (i in refMsl.indices) {
            val r = refMsl[i] ?: continue
            val m = modelMsl.getOrNull(i - lag) ?: continue
            diffs.add(gain * m + offset - r)
        }
        return rmse(diffs)
    }

    /** 半样本外：fit 前 60% 求 (lag, gain, offset)，test 后 40% 对比基线 RMSE。 */
    private fun holdoutValidate(pairs: List<Pair<Int, Pair<Double, Double>>>, fullRawRmse: Double): Holdout? {
        val split = (pairs.size * 0.6).toInt()
        if (split < MIN_SAMPLES || pairs.size - split < 30) return null
        val fitPart = pairs.subList(0, split)
        val testPart = pairs.subList(split, pairs.size)

        val tFit = fitPart.map { it.first.toDouble() }
        val mDetFit = detrend(fitPart.map { it.second.first }, tFit)
        val rDetFit = detrend(fitPart.map { it.second.second }, tFit)

        var bestLag = 0
        var bestR = Double.NEGATIVE_INFINITY
        for (lag in -LAG_SEARCH_RADIUS..LAG_SEARCH_RADIUS) {
            val r = crossCorrelation(mDetFit, rDetFit, lag)
            if (r.isFinite() && r > bestR) {
                bestR = r
                bestLag = lag
            }
        }
        val (gain, offset) = olsFit(mDetFit, rDetFit, bestLag)

        // test 段：基线 RMSE（原值直比）与校正 RMSE（fit 段参数外推）
        val base = ArrayList<Double>()
        val corr = ArrayList<Double>()
        for ((idx, v) in testPart) {
            val m0 = v.first
            val r0 = v.second
            base.add(m0 - r0)
            val j = idx - bestLag
            val m = pairs.firstOrNull { it.first == j }?.second?.first
                ?: pairs.asSequence().map { it.first to it.second.first }.toMap()[j]
            if (m != null) corr.add(gain * m + offset - r0)
        }
        return Holdout(rmse(base), rmse(corr))
    }

    private fun rmse(diffs: List<Double>): Double =
        sqrt(diffs.map { it * it }.sum() / diffs.size)

    /**
     * OPT-V11-07：按观测类型取模型候选标记时刻。
     * 「开始涨潮」与低潮同刻（落→涨翻转）、「开始退潮」与高潮同刻（涨→落翻转）；
     * 分类型表缺失时退回极值全集（历史数据语义），绝不丢观测。
     */
    private fun candidatesFor(
        type: TideObservationType?,
        markers: Map<TideObservationType, List<Long>>,
        allExtremes: List<Long>,
    ): List<Long> = when (type) {
        null -> allExtremes
        TideObservationType.HIGH -> markers[TideObservationType.HIGH] ?: allExtremes
        TideObservationType.FALLING_START ->
            markers[TideObservationType.FALLING_START] ?: markers[TideObservationType.HIGH] ?: allExtremes
        TideObservationType.LOW -> markers[TideObservationType.LOW] ?: allExtremes
        TideObservationType.RISING_START ->
            markers[TideObservationType.RISING_START] ?: markers[TideObservationType.LOW] ?: allExtremes
    }

    /**
     * §6.2b 规则 5（CHG-04）：无合法参考序列地区（含中国沿海）的现场校潮。
     * 用户记录高低潮时刻，≥2 条（±6h 内有匹配模型极值，OPT-06/M5）才启用拟合（gain 固定 1.0，仅拟合 lag/offset）；
     * 仅 1 条时只落 manual_observation 不启用。
     */
    fun fitManual(
        observationTimesEpochSec: List<Long>,
        modelExtremeTimesEpochSec: List<Long>,
        // OPT-V11-07（P1，尾部只增）：观测类型（与时刻逐条对齐；空 = 历史数据「任意极值最近匹配」）
        observationTypes: List<TideObservationType?> = emptyList(),
        // OPT-V11-07：模型分类型标记时刻（高潮/低潮/开始涨/开始退；缺键按极值全集兜底）
        modelMarkersByType: Map<TideObservationType, List<Long>> = emptyMap(),
        // OPT-V11-13：文案源（默认中文，零行为漂移；Android 侧注入 ResDomainCopy）
        copy: DomainCopy = ZhDomainCopy,
    ): ManualTideFit {
        if (observationTimesEpochSec.size < 2) {
            return ManualTideFit(
                lagHours = 0, offsetM = 0.0, gain = 1.0, adopted = false,
                message = copy.manualNeedMore(2 - observationTimesEpochSec.size),
            )
        }
        // OPT-06（M5）：模型极值匹配限 ±6h 窗（abs(it-obs) <= 6*3600 先过滤再取最近）——
        // 窗内无匹配的观测不计入 lags（旧实现把失配观测当 lag=0 计入中位数，脏数据污染拟合）。
        // lag = 有效观测与最近模型极值时刻差值的中位数（四舍五入到整小时）。
        // 现场观测只记录时刻、无水位值 → offset 无数据可拟合，固定 0；gain 固定 1.0（SRS §6.2b 规则 5）。
        val matchWindowSec = 6 * 3600L
        val lags = observationTimesEpochSec.mapIndexedNotNull { i, obs ->
            val type = observationTypes.getOrNull(i)
            val candidates = candidatesFor(type, modelMarkersByType, modelExtremeTimesEpochSec)
            candidates
                .filter { abs(it - obs) <= matchWindowSec }
                .minByOrNull { abs(it - obs) }
                ?.let { (obs - it) / 3600.0 }
        }.sorted()
        if (lags.size < 2) {
            return ManualTideFit(
                lagHours = 0, offsetM = 0.0, gain = 1.0, adopted = false,
                message = copy.manualNoValidObservations(lags.size),
            )
        }
        val median = if (lags.size % 2 == 1) lags[lags.size / 2] else (lags[lags.size / 2 - 1] + lags[lags.size / 2]) / 2.0
        val lag = Math.round(median).toInt()
        if (abs(lag) > MAX_ABS_LAG) {
            return ManualTideFit(lag, 0.0, 1.0, false, copy.manualLagExceeds(lag, MAX_ABS_LAG))
        }
        return ManualTideFit(lag, 0.0, 1.0, true, null)
    }

    /** 现场校潮拟合结果（gain 固定 1.0，SRS §6.2b 规则 5）。 */
    data class ManualTideFit(
        val lagHours: Int,
        val offsetM: Double,
        val gain: Double,
        val adopted: Boolean,
        val message: String?,
    )
}
