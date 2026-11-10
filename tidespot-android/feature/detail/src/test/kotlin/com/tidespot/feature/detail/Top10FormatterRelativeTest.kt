package com.tidespot.feature.detail

import com.tidespot.core.domain.model.BestWindow
import com.tidespot.feature.detail.detail.Top10Formatter
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/** OPT-V11-06（P1）：Top10 条目时间标签相对格式锁（起始改「今天/明天/周X」，峰值仍 HH:mm）。 */
class Top10FormatterRelativeTest {

    private val zone: ZoneOffset = ZoneOffset.ofHours(9)

    private fun epoch(h: Int, min: Int = 0, plusDays: Long = 0): Long =
        LocalDateTime.of(2026, 11, 6, h, min).toInstant(ZoneOffset.ofHours(9)).epochSecond + plusDays * 86_400

    private fun window(startEpochSec: Long = epoch(15), peakEpochSec: Long = epoch(17)) = BestWindow(
        startEpochSec = startEpochSec,
        peakEpochSec = peakEpochSec,
        durationHours = 3,
        peakScore = 92,
        tier = "爆护窗口",
        topFactors = listOf("solunar", "tide"),
        warnings = emptyList(),
        rank = 80.0,
    )

    @Test
    fun startLine_sameDay_isToday() {
        assertEquals(
            "起始 今天 15:00 · 峰值 17:00 · 时长 3 小时",
            Top10Formatter.startLine(window(), zone, nowEpochSec = epoch(9)),
        )
    }

    @Test
    fun startLine_nextDay_isTomorrow() {
        assertEquals(
            "起始 明天 15:00 · 峰值 16:00 · 时长 3 小时",
            Top10Formatter.startLine(
                window(startEpochSec = epoch(15, plusDays = 1), peakEpochSec = epoch(16, plusDays = 1)),
                zone,
                nowEpochSec = epoch(20),
            ),
        )
    }

    @Test
    fun startLine_farDate_keepsAbsoluteDate() {
        assertEquals(
            "起始 11月20日 15:00 · 峰值 17:00 · 时长 3 小时",
            Top10Formatter.startLine(window(startEpochSec = epoch(15, plusDays = 14)), zone, nowEpochSec = epoch(9)),
        )
    }
}