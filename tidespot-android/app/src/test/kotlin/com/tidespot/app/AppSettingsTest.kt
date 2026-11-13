package com.tidespot.app

import com.tidespot.core.ui.theme.TideSpotSkins
import com.tidespot.feature.settings.SettingsKeys
import com.tidespot.feature.settings.SettingsState
import com.tidespot.feature.settings.SettingsStore
import com.tidespot.feature.settings.ThemeMode
import com.tidespot.feature.settings.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        // UX-19：默认彩色 Voyager
        assertEquals(com.tidespot.feature.settings.MapStyle.VOYAGER, s.mapStyle)
    }

    // ── UX-19：地图样式持久化 ──

    @Test
    fun 地图样式序列化往返_非法值回落默认() {
        val store = FakeStore()
        AppSettings.writeMapStyle(store, com.tidespot.feature.settings.MapStyle.DARK_MATTER)
        assertEquals(com.tidespot.feature.settings.MapStyle.DARK_MATTER, AppSettings.read(store).mapStyle)
        AppSettings.writeMapStyle(store, com.tidespot.feature.settings.MapStyle.POSITRON)
        assertEquals(com.tidespot.feature.settings.MapStyle.POSITRON, AppSettings.read(store).mapStyle)
        // 脏值 → Voyager（保守默认，不 crash）
        store.writeString(SettingsKeys.MAP_STYLE, "NOT_A_STYLE")
        assertEquals(com.tidespot.feature.settings.MapStyle.VOYAGER, AppSettings.read(store).mapStyle)
    }

    @Test
    fun 地图样式显式标志往返() {
        // 全新安装：无任何键 → 未改过（底图跟随明暗自动切）
        assertFalse(AppSettings.read(FakeStore()).mapStyleExplicit)

        val store = FakeStore()
        AppSettings.writeMapStyle(store, com.tidespot.feature.settings.MapStyle.POSITRON)
        AppSettings.writeMapStyleExplicit(store, true)
        assertTrue(AppSettings.read(store).mapStyleExplicit)

        // 只有旧 MAP_STYLE 键、无显式标志键 → 仍视为未改过（v1.16 全量写回会留下该键，不可据此锁定）
        val legacy = FakeStore()
        legacy.writeString(SettingsKeys.MAP_STYLE, "POSITRON")
        assertFalse(AppSettings.read(legacy).mapStyleExplicit)

        // 显式标志可覆盖为 false（回到跟随明暗）
        AppSettings.writeMapStyleExplicit(store, false)
        assertFalse(AppSettings.read(store).mapStyleExplicit)
    }

    @Test
    fun 地图提供方往返与非法值回落开放式源() {
        val store = FakeStore()
        assertEquals(com.tidespot.feature.settings.MapProvider.OPENFREEMAP, AppSettings.read(store).mapProvider)
        AppSettings.writeMapProvider(store, com.tidespot.feature.settings.MapProvider.CARTO)
        assertEquals(com.tidespot.feature.settings.MapProvider.CARTO, AppSettings.read(store).mapProvider)
        // 脏值 → OpenFreeMap（实测可达的默认）
        store.writeString(SettingsKeys.MAP_PROVIDER, "BING")
        assertEquals(com.tidespot.feature.settings.MapProvider.OPENFREEMAP, AppSettings.read(store).mapProvider)
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

    // ── OPT-A-02：API Key 下线后的历史残留清理 ──

    @Test
    fun 历史apiKey一次性清理() {
        val store = FakeStore()
        store.writeString("settings.apiKey", "legacy-secret")
        AppSettings.clearLegacyApiKey(store)
        assertNull(store.readString("settings.apiKey"))
        // 幂等：重复清理不报错
        AppSettings.clearLegacyApiKey(store)
        assertNull(store.readString("settings.apiKey"))
    }

    @Test
    fun 兜底点位名固定Locale小数点() {
        assertEquals("点位 30.50000, 120.30000", AppSettings.fallbackSpotName(30.5, 120.3))
        assertEquals("点位 -0.50000, 179.99999", AppSettings.fallbackSpotName(-0.5, 179.99999))
    }

    // ── SKIN-3：皮肤持久化（未知 id 归一，冷启动不崩） ──

    @Test
    fun 皮肤id往返_未知id归一为默认() {
        val store = FakeStore()
        assertEquals(TideSpotSkins.default.id, AppSettings.read(store).skinId)
        AppSettings.writeSkinId(store, "a-vintage")
        assertEquals("a-vintage", AppSettings.read(store).skinId)
        // 写入脏值 → 落盘即归一
        AppSettings.writeSkinId(store, "ghost-skin")
        assertEquals(TideSpotSkins.default.id, AppSettings.read(store).skinId)
        // 存量脏值（旧版本/手改）→ 读侧同样归一
        store.writeString(SettingsKeys.SKIN_ID, "ghost-skin")
        assertEquals(TideSpotSkins.default.id, AppSettings.read(store).skinId)
    }

    // ── OPT-A-03：明暗持久化 ──

    @Test
    fun 明暗模式往返_脏值落跟随系统() {
        val store = FakeStore()
        assertEquals(ThemeMode.SYSTEM, AppSettings.read(store).themeMode)
        AppSettings.writeThemeMode(store, ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, AppSettings.read(store).themeMode)
        AppSettings.writeThemeMode(store, ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, AppSettings.read(store).themeMode)
        store.writeString(SettingsKeys.THEME_MODE, "NOT_A_MODE")
        assertEquals(ThemeMode.SYSTEM, AppSettings.read(store).themeMode)
        assertEquals(ThemeMode.SYSTEM, AppSettings.parseThemeMode(null))
    }

    @Test
    fun 三轴互不重置_切皮肤不动明暗_切明暗不动皮肤() {
        val store = FakeStore()
        AppSettings.writeSkinId(store, "a-vintage")
        AppSettings.writeThemeMode(store, ThemeMode.DARK)
        // 只写皮肤 → 明暗保持
        AppSettings.writeSkinId(store, TideSpotSkins.default.id)
        assertEquals(ThemeMode.DARK, AppSettings.read(store).themeMode)
        // 只写明暗 → 皮肤保持
        AppSettings.writeThemeMode(store, ThemeMode.LIGHT)
        assertEquals("a-vintage", AppSettings.read(store).skinId)
        // 地图样式也不被这两轴带走（水体是第三轴，由点位决定，不落设置）
        assertEquals(com.tidespot.feature.settings.MapStyle.VOYAGER, AppSettings.read(store).mapStyle)
    }
}
