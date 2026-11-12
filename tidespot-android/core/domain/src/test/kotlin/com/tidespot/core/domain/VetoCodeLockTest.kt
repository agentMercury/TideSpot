package com.tidespot.core.domain

import com.tidespot.core.domain.algorithm.Presets
import com.tidespot.core.domain.algorithm.VetoEvaluator
import com.tidespot.core.domain.model.Confidence
import com.tidespot.core.domain.model.FactorScore
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather
import com.tidespot.core.domain.model.VetoCode
import com.tidespot.core.domain.window.BestWindowFinder
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M6 CHG-16①/OPT-M5-04/FO-01 锁测试：机器可读否决码 [VetoCode] 与显示文案 vetoReasons
 * **同步产生/同步为空**；BestWindowFinder 窗口筛选**只认 codes**，改 THUNDERSTORM_REASON
 * 文案后行为不得漂移（i18n 前置护栏）。
 */
class VetoCodeLockTest {

    private val base = LocalDateTime.of(2026, 11, 2, 0, 0).toEpochSecond(ZoneOffset.ofHours(8))

    // ─────────────── 锁①：codes 与 reasons 同步产生/同步为空 ───────────────

    @Test
    fun `锁1_雷暴触发时codes含THUNDERSTORM且与reasons同步`() {
        val o = VetoEvaluator.evaluate(
            wmoCode = 95, windSpeedKmh = 5.0, gustKmh = 8.0, dP3 = 0.5,
            rainMmPerH = 0.0, waveHeightM = null, doPotentialMgL = null,
        )
        assertTrue(o.reasons.any { it.contains("雷暴") }, "雷暴原因入 reasons")
        assertTrue(VetoCode.THUNDERSTORM in o.codes, "机器码 THUNDERSTORM 同步产生")
        // 雷暴本身 ×0.05 → V<0.75 → 衰减明细同轮触发，FISHABILITY_DECAY 必须同时在码里
        assertTrue(VetoCode.FISHABILITY_DECAY in o.codes, "V<0.75 衰减明细 → FISHABILITY_DECAY 同步")
        // 同步性：reasons 条目数 = codes 条目数（雷暴文案 + 衰减明细 ⇔ 两码）
        assertEquals(o.reasons.size, o.codes.size)
    }

    @Test
    fun `锁1_普通衰减时codes含FISHABILITY_DECAY且无THUNDERSTORM`() {
        // 大风衰减（V<0.75）但无雷暴
        val o = VetoEvaluator.evaluate(
            wmoCode = 1, windSpeedKmh = 45.0, gustKmh = 60.0, dP3 = 0.0,
            rainMmPerH = 0.0, waveHeightM = null, doPotentialMgL = null,
        )
        assertTrue(o.value < 0.75, "构造前提：V<0.75 触发衰减明细")
        assertTrue(o.reasons.any { it.contains("可作钓性衰减") }, "衰减明细入 reasons")
        assertEquals(listOf(VetoCode.FISHABILITY_DECAY), o.codes)
    }

    @Test
    fun `锁1_良态小时codes与reasons同步为空`() {
        val o = VetoEvaluator.evaluate(
            wmoCode = 1, windSpeedKmh = 5.0, gustKmh = 8.0, dP3 = 0.5,
            rainMmPerH = 0.0, waveHeightM = null, doPotentialMgL = null,
        )
        assertTrue(o.reasons.isEmpty())
        assertTrue(o.codes.isEmpty(), "无否决 = codes 空列表（与 reasons 同步为空）")
        assertEquals(o.reasons.isEmpty(), o.codes.isEmpty())
    }

    @Test
    fun `锁1_评分管线全序列codes与reasons空非空逐小时同步`() {
        val calc = FishScoreCalculator()
        val inputs = (0 until 48).map { i ->
            FishScoreCalculator.HourlyScoreInput(
                timeEpochSec = base + i * 3600L,
                weather = HourlyWeather(
                    timeEpochSec = base + i * 3600L,
                    temperatureC = 18.0, pressureMslHpa = 1013.0, cloudCoverPct = 40.0,
                    rainMm = 0.0, showersMm = 0.0, weatherCode = if (i == 10) 95 else 1,
                    windSpeedKmh = if (i in 20..24) 42.0 else 10.0, windGustKmh = 16.0,
                    apparentC = 17.0,
                ),
                sunriseLocalHour = 6.3, sunsetLocalHour = 17.5, tzOffsetHours = 8.0,
            )
        }
        val results = calc.calculate(inputs, Presets.FRESH_LURE, 119.04)
        assertEquals(48, results.size)
        results.forEach { r ->
            assertEquals(
                r.vetoReasons.isEmpty(), r.vetoCodes.isEmpty(),
                "hour=${r.timeEpochSec}: codes 与 reasons 必须同步为空/非空",
            )
            if (r.vetoReasons.any { it.contains("雷暴") }) {
                assertTrue(VetoCode.THUNDERSTORM in r.vetoCodes, "雷暴文案 ⇔ THUNDERSTORM 码")
            }
            if (r.vetoReasons.any { it.contains("可作钓性衰减") }) {
                assertTrue(VetoCode.FISHABILITY_DECAY in r.vetoCodes, "衰减明细 ⇔ FISHABILITY_DECAY 码")
            }
        }
        assertTrue(results.any { VetoCode.THUNDERSTORM in it.vetoCodes }, "序列内确实有雷暴小时（覆盖有效性）")
        assertTrue(results.any { VetoCode.FISHABILITY_DECAY in it.vetoCodes }, "序列内确实有衰减小时（覆盖有效性）")
    }

