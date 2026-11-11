package com.tidespot.feature.favorites.favorites

import com.tidespot.core.data.repo.DataSource
import com.tidespot.core.data.repo.SpotDetailResult

/**
 * OPT-V12-02（CHG-57）：收藏列表刷新的**纯逻辑**（组装层盲区教训：分类/汇总先抽函数 + JVM 锁测试）。
 *
 * 口径（`OPT_REQUIREMENTS_V11.md` §7 OPT-V12-02）：
 * - 刷新走 `refreshSpotDetail(spot)`（**非 force**）——TTL 命中即 0 请求，超 TTL 才按令牌桶排队；
 * - 失败项**保留旧数据**并保留「陈旧」标记（本层只分类，不改标记语义）；
 * - 汇总「已刷新 X · 已是最新 Y · 失败 Z」由 UI 按资源格式化（纯层不携带文案）。
 */
object FavoritesRefreshLogic {

    /** 单点刷新结果分类。 */
    enum class Outcome { REFRESHED, UP_TO_DATE, FAILED }

    /** 刷新汇总（GWT：已刷新/已是最新/失败三计数）。 */
    data class Summary(val refreshed: Int, val upToDate: Int, val failed: Int) {
        val total: Int get() = refreshed + upToDate + failed
    }

    /**
     * 分类：
     * 1. `errors` 非空 → [Outcome.FAILED]（请求失败/降级，保留旧数据）；
     * 2. 全源命中缓存（TTL 内 → 0 请求）→ [Outcome.UP_TO_DATE]；
     * 3. 其余（至少一个源发生了真实拉取）→ [Outcome.REFRESHED]。
     */
    fun classify(result: SpotDetailResult): Outcome = when {
        result.errors.isNotEmpty() -> Outcome.FAILED
        result.servedFromCache.containsAll(ALL_SOURCES) -> Outcome.UP_TO_DATE
        else -> Outcome.REFRESHED
    }

    /** 逐点结果 → 汇总计数。 */
    fun summarize(outcomes: List<Outcome>): Summary = Summary(
        refreshed = outcomes.count { it == Outcome.REFRESHED },
        upToDate = outcomes.count { it == Outcome.UP_TO_DATE },
        failed = outcomes.count { it == Outcome.FAILED },
    )

    /** 需要纳入刷新的点：去重 id（同一 spot 的重复行只刷一次，禁叠加令牌消耗）。 */
    fun targets(spotIds: List<Long>): List<Long> = spotIds.distinct()

    private val ALL_SOURCES = setOf(DataSource.WEATHER, DataSource.MARINE)
}