package com.tidespot.core.domain

import com.tidespot.core.domain.util.OpenLocationCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OLC 单一实现官方向量对拍（CHG-09⑤ / NEW-R3）。
 *
 * 向量来源与验证过程（报告留痕）：
 * - B7b 两点（SRS 附录，工程既有黄金值）；
 * - (31.2, 120.3) = `8Q326822+22`（CHG-09 任务书指定）；
 * - 官方 README 苏黎世示例 encode(47.365547, 8.566542)：本机脚本（Node，×1e8 截断法）+
 *   手算逐位推导双验证输出 `8FVC9H88+6J`（8FVC = 苏黎世 4 位区域码，与官方 README 一致）；
 * - 边界：末位半格点偏移（floor 语义防回归）、经度 ±360 归一化等价。
 * 全部向量先经独立脚本验证后固化为断言，无任何硬凑。
 */
class OpenLocationCodeTest {

    @Test
    fun `B7b黄金值_千岛湖湖心`() {
        assertEquals("7PXWHX22+22", OpenLocationCode.encode(29.55, 118.95))
    }

    @Test
    fun `B7b黄金值_岸边`() {
        assertEquals("7PXXJ24Q+26", OpenLocationCode.encode(29.605, 119.038))
    }

    @Test
    fun `官方向量_31点2_120点3`() {
        assertEquals("8Q326822+22", OpenLocationCode.encode(31.2, 120.3))
    }

    @Test
    fun `官方README示例_苏黎世`() {
        assertEquals("8FVC9H88+6J", OpenLocationCode.encode(47.365547, 8.566542))
    }

    @Test
    fun `信息条既有向量_29点6_119点038`() {
        // 原 feature:map encodePlusCode 的固化断言（现收敛到 domain 实现，值不变）
        assertEquals("7PXXJ22Q+26", OpenLocationCode.encode(29.6, 119.038))
    }

    @Test
    fun `严格截断floor_末位半格点偏移_保留下格`() {
        // 29.5500625 = 29.55 + 0.0000625（末位 0.000125° 的半格）：floor 编入下格 [29.55, 29.550125)
        // → 与 29.55 同码；round 版会取上格（末位 '2'→'3'），此断言即 NEW-R3 错码根因防回归
        assertEquals("7PXWHX22+22", OpenLocationCode.encode(29.5500625, 118.95))
    }

    @Test
    fun `负经度归一化_±360度等价`() {
        // 119.038 - 360 = -240.962：同一经线，编码必须一致
        assertEquals("7PXXJ24Q+26", OpenLocationCode.encode(29.605, -240.962))
        // -241.0 ≡ 119.0
        assertEquals(OpenLocationCode.encode(29.605, 119.0), OpenLocationCode.encode(29.605, -241.0))
        // 180.1 ≡ -179.9（跨 ±180 边界）
        assertEquals(
            OpenLocationCode.encode(31.2, -179.9),
            OpenLocationCode.encode(31.2, 180.1),
        )
    }

    @Test
    fun `格式_固定10位且加号在第8位后_码表内字符`() {
        for (v in listOf(
            29.55 to 118.95, 29.605 to 119.038, 31.2 to 120.3,
            47.365547 to 8.566542, -22.5438971 to -43.1098712, 0.0 to 0.0,
        )) {
            val code = OpenLocationCode.encode(v.first, v.second)
            assertEquals(11, code.length)
            assertEquals('+', code[8])
            assertTrue(code.take(8).none { it == '+' }, "加号只出现一次")
            assertTrue(code.all { it == '+' || it in "23456789CFGHJMPQRVWX" })
        }
    }
}