    // ─────────────── 锁②：finder 只认 codes——改 THUNDERSTORM_REASON 文案行为不变 ───────────────

    private fun row(hour: Int, score: Int, reasons: List<String>, codes: List<VetoCode>) = FishScoreResult(
        timeEpochSec = base + hour * 3600L,
        total = score,
        factors = listOf(FactorScore("solunar", 0.8, 0.17), FactorScore("tide", 0.9, 0.24)),
        vetoReasons = reasons,
        confidence = Confidence.MODEL,
        vetoCodes = codes,
    )

    @Test
    fun `锁2_改雷暴文案后窗口筛选行为不变`() {
        val scores = List(240) { h -> if (h in 10..14) 85 else 40 }
        val baseline = scores.mapIndexed { h, s ->
            if (h == 10) row(h, s, listOf("雷暴（安全否决）"), listOf(VetoCode.THUNDERSTORM))
            else if (h in 11..14) row(h, s, listOf("可作钓性衰减 V=0.60"), listOf(VetoCode.FISHABILITY_DECAY))
            else row(h, s, emptyList(), emptyList())
        }
        val outBaseline = BestWindowFinder.find(baseline)
        // 文案 i18n 化：THUNDERSTORM_REASON 改成任意串（codes 不变）
        val renamed = scores.mapIndexed { h, s ->
            if (h == 10) row(h, s, listOf("Thunderstorm safety veto / 完全不同的文案"), listOf(VetoCode.THUNDERSTORM))
            else if (h in 11..14) row(h, s, listOf("decay note xyz"), listOf(VetoCode.FISHABILITY_DECAY))
            else row(h, s, emptyList(), emptyList())
        }
        val outRenamed = BestWindowFinder.find(renamed)
        // warnings 由 vetoReasons 文案渲染，随 i18n 变化属预期；筛选行为 = 窗口结构字段必须不变
        fun strip(w: List<com.tidespot.core.domain.model.BestWindow>) = w.map { it.copy(warnings = emptyList()) }
        assertEquals(strip(outBaseline.windows), strip(outRenamed.windows), "改 THUNDERSTORM_REASON 文案，窗口筛选（结构字段）逐字段不变")
        assertEquals(1, outRenamed.windows.size)
        assertEquals(base + 11 * 3600L, outRenamed.windows.first().startEpochSec, "雷暴小时仍按机器码排除")
    }

    @Test
    fun `锁2_仅文案含雷暴而无机器码_不得排除`() {
        // FO-01 反向护栏：文案里出现「雷暴」子串但 codes 无 THUNDERSTORM → 窗口照常入选
        val scores = List(240) { h -> if (h in 10..12) 85 else 40 }
        val rows = scores.mapIndexed { h, s ->
            if (h == 10) row(h, s, listOf("用户备注提到雷暴但非硬否决"), emptyList())
            else row(h, s, emptyList(), emptyList())
        }
        val out = BestWindowFinder.find(rows)
        assertEquals(1, out.windows.size, "finder 禁再 parse 文案（contains 雷漂移），只认 codes")
        assertEquals(base + 10 * 3600L, out.windows.first().startEpochSec)
    }

    @Test
    fun `锁2_读侧重建_无codes的历史库行_语义与vetoReasons为空一致`() {
        // 读侧（hourly_score 重建）旧行无 codes：等效「无否决」，不得因文案误排除
        val scores = List(240) { h -> if (h in 10..12) 85 else 40 }
        val rows = scores.mapIndexed { h, s -> row(h, s, listOf("雷暴（安全否决）").let { if (h == 10) it else emptyList() }, emptyList()) }
        val out = BestWindowFinder.find(rows)
        assertEquals(1, out.windows.size)
        assertEquals(base + 10 * 3600L, out.windows.first().startEpochSec)
    }
}