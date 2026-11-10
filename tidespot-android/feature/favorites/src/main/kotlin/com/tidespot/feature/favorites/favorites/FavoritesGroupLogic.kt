package com.tidespot.feature.favorites.favorites

import com.tidespot.core.domain.model.FavoriteSpot

/**
 * OPT-V11-16（P2/CHG-37）收藏分组纯函数层（JVM 单测锁）。
 *
 * 口径（OPT-V11-16 原文）：**默认组 = 「未分组」，不强制**；列表按分组折叠显示。
 * - `spot.groupName` 为 null/空白 → 归入「未分组」；
 * - 组间顺序：自定义组按名称升序，「未分组」恒排最后（默认组不抢注意力）；
 * - 组内顺序：沿用调用方传入的排序结果（排序与分组正交，先筛/排后分组）；
 * - 折叠仅影响渲染，不影响批量选择与数据。
 */
object FavoritesGroupLogic {

    /** 默认组名（未设置 `groupName` 的点归入此组；禁空串表达）。 */
    const val UNGROUPED = "未分组"

    /** 一个渲染分组。 */
    data class Group(
        val name: String,
        val spots: List<FavoriteSpot>,
    ) {
        /** true = 默认组（「未分组」）；UI 用其决定是否显示「改名/移组」提示。 */
        val isDefault: Boolean get() = name == UNGROUPED
    }

    /** 单点的归属组名（null/空白 → 默认组）。 */
    fun groupNameOf(spot: FavoriteSpot): String =
        spot.groupName?.trim()?.ifEmpty { null } ?: UNGROUPED

    /**
     * 分组：给定已排序/已筛选的点列表，输出组列表（**组内保持输入序**）。
     * 无任何点的组不产出（不渲染空组）。
     */
    fun groups(sortedSpots: List<FavoriteSpot>): List<Group> {
        if (sortedSpots.isEmpty()) return emptyList()
        val buckets = sortedSpots.groupBy { groupNameOf(it) }
        val custom = buckets.keys.filter { it != UNGROUPED }.sorted()
        val ordered = custom + (if (buckets.containsKey(UNGROUPED)) listOf(UNGROUPED) else emptyList())
        return ordered.map { name -> Group(name, buckets.getValue(name)) }
    }

    /** 折叠判定（默认全部展开；`collapsed` 携带已折叠组名）。 */
    fun isCollapsed(groupName: String, collapsed: Set<String>): Boolean =
        collapsed.contains(groupName)

    /** 折叠切换（纯函数；供 ViewModel 更新状态）。 */
    fun toggleCollapsed(groupName: String, collapsed: Set<String>): Set<String> =
        if (collapsed.contains(groupName)) collapsed - groupName else collapsed + groupName

    /** 单点改分组的写入值归一：空白 → null（= 移回未分组）。 */
    fun normalizeGroupName(raw: String?): String? = raw?.trim()?.ifEmpty { null }
}