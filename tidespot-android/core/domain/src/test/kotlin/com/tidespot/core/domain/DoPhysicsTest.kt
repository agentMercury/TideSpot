package com.tidespot.core.domain

import com.tidespot.core.domain.water.DoPhysics
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B1 溶氧公式黄金值（SRS 附录 B1，容差 ±0.05 mg/L；§10.1-1 DoPhysicsTest）。
 * (10, 1013.25, 0)=11.25 | (25, 1013.25, 0)=8.27 | (25, 990, 0)=8.07 | (25, 1013.25, 30)=6.41
 */
class DoPhysicsTest {

    private fun assertGolden(tw: Double, p: Double, salt: Double, expected: Double) {
        assertEquals(expected, DoPhysics.doPotential(tw, p, salt), 0.05, "DO_p($tw, $p, $salt)")
    }

    @Test
    fun `B1 黄金值 4 组 - 标准大气淡水高温`() = assertGolden(25.0, 1013.25, 0.0, 8.27)

    @Test
    fun `B1 黄金值 4 组 - 低温淡水`() = assertGolden(10.0, 1013.25, 0.0, 11.25)

    @Test
    fun `B1 黄金值 4 组 - 低气压淡水`() = assertGolden(25.0, 990.0, 0.0, 8.07)

    @Test
    fun `B1 黄金值 4 组 - 海水盐度修正`() = assertGolden(25.0, 1013.25, 30.0, 6.41)

    @Test
    fun `DO_ref 暖水下调满分门槛且被 5_2-8_5 夹紧`() {
        assertEquals(7.4, DoPhysics.doRef(15.0, 7.4), 1e-9)      // 基准温度不调
        assertEquals(5.2, DoPhysics.doRef(40.0, 5.0), 1e-9)     // 下限夹紧（5.0−0.05·25=3.75 → 5.2）
        assertEquals(8.5, DoPhysics.doRef(0.0, 9.0), 1e-9)      // 上限夹紧
    }

    @Test
    fun `低溶氧 4 mgL 以下子分为 0`() {
        assertEquals(0.0, DoPhysics.oxygenScore(3.5, 7.0), 1e-9)
        assertEquals(1.0, DoPhysics.oxygenScore(9.0, 7.0), 1e-9)
    }
}
