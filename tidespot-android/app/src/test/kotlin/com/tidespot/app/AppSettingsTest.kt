package com.tidespot.app

import com.tidespot.feature.settings.SettingsKeys
import com.tidespot.feature.settings.SettingsState
import com.tidespot.feature.settings.SettingsStore
import com.tidespot.feature.settings.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 设置键值 ↔ SettingsState 映射锁测试（M6 批次 3）：纯函数 + 内存 fake store（JVM）。
 * SharedPreferences 实现仅键值读写，语义默认值/收敛全部在此层锁死。
 */
class AppSettingsTest {

    /** 内存 fake（与 SettingsStore 契约同形；锁测试不依赖 Android 框架）。 */
    private class FakeStore : SettingsStore {
        val strings = mutableMapOf<String, String?>()
        val booleans = mutableMapOf<String, Boolean>()
        override fun readString(key: String): String? = strings[key]
        override fun writeString(key: String, value: String?) { strings[key] = value }
        override fun readBoolean(key: String, default: Boolean): Boolean = booleans[key] ?: default
        override fun writeBoolean(key: String, value: Boolean) { booleans[key] = value }
    }

    @Test
    fun 空store读出全默认值() {
        val s = AppSettings.read(FakeStore())
        assertEquals(SettingsState(), s)
        assertEquals(UnitSystem.SI, s.unitSystem)
        assertEquals(7, s.detailDays)
        assertEquals(true, s.prefetchEnabled)
        assertEquals(true, s.reverseGeoEnabled)
        assertNull(s.apiKey)
    }

    @Test
    fun 单位制序列化往返() {
        val store = FakeStore()
        AppSettings.writeUnit(store, UnitSystem.IMPERIAL)
        assertEquals(UnitSystem.IMPERIAL, AppSettings.read(store).unitSystem)
        AppSettings.writeUnit(store, UnitSystem.SI)
        assertEquals(UnitSystem.SI, AppSettings.read(store).unitSystem)
    }

    @Test
    fun 非法单位串落SI保守默认() {
        val store = FakeStore()
        store.writeString(SettingsKeys.UNIT_SYSTEM, "GARBAGE")
        assertEquals(UnitSystem.SI, AppSettings.read(store).unitSystem)
        assertEquals(UnitSystem.SI, AppSettings.parseUnit(null))
    }

    @Test
    fun 详情天数7与10往返且非法值收敛() {
        val store = FakeStore()
        AppSettings.writeDetailDays(store, 10)
        assertEquals(10, AppSettings.read(store).detailDays)
        AppSettings.writeDetailDays(store, 7)
        assertEquals(7, AppSettings.read(store).detailDays)
        // 越界值经 SettingsLogic.coerceDays（DaySelectorLogic.coerceDaysRange 单源）收敛
        AppSettings.writeDetailDays(store, 99)
        assertEquals(10, AppSettings.read(store).detailDays)
    }

    @Test
    fun 开关读写往返且缺失走默认开() {
        val store = FakeStore()
        AppSettings.writePrefetch(store, false)
        AppSettings.writeReverseGeo(store, false)
        val s = AppSettings.read(store)
        assertEquals(false, s.prefetchEnabled)
        assertEquals(false, s.reverseGeoEnabled)
        AppSettings.writePrefetch(store, true)
        AppSettings.writeReverseGeo(store, true)
        assertEquals(true, AppSettings.read(store).prefetchEnabled)
        assertEquals(true, AppSettings.read(store).reverseGeoEnabled)
    }

    @Test
    fun apiKey空白清洗为null且null透传() {
        val store = FakeStore()
        AppSettings.writeApiKey(store, "  ")
        assertNull(AppSettings.read(store).apiKey)
        AppSettings.writeApiKey(store, " k123 ")
        assertEquals("k123", AppSettings.read(store).apiKey)
        AppSettings.writeApiKey(store, null)
        assertNull(AppSettings.read(store).apiKey)
    }

    @Test
    fun 兜底点位名固定Locale小数点() {
        assertEquals("点位 30.50000, 120.30000", AppSettings.fallbackSpotName(30.5, 120.3))
        assertEquals("点位 -0.50000, 179.99999", AppSettings.fallbackSpotName(-0.5, 179.99999))
    }
}
