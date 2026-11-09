package com.tidespot.core.domain

import com.tidespot.core.domain.algorithm.SubScores
import com.tidespot.core.domain.algorithm.VetoEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 气压趋势（§10.1-5 PressureTrendTest；SRS §6.2d/§6.5）。
 * 要点：ΔP3=−2.4（日变化）不触发重罚；ΔP3=−5 显著衰减；不得只用绝对气压跨地区判定。
 */
class PressureTrendTest {

    @Test
    fun `ΔP3 = -2_4 属日变化 - 子分不重罚且 veto 不触发`() {
        val sub = SubScores.pressure(1015.0, dP3 = -2.4, dP24 = 0.0)
        assertTrue(sub > 0.45, "−2.4 hPa/3h 子分 $sub 不应塌到低档")
        // 相对比值消除风等其它乘项：V(−2.4)/V(0) 应恰为 1（−2.4 在惩罚起点 −2.5 之内）
        val v24 = VetoEvaluator.evaluate(0, 12.0, 20.0, dP3 = -2.4, rainMmPerH = 0.0, waveHeightM = null, doPotentialMgL = 8.5).value
        val v0 = VetoEvaluator.evaluate(0, 12.0, 20.0, dP3 = 0.0, rainMmPerH = 0.0, waveHeightM = null, doPotentialMgL = 8.5).value
        assertEquals(1.0, v24 / v0, 1e-9, "−2.4 属日变化，ΠV 不应衰减")
    }

    @Test
    fun `ΔP3 = -5 显著衰减`() {
        val mild = SubScores.pressure(1015.0, -2.4, 0.0)
        val sharp = SubScores.pressure(1015.0, -5.0, 0.0)
        assertTrue(sharp < mild - 0.1, "骤降应显著低于日变化：$sharp vs $mild")
        val veto = VetoEvaluator.evaluate(0, 12.0, 20.0, dP3 = -5.0, rainMmPerH = 0.0, waveHeightM = null, doPotentialMgL = 8.5)
        assertTrue(veto.value < 0.95, "−5 hPa/3h 应触发可感知衰减（V=${veto.value}）")
    }

    @Test
    fun `高原点低绝对气压不误判 - 相对项归一`() {
        // 拉萨型地表压 ~650 hPa：绝对项归零，但趋势平稳时子分仍应可用（trend+24h 项主导）
        val plateau = SubScores.pressure(650.0, dP3 = 1.0, dP24 = 3.0)
        val sea = SubScores.pressure(1020.0, dP3 = 1.0, dP24 = 3.0)
        // 高原只丢绝对气压项（0.32 权重），趋势项贡献一致 → 下限 ≥ sea − 0.32
        assertTrue(plateau > 0.45, "高原点子分 $plateau 不得系统性判'别去'")
        assertTrue(sea - plateau < 0.32 + 0.01)
    }
}
