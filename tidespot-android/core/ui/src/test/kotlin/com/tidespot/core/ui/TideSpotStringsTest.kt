package com.tidespot.core.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tidespot.core.ui.badge.Kind
import com.tidespot.core.ui.strings.ResStrings
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.strings.ZhStrings
import com.tidespot.core.ui.titles.WaterBodyTabTitles
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.lang.reflect.Method
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FO-01 / OPT-V11-13 文案层锁：
 * ① **零行为漂移**——[ZhStrings]（中文默认实现）与 `res/values/strings.xml` 逐键相等；
 * ② D11 免责恒 5 条且第 5 条为「潮汐校正」；
 * ③ Tab 标题单源委托（CHG-13/D01 锁延续）；
 * ④ 徽标标签仍取资源（[Kind.labelRes]），不靠颜色单独编码（N-7）。
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "zh-rCN")
class TideSpotStringsTest {

    private val zh = ZhStrings
    private val resStrings = ResStrings(
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().resources,
    )

    /** 接口的全部 `getXxx(): String` 属性 getter。 */
    private fun stringGetters(): List<Method> = TideSpotStrings::class.java.methods.filter {
        it.parameterCount == 0 && it.returnType == String::class.java &&
            it.name.startsWith("get") && it.name != "getClass"
    }

    @Test
    fun zhStrings_matchesChineseResources_keyByKey() {
        val getters = stringGetters()
        assertTrue(getters.size > 50, "文案接口属性数量异常：${getters.size}")
        getters.forEach { getter ->
            val expected = getter.invoke(zh)
            val actual = getter.invoke(resStrings)
            assertEquals(expected, actual, "键 ${getter.name} 在 ZhStrings 与 values/strings.xml 不一致")
        }
    }

    @Test
    fun disclaimer_hasExactlyFiveItems() {
        assertEquals(5, zh.disclaimerItems().size)
    }

    @Test
    fun disclaimer_fifthItem_isTideCorrection() {
        val fifth = zh.disclaimerItems()[4]
        assertTrue(fifth.contains("校潮"), "第 5 条必须为潮汐校正（实际：$fifth）")
        assertTrue(fifth.contains("非官方潮汐表"))
    }

    @Test
    fun disclaimer_fourLegacyItems_zeroDrift() {
        // 原 DetailScreen 4 条免责逐字保真（迁移零漂移）
        assertEquals(
            "潮位与潮流来自 ~8 km 全球模型，官方声明不适用于近岸航行；本 App 不替代航路指南与官方潮汐表。",
            zh.disclaimerWaveTide,
        )
        assertEquals(
            "雷暴、大风、大浪判定仅供参考。水上安全以当地预警为准，切勿在雷电、台风、大潮汛期间出钓。",
            zh.disclaimerWeather,
        )
        assertEquals("指数基于气象与天文因子的经验加权估算，不构成渔获保证。", zh.disclaimerScore)
        assertEquals(
            "中国境内多数点位水质为模型估算值，非实测，不作为用水、饮用或接触安全依据。",
            zh.disclaimerWaterQuality,
        )
    }

    @Test
    fun tabTitles_valuesLockedToLegacyLiterals() {
        assertEquals("风浪", zh.tabWave)
        assertEquals("天气", zh.tabWeather)
        assertEquals("钓鱼指数", zh.tabScore)
        assertEquals("水质", zh.tabWaterQuality)
        assertEquals("潮流", zh.tabTide)
    }

    @Test
    fun tabTitles_singleSourceFromStringsImplementation() {
        val titles = WaterBodyTabTitles.titlesFor(
            com.tidespot.core.domain.model.WaterBodyType.INLAND_WATER,
            zh,
        )
        assertEquals(zh.tabWaveInland, titles.waveTab)
        assertEquals(zh.tabWeather, titles.weatherTab)
        assertEquals(zh.tabScore, titles.scoreTab)
        assertEquals(zh.tabWaterQuality, titles.waterQualityTab)
        assertEquals(zh.tabTideInland, titles.tideTab)
    }

    @Test
    fun badgeLabels_areResourceBackedAndMatchKinds() {
        // Kind 只持有资源 id（不存字面量）；值由资源给出，且 6 档互异
        val labels = Kind.entries.map { resStrings.badgeLabel(it) }
        assertEquals(6, labels.distinct().size)
        assertEquals("实测", labels[Kind.MEASURED.ordinal])
        assertEquals("低置信", labels[Kind.LOW_CONFIDENCE.ordinal])
    }
}

/** 测试辅助：按 Kind 取徽标文案（Tag 与资源 id 一一对应）。 */
private fun TideSpotStrings.badgeLabel(kind: Kind): String = when (kind) {
    Kind.MEASURED -> badgeMeasured
    Kind.SATELLITE -> badgeSatellite
    Kind.MODEL -> badgeModel
    Kind.ESTIMATED -> badgeEstimated
    Kind.UNCALIBRATED -> badgeUncalibrated
    Kind.LOW_CONFIDENCE -> badgeLowConfidence
}