package com.tidespot.feature.favorites.favorites

import com.tidespot.core.ui.zone.SpotZones
import java.time.ZoneId

import com.tidespot.core.domain.model.BestWindow
import com.tidespot.core.domain.model.FavoriteSpot

/**
 * 收藏列表排序方式（F-2.4 三选一 + OPT-V11-14 新增「综合推荐」）。
 *
 * - [NAME] 名称字典序；[LAST_VIEWED] 最近查看降序；[SCORE] 最高窗口 peakScore 降序；
 * - [RECOMMENDED]（P2）：最高窗口 `rank` 降序，同 rank 按 name（跨点窗口价值优先）。
 */
enum class SortOrder { NAME, LAST_VIEWED, SCORE, RECOMMENDED }

/**
 * 每个收藏点的最佳窗口摘要数据（F-2.5；由 [com.tidespot.core.data.repo.TideSpotRepository.topWindows]
 * 无网读取派生，UI 侧文案经 [FavoritesSummaryFormatter] 格式化）。
 */
data class FavoritesSpotSummary(
    /** 库内有效小时行数；=0 表示未算过/无库内行 → 「未加载」。 */
    val validHoursPresent: Int = 0,
    /** 最高窗口（windows 按 rank 降序首项）；无窗口（兜底/禁用）→ null。 */
    val window: BestWindow? = null,
    /** 兜底文案透传（null = 有窗口）。 */
    val fallbackMessage: String? = null,
    /** 兜底最高分时刻（epoch 秒；UI 渲染「（时刻…）」）。 */
    val fallbackBestEpochSec: Long? = null,
    /** OPT-V11-11（P1）：逐源最近成功拉取时刻（epoch 毫秒；0 = 无记录）——列表级陈旧标记输入。 */
    val fetchedAtMillis: Map<com.tidespot.core.data.repo.DataSource, Long> = emptyMap(),
)

/** 收藏列表 UI 状态（ViewModel 单一 StateFlow；派生只在数据到达时算一次，N-2 同源约束）。 */
data class FavoritesUiState(
    val loading: Boolean = true,
    val spots: List<FavoriteSpot> = emptyList(),
    /** spotId → 摘要（F-2.5）。 */
    val summaries: Map<Long, FavoritesSpotSummary> = emptyMap(),
    /** spotId → 最高窗口 peakScore（F-2.4 分数序排序依据；未加载/无窗口的点不在此 map）。 */
    val scoreBySpotId: Map<Long, Int> = emptyMap(),
    /** OPT-V11-14（P2/CHG-37）：spotId → 最高窗口 rank（「综合推荐」排序依据；缺 key = 未加载）。 */
    val rankBySpotId: Map<Long, Double> = emptyMap(),
    val sortOrder: SortOrder = SortOrder.NAME,
    /** OPT-M5-07：最近一次新建收藏被坐标防御拒绝时的提示（null=无）。 */
    val lastSaveError: String? = null,
    /** F-2.6 跨收藏点 Top10（rankAcrossSpots 全局 rank 降序；OPT-M5-03 携带各点时区）。 */
    val top10: List<CrossSpotTop10Entry> = emptyList(),
    /** OPT-V11-06：加载时刻（相对时间摘要的「现在」，epoch 秒；0 = 未加载 → 回落绝对格式）。 */
    val nowEpochSec: Long = 0L,
    /** OPT-V11-10（P1）：搜索/筛选条件（与排序正交：先筛后排）。 */
    val filter: FavoritesFilterLogic.Filter = FavoritesFilterLogic.Filter(),
    /**
     * OPT-V11-12（P2/CHG-37）：多选模式选中的 spotId 集合（空集 = 未进入多选）。
     * 进入多选由长按触发；全选/反选/批量删除/批量编辑都以此为唯一真源。
     */
    val selection: Set<Long> = emptySet(),
    /** OPT-V11-16（P2/CHG-37）：已折叠的分组名（默认全部展开；「未分组」同样可折叠）。 */
    val collapsedGroups: Set<String> = emptySet(),
    /** OPT-V11-15（P2/CHG-37）：展开信息卡片的 spotId（null = 无；同一时刻最多一个）。 */
    val infoSpotId: Long? = null,
    /** OPT-V11-12：最近一次批量操作失败/拒绝提示（null = 无）。 */
    val batchMessage: String? = null,
)

/** OPT-V11-12：多选模式判定（UI 顶部操作栏是否渲染）。 */
val FavoritesUiState.isSelecting: Boolean
    get() = selection.isNotEmpty()

/**
 * F-2.6 跨点 Top10 渲染条目（OPT-M5-03）：携带归属点时区（SpotZones.zoneForSpot）。
 * [spot] = null 仅在输入序不一致的防御分支出现（落系统默认口径并在 UI 注明）。
 */
data class CrossSpotTop10Entry(
    val spotName: String,
    val spot: FavoriteSpot?,
    val window: BestWindow,
) {
    /** 渲染时区：spot.tzOffsetSec → ZoneOffset；null/未回写 → 系统默认（口径由 usesDeviceZone 注明）。 */
    val zone: ZoneId
        get() = spot?.let { SpotZones.zoneForSpot(it) } ?: ZoneId.systemDefault()

    /** true = 时区未回写，按本机时区渲染（UI 须注明口径）。 */
    val usesDeviceZone: Boolean
        get() = spot == null || spot.tzOffsetSec == null
}


