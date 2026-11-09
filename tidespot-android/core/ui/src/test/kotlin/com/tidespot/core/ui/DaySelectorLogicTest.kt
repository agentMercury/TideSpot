package com.tidespot.core.ui

import com.tidespot.core.ui.selector.DaySelectorLogic
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** DaySelector 日期范围（7/10 收敛）+ 当地时区「今天」边界（F-3.8 / §10.1-12 TzTest 口径）。 */
class DaySelectorLogicTest {

    private val zone: ZoneId = ZoneOffset.ofHours(9) // JST

    /** JST 2026-11-06 23:30（UTC 2026-11-06 14:30）：「今天」必须是当地 11-06，不能因 UTC 仍 11-06 而侥幸，也不能提前翻日。 */
    private val lateEvening = LocalDateTime.of(2026, 11, 6, 23, 30)
        .toInstant(ZoneOffset.ofHours(9)).epochSecond

    /** JST 2026-11-07 00:30（UTC 2026-11-06 15:30）：UTC 仍是 11-06，但当地已是 11-07。 */
    private val pastMidnight = LocalDateTime.of(2026, 11, 7, 0, 30)
        .toInstant(ZoneOffset.ofHours(9)).epochSecond

    @Test
    fun daysRange_coercedToSevenOrTen() {
        assertEquals(7, DaySelectorLogic.coerceDaysRange(7))
        assertEquals(10, DaySelectorLogic.coerceDaysRange(10))
        assertEquals(7, DaySelectorLogic.coerceDaysRange(9))   // 设置项只允许 7/10
        assertEquals(7, DaySelectorLogic.coerceDaysRange(3))
        assertEquals(10, DaySelectorLogic.coerceDaysRange(30))
    }

    @Test
    fun chips_sevenDays_todayFirst() {
        val chips = DaySelectorLogic.chips(7, zone, lateEvening)
        assertEquals(7, chips.size)
        assertEquals(0, chips[0].offsetDays)
        assertTrue(chips[0].chipLabel.startsWith("今天 "))
        assertEquals("11-06", chips[0].dateLabel)
        assertEquals("+1", chips[1].chipLabel)
        assertEquals(LocalDate.of(2026, 11, 7), chips[1].localDate)
    }

    @Test
    fun chips_tenDays_lastIsPlusNine() {
        val chips = DaySelectorLogic.chips(10, zone, lateEvening)
        assertEquals(10, chips.size)
        assertEquals("+9", chips[9].chipLabel)
        assertEquals(LocalDate.of(2026, 11, 15), chips[9].localDate)
    }

    @Test
    fun todayBoundary_followsSpotLocalZone_notUtc() {
        // 当地已过午夜（JST 11-07），UTC 仍是 11-06 → 今天=11-07（F-3.8 大阪点口径）
        val chips = DaySelectorLogic.chips(7, zone, pastMidnight)
        assertEquals(LocalDate.of(2026, 11, 7), chips[0].localDate)
        assertTrue(chips[0].chipLabel.startsWith("今天 "))
        // 同一 epoch 换 UTC 时区 → 今天仍 11-06（两时区互不串轴）
        val utcChips = DaySelectorLogic.chips(7, ZoneOffset.UTC, pastMidnight)
        assertEquals(LocalDate.of(2026, 11, 6), utcChips[0].localDate)
    }

    @Test
    fun todayBoundary_lateEveningDoesNotRollOver() {
        val chips = DaySelectorLogic.chips(7, zone, lateEvening)
        assertEquals(LocalDate.of(2026, 11, 6), chips[0].localDate)
    }

    @Test
    fun instantRoundTrip_isStableForFixedEpoch() {
        // chips 由固定 epoch 决定（禁依赖系统时钟），同输入必同输出
        val a = DaySelectorLogic.chips(7, zone, lateEvening)
        val b = DaySelectorLogic.chips(7, zone, lateEvening)
        assertEquals(a, b)
        assertEquals(7, a.size)
        // Instant↔epoch 一致性（防 JVM 默认时区渗入）
        assertEquals(lateEvening, Instant.ofEpochSecond(lateEvening).epochSecond)
    }
    // ── D14（CHG-16⑮）：跨零点「今天」自愈 ──

    @Test
    fun isSameLocalDay_sameEveningMinute_isTrue() {
        // 23:30 → 23:50 同一当地日：chips 无需重算
        assertTrue(DaySelectorLogic.isSameLocalDay(lateEvening, lateEvening + 1200, zone))
    }

    @Test
    fun isSameLocalDay_crossesMidnight_isFalse_triggersSelfHeal() {
        // 23:30 → 次日 00:30：跨零点 → chips 必须重算（「今天」自愈）
        assertTrue(!DaySelectorLogic.isSameLocalDay(lateEvening, pastMidnight, zone))
    }

    @Test
    fun isSameLocalDay_utcViewOfSamePair_isTrue_zonesDoNotBleed() {
        // 同一对 epoch 在 UTC 下均为 11-06 → 同日（两时区口径互不串轴）
        assertTrue(DaySelectorLogic.isSameLocalDay(lateEvening, pastMidnight, ZoneOffset.UTC))
    }
}

