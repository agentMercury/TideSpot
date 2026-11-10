package com.tidespot.feature.detail

import com.tidespot.core.domain.tide.TideObservationType
import com.tidespot.feature.detail.detail.TideObservationEntryLogic
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OPT-V11-07（P1）观测录入纯函数锁：默认当前时刻、补记解析与范围校验、模型对照文案。
 */
class TideObservationEntryLogicTest {

    private val zone = ZoneOffset.ofHours(9)
    private fun epoch(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).toInstant(ZoneOffset.ofHours(9)).epochSecond

    private val now = epoch(2026, 11, 10, 12, 0)

    @Test
    fun defaultText_isLocalNow() {
        assertEquals("2026-11-10 12:00", TideObservationEntryLogic.defaultText(now, zone))
    }

    @Test
    fun parse_validPastTime_forBackfill() {
        val p = TideObservationEntryLogic.parse("2026-11-09 06:10", zone, now)
        assertTrue(p.ok)
        assertEquals(epoch(2026, 11, 9, 6, 10), p.epochSec)
        assertNull(p.error)
    }

    @Test
    fun parse_badFormat_rejects() {
        assertEquals(
            TideObservationEntryLogic.ParseError.BAD_FORMAT,
            TideObservationEntryLogic.parse("2026/11/09 06:10", zone, now).error,
        )
    }

    @Test
    fun parse_beyondWindow_rejects() {
        val p = TideObservationEntryLogic.parse("2026-10-01 06:10", zone, now)
        assertNull(p.epochSec)
        assertEquals(TideObservationEntryLogic.ParseError.OUT_OF_RANGE, p.error)
    }

    @Test
    fun comparisonLine_reportsEarlierLater() {
        val marker = epoch(2026, 11, 10, 6, 0)
        val markers = mapOf(TideObservationType.HIGH to listOf(marker))
        // 晚 0.5h
        assertEquals(
            "模型高潮 06:00 · 你记录晚 0.5h",
            TideObservationEntryLogic.comparisonLine(markers, TideObservationType.HIGH, marker + 1800, zone),
        )
        // 早 1.0h
        assertEquals(
            "模型高潮 06:00 · 你记录早 1.0h",
            TideObservationEntryLogic.comparisonLine(markers, TideObservationType.HIGH, marker - 3600, zone),
        )
        // 基本一致（<0.25h）
        assertEquals(
            "模型高潮 06:00 · 你记录基本一致",
            TideObservationEntryLogic.comparisonLine(markers, TideObservationType.HIGH, marker + 300, zone),
        )
    }

    @Test
    fun comparisonLine_noMarkerWithinWindow_returnsNull() {
        val markers = mapOf(TideObservationType.LOW to listOf(epoch(2026, 11, 10, 6, 0)))
        assertNull(
            TideObservationEntryLogic.comparisonLine(markers, TideObservationType.LOW, epoch(2026, 11, 10, 14, 0), zone),
        )
    }

    @Test
    fun observationLine_carriesTypeAndClock() {
        assertEquals(
            "我开始涨潮 · 11月10日 06:10",
            TideObservationEntryLogic.observationLine(TideObservationType.RISING_START, epoch(2026, 11, 10, 6, 10), zone),
        )
        // 历史无类型 → 「高低潮」
        assertEquals(
            "高低潮 · 11月10日 06:10",
            TideObservationEntryLogic.observationLine(null, epoch(2026, 11, 10, 6, 10), zone),
        )
        assertNotNull(TideObservationEntryLogic.errorText(TideObservationEntryLogic.ParseError.BAD_FORMAT))
    }
}