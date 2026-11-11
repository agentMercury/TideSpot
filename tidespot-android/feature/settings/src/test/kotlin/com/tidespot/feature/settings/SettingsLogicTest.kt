package com.tidespot.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SettingsLogic 全覆盖锁（CHG-16⑱）：天数 coerce（接 DaySelectorLogic.coerceDaysRange）、
 * 单位换算语义、开关翻转、API Key 清洗、许可清单内容、隐私逐源披露、SettingsStore 键值往返。
 */
class SettingsLogicTest {

    // ── 详情天数：7|10 收敛（DaySelectorLogic.coerceDaysRange 单源） ──

    @Test
    fun `days_非法值收敛到7或10`() {
        assertEquals(7, SettingsLogic.coerceDays(6))
        assertEquals(7, SettingsLogic.coerceDays(7))
        assertEquals(7, SettingsLogic.coerceDays(9))
        assertEquals(10, SettingsLogic.coerceDays(10))
        assertEquals(10, SettingsLogic.coerceDays(11))
        assertEquals(7, SettingsLogic.coerceDays(0))
        assertEquals(7, SettingsLogic.coerceDays(-3))
    }

    @Test
    fun `withDetailDays_收敛不改原状态`() {
        val s = SettingsState()
        val s2 = SettingsLogic.withDetailDays(s, 9)
        assertEquals(7, s2.detailDays)
        assertEquals(7, s.detailDays) // 原状态不变（纯函数）
    }

    // ── 单位制语义 ──

