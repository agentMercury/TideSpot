package com.tidespot.core.domain

import com.tidespot.core.domain.algorithm.Presets
import com.tidespot.core.domain.algorithm.SubScores
import com.tidespot.core.domain.algorithm.VetoEvaluator
import com.tidespot.core.domain.model.Confidence
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather
import com.tidespot.core.domain.water.DoPhysics
import com.tidespot.core.domain.water.WaterTempFusion
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M4 必修 2（BLK-03）：编排层中间量单测——黄金对拍走生产管线后，对
 * FishScoreCalculator.scoreOne 的 Tw 融合 / ΔP3 降权 / 加权合成 / ΠV 组装逐项断言。
 * 另含 M4 必修 5（rain24h 接线 §6.2j）与 必修 4（FO-18 时区 §10.1-12 TzTest）的 domain 侧用例。
 */
class ScoreOrchestrationTest {

    private val calc = FishScoreCalculator()
    private val dayBase = LocalDateTime.of(2026, 11, 1, 0, 0).toEpochSecond(ZoneOffset.ofHours(8))
    private fun hour(h: Int) = dayBase + h * 3600L

    private fun input(epoch: Long, weather: HourlyWeather, marine: HourlyMarine? = null, tz: Double = 8.0) =
        FishScoreCalculator.HourlyScoreInput(epoch, weather, marine, tzOffsetHours = tz)

    private fun goodWeather(pressure: Double? = 1015.0, code: Int? = 0, rainMm: Double? = 0.0) = HourlyWeather(
        timeEpochSec = 0, temperatureC = 18.0, pressureMslHpa = pressure,
        apparentC = 16.0, windSpeedKmh = 10.0, windGustKmh = 18.0,
        cloudCoverPct = 40.0, rainMm = rainMm, weatherCode = code,
    )

    // ─────────────── 编排 1：Tw 融合（§6.1.2 谐波滞后 + α=0.70） ───────────────

    @Test
    fun `Tw融合_恒温序列_谐波滞后加α0_70精确还原题面Tw`() {
        val tw = 16.078
        // 32h 恒温序列：Tlag = 加权平均（谐波 w=1/(1+Δ)）= 恒温值；Tw = 0.70·Tlag + 0.30·now = 恒温值
        val inputs = (0 until 32).map { h ->
            input(hour(h), HourlyWeather(timeEpochSec = 0, temperatureC = tw, pressureMslHpa = 1015.0, weatherCode = 0))
        }
        val r = calc.calculate(inputs, Presets.FRESH_LURE, 119.04)[31]
        assertEquals(
            SubScores.tempMatch(tw, Presets.FRESH_LURE.tempOptC, Presets.FRESH_LURE.tempSigmaC),
            r.factors.first { it.id == "tempMatch" }.sub, 1e-9,
            "编排层 Tw == 题面值（谐波滞后 w=1/(1+Δ) + α=0.70，§6.1.2 / CHG-08 ①）",
        )
    }

    @Test
    fun `Tw融合_变温序列_编排层与WaterTempFusion逐位一致`() {
        val temps = (0 until 40).map { 10.0 + it * 0.5 }
        val inputs = temps.mapIndexed { h, t ->
            input(hour(h), HourlyWeather(timeEpochSec = 0, temperatureC = t, pressureMslHpa = 1015.0, weatherCode = 0))
        }
        val r = calc.calculate(inputs, Presets.FRESH_LURE, 119.04)[39]
        val expectedTw = WaterTempFusion.fuse(temps, 39, temps[39], null)!!
        assertEquals(
            SubScores.tempMatch(expectedTw, Presets.FRESH_LURE.tempOptC, Presets.FRESH_LURE.tempSigmaC),
            r.factors.first { it.id == "tempMatch" }.sub, 1e-12,
            "编排层必须调用 WaterTempFusion.fuse（31h 窗口），不得旁路或改权重",
        )
        // SST 优先（§6.1.1）：marine.sst 覆盖气温融合
        val withSst = inputs.mapIndexed { h, inp ->
            input(inp.timeEpochSec, inp.weather, HourlyMarine(timeEpochSec = 0, seaSurfaceTempC = 21.0))
        }
        val rSst = calc.calculate(withSst, Presets.FRESH_LURE, 119.04)[39]
        assertEquals(
            SubScores.tempMatch(21.0, Presets.FRESH_LURE.tempOptC, Presets.FRESH_LURE.tempSigmaC),
            rSst.factors.first { it.id == "tempMatch" }.sub, 1e-12,
        )
    }

    // ─────────────── 编排 2：ΔP3 缺 past 降权（CHG-07 修复 2 编排侧） ───────────────

