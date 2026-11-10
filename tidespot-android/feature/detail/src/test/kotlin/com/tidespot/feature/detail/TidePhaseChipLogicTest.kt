package com.tidespot.feature.detail

import com.tidespot.core.domain.algorithm.Solunar
import com.tidespot.core.domain.tide.TidePhase
import com.tidespot.feature.detail.detail.TidePhaseChipLogic
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UX-23（OPT-V11-23）潮汛 chip 纯函数锁：当地日正午月龄 → TidePhase 文案（零请求、仅展示）。
 * 黄金锚点：附录 B2 —— 2026-11-09 为新月（月龄 ≈0.4 天，容差 ±0.6）→ 大潮 · 活汛。
 */
class TidePhaseChipLogicTest {

    private val zone = ZoneOffset.ofHours(8)

    @Test
    fun newMoonDate_showsSpringActive() {
        assertEquals("大潮 · 活汛", TidePhaseChipLogic.chipText(LocalDate.of(2026, 11, 9), zone))
    }

    @Test
    fun chipMatchesTidePhaseOfLocalNoon() {
        val date = LocalDate.of(2026, 11, 20)
        val noonMillis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(TidePhase.labelFor(Solunar.lunarAge(noonMillis)), TidePhaseChipLogic.chipText(date, zone))
    }

    @Test
    fun chipNeverBlank_andSizeLabelsAreExhaustive() {
        var active = 0
        var dead = 0
        for (day in 1..29) {
            val text = TidePhaseChipLogic.chipText(LocalDate.of(2026, 11, day), zone)
            assertTrue(text.isNotBlank(), "2026-11-$day 潮汛文案不得为空")
            if (text.contains("活汛")) active++
            if (text.contains("死汛")) dead++
        }
        assertTrue(active > 0 && dead > 0, "一个朔望月内活汛/死汛都应出现（实际 $active/$dead）")
    }
}