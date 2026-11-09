package com.tidespot.core.ui

import com.tidespot.core.ui.badge.Kind
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.titles.WaterBodyTabTitles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FO-01 文案层锁（CHG-16⑳）：核心用户可见串唯一入口；既有常量/标签委托引用，零行为漂移。
 */
class TideSpotStringsTest {

    // ── D11：免责恒 5 条，第 5 条为「潮汐校正」 ──

    @Test
    fun disclaimer_hasExactlyFiveItems() {
        assertEquals(5, TideSpotStrings.DISCLAIMER_ITEMS.size)
    }

    @Test
    fun disclaimer_fifthItem_isTideCorrection() {
        val fifth = TideSpotStrings.DISCLAIMER_ITEMS[4]
        assertTrue(fifth.contains("校潮"), "第 5 条必须为潮汐校正（实际：$fifth）")
        assertTrue(fifth.contains("非官方潮汐表"))
    }

    @Test
    fun disclaimer_fourLegacyItems_zeroDrift() {
        // 原 DetailScreen 4 条免责逐字保真（迁移零漂移）
        assertEquals(
            "潮位与潮流来自 ~8 km 全球模型，官方声明不适用于近岸航行；本 App 不替代航路指南与官方潮汐表。",
            TideSpotStrings.DISCLAIMER_WAVE_TIDE,
        )
        assertEquals(
            "雷暴、大风、大浪判定仅供参考。水上安全以当地预警为准，切勿在雷电、台风、大潮汛期间出钓。",
            TideSpotStrings.DISCLAIMER_WEATHER,
        )
        assertEquals("指数基于气象与天文因子的经验加权估算，不构成渔获保证。", TideSpotStrings.DISCLAIMER_SCORE)
        assertEquals(
            "中国境内多数点位水质为模型估算值，非实测，不作为用水、饮用或接触安全依据。",
            TideSpotStrings.DISCLAIMER_WATER_QUALITY,
        )
    }

    // ── Tab 标题：WaterBodyTabTitles 串源委托（CHG-13/D01 锁延续） ──

    @Test
    fun tabTitles_singleSource_delegatesToTideSpotStrings() {
        assertEquals(TideSpotStrings.TAB_WAVE, WaterBodyTabTitles.WAVE_DEFAULT)
        assertEquals(TideSpotStrings.TAB_WEATHER, WaterBodyTabTitles.WEATHER_DEFAULT)
        assertEquals(TideSpotStrings.TAB_SCORE, WaterBodyTabTitles.SCORE_DEFAULT)
        assertEquals(TideSpotStrings.TAB_WATER_QUALITY, WaterBodyTabTitles.WATER_QUALITY_DEFAULT)
        assertEquals(TideSpotStrings.TAB_TIDE, WaterBodyTabTitles.TIDE_DEFAULT)
        assertEquals(TideSpotStrings.TAB_WAVE_INLAND, WaterBodyTabTitles.WAVE_INLAND)
        assertEquals(TideSpotStrings.TAB_TIDE_INLAND, WaterBodyTabTitles.TIDE_INLAND)
        assertEquals(TideSpotStrings.TAB_WAVE_LAND, WaterBodyTabTitles.WAVE_LAND)
        assertEquals(TideSpotStrings.TAB_WATER_QUALITY_LAND, WaterBodyTabTitles.WATER_QUALITY_LAND)
        assertEquals(TideSpotStrings.TAB_TIDE_LAND, WaterBodyTabTitles.TIDE_LAND)
    }

    @Test
    fun tabTitleValues_lockedToLegacyLiterals() {
        // 迁移零漂移：串值与 M4 验收文案逐字一致
        assertEquals("风浪", TideSpotStrings.TAB_WAVE)
        assertEquals("天气", TideSpotStrings.TAB_WEATHER)
        assertEquals("钓鱼指数", TideSpotStrings.TAB_SCORE)
        assertEquals("水质", TideSpotStrings.TAB_WATER_QUALITY)
        assertEquals("潮流", TideSpotStrings.TAB_TIDE)
    }

    // ── 徽标标签：Kind 串源委托 ──

    @Test
    fun badgeLabels_delegatesToTideSpotStrings_andMatchKinds() {
        assertEquals(TideSpotStrings.BADGE_MEASURED, Kind.MEASURED.label)
        assertEquals(TideSpotStrings.BADGE_SATELLITE, Kind.SATELLITE.label)
        assertEquals(TideSpotStrings.BADGE_MODEL, Kind.MODEL.label)
        assertEquals(TideSpotStrings.BADGE_ESTIMATED, Kind.ESTIMATED.label)
        assertEquals(TideSpotStrings.BADGE_UNCALIBRATED, Kind.UNCALIBRATED.label)
        assertEquals(TideSpotStrings.BADGE_LOW_CONFIDENCE, Kind.LOW_CONFIDENCE.label)
        assertEquals("实测", Kind.MEASURED.label)
        assertEquals("低置信", Kind.LOW_CONFIDENCE.label)
    }
}