    @Test
    fun `dP3缺past_权重0_14降到0_08且标注趋势数据不足_有值恢复`() {
        // 前 3 小时：index<3 → 降级路径
        val short = (0 until 3).map { h -> input(hour(h), goodWeather(pressure = 1015.0 + h)) }
        val shortResults = calc.calculate(short, Presets.FRESH_LURE, 119.04)
        shortResults.forEach { r ->
            val pressure = r.factors.first { it.id == "pressure" }
            assertEquals(0.08, pressure.weight, 1e-12, "§6.7：缺 past → 0.14→0.08")
            assertEquals("趋势数据不足", pressure.detail)
        }
        // 第 4 小时起 past 齐备 → 恢复 0.14、无标注
        val four = (0 until 4).map { h -> input(hour(h), goodWeather(pressure = 1015.0 + h)) }
        val p4 = calc.calculate(four, Presets.FRESH_LURE, 119.04)[3].factors.first { it.id == "pressure" }
        assertEquals(0.14, p4.weight, 1e-12)
        assertEquals(null, p4.detail)
    }

    // ─────────────── 编排 3：加权合成（null 因子剔除分母 + 低置信） ───────────────

    @Test
    fun `加权合成_null因子剔除分母_合成公式可由factors复算`() {
        val base = (0 until 25).map { h -> input(hour(h), goodWeather()) }
        val rb = calc.calculate(base, Presets.FRESH_LURE, 119.04)[24]
        val wSum = rb.factors.sumOf { it.weight }
        assertEquals(Presets.FRESH_LURE.weightSum, wSum, 1e-9, "良态序列无因子剔除")
        // ΠV 组装：total = round(base·ΠV·100)，base 可由 factors 反算（含 waterQuality 权重 0 不计入）
        val doP = DoPhysics.doPotential(18.0, 1015.0, Presets.FRESH_LURE.salinityPpt)
        val veto = VetoEvaluator.evaluate(0, 10.0, 18.0, 0.0, 0.0, null, doP)
        val baseScore = rb.factors.sumOf { it.sub * it.weight } / wSum
        assertEquals(
            (baseScore * veto.value * 100.0).roundToInt(),
            rb.total,
            "编排合成公式 §6.3/§6.5：Σw·s/Σw × ΠV × 100",
        )
        assertTrue(rb.vetoReasons.isEmpty(), "良态无否决（CHG-08 空列表）")
        assertEquals(Confidence.MODEL, rb.confidence)
    }

    @Test
    fun `加权合成_多维缺失有效权重比低于0_6_低置信且分数不崩`() {
        // 剔除 oxygen/tempMatch/comfort/wind/weather（气温全缺 + 无 apparent/wind/code）→
        // 仅剩 pressure+solunar+daypart，Σw = 0.39 < 0.6 → 低置信（§6.7）
        val stripped = (0 until 4).map { h ->
            input(hour(h), HourlyWeather(timeEpochSec = 0, pressureMslHpa = 1015.0))
        }
        val rs = calc.calculate(stripped, Presets.FRESH_LURE, 119.04)[3]
        assertEquals(Confidence.ESTIMATED, rs.confidence, "有效权重 <0.6 → 低置信徽标（§6.7）")
        val wSum = rs.factors.sumOf { it.weight }
        assertTrue(wSum < 0.6 * Presets.FRESH_LURE.weightSum, "Σw = $wSum")
        assertTrue(rs.factors.none { it.id == "oxygen" || it.id == "wind" || it.id == "weather" || it.id == "comfort" || it.id == "tempMatch" })
        assertTrue(rs.total in 0..100)
    }

    // ─────────────── 编排 4：ΠV 组装（§6.5） ───────────────

    @Test
    fun `ΠV组装_雷暴硬否决_reasons非空且total骤降`() {
        val calm = (0 until 25).map { h -> input(hour(h), goodWeather()) }
        val rc = calc.calculate(calm, Presets.FRESH_LURE, 119.04)[24]
        val storm = (0 until 25).map { h ->
            input(hour(h), goodWeather(code = if (h == 24) 95 else 0))
        }
        val rs = calc.calculate(storm, Presets.FRESH_LURE, 119.04)[24]
        assertTrue(rs.vetoReasons.isNotEmpty() && rs.vetoReasons.first().contains("雷暴"), "唯一硬否决入 reasons（§6.5）")
        assertTrue(rs.total <= rc.total / 10, "V ×0.05 → 分数骤降：${rs.total} vs ${rc.total}")
    }

    // ─────────────── M4 必修 5：rain24h 接线（§6.2j / FO-15） ───────────────

    @Test
    fun `rain24h_无雨_水质代理中性`() {
        val dry = (0 until 30).map { h -> input(hour(h), goodWeather(cloudRain())) }
        val r = calc.calculate(dry, Presets.FRESH_LURE, 119.04)[29]
        val w = r.factors.first { it.id == "waterQuality" }.sub
        // rain24h = 0.0（真实零值）→ turbidity = 0.5+0.25·gauss(60,60,40) = 0.75 中性高
        assertEquals(SubScores.waterQuality(oxygenOf(), 0.75, chlOf()), w, 1e-9)
    }

    @Test
    fun `rain24h_前24h强降水_浑水惩罚生效`() {
        val wet = (0 until 30).map { h -> input(hour(h), goodWeather(cloudRain(rain = 8.0))) }
        val dry = (0 until 30).map { h -> input(hour(h), goodWeather(cloudRain())) }
        val wetW = calc.calculate(wet, Presets.FRESH_LURE, 119.04)[29].factors.first { it.id == "waterQuality" }.sub
        val dryW = calc.calculate(dry, Presets.FRESH_LURE, 119.04)[29].factors.first { it.id == "waterQuality" }.sub
        assertTrue(wetW < dryW, "前 24h 累计 8mm/h → turbidity 降 0.4（FO-15 接线生效）：wet=$wetW dry=$dryW")
    }

    @Test
    fun `rain24h_序列不足24h_按已有样本累计_缺测小时不计入`() {
        // 3h 序列 @index2：窗口 = 前 2 小时（不足 24h）→ 按已有样本累计 = 16mm → turbidity 0（clip01 触底）
        val a = (0 until 3).map { h -> input(hour(h), goodWeather(cloudRain(rain = 8.0))) }
        val subA = calc.calculate(a, Presets.FRESH_LURE, 119.04)[2].factors.first { it.id == "waterQuality" }.sub
        // 对照：仅第 0 小时有雨（累计 8mm）→ turbidity = 0.35
        val b = (0 until 3).map { h -> input(hour(h), goodWeather(cloudRain(rain = if (h == 0) 8.0 else 0.0))) }
        val subB = calc.calculate(b, Presets.FRESH_LURE, 119.04)[2].factors.first { it.id == "waterQuality" }.sub
        assertTrue(subA < subB, "不足 24h 按已有样本累计：16mm($subA) < 8mm($subB)")
        // 缺测小时（rain/showers 全 null）不计入：窗口内仅 1 个 8mm 样本 + 1 个缺测小时 → 同 8mm
        val c = (0 until 3).map { h ->
            input(hour(h), goodWeather(cloudRain(rain = if (h == 0) 8.0 else null)))
        }
        val subC = calc.calculate(c, Presets.FRESH_LURE, 119.04)[2].factors.first { it.id == "waterQuality" }.sub
        assertEquals(subB, subC, 1e-9, "缺测小时不计入累计（§6.7 缺失策略），禁当 0 或造假")
    }

    // ─────────────── M4 必修 4：FO-18 时区（§10.1-12 TzTest domain 侧） ───────────────

    @Test
    fun `TzTest_JST加9_当地23_30hourLocal不串轴`() {
        // 当地 2026-11-09T23:30 JST = UTC 14:30 同日；编排层 hourLocal 必须 = 23.5
        val epoch = LocalDateTime.of(2026, 11, 9, 14, 30).toEpochSecond(ZoneOffset.UTC)
        val r = calc.calculate(listOf(input(epoch, goodWeather(), tz = 9.0)), Presets.FRESH_LURE, 119.04)[0]
        assertEquals(
            SubScores.daypart(23.5, 6.3, 17.4, false),
            r.factors.first { it.id == "daypart" }.sub, 1e-12,
            "JST 当地 23:30 → hourLocal=23.5（F-3.8/N-9）",
        )
        // 同一 epoch 按 +8 → 22.5（不串轴对照）
        val r8 = calc.calculate(listOf(input(epoch, goodWeather(), tz = 8.0)), Presets.FRESH_LURE, 119.04)[0]
        assertEquals(SubScores.daypart(22.5, 6.3, 17.4, false), r8.factors.first { it.id == "daypart" }.sub, 1e-12)
    }

    @Test
    fun `TzTest_加5_30半点时区_hourLocal正确`() {
        // UTC 14:30 + 5:30 = 当地 20:00
        val epoch = LocalDateTime.of(2026, 11, 9, 14, 30).toEpochSecond(ZoneOffset.UTC)
        val r = calc.calculate(listOf(input(epoch, goodWeather(), tz = 5.5)), Presets.FRESH_LURE, 119.04)[0]
        assertEquals(
            SubScores.daypart(20.0, 6.3, 17.4, false),
            r.factors.first { it.id == "daypart" }.sub, 1e-12,
            "+5:30 半点偏移 → hourLocal=20.0",
        )
    }

    // ─────────────── 辅助 ───────────────

    private fun cloudRain(rain: Double? = 0.0) = mapOf("rain" to rain)
    private fun goodWeather(extra: Map<String, Double?> = cloudRain()): HourlyWeather {
        val rain = extra["rain"]
        return HourlyWeather(
            timeEpochSec = 0, temperatureC = 18.0, pressureMslHpa = 1015.0,
            apparentC = 16.0, windSpeedKmh = 10.0, windGustKmh = 18.0,
            cloudCoverPct = 60.0, rainMm = rain, showersMm = null, weatherCode = 0,
        )
    }
    private fun oxygenOf(): Double {
        val tw = 18.0
        val doP = DoPhysics.doPotential(tw, 1015.0, Presets.FRESH_LURE.salinityPpt)
        return DoPhysics.oxygenScore(doP, DoPhysics.doRef(tw, Presets.FRESH_LURE.doBase))
    }
    private fun chlOf(): Double = SubScores.chlorophyllIndex(null, 18.0, 60.0)
}
