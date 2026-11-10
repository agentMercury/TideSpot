package com.tidespot.feature.favorites

import com.tidespot.core.data.repo.TopWindowsResult
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.feature.favorites.favorites.TopWindowsLruCache
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * OPT-V11-08（P1）跨点 Top10 LRU 缓存锁：
 * - 二次进入 0 次加载（GWT：第二次 0 次 find/读库）；
 * - 容量淘汰（LRU）；
 * - 单点失效 / 算法版本失效 / 清空。
 */
class TopWindowsLruCacheTest {

    private fun spot(id: Long) = FavoriteSpot(id = id, name = "点$id", lat = 30.0, lon = 120.0)

    private fun result(validHours: Int) = TopWindowsResult(
        windows = emptyList(),
        fallbackMessage = null,
        fallbackBestEpochSec = null,
        validHoursPresent = validHours,
    )

    @Test
    fun secondLoad_hitsCache_zeroLoaderCalls(): Unit = runBlocking {
        val cache = TopWindowsLruCache()
        var calls = 0
        val loader: suspend (FavoriteSpot) -> TopWindowsResult? = { calls++; result(240) }
        cache.getOrLoad(spot(1), algoVersion = 1, horizonDays = 7, loader = loader)
        val second = cache.getOrLoad(spot(1), algoVersion = 1, horizonDays = 7, loader = loader)
        assertEquals(1, calls, "第二次进入必须命中缓存（0 次加载）")
        assertEquals(240, second?.validHoursPresent)
    }

    @Test
    fun capacity_evictsLeastRecentlyUsed(): Unit = runBlocking {
        val cache = TopWindowsLruCache(capacity = 2)
        val loader: suspend (FavoriteSpot) -> TopWindowsResult? = { result(1) }
        cache.getOrLoad(spot(1), 1, 7, loader)
        cache.getOrLoad(spot(2), 1, 7, loader)
        // 访问 1 → 2 变为最久未用
        cache.getOrLoad(spot(1), 1, 7, loader)
        cache.getOrLoad(spot(3), 1, 7, loader)
        assertEquals(2, cache.size)
        assertNotNull(cache.get(TopWindowsLruCache.Key(1, 1, 7)))
        assertNull(cache.get(TopWindowsLruCache.Key(2, 1, 7)), "最久未用的点应被淘汰")
    }

    @Test
    fun invalidateSpot_removesOnlyThatSpot(): Unit = runBlocking {
        val cache = TopWindowsLruCache()
        val loader: suspend (FavoriteSpot) -> TopWindowsResult? = { result(1) }
        cache.getOrLoad(spot(1), 1, 7, loader)
        cache.getOrLoad(spot(2), 1, 7, loader)
        cache.invalidateSpot(1)
        assertNull(cache.get(TopWindowsLruCache.Key(1, 1, 7)))
        assertNotNull(cache.get(TopWindowsLruCache.Key(2, 1, 7)))
    }

    @Test
    fun algoVersionChange_missesCache(): Unit = runBlocking {
        val cache = TopWindowsLruCache()
        var calls = 0
        val loader: suspend (FavoriteSpot) -> TopWindowsResult? = { calls++; result(240) }
        cache.getOrLoad(spot(1), algoVersion = 1, horizonDays = 7, loader)
        cache.getOrLoad(spot(1), algoVersion = 2, horizonDays = 7, loader)
        assertEquals(2, calls, "算法版本变化必须重算")
    }

    @Test
    fun loaderFailure_isNotCached(): Unit = runBlocking {
        val cache = TopWindowsLruCache()
        var calls = 0
        val failing: suspend (FavoriteSpot) -> TopWindowsResult? = { calls++; null }
        assertNull(cache.getOrLoad(spot(1), 1, 7, failing))
        assertNull(cache.getOrLoad(spot(1), 1, 7, failing))
        assertEquals(2, calls)
        assertEquals(0, cache.size)
    }

    @Test
    fun clear_dropsAll(): Unit = runBlocking {
        val cache = TopWindowsLruCache()
        cache.getOrLoad(spot(1), 1, 7) { result(1) }
        cache.clear()
        assertEquals(0, cache.size)
    }
}