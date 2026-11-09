package com.tidespot.core.domain

import com.tidespot.core.domain.algorithm.SubScores
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 潮汐子分：dh/dt 符号与"停流"判定（§10.1-3 TideRateTest；SRS §6.2b）。
 */
class TideSubScoreTest {

    @Test
    fun `dh 正负不影响子分 - 流动幅度才是主导`() {
        val rising = SubScores.tide(0.39, 1.9)
        val falling = SubScores.tide(-0.39, 1.9)
        assertEquals(rising, falling, 1e-12, "s_tide 只用 |dh|，涨落对称")
    }

    @Test
    fun `停流判定 - 绝对差分小于 0_02 m 触发 x0_72 惩罚`() {
        val slack = SubScores.tide(0.01, 1.9)
        val moving = SubScores.tide(0.05, 1.9)
        // 0.01 m/h：moving 项 = 0.01/0.16，v0=0.22+0.58·0.0625+0.2=0.256 → ×0.72 ≈ 0.1846
        assertEquals((0.22 + 0.58 * (0.01 / 0.16) + 0.20) * 0.72, slack, 1e-9)
        // 0.05 m/h 不触发停流
        assertEquals(0.22 + 0.58 * (0.05 / 0.16) + 0.20, moving, 1e-9)
        assertTrue(slack < moving)
    }

    @Test
    fun `涨落率符号序列 - 潮位差分符号与升降一致`() {
        // 构造一个半日潮位序列（m/h 级），差分符号应与升降一致，且高/低平潮处 |dh|<0.02
        val sl = listOf(0.0, 0.4, 0.9, 1.3, 1.55, 1.62, 1.61, 1.56, 1.30, 0.9, 0.45, 0.1, 0.0)
        val dh = sl.zipWithNext { a, b -> b - a }
        assertTrue(dh.take(4).all { it > 0 }, "涨潮段 dh>0")
        assertTrue(dh[4] >= 0 && abs(dh[5]) < 0.02, "高潮平潮处 |dh|<0.02（dh=${dh[5]}）")
        assertTrue(dh.drop(6).all { it < 0 }, "落潮段 dh<0")
    }

    @Test
    fun `子分边界 - 0_02 恰好不触发停流（参照实现为严格小于）`() {
        // |dh|=0.02 与 |dh|=0.019 分别处于边界两侧，保证与 fishscore.mjs 一致
        val atBoundary = SubScores.tide(0.02, 1.9)
        val justBelow = SubScores.tide(0.0199, 1.9)
        assertTrue(justBelow < atBoundary, "0.0199 应触发停流惩罚而 0.02 不触发")
    }
}
