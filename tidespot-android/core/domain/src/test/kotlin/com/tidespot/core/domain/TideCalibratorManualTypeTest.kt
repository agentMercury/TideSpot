package com.tidespot.core.domain

import com.tidespot.core.domain.tide.TideCalibrator
import com.tidespot.core.domain.tide.TideObservationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OPT-V11-07（P1）`fitManual` 按观测类型匹配锁：
 * - 高潮类观测只与模型**高潮**标记匹配，低潮类只与**低潮**匹配；
 * - 混合类型按中位数拟合；
 * - ≥2 条门槛与 ±6h 匹配窗沿用 CHG-04/OPT-06（不回归）。
 */
class TideCalibratorManualTypeTest {

    private val base = 1_700_000_000L
    private val h = 3600L
    private val highs = listOf(base, base + 6 * h, base + 12 * h)
    private val lows = listOf(base + 3 * h, base + 9 * h, base + 15 * h)
    private val markers = mapOf(
        TideObservationType.HIGH to highs,
        TideObservationType.LOW to lows,
        TideObservationType.FALLING_START to highs,
        TideObservationType.RISING_START to lows,
    )

    @Test
    fun highObservations_matchHighMarkers_lagPlusOne() {
        val fit = TideCalibrator.fitManual(
            observationTimesEpochSec = listOf(base + h, base + 6 * h + h),
            modelExtremeTimesEpochSec = (highs + lows).sorted(),
            observationTypes = listOf(TideObservationType.HIGH, TideObservationType.HIGH),
            modelMarkersByType = markers,
        )
        assertTrue(fit.adopted)
        assertEquals(1, fit.lagHours)
    }

    @Test
    fun lowObservations_matchLowMarkers_lagMinusTwo() {
        val fit = TideCalibrator.fitManual(
            observationTimesEpochSec = listOf(base + 3 * h - 2 * h, base + 9 * h - 2 * h),
            modelExtremeTimesEpochSec = (highs + lows).sorted(),
            observationTypes = listOf(TideObservationType.LOW, TideObservationType.LOW),
            modelMarkersByType = markers,
        )
        assertTrue(fit.adopted)
        assertEquals(-2, fit.lagHours)
    }

    @Test
    fun risingAndFallingStart_useLowAndHighMarkersRespectively() {
        // 「开始涨潮」与低潮同刻、「开始退潮」与高潮同刻
        val fit = TideCalibrator.fitManual(
            observationTimesEpochSec = listOf(
                base + 3 * h - h,   // 低潮早 1h（开始涨潮）
                base + 6 * h - h,   // 高潮早 1h（开始退潮）
            ),
            modelExtremeTimesEpochSec = (highs + lows).sorted(),
            observationTypes = listOf(TideObservationType.RISING_START, TideObservationType.FALLING_START),
            modelMarkersByType = markers,
        )
        assertTrue(fit.adopted)
        assertEquals(-1, fit.lagHours)
    }

    @Test
    fun mixedTypes_medianLag() {
        // HIGH +1h、LOW +2h → 中位数 1.5 → 四舍五入 2h
        val fit = TideCalibrator.fitManual(
            observationTimesEpochSec = listOf(base + h, base + 9 * h + 2 * h),
            modelExtremeTimesEpochSec = (highs + lows).sorted(),
            observationTypes = listOf(TideObservationType.HIGH, TideObservationType.LOW),
            modelMarkersByType = markers,
        )
        assertTrue(fit.adopted)
        assertEquals(2, fit.lagHours)
    }

    @Test
    fun singleObservation_stillNotAdopted() {
        val fit = TideCalibrator.fitManual(
            observationTimesEpochSec = listOf(base + h),
            modelExtremeTimesEpochSec = highs,
            observationTypes = listOf(TideObservationType.HIGH),
            modelMarkersByType = markers,
        )
        assertFalse(fit.adopted)
        assertEquals("再记 1 条即可校潮", fit.message)
    }

    @Test
    fun outOfWindowType_doesNotPolluteLag() {
        // 稀疏标记：仅一个高潮（base）、一个低潮（base+12h）；两条「高潮」观测中第二条离高潮 11h > ±6h
        // → 不计入（有效 1 条 → 不启用），证明类型匹配窗生效且脏数据不污染中位数
        val sparse = mapOf(
            TideObservationType.HIGH to listOf(base),
            TideObservationType.LOW to listOf(base + 12 * h),
        )
        val fit = TideCalibrator.fitManual(
            observationTimesEpochSec = listOf(base + h, base + 12 * h),
            modelExtremeTimesEpochSec = listOf(base, base + 12 * h),
            observationTypes = listOf(TideObservationType.HIGH, TideObservationType.HIGH),
            modelMarkersByType = sparse,
        )
        assertFalse(fit.adopted)
        assertTrue(fit.message!!.contains("有效观测不足"))
    }
}