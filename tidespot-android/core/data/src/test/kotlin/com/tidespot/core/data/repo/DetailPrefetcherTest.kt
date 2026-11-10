package com.tidespot.core.data.repo

import com.tidespot.core.data.remote.TokenBucket
import com.tidespot.core.domain.model.FavoriteSpot
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OPT-V11-05（P1）真预取锁：
 * - 只取 lastViewedAt 前 5 条；
 * - 「距上次拉取 > TTL/2」才刷新（天气 1.5h / 海洋 3h）；
 * - 令牌桶耗尽的剩余项放弃（不排队）；
 * - 离线 → 零请求；失败静默不抛出。
 */
class DetailPrefetcherTest {

    private val now = 1_700_000_000_000L
    private fun hoursAgo(h: Double): Long = now - (h * 3_600_000).toLong()

    private fun spot(id: Long, lastViewed: Long) = FavoriteSpot(
        id = id, name = "点$id", lat = 30.0, lon = 120.0, lastViewedAtEpochSec = lastViewed,
    )

    private fun freshFetched(): Map<DataSource, Long> =
        mapOf(DataSource.WEATHER to hoursAgo(0.1), DataSource.MARINE to hoursAgo(0.1))

    @Test
    fun onlyTopFiveByLastViewed_areConsidered(): Unit = runBlocking {
        val spots = (1L..7L).map { spot(it, lastViewed = it * 10) }
        val prefetcher = DetailPrefetcher(
            lastFetched = { freshFetched() },
            refresh = { },
            isOnline = { true },
            bucket = TokenBucket(capacity = 6, periodMillis = 60_000, clock = { now }),
        )
        // 全部新鲜 → 候选为空
        assertEquals(emptyList(), prefetcher.candidates(spots, now))

        val needs = (1L..7L).map { spot(it, lastViewed = it * 10) }
        val stalePrefetcher = DetailPrefetcher(
            lastFetched = { mapOf(DataSource.WEATHER to hoursAgo(2.0)) }, // 超 TTL/2（1.5h）
            refresh = { },
            isOnline = { true },
            bucket = TokenBucket(capacity = 6, periodMillis = 60_000, clock = { now }),
        )
        val cand = stalePrefetcher.candidates(needs, now)
        assertEquals(5, cand.size)
        // lastViewedAt 降序 → id 7,6,5,4,3
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), cand.map { it.id })
    }

    @Test
    fun needsPrefetch_usesHalfTtl_perSource(): Unit = runBlocking {
        val prefetcher = DetailPrefetcher(
            lastFetched = { emptyMap() }, refresh = { }, isOnline = { true },
            bucket = TokenBucket(clock = { now }),
        )
        // 无记录 → 需要
        assertTrue(prefetcher.needsPrefetch(emptyMap(), now))
        // 天气 2h（> 1.5h）→ 需要
        assertTrue(prefetcher.needsPrefetch(mapOf(DataSource.WEATHER to hoursAgo(2.0)), now))
        // 天气 1h + 海洋 2h（< 3h）→ 不需要
        assertTrue(
            !prefetcher.needsPrefetch(
                mapOf(DataSource.WEATHER to hoursAgo(1.0), DataSource.MARINE to hoursAgo(2.0)),
                now,
            ),
        )
    }

    @Test
    fun tokenExhaustion_skipsRemaining_withoutQueueing(): Unit = runBlocking {
        val spots = (1L..6L).map { spot(it, lastViewed = it * 10) }
        var refreshed = 0
        val prefetcher = DetailPrefetcher(
            lastFetched = { mapOf(DataSource.WEATHER to hoursAgo(2.0)) },
            refresh = { refreshed++ },
            isOnline = { true },
            // 容量 2 → 仅前 2 条取到令牌，其余放弃
            bucket = TokenBucket(capacity = 2, periodMillis = 60_000, clock = { now }),
        )
        val outcome = prefetcher.prefetch(spots, now)
        assertEquals(2, outcome.attemptedSpotIds.size)
        // 候选上限 5 条，令牌 2 个 → 放弃 3 条
        assertEquals(3, outcome.skippedByToken)
        assertEquals(2, refreshed)
    }

    @Test
    fun offline_issuesZeroRequests(): Unit = runBlocking {
        var refreshed = 0
        val prefetcher = DetailPrefetcher(
            lastFetched = { emptyMap() },
            refresh = { refreshed++ },
            isOnline = { false },
            bucket = TokenBucket(clock = { now }),
        )
        val outcome = prefetcher.prefetch((1L..3L).map { spot(it, 0) }, now)
        assertTrue(outcome.offline)
        assertEquals(0, refreshed)
    }

    @Test
    fun refreshFailure_isSilent(): Unit = runBlocking {
        val prefetcher = DetailPrefetcher(
            lastFetched = { emptyMap() },
            refresh = { throw IllegalStateException("network down") },
            isOnline = { true },
            bucket = TokenBucket(clock = { now }),
        )
        val outcome = prefetcher.prefetch(listOf(spot(1, 1), spot(2, 2)), now)
        assertEquals(2, outcome.attemptedSpotIds.size)
    }
}