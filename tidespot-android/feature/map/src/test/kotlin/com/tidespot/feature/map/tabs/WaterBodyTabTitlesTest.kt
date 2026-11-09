package com.tidespot.feature.map.tabs

import com.tidespot.core.domain.model.WaterBodyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaterBodyTabTitlesTest {

    @Test
    fun inlandWater_usesDowngradedTitles() { // F-1.6 千岛湖
        val t = WaterBodyTabTitles.titlesFor(WaterBodyType.INLAND_WATER)
        assertEquals("湖面风与水温", t.waveTab)
        assertEquals("水位（无潮汐）", t.tideTab)
        assertEquals("天气", t.weatherTab)
        assertNull(t.notice)
    }

    @Test
    fun oceanAndNearshore_useDefaultFiveTabs() { // F-1.6 青岛防波堤
        for (type in listOf(WaterBodyType.OCEAN, WaterBodyType.NEARSHORE)) {
            val t = WaterBodyTabTitles.titlesFor(type)
            assertEquals("风浪", t.waveTab)
            assertEquals("天气", t.weatherTab)
            assertEquals("钓鱼指数", t.scoreTab)
            assertEquals("水质", t.waterQualityTab)
            assertEquals("潮流", t.tideTab)
            assertNull(t.notice)
        }
    }

    @Test
    fun land_marksEstimatedAndUnavailable() { // F-1.7
        val t = WaterBodyTabTitles.titlesFor(WaterBodyType.LAND)
        assertTrue(t.waveTab.contains("估算"))
        assertTrue(t.waterQualityTab.contains("估算"))
        assertTrue(t.tideTab.contains("不可用"))
        assertNotNull(t.notice)
        assertTrue(t.notice!!.isNotBlank())
    }
}