package com.tidespot.core.domain.window

import com.tidespot.core.domain.model.BestWindow

/**
 * 首页「下一个最佳窗口」挑选器（对齐 Web `domain/nextWindow.ts`）。
 *
 * ## 与 [BestWindowFinder] 的分工
 * - [BestWindowFinder] 回答「哪个窗口**最好**」——按 rank（峰值·均值·时长加权）排序
 * - **本对象**回答「现在/接下来**该不该出门**」——按**时间紧迫性**排序
 *
 * 刻意不按 rank 选：若按分数选，可能提示「傍晚有个 92 分窗口」，
 * 却漏掉「此刻正有 78 分窗口只剩 40 分钟」——后者才是当下最需要知道的。
 *
 * ## 窗口结束时刻怎么算（**本文件最容易出错的地方**）
 * [BestWindow] 没有直接的「窗口结束」字段，且 [BestWindow.durationHours] **不是**原窗长度——
 * 它在 OPT-V11-25 之后存的是**核心段小时数**（峰值 ±2h，≤4h）。用它算结束会算短。
 *
 * 正确的结束时刻来源：
 * - `truncatedUntilEpochSec != null` → 该值即原窗尾（核心段短于原窗时才会填）
 * - `truncatedUntilEpochSec == null` → 核心段即整窗，原窗尾 = [BestWindow.coreEndEpochSec]
 *
 * 再 +3600 得到**开区间右端**（因为尾字段是最后一小时的**起点**，不是收杆时刻）。
 *
 * 该语义与 Web `windowEndExclusive(w) = w.endEpochSec + 3600` 对齐。
 */
object NextWindowFinder {

    /** 挑选结果状态。 */
    enum class State {
        /** 正在窗口内（用户应立刻出门） */
        IN_WINDOW,

        /** 有未来窗口（可以准备） */
        UPCOMING,

        /** 无可用窗口（不渲染提示条） */
        NONE,
    }

    /**
     * 挑选结果。
     *
     * @property state 三种状态之一
     * @property window 命中的窗口（state == NONE 时为 null）
     * @property remainingSec 剩余秒数：IN_WINDOW = 距窗口结束；UPCOMING = 距窗口开始；NONE = 0
     * @property remainingHours IN_WINDOW 时窗内剩余小时数（向上取整，最小 1）；其余为 0
     */
    data class Result(
        val state: State,
        val window: BestWindow? = null,
        val remainingSec: Long = 0,
        val remainingHours: Int = 0,
    )

    /**
     * 窗口尾时刻（最后一小时的**起点**，不含 3600 偏移）。
     *
     * 优先用 [BestWindow.truncatedUntilEpochSec]（核心段短于原窗时的原窗尾）；
     * 为 null 表示核心段即整窗，取 [BestWindow.coreEndEpochSec]。
     *
     * 兜底：若两个字段都未派生（0，向后兼容旧数据），用峰值 + 核心段长度估算。
     */
    fun windowTailEpochSec(window: BestWindow): Long {
        window.truncatedUntilEpochSec?.let { return it }
        if (window.coreEndEpochSec > 0) return window.coreEndEpochSec
        // 未派生（旧数据）：峰值小时起到核心段末尾
        return window.peakEpochSec + (window.durationHours - 1).coerceAtLeast(0) * 3600L
    }

    /**
     * 窗口实际覆盖的开区间右端（不含）。
     *
     * = [windowTailEpochSec] + 3600：尾字段是最后一小时起点，收杆时刻在其后一小时。
     */
    fun windowEndExclusive(window: BestWindow): Long = windowTailEpochSec(window) + 3600L

    /** 判断某时刻是否落在窗口内（左闭右开 `[start, endExclusive)`）。 */
    fun isInWindow(window: BestWindow, nowEpochSec: Long): Boolean =
        nowEpochSec >= window.startEpochSec && nowEpochSec < windowEndExclusive(window)

    /**
     * 从一组窗口里挑出「此刻最该关注的那个」。
     *
     * 优先级：
     * 1. 正在窗口内 → 选**剩余时间最短**的（最紧迫，提醒尽快出发）
     * 2. 否则 → 选**开始时间最近**的未来窗口
     * 3. 都没有 → [State.NONE]
     *
     * @param windows 该点的窗口列表（通常来自 [BestWindowFinder.find]；本方法不依赖其排序）
     * @param nowEpochSec 当前时刻（epoch 秒）；由调用方注入，便于单测
     */
    fun findNext(windows: List<BestWindow>, nowEpochSec: Long): Result {
        if (windows.isEmpty()) return Result(State.NONE)

        // 1) 活跃窗口：选剩余最短的（最紧迫）
        val active = windows.filter { isInWindow(it, nowEpochSec) }
        if (active.isNotEmpty()) {
            val pick = active.minBy { windowEndExclusive(it) - nowEpochSec }
            val remainingSec = windowEndExclusive(pick) - nowEpochSec
            // 向上取整，且至少 1（只剩几分钟也算「1 小时」内，避免显示 0）
            val remainingHours = ((remainingSec + 3599L) / 3600L).toInt().coerceAtLeast(1)
            return Result(State.IN_WINDOW, pick, remainingSec, remainingHours)
        }

        // 2) 未来窗口：选开始最近的
        val upcoming = windows.filter { it.startEpochSec > nowEpochSec }
        if (upcoming.isNotEmpty()) {
            val pick = upcoming.minBy { it.startEpochSec }
            return Result(State.UPCOMING, pick, pick.startEpochSec - nowEpochSec, 0)
        }

        // 3) 全部已过期
        return Result(State.NONE)
    }

    // ---------- 文案格式化（纯函数，供 UI 与测试共用） ----------

    /**
     * 时长文案：`2 小时 15 分` / `3 小时` / `45 分钟` / `不到 1 分钟` / `0 分钟`。
     *
     * 与 Web `formatDuration` 逐分支对齐（含「不足 1 分钟」的特殊处理）。
     */
    fun formatDuration(totalSec: Long): String {
        if (totalSec <= 0L) return "0 分钟"
        if (totalSec < 60L) return "不到 1 分钟"
        val totalMin = totalSec / 60L
        val hours = totalMin / 60L
        val mins = totalMin % 60L
        return when {
            hours == 0L -> "$mins 分钟"
            mins == 0L -> "$hours 小时"
            else -> "$hours 小时 $mins 分"
        }
    }

    /** 本地整点 `HH:00`（按给定时区偏移秒）。 */
    fun formatLocalHour(epochSec: Long, tzSec: Int): String {
        val h = ((epochSec + tzSec) % 86400L + 86400L) % 86400L / 3600L
        return (if (h < 10) "0$h" else "$h") + ":00"
    }

    /**
     * 窗口区间文案：`09:00–12:00`。
     *
     * 右端用 [windowEndExclusive]（收杆时刻），与 Web `formatWindowRange` 对齐。
     */
    fun formatWindowRange(window: BestWindow, tzSec: Int): String =
        formatLocalHour(window.startEpochSec, tzSec) + "–" + formatLocalHour(windowEndExclusive(window), tzSec)
}
