package com.tidespot.feature.map

import com.tidespot.feature.map.stars.FavoriteStar
import com.tidespot.feature.map.stars.FavoriteStarLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OPT-V12-01（CHG-57）锁测试：星标 GeoJSON 构造与命中解析（纯函数，组装层盲区教训）。
 */
class FavoriteStarLayerTest {

    @Test
    fun `GeoJSON 包含全部收藏点且坐标顺序为 lon lat`() {
        val json = FavoriteStarLayer.geoJson(
            listOf(
                FavoriteStar(id = 7, name = "古港新地", lat = 22.5431, lon = 114.0579),
                FavoriteStar(id = 9, name = "B 点", lat = 30.5, lon = 120.0),
            ),
        )
        assertTrue(json.startsWith("{\"type\":\"FeatureCollection\",\"features\":["))
        assertTrue(json.contains("\"spotId\":7"))
        assertTrue(json.contains("\"spotId\":9"))
        assertTrue(json.contains("[114.057900,22.543100]"), "GeoJSON 坐标为 [lon, lat]")
        assertTrue(json.endsWith("]}"))
    }

    @Test
    fun `空集合产出空 FeatureCollection`() {
        assertEquals("{\"type\":\"FeatureCollection\",\"features\":[]}", FavoriteStarLayer.geoJson(emptyList()))
    }

    @Test
    fun `名称中的引号与反斜杠被转义`() {
        val json = FavoriteStarLayer.geoJson(
            listOf(FavoriteStar(id = 1, name = "a\"b\\c\nd", lat = 1.0, lon = 2.0)),
        )
        assertTrue(json.contains("\"name\":\"a\\\"b\\\\c\\nd\""), "JSON 字符串必须转义：$json")
    }

    @Test
    fun `命中解析支持 Double 与 Long 两种数值类型`() {
        assertEquals(12L, FavoriteStarLayer.starIdOf(12.0))
        assertEquals(12L, FavoriteStarLayer.starIdOf(12L))
        assertEquals(12L, FavoriteStarLayer.starIdOf(12.4f))
        assertNull(FavoriteStarLayer.starIdOf(null))
    }

    @Test
    fun `坐标固定六位小数（无科学计数法）`() {
        assertEquals("114.057900", FavoriteStarLayer.coordinate(114.0579))
        assertEquals("0.000001", FavoriteStarLayer.coordinate(1.0E-6))
    }
}