package com.tidespot.core.domain

import com.tidespot.core.domain.model.BestWindow
import com.tidespot.core.domain.window.DetailFocus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 详情页初始定位（issue #3 体验跟进）。
 *
 * 覆盖：跨日、边界、超出 daysRange、时区偏移、当日最高分标注。
 */
class DetailFocusTest {

    private val tz = 8 * 3600   // UTC+8

    /** 构造一个峰值落在本地某日某时的窗口。 */
    private fun winAt(localEpochSec: Long, peak: Int = 88): BestWindow = BestWindow(
        startEpochSec = localEpochSec,
        peakEpochSec = localEpochSec,
        durationHours = 4,
        peakScore = peak,
        tier = "爆护窗口",
        topFactors = emptyList(),
        warnings = emptyList(),
    )

    /** 本地 2026-11-17 10:00 的 epoch（UTC 02:00；已按 UTC+8 换算好，不要重复减 tz）。 */
    private val nowLocal10 = 1794880800L

    @Test
    fun `同日窗口 - 定位到今天对应小时`() {
        // 本地今天 17:00
        val today = nowLocal10 - 10 * 3600L + 17 * 3600L
        val f = DetailFocus.locateWindow(winAt(today), nowLocal10, tz, 7)
        assertNotNull(f)
        assertEquals(0, f.dayIndex)
        assertEquals(17, f.hourIndex)
    }

    @Test
    fun `跨日窗口 - 定位到明天`() {
        val tomorrow17 = nowLocal10 - 10 * 3600L + 24 * 3600L + 17 * 3600L
        val f = DetailFocus.locateWindow(winAt(tomorrow17), nowLocal10, tz, 7)
        assertNotNull(f)
        assertEquals(1, f.dayIndex)
        assertEquals(17, f.hourIndex)
    }

    @Test
    fun `超出 daysRange - 返回 null`() {
        // 有效范围是 0 until daysRange（与 DetailStateHolder.selectDay 同口径）
        // daysRange=7 → 0..6；daysRange=10 → 0..9
        val todayStart = nowLocal10 - 10 * 3600L
        val day7 = todayStart + 7L * 24 * 3600L + 8 * 3600L
        val day9 = todayStart + 9L * 24 * 3600L + 8 * 3600L
        assertNull(DetailFocus.locateWindow(winAt(day7), nowLocal10, tz, 7), "第 7 天（index 7）超出 7 日范围 0..6")
        assertNotNull(DetailFocus.locateWindow(winAt(day9), nowLocal10, tz, 10), "index 9 在 10 日范围 0..9 内")
        assertNull(DetailFocus.locateWindow(winAt(day9), nowLocal10, tz, 7), "index 9 超出 7 日范围")
    }

    @Test
    fun `过去窗口 - 返回 null（详情页不回看历史窗口）`() {
        val yesterday = nowLocal10 - 24 * 3600L
        assertNull(DetailFocus.locateWindow(winAt(yesterday), nowLocal10, tz, 7))
    }

    @Test
    fun `午夜边界 - 00 00 归入当天第 0 小时`() {
        val todayMidnight = nowLocal10 - 10 * 3600L
        val f = DetailFocus.locateWindow(winAt(todayMidnight), nowLocal10, tz, 7)
        assertNotNull(f)
        assertEquals(0, f.dayIndex)
        assertEquals(0, f.hourIndex)
    }

    @Test
    fun `时区偏移生效 - 同一 epoch 在 UTC 与 UTC+8 下日期不同`() {
        // 本地 11-17 04:00 = 今天本地 00:00 + 4 小时（UTC 前一天 20:00）
        val utcEvening = nowLocal10 - 10 * 3600L + 4 * 3600L
        val fUtc8 = DetailFocus.locateWindow(winAt(utcEvening), nowLocal10, tz, 7)
        assertNotNull(fUtc8)
        assertEquals(0, fUtc8.dayIndex)
        assertEquals(4, fUtc8.hourIndex)
    }

    @Test
    fun `bestOfDay - 返回最高分与本地时刻`() {
        val base = nowLocal10 - 10 * 3600L   // 今天本地 00:00
        val scores = listOf(
            base + 6 * 3600L to 91,
            base + 17 * 3600L to 88,
            base + 12 * 3600L to 70,
        )
        val best = DetailFocus.bestOfDay(scores, tz)
        assertEquals(91 to 6, best)
    }

    @Test
    fun `bestOfDay - 空序列返回 null`() {
        assertNull(DetailFocus.bestOfDay(emptyList(), tz))
    }

    @Test
    fun `bestOfDay - 同分取靠前者（maxByOrNull 语义）`() {
        val base = nowLocal10 - 10 * 3600L
        val scores = listOf(base + 3 * 3600L to 90, base + 20 * 3600L to 90)
        val best = DetailFocus.bestOfDay(scores, tz)
        assertEquals(90, best?.first)
    }
}
