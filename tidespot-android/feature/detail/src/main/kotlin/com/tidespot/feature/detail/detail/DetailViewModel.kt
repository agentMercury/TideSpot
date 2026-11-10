package com.tidespot.feature.detail.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tidespot.core.data.repo.HourlyScoreStore
import com.tidespot.core.data.repo.TideSpotRepository
import com.tidespot.core.data.repo.WaterQualityObservation
import com.tidespot.core.data.repo.WaterQualityObservationKind
import com.tidespot.core.domain.algorithm.ScenePreset
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.tide.TideCalibrator
import com.tidespot.core.domain.window.BestWindowFinder
import com.tidespot.core.ui.zone.SpotZones
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 详情页 ViewModel（F-2.3 / F-3.x 数据流）：
 * `TideSpotRepository.cachedDetail(spot)` 缓存先行渲染 → `refreshSpotDetail(spot)`
 * （15 min 节流与 TTL 内 0 请求已由 :core:data 内置，本层不再自造节流）。
 *
 * 派生计算编排进 [DetailStateHolder]（纯函数可测）；本类只做协程调度。
 *
 * 集成验收上报项（不改 :core:data/:core:domain 的层内兜底）：
 * - [zone]（D14）：默认按 spot.tzOffsetSec → ZoneOffset（SpotZones 单源）；null = 未回写落设备默认，
 *   :app 批次 3 可显式注入 IANA ZoneId 覆盖。禁固定偏移（跨零点「今天」窗口按选点当地日判定）。
 * - [preset]：M6 设置项接入；当前按 spot.preset 解析，缺省淡水·路亚。
 */
class DetailViewModel(
    private val repository: TideSpotRepository,
    private val spot: FavoriteSpot,
    zone: ZoneId = SpotZones.zoneForSpot(spot), // D14：spot.tzOffsetSec 单源；null=设备默认
    daysRange: Int = 7,
    preset: ScenePreset? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val holder = DetailStateHolder(
        zone = zone,
        daysRange = daysRange,
        lonDeg = spot.lon,
        // CHG-13/D03：与 :core:data 落库共用唯一解析（id→中文标签→水体兜底），禁 UI 侧另行解析
        preset = preset ?: HourlyScoreStore.resolvePreset(spot),
    )

    val state: kotlinx.coroutines.flow.StateFlow<DetailUiState> = holder.state
    val zoneId: ZoneId = zone
    val spotRef: FavoriteSpot = spot

    init {
        refresh(initial = true)
    }

    /** 缓存先行（不 force，15 min 节流内置）——首次进入调用。 */
    private fun refresh(initial: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            if (initial) {
                holder.setLoading(true)
                runCatching { repository.cachedDetail(spot) }.onSuccess { holder.onResult(it) }
            }
            holder.setRefreshing(true)
            runCatching { repository.refreshSpotDetail(spot) }
                .onSuccess { holder.onResult(it) }
                .onFailure { holder.setRefreshing(false) }
            holder.setLoading(false)
            loadWaterQualityObservations()
        }
    }

    /** F-3.6 重试（用户显式触发，绕过 15 min gate 由 :core:data force 语义处理）。 */
    fun retry() {
        viewModelScope.launch(ioDispatcher) {
            holder.setRefreshing(true)
            runCatching { repository.refreshSpotDetail(spot, force = true) }
                .onSuccess { holder.onResult(it) }
                .onFailure { holder.setRefreshing(false) }
        }
    }

    // ────────────────── OPT-V11-02 水质实测录入（CHG-27） ──────────────────

    /** 已录入的水质实测（读库；Sheet 展示原值与录入时间）。 */
    suspend fun waterQualityObservations(): List<WaterQualityObservation> =
        repository.waterQualityObservations(spot.id)

    /**
     * 保存水质实测（离线可录入；逐条落库 `manual_observation(kind/value)`），
     * 随后重算水温融合与徽标（±6h → `Tw=0.6·实测+0.4·模型`，confidence=MEASURED）。
     */
    suspend fun saveWaterQualitySelections(
        entries: List<Pair<WaterQualityObservationKind, Double>>,
        observedAtEpochSec: Long,
    ) {
        entries.forEach { (kind, value) ->
            repository.addWaterQualityObservation(spot.id, kind, value, observedAtEpochSec)
        }
        loadWaterQualityObservations()
    }

    /** 读取水温实测 → holder 融合重算 quality（其他维度不动）。 */
    suspend fun loadWaterQualityObservations() {
        val temps = repository.waterQualityObservations(spot.id)
            .filter { it.kind == WaterQualityObservationKind.WATER_TEMP.name }
            .mapNotNull { o -> o.value?.let { v -> o.observedAtEpochSec to v } }
        holder.applyWaterQualityObservations(temps)
    }

    // ────────────────── §6.2b 规则 5 现场校潮（Session 级观测记录） ──────────────────

    /**
     * 用户记录的高潮观测时刻（epoch 秒，Session 级暂存；持久化经 [saveTideFit] 走 repository）。
     * ≥2 条才启用语义由 TideCalibrator.fitManual 内置，UI 只透传。
     */
    private val _manualObservations = MutableStateFlow<List<Long>>(emptyList())
    val manualObservations: StateFlow<List<Long>> = _manualObservations

    /** 「记录一条高潮观测」：取当前时刻 append（重复同秒去重）。 */
    fun recordObservation(nowEpochSec: Long = System.currentTimeMillis() / 1000L) {
        _manualObservations.update { cur -> if (cur.lastOrNull() == nowEpochSec) cur else cur + nowEpochSec }
    }

    /** 清空本次会话观测记录。 */
    fun clearObservations() {
        _manualObservations.value = emptyList()
    }

    /**
     * M6（§6.2b 规则 5 / CHG-16⑦）：拟合启用（adopted）时持久化 + force 刷新 →
     * SpotDetailResult.tideCorrection 透出，潮 Tab 渲染「校正前/后」对照（D12）。
     * adopted=false 不落库（登记语义；D12 对照以刷新结果 tideCorrection 为准）。
     */
    fun saveTideFit(fit: TideCalibrator.ManualTideFit) {
        if (!fit.adopted) return
        viewModelScope.launch(ioDispatcher) {
            runCatching { repository.saveManualTideFit(spotRef.id, fit) }
            holder.setRefreshing(true)
            runCatching { repository.refreshSpotDetail(spotRef, force = true) }
                .onSuccess { holder.onResult(it) }
                .onFailure { holder.setRefreshing(false) }
        }
    }

    fun selectTab(tab: DetailTab) = holder.selectTab(tab)
    fun selectDay(dayIndex: Int) = holder.selectDay(dayIndex)
    fun selectHour(hourIndex: Int?) = holder.selectHour(hourIndex)

    // 供 Screen 消费（保持单测可对 holder 纯逻辑断言）
    internal fun holderInternal(): DetailStateHolder = holder

    /**
     * OPT-V11-03（CHG-27）：Top10 时段筛选重算（转发 holder；与全窗同源、只加输出过滤）。
     * 0/0 = 不筛选（与 state.top10 恒等）。
     */
    fun top10Filtered(windowStartEpochSec: Long, windowEndEpochSec: Long): BestWindowFinder.Output =
        holder.top10Filtered(windowStartEpochSec, windowEndEpochSec)
}



