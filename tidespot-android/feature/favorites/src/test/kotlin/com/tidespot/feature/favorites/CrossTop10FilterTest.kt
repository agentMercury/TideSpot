package com.tidespot.feature.favorites

import com.tidespot.core.domain.model.BestWindow
import com.tidespot.core.domain.window.Top10TimeFilter
import com.tidespot.feature.favorites.favorites.CrossSpotTop10Entry
import com.tidespot.feature.favorites.favorites.CrossTop10Filter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OPT-A-04 跨点 Top10 筛选锁（设计稿 OV01 口径）：
 * ≥60 分门槛 + 时段相交过滤；宁可空列表，也不凑数。
 */
class CrossTop10FilterTest {

    private fun entry(name: String, score: Int, startSec: Long, hours: Int = 4): CrossSpotTop10Entry =
        CrossSpotTop10Entry(
            spotName = name,
            spot = null,
            window = BestWindow(
                startEpochSec = startSec,
                peakEpochSec = startSec + 3600,
                durationHours = hours,
                peakScore = score,
                tier = "保护窗口",
                topFactors = emptyList(),
                warnings = emptyList(),
                rank = score.toDouble(),
            ),
        )

    @Test
    fun `小于60分的窗口一律不入选`() {
        val list = listOf(
            entry("高分点", 88, 1_000L),
            entry("低分点", 59, 1_000L),
            entry("临界点", 60, 1_000L),
        )
        val shown = CrossTop10Filter.filter(list, range = null)
        assertEquals(listOf("高分点", "临界点"), shown.map { it.spotName })
    }

    @Test
    fun `时段为null等于全部不筛`() {
        val list = listOf(entry("A", 70, 0L), entry("B", 90, 100_000L))
        assertEquals(2, CrossTop10Filter.filter(list, range = null).size)
    }

    @Test
    fun `时段只保留相交窗口并保持原顺序`() {
        val list = listOf(
            entry("窗口1", 80, 1_000L, hours = 2),      // 1000–8200
            entry("窗口2", 90, 20_000L, hours = 2),     // 20000–27200
            entry("窗口3", 85, 50_000L, hours = 2),     // 50000–57200
        )
        // 只覆盖 15000–25000 → 与窗口2 相交
        val range = Top10TimeFilter.TimeRange(15_000L, 25_000L)
        assertEquals(listOf("窗口2"), CrossTop10Filter.filter(list, range).map { it.spotName })
    }

    @Test
    fun `交界点按半开区间处理`() {
        val list = listOf(entry("紧邻前", 80, 1_000L, hours = 1)) // 1000–4600
        // 区间正好从窗口结束时刻开始 → 不相交
        assertTrue(CrossTop10Filter.filter(list, Top10TimeFilter.TimeRange(4_600L, 9_000L)).isEmpty())
        // 区间与窗口尾部重叠 → 相交
        assertEquals(1, CrossTop10Filter.filter(list, Top10TimeFilter.TimeRange(4_000L, 9_000L)).size)
    }

    @Test
    fun `全部低于门槛时返回空列表而不是兜底数据`() {
        val list = listOf(entry("A", 40, 0L), entry("B", 55, 0L))
        assertTrue(CrossTop10Filter.filter(list, range = null).isEmpty())
    }
}
