package com.tidespot.core.domain

import com.tidespot.core.domain.algorithm.Presets
import com.tidespot.core.domain.algorithm.VetoEvaluator
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather
import com.tidespot.core.domain.tide.TideCalibrator
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * N-4 异常兜底 + 缺测安全侧行为（BLK-06 / FO-11 / OPT-02；CHG-07 P1 修复 1/2/4 强制用例）。
 * 全部断言「不抛异常且降级路径正确」（SRS N-4 强制异常用例）。
 */
class ResilienceTest {

    private val calc = FishScoreCalculator()
    private val dayBase = LocalDateTime.of(2026, 11, 1, 0, 0).toEpochSecond(ZoneOffset.ofHours(8))
    private fun hour(h: Int) = dayBase + h * 3600L

    private fun input(h: Int, weather: HourlyWeather, marine: HourlyMarine? = null) =
        FishScoreCalculator.HourlyScoreInput(hour(h), weather, marine, tzOffsetHours = 8.0)

    private fun goodWeather(pressure: Double? = 1015.0) = HourlyWeather(
        timeEpochSec = 0, temperatureC = 18.0, pressureMslHpa = pressure,
        apparentC = 16.0, windSpeedKmh = 10.0, windGustKmh = 18.0,
        cloudCoverPct = 40.0, rainMm = 0.0, weatherCode = 0,
    )

    // ─────────────── 修复 4：强制异常用例 ───────────────

    @Test
    fun `空序列调用 FishScoreCalculator - 返回空结果不抛异常（N-4）`() {
        val results = calc.calculate(emptyList(), Presets.FRESH_LURE, lonDeg = 119.04)
        assertTrue(results.isEmpty(), "空 slices → 空 scoreList（不得抛 IllegalArgumentException）")
    }

    @Test
    fun `样本小于 2 调 TideCalibrator - 返回未校正不抛异常（N-4）`() {
        val empty = TideCalibrator.calibrate(emptyList<Double?>(), emptyList<Double?>())
        assertTrue(!empty.adopted, "0 对齐样本 → 未校正")
        assertEquals(0, empty.alignedSamples)
        assertTrue(empty.rejectReason!!.contains("样本"))
        assertEquals(noiseOf(1), TideCalibrator.apply(listOf(noiseOf(1)[0]), empty), "未采纳时 apply 原样返回")

        val single = TideCalibrator.calibrate(listOf(1.0), listOf<Double?>(1.2))
        assertTrue(!single.adopted, "1 对齐样本 → 未校正")
        assertTrue(single.rejectReason!!.contains("样本"))
    }

    private fun noiseOf(n: Int) = List(n) { it.toDouble() }

    @Test
    fun `缺测否决输入 - VetoEvaluator 不抛异常且按保守档衰减`() {
        // 全缺测（wind/gust/dP3/rain 均 null）：不抛，value ∈ (0,1]
        val allMissing = VetoEvaluator.evaluate(0, null, null, null, null, waveHeightM = null, doPotentialMgL = null)
        assertTrue(allMissing.value in 0.0..1.0 && allMissing.value > 0.0)
        // 保守方向：缺测 veto 严格小于"按良态 0 值"的 veto（旧 bug 行为）
        val calm = VetoEvaluator.evaluate(0, 10.0, 20.0, 1.0, 0.0, waveHeightM = null, doPotentialMgL = 8.5)
        assertTrue(allMissing.value < calm.value, "缺测 ${allMissing.value} 必须比良态 ${calm.value} 更保守")
        // 触发衰减 → 必须给出原因（§6.5 修正 3 / CHG-08：非空 List，判定用 isEmpty()）
        assertTrue(allMissing.reasons.isNotEmpty())

        // wind 单独缺测 → ×0.50 档（sigmoid 拐点 34），不得按"无风"良态处理
        val windMissing = VetoEvaluator.evaluate(0, null, 20.0, 1.0, 0.0, waveHeightM = null, doPotentialMgL = 8.5)
        val windCalm = VetoEvaluator.evaluate(0, 10.0, 20.0, 1.0, 0.0, waveHeightM = null, doPotentialMgL = 8.5)
        assertTrue(windMissing.value < windCalm.value, "wind 缺测不得按无风良态处理")
        // 恰为拐点档：V(null)/V(消风) 比值 = sig(34,34,0.30)/sig(0,34,0.30) = 0.5×(1+e^(−10.2)) ≈ 0.5000186
        // （sigmoid 在 x=0 处 ≈0.999963 而非恰 1，故容差取 1e-3）
        val noWindTerm = VetoEvaluator.evaluate(0, 0.0, 20.0, 1.0, 0.0, waveHeightM = null, doPotentialMgL = 8.5)
        assertEquals(0.5, windMissing.value / noWindTerm.value, 1e-3)

        // rain 缺测 → 不得假设无雨（×0.65 档）
        val rainMissing = VetoEvaluator.evaluate(0, 10.0, 20.0, 1.0, null, waveHeightM = null, doPotentialMgL = 8.5)
        val rainDry = VetoEvaluator.evaluate(0, 10.0, 20.0, 1.0, 0.0, waveHeightM = null, doPotentialMgL = 8.5)
        assertTrue(rainMissing.value < rainDry.value, "rain 缺测不得按 0（无雨）处理")
        val noRainTerm = VetoEvaluator.evaluate(0, 10.0, 20.0, 1.0, 0.0, waveHeightM = null, doPotentialMgL = 8.5)
        assertEquals(0.65, rainMissing.value / noRainTerm.value, 1e-9)
    }

    @Test
    fun `dP3 缺测 - 代入惩罚起点 -2_5（乘子 1_0，不视为无变化也不误杀）`() {
        // 保守档位取值依据（修复 1 报告）：信息缺失已由 §6.7 权重降级 0.14→0.08 承担；
        // 再叠加 ΠV 衰减会违反 §6.5 修正 2"日变化量级不得误杀"对未知数据的保护；
        // −2.5 是惩罚起点 = "不视为无变化"的最保守且不误杀的点（乘子恰为 1.0）。
        val missing = VetoEvaluator.evaluate(0, 12.0, 20.0, dP3 = null, rainMmPerH = 0.0, waveHeightM = null, doPotentialMgL = 8.5)
        val onset = VetoEvaluator.evaluate(0, 12.0, 20.0, dP3 = -2.5, rainMmPerH = 0.0, waveHeightM = null, doPotentialMgL = 8.5)
        assertEquals(onset.value, missing.value, 1e-12, "缺测 dP3 的乘子必须等于代入 −2.5 的乘子")
        // 不得越过起点误杀：与 dP3=−3.0（真实骤降）必须可区分
        val realDrop = VetoEvaluator.evaluate(0, 12.0, 20.0, dP3 = -3.0, rainMmPerH = 0.0, waveHeightM = null, doPotentialMgL = 8.5)
        assertTrue(missing.value > realDrop.value, "缺测不得比真实日变化骤降罚得更重")
    }

    // ─────────────── 修复 2：ΔP3 缺 past 降权 + 标注 ───────────────

    @Test
    fun `缺 past 算不了 dP3 - pressure 权重 0_08 且标注趋势数据不足`() {
        // 3 小时序列：所有小时 index<3 → 全部走降级路径
        val short = (0 until 3).map { h -> input(h, goodWeather(pressure = 1015.0 + h)) }
        val shortResults = calc.calculate(short, Presets.FRESH_LURE, 119.04)
        assertEquals(3, shortResults.size)
        for (r in shortResults) {
            val pressure = r.factors.firstOrNull { it.id == "pressure" }
            assertTrue(pressure != null, "气压有值 → pressure 因子不剔除")
            assertEquals(0.08, pressure!!.weight, 1e-12, "缺 past → 权重 0.14→0.08（§6.7）")
            assertEquals("趋势数据不足", pressure.detail)
        }

        // 第 4 小时（index=3）起 past 齐备 → 恢复正常权重 0.14、无标注（有值输入行为与旧实现一致）
        val four = (0 until 4).map { h -> input(h, goodWeather(pressure = 1015.0 + h)) }
        val fourth = calc.calculate(four, Presets.FRESH_LURE, 119.04)[3]
        val p4 = fourth.factors.first { it.id == "pressure" }
        assertEquals(0.14, p4.weight, 1e-12)
        assertNull(p4.detail)
    }

    @Test
    fun `气压 3h 前缺测 - 同样走降级路径（无法计算 dP3）`() {
        // index=3 但 pressures[0]=null → prevP3 为 null → 降级
        val inputs = listOf(
            input(0, goodWeather(pressure = null)),
            input(1, goodWeather(pressure = 1015.0)),
            input(2, goodWeather(pressure = 1015.2)),
            input(3, goodWeather(pressure = 1015.4)),
        )
        val r = calc.calculate(inputs, Presets.FRESH_LURE, 119.04)[3]
        val pressure = r.factors.first { it.id == "pressure" }
        assertEquals(0.08, pressure.weight, 1e-12)
        assertEquals("趋势数据不足", pressure.detail)
    }

    @Test
    fun `rain 全分量缺测 - 不加小雨 bonus 且 total 有值（N-4 不崩）`() {
        // WMO 51（小雨码）：旧实现 rain 缺测当 0 → 误加 +0.12 bonus；现在缺测 → bonus 0
        val withRainField = (0 until 4).map { h ->
            input(h, HourlyWeather(
                timeEpochSec = 0, temperatureC = 18.0, pressureMslHpa = 1015.0,
                apparentC = 16.0, windSpeedKmh = 10.0, weatherCode = 51,
                rainMm = 0.0, showersMm = 0.0,
            ))
        }
        val missingRain = (0 until 4).map { h ->
            input(h, HourlyWeather(
                timeEpochSec = 0, temperatureC = 18.0, pressureMslHpa = 1015.0,
                apparentC = 16.0, windSpeedKmh = 10.0, weatherCode = 51,
                rainMm = null, showersMm = null,
            ))
        }
        val a = calc.calculate(withRainField, Presets.FRESH_LURE, 119.04)[3]
        val b = calc.calculate(missingRain, Presets.FRESH_LURE, 119.04)[3]
        val subA = a.factors.first { it.id == "weather" }.sub
        val subB = b.factors.first { it.id == "weather" }.sub
        assertEquals(0.82 + 0.12, subA, 1e-9, "有值 rain=0 → WMO51 基值 0.82 + 小雨 bonus 0.12（行为不变）")
        assertEquals(0.82, subB, 1e-9, "rain 缺测 → 只剩基值、不加 bonus（§6.5/CHG-07 修复 1）")
        assertTrue(b.total in 0..100)
    }

    @Test
    fun `有值输入行为零变化 - 良态小时分数不受缺测修复影响`() {
        // 完整良态序列（所有输入有值）：total 与 vetoReasons 空列表语义之外不应有任何行为漂移（CHG-08）
        val inputs = (0 until 25).map { h -> input(h, goodWeather(), marine = HourlyMarine(timeEpochSec = 0)) }
        val results = calc.calculate(inputs, Presets.FRESH_LURE, 119.04)
        for (r in results.drop(24)) {   // index≥3 且 index≥24 → 全部走有值路径
            assertTrue(r.vetoReasons.isEmpty(), "良态有值小时无否决原因（CHG-08 空列表口径）")
            assertTrue(r.total > 50, "良态有值小时分数不被修复拖累：${r.total}")
        }
    }
}
