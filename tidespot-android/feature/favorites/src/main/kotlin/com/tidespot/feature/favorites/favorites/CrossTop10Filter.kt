package com.tidespot.feature.favorites.favorites

import com.tidespot.core.domain.window.Top10TimeFilter

/**
 * OPT-A-04：跨点 Top10 的「时段筛选 + ≥60 分门槛」（设计稿 OV01 口径，纯函数 / JVM 可测）。
 *
 * 两条规则都来自设计稿 `screens_full/ov01-top10.png`：
 * 1. **无 ≥60 分窗口时显示空态，禁止兜底造假**——所以门槛是过滤，不是排序权重；
 * 2. 时段 Chip（全部 / 接下来 24h / 本周末 / 自选）只做**输出过滤**，
 *    窗口已由各点 `topWindows` 算好，这里不改 rank/去重公式。
 *
 * 与 `feature:detail` 的差异：详情页把时段传给 `BestWindowFinder.find` **重算**候选；
 * 跨点聚合没有重算入口，故用半开区间相交（口径见 [Top10TimeFilter.intersects]）。
 */
object CrossTop10Filter {

    /** 设计稿门槛：低于 60 分的窗口不进 Top10（宁可空态，也不凑数）。 */
    const val MIN_SCORE = 60

    /**
     * 过滤跨点 Top10。
     *
     * @param range null = 「全部」（不筛时段）；非空 = 与该区间相交的窗口才保留
     */
    fun filter(
        entries: List<CrossSpotTop10Entry>,
        range: Top10TimeFilter.TimeRange?,
    ): List<CrossSpotTop10Entry> = entries.filter { entry ->
        val w = entry.window
        w.peakScore >= MIN_SCORE &&
            Top10TimeFilter.intersects(
                range = range,
                windowStartSec = w.startEpochSec,
                // BestWindow 不存 endEpochSec：起 + 时长即窗口终点（时长 ≥1h，恒正）
                windowEndSec = w.startEpochSec + w.durationHours * 3600L,
            )
    }
}
