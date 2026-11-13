package com.tidespot.app

import com.tidespot.core.ui.theme.TideSpotSkins
import com.tidespot.feature.settings.MapStyle
import com.tidespot.feature.settings.SettingsKeys
import com.tidespot.feature.settings.SettingsLogic
import com.tidespot.feature.settings.SettingsState
import com.tidespot.feature.settings.SettingsStore
import com.tidespot.feature.settings.ThemeMode
import com.tidespot.feature.settings.UnitSystem
import java.util.Locale

/**
 * 设置键值 ↔ [SettingsState] 映射（M6 批次 3）：纯函数（JVM 锁测试 [AppSettingsTest]），
 * [com.tidespot.app.SharedPreferencesSettingsStore] 只做键值读写，不承载语义。
 *
 * OPT-M5-07 收藏对话框名称兜底也收口在本对象（纯函数，禁 0.0/0.0 占位由
 * FavoritesSaveRules.canCreate 防御，此处只负责显示名格式化）。
 */
object AppSettings {

    // ────────────── 单位制序列化（显示层语义；内部恒 SI，冻结点 3） ──────────────

    const val UNIT_SI = "SI"
    const val UNIT_IMPERIAL = "IMPERIAL"

    fun unitName(unit: UnitSystem): String =
        if (unit == UnitSystem.SI) UNIT_SI else UNIT_IMPERIAL

    /** 非法/缺失值一律落 SI（保守默认）。 */
    fun parseUnit(raw: String?): UnitSystem =
        if (raw == UNIT_IMPERIAL) UnitSystem.IMPERIAL else UnitSystem.SI

    // ────────────── UX-19：地图样式（三选；非法/缺失 → Voyager 默认） ──────────────

    fun parseMapStyle(raw: String?): MapStyle =
        MapStyle.entries.firstOrNull { it.name == raw } ?: MapStyle.VOYAGER

    fun writeMapStyle(store: SettingsStore, style: MapStyle) {
        store.writeString(SettingsKeys.MAP_STYLE, style.name)
    }

    /** 「用户是否显式改过地图样式」：决定底图跟明暗自动切，还是用户选择优先。 */
    fun writeMapStyleExplicit(store: SettingsStore, explicit: Boolean) {
        store.writeBoolean(SettingsKeys.MAP_STYLE_EXPLICIT, explicit)
    }

    // ────────────── 三轴正交：皮肤 / 明暗（SKIN-3 + OPT-A-03） ──────────────

    /** 明暗序列化：非法/缺失一律落 `SYSTEM`（跟系统是最保守的默认）。 */
    fun parseThemeMode(raw: String?): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM

    /** 写皮肤：先经注册表归一（未知 id → 默认），避免脏值落盘。 */
    fun writeSkinId(store: SettingsStore, id: String) {
        store.writeString(SettingsKeys.SKIN_ID, TideSpotSkins.byId(id).id)
    }

    fun writeThemeMode(store: SettingsStore, mode: ThemeMode) {
        store.writeString(SettingsKeys.THEME_MODE, mode.name)
    }

    // ────────────── 读取（store → state） ──────────────

    fun read(store: SettingsStore): SettingsState = SettingsState(
        unitSystem = parseUnit(store.readString(SettingsKeys.UNIT_SYSTEM)),
        detailDays = SettingsLogic.coerceDays(
            store.readString(SettingsKeys.DETAIL_DAYS)?.toIntOrNull() ?: 7,
        ),
        prefetchEnabled = store.readBoolean(SettingsKeys.PREFETCH_ENABLED, default = true),
        reverseGeoEnabled = store.readBoolean(SettingsKeys.REVERSE_GEO_ENABLED, default = true),
        mapStyle = parseMapStyle(store.readString(SettingsKeys.MAP_STYLE)),
        // 只在「用户动手选过」时才为 true，因此默认 false。
        // 不拿「MAP_STYLE 键是否存在」推断：v1.16 的 persistSettings 全量写回，未改过样式的用户
        // 也会留下该键（改任何设置都会写），据此推断会把绝大多数人误判为「已锁定」，
        // 他们就永远拿不到自动跟随深色。老用户若真选过，重新点一次即恢复锁定。
        mapStyleExplicit = store.readBoolean(SettingsKeys.MAP_STYLE_EXPLICIT, default = false),
        // 冷启动分别回放：任一轴缺失/非法只落该轴默认，不影响另外两轴
        skinId = TideSpotSkins.byId(store.readString(SettingsKeys.SKIN_ID)).id,
        themeMode = parseThemeMode(store.readString(SettingsKeys.THEME_MODE)),
    )

    // ────────────── 写回（state 片段 → store） ──────────────

    fun writeUnit(store: SettingsStore, unit: UnitSystem) {
        store.writeString(SettingsKeys.UNIT_SYSTEM, unitName(unit))
    }

    fun writeDetailDays(store: SettingsStore, days: Int) {
        store.writeString(SettingsKeys.DETAIL_DAYS, SettingsLogic.coerceDays(days).toString())
    }

    fun writePrefetch(store: SettingsStore, enabled: Boolean) {
        store.writeBoolean(SettingsKeys.PREFETCH_ENABLED, enabled)
    }

    fun writeReverseGeo(store: SettingsStore, enabled: Boolean) {
        store.writeBoolean(SettingsKeys.REVERSE_GEO_ENABLED, enabled)
    }

    /**
     * OPT-A-02（2026-11-12 产品决策）：API Key 功能已从设置页下线。
     *
     * 该键在 v1.16 及更早版本可能被写入过；启动时调用本函数做**一次性清理**——
     * 只删除本地键值，不读取内容、不上传、不打印，也不参与任何请求签名。
     * 键名保留为字面量（`SettingsKeys` 已无该成员），仅用于清理历史残留。
     */
    fun clearLegacyApiKey(store: SettingsStore) {
        store.writeString(LEGACY_API_KEY_KEY, null)
    }

    private const val LEGACY_API_KEY_KEY = "settings.apiKey"

    // ────────────── OPT-M5-07 收藏对话框名称兜底 ──────────────

    /**
     * 逆地理名称缺失/空白时的显示名兜底（固定 Locale.US 保证小数点不随设备区域漂移）。
     * 坐标合法性（0.0/0.0 占位拒绝）由 FavoritesSaveRules.canCreate 单源防御。
     */
    fun fallbackSpotName(lat: Double, lon: Double): String =
        String.format(Locale.US, "点位 %.5f, %.5f", lat, lon) // i18n-exempt: 默认点位名落库（历史数据兼容）
}
