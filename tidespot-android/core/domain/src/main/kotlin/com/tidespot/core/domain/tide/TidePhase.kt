package com.tidespot.core.domain.tide

import com.tidespot.core.domain.algorithm.Solunar
import com.tidespot.core.domain.strings.DomainCopy
import com.tidespot.core.domain.strings.ZhDomainCopy
import kotlin.math.abs

/**
 * 潮汛术语（OPT-V11-23 / UX-23；D10 月龄制 + D13 活汛/死汛口径）。
 *
 * 纯函数、零请求：输入月龄（[Solunar.lunarAge]，§6.2c 已有），输出大潮/小潮与活汛/死汛。
 * - **大潮**：|月龄−0|≤1.5 或 |月龄−14.77|≤1.5（朔/望 ±1.5 天）
 * - **小潮**：|月龄−7.38|≤1.5 或 |月龄−22.15|≤1.5（上下弦 ±1.5 天）
 * - **活汛** = 潮差增大半周期；**死汛** = 潮差减小半周期
 * - D13 覆盖：大潮期恒活汛、小潮期恒死汛；过渡带（1.5 天带外）按潮差增减判定
 *
 * 数据区隔：仅展示层使用，**不得进入 FishScoreCalculator**（与水体判定隔离同等语义）。
 *
 * OPT-V11-13：枚举不再携带中文 label（本文件名零字面量）；展示名走 [DomainCopy]
 * （默认 [ZhDomainCopy]，零行为漂移）。
 */
object TidePhase {

    /** 潮差极值相位（天）：朔 / 望（大潮）与上下弦（小潮）。 */
    private val FULL_MOON_AGE = Solunar.SYNODIC_MONTH_DAYS / 2.0
    private val FIRST_QUARTER_AGE = Solunar.SYNODIC_MONTH_DAYS / 4.0
    private val LAST_QUARTER_AGE = Solunar.SYNODIC_MONTH_DAYS * 3.0 / 4.0

    /** 大潮/小潮判定半宽（天）。 */
    const val BAND_DAYS: Double = 1.5

    /** 大潮/小潮（NONE = 过渡带，非大非小）。 */
    enum class TideSize { SPRING, NEAP, NONE }

    /** 活汛/死汛。 */
    enum class TideCurrent { ACTIVE, DEAD }

    data class Phase(val size: TideSize, val current: TideCurrent) {
        /** UI chip 文案：大潮/小潮存在时「大潮 · 活汛」，过渡带仅「活汛」。 */
        fun label(copy: DomainCopy = ZhDomainCopy): String =
            listOfNotNull(copy.tideSizeLabel(size), copy.tideCurrentLabel(current)).joinToString(" · ")
    }

    /** 月龄归一化到 [0, 朔望月) —— 负数/超月龄（>29.53）一律折回。 */
    fun normalizeAge(ageDays: Double): Double {
        val p = Solunar.SYNODIC_MONTH_DAYS
        val raw = ageDays % p
        return if (raw < 0) raw + p else raw
    }

    /**
     * 月龄 → 潮汛。判定顺序：大潮带 → 小潮带 → 过渡带按潮差增减
     * （月龄位于「弦月→望」与「望→下弦之外的后半段」区间时潮差增大）。
     */
    fun of(ageDays: Double): Phase {
        val age = normalizeAge(ageDays)
        val p = Solunar.SYNODIC_MONTH_DAYS
        val spring = minOf(abs(age - 0.0), abs(age - p), abs(age - FULL_MOON_AGE)) <= BAND_DAYS
        val neap = minOf(abs(age - FIRST_QUARTER_AGE), abs(age - LAST_QUARTER_AGE)) <= BAND_DAYS
        val size = when {
            spring -> TideSize.SPRING
            neap -> TideSize.NEAP
            else -> TideSize.NONE
        }
        val current = when {
            // D13 覆盖：大潮期恒活汛、小潮期恒死汛（带外才看潮差增减）
            spring -> TideCurrent.ACTIVE
            neap -> TideCurrent.DEAD
            // 潮差增大半周期：弦月(7.38)→望(14.77)、望→新月(29.53/0)
            age in FIRST_QUARTER_AGE..FULL_MOON_AGE -> TideCurrent.ACTIVE
            age >= LAST_QUARTER_AGE -> TideCurrent.ACTIVE
            else -> TideCurrent.DEAD
        }
        return Phase(size, current)
    }

    /** 便捷：月龄 → chip 文案（UI 唯一入口，禁 Screen 内拼串）。 */
    fun labelFor(ageDays: Double, copy: DomainCopy = ZhDomainCopy): String = of(ageDays).label(copy)
}