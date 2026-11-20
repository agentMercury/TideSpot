package com.tidespot.core.domain

import com.tidespot.core.domain.model.Confidence
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.window.DayScoreCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 日综合分（v7）：均值打底 + 高光加成（可持续性折扣）+ 安全封顶。
 *
 * 覆盖 11 种典型日型，重点验证「排序合理」与「雷暴日不虚高」。
 */
class DayScoreCalculatorTest {

    private val tz = 8 * 3600

    /**
     * 本地 2026-11-17 00:00 的 epoch（UTC+8 下 UTC 是 11-16 16:00）。
     *
     * ⚠️ 这个常量**已经是本地 00:00**，不要再用 `- tz` 换算——
     * `localHourOf(dayStart + h*3600, tz)` 会直接得到 h（已实测验证）。
     */
    private val dayStart = 1794844800L

    /** 用 24 小时分数构造序列（下标 = 本地小时）。 */
    private fun day(hourly: List<Int>, dayOffset: Long = 0L): List<FishScoreResult> =
        hourly.mapIndexed { h, v ->
            FishScoreResult(
                timeEpochSec = dayStart + dayOffset * 86400L + h * 3600L,
                total = v,
                factors = emptyList(),
                vetoReasons = emptyList(),
                confidence = Confidence.MODEL,
            )
        }

    private fun score(hourly: List<Int>): Double? =
        DayScoreCalculator.compute(day(hourly), tz)

    // ---------- 基础语义 ----------

    @Test
    fun `空输入返回 null（禁编造 0）`() {
        assertNull(DayScoreCalculator.compute(emptyList(), tz))
    }

    @Test
    fun `全天均匀 - 日分等于该分`() {
        assertEquals(70.0, score(List(24) { 70 })!!, 0.01)
        assertEquals(55.0, score(List(24) { 55 })!!, 0.01)
        assertEquals(85.0, score(List(24) { 85 })!!, 0.01)
    }

    @Test
    fun `平均值口径 - 只统计可钓时段 5-21 时`() {
        // 可钓时段 17 小时全是 70，深夜 7 小时全是 20
        val hourly = MutableList(24) { 70 }
        for (h in listOf(0, 1, 2, 3, 4, 22, 23)) hourly[h] = 20
        // 若用全 24h 平均 ≈ 55.4；用可钓时段应为 70（高光加成后 ≥70）
        val v = score(hourly)!!
        assertTrue(v >= 70.0, "应按可钓时段算，实际 $v")
    }

    // ---------- 高光加成 ----------

    @Test
    fun `长时高光优于短时高光`() {
        val long = MutableList(24) { 40 }.also { for (h in 6..13) it[h] = 90 }   // 8 小时
        val short = MutableList(24) { 40 }.also { for (h in 18..19) it[h] = 90 } // 2 小时
        val vl = score(long)!!
        val vs = score(short)!!
        assertTrue(vl > vs, "8h 高光($vl) 应高于 2h 高光($vs)")
        assertTrue(vl - vs > 15, "差距应显著，实际 ${vl - vs}")
    }

    @Test
    fun `单峰窄窗不冒充好天 - 低于全天均匀中上`() {
        val narrow = MutableList(24) { 40 }.also { for (h in 18..19) it[h] = 92 }
        val steady = List(24) { 70 }
        assertTrue(score(narrow)!! < score(steady)!!, "窄窗(${score(narrow)}) 应低于全天 70 分")
    }

    // ---------- 安全封顶 ----------

    @Test
    fun `雷暴日封顶 - 即使其他时段好也不超过 35`() {
        // 上午不错，中午三个小时因雷暴被硬否决到 5 分，傍晚又好
        val storm = listOf(
            45, 42, 40, 38, 42, 55, 70, 72, 68, 60, 55, 50,
            5, 5, 5, 50, 60, 65, 70, 68, 60, 52, 48, 45,
        )
        val v = score(storm)!!
        assertEquals(35.0, v, 0.01, "有雷暴小时应封顶到 35，实际 $v")
    }

    @Test
    fun `无雷暴不封顶`() {
        val good = listOf(
            45, 42, 40, 38, 42, 55, 91, 88, 75, 68, 62, 58,
            55, 52, 50, 60, 72, 88, 90, 85, 78, 70, 60, 52,
        )
        val v = score(good)!!
        assertTrue(v > 35.0, "无雷暴不应封顶，实际 $v")
    }

    @Test
    fun `封顶阈值边界 - 恰好 10 分触发封顶`() {
        val atEdge = MutableList(24) { 80 }.also { it[12] = 10 }
        assertEquals(35.0, score(atEdge)!!, 0.01)
        val aboveEdge = MutableList(24) { 80 }.also { it[12] = 11 }
        assertTrue(score(aboveEdge)!! > 35.0, "11 分不触发封顶")
    }

    // ---------- 排序合理性 ----------

