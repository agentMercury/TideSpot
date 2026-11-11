package com.tidespot.feature.detail.detail

import com.tidespot.core.data.repo.DataSource
import com.tidespot.core.data.repo.SpotDetailResult
import com.tidespot.core.data.repo.Ttl
import com.tidespot.core.domain.algorithm.ScenePreset
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.window.BestWindowFinder
import com.tidespot.core.ui.breakdown.RawInputRow
import com.tidespot.core.ui.format.Format
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.strings.ZhStrings
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

    /** OPT-V11-03（CHG-27）：最近一次 onResult 的「下一整点」（时段筛选重算的时间域下限）。 */
    private var lastNextHourEpochSec = 0L

    /** OPT-V11-02（CHG-27）：已加载的水温实测（epoch 秒 → ℃），onResult 与录入后重算共用。 */
    private var waterTempObservations: List<Pair<Long, Double>> = emptyList()

    /**
     * OPT-V11-02（CHG-27）：应用/刷新水温实测 → 重算 quality（其他派生不动）。
     * ±6h 融合与徽标口径在 [Derivations.qualitySeries]（§6.1.3）。
     */
    fun applyWaterQualityObservations(observations: List<Pair<Long, Double>>) {
        waterTempObservations = observations
        val s = _state.value
        val result = s.result ?: return
        val quality = Derivations.qualitySeries(
            result.slices,
            result.weather.associateBy { it.timeEpochSec },
            zone,
            preset.salinityPpt,
            waterTempObservations = waterTempObservations,
        )
        _state.update { it.copy(quality = quality) }
    }

    /**
     * OPT-V11-03（CHG-27）：按输出时段重算 Top10——与全窗同源（state.scores + result.marine + 同一 find），
     * 仅加 windowStart/End 输出过滤（不改候选/rank/去重；空结果兜底取时段内最高分）。
     */
    fun top10Filtered(windowStartEpochSec: Long, windowEndEpochSec: Long): BestWindowFinder.Output {
        val s = _state.value
        return TopWindowDerivations.top10(
            scores = s.scores,
            marine = s.result?.marine ?: emptyList(),
            nowEpochSec = lastNextHourEpochSec,
            windowStartEpochSec = windowStartEpochSec,
            windowEndEpochSec = windowEndEpochSec,
            // OPT-V11-09：与全窗同源透传有效校潮 lag（无校正 = null）
            manualTideLagHours = s.result?.tideCorrection?.takeIf { it.adopted }?.lagHours,
        )
    }

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
        val top10 = TopWindowDerivations.top10(
            scores,
            result.marine,
            nowEpochSec = nextHourEpochSec,
            // OPT-V11-09（P1）：有效现场校潮 → Top10 注意事项追加「已按现场校潮校正：提前/延后 Xh」
            manualTideLagHours = result.tideCorrection?.takeIf { it.adopted }?.lagHours,
        )
        lastNextHourEpochSec = nextHourEpochSec
        val quality = if (weatherUnusable) emptyList()
        else Derivations.qualitySeries(
            result.slices,
            weatherByEpoch,
            zone,
            preset.salinityPpt,
            waterTempObservations = waterTempObservations,
        )
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
    fun rawInputsFor(epochSec: Long, strings: TideSpotStrings = ZhStrings): List<RawInputRow> {
        val result = _state.value.result ?: return emptyList()
        val w = result.weather.firstOrNull { it.timeEpochSec == epochSec } ?: return emptyList()
        val m = result.marine.firstOrNull { it.timeEpochSec == epochSec }
        val q = _state.value.quality.firstOrNull { it.timeEpochSec == epochSec }
        val byEpoch = result.weather.associateBy { it.timeEpochSec }
        fun row(label: String, v: Double?, digits: Int = 1) =
            RawInputRow(label, if (v == null) Format.MISSING else Format.value(v, digits))
        return listOf(
            row("Tw", q?.waterTempC),
            row("DO_p", q?.doMgL, 2),
            row("P", w.pressureMslHpa),
            row("ΔP3", Derivations.deltaPressure(byEpoch, epochSec, 3), 2),
            row("ΔP24", Derivations.deltaPressure(byEpoch, epochSec, 24), 2),
            row(strings.rawWind, w.windSpeedKmh),
            row(strings.rawGust, w.windGustKmh),
            row(strings.rawRain, w.precipMm, 2),
            row(strings.rawCloud, w.cloudCoverPct, 0),
            row("WMO", w.weatherCode?.toDouble(), 0),
            row(strings.rawWave, m?.waveHeightM),
            row(strings.rawTideLevel, m?.seaLevelHeightMslM),
            row("dh/dt", _state.value.tideRates[epochSec]),
            row(strings.rawCurrent, m?.oceanCurrentVelocityKmh),
            row(strings.rawTurbidity, q?.turbidityProxy, 2),
            row(strings.rawChlorophyll, q?.chlorophyllProxy, 2),
        )
    }
}
