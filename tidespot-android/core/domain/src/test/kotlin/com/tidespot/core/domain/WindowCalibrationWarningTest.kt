package com.tidespot.core.domain

import com.tidespot.core.domain.model.Confidence
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.window.BestWindowFinder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OPT-V11-09（P1）校潮状态提示锁：
 * - 文案纯函数（lag>0 延后 / lag<0 提前 / 0 无偏移）；
 * - `find` 传入有效 lag → 每条窗口注意事项含该句；不传 → 不出现（禁空串，CHG-08④）。
 */
class WindowCalibrationWarningTest {

    private val base = 1_767_225_600L // 2026-01-01T00:00:00Z

    private fun hours(n: Int, score: Int = 80): List<FishScoreResult> = (0 until n).map { i ->
        FishScoreResult(
            timeEpochSec = base + i * 3600L,
            total = score,
            factors = emptyList(),
            vetoReasons = emptyList(),
            confidence = Confidence.ESTIMATED,
            vetoCodes = emptyList(),
        )
    }

    @Test
    fun lagSign_mapsToEarlierOrLaterWording() {
        assertEquals("已按现场校潮校正：时刻延后 1h", BestWindowFinder.manualCalibrationWarning(1))
        assertEquals("已按现场校潮校正：时刻提前 2h", BestWindowFinder.manualCalibrationWarning(-2))
        assertEquals("已按现场校潮校正：时刻无偏移", BestWindowFinder.manualCalibrationWarning(0))
    }

    @Test
    fun find_withAdoptedLag_appendsNoticeToEveryWindow() {
        val out = BestWindowFinder.find(hours(240), manualTideLagHours = 1)
        assertTrue(out.windows.isNotEmpty())
        out.windows.forEach { w ->
            assertTrue(
                w.warnings.contains("已按现场校潮校正：时刻延后 1h"),
                "每条窗口注意事项都应含校潮句：${w.warnings}",
            )
        }
    }

    @Test
    fun find_withoutLag_hasNoCalibrationNotice() {
        val out = BestWindowFinder.find(hours(240))
        assertTrue(out.windows.isNotEmpty())
        assertTrue(out.windows.none { w -> w.warnings.any { it.contains("现场校潮") } })
    }
}