package com.tidespot.feature.favorites

import com.tidespot.core.data.repo.DataSource
import com.tidespot.feature.favorites.favorites.FavoritesStaleLogic
import com.tidespot.feature.favorites.favorites.StaleLevel
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OPT-V11-11（P1）陈旧标记锁：TTL 复用（天气 3h / 海洋 6h）+ ≥48h 已过期 + 无记录不编造数据时间。
 */
class FavoritesStaleLogicTest {

    private val zone = ZoneOffset.ofHours(8)
    private val now = LocalDateTime.of(2026, 11, 10, 12, 0).toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
    private fun hoursAgo(h: Long): Long = now - h * 3_600_000L

    @Test
    fun weatherPastTtl_isStale_withDataTimeLabel() {
        val mark = FavoritesStaleLogic.mark(
            mapOf(DataSource.WEATHER to hoursAgo(4), DataSource.MARINE to hoursAgo(1)),
            now,
            zone,
        )
        assertEquals(StaleLevel.STALE, mark.level)
        assertEquals("陈旧", FavoritesStaleLogic.badge(mark.level))
        // 数据时间 = 最近一次成功拉取（海洋 1h 前 → 11:00）
        assertEquals("数据 11-10 11:00", mark.dataTimeLabel)
    }

    @Test
    fun latestBeyond48h_isExpired() {
        val mark = FavoritesStaleLogic.mark(
            mapOf(DataSource.WEATHER to hoursAgo(49), DataSource.MARINE to hoursAgo(49)),
            now,
            zone,
        )
        assertEquals(StaleLevel.EXPIRED, mark.level)
        assertEquals("已过期", FavoritesStaleLogic.badge(mark.level))
        assertTrue(mark.dataTimeLabel!!.startsWith("数据 "))
    }

    @Test
    fun freshSources_noMark() {
        val mark = FavoritesStaleLogic.mark(
            mapOf(DataSource.WEATHER to hoursAgo(1), DataSource.MARINE to hoursAgo(2)),
            now,
            zone,
        )
        assertEquals(StaleLevel.NONE, mark.level)
        assertNull(FavoritesStaleLogic.badge(mark.level))
        assertEquals("数据 11-10 11:00", mark.dataTimeLabel)
    }

    @Test
    fun noFetchRecord_noDataTime_noMark() {
        val mark = FavoritesStaleLogic.mark(emptyMap(), now, zone)
        assertEquals(StaleLevel.NONE, mark.level)
        assertNull(mark.dataTimeLabel)
    }
}