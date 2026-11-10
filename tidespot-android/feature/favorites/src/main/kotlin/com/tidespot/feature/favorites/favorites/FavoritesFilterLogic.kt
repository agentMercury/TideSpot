package com.tidespot.feature.favorites.favorites

import com.tidespot.core.domain.model.FavoriteSpot

/**
 * OPT-V11-10（P1）收藏列表搜索与筛选纯函数层（先筛后排，与 [FavoritesSortLogic] 正交）。
 *
 * - [Filter.query]：名称或备注**包含**（忽略大小写、去首尾空白）即命中；
 * - [Filter.tag]：标签精确匹配（`tags.any { it == tag }`）；
 * - [Filter.preset]：场景预设精确匹配；
 * - 多个条件叠加 = 交集；全空 = 全量（零行为漂移）。
 */
object FavoritesFilterLogic {

    data class Filter(
        val query: String = "",
        val tag: String? = null,
        val preset: String? = null,
    ) {
        /** 是否有任一筛选条件生效（UI 空态与「清除」按钮判定）。 */
        val isActive: Boolean
            get() = query.isNotBlank() || tag != null || preset != null
    }

    fun matches(spot: FavoriteSpot, filter: Filter): Boolean {
        val q = filter.query.trim()
        if (q.isNotEmpty()) {
            val hitName = spot.name.contains(q, ignoreCase = true)
            val hitMemo = spot.memo?.contains(q, ignoreCase = true) == true
            if (!hitName && !hitMemo) return false
        }
        if (filter.tag != null && spot.tags.none { it == filter.tag }) return false
        if (filter.preset != null && spot.preset != filter.preset) return false
        return true
    }

    /** 过滤（保持输入序；排序由调用方在过滤结果上执行）。 */
    fun apply(spots: List<FavoriteSpot>, filter: Filter): List<FavoriteSpot> =
        if (!filter.isActive) spots else spots.filter { matches(it, filter) }

    /** 可用标签（去重 + 字典序；空输入 → 空列表，非 null）。 */
    fun availableTags(spots: List<FavoriteSpot>): List<String> =
        spots.flatMap { it.tags }.filter { it.isNotBlank() }.distinct().sorted()

    /** 可用场景预设（去重 + 字典序；空输入 → 空列表）。 */
    fun availablePresets(spots: List<FavoriteSpot>): List<String> =
        spots.mapNotNull { it.preset?.takeIf { p -> p.isNotBlank() } }.distinct().sorted()
}