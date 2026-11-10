package com.tidespot.feature.settings

import com.tidespot.core.ui.selector.DaySelectorLogic

/**
 * 设置页纯逻辑（CHG-16⑱；UI 逻辑先抽纯函数再进 Composable）：全 JVM 可测。
 *
 * - 单位制：SI/英制切换仅是显示层换算语义（内部恒 SI，冻结点 3），换算函数在此单源；
 * - 详情天数：7|10 二选一，收敛单源接 [DaySelectorLogic.coerceDaysRange]（D14 设置项）；
 * - 开关/API Key：翻转与清洗均为纯函数；
 * - 许可清单/隐私披露：静态数据 + 开关联动文案。
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

    // ────────────── 可选 API Key 占位 ──────────────

    /** API Key 清洗：trim；空白 → null（不存空白键）。 */
    fun withApiKey(state: SettingsState, raw: String?): SettingsState =
        state.copy(apiKey = raw?.trim()?.ifEmpty { null })

    // ────────────── 开源许可清单（HANDOVER §8-2：Copernicus/NASA/NOAA/USGS 需署名） ──────────────

    data class LicenseEntry(
        val name: String,
        val role: String,
        val license: String,
        val attribution: String,
    )

    /** 数据源/库许可与署名（渲染顺序即展示顺序；锁测试钉内容）。 */
    val LICENSES: List<LicenseEntry> = listOf(
        LicenseEntry("Open-Meteo", "天气 / 海洋 / 正地理搜索", "CC BY 4.0（数据）", "Weather data by Open-Meteo.com"),
        LicenseEntry("Copernicus Marine Service", "海洋再分析 / 水位 / 水质", "Copernicus Marine 许可（需署名）", "© Copernicus Marine Environment Monitoring Service"),
        LicenseEntry("NASA", "水体掩膜 / 遥感参考", "NASA 开放数据协议（需署名）", "Courtesy of NASA Earth Science Division"),
        LicenseEntry("NOAA", "水位 / 海洋参考", "公有领域（建议署名）", "Source: NOAA"),
        LicenseEntry("USGS", "地形 / 水体参考", "公有领域（建议署名）", "Source: USGS"),
        LicenseEntry("OpenStreetMap · MapLibre", "地图底图 / 渲染", "ODbL（数据）/ BSD-2（MapLibre）", "© OpenStreetMap contributors"),
        LicenseEntry("BigDataCloud", "逆地理 RG-A", "CC BY 4.0（客户端 API 免费层）", "© BigDataCloud"),
        LicenseEntry("Photon", "逆地理 RG-B", "ODbL（数据 © OpenStreetMap）", "© Photon by komoot"),
    )

    // ────────────── 隐私逐源披露（发送内容 + 开关联动） ──────────────

    data class PrivacyLine(val source: String, val sends: String, val toggleNote: String)

    /**
     * 逐源披露行（RG-A BigDataCloud / RG-B Photon / 正地理 Open-Meteo / 天气 / 海洋 / 水质推导）。
     * [reverseGeoEnabled] 关 → 逆地理两行注明「已关闭（不发请求）」；
     * [prefetchEnabled] 关 → 天气行注明「仅进详情页时拉取」。
     */
    fun privacyLines(reverseGeoEnabled: Boolean, prefetchEnabled: Boolean): List<PrivacyLine> {
        val geoNote = if (reverseGeoEnabled) "受「逆地理名称」总开关控制：已开启"
        else "受「逆地理名称」总开关控制：已关闭（不发请求）"
        val weatherNote = if (prefetchEnabled) "「预取」已开启：进入列表页可能后台预热"
        else "「预取」已关闭：仅进入详情页时拉取"
        return listOf(
            PrivacyLine("逆地理 RG-A · BigDataCloud", "仅发送坐标（纬度/经度），不含账号或设备标识", geoNote),
            PrivacyLine("逆地理 RG-B · Photon", "仅发送坐标（纬度/经度），不含账号或设备标识", geoNote),
            PrivacyLine("正地理 · Open-Meteo /v1/search", "仅发送搜索关键词", "仅在用户搜索时发送"),
            PrivacyLine("天气 · Open-Meteo", "发送坐标与请求时段", weatherNote),
            PrivacyLine("海洋 · Copernicus / 海洋模型", "发送坐标与请求时段", weatherNote),
            PrivacyLine("水质 · 本地推导", "不发送任何数据（由天气序列在设备内推导）", "无网络请求"),
        )
    }
}
