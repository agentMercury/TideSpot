package com.tidespot.feature.favorites

import com.tidespot.core.data.DataError
import com.tidespot.core.data.repo.DataSource
import com.tidespot.core.data.repo.SpotDetailResult
import com.tidespot.feature.favorites.favorites.FavoritesRefreshLogic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OPT-V12-02（CHG-57）锁测试：刷新结果分类与汇总（GWT「已刷新 X / 已是最新 Y / 失败 Z」）。
 */
class FavoritesRefreshLogicTest {

    @Test
    fun `全源命中缓存归类为已是最新（TTL 内 0 请求）`() {
        val outcome = FavoritesRefreshLogic.classify(
            result(servedFromCache = setOf(DataSource.WEATHER, DataSource.MARINE)),
        )
        assertEquals(FavoritesRefreshLogic.Outcome.UP_TO_DATE, outcome)
    }

    @Test
    fun `至少一个源真实拉取归类为已刷新`() {
        val outcome = FavoritesRefreshLogic.classify(
            result(servedFromCache = setOf(DataSource.WEATHER)),
        )
        assertEquals(FavoritesRefreshLogic.Outcome.REFRESHED, outcome)
    }

    @Test
    fun `存在分维错误归类为失败（保留旧数据与陈旧标记）`() {
        val outcome = FavoritesRefreshLogic.classify(
            result(
                servedFromCache = setOf(DataSource.WEATHER, DataSource.MARINE),
                errors = mapOf(DataSource.MARINE to DataError.ServerError(500, "boom")),
            ),
        )
        assertEquals(FavoritesRefreshLogic.Outcome.FAILED, outcome)
    }

    @Test
    fun `汇总三计数与总数一致`() {
        val summary = FavoritesRefreshLogic.summarize(
            listOf(
                FavoritesRefreshLogic.Outcome.REFRESHED,
                FavoritesRefreshLogic.Outcome.REFRESHED,
                FavoritesRefreshLogic.Outcome.UP_TO_DATE,
                FavoritesRefreshLogic.Outcome.FAILED,
            ),
        )
        assertEquals(2, summary.refreshed)
        assertEquals(1, summary.upToDate)
        assertEquals(1, summary.failed)
        assertEquals(4, summary.total)
    }

    @Test
    fun `刷新目标按 spotId 去重（同一 spot 只刷一次）`() {
        assertEquals(listOf(3L, 1L, 2L), FavoritesRefreshLogic.targets(listOf(3, 1, 1, 2, 3)))
    }

    @Test
    fun `空结果汇总为全零`() {
        val summary = FavoritesRefreshLogic.summarize(emptyList())
        assertEquals(0, summary.total)
        assertTrue(summary.total == 0)
    }

    private fun result(
        servedFromCache: Set<DataSource>,
        errors: Map<DataSource, DataError> = emptyMap(),
    ): SpotDetailResult = SpotDetailResult(
        spotId = 1,
        slices = emptyList(),
        weather = emptyList(),
        marine = emptyList(),
        daily = null,
        weatherDegraded = false,
        marineDegraded = false,
        marineAllNullInland = false,
        errors = errors,
        servedFromCache = servedFromCache,
        fetchedAtMillis = emptyMap(),
    )
}