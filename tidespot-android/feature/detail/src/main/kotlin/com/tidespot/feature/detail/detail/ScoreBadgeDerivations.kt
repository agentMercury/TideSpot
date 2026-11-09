package com.tidespot.feature.detail.detail

import com.tidespot.core.domain.model.FactorScore
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.ui.badge.Kind

/**
 * D10/OPT-M5-05 数值区/图表徽标派生（纯函数，JVM 锁测试；CHG-16⑫）：
 * - 常规置信：[Kind.fromConfidence]（score.confidence domain 透出；**禁硬编码 ESTIMATED**——
 *   原 DetailScreen 水质 Tab 硬编码即本次要清除的根因）；
 * - §6.7 权重降级「低置信」：缺测因子剔除后有效权重占比 <0.6 → [Kind.LOW_CONFIDENCE]。
 *   与 FishScoreCalculator 同口径（wSum/preset.weightSum < 0.6）：domain 只透出最终 confidence
 *   （降级即 ESTIMATED），UI 按 factors 权重和重算占比以区分「低置信」与普通估算档。
 */
object ScoreBadgeDerivations {

    /** §6.7 有效权重占比阈值（FishScoreCalculator.kt:222 同口径）。 */
    const val LOW_CONFIDENCE_RATIO = 0.6

    /**
     * 有效权重占比 = 该小时产出因子权重和 / 预设满权重和；
     * factors 为空（未评分/全缺测）或预设权重和 ≤0 → null（不虚构比例）。
     */
    fun effectiveWeightRatio(factors: List<FactorScore>, presetWeightSum: Double): Double? =
        if (factors.isEmpty() || presetWeightSum <= 0.0) null
        else factors.sumOf { it.weight } / presetWeightSum

    /**
     * 徽标档位：权重降级（占比 <0.6）优先标「低置信」；否则按 domain confidence 映射。
     * score = null → null（不渲染徽标）。
     */
    fun badgeKind(score: FishScoreResult?, presetWeightSum: Double): Kind? {
        if (score == null) return null
        val ratio = effectiveWeightRatio(score.factors, presetWeightSum)
        return if (ratio != null && ratio < LOW_CONFIDENCE_RATIO) Kind.LOW_CONFIDENCE
        else Kind.fromConfidence(score.confidence)
    }
}
