package com.tidespot.core.data.geo

import kotlin.test.Test
import kotlin.test.assertEquals

/** 简繁归一（SRS §5.8 坑 1 / B7b：山東省→山东省、青島市→青岛市、嶗山區→崂山区；同界面不得混排）。 */
class T2STest {

    @Test
    fun `省市区层级归一_B7b繁体黄金值`() {
        assertEquals("山东省", T2S.toSimplified("山東省"))
        assertEquals("青岛市", T2S.toSimplified("青島市"))
        assertEquals("崂山区", T2S.toSimplified("嶗山區"))
    }

    @Test
    fun `混排归一_太湖黄金值_省繁市简`() {
        assertEquals("江苏省苏州市吴中区", T2S.toSimplified("江蘇省苏州市吴中区"))
    }

    @Test
    fun `幂等_简体输入原样返回且二次归一不变`() {
        val s = "浙江省杭州市淳安县"
        assertEquals(s, T2S.toSimplified(s))
        assertEquals(T2S.toSimplified(s), T2S.toSimplified(T2S.toSimplified(s)))
    }

    @Test
    fun `海区名归一`() {
        assertEquals("菲律宾海", T2S.toSimplified("菲律賓海"))
        assertEquals("琼州海峡", T2S.toSimplified("瓊州海峽"))
    }

    @Test
    fun `空串与无映射字符不受影响`() {
        assertEquals("", T2S.toSimplified(""))
        assertEquals("千岛湖 123 ABC", T2S.toSimplified("千岛湖 123 ABC"))
    }
}
