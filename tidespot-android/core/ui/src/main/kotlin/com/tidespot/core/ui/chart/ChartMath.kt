package com.tidespot.core.ui.chart

/**
 * HourChart 纯计算层（§8.2-2）：把「null 断线不连线 / 断段切分 / y 轴自适应 / ≥80 窗口段」
 * 从 Compose Canvas 中抽为纯 Kotlin 函数单测（M4 退出标准硬性项：HourChart null 断点逻辑单测）。
 *
 * 红线：缺值=null 禁止 0 填充（§5.0 / §6.7）——本文件所有函数对 null 的唯一处理是「跳过/断开」，
 * 任何实现不得把 null 参与数值运算或把断段端点补 0。
 */
object ChartMath {

    /** 连续非 null 段（小时下标闭区间 [startHour, endHourInclusive]）。 */
    data class Segment(val startHour: Int, val endHourInclusive: Int) {
        val length: Int get() = endHourInclusive - startHour + 1
    }

    /**
     * null 断段切分：把 24 点序列切成若干连续非 null 段；null 小时不属于任何段（画图时断线，
     * 禁止跨 null 连线，也禁止画成 0）。首/尾 null 同样被剔除。空段列表 = 全 null。
     */
    fun splitSegments(values: List<Double?>): List<Segment> {
        val segments = ArrayList<Segment>()
        var start = -1
        for (i in values.indices) {
            if (values[i] != null) {
                if (start < 0) start = i
            } else if (start >= 0) {
                segments += Segment(start, i - 1)
                start = -1
            }
        }
        if (start >= 0) segments += Segment(start, values.size - 1)
        return segments
    }

    /** 段内取值对（供连线绘制直接消费，null 不会出现在输出中）。 */
    fun segmentPoints(values: List<Double?>, segment: Segment): List<Pair<Int, Double>> =
        (segment.startHour..segment.endHourInclusive).mapNotNull { h -> values[h]?.let { h to it } }

    /** y 轴规格：min/max 自适应（不含 null）+ 好看的刻度步长 + 刻度列表。 */
    data class YAxisSpec(val min: Double, val max: Double, val ticks: List<Double>, val step: Double) {
        /** 值 → 归一化高度 [0,1]（0=轴底，1=轴顶）；null 必须在调用侧先被段切分剔除。 */
        fun normalized(value: Double): Double {
            val span = max - min
            if (span <= 0.0) return 0.5
            return ((value - min) / span).coerceIn(0.0, 1.0)
        }
    }

    /**
     * y 轴自适应：取有效值 min/max，各向外扩 8% 余量并对齐到 nice 步长；全 null/空 →
     * 退化为 0..1 占位轴（不产生任何「值=0」的语义，只是空轴框架）。
     */
    fun yAxis(values: List<Double?>): YAxisSpec {
        val valid = values.filterNotNull()
        if (valid.isEmpty()) return YAxisSpec(0.0, 1.0, listOf(0.0, 0.5, 1.0), 0.5)
        val dataMin = valid.min()
        val dataMax = valid.max()
        val rawSpan = (dataMax - dataMin).takeIf { it > 0.0 } ?: (dataMax.coerceAtLeast(1.0) * 0.1)
        val step = niceStep(rawSpan)
        val lo = kotlin.math.floor((dataMin - rawSpan * 0.08) / step) * step
        val hi = kotlin.math.ceil((dataMax + rawSpan * 0.08) / step) * step
        val span = hi - lo
        val tickCount = (span / step).toInt().coerceIn(2, 10)
        val ticks = (0..tickCount).map { lo + it * step }.filter { it <= hi + step / 2 }
        return YAxisSpec(lo, hi, ticks, step)
    }

    /**
     * nice 步长：1/2/2.5/5 ×10^k 中选首个使段数 ≤8 的（避免刻度稀碎）。
     * D04（CHG-16⑪）：候选含 10 的负幂（0.1/0.01/0.001…）——潮位/dh 等小跨度序列此前
     * 步长被抬到 ≥1.0，floor/ceil 对齐后轴退化（lo==hi）。现从 10^-3 覆盖到 10^5。
     */
    private fun niceStep(span: Double): Double {
        val candidates = mutableListOf<Double>()
        var base = 0.001
        repeat(9) {
            for (m in listOf(1.0, 2.0, 2.5, 5.0)) candidates += m * base
            base *= 10.0
        }
        for (c in candidates) {
            if (span / c <= 8.0) return c
        }
        return candidates.last()
    }

    /**
     * BAR 型图（钓鱼指数 0–100 分）固定轴（D04）：分数域恒定，禁随当日数据自适应缩放
     * （避免「全天低分时柱被放大到满轴」的视觉误导）；刻度 0/25/50/75/100。
     */
    fun barYAxis(): YAxisSpec = YAxisSpec(
        min = 0.0,
        max = 100.0,
        ticks = listOf(0.0, 25.0, 50.0, 75.0, 100.0),
        step = 25.0,
    )

    /**
     * ≥80 分「窗口」横条覆盖的小时集合（§8.2-2：指数 Tab ≥80 段叠横条）。
     * null 分数不参与（不补 0）。阈值 [threshold] 默认 80。
     */
    fun windowHours(scores: List<Double?>, threshold: Int = ScoreWindow.THRESHOLD): Set<Int> =
        scores.indices.filter { h -> scores[h]?.let { it >= threshold } ?: false }.toSet()

    /** 窗口集合 → 连续段（供横条绘制）。 */
    fun windowSegments(hours: Set<Int>, totalHours: Int): List<Segment> {
        if (hours.isEmpty()) return emptyList()
        val segments = ArrayList<Segment>()
        var start = -1
        var prev = -2
        for (h in 0 until totalHours) {
            if (h in hours) {
                if (prev + 1 != h) {
                    if (start >= 0) segments += Segment(start, prev)
                    start = h
                }
                prev = h
            }
        }
        if (start >= 0) segments += Segment(start, prev)
        return segments
    }

    /** 别名常量（避免对 enum 的循环依赖）。 */
    object ScoreWindow {
        const val THRESHOLD = 80
    }
}

