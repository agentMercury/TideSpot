package com.tidespot.feature.detail

import com.tidespot.core.domain.model.Confidence
import com.tidespot.core.domain.model.FactorScore
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.ui.badge.Kind
import com.tidespot.feature.detail.detail.ScoreBadgeDerivations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D10/OPT-M5-05 徽标派生锁（CHG-16⑫）：confidence→Kind 映射接线 + §6.7 权重降级「低置信」判定
 * （禁硬编码 ESTIMATED——DetailScreen 原水质 Tab 硬编码已由接线层替换）。
 */
class ScoreBadgeDerivationsTest {

    private fun factor(weight: Double, sub: Double = 0.8) = FactorScore("OXYGEN", sub, weight)

    private fun score(factors: List<FactorScore>, confidence: Confidence) =
        FishScoreResult(timeEpochSec = 0L, total = 70, factors = factors, vetoReasons = emptyList(), confidence = confidence)

    @Test
    fun `null评分_不渲染徽标`() {
        assertNull(ScoreBadgeDerivations.badgeKind(null, 1.0))
    }

    @Test
    fun `满权重_按domain_confidence映射_五档`() {
        val full = listOf(factor(1.0))
        assertEquals(Kind.MEASURED, ScoreBadgeDerivations.badgeKind(score(full, Confidence.MEASURED), 1.0))
        assertEquals(Kind.SATELLITE, ScoreBadgeDerivations.badgeKind(score(full, Confidence.SATELLITE), 1.0))
        assertEquals(Kind.MODEL, ScoreBadgeDerivations.badgeKind(score(full, Confidence.MODEL), 1.0))
        assertEquals(Kind.ESTIMATED, ScoreBadgeDerivations.badgeKind(score(full, Confidence.ESTIMATED), 1.0))
        assertEquals(Kind.UNCALIBRATED, ScoreBadgeDerivations.badgeKind(score(full, Confidence.UNAVAILABLE), 1.0))
    }

    @Test
    fun `权重降级_占比小于0p6_低置信优先`() {
        // 有效权重占比 0.5 < 0.6（§6.7，与 FishScoreCalculator wSum/Σw 同口径）→ LOW_CONFIDENCE
        assertEquals(Kind.LOW_CONFIDENCE, ScoreBadgeDerivations.badgeKind(score(listOf(factor(0.5)), Confidence.ESTIMATED), 1.0))
    }

    @Test
    fun `权重占比边界_恰为0p6_不标低置信`() {
        assertEquals(Kind.MODEL, ScoreBadgeDerivations.badgeKind(score(listOf(factor(0.6)), Confidence.MODEL), 1.0))
    }

    @Test
    fun `空因子或非法预设权重和_不虚构低置信`() {
        assertEquals(Kind.MODEL, ScoreBadgeDerivations.badgeKind(score(emptyList(), Confidence.MODEL), 1.0))
        assertEquals(Kind.MODEL, ScoreBadgeDerivations.badgeKind(score(listOf(factor(1.0)), Confidence.MODEL), 0.0))
    }

    @Test
    fun `有效权重占比_纯函数口径`() {
        assertEquals(0.5, ScoreBadgeDerivations.effectiveWeightRatio(listOf(factor(0.3), factor(0.2)), 1.0))
        assertNull(ScoreBadgeDerivations.effectiveWeightRatio(emptyList(), 1.0))
        assertNull(ScoreBadgeDerivations.effectiveWeightRatio(listOf(factor(1.0)), -1.0))
    }

    @Test
    fun `低置信阈值常量_与domain口径一致`() {
        assertTrue(ScoreBadgeDerivations.LOW_CONFIDENCE_RATIO == 0.6)
    }
}
