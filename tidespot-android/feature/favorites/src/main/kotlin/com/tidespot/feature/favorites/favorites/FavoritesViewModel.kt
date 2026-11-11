package com.tidespot.feature.favorites.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tidespot.core.data.repo.DataSource
import com.tidespot.core.data.repo.TideSpotRepository
import com.tidespot.core.data.repo.TopWindowsResult
import com.tidespot.core.domain.FishScoreCalculator
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.window.BestWindowFinder
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.strings.ZhStrings
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 收藏列表 ViewModel（SRS F-2.1~F-2.6）：构造器注入 [TideSpotRepository]（与 :feature:detail
 * 的 ViewModel 同风格；:app 接线在 M6）。
 *
 * 数据面（F-2.4 离线可用）：favorites() + 每点 topWindows() 均为无网 DB 读侧（D03 读侧收口），
 * 编辑/删除/标记查看走 upsertFavorite/deleteFavorite/markViewed——全链路零网络请求。
 *
 * F-2.6 跨点 Top10：各点 topWindows → BestWindowFinder.rankAcrossSpots（rank 全局降序，FO-14）。
 *
 * 加载口径：并发不严格受限（列表 ≤100 条；网络并发 ≤4 由 :core:data 网络层内置，本层无网络）。
 */
class FavoritesViewModel(
    private val repository: TideSpotRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val strings: TideSpotStrings = ZhStrings,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state

    /** OPT-V11-08（P1）：跨点 Top10 内存 LRU（容量 = 收藏上限 100；**不落库**）。 */
    private val topCache = TopWindowsLruCache()

    /** 单点装载中间结果（OPT-V11-11 需要逐源拉取时刻）。 */
    private data class SpotLoad(
        val id: Long,
        val result: TopWindowsResult?,
        val fetchedAtMillis: Map<DataSource, Long>,
    )

    init {
        load()
    }

    /** 全量加载：favorites → 并发 per-spot topWindows（LRU 命中即跳过读库）→ 摘要/分数/Top10 一次性填充。 */
    fun load(showLoading: Boolean = true) {
        viewModelScope.launch(ioDispatcher) {
            if (showLoading) _state.update { it.copy(loading = true) }
            val spots = runCatching { repository.favorites() }.getOrDefault(emptyList())
            // 每点只读一次 topWindows（OPT-V11-08：LRU 命中即 0 次读库/0 次 find）；summary（F-2.5）与
            // 完整 windows（F-2.6 聚合输入）同源；OPT-V11-11：顺带取逐源拉取时刻（陈旧标记）
            val algoVersion = FishScoreCalculator.ALGO_VERSION
            val loaded: List<SpotLoad> = coroutineScope {
                spots.map { spot ->
                    async {
                        val result = runCatching {
                            topCache.getOrLoad(spot, algoVersion, 7) { repository.topWindows(it) }
                        }.getOrNull()
                        val fetched = runCatching { repository.lastFetchedMillis(spot.id) }
                            .getOrDefault(emptyMap())
                        SpotLoad(spot.id, result, fetched)
                    }
                }.awaitAll()
            }
            val perSpot: Map<Long, FavoritesSpotSummary> = loaded.associate { l ->
                l.id to (l.result?.let { r ->
                    FavoritesSpotSummary(
                        validHoursPresent = r.validHoursPresent,
                        window = r.windows.maxByOrNull { w -> w.rank },
                        fallbackMessage = r.fallbackMessage,
                        fallbackBestEpochSec = r.fallbackBestEpochSec,
                        fetchedAtMillis = l.fetchedAtMillis,
                    )
                } ?: FavoritesSpotSummary(fetchedAtMillis = l.fetchedAtMillis))
            }
            val windowsBySpotId: Map<Long, List<com.tidespot.core.domain.model.BestWindow>> =
                loaded.associate { it.id to (it.result?.windows ?: emptyList()) }
            // F-2.4 分数序依据：各点最高窗口 peakScore（无窗口/未加载的点不入 map → 排序时排最后）
            val scoreBySpotId = perSpot.mapNotNull { (id, s) -> s.window?.let { id to it.peakScore } }.toMap()
            // OPT-V11-14（P2/CHG-37）：「综合推荐」排序依据 = 各点最高窗口 rank（无窗口/未加载不入 map）
            val rankBySpotId = perSpot.mapNotNull { (id, s) -> s.window?.let { id to it.rank } }.toMap()
            // F-2.6 跨点 Top10：仅含「有窗口」的点（兜底/未加载无窗口可聚合）
            // OPT-M5-03：跨点 Top10 携带各点时区（rankAcrossSpots 全局 rank 降序序不变）
            val top10Inputs = spots.mapNotNull { spot ->
                windowsBySpotId[spot.id]?.takeIf { ws -> ws.isNotEmpty() }?.let { spot to it }
            }
            val top10 = FavoritesZones.attachZones(
                BestWindowFinder.rankAcrossSpots(top10Inputs.map { it.first.name to it.second }),
                top10Inputs,
            )
            _state.update {
                it.copy(
                    loading = false,
                    spots = spots,
                    summaries = perSpot,
                    scoreBySpotId = scoreBySpotId,
                    rankBySpotId = rankBySpotId,
                    top10 = top10,
                    // OPT-V11-06：列表摘要按「今天/明天/周X」渲染 → 每次加载刷新「现在」
                    nowEpochSec = System.currentTimeMillis() / 1000L,
                    // OPT-V11-12：重载后剔除已不存在点的选中项（防幽灵选中计数/误删）
                    selection = it.selection.filter { id -> spots.any { spot -> spot.id == id } }.toSet(),
                )
            }
        }
    }

    /**
     * F-2.4 排序切换（纯函数 [FavoritesSortLogic.sort]，仅改展示序不改数据）。
     * OPT-V11-08：规格口径——排序切换属于 Top10 缓存失效条件之一（本次实现为清空，重进列表重算）。
     */
    fun setSortOrder(order: SortOrder) {
        topCache.clear()
        _state.update { it.copy(sortOrder = order) }
    }

    /** OPT-V11-10（P1）：搜索/筛选条件变更（纯展示，不触网络/不触 DB）。 */
    fun setFilter(filter: FavoritesFilterLogic.Filter) {
        _state.update { it.copy(filter = filter) }
    }

    /** OPT-V11-08：单点缓存失效入口（该点数据刷新/修改后调用）。 */
    fun invalidateSpot(spotId: Long) {
        topCache.invalidateSpot(spotId)
    }

    /**
     * F-2.1 新建/编辑（patch 语义由 :core:data upsertFavorite 内置：编辑不冲派生列）。
     * [preset] 传中文字符串（淡水路亚/海水岸边/淡水台钓），与 resolvePreset 标签解析兼容；null = 不设置。
     */
    /**
     * F-2.1 新建/编辑（patch 语义由 :core:data upsertFavorite 内置：编辑不冲派生列）。
     * [preset] 传中文字符串（淡水路亚/海水岸边/淡水台钓），与 resolvePreset 标签解析兼容；null = 不设置。
     * OPT-M5-07（CHG-16⑰）：尾增可选 [lat]/[lon]（默认 null，既有调用兼容）；新建路径必须携带坐标——
     * null 或 (0.0, 0.0) 拒绝落库（防御；:app 准星主路径接线在批次 3）。编辑路径坐标不变。
     */
    fun saveFavorite(
        existing: FavoriteSpot?,
        name: String,
        memo: String?,
        preset: String?,
        tags: List<String>,
        lat: Double? = null,
        lon: Double? = null,
        /**
         * OPT-V11-16（P2/CHG-37）：分组名（空白已由调用方归一为 null = 未分组）。
         * 编辑路径由 UI 传入「对话框最终值」；新建路径 null = 未分组。
         */
        groupName: String? = null,
    ) {
        if (name.isBlank()) return
        if (existing == null && !FavoritesSaveRules.canCreate(lat, lon)) {
            _state.update { it.copy(lastSaveError = FavoritesSaveRules.rejectMessage(strings)) }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            val favorite = if (existing != null) {
                existing.copy(name = name.trim(), memo = memo?.trim()?.ifEmpty { null },
                    preset = preset, tags = tags, groupName = groupName)
            } else {
                FavoriteSpot(
                    id = 0,
                    name = name.trim(),
                    lat = lat ?: return@launch, // canCreate 已保证非 null（OPT-M5-07 防御）
                    lon = lon ?: return@launch,
                    memo = memo?.trim()?.ifEmpty { null },
                    preset = preset,
                    tags = tags,
                    groupName = groupName,
                )
            }
            runCatching { repository.upsertFavorite(favorite) }
            // OPT-V11-08：编辑已存在点 → 单点缓存失效（新建点 id 尚无缓存项）
            if (existing != null) topCache.invalidateSpot(existing.id)
            _state.update { it.copy(lastSaveError = null) }
            load()
        }
    }

    /** F-2.4 删除（UI 侧二次确认对话框先行；本函数为确认后执行）。 */
    fun deleteFavorite(id: Long) {
        viewModelScope.launch(ioDispatcher) {
            runCatching { repository.deleteFavorite(id) }
            // OPT-V11-08：删除 → 单点缓存失效（防已删点残留窗口）
            topCache.invalidateSpot(id)
            load()
        }
    }

    /** 进入详情（M6 接线）前标记最近查看（F-2.4 最近查看排序的数据来源）。 */
    fun markViewed(spotId: Long) {
        viewModelScope.launch(ioDispatcher) {
            runCatching { repository.markViewed(spotId) }
        }
    }

    // ────────────── OPT-V11-12 / 15 / 16（P2/CHG-37）：多选、批量、信息卡、分组 ──────────────

    /**
     * 长按卡片进入多选（客户裁定 D16：长按 = 多选，不占信息卡片手势）。
     * 取消最后一个选中项即退出多选（[FavoritesUiState.isSelecting] = 选中集合非空）。
     */
    fun toggleSelection(id: Long) {
        _state.update {
            it.copy(selection = FavoritesBatchLogic.toggle(it.selection, id), batchMessage = null)
        }
    }

    /** 全选（域 = 当前可见列表，被筛掉的点不会被「看不见地被删」）。 */
    fun selectAllVisible(visible: List<FavoriteSpot>) {
        _state.update {
            it.copy(
                selection = FavoritesBatchLogic.selectAll(visible.map { spot -> spot.id }),
                batchMessage = null,
            )
        }
    }

    /** 反选（域 = 当前可见列表；域外既有选择保留）。 */
    fun invertSelection(visible: List<FavoriteSpot>) {
        _state.update {
            it.copy(
                selection = FavoritesBatchLogic.invertSelection(
                    it.selection,
                    visible.map { spot -> spot.id },
                ),
                batchMessage = null,
            )
        }
    }

    /** 退出多选。 */
    fun clearSelection() {
        _state.update { it.copy(selection = emptySet(), batchMessage = null) }
    }

    /**
     * OPT-V11-12：批量删除（UI 二次确认后调用；Repository 单事务 + 级联清理 6 张表）。
     * 完成后清空多选并整体重载（列表/摘要/缓存同步，验收 GWT 要求）。
     */
    fun deleteSelected(visible: List<FavoriteSpot>) {
        val ids = FavoritesBatchLogic.selectedIds(visible, _state.value.selection)
        if (ids.isEmpty()) return
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching { repository.deleteFavorites(ids) }
            // 删除后跨点缓存整体失效（已删点不得残留窗口）
            topCache.clear()
            _state.update {
                it.copy(
                    selection = emptySet(),
                    batchMessage = result.exceptionOrNull()?.let { strings.errBatchDelete },
                )
            }
            load()
        }
    }

    /**
     * OPT-V11-12：批量改标签（追加 / 替换二选一，D3）。
     * 合并语义由 [FavoritesBatchLogic.mergeTags] 决定；数据层只落最终标签（D07 patch 语义不破坏）。
     */
    fun saveSelectedTags(raw: String, replace: Boolean, visible: List<FavoriteSpot>) {
        val selected = FavoritesBatchLogic.selectedIds(visible, _state.value.selection).toSet()
        if (selected.isEmpty()) return
        val input = FavoritesBatchLogic.parseTags(raw)
        val updates = visible
            .filter { selected.contains(it.id) }
            .associate { spot ->
                spot.id to FavoritesBatchLogic.mergeTags(spot.tags, input, replace)
            }
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching { repository.updateFavoritesTags(updates) }
            _state.update {
                it.copy(
                    selection = emptySet(),
                    batchMessage = result.exceptionOrNull()?.let { strings.errBatchTag },
                )
            }
            load()
        }
    }

    /**
     * OPT-V11-16：批量改分组（空白 → 移回「未分组」）。
     */
    fun saveSelectedGroup(raw: String?, visible: List<FavoriteSpot>) {
        val selected = FavoritesBatchLogic.selectedIds(visible, _state.value.selection)
        if (selected.isEmpty()) return
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching { repository.updateFavoritesGroup(selected, raw) }
            _state.update {
                it.copy(
                    selection = emptySet(),
                    batchMessage = result.exceptionOrNull()?.let { strings.errBatchGroup },
                )
            }
            load()
        }
    }

    /** OPT-V11-15：展开/收起信息卡片（同一时刻最多一个）。 */
    fun toggleInfo(spotId: Long) {
        _state.update { it.copy(infoSpotId = if (it.infoSpotId == spotId) null else spotId) }
    }

    /** OPT-V11-16：分组折叠切换（仅展示态，不触 DB/网络）。 */
    fun toggleGroup(groupName: String) {
        _state.update {
            it.copy(collapsedGroups = FavoritesGroupLogic.toggleCollapsed(groupName, it.collapsedGroups))
        }
    }

    /** 清空批量操作提示。 */
    fun clearBatchMessage() {
        _state.update { it.copy(batchMessage = null) }
    }
}

