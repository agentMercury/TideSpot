package com.tidespot.core.data.repo

import com.tidespot.core.data.remote.TokenBucket
import com.tidespot.core.domain.model.FavoriteSpot
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * OPT-V11-05（P1）真预取：进入收藏列表时后台刷新「最近查看 5 条」。
 *
 * 口径（规格原文）：
 * - 目标 = `lastViewedAt` 降序前 [maxSpots] 条中「距上次成功拉取 > 该源 TTL/2」（无记录视为需要刷新）；
 * - **全局令牌桶 6 req/min**：`tryAcquire` 失败 → 放弃本轮剩余项（不排队、不阻塞 UI）；
 * - 并发 ≤ [concurrency]（默认 2）；
 * - 与手动刷新同一落库路径（[refresh] 注入 `repository.refreshSpotDetail`），失败静默（fetch_log 由数据层留痕）；
 * - 离线（[isOnline] = false）→ 零请求；
 * - **不新增 UI 状态**、不引 WorkManager。
 *
 * 可测性：仓储以函数注入（JVM 锁无需 Android/Room）。
 */
class DetailPrefetcher(
    private val lastFetched: suspend (Long) -> Map<DataSource, Long>,
    private val refresh: suspend (FavoriteSpot) -> Unit,
    private val isOnline: suspend () -> Boolean,
    private val bucket: TokenBucket = TokenBucket(capacity = 6, periodMillis = 60_000L),
    private val maxSpots: Int = 5,
    private val concurrency: Int = 2,
) {

    /** 本轮结果（仅诊断用；UI 不消费）。 */
    data class Outcome(
        val attemptedSpotIds: List<Long> = emptyList(),
        val skippedByToken: Int = 0,
        val offline: Boolean = false,
    )

    /** 需要预刷新判定：任一无记录的源，或任一源距上次成功拉取 > TTL/2。 */
    fun needsPrefetch(fetchedAtMillis: Map<DataSource, Long>, nowMillis: Long): Boolean =
        DataSource.entries.any { src ->
            val last = fetchedAtMillis[src] ?: 0L
            last <= 0L || (nowMillis - last) > src.ttlMillis / 2
        }

    /**
     * 本轮候选：按 lastViewedAt 降序取前 [maxSpots]，逐点判定 TTL/2（保持顺序、同值按 id 降序稳定）。
     */
    suspend fun candidates(spots: List<FavoriteSpot>, nowMillis: Long): List<FavoriteSpot> =
        spots.sortedWith(compareByDescending<FavoriteSpot> { it.lastViewedAtEpochSec }.thenByDescending { it.id })
            .take(maxSpots)
            .filter { needsPrefetch(lastFetched(it.id), nowMillis) }

    suspend fun prefetch(spots: List<FavoriteSpot>, nowMillis: Long = System.currentTimeMillis()): Outcome {
        if (!isOnline()) return Outcome(offline = true)
        val todo = candidates(spots, nowMillis)
        if (todo.isEmpty()) return Outcome()
        // 令牌桶先按序取：取不到即放弃剩余（不排队）
        val granted = ArrayList<FavoriteSpot>(todo.size)
        var skipped = 0
        for (spot in todo) {
            if (bucket.tryAcquire()) granted.add(spot) else { skipped = todo.size - granted.size; break }
        }
        if (granted.isEmpty()) return Outcome(skippedByToken = skipped)
        val gate = Semaphore(concurrency.coerceAtLeast(1))
        coroutineScope {
            granted.forEach { spot ->
                launch {
                    gate.withPermit {
                        // 失败静默（fetch_log 由 :core:data 数据层留痕）
                        runCatching { refresh(spot) }
                    }
                }
            }
        }
        return Outcome(attemptedSpotIds = granted.map { it.id }, skippedByToken = skipped)
    }

    companion object {
        /** 生产组装：与手动刷新同一入口 [TideSpotRepository.refreshSpotDetail]（TTL/15min gate 内置）。 */
        fun from(
            repository: TideSpotRepository,
            isOnline: suspend () -> Boolean,
        ): DetailPrefetcher = DetailPrefetcher(
            lastFetched = { repository.lastFetchedMillis(it) },
            refresh = { repository.refreshSpotDetail(it) },
            isOnline = isOnline,
        )
    }
}