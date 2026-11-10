package com.tidespot.feature.favorites.favorites

import com.tidespot.core.domain.model.FavoriteSpot

/**
 * OPT-V11-12（P2/CHG-37）批量操作纯函数层（JVM 单测锁；UI 只做渲染与转发）。
 *
 * 交互口径（客户裁定 D16）：**长按卡片进入多选**；详情卡片改走卡片内「信息」图标（OPT-V11-15）。
 * [com.tidespot.feature.favorites.favorites.FavoritesUiState.isSelecting] 由选中集合非空判定——
 * 取消最后一个选中项即退出多选，无需额外模式标志（单一真源）。
 *
 * 数据面语义（D3 收窄）：批量操作仅 **删除** 与 **改标签/改分组**，**不做任何导出**。
 */
object FavoritesBatchLogic {

    /**
     * 全选/反选的单点切换：已选中 → 移除；未选中 → 加入（保持 Set 语义，无重复）。
     * 只接受当前可见列表中的 id（调用方保证）。
     */
    fun toggle(selection: Set<Long>, id: Long): Set<Long> =
        if (selection.contains(id)) selection - id else selection + id

    /** 全选（以当前可见/筛选结果为域，不含被筛掉的点——避免「看不见却被删」）。 */
    fun selectAll(ids: List<Long>): Set<Long> = ids.toSet()

    /** 反选：可见域内未选中的变为选中，已选中的取消；域外的既有选择保留。 */
    fun invertSelection(selection: Set<Long>, ids: List<Long>): Set<Long> {
        val visible = ids.toSet()
        val kept = selection.filterNot { visible.contains(it) }.toSet()
        val inverted = visible.filterNot { selection.contains(it) }.toSet()
        return kept + inverted
    }

    /** 可见域是否已全选（空域 → false，防「0 项全选」误显示）。 */
    fun isAllSelected(selection: Set<Long>, ids: List<Long>): Boolean =
        ids.isNotEmpty() && ids.all { selection.contains(it) }

    /**
     * 批量改标签的合并语义（D3：追加 / 替换二选一）：
     * - [replace] = true → 用 [input] 覆盖（去空、去重、保序；**输入为空 = 清空标签**，属显式选择）；
     * - [replace] = false → [existing] 在前 + [input] 去重追加（保序，existing 优先；空输入 = 不变）。
     */
    fun mergeTags(existing: List<String>, input: List<String>, replace: Boolean): List<String> {
        val normalizedInput = input.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (replace) return normalizedInput
        val normalizedExisting = existing.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return (normalizedExisting + normalizedInput).distinct()
    }

    /** 批量标签输入解析（与单点编辑同口径：`, ， 、 空格` 分隔 → trim → 去空 → 去重）。 */
    fun parseTags(raw: String): List<String> = FavoritesSortLogic.parseTags(raw)

    /** 批量删除二次确认文案（条数明示，验收 GWT 要求）。 */
    fun deleteConfirmMessage(count: Int): String =
        "确定删除选中的 $count 个收藏点吗？该操作不可撤销。"

    /** 批量删除的 id 列表（按可见序输出，保证删除集合与用户所见一致）。 */
    fun selectedIds(visible: List<FavoriteSpot>, selection: Set<Long>): List<Long> =
        visible.mapNotNull { spot -> spot.id.takeIf { selection.contains(it) } }

    /** 批量改分组的候选（既有分组名去重排序；空 → 空列表，非 null）。 */
    fun existingGroups(spots: List<FavoriteSpot>): List<String> =
        spots.mapNotNull { it.groupName?.trim()?.ifEmpty { null } }.distinct().sorted()
}