package com.tidespot.feature.detail

import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.tide.TideObservationType
import com.tidespot.feature.detail.detail.TopWindowDerivations
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OPT-V11-07（P1）分类型模型标记锁：涨→落翻转 = 高潮/开始退潮；落→涨翻转 = 低潮/开始涨潮。
 * 与既有 `extremeTimes`（任意极值）口径一致，只是按类型拆分。
 */
class TideMarkersTest {

    private val base = 1_700_000_000L
    private val h = 3600L

    private fun marine(levels: List<Double>) = levels.mapIndexed { i, v ->
        HourlyMarine(timeEpochSec = base + i * h, seaLevelHeightMslM = v)
    }

    @Test
    fun flipsAreClassifiedByDirection() {
        // 1.0 → 1.4 → 1.8 → 1.4 → 1.0 → 0.6 → 0.2 → 0.6 → 1.0
        // 差分：+ + - - - - + +  → 高潮在 i=2（+→−），低潮在 i=6（−→+）
        val markers = TopWindowDerivations.tideMarkers(
            marine(listOf(1.0, 1.4, 1.8, 1.4, 1.0, 0.6, 0.2, 0.6, 1.0)),
        )
        assertEquals(listOf(base + 2 * h), markers[TideObservationType.HIGH])
        assertEquals(listOf(base + 2 * h), markers[TideObservationType.FALLING_START])
        assertEquals(listOf(base + 6 * h), markers[TideObservationType.LOW])
        assertEquals(listOf(base + 6 * h), markers[TideObservationType.RISING_START])
    }

    @Test
    fun allNullInland_returnsEmptyMarkers() {
        val markers = TopWindowDerivations.tideMarkers(
            marine(listOf(0.0, 0.0, 0.0)).map { it.copy(seaLevelHeightMslM = null) },
        )
        TideObservationType.entries.forEach { assertEquals(emptyList(), markers[it]) }
    }

    @Test
    fun typedFit_usesMarkersByType() {
        // 模型：高潮 base+2h、低潮 base+6h；观测「开始涨潮」记录 base+6h+1h、「开始退潮」记录 base+2h+1h → lag=+1
        val m = marine(listOf(1.0, 1.4, 1.8, 1.4, 1.0, 0.6, 0.2, 0.6, 1.0))
        val fit = TopWindowDerivations.manualFitTyped(
            listOf(
                TideObservationType.RISING_START to base + 6 * h + h,
                TideObservationType.FALLING_START to base + 2 * h + h,
            ),
            m,
        )
        assertEquals(true, fit.adopted)
        assertEquals(1, fit.lagHours)
    }
}