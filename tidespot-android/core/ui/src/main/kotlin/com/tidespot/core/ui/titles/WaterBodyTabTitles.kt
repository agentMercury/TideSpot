package com.tidespot.core.ui.titles

import com.tidespot.core.domain.model.WaterBodyType
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.strings.ZhStrings

/**
 * Tab 标题映射【canonical 实现】（M4 批次 B 自 :feature:map 迁入，CONTRACT「M4 子契约」：
 * WaterBodyTabTitles canonical 实现迁至 :core:ui；:feature:map 原对象一行委托，测试保持通过）。
 *
 * 顺序对应 §8.1 DetailScreen：风浪 | 天气 | 钓鱼指数 | 水质 | 潮流。
 * M4 详情页（:feature:detail）与地图信息条（:feature:map）共用本实现；内陆点按 §6.9 切标题、不隐藏 Tab（F-3.1/F-1.6）。
 *
 * OPT-V11-13：文案改由 [TideSpotStrings] 注入（Composable 传 `localTideSpotStrings()`，
 * JVM 单测/默认传 [ZhStrings]），本对象内**零字面量**。
 */
data class WaterTabTitles(
    val waveTab: String,
    val weatherTab: String,
    val scoreTab: String,
    val waterQualityTab: String,
    val tideTab: String,
    /** 顶部估算/不可用提示位（§8 / F-1.7），可为 null。 */
    val notice: String? = null,
)

object WaterBodyTabTitles {

    /**
     * 水体类型 → 五维标题（OCEAN/NEARSHORE 全开；INLAND 降级；LAND 标注；UNKNOWN 待判定提示）。
     *
     * @param strings 文案来源，默认中文（[ZhStrings]）保持既有行为零漂移。
     */
    fun titlesFor(type: WaterBodyType, strings: TideSpotStrings = ZhStrings): WaterTabTitles = when (type) {
        WaterBodyType.INLAND_WATER -> WaterTabTitles(
            waveTab = strings.tabWaveInland,
            weatherTab = strings.tabWeather,
            scoreTab = strings.tabScore,
            waterQualityTab = strings.tabWaterQuality,
            tideTab = strings.tabTideInland,
        )
        WaterBodyType.OCEAN, WaterBodyType.NEARSHORE -> WaterTabTitles(
            waveTab = strings.tabWave,
            weatherTab = strings.tabWeather,
            scoreTab = strings.tabScore,
            waterQualityTab = strings.tabWaterQuality,
            tideTab = strings.tabTide,
        )
        WaterBodyType.UNKNOWN -> WaterTabTitles(
            waveTab = strings.tabWave,
            weatherTab = strings.tabWeather,
            scoreTab = strings.tabScore,
            waterQualityTab = strings.tabWaterQuality,
            tideTab = strings.tabTide,
            notice = strings.noticeUnknown,
        )
        WaterBodyType.LAND -> WaterTabTitles(
            waveTab = strings.tabWaveLand,
            weatherTab = strings.tabWeather,
            scoreTab = strings.tabScore,
            waterQualityTab = strings.tabWaterQualityLand,
            tideTab = strings.tabTideLand,
            notice = strings.noticeLand,
        )
    }

    /**
     * DetailScreen Tab 顺序（UX-22 / CHG-21：钓鱼指数 → 潮流(水位) → 天气 → 水质 → 风浪）→ 标题数组
     * （CHG-13/D01 锁）。**恒为 5 项**且与 [DetailTab 顺序] 一一对应；禁止调用方手工再组数组
     * （D01 根因即 6 项错位 + `.also` 无效自赋值，潮流 Tab 被水质标题覆盖）。UI 组装层唯一取标题入口。
     */
    fun titlesInTabOrder(titles: WaterTabTitles): List<String> = listOf(
        titles.scoreTab,
        titles.tideTab,
        titles.weatherTab,
        titles.waterQualityTab,
        titles.waveTab,
    )
}