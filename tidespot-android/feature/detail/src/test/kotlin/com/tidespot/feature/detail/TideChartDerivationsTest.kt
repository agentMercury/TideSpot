package com.tidespot.feature.detail

import com.tidespot.core.data.repo.TideCorrectionSummary
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.feature.detail.detail.TideChartDerivations
import com.tidespot.feature.detail.detail.TideChartDerivations.ExtremeType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D12 潮 Tab 纯派生锁（CHG-16⑭）：dt 序列对齐、高低潮局部极值分类、当地日过滤、
 * lag 平移校正对照（M'(h)=M(h−lag) ⇒ 极值延后 lag）。
 */
class TideChartDerivationsTest {

    private val zone = ZoneOffset.ofHours(9)
    private val today = LocalDate.of(2026, 11, 6)

    private fun epoch(h: Int): Long = LocalDateTime.of(2026, 11, 6, 0, 0)
        .toInstant(ZoneOffset.ofHours(9)).epochSecond + h * 3600L

    private fun marine(h: Int, level: Double?) =
        HourlyMarine(timeEpochSec = epoch(h), seaLevelHeightMslM = level)

    @Test
    fun `dtSeries_与日切片对齐_缺测null保真`() {
        val slices = listOf(
            com.tidespot.core.data.repo.HourSlice(epoch(0), null, marine(0, 1.0)),
            com.tidespot.core.data.repo.HourSlice(epoch(1), null, marine(1, 1.5)),
            com.tidespot.core.data.repo.HourSlice(epoch(2), null, marine(2, null)),
        )
        val rates = mapOf<Long, Double?>(epoch(0) to null, epoch(1) to 0.5, epoch(2) to null)
        val dt = TideChartDerivations.dtSeries(slices, rates)
        assertEquals(listOf<Double?>(null, 0.5, null), dt)
    }

    @Test
    fun `classifiedExtremes_单峰单谷_高低各一`() {
        // 1,2,3,2,1,0,1：h2 峰（+→−）HIGH、h5 谷（−→+）LOW
        val ms = listOf(1.0, 2.0, 3.0, 2.0, 1.0, 0.0, 1.0).mapIndexed { h, v -> marine(h, v) }
        val extremes = TideChartDerivations.classifiedExtremes(ms)
        assertEquals(2, extremes.size)
        assertEquals(ExtremeType.HIGH to epoch(2), extremes[0].type to extremes[0].epochSec)
        assertEquals(ExtremeType.LOW to epoch(5), extremes[1].type to extremes[1].epochSec)
    }

    @Test
    fun `classifiedExtremes_缺测断开不判定_恒0平潮无极值`() {
        val gap = listOf(1.0, 2.0, null, 2.0, 1.0).mapIndexed { h, v -> marine(h, v) }
        assertTrue(TideChartDerivations.classifiedExtremes(gap).isEmpty(), "缺测断开处不判定极值")
        val flat = listOf(1.0, 1.0, 1.0).mapIndexed { h, v -> marine(h, v) }
        assertTrue(TideChartDerivations.classifiedExtremes(flat).isEmpty(), "恒 0 差分（平潮）无极值")
    }

    @Test
    fun `dayExtremes_按当地日过滤`() {
        val ms = (0..47).map { h ->
            val v = 1.0 + kotlin.math.sin(h / 6.0 * Math.PI)
            marine(h, v)
        }
        val all = TideChartDerivations.classifiedExtremes(ms)
        val day0 = TideChartDerivations.dayExtremes(all, 0, zone, today)
        val day1 = TideChartDerivations.dayExtremes(all, 1, zone, today)
        assertTrue(day0.all { it.epochSec in epoch(0)..epoch(23) })
        assertTrue(day1.all { it.epochSec >= epoch(24) })
    }

    @Test
    fun `shiftByLag_零滞后原样_滞后整体延后`() {
        val ms = listOf(1.0, 2.0, 3.0, 2.0, 1.0).mapIndexed { h, v -> marine(h, v) }
        val extremes = TideChartDerivations.classifiedExtremes(ms)
        assertEquals(extremes, TideChartDerivations.shiftByLag(extremes, 0))
        val shifted = TideChartDerivations.shiftByLag(extremes, 2)
        assertEquals(extremes.map { it.epochSec + 7200 }, shifted.map { it.epochSec })
        assertEquals(extremes.map { it.type }, shifted.map { it.type })
    }

    @Test
    fun `correctionCompare_校正前后对照_lag平移`() {
        val ms = listOf(1.0, 2.0, 3.0, 2.0, 1.0, 0.0, 1.0).mapIndexed { h, v -> marine(h, v) }
        val extremes = TideChartDerivations.dayExtremes(TideChartDerivations.classifiedExtremes(ms), 0, zone, today)
        val cmp = TideChartDerivations.correctionCompare(
            extremes, TideCorrectionSummary(1, 0.0, 1.0, true), zone,
        )!!
        assertEquals(1, cmp.lagHours)
        assertTrue(cmp.beforeLine.contains("校正前"))
        assertTrue(cmp.afterLine.contains("校正后（滞后 1h）"))
        assertTrue(cmp.afterLine.contains("高潮") && cmp.afterLine.contains("低潮"))
    }

    @Test
    fun `correctionCompare_当日无极值_不渲染`() {
        assertNull(TideChartDerivations.correctionCompare(emptyList(), TideCorrectionSummary(1, 0.0, 1.0, true), zone))
    }

    @Test
    fun `extremesLine_空序列null_非空含高低潮`() {
        assertNull(TideChartDerivations.extremesLine(emptyList(), zone))
        val line = TideChartDerivations.extremesLine(
            listOf(TideChartDerivations.TideExtreme(ExtremeType.HIGH, epoch(2)),
                TideChartDerivations.TideExtreme(ExtremeType.LOW, epoch(5))),
            zone,
        )!!
        assertTrue(line.contains("高潮") && line.contains("低潮"))
        assertTrue(line.contains("02:00") && line.contains("05:00"))
    }
}
