package com.tidespot.core.domain

import com.tidespot.core.domain.algorithm.VetoEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * VetoContinuity 关键回归（SRS §10.1-4，CHG-08 重写为可执行规格；SRS §6.5 三条修正经验）。
 *
 * CHG-08 裁决：旧「相邻差 < 12」隐含**等间距输入**假设——对 45.1→54.7（跨 9.6 km/h）套用
 * <12 属量纲错误而非实现错误。新规格：在 [44, 56] km/h 按 **0.1 km/h 固定步长**（121 点）
 * 密集采样驱动 ΠV，断言：
 * ① V 序列**单调不升**；
 * ② 每个**等步长**的相邻分差（×100 后）< 12 分；
 * ③ 45.1→54.7（跨区大跨度）与 54.7→55.0（边界）两个对**仅断言单调与「分差 < 50」**
 *   （排除旧硬阈值 54.7→55.0 出现的 50 分级跳变），**不套用 <12**；
 * ④ V 关于输入**连续无阶跃**——由 ①② 联合覆盖：固定步长采样下「单调不升 + 每步分差
 *   有界 < 12」即排除任何阶跃，故不再单独设断言（此处即说明注释）。
 */
class VetoContinuityTest {

    /**
     * 驱动约定（§10.1-4）：gust 为扫描变量；其余因子固定良态——wind=10、dP3=1.0、rain=0.0、
     * wave/DO 传 null（内陆语义跳过浪项与低溶氧项）、wmo=0（无雷暴）。
     */
    private fun outcomeAt(gust: Double): VetoEvaluator.Outcome = VetoEvaluator.evaluate(
        wmoCode = 0, windSpeedKmh = 10.0, gustKmh = gust,
        dP3 = 1.0, rainMmPerH = 0.0, waveHeightM = null, doPotentialMgL = null,
    )

    /** [44.0, 56.0] km/h × 0.1 步长 = 121 点（§10.1-4 密集采样）。 */
    private val grid: List<Double> = (0..120).map { 44.0 + it / 10.0 }

    private val vetoes: List<Double> = grid.map { outcomeAt(it).value }

    // ────────────────────────── ①②：均匀网格连续性 ──────────────────────────

    @Test
    fun `① 密集采样 121 点 - V 序列单调不升`() {
        for (i in 1 until vetoes.size) {
            assertTrue(
                vetoes[i] <= vetoes[i - 1] + 1e-12,
                "阵风 ${grid[i]} km/h 处 veto 应不上升（单调不升，§10.1-4 ①）",
            )
        }
    }

    @Test
    fun `② 等步长 0_1 相邻分差小于 12 分 - 禁止硬阈值跳变`() {
        // sigmoid 最大斜率 k/4 = 0.045/km/h → 0.1 步长分差理论峰值约 0.45 分，远离 12 分红线
        for (i in 1 until vetoes.size) {
            val delta = (vetoes[i - 1] - vetoes[i]) * 100.0
            assertTrue(
                delta < 12.0,
                "阵风 ${grid[i - 1]}→${grid[i]} 等步长分差 $delta 应 < 12 分（§10.1-4 ②）",
            )
        }
    }

    // ────────────────────────── ③：跨区/边界对（不套 <12） ──────────────────────────

    @Test
    fun `③ 跨区对 45_1 到 54_7 - 仅断言单调与分差小于 50（无 50 分级跳变）`() {
        // 跨 9.6 km/h 的连续衰减总量（约 30+ 分）是 sigmoid 的物理形状而非阈值跳变；
        // 旧规则（阵风>55 → ×0.20）的荒谬之处是 50 分瞬间跳变，故只断言 < 50（§10.1-4 ③）
        val v1 = outcomeAt(45.1).value
        val v2 = outcomeAt(54.7).value
        assertTrue(v2 <= v1 + 1e-12, "45.1→54.7 必须单调不升")
        val delta = (v1 - v2) * 100.0
        assertTrue(delta < 50.0, "45.1→54.7 分差 $delta 必须远小于 50（无 50 分级跳变）")
    }

