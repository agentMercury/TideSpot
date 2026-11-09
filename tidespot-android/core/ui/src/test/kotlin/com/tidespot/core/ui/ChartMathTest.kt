package com.tidespot.core.ui

import com.tidespot.core.ui.chart.ChartMath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** M4 退出标准硬性项：HourChart null 断点逻辑单测（null 不连线 / 断段切分 / 禁补 0）。 */
class ChartMathTest {

    @Test
    fun nullSplitsIntoSegments_noBridgingAcrossNull() {
        // 0..23 中 5、6、12 为 null → 三段连续非 null 段，null 小时不属于任何段
        val v: List<Double?> = (0..23).map { h -> if (h == 5 || h == 6 || h == 12) null else h * 0.1 }
        val segments = ChartMath.splitSegments(v)
        assertEquals(listOf(0..4, 7..11, 13..23), segments.map { it.startHour..it.endHourInclusive })
        // 段内点不含 null（null 永不出现在连线输出里）
        segments.forEach { seg ->
            seg.startHour..seg.endHourInclusive
            assertTrue(ChartMath.segmentPoints(v, seg).all { (h, _) -> v[h] != null })
        }
    }

    @Test
    fun leadingAndTrailingNullsAreTrimmed() {
        val v: List<Double?> = listOf(null, null) + listOf(1.0, 2.0) + listOf(null, null)
        val segments = ChartMath.splitSegments(v)
        assertEquals(listOf(2..3), segments.map { it.startHour..it.endHourInclusive })
    }

    @Test
    fun allNullGivesNoSegments_andNeutralYAxis() {
        val v: List<Double?> = List(24) { null }
        assertTrue(ChartMath.splitSegments(v).isEmpty())
        val axis = ChartMath.yAxis(v)
        assertTrue(axis.max > axis.min) // 空轴占位框架，不产生「值=0」语义
    }

    @Test
    fun isolatedNullBetweenValues_doesNotConnect() {
        // 两侧有值、中间单点 null：切为两段，且两段点集互不包含 null
        val v: List<Double?> = listOf(1.0, null, 3.0)
        val segments = ChartMath.splitSegments(v)
        assertEquals(2, segments.size)
        assertEquals(listOf(0..0, 2..2), segments.map { it.startHour..it.endHourInclusive })
    }

    @Test
    fun yAxisAdaptsToData_notToZero() {
        val v: List<Double?> = listOf(1.2, null, 1.8, 2.4)
        val axis = ChartMath.yAxis(v)
        assertTrue(axis.min < 1.2)   // 下界外扩
        assertTrue(axis.max > 2.4)   // 上界外扩
        assertTrue(axis.ticks.isNotEmpty())
        // 0 不在数据范围内时轴底不应出现 0（数据 1.2–2.4，min 外扩 8% 仍 >1）
        assertTrue(axis.min > 0.0)
    }

    @Test
    fun yAxisNormalizesWithinBounds() {
        val axis = ChartMath.yAxis(listOf(0.0, 10.0))
        assertTrue(axis.normalized(5.0) in 0.0..1.0)
        assertTrue(axis.normalized(-100.0) == 0.0)
        assertTrue(axis.normalized(100.0) == 1.0)
    }

    @Test
    fun windowHours_overThresholdOnly_andNullNeverQualifies() {
        val v: List<Double?> = listOf(79.0, 80.0, null, 99.9, 60.0)
        val hours = ChartMath.windowHours(v)
        assertEquals(setOf(1, 3), hours)
    }

    @Test
    fun windowSegments_mergeContiguousHours() {
        val segs = ChartMath.windowSegments(setOf(3, 4, 5, 9), 24)
        assertEquals(listOf(3..5, 9..9), segs.map { it.startHour..it.endHourInclusive })
    }
    // ── D04（CHG-16⑪）：niceStep 10 的负幂候选 + BAR 固定 0–100 轴 ──

    @Test
    fun niceStep_supportsNegativePowersOfTen_smallSpanAxisNotDegenerate() {
        val axis = ChartMath.yAxis(listOf(0.21, 0.27))
        assertTrue(axis.max > axis.min, "小跨度轴必须 lo<hi")
        assertTrue(axis.step < 1.0, "step 应落到负幂档（实际 ${axis.step}）")
        assertTrue(axis.ticks.size >= 2)
    }

    @Test
    fun niceStep_hundredthSpan_usesThousandthStep() {
        val axis = ChartMath.yAxis(listOf(0.002, 0.004))
        assertTrue(axis.max > axis.min)
        assertTrue(axis.step <= 0.005, "极小跨度 step 应 ≤0.005（实际 ${axis.step}）")
    }

    @Test
    fun barYAxis_fixedZeroTo100_independentOfData() {
        val axis = ChartMath.barYAxis()
        assertEquals(0.0, axis.min)
        assertEquals(100.0, axis.max)
        assertEquals(listOf(0.0, 25.0, 50.0, 75.0, 100.0), axis.ticks)
        assertEquals(25.0, axis.step)
        assertEquals(0.8, axis.normalized(80.0))
        assertEquals(0.0, axis.normalized(-5.0))
        assertEquals(1.0, axis.normalized(120.0))
        assertEquals(axis, ChartMath.barYAxis())
    }
}

