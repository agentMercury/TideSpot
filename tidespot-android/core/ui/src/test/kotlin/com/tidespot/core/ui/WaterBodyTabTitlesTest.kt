package com.tidespot.core.ui

import com.tidespot.core.domain.model.WaterBodyType
import com.tidespot.core.ui.titles.WaterBodyTabTitles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** canonical 实现迁入 :core:ui 后的等价回归（与 :feature:map 委托测试同口径，F-1.6/F-1.7/CHG-09②）。 */
class WaterBodyTabTitlesTest {

    @Test
    fun inlandWater_usesDowngradedTitles() {
        val t = WaterBodyTabTitles.titlesFor(WaterBodyType.INLAND_WATER)
        assertEquals("湖面风与水温", t.waveTab)
        assertEquals("水位（无潮汐）", t.tideTab)
        assertEquals("天气", t.weatherTab)
        assertNull(t.notice)
    }

    @Test
    fun oceanAndNearshore_useDefaultFiveTabs() {
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
    fun land_marksEstimatedAndUnavailable() {
        val t = WaterBodyTabTitles.titlesFor(WaterBodyType.LAND)
        assertTrue(t.waveTab.contains("估算"))
        assertTrue(t.waterQualityTab.contains("估算"))
        assertTrue(t.tideTab.contains("不可用"))
        assertNotNull(t.notice)
        assertTrue(t.notice!!.isNotBlank())
    }

    @Test
    fun unknown_showsPendingNotice() {
        val t = WaterBodyTabTitles.titlesFor(WaterBodyType.UNKNOWN)
        assertNotNull(t.notice)
        assertTrue(t.notice!!.contains("待判定"))
    }

    /**
     * CHG-13/D01 锁 + UX-22（CHG-21）顺序锁：Tab 标题数组恒 5 项、**顺序固定**
     * （钓鱼指数 → 潮流 → 天气 → 水质 → 风浪）、互异、钓鱼指数首位、潮流次位。
     */
    @Test
    fun titlesInTabOrder_alwaysFiveDistinct_scoreFirstTideSecond() {
        for (type in WaterBodyType.entries) {
            val titles = WaterBodyTabTitles.titlesFor(type)
            val ordered = WaterBodyTabTitles.titlesInTabOrder(titles)
            assertEquals(5, ordered.size, "$type 标题数组必须 5 项")
            assertEquals(
                listOf(titles.scoreTab, titles.tideTab, titles.weatherTab, titles.waterQualityTab, titles.waveTab),
                ordered,
            )
            assertEquals(titles.scoreTab, ordered[0], "$type 第 1 个 Tab 必须是钓鱼指数标题")
            assertEquals(titles.tideTab, ordered[1], "$type 第 2 个 Tab 必须是潮流标题")
            assertEquals(titles.waveTab, ordered[4], "$type 第 5 个 Tab 必须是风浪标题")
            assertEquals(5, ordered.toSet().size, "$type 五个 Tab 标题不得重复（D01：水质覆盖潮流）")
        }
        // 内陆矩阵抽查：第 2 Tab = 「水位（无潮汐）」、第 5 Tab = 「湖面风与水温」（F-1.6 降级文案可达）
        val inland = WaterBodyTabTitles.titlesInTabOrder(WaterBodyTabTitles.titlesFor(WaterBodyType.INLAND_WATER))
        assertEquals("水位（无潮汐）", inland[1])
        assertEquals("湖面风与水温", inland[4])
    }
}