    @Test
    fun `③ 边界对 54_7 到 55_0 - 仅断言单调与分差小于 50（不套小于 12）`() {
        // 黄金经验点：旧硬阈值在 54.7→55.0 出现 50→12 分荒谬跳变；连续 sigmoid 下此处仅约 0.7 分
        val v1 = outcomeAt(54.7).value
        val v2 = outcomeAt(55.0).value
        assertTrue(v2 <= v1 + 1e-12, "54.7→55.0 必须单调不升")
        val delta = (v1 - v2) * 100.0
        assertTrue(delta < 50.0, "54.7→55.0 分差 $delta 必须远小于 50（无 50 分级跳变）")
    }

    // ────────────────────────── ④：连续无阶跃 ──────────────────────────
    // §10.1-4 ④ 由 ①（单调不升）+ ②（每步分差 < 12）联合覆盖：固定步长下不可能出现任何
    // 阶跃，故按规格以本注释说明，不另设断言。

    // ────────────────────────── 保留用例（不涉连续性规格） ──────────────────────────

    @Test
    fun `修正经验 2 - dP3 = -3_0（正常午后日变化）不触发重罚`() {
        // 用相对比值消除其它乘项（风等）的干扰：V(dP3=-3)/V(dP3=0) 应恰为 ΔP3 分项
        // = 1 − clip01((3.0−2.5)/3)·0.55 ≈ 0.9083（起点 −2.5，日变化量级仅轻微衰减）
        val v3 = VetoEvaluator.evaluate(0, 12.0, 22.0, dP3 = -3.0, rainMmPerH = 0.0, waveHeightM = null, doPotentialMgL = 8.5).value
        val v0 = VetoEvaluator.evaluate(0, 12.0, 22.0, dP3 = 0.0, rainMmPerH = 0.0, waveHeightM = null, doPotentialMgL = 8.5).value
        assertEquals(1 - (1.0 / 6.0) * 0.55, v3 / v0, 1e-9)
        assertTrue(v3 > 0.85, "日变化量级不得误杀（V=$v3）")
    }

    @Test
    fun `雷暴 WMO 大于等于 95 为唯一硬否决 x0_05 且原因入库`() {
        // 相对比值：雷暴项恰为 ×0.05（其它乘项在比值中抵消）
        val thunder = VetoEvaluator.evaluate(95, 12.0, 22.0, dP3 = 0.0, rainMmPerH = 0.0, waveHeightM = null, doPotentialMgL = 8.5).value
        val calm = VetoEvaluator.evaluate(0, 12.0, 22.0, dP3 = 0.0, rainMmPerH = 0.0, waveHeightM = null, doPotentialMgL = 8.5).value
        assertEquals(0.05, thunder / calm, 1e-9)
        val outcome = VetoEvaluator.evaluate(95, 12.0, 22.0, dP3 = 0.0, rainMmPerH = 0.0, waveHeightM = null, doPotentialMgL = 8.5)
        assertEquals(1, outcome.reasons.filter { it.contains("雷暴") }.size, "雷暴时 reasons 非空且恰含一条雷暴原因")
    }

    @Test
    fun `良态小时无否决原因 - 空列表语义（UI 判 isEmpty）`() {
        val outcome = VetoEvaluator.evaluate(0, 10.0, 20.0, dP3 = 1.0, rainMmPerH = 0.0, waveHeightM = 0.3, doPotentialMgL = 8.5)
        assertTrue(outcome.value >= 0.75)
        // §6.5 修正 3（CHG-08）：无原因时为空列表，判定用 isEmpty()（禁止 null/空串/占位文本）
        assertTrue(outcome.reasons.isEmpty(), "良态小时 reasons 必须为空列表（CHG-08 isEmpty() 口径）")
    }

    @Test
    fun `内陆点 wave = null 跳过浪项 - 与海上良态同值`() {
        val inland = VetoEvaluator.evaluate(0, 10.0, 20.0, 1.0, 0.0, waveHeightM = null, doPotentialMgL = 8.5)
        val seaCalm = VetoEvaluator.evaluate(0, 10.0, 20.0, 1.0, 0.0, waveHeightM = 0.05, doPotentialMgL = 8.5)
        // 0.05 m 小浪的 sigmoid ≈ 0.996，不应产生显著差异；内陆绝不因"无浪数据"被罚
        assertTrue(inland.value >= seaCalm.value - 0.01)
        assertTrue(inland.value > 0.9)
    }
}