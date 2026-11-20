package com.tidespot.core.domain.window

import com.tidespot.core.domain.model.FishScoreResult

/**
 * 日综合分（替换详情页大卡原先的「当日峰值」）。
 *
 * ## 为什么不用峰值
 * 详情页大卡原本取当日最高小时分。问题：上午 10 点打开，大卡显示 91（06:00 的峰值），
 * 但此刻可能只有 68 分——用户看到 91 出门，实际是「很适合」甚至「别去」。
 *
 * ## 算法（v7，双端共享口径）
 * ```
 * 可钓时段   = 05:00–21:00（按选点时区判定本地小时）
 * 基础分     = 可钓时段内逐小时分数的算术平均
 * 高光分     = 可钓时段内「最佳连续 3 小时」的平均（不足 3h 用全部）
 * 高光小时数 = 可钓时段中 ≥75 分的小时数
 * 可持续性   = clamp((高光小时数 − 1) / 7, 0, 1)    // 8h 及以上算满分
 * 提升量     = max(0, 高光分 − 基础分) × (0.5 + 0.5·可持续性) × 0.6
 * 日综合分   = 基础分 + 提升量
 * ```
 *
 * 三个设计取舍：
 * 1. **均值打底**——回答「今天整体怎么样」。全天 70 分应高于「只有 2 小时 90 分」。
 * 2. **高光加成但打折**——「有没有值得专程去的时候」也重要，但短时高光不能冒充好天。
 * 3. **安全封顶**——当天出现任意 ≤10 分的小时（通常来自雷暴硬否决 ×0.05）→ 整日分封顶
 *    [STORM_CAP]，否则雷暴日会被均值的其他时段拉成「可以」，误导用户出门。
 *
 * ## 与 BestWindowFinder 的关系
 * - [BestWindowFinder] 找「哪个时段最好」（窗口 / rank）
 * - **本对象**给「一整天打一个分」（展示在大卡）
 * 两者独立，互不改写。
 */
object DayScoreCalculator {

    /** 可钓时段起止（本地小时，含端点）。5:00–21:00 共 17 小时。 */
    const val FISHABLE_START_HOUR = 5
    const val FISHABLE_END_HOUR = 21

    /** 高光判定的分数阈值（与 ScoreBand.EXPLOSIVE 下限 80 不同：这里是「值得专程」的下限 75）。 */
    const val HIGHLIGHT_MIN_SCORE = 75

    /** 可持续性满分所需的高光小时数。 */
    const val SUSTAIN_FULL_HOURS = 8

    /** 安全封顶：出现严重小时（≤ SEVERE_SCORE）时整日分不超过此值。 */
    const val STORM_CAP = 35.0

    /** 严重小时阈值：≤ 此分视为「当日存在危险/不可用时段」。 */
    const val SEVERE_SCORE = 10

    /**
     * 计算日综合分。
     *
     * @param scores 该日逐小时评分（顺序不限；会按 epoch 升序排序后处理）
     * @param zoneOffsetSec 选点时区偏移秒（用于判定「本地小时」是否落在可钓时段）
     * @return 0–100 的日综合分；空输入返回 null（调用方显示「—」，禁编造 0）
     */
    fun compute(scores: List<FishScoreResult>, zoneOffsetSec: Int): Double? {
        if (scores.isEmpty()) return null

        val sorted = scores.sortedBy { it.timeEpochSec }

        // 按本地小时筛出可钓时段
        val fishable = sorted.filter { r ->
            val h = localHourOf(r.timeEpochSec, zoneOffsetSec)
            h in FISHABLE_START_HOUR..FISHABLE_END_HOUR
        }
        if (fishable.isEmpty()) return null

        val totals = fishable.map { it.total.toDouble() }
        val base = totals.average()

        // 最佳连续 3 小时（不足 3h → 用全部）
        val windowSize = minOf(3, totals.size)
        var best3 = Double.NEGATIVE_INFINITY
        for (i in 0..(totals.size - windowSize)) {
            val avg = totals.subList(i, i + windowSize).average()
            if (avg > best3) best3 = avg
        }
        if (best3 == Double.NEGATIVE_INFINITY) best3 = base

        val highlightHours = totals.count { it >= HIGHLIGHT_MIN_SCORE }
        val sustain = ((highlightHours - 1).toDouble() / (SUSTAIN_FULL_HOURS - 1)).coerceIn(0.0, 1.0)
        val lift = maxOf(0.0, best3 - base) * (0.5 + 0.5 * sustain) * 0.6
        val raw = base + lift

        // 安全封顶：当日存在严重小时（雷暴硬否决等）
        val hasSevere = sorted.any { it.total <= SEVERE_SCORE }
        return if (hasSevere) minOf(raw, STORM_CAP) else raw
    }

    /** 本地小时（0–23），按给定时区偏移换算。 */
    private fun localHourOf(epochSec: Long, zoneOffsetSec: Int): Int {
        val local = epochSec + zoneOffsetSec
        val secOfDay = ((local % 86400L) + 86400L) % 86400L
        return (secOfDay / 3600L).toInt()
    }
}
