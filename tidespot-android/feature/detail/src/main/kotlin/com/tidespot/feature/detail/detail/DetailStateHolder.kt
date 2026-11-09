package com.tidespot.feature.detail.detail

import com.tidespot.core.data.repo.DataSource
import com.tidespot.core.data.repo.SpotDetailResult
import com.tidespot.core.data.repo.Ttl
import com.tidespot.core.domain.algorithm.ScenePreset
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.ui.breakdown.RawInputRow
import com.tidespot.core.ui.format.Format
import com.tidespot.core.ui.selector.DaySelectorLogic
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 详情页状态机（纯 Kotlin，可 JVM 单测；ViewModel 只做协程编排委托）。
 * 语义（N-2 / F-3.6）：派生序列只在 onResult 到达时计算一次；Tab/Day 切换仅改选中位；
 * 维度降级互不影响（Derivations.tabStatus）；null 全程保真（禁 0 填充）。
 */
class DetailStateHolder(
    val zone: ZoneId,
    daysRange: Int,
    val lonDeg: Double,
    val preset: ScenePreset,
) {

    private val _state = MutableStateFlow(
        DetailUiState(daysRange = DaySelectorLogic.coerceDaysRange(daysRange)),
    )
    val state: StateFlow<DetailUiState> = _state

    /** 数据到达（缓存先行或刷新完成）→ 一次性派生。 */
    fun onResult(result: SpotDetailResult, nowMillis: Long = System.currentTimeMillis()) {
        val weatherByEpoch = result.weather.associateBy { it.timeEpochSec }
        // CHG-13/N15（§6.7 第 4 行）：天气源缓存年龄 ≥48h → 不可用，评分链路短路（不再据此评分）；
        // 其余维度照常渲染（分维降级互不影响，F-3.6）。
        val weatherUnusable = Ttl.unusableAgeMillis(
            result.fetchedAtMillis[DataSource.WEATHER] ?: 0L, nowMillis,
        ) != null
        val inputs = if (weatherUnusable) emptyList() else Derivations.buildScoreInputs(result.slices, result.daily, zone)
        val scores = if (weatherUnusable) emptyList() else Derivations.computeScores(inputs, preset, lonDeg)
        val rates = Derivations.tideRates(result.marine)
        // F-3.5（M5 接通）：本点 Top10 现算（与详情页 scores 同源 → 恒等）；weather 不可用 scores 空 → find 内置禁用文案
        // CHG-15 偏差①：时间域 = 当前小时的下一整点起（SRS §6.6），历史窗口不入选
        val nowSec = nowMillis / 1000
        val nextHourEpochSec = nowSec - nowSec % 3600 + 3600
        val top10 = TopWindowDerivations.top10(scores, result.marine, nowEpochSec = nextHourEpochSec)
        val quality = if (weatherUnusable) emptyList()
        else Derivations.qualitySeries(result.slices, weatherByEpoch, zone, preset.salinityPpt)
        _state.update {
            it.copy(result = result, scores = scores, tideRates = rates, quality = quality, top10 = top10, loading = false, refreshing = false)
        }
    }

    fun setLoading(loading: Boolean) = _state.update { it.copy(loading = loading) }
    fun setRefreshing(refreshing: Boolean) = _state.update { it.copy(refreshing = refreshing) }

    /** Tab 切换：仅改选中位（N-2 不重算）。 */
    fun selectTab(tab: DetailTab) = _state.update { it.copy(selectedTab = tab, selectedHour = null) }

    /** 日切换：仅改选中位并清空小时选中。 */
    fun selectDay(dayIndex: Int) {
        if (dayIndex in 0 until _state.value.daysRange) {
            _state.update { it.copy(selectedDay = dayIndex, selectedHour = null) }
        }
    }

    fun selectHour(hourIndex: Int?) = _state.update { it.copy(selectedHour = hourIndex) }

    fun scoresByEpoch(): Map<Long, FishScoreResult> = _state.value.scores.associateBy { it.timeEpochSec }

    /** 当日溶氧潜力（ΠV 重算与原始输入区共用；缺测 → null 保真）。 */
    fun doPotentialByEpoch(): Map<Long, Double?> =
        _state.value.quality.associate { it.timeEpochSec to it.doMgL }

    /** 原始输入区（§8.2-3 清单；缺测显示 —，不补 0）。 */
    fun rawInputsFor(epochSec: Long): List<RawInputRow> {
        val result = _state.value.result ?: return emptyList()
        val w = result.weather.firstOrNull { it.timeEpochSec == epochSec } ?: return emptyList()
        val m = result.marine.firstOrNull { it.timeEpochSec == epochSec }
        val q = _state.value.quality.firstOrNull { it.timeEpochSec == epochSec }
        val byEpoch = result.weather.associateBy { it.timeEpochSec }
        fun row(label: String, v: Double?, digits: Int = 1) =
            RawInputRow(label, if (v == null) "—" else Format.value(v, digits))
        return listOf(
            row("Tw", q?.waterTempC),
            row("DO_p", q?.doMgL, 2),
            row("P", w.pressureMslHpa),
            row("ΔP3", Derivations.deltaPressure(byEpoch, epochSec, 3), 2),
            row("ΔP24", Derivations.deltaPressure(byEpoch, epochSec, 24), 2),
            row("风", w.windSpeedKmh),
            row("阵风", w.windGustKmh),
            row("雨", w.precipMm, 2),
            row("云", w.cloudCoverPct, 0),
            row("WMO", w.weatherCode?.toDouble(), 0),
            row("浪", m?.waveHeightM),
            row("潮位", m?.seaLevelHeightMslM),
            row("dh/dt", _state.value.tideRates[epochSec]),
            row("流速", m?.oceanCurrentVelocityKmh),
            row("浊度", q?.turbidityProxy, 2),
            row("叶绿素", q?.chlorophyllProxy, 2),
        )
    }
}