    @Test
    fun `11 种日型排序符合直觉`() {
        val all85 = List(24) { 85 }
        val peak91 = listOf(
            45, 42, 40, 38, 42, 55, 91, 88, 75, 68, 62, 58,
            55, 52, 50, 60, 72, 88, 90, 85, 78, 70, 60, 52,
        )
        val longHigh = MutableList(24) { 40 }.also { for (h in 6..13) it[h] = 90 }
        val singlePeak = listOf(
            30, 28, 25, 25, 30, 40, 50, 55, 58, 60, 55, 50,
            45, 42, 40, 55, 70, 85, 92, 90, 78, 65, 50, 40,
        )
        val steady70 = List(24) { 70 }
        val shortHigh = MutableList(24) { 40 }.also { for (h in 18..19) it[h] = 90 }
        val steady55 = List(24) { 55 }
        val bad = listOf(
            25, 22, 20, 20, 25, 30, 35, 38, 40, 38, 35, 32,
            30, 28, 26, 30, 35, 38, 42, 45, 40, 35, 28, 25,
        )

        val scores = listOf(all85, peak91, longHigh, singlePeak, steady70, shortHigh, steady55, bad)
            .map { score(it)!! }

        // 严格递减：全天85 > 双峰 > 长高光 > 单峰 > 全天70 > 短高光 > 全天55 > 差天
        for (i in 0 until scores.size - 1) {
            assertTrue(
                scores[i] > scores[i + 1],
                "第 ${i + 1} 项(${scores[i]}) 应严格大于第 ${i + 2} 项(${scores[i + 1]})",
            )
        }
    }

    // ---------- 跨日 ----------

    @Test
    fun `不同日期互不影响 - 按 epoch 自然区分`() {
        val d0 = day(List(24) { 80 }, dayOffset = 0)
        val d1 = day(List(24) { 40 }, dayOffset = 1)
        assertEquals(80.0, DayScoreCalculator.compute(d0, tz)!!, 0.01)
        assertEquals(40.0, DayScoreCalculator.compute(d1, tz)!!, 0.01)
    }

    @Test
    fun `时区偏移生效 - 同一 epoch 在 UTC 与 UTC+8 下可钓时段不同`() {
        // 本地 00:00–04:00 是深夜（UTC 前一天 16:00-20:00）
        val hourly = MutableList(24) { 30 }.also { for (h in 0..4) it[h] = 30; for (h in 5..21) it[h] = 75 }
        val d = day(hourly)
        val vUtc8 = DayScoreCalculator.compute(d, tz)!!
        val vUtc = DayScoreCalculator.compute(d, 0)!!
        assertTrue(vUtc8 != vUtc, "不同时区应落在不同可钓时段，$vUtc8 vs $vUtc")
    }
    // ---------- 双端对拍（黄金值由 Web 生成） ----------

    /**
     * 与 Web `computeDayScore` 逐字段对拍。
     *
     * 黄金值来自 Web 侧实际运行输出（不是手算），容差 1e-6。
     * 若此项失败，说明两端算法漂移——**必须**先查清再改，不允许放宽容差。
     */
    @Test
    fun `双端对拍 - 10 种日型与 Web 完全一致`() {
        data class Case(val name: String, val hourly: List<Int>, val expected: Double)
        val cases = listOf(
            Case("all85", List(24) { 85 }, 85.000000),
            Case("all70", List(24) { 70 }, 70.000000),
            Case("all55", List(24) { 55 }, 55.000000),
            Case("all40", List(24) { 40 }, 40.000000),
            Case(
                "peaks91",
                listOf(45, 42, 40, 38, 42, 55, 91, 88, 75, 68, 62, 58, 55, 52, 50, 60, 72, 88, 90, 85, 78, 70, 60, 52),
                80.025210,
            ),
            Case(
                "long8h90",
                MutableList(24) { 40 }.also { for (h in 6..13) it[h] = 90 },
                79.411765,
            ),
            Case(
                "short2h90",
                MutableList(24) { 40 }.also { for (h in 18..19) it[h] = 90 },
                55.294118,
            ),
            Case(
                "single92",
                listOf(30, 28, 25, 25, 30, 40, 50, 55, 58, 60, 55, 50, 45, 42, 40, 55, 70, 85, 92, 90, 78, 65, 50, 40),
                72.764706,
            ),
            Case(
                "bad",
                listOf(25, 22, 20, 20, 25, 30, 35, 38, 40, 38, 35, 32, 30, 28, 26, 30, 35, 38, 42, 45, 40, 35, 28, 25),
                37.282353,
            ),
            Case(
                "storm",
                listOf(45, 42, 40, 38, 42, 55, 70, 72, 68, 60, 55, 50, 5, 5, 5, 50, 60, 65, 70, 68, 60, 52, 48, 45),
                35.000000,
            ),
        )
        for (c in cases) {
            val actual = score(c.hourly)!!
            assertEquals(c.expected, actual, 1e-6, c.name + " 与 Web 不一致：实际 " + actual + " 期望 " + c.expected)
        }
    }
}
