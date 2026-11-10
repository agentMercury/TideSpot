package com.tidespot.feature.favorites.favorites

import com.tidespot.core.data.repo.TopWindowsResult
import com.tidespot.core.domain.model.FavoriteSpot

/**
 * OPT-V11-08（P1）跨点 Top10 内存 LRU 缓存（容量 = 收藏上限 100，**不落库**）。
 *
 * 失效口径：
 * - 该点数据刷新 → [invalidateSpot]（编辑/删除/预取后由调用方触发）；
 * - 排序切换 → [clear]（规格口径，调用方在 setSortOrder 时清）；
 * - 算法版本变化 → 键含 [Key.algoVersion]，旧键自然失效；
 * - 进程重启 → 内存实例随进程消失。
 *
 * LRU 通过 `LinkedHashMap(accessOrder = true)` 的 `removeEldestEntry` 实现；
 * 方法加锁（IO 并发装载安全）。禁止新增 Room 表（规格明确本轮不落库）。
 */
class TopWindowsLruCache(private val capacity: Int = 100) {

    data class Key(val spotId: Long, val algoVersion: Int, val horizonDays: Int)

    private val lock = Any()
    private val entries = object : LinkedHashMap<Key, TopWindowsResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, TopWindowsResult>): Boolean =
            size > capacity
    }

    val size: Int get() = synchronized(lock) { entries.size }

    fun get(key: Key): TopWindowsResult? = synchronized(lock) { entries[key] }

    fun put(key: Key, value: TopWindowsResult) = synchronized(lock) { entries[key] = value }

    /** 单点失效（该点刷新/删除后调用）。 */
    fun invalidateSpot(spotId: Long) = synchronized(lock) {
        entries.keys.removeAll { it.spotId == spotId }
    }

    fun clear() = synchronized(lock) { entries.clear() }

    /** 命中直接返回；未命中调用 [loader] 并在成功时写入（失败不缓存，保持「未加载」语义）。 */
    suspend fun getOrLoad(
        spot: FavoriteSpot,
        algoVersion: Int,
        horizonDays: Int,
        loader: suspend (FavoriteSpot) -> TopWindowsResult?,
    ): TopWindowsResult? {
        val key = Key(spot.id, algoVersion, horizonDays)
        get(key)?.let { return it }
        val loaded = loader(spot) ?: return null
        put(key, loaded)
        return loaded
    }
}