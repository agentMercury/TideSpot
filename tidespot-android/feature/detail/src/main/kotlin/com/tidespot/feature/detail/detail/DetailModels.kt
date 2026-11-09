package com.tidespot.feature.detail.detail

import com.tidespot.core.data.repo.DataSource
import com.tidespot.core.data.repo.SpotDetailResult
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.window.BestWindowFinder
import com.tidespot.core.domain.model.WaterQualitySample

/** 详情页 5 Tab（§8.1 顺序；标题按 WaterBodyType 切换，不隐藏 Tab）。 */
enum class DetailTab { WAVE, WEATHER, SCORE, WATER_QUALITY, TIDE }

/**
 * F-3.6 分维降级状态（互不影响：某维度失败只影响其 Tab，其余照常）。
 * [lastFetchedMillis] = 该源最近一次成功拉取时刻（「最近缓存时间」文案；≤0 = 无记录）。
 */
sealed class DimensionStatus {
    data object Available : DimensionStatus()
    data class Degraded(
        /** 「来源不可用」= 请求失败；「本点无该来源数据」= 内陆点海洋全 null（§6.7 整维度 null）。 */
        val reason: String,
        val lastFetchedMillis: Long?,
        val lastFetchedSource: DataSource?,
    ) : DimensionStatus()
}

/**
 * 详情页 UI 状态。scores/quality/tideRates 等派生序列只在 [SpotDetailResult] 到达时计算一次
 * （N-2：Tab 切换不重算）；Tab/Day/Hour 选择仅改选中位，不触发派生。
 */
data class DetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val result: SpotDetailResult? = null,
    /** 钓鱼指数逐小时（F-3.4；与 result.slices 同源按 epoch 对齐）。 */
    val scores: List<FishScoreResult> = emptyList(),
    /** 本点 Top10（F-3.5，M5 接通）：onResult 时派生层现算一次（N-2）；null = 数据未到达。 */
    val top10: BestWindowFinder.Output? = null,
    /** 潮位 dh/dt（m/h；相邻缺测 → null，禁补 0）。 */
    val tideRates: Map<Long, Double?> = emptyMap(),
    /** 水质本地推导序列（§5.3 Q-兜底；徽标「估算」）。 */
    val quality: List<WaterQualitySample> = emptyList(),
    val selectedDay: Int = 0,
    val selectedTab: DetailTab = DetailTab.WAVE,
    /** 当前选中小时（当地 0–23 下标位；null = 未选中）。 */
    val selectedHour: Int? = null,
    val daysRange: Int = 7,
)
