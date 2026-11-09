package com.tidespot.feature.favorites.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tidespot.core.data.repo.TideSpotRepository
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.window.BestWindowFinder
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
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state

    init {
        load()
    }

    /** 全量加载：favorites → 并发 per-spot topWindows（缓存命中）→ 摘要/分数/Top10 一次性填充。 */
    fun load() {
        viewModelScope.launch(ioDispatcher) {
            _state.update { it.copy(loading = true) }
            val spots = runCatching { repository.favorites() }.getOrDefault(emptyList())
            // 每点只读一次 topWindows（无网 DB 读）；summary（F-2.5）与完整 windows（F-2.6 聚合输入）同源
            val loaded: List<Triple<Long, FavoritesSpotSummary, List<com.tidespot.core.domain.model.BestWindow>>> = coroutineScope {
                spots.map { spot ->
                    async {
                        val result = runCatching { repository.topWindows(spot) }.getOrNull()
                        val summary = result?.let {
                            FavoritesSpotSummary(
                                validHoursPresent = it.validHoursPresent,
                                window = it.windows.maxByOrNull { w -> w.rank },
                                fallbackMessage = it.fallbackMessage,
                                fallbackBestEpochSec = it.fallbackBestEpochSec,
                            )
                        } ?: FavoritesSpotSummary()
                        Triple(spot.id, summary, result?.windows ?: emptyList())
                    }
                }.awaitAll()
            }
            val perSpot: Map<Long, FavoritesSpotSummary> = loaded.associate { it.first to it.second }
            val windowsBySpotId: Map<Long, List<com.tidespot.core.domain.model.BestWindow>> =
                loaded.associate { it.first to it.third }
            // F-2.4 分数序依据：各点最高窗口 peakScore（无窗口/未加载的点不入 map → 排序时排最后）
            val scoreBySpotId = perSpot.mapNotNull { (id, s) -> s.window?.let { id to it.peakScore } }.toMap()
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
                    top10 = top10,
                )
            }
        }
    }

    /** F-2.4 排序切换（纯函数 [FavoritesSortLogic.sort]，仅改展示序不改数据）。 */
    fun setSortOrder(order: SortOrder) {
        _state.update { it.copy(sortOrder = order) }
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
    ) {
        if (name.isBlank()) return
        if (existing == null && !FavoritesSaveRules.canCreate(lat, lon)) {
            _state.update { it.copy(lastSaveError = FavoritesSaveRules.REJECT_MESSAGE) }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            val favorite = if (existing != null) {
                existing.copy(name = name.trim(), memo = memo?.trim()?.ifEmpty { null },
                    preset = preset, tags = tags)
            } else {
                FavoriteSpot(
                    id = 0,
                    name = name.trim(),
                    lat = lat ?: return@launch, // canCreate 已保证非 null（OPT-M5-07 防御）
                    lon = lon ?: return@launch,
                    memo = memo?.trim()?.ifEmpty { null },
                    preset = preset,
                    tags = tags,
                )
            }
            runCatching { repository.upsertFavorite(favorite) }
            _state.update { it.copy(lastSaveError = null) }
            load()
        }
    }

    /** F-2.4 删除（UI 侧二次确认对话框先行；本函数为确认后执行）。 */
    fun deleteFavorite(id: Long) {
        viewModelScope.launch(ioDispatcher) {
            runCatching { repository.deleteFavorite(id) }
            load()
        }
    }

    /** 进入详情（M6 接线）前标记最近查看（F-2.4 最近查看排序的数据来源）。 */
    fun markViewed(spotId: Long) {
        viewModelScope.launch(ioDispatcher) {
            runCatching { repository.markViewed(spotId) }
        }
    }
}

