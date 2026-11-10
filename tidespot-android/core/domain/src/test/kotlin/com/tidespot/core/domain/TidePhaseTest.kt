package com.tidespot.core.domain

import com.tidespot.core.domain.tide.TidePhase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OPT-V11-23（UX-23）TidePhase 纯函数锁（D10 月龄制 + D13 活汛/死汛口径）：
 * 三条 GWT + 29.53 天逐日扫描（大小潮各 2 次、活死汛各 2 段）。
 */
class TidePhaseTest {

    @Test
    fun newMoon_isSpringAndActive() {
        val p = TidePhase.of(0.4)
        assertEquals(TidePhase.TideSize.SPRING, p.size)
        assertEquals(TidePhase.TideCurrent.ACTIVE, p.current)
        assertEquals("大潮 · 活汛", p.label)
    }

    @Test
    fun firstQuarter_isNeapAndDead() {
        val p = TidePhase.of(7.4)
        assertEquals(TidePhase.TideSize.NEAP, p.size)
        assertEquals(TidePhase.TideCurrent.DEAD, p.current)
        assertEquals("小潮 · 死汛", p.label)
    }

    @Test
    fun transitionTowardFullMoon_isActiveWithoutSize() {
        val p = TidePhase.of(11.0)
        assertEquals(TidePhase.TideSize.NONE, p.size)
        assertEquals(TidePhase.TideCurrent.ACTIVE, p.current)
        assertEquals("活汛", p.label)
    }

    @Test
    fun lastQuarterBand_isNeapAndDead() {
        val p = TidePhase.of(22.2)
        assertEquals(TidePhase.TideSize.NEAP, p.size)
        assertEquals(TidePhase.TideCurrent.DEAD, p.current)
    }

    @Test
    fun fullMoonBand_isSpringAndActive() {
        val p = TidePhase.of(14.8)
        assertEquals(TidePhase.TideSize.SPRING, p.size)
        assertEquals(TidePhase.TideCurrent.ACTIVE, p.current)
    }

    @Test
    fun normalizeAge_wrapsNegativeAndOverMonth() {
        assertEquals(TidePhase.of(0.4), TidePhase.of(0.4 + 29.530588861))
        assertEquals(TidePhase.of(29.1), TidePhase.of(-0.430588861))
    }

    @Test
    fun dailyScan_springAndNeapTwice_eachCurrentTwoSegments() {
        // 逐日扫描一个朔望月（0..29 天）；段数按**循环序列**统计（跨月末首尾相连）
        val ages = (0..29).map { it.toDouble() }
        fun episodes(pred: (Double) -> Boolean): Int {
            val flags = ages.map(pred)
            return flags.indices.count { i ->
                flags[i] && !flags[(i - 1 + flags.size) % flags.size]
            }
        }
        val spring = episodes { TidePhase.of(it).size == TidePhase.TideSize.SPRING }
        val neap = episodes { TidePhase.of(it).size == TidePhase.TideSize.NEAP }
        val active = episodes { TidePhase.of(it).current == TidePhase.TideCurrent.ACTIVE }
        val dead = episodes { TidePhase.of(it).current == TidePhase.TideCurrent.DEAD }
        assertEquals(2, spring, "大潮期应出现 2 次")
        assertEquals(2, neap, "小潮期应出现 2 次")
        assertEquals(2, active, "活汛应出现 2 段")
        assertEquals(2, dead, "死汛应出现 2 段")
    }
}