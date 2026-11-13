package com.tidespot.feature.settings

import androidx.annotation.StringRes
import com.tidespot.core.ui.selector.DaySelectorLogic
import com.tidespot.core.ui.theme.TideSpotSkins

/**
 * 设置页纯逻辑（CHG-16⑱；UI 逻辑先抽纯函数再进 Composable）：全 JVM 可测。
 *
 * - 单位制：SI/英制切换仅是显示层换算语义（内部恒 SI，冻结点 3），换算函数在此单源；
 * - 详情天数：7|10 二选一，收敛单源接 [DaySelectorLogic.coerceDaysRange]（D14 设置项）；
 * - 开关/API Key：翻转与清洗均为纯函数；
 * - 许可清单/隐私披露：静态数据 + 开关联动；OPT-V11-13 起文案全部外移资源（本类只持有 `@StringRes`）。
 */
object SettingsLogic {

    // ────────────── 详情天数（7|10；DaySelectorLogic 单源收敛） ──────────────

    fun coerceDays(days: Int): Int = DaySelectorLogic.coerceDaysRange(days)

    fun withDetailDays(state: SettingsState, days: Int): SettingsState =
        state.copy(detailDays = coerceDays(days))

    // ────────────── 单位制语义（显示层换算；禁回写内部 SI） ──────────────

    fun withUnitSystem(state: SettingsState, unit: UnitSystem): SettingsState =
        state.copy(unitSystem = unit)

    fun tempLabel(unit: UnitSystem): String = if (unit == UnitSystem.SI) "°C" else "°F"

    fun windLabel(unit: UnitSystem): String = if (unit == UnitSystem.SI) "km/h" else "mph"

    fun lengthLabel(unit: UnitSystem): String = if (unit == UnitSystem.SI) "m" else "ft"

    /** 摄氏 → 显示值（SI 原样；英制 °F = °C×9/5+32；null 透传，禁 0 填充）。 */
    fun convertTemp(celsius: Double?, unit: UnitSystem): Double? {
        if (celsius == null) return null
        return if (unit == UnitSystem.SI) celsius else celsius * 9.0 / 5.0 + 32.0
    }

    /** km/h → 显示值（英制 mph = km/h ÷ 1.609344）。 */
    fun convertWind(kmh: Double?, unit: UnitSystem): Double? {
        if (kmh == null) return null
        return if (unit == UnitSystem.SI) kmh else kmh / 1.609344
    }

    /** 米 → 显示值（英制 ft = m × 3.28084）。 */
    fun convertLength(meters: Double?, unit: UnitSystem): Double? {
        if (meters == null) return null
        return if (unit == UnitSystem.SI) meters else meters * 3.28084
    }

    // ────────────── 开关 ──────────────

    fun withPrefetch(state: SettingsState, enabled: Boolean): SettingsState =
        state.copy(prefetchEnabled = enabled)

    fun withReverseGeo(state: SettingsState, enabled: Boolean): SettingsState =
        state.copy(reverseGeoEnabled = enabled)

    /** UX-19：地图样式三选（片刻生效；深色系统不覆盖用户显式选择）。 */
    fun withMapStyle(state: SettingsState, style: MapStyle): SettingsState =
        state.copy(mapStyle = style)

    // ────────────── 皮肤 / 明暗（三轴正交：SKIN-3 + OPT-A-03） ──────────────

    /** 选皮肤：只写 `skinId`，不碰明暗（互不重置）；未知 id 归一为默认皮肤，脏值不入库。 */
    fun withSkinId(state: SettingsState, id: String): SettingsState =
        state.copy(skinId = TideSpotSkins.byId(id).id)

    /** 选明暗：只写 `themeMode`，不碰皮肤（互不重置）。 */
    fun withThemeMode(state: SettingsState, mode: ThemeMode): SettingsState =
        state.copy(themeMode = mode)

    /**
     * 明暗轴解析：`SYSTEM` 跟系统，`LIGHT` / `DARK` 与系统解耦（OPT-A-03 验收第 2 条）。
     * 纯函数，JVM 可测；主题注入只认它的结果。
     */
    fun resolveDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // ────────────── 开源许可清单（HANDOVER §8-2：Copernicus/NASA/NOAA/USGS 需署名） ──────────────

    data class LicenseEntry(
        val name: String,
        @StringRes val roleRes: Int,
        @StringRes val licenseRes: Int,
        val attribution: String,
    )

    /** 数据源/库许可与署名（渲染顺序即展示顺序；锁测试钉 id 序列，OPT-V11-13 文案外移）。 */
    val LICENSES: List<LicenseEntry> = listOf(
        LicenseEntry("Open-Meteo", R.string.ts_license_open_meteo_role, R.string.ts_license_open_meteo_license, "Weather data by Open-Meteo.com"),
        LicenseEntry("Copernicus Marine Service", R.string.ts_license_copernicus_role, R.string.ts_license_copernicus_license, "© Copernicus Marine Environment Monitoring Service"),
        LicenseEntry("NASA", R.string.ts_license_nasa_role, R.string.ts_license_nasa_license, "Courtesy of NASA Earth Science Division"),
        LicenseEntry("NOAA", R.string.ts_license_noaa_role, R.string.ts_license_noaa_license, "Source: NOAA"),
        LicenseEntry("USGS", R.string.ts_license_usgs_role, R.string.ts_license_usgs_license, "Source: USGS"),
        LicenseEntry("OpenStreetMap · MapLibre", R.string.ts_license_osm_role, R.string.ts_license_osm_license, "© OpenStreetMap contributors"),
        LicenseEntry("BigDataCloud", R.string.ts_license_bdc_role, R.string.ts_license_bdc_license, "© BigDataCloud"),
        LicenseEntry("Photon", R.string.ts_license_photon_role, R.string.ts_license_photon_license, "© Photon by komoot"),
    )

    // ────────────── 隐私逐源披露（发送内容 + 开关联动） ──────────────

    data class PrivacyLine(
        @StringRes val sourceRes: Int,
        @StringRes val sendsRes: Int,
        @StringRes val toggleNoteRes: Int,
    )

    /**
     * 逐源披露行（RG-A BigDataCloud / RG-B Photon / 正地理 Open-Meteo / 天气 / 海洋 / 水质推导）。
     * [reverseGeoEnabled] 关 → 逆地理两行注明「已关闭（不发请求）」；
     * [prefetchEnabled] 关 → 天气行注明「仅进详情页时拉取」。
     */
    fun privacyLines(reverseGeoEnabled: Boolean, prefetchEnabled: Boolean): List<PrivacyLine> {
        val geoNote = if (reverseGeoEnabled) R.string.ts_privacy_geo_on else R.string.ts_privacy_geo_off
        val weatherNote = if (prefetchEnabled) R.string.ts_privacy_prefetch_on else R.string.ts_privacy_prefetch_off
        return listOf(
            PrivacyLine(R.string.ts_privacy_rga_source, R.string.ts_privacy_rg_coords, geoNote),
            PrivacyLine(R.string.ts_privacy_rgb_source, R.string.ts_privacy_rg_coords, geoNote),
            PrivacyLine(R.string.ts_privacy_geocode_source, R.string.ts_privacy_geocode_sends, R.string.ts_privacy_geocode_only),
            PrivacyLine(R.string.ts_privacy_weather_source, R.string.ts_privacy_coords_period, weatherNote),
            PrivacyLine(R.string.ts_privacy_marine_source, R.string.ts_privacy_coords_period, weatherNote),
            PrivacyLine(R.string.ts_privacy_wq_source, R.string.ts_privacy_wq_sends, R.string.ts_privacy_no_network),
        )
    }
}