package com.tidespot.feature.map.infobar

import com.tidespot.core.domain.model.NamedPlace
import com.tidespot.core.domain.model.ReverseGeoSource
import com.tidespot.core.domain.util.OpenLocationCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InfoBarStateTest {

    private fun place(
        displayName: String = "千岛湖",
        subLabel: String? = "淳安县, 浙江",
        plusCode: String? = "7PXXJ24Q+26",
    ) = NamedPlace(
        lat = 29.6, lon = 119.038,
        displayName = displayName, subLabel = subLabel,
        plusCode = plusCode, waterName = null,
        locality = null, city = "杭州市", admin1 = "浙江",
        source = ReverseGeoSource.RG_A,
    )

    @Test
    fun coordinate_formattedWithSixDecimals() {
        assertEquals("29.600000, 119.038000", formatCoordinate(29.6, 119.038))
        assertEquals("-22.500000, -43.100000", formatCoordinate(-22.5, -43.1))
        assertEquals("0.000000, 0.000000", formatCoordinate(0.0, 0.0))
    }

    @Test
    fun plusCode_goldenValue() { // SRS F-1.3 示例串（CHG-09⑤：编码统一走 domain 单一实现）
        assertEquals("7PXXJ24Q+26", OpenLocationCode.encode(29.605, 119.038))
        assertEquals("7PXXJ22Q+26", OpenLocationCode.encode(29.6, 119.038))
    }

    @Test
    fun plusCode_formatAndCharset() {
        val code = OpenLocationCode.encode(-22.5, -43.1)
        assertEquals(11, code.length)
        assertEquals('+', code[8])
        assertTrue(code.all { it == '+' || it in "23456789CFGHJMPQRVWX" })
    }

    @Test
    fun namedPlace_shownAs_displayName_subLabel() {
        val s = buildInfoBarState(InfoBarMode.CONFIRMED, 29.6, 119.038, place(), true)
        assertEquals("千岛湖 · 淳安县, 浙江", s.mainText)
        assertEquals("29.600000, 119.038000", s.subText)
    }

    @Test
    fun noName_fallsBackToCoordinates() {
        val s = buildInfoBarState(InfoBarMode.CONFIRMED, 29.6, 119.038, null, true)
        assertEquals("29.600000, 119.038000", s.mainText)
    }

    @Test
    fun nothing_fallsBackToUnnamed() {
        val s = buildInfoBarState(InfoBarMode.CONFIRMED, null, null, null, true)
        assertEquals("未命名水域", s.mainText)
    }

    @Test
    fun blankDisplayName_fallsBackToCoordinates() {
        val s = buildInfoBarState(InfoBarMode.CONFIRMED, 10.0, 20.0, place(displayName = "  "), true)
        assertEquals("10.000000, 20.000000", s.mainText)
    }

    @Test
    fun previewAppendsSwitchHint() {
        val s = buildInfoBarState(InfoBarMode.PREVIEW, 29.6, 119.038, place(), true)
        assertEquals("移动中，点确定切换", s.hint)
        val c = buildInfoBarState(InfoBarMode.CONFIRMED, 29.6, 119.038, place(), true)
        assertNull(c.hint)
    }

    @Test
    fun reverseGeoDisabled_showsCoordsPlusPlusCode() {
        val s = buildInfoBarState(InfoBarMode.CONFIRMED, 29.6, 119.038, null, reverseGeoEnabled = false)
        assertEquals("29.600000, 119.038000", s.mainText)
        assertEquals("7PXXJ22Q+26", s.subText)
    }

    @Test
    fun neverBlank_inEveryCombination() {
        for (mode in InfoBarMode.entries) {
            for (reverse in listOf(true, false)) {
                val s = buildInfoBarState(mode, null, null, null, reverse)
                assertTrue(s.mainText.isNotBlank(), "mainText 不得为空白 mode=$mode reverse=$reverse")
            }
        }
    }
}