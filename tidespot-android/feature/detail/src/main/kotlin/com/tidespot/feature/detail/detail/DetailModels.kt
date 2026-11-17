package com.tidespot.feature.detail.detail

import com.tidespot.core.data.repo.DataSource
import com.tidespot.core.data.repo.SpotDetailResult
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.window.BestWindowFinder
import com.tidespot.core.domain.model.WaterQualitySample

/**
 * 详情页 5 Tab（UX-22 / CHG-21 顺序：**钓鱼指数 → 潮流(水位) → 天气 → 水质 → 风浪(湖面风与水温)**；
 * 标题按 WaterBodyType 切换，不隐藏 Tab）。
 *
 * 枚举序 = TabRow 展示序（`ordinal` 直接作 `selectedTabIndex`），必须与
 * `WaterBodyTabTitles.titlesInTabOrder()` 逐位对应——两处任一单独改动都会造成标题与 Tab 错位
 * （= CHG-13/D01 回归），故成对修改并由顺序锁测试守护。
 */
enum class DetailTab { SCORE, TIDE, WEATHER, WATER_QUALITY, WAVE }

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
 * OPT-V11-07（P1）：现场观测草稿（四类 + 时刻）。[type] = null 仅历史无类型观测读回时出现。
 */
data class TideObservationDraft(
    val type: com.tidespot.core.domain.tide.TideObservationType?,
    val epochSec: Long,
)

/**
 * OPT-V12-03（CHG-57）：手动下拉刷新的结果分类。
 * 本层只携带**枚举**，文案由 UI 侧按资源映射（`:core:*` 纯 Kotlin 层禁硬编码中文）。
 */
enum class RefreshFeedback { UPDATED, FAILED, OFFLINE }

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
    /** UX-22：默认落地 Tab = 钓鱼指数（Tab 序首位）。 */
    val selectedTab: DetailTab = DetailTab.SCORE,
    /** 当前选中小时（当地 0–23 下标位；null = 未选中）。 */
    val selectedHour: Int? = null,
    val daysRange: Int = 7,
    /** OPT-V12-03：最近一次手动刷新结果（null = 尚无手动刷新 → 不渲染提示）。 */
    val refreshFeedback: RefreshFeedback? = null,
    /** OPT-V12-03：最近一次手动刷新成功时刻（epoch 毫秒；null = 无，禁编造时间）。 */
    val lastRefreshAtMillis: Long? = null,)
