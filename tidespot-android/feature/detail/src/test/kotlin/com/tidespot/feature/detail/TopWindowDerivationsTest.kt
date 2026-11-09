package com.tidespot.feature.detail

import com.tidespot.core.domain.model.Confidence
import com.tidespot.core.domain.model.FactorScore
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.window.BestWindowFinder
import com.tidespot.feature.detail.detail.TopWindowDerivations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TopWindowDerivations 纯函数锁（F-3.5 Top10 现算 / §6.2b 模型极值 / §6.6 同周期去重输入）。
 * 红线：缺测跳过不插值、缺值禁 0 填充、内陆全 null = 空 map 语义。
 */
class TopWindowDerivationsTest {

    /** 2026-11-02T00:00+08:00 基准（与其他模块测试同源）。 */
    private fun epoch(h: Int): Long = 1798147200L + h * 3600L

    private fun marine(h: Int, level: Double?): HourlyMarine = HourlyMarine(
        timeEpochSec = epoch(h),
        waveHeightM = 1.0,
        wavePeriodS = 6.0,
        waveDirectionDeg = 148.0,
        seaLevelHeightMslM = level,
        oceanCurrentVelocityKmh = 2.0,
        seaSurfaceTempC = 18.0,
    )

    private fun score(h: Int, total: Int) = FishScoreResult(
        timeEpochSec = epoch(h),
        total = total,
        factors = listOf(FactorScore("solunar", 0.8, 0.17), FactorScore("tide", 0.9, 0.24)),
        vetoReasons = emptyList(),
        confidence = Confidence.MODEL,
    )

    // ──────────────────────────── tideRatesFromMarine（dh 一阶差分） ────────────────────────────

    @Test
    fun `dh差分_相邻整点相减_键为当前小时`() {
        val rates = TopWindowDerivations.tideRatesFromMarine(
            listOf(marine(0, 1.0), marine(1, 1.5), marine(2, 1.2)),
        )
        assertEquals(1.5 - 1.0, rates[epoch(1)]!!, 1e-9)
        assertEquals(1.2 - 1.5, rates[epoch(2)]!!, 1e-9)
        assertEquals(2, rates.size, "首小时无前驱不产出")
    }

    @Test
    fun `dh差分_缺测小时断开_不跨算不插值`() {
        val rates = TopWindowDerivations.tideRatesFromMarine(
            listOf(marine(0, 1.0), marine(1, null), marine(2, 2.0)),
        )
        assertTrue(rates.isEmpty(), "1.0 与 2.0 隔了缺测小时 → 不得跨算（禁插值/禁当 0）")
    }

    @Test
    fun `dh差分_非整点间隔不产出`() {
        val odd = listOf(marine(0, 1.0), marine(2, 2.0))   // 间隔 7200s
        assertTrue(TopWindowDerivations.tideRatesFromMarine(odd).isEmpty(), "间隔非 3600s 不算差分")
    }

    @Test
    fun `dh差分_内陆全null_空map语义`() {
        val inland = List(5) { marine(it, null) }
        assertTrue(TopWindowDerivations.tideRatesFromMarine(inland).isEmpty(), "全 null → 空 map = find 跳过同周期去重")
    }

    // ──────────────────────────── extremeTimes（§6.2b 模型极值） ────────────────────────────

    /** 两个完整涨落周期：h2 高潮（+→−）、h5 低潮（−→+）、h8 高潮。 */
    private fun twoCycleMarine() = listOf(
        marine(0, 1.0), marine(1, 2.0), marine(2, 3.0),        // 涨
        marine(3, 2.0), marine(4, 1.0),                         // 落
        marine(5, 2.0), marine(6, 3.0),                         // 涨
        marine(7, 2.0), marine(8, 1.0),                         // 落
    )

    @Test
    fun `模型极值_差分符号翻转点_共享小时为极值时刻`() {
        val extremes = TopWindowDerivations.extremeTimes(twoCycleMarine())
        // 差分：h1 +1, h2 +1, h3 −1, h4 −1, h5 +1, h6 +1, h7 −1, h8 −1
        // 翻转：+→− 在 (h2,h3) → 极值 = h2（高潮）；−→+ 在 (h4,h5) → 极值 = h4（低潮）；+→− 在 (h6,h7) → 极值 = h6
        assertEquals(listOf(epoch(2), epoch(4), epoch(6)), extremes)
    }

    @Test
    fun `模型极值_恒涨无翻转_空列表`() {
        val rising = listOf(marine(0, 1.0), marine(1, 2.0), marine(2, 3.0), marine(3, 4.0))
        assertTrue(TopWindowDerivations.extremeTimes(rising).isEmpty(), "单调涨无极值")
    }

