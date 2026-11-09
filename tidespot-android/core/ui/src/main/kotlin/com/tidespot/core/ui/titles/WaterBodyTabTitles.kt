package com.tidespot.core.ui.titles

import com.tidespot.core.domain.model.WaterBodyType
import com.tidespot.core.ui.strings.TideSpotStrings

/**
 * Tab 标题映射【canonical 实现】（M4 批次 B 自 :feature:map 迁入，CONTRACT「M4 子契约」：
 * WaterBodyTabTitles canonical 实现迁至 :core:ui；:feature:map 原对象一行委托，测试保持通过）。
 *
 * 顺序对应 §8.1 DetailScreen：风浪 | 天气 | 钓鱼指数 | 水质 | 潮流。
 * M4 详情页（:feature:detail）与地图信息条（:feature:map）共用本实现；内陆点按 §6.9 切标题、不隐藏 Tab（F-3.1/F-1.6）。
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

    // OCEAN / NEARSHORE：默认 5 维标题（全开）
    const val WAVE_DEFAULT = TideSpotStrings.TAB_WAVE
    const val WEATHER_DEFAULT = TideSpotStrings.TAB_WEATHER
    const val SCORE_DEFAULT = TideSpotStrings.TAB_SCORE
    const val WATER_QUALITY_DEFAULT = TideSpotStrings.TAB_WATER_QUALITY
    const val TIDE_DEFAULT = TideSpotStrings.TAB_TIDE

    // F-1.6 内陆水体降级：风浪 Tab 标题变「湖面风与水温」、潮流 Tab 标注「无潮汐」
    const val WAVE_INLAND = TideSpotStrings.TAB_WAVE_INLAND
    const val TIDE_INLAND = TideSpotStrings.TAB_TIDE_INLAND

    // F-1.7 陆地点：水质/潮流显式标注估算或不可用
    const val WAVE_LAND = TideSpotStrings.TAB_WAVE_LAND
    const val WATER_QUALITY_LAND = TideSpotStrings.TAB_WATER_QUALITY_LAND
    const val TIDE_LAND = TideSpotStrings.TAB_TIDE_LAND
    const val NOTICE_LAND = TideSpotStrings.NOTICE_LAND

    // CHG-09②（产品口径 C1）：探针失败期水体判 UNKNOWN + 「待判定」徽标（OSTIA 复测通过后自动恢复）
    const val NOTICE_UNKNOWN = TideSpotStrings.NOTICE_UNKNOWN

    fun titlesFor(type: WaterBodyType): WaterTabTitles = when (type) {
        WaterBodyType.INLAND_WATER -> WaterTabTitles(
            waveTab = WAVE_INLAND,
            weatherTab = WEATHER_DEFAULT,
            scoreTab = SCORE_DEFAULT,
            waterQualityTab = WATER_QUALITY_DEFAULT,
            tideTab = TIDE_INLAND,
        )
        WaterBodyType.OCEAN, WaterBodyType.NEARSHORE -> WaterTabTitles(
            waveTab = WAVE_DEFAULT,
            weatherTab = WEATHER_DEFAULT,
            scoreTab = SCORE_DEFAULT,
            waterQualityTab = WATER_QUALITY_DEFAULT,
            tideTab = TIDE_DEFAULT,
        )
        WaterBodyType.UNKNOWN -> WaterTabTitles(
            waveTab = WAVE_DEFAULT,
            weatherTab = WEATHER_DEFAULT,
            scoreTab = SCORE_DEFAULT,
            waterQualityTab = WATER_QUALITY_DEFAULT,
            tideTab = TIDE_DEFAULT,
            notice = NOTICE_UNKNOWN,
        )
        WaterBodyType.LAND -> WaterTabTitles(
            waveTab = WAVE_LAND,
            weatherTab = WEATHER_DEFAULT,
            scoreTab = SCORE_DEFAULT,
            waterQualityTab = WATER_QUALITY_LAND,
            tideTab = TIDE_LAND,
            notice = NOTICE_LAND,
        )
    }

    /**
     * DetailScreen Tab 顺序（§8.1：风浪|天气|钓鱼指数|水质|潮流）→ 标题数组（CHG-13/D01 锁）。
     * **恒为 5 项**且与 [DetailTab 顺序] 一一对应；禁止调用方手工再组数组（D01 根因即 6 项错位 +
     * `.also` 无效自赋值，潮流 Tab 被水质标题覆盖）。UI 组装层唯一取标题入口。
     */
    fun titlesInTabOrder(titles: WaterTabTitles): List<String> = listOf(
        titles.waveTab,
        titles.weatherTab,
        titles.scoreTab,
        titles.waterQualityTab,
        titles.tideTab,
    )
}

