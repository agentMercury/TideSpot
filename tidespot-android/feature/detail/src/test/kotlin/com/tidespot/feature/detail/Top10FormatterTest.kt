package com.tidespot.feature.detail

import com.tidespot.core.domain.model.BestWindow
import com.tidespot.core.domain.tide.TideCalibrator
import com.tidespot.core.ui.format.Format
import com.tidespot.feature.detail.detail.Top10Formatter
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/** Top10Formatter 纯函数锁（F-3.5 显示格式；缺值 = — 禁 0 填充；档位透传禁重复解析）。 */
class Top10FormatterTest {

    private val zone: ZoneOffset = ZoneOffset.ofHours(9)   // JST（F-3.8 口径同 DetailStateHolderTest）

    private fun epoch(h: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 11, 6, h, minute).toInstant(ZoneOffset.ofHours(9)).epochSecond

    private fun window(
        peakScore: Int = 92,
        tier: String = "爆护窗口",
        topFactors: List<String> = listOf("solunar", "tide", "do"),
        warnings: List<String> = emptyList(),
    ) = BestWindow(
        startEpochSec = epoch(15),
        peakEpochSec = epoch(17),
        durationHours = 3,
        peakScore = peakScore,
        tier = tier,
        topFactors = topFactors,
        warnings = warnings,
        rank = 80.0,
    )

    @Test
    fun `time_当地墙上时间_月日时分`() {
        assertEquals("11月6日 17:00", Top10Formatter.time(epoch(17), zone))
    }

    @Test
    fun `time_跨日日期正确`() {
        assertEquals("11月7日 00:30", Top10Formatter.time(epoch(0, 30) + 24 * 3600, zone))
    }

    @Test
    fun `hm_紧凑时刻`() {
        assertEquals("17:05", Top10Formatter.hm(epoch(17, 5), zone))
    }

    @Test
    fun `duration_整小时`() {
        assertEquals("3 小时", Top10Formatter.duration(3))
    }

    @Test
    fun `peakScoreLine_分数加档位_档位透传`() {
        assertEquals("92 分 · 爆护窗口", Top10Formatter.peakScoreLine(window()))
        assertEquals("55 分 · 较差", Top10Formatter.peakScoreLine(window(peakScore = 55, tier = "较差")))
    }

    @Test
    fun `topFactorsLine_只取前2项顿号拼接`() {
        assertEquals("solunar、tide", Top10Formatter.topFactorsLine(window().topFactors))
        assertEquals("仅一项", Top10Formatter.topFactorsLine(listOf("仅一项")))
    }

    @Test
    fun `topFactorsLine_空列表显示占位_禁0填充`() {
        assertEquals(Format.MISSING, Top10Formatter.topFactorsLine(emptyList()))
    }

    @Test
    fun `fallbackLine_有兜底时刻_文案附加括号时刻`() {
        val line = Top10Formatter.fallbackLine("未来 7 天无 ≥60 分窗口，最高 55 分", epoch(13, 30), zone)
        assertEquals("未来 7 天无 ≥60 分窗口，最高 55 分（11月6日 13:30）", line)
    }

    @Test
    fun `fallbackLine_无兜底时刻_仅文案`() {
        val line = Top10Formatter.fallbackLine("有效数据不足 24 小时，最佳窗口功能暂不可用", null, zone)
        assertEquals("有效数据不足 24 小时，最佳窗口功能暂不可用", line)
    }

    @Test
    fun `calibrationLine_启用显示lag_未启用透传message`() {
        assertEquals("已启用：滞后 2h", Top10Formatter.calibrationLine(TideCalibrator.ManualTideFit(2, 0.0, 1.0, true, null)))
        assertEquals("再记 1 条即可校潮", Top10Formatter.calibrationLine(TideCalibrator.ManualTideFit(0, 0.0, 1.0, false, "再记 1 条即可校潮")))
        assertEquals("未启用", Top10Formatter.calibrationLine(TideCalibrator.ManualTideFit(0, 0.0, 1.0, false, null)))
    }
}