    @Test
    fun `模型极值_缺测断开处不判定`() {
        val broken = listOf(marine(0, 1.0), marine(1, 3.0), marine(2, null), marine(3, 3.0), marine(4, 1.0))
        // 差分：h1 +2；h2 缺测断开；h4 −2 —— h1 与 h4 不相邻（间隔 2h）→ 无翻转判定
        assertTrue(TopWindowDerivations.extremeTimes(broken).isEmpty())
    }

    @Test
    fun `模型极值_内陆全null_空列表`() {
        assertTrue(TopWindowDerivations.extremeTimes(List(5) { marine(it, null) }).isEmpty())
    }

    // ──────────────────────────── top10（F-3.5 现算入口） ────────────────────────────

    @Test
    fun `top10_同源scores正常输出_与直接find等价`() {
        val scores = List(240) { h -> score(h, if (h in 10..13) 88 else 40) }
        val out = TopWindowDerivations.top10(scores, emptyList())
        assertEquals(1, out.windows.size)
        assertEquals(88, out.windows.first().peakScore)
        // 与直接调 find（同参 tideRates=空 map 语义）逐字段等价
        val direct = BestWindowFinder.find(scores, tideRates = emptyMap(), validHoursPresent = scores.size)
        assertEquals(direct.windows, out.windows)
    }

    @Test
    fun `top10_有dh时走同周期去重_无dh跳过`() {
        // 240h 全序列（validHoursPresent = scores.size，<24 会走禁用分支）；
        // 仅 h10=90、h11=70 达候选线 → 两窗中点差 1h（<2.5h）且 dh 同号 → 去重留 rank 高者
        val scores = List(240) { h -> score(h, when (h) { 10 -> 90; 11 -> 70; else -> 40 }) }
        val marine = listOf(marine(9, 1.0), marine(10, 1.5), marine(11, 2.0), marine(12, 2.5))
        val deduped = TopWindowDerivations.top10(scores, marine)
        assertEquals(1, deduped.windows.size, "同涨潮段（dh 恒正）两窗去重留 90 分窗")
        assertEquals(90, deduped.windows.first().peakScore)
    }

    @Test
    fun `top10_scores为空_走禁用文案`() {
        val out = TopWindowDerivations.top10(emptyList(), emptyList())
        assertTrue(out.windows.isEmpty())
        assertTrue(out.fallbackMessage?.contains("不足 24") == true, "空 scores → find 内置禁用文案：${out.fallbackMessage}")
    }

    @Test
    fun `top10_nowEpochSec剔除历史高分窗_CHG15偏差1`() {
        // h5=92（历史，now=第 100 小时）、h200/h201=85（未来）→ 历史窗不入选（SRS §6.6 时间域）
        val scores = List(240) { h -> score(h, when (h) { 5 -> 92; 200, 201 -> 85; else -> 40 }) }
        val out = TopWindowDerivations.top10(scores, emptyList(), nowEpochSec = epoch(100))
        assertEquals(1, out.windows.size, "历史高分窗不得入选：${out.windows.map { it.peakScore }}")
        assertEquals(85, out.windows[0].peakScore)
    }

    // ──────────────────────────── manualFit（§6.2b 规则 5 接线） ────────────────────────────

    @Test
    fun `manualFit_两条对准极值的观测_启用且lag为1h`() {
        val fit = TopWindowDerivations.manualFit(
            observations = listOf(epoch(2) + 3600, epoch(6) + 3600),   // 各滞后高潮极值 1h
            marine = twoCycleMarine(),
        )
        assertTrue(fit.adopted, "2 条有效观测应启用：${fit.message}")
        assertEquals(1, fit.lagHours)
        assertEquals(1.0, fit.gain, 1e-9)
    }

    @Test
    fun `manualFit_观测不足2条_透传未启用文案`() {
        val fit = TopWindowDerivations.manualFit(listOf(epoch(2) + 3600), twoCycleMarine())
        assertTrue(!fit.adopted)
        assertTrue(fit.message?.contains("再记") == true, "1 条 → 提示再记：${fit.message}")
    }

    @Test
    fun `manualFit_观测远离极值_不启用`() {
        val fit = TopWindowDerivations.manualFit(
            observations = listOf(epoch(20), epoch(21)),   // 距任何极值 > 6h
            marine = twoCycleMarine(),
        )
        assertTrue(!fit.adopted, "±6h 内无匹配极值 → 不启用：${fit.message}")
    }
}
