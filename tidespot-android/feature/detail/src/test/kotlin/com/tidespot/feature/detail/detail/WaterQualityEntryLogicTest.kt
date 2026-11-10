package com.tidespot.feature.detail.detail

import com.tidespot.core.data.repo.WaterQualityObservationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CHG-27（P0-2 / OPT-V11-02）锁测试：[WaterQualityEntryLogic] 表单纯逻辑。
 *
 * 口径（OPT_REQUIREMENTS_V11 §3）：
 * - 三项可选填（空 = 不录入）；非数字/超范围拒绝；
 * - 范围：水温 −5~45 ℃、DO 0~20 mg/L、浊度 0~1000 NTU；
 * - 录入时刻 ±24h 内（事后补记边界）。
 */
class WaterQualityEntryLogicTest {

    @Test
    fun `空输入_不录入`() {
        assertNull(WaterQualityEntryLogic.parse(WaterQualityObservationKind.WATER_TEMP, ""))
        assertNull(WaterQualityEntryLogic.parse(WaterQualityObservationKind.DO, "   "))
    }

    @Test
    fun `合法数值_解析成功`() {
        val temp = WaterQualityEntryLogic.parse(WaterQualityObservationKind.WATER_TEMP, "22.0")
        assertEquals(22.0, (temp as WaterQualityEntryLogic.ValueResult.Ok).value)
        val doOk = WaterQualityEntryLogic.parse(WaterQualityObservationKind.DO, "8.1")
        assertEquals(8.1, (doOk as WaterQualityEntryLogic.ValueResult.Ok).value)
        val turb = WaterQualityEntryLogic.parse(WaterQualityObservationKind.TURBIDITY, "35")
        assertEquals(35.0, (turb as WaterQualityEntryLogic.ValueResult.Ok).value)
    }

    @Test
    fun `非数字_错误码`() {
        val r = WaterQualityEntryLogic.parse(WaterQualityObservationKind.WATER_TEMP, "abc")
        assertEquals(
            WaterQualityEntryLogic.Error.NOT_A_NUMBER,
            (r as WaterQualityEntryLogic.ValueResult.Err).error,
        )
    }

    @Test
    fun `超范围_拒绝_边界值接受`() {
        fun err(kind: WaterQualityObservationKind, text: String) =
            (WaterQualityEntryLogic.parse(kind, text) as WaterQualityEntryLogic.ValueResult.Err).error

        assertEquals(WaterQualityEntryLogic.Error.OUT_OF_RANGE, err(WaterQualityObservationKind.WATER_TEMP, "50"))
        assertEquals(WaterQualityEntryLogic.Error.OUT_OF_RANGE, err(WaterQualityObservationKind.WATER_TEMP, "-6"))
        assertEquals(WaterQualityEntryLogic.Error.OUT_OF_RANGE, err(WaterQualityObservationKind.DO, "20.1"))
        assertEquals(WaterQualityEntryLogic.Error.OUT_OF_RANGE, err(WaterQualityObservationKind.TURBIDITY, "1001"))

        // 边界值本身合法（闭区间）
        assertTrue(WaterQualityEntryLogic.parse(WaterQualityObservationKind.WATER_TEMP, "45") is WaterQualityEntryLogic.ValueResult.Ok)
        assertTrue(WaterQualityEntryLogic.parse(WaterQualityObservationKind.WATER_TEMP, "-5") is WaterQualityEntryLogic.ValueResult.Ok)
        assertTrue(WaterQualityEntryLogic.parse(WaterQualityObservationKind.DO, "20") is WaterQualityEntryLogic.ValueResult.Ok)
        assertTrue(WaterQualityEntryLogic.parse(WaterQualityObservationKind.TURBIDITY, "1000") is WaterQualityEntryLogic.ValueResult.Ok)
    }

    @Test
    fun `录入时刻_pm24h窗口`() {
        val now = 1_700_000_000L
        assertTrue(WaterQualityEntryLogic.isTimeWithinWindow(now, now))
        assertTrue(WaterQualityEntryLogic.isTimeWithinWindow(now + 24 * 3600L, now), "恰好 +24h 允许")
        assertTrue(WaterQualityEntryLogic.isTimeWithinWindow(now - 24 * 3600L, now), "恰好 -24h 允许")
        assertTrue(!WaterQualityEntryLogic.isTimeWithinWindow(now + 24 * 3600L + 1, now), "超 24h 拒绝")
        assertTrue(!WaterQualityEntryLogic.isTimeWithinWindow(now - 24 * 3600L - 1, now))
    }

    @Test
    fun `单位与名称口径`() {
        assertEquals("℃", WaterQualityEntryLogic.unit(WaterQualityObservationKind.WATER_TEMP))
        assertEquals("mg/L", WaterQualityEntryLogic.unit(WaterQualityObservationKind.DO))
        assertEquals("NTU", WaterQualityEntryLogic.unit(WaterQualityObservationKind.TURBIDITY))
        assertEquals("水温", WaterQualityEntryLogic.label(WaterQualityObservationKind.WATER_TEMP))
        assertEquals("溶解氧", WaterQualityEntryLogic.label(WaterQualityObservationKind.DO))
        assertEquals("浊度", WaterQualityEntryLogic.label(WaterQualityObservationKind.TURBIDITY))
    }
}