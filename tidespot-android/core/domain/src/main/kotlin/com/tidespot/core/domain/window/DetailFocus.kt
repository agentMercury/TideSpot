package com.tidespot.core.domain.window

import com.tidespot.core.domain.model.BestWindow

/**
 * 详情页初始定位（issue #3 体验跟进）。
 *
 * ## 问题
 * 首页提示条按**时间紧迫性**指向「下一个窗口」（如 17:00–21:00 / 88 分），
 * 用户点进详情页却看到「当日最高分」（如 06:00 / 91 分）——两个数字对不上，
 * 用户会怀疑数据不一致。
 *
 * ## 解法
 * 详情页进入时定位到**提示条指向的那个窗口**，并在评分卡下标注当日最高分与时刻。
 *
 * 本对象只做**纯计算**（不碰 UI/状态），便于单测。
 */
object DetailFocus {

    /** 应定位到的位置：[dayIndex] 相对「今天」的日偏移（0 = 今天），[hourIndex] 当地 0–23 小时下标。 */
    data class Focus(val dayIndex: Int, val hourIndex: Int)

    /**
     * 把窗口**峰值时刻**映射为详情页的 (日下标, 小时下标)。
     *
     * 用峰值而不是窗口起点：详情页图表按小时高亮，峰值那格才是该窗口分数最高的位置，
     * 也最贴近提示条上展示的 `window.peakScore`。
     *
     * @return 定位结果；窗口不在可显示范围内时返回 null（调用方保持默认「今天」）
     */
    fun locateWindow(
        window: BestWindow,
        nowEpochSec: Long,
        zoneOffsetSec: Int,
        daysRange: Int,
    ): Focus? {
        val todayLocalDay = floorDiv(nowEpochSec + zoneOffsetSec, 86400L)
        val peakLocal = window.peakEpochSec + zoneOffsetSec
        val peakDay = floorDiv(peakLocal, 86400L)
        val peakHour = (((peakLocal % 86400L) + 86400L) % 86400L / 3600L).toInt()
        val dayIndex = (peakDay - todayLocalDay).toInt()
        if (dayIndex < 0 || dayIndex >= daysRange) return null
        if (peakHour < 0 || peakHour > 23) return null
        return Focus(dayIndex, peakHour)
    }

    /**
     * 当日最高分与本地时刻（用于「最高 91 分在 06:00」标注）。
     *
     * @param dayScores 该日逐小时 (epochSec, 分数)；空 → null
     * @return (最高分, 本地小时 0–23)；空序列 → null
     */
    fun bestOfDay(dayScores: List<Pair<Long, Int>>, zoneOffsetSec: Int): Pair<Int, Int>? {
        if (dayScores.isEmpty()) return null
        val best = dayScores.maxByOrNull { it.second } ?: return null
        val local = best.first + zoneOffsetSec
        val hour = (((local % 86400L) + 86400L) % 86400L / 3600L).toInt()
        return best.second to hour
    }

    /** 向下取整除法（Kotlin `/` 对负数向零取整）。 */
    private fun floorDiv(a: Long, b: Long): Long {
        val q = a / b
        return if (a % b != 0L && (a xor b) < 0) q - 1 else q
    }
}