    @Test
    fun `convertTemp_零度与沸点_英制换算`() {
        assertEquals(32.0, SettingsLogic.convertTemp(0.0, UnitSystem.IMPERIAL))
        assertEquals(212.0, SettingsLogic.convertTemp(100.0, UnitSystem.IMPERIAL))
        assertEquals(68.0, SettingsLogic.convertTemp(20.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun `convertTemp_SI原样_null透传禁0填充`() {
        assertEquals(20.0, SettingsLogic.convertTemp(20.0, UnitSystem.SI))
        assertNull(SettingsLogic.convertTemp(null, UnitSystem.IMPERIAL))
        assertNull(SettingsLogic.convertTemp(null, UnitSystem.SI))
    }

    @Test
    fun `convertWind_英制mph换算`() {
        assertEquals(10.0, SettingsLogic.convertWind(10.0, UnitSystem.SI))
        assertEquals(6.2, Math.round(SettingsLogic.convertWind(10.0, UnitSystem.IMPERIAL)!! * 10) / 10.0)
    }

    @Test
    fun `convertLength_英制ft换算`() {
        assertEquals(1.0, SettingsLogic.convertLength(1.0, UnitSystem.SI))
        assertEquals(3.3, Math.round(SettingsLogic.convertLength(1.0, UnitSystem.IMPERIAL)!! * 10) / 10.0)
        assertNull(SettingsLogic.convertLength(null, UnitSystem.IMPERIAL))
    }

    @Test
    fun `单位标签_SI与英制`() {
        assertEquals("°C", SettingsLogic.tempLabel(UnitSystem.SI)); assertEquals("°F", SettingsLogic.tempLabel(UnitSystem.IMPERIAL))
        assertEquals("km/h", SettingsLogic.windLabel(UnitSystem.SI)); assertEquals("mph", SettingsLogic.windLabel(UnitSystem.IMPERIAL))
        assertEquals("m", SettingsLogic.lengthLabel(UnitSystem.SI)); assertEquals("ft", SettingsLogic.lengthLabel(UnitSystem.IMPERIAL))
    }

    @Test
    fun `withUnitSystem_翻转不改原状态`() {
        val s = SettingsState()
        val s2 = SettingsLogic.withUnitSystem(s, UnitSystem.IMPERIAL)
        assertEquals(UnitSystem.IMPERIAL, s2.unitSystem)
        assertEquals(UnitSystem.SI, s.unitSystem)
    }

    // ── 开关翻转 ──

    @Test
    fun `开关翻转_预取与逆地理`() {
        val s = SettingsState()
        val s2 = SettingsLogic.withPrefetch(SettingsLogic.withReverseGeo(s, false), false)
        assertFalse(s2.prefetchEnabled)
        assertFalse(s2.reverseGeoEnabled)
        // 原状态不变
        assertTrue(s.prefetchEnabled); assertTrue(s.reverseGeoEnabled)
    }

    // ── API Key 占位清洗 ──

    @Test
    fun `apiKey_trim与空白落null`() {
        assertEquals("abc123", SettingsLogic.withApiKey(SettingsState(), "  abc123  ").apiKey)
        assertNull(SettingsLogic.withApiKey(SettingsState(), "   ").apiKey)
        assertNull(SettingsLogic.withApiKey(SettingsState(), null).apiKey)
    }

    // ── 许可清单（HANDOVER §8-2：Copernicus/NASA/NOAA/USGS 需署名） ──

    @Test
    fun `许可清单_覆盖契约九源`() {
        val names = SettingsLogic.LICENSES.map { it.name }
        assertEquals(8, SettingsLogic.LICENSES.size)
        listOf("Open-Meteo", "Copernicus Marine Service", "NASA", "NOAA", "USGS",
            "OpenStreetMap · MapLibre", "BigDataCloud", "Photon").forEach {
            assertTrue(names.contains(it), "许可清单缺 $it")
        }
        // 需署名源的 attribution 非空
        SettingsLogic.LICENSES.forEach { assertTrue(it.attribution.isNotBlank(), "${it.name} 署名文案缺失") }
    }

    // ── 隐私逐源披露 ──

    @Test
    fun `隐私披露_覆盖六源_逆地理与天气海洋水质`() {
        val lines = SettingsLogic.privacyLines(reverseGeoEnabled = true, prefetchEnabled = true)
        assertEquals(6, lines.size)
        // OPT-V11-13：文案已外移资源，锁「资源 id 序列」（顺序即渲染顺序）
        assertEquals(
            listOf(
                R.string.ts_privacy_rga_source,
                R.string.ts_privacy_rgb_source,
                R.string.ts_privacy_geocode_source,
                R.string.ts_privacy_weather_source,
                R.string.ts_privacy_marine_source,
                R.string.ts_privacy_wq_source,
            ),
            lines.map { it.sourceRes },
        )
        assertTrue(lines.all { it.sendsRes != 0 && it.toggleNoteRes != 0 })
    }

    @Test
    fun `隐私披露_逆地理关_两行注明不发请求`() {
        val lines = SettingsLogic.privacyLines(reverseGeoEnabled = false, prefetchEnabled = true)
        val geoLines = lines.filter {
            it.sourceRes == R.string.ts_privacy_rga_source || it.sourceRes == R.string.ts_privacy_rgb_source
        }
        assertEquals(2, geoLines.size)
        geoLines.forEach {
            assertEquals(R.string.ts_privacy_geo_off, it.toggleNoteRes, "逆地理关闭须注明不发请求")
        }
        SettingsLogic.privacyLines(reverseGeoEnabled = true, prefetchEnabled = true)
            .filter {
                it.sourceRes == R.string.ts_privacy_rga_source || it.sourceRes == R.string.ts_privacy_rgb_source
            }
            .forEach { assertEquals(R.string.ts_privacy_geo_on, it.toggleNoteRes) }
    }

    @Test
    fun `隐私披露_预取关_天气行注明仅详情拉取`() {
        val off = SettingsLogic.privacyLines(true, prefetchEnabled = false)
            .first { it.sourceRes == R.string.ts_privacy_weather_source }
        assertEquals(R.string.ts_privacy_prefetch_off, off.toggleNoteRes)
        val on = SettingsLogic.privacyLines(true, prefetchEnabled = true)
            .first { it.sourceRes == R.string.ts_privacy_weather_source }
        assertEquals(R.string.ts_privacy_prefetch_on, on.toggleNoteRes)
    }

    // ── SettingsStore 接口（JVM 假实现往返） ──

    @Test
    fun `settingsStore_键值往返`() {
        val store = InMemorySettingsStore()
        store.writeString(SettingsKeys.UNIT_SYSTEM, "IMPERIAL")
        store.writeBoolean(SettingsKeys.PREFETCH_ENABLED, false)
        assertEquals("IMPERIAL", store.readString(SettingsKeys.UNIT_SYSTEM))
        assertFalse(store.readBoolean(SettingsKeys.PREFETCH_ENABLED, true))
        assertTrue(store.readBoolean(SettingsKeys.REVERSE_GEO_ENABLED, true)) // default 生效
        store.writeString(SettingsKeys.API_KEY, null)
        assertNull(store.readString(SettingsKeys.API_KEY))
    }

    @Test
    fun `settingsKeys_六个键名非空`() {
        listOf(SettingsKeys.UNIT_SYSTEM, SettingsKeys.DETAIL_DAYS, SettingsKeys.PREFETCH_ENABLED,
            SettingsKeys.REVERSE_GEO_ENABLED, SettingsKeys.API_KEY, SettingsKeys.MAP_STYLE)
            .forEach { assertTrue(it.isNotBlank()) }
    }

    // ── UX-19：地图样式三选 ──

    @Test
    fun `withMapStyle_三选且不改原状态`() {
        val base = SettingsState()
        assertEquals(MapStyle.VOYAGER, base.mapStyle, "默认必须是彩色 Voyager（D8）")
        val dark = SettingsLogic.withMapStyle(base, MapStyle.DARK_MATTER)
        assertEquals(MapStyle.DARK_MATTER, dark.mapStyle)
        assertEquals(MapStyle.VOYAGER, base.mapStyle)
        assertEquals(MapStyle.VOYAGER, SettingsLogic.withMapStyle(base, MapStyle.VOYAGER).mapStyle)
        assertEquals(MapStyle.POSITRON, SettingsLogic.withMapStyle(base, MapStyle.POSITRON).mapStyle)
    }

    @Test
    fun `mapStyleUrl_三样式URL各不相同且均为CARTO`() {
        val urls = MapStyle.entries.map { it.url }
        assertEquals(3, urls.toSet().size)
        assertTrue(urls.all { it.startsWith("https://basemaps.cartocdn.com/gl/") })
    }
}

/** 测试用内存键值实现（:app 批次 3 以 SharedPreferences 实现同一接口）。 */
private class InMemorySettingsStore : SettingsStore {
    val strings = HashMap<String, String?>()
    val booleans = HashMap<String, Boolean>()
    override fun readString(key: String): String? = strings[key]
    override fun writeString(key: String, value: String?) { strings[key] = value }
    override fun readBoolean(key: String, default: Boolean): Boolean = booleans[key] ?: default
    override fun writeBoolean(key: String, value: Boolean) { booleans[key] = value }
}
