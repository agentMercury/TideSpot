package com.tidespot.core.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * TideSpot 唯一外部 URL 构造器（CHG-08 ② / SRS §7.1 网络层硬约束）。
 *
 * 全部外部 URL 只允许在本对象拼接，各 Client（Open-Meteo 天气/海洋、Geocoding、
 * ReverseGeo RG-A/RG-B、CopernicusAuthClient、OstiaLakeProbe）一律传入各自构造器注入的
 * base（测试用 MockWebServer 地址覆盖）后调用本对象——**禁止出现第二处 URL 拼接**，
 * 保证单文件可审计（M3 已新增 3~4 个域名，CHG-08 ②）。
 *
 * 行为不变约束（CHG-08）：同参数生成的 URL 与收敛前**逐字符一致**，由既有防回归断言守护：
 * - `TideSpotRepositoryTest.状态1b_UA与URL参数防回归`（§5.1/§5.2 参数）
 * - `OstiaLakeProbeTest.URL参数防回归_WebMercator瓦片与像素_Bearer与UA`（WMTS 参数）
 * - `ReverseGeocoderTest` / `GeocodingClientTest` 参数断言（§5.8/§5.9 参数）
 */
object TideSpotUrls {

    // ───────────────────── 生产 base（Client 构造器默认值引用此处的常量） ─────────────────────

    /** §5.1 Open-Meteo Weather API 生产域。 */
    const val WEATHER_BASE = "https://api.open-meteo.com"

    /** §5.2 Open-Meteo Marine API 生产域。 */
    const val MARINE_BASE = "https://marine-api.open-meteo.com"

    /** §5.9 Open-Meteo Geocoding API 生产域。 */
    const val GEOCODING_BASE = "https://geocoding-api.open-meteo.com"

    /** §5.8 RG-A BigDataCloud 生产域。 */
    const val BIG_DATA_CLOUD_BASE = "https://api.bigdatacloud.net"

    /** §5.8 RG-B Photon 生产域。 */
    const val PHOTON_BASE = "https://photon.komoot.io"

    /** §6.9 第 3 级 Copernicus Marine 鉴权域（DATA_SOURCE_PROBE M3.0 A6-r1）。 */
    const val COPERNICUS_AUTH_BASE = "https://auth.marine.copernicus.eu"

    /** §6.9 第 3 级 OSTIA WMTS 生产域（DATA_SOURCE_PROBE M3.0 A6-r3）。 */
    const val WMTS_BASE = "https://wmts.marine.copernicus.eu"

    /** CHG-25（P0-1）：Copernicus Marine 免费注册页（设置页凭据版块链接；URL 单点收敛）。 */
    const val COPERNICUS_REGISTER_URL = "https://data.marine.copernicus.eu/register"

    // ───────────────────── §5.1/§5.2 字段清单（query 的 hourly/daily 内容） ─────────────────────

    /** §5.1 hourly 参数（顺序与契约一致，21 项）。 */
    val WEATHER_HOURLY = listOf(
        "temperature_2m", "relative_humidity_2m", "dew_point_2m", "apparent_temperature",
        "pressure_msl", "surface_pressure", "cloud_cover", "cloud_cover_low", "cloud_cover_mid",
        "cloud_cover_high", "visibility", "precipitation", "rain", "showers", "snowfall",
        "weather_code", "wind_speed_10m", "wind_direction_10m", "wind_gusts_10m", "cape",
        "freezing_level_height",
    )

    /** §5.1 daily 参数。 */
    val WEATHER_DAILY = listOf(
        "sunrise", "sunset", "temperature_2m_max", "temperature_2m_min",
        "precipitation_sum", "wind_speed_10m_max", "wind_gusts_10m_max", "weather_code",
    )

    /**
     * §5.2 hourly 参数（16 项）。陷阱字段：潮位 = `sea_level_height_msl`
     * （写成 mssh → 400 B9，§5.2 / 附录 B9 防回归点）。
     */
    val MARINE_HOURLY = listOf(
        "wave_height", "wave_period", "wave_direction", "wave_peak_period",
        "wind_wave_height", "wind_wave_period", "wind_wave_direction",
        "swell_wave_height", "swell_wave_period", "swell_wave_direction",
        "secondary_swell_wave_height", "tertiary_swell_wave_height",
        "ocean_current_velocity", "ocean_current_direction",
        "sea_level_height_msl", "sea_surface_temperature",
    )

    // ───────────────────── OSTIA WMTS 常量（Web Mercator 换算随 URL 组装走单点） ─────────────────────

    /** DATA_SOURCE_PROBE A6-r4：z=6/7/8 均核实，取 7（0.05° 数据在 256px 瓦片内像素粒度足够）。 */
    const val OSTIA_DEFAULT_ZOOM = 7

    /** Web Mercator 瓦片像素尺寸。 */
    const val WEB_MERCATOR_TILE_SIZE = 256

    /** EPSG:3857 纬度边界（墨卡托正切函数定义域）。 */
    const val WEB_MERCATOR_MAX_LAT = 85.05112878

    /** OSTIA 图层全名（CHG-06，DATA_SOURCE_PROBE A6-r3 已核实 queryable=1）。 */
    const val OSTIA_LAYER = "SST_GLO_SST_L4_NRT_OBSERVATIONS_010_001/" +
        "METOFFICE-GLO-SST-L4-NRT-OBS-SST-V2/analysed_sst"

    // ───────────────────── URL 构造函数（每个函数 KDoc 注明对应 SRS 条款） ─────────────────────

    /**
     * §5.1 天气 240h 整窗（hourly 21 字段 + daily 8 字段；`past_days=7` 非可选：
     * Δ3h/Δ24h/7 日基线必需；`forecast_days=10` CHG-03 整窗；`timezone=auto`）。
     */
    fun weather(base: String, lat: Double, lon: Double): HttpUrl = base.toHttpUrl().newBuilder()
        .addPathSegments("v1/forecast")
        .addQueryParameter("latitude", lat.toString())
        .addQueryParameter("longitude", lon.toString())
        .addQueryParameter("hourly", WEATHER_HOURLY.joinToString(","))
        .addQueryParameter("daily", WEATHER_DAILY.joinToString(","))
        .addQueryParameter("past_days", "7")
        .addQueryParameter("forecast_days", "10")
        .addQueryParameter("timezone", "auto")
        .build()

    /** §5.2 海洋 240h 整窗（16 字段；past_days=1；陷阱字段 sea_level_height_msl 见上）。 */
    fun marine(base: String, lat: Double, lon: Double): HttpUrl = base.toHttpUrl().newBuilder()
        .addPathSegments("v1/marine")
        .addQueryParameter("latitude", lat.toString())
        .addQueryParameter("longitude", lon.toString())
        .addQueryParameter("hourly", MARINE_HOURLY.joinToString(","))
        .addQueryParameter("past_days", "1")
        .addQueryParameter("forecast_days", "10")
        .addQueryParameter("timezone", "auto")
        .build()

    /** §5.9 正地理搜索 `/v1/search`（language=zh、format=json；count 夹取 [1,50]）。 */
    fun geocoding(base: String, query: String, count: Int): HttpUrl = base.toHttpUrl().newBuilder()
        .addPathSegments("v1/search")
        .addQueryParameter("name", query)
        .addQueryParameter("count", count.coerceIn(1, 50).toString())
        .addQueryParameter("language", "zh")
        .addQueryParameter("format", "json")
        .build()

    /** §5.8 RG-A 主源 BigDataCloud `data/reverse-geocode-client`（localityLanguage=zh；307 跳备用域由 HTTP 层跟随）。 */
    fun bigDataCloudReverse(base: String, lat: Double, lon: Double): HttpUrl =
        base.toHttpUrl().newBuilder()
            .addPathSegments("data/reverse-geocode-client")
            .addQueryParameter("latitude", lat.toString())
            .addQueryParameter("longitude", lon.toString())
            .addQueryParameter("localityLanguage", "zh")
            .build()

    /**
     * §5.8 RG-B 次源 Photon `reverse`（limit=1、layer=locality——实测只接受单值 locality，
     * B7b：组合 layer → 400）。**禁止 lang 参数**（B7b：lang=zh → 400）。
     */
    fun photonReverse(base: String, lat: Double, lon: Double): HttpUrl = base.toHttpUrl().newBuilder()
        .addPathSegments("reverse")
        .addQueryParameter("lat", lat.toString())
        .addQueryParameter("lon", lon.toString())
        .addQueryParameter("limit", "1")
        .addQueryParameter("layer", "locality")
        .build()

    /** §6.9 第 3 级 / DATA_SOURCE_PROBE M3.0 A6-r1：Copernicus openid-connect token 端点 path。 */
    fun copernicusToken(base: String): HttpUrl = base.toHttpUrl().newBuilder()
        .addPathSegments("realms/MIS/protocol/openid-connect/token")
        .build()

    /**
     * §6.9 第 3 级 / DATA_SOURCE_PROBE A6-r3/A6-r4：OSTIA WMTS GetFeatureInfo 全参数
     * （SERVICE/REQUEST/VERSION/LAYER/STYLE/FORMAT/TILEMATRIXSET=EPSG:3857/TILEMATRIX/
     * TILEROW/TILECOL/I/J/INFOFORMAT）。Web Mercator 瓦片号与 256px 瓦片内像素换算
     * 按常规公式在本函数完成（URL 组装单点化，CHG-08 ②）。
     */
    fun ostiaFeatureInfo(base: String, lat: Double, lon: Double, zoom: Int = OSTIA_DEFAULT_ZOOM): HttpUrl {
        val n = 1 shl zoom
        val xNorm = (lon + 180.0) / 360.0
        val clampedLat = lat.coerceIn(-WEB_MERCATOR_MAX_LAT, WEB_MERCATOR_MAX_LAT)
        val rad = Math.toRadians(clampedLat)
        val yNorm = (1 - ln(tan(rad) + 1 / cos(rad)) / PI) / 2
        val col = (xNorm * n).toInt().coerceIn(0, n - 1)
        val row = (yNorm * n).toInt().coerceIn(0, n - 1)
        val i = ((xNorm * n - col) * WEB_MERCATOR_TILE_SIZE).toInt().coerceIn(0, WEB_MERCATOR_TILE_SIZE - 1)
        val j = ((yNorm * n - row) * WEB_MERCATOR_TILE_SIZE).toInt().coerceIn(0, WEB_MERCATOR_TILE_SIZE - 1)
        return base.toHttpUrl().newBuilder()
            .addPathSegments("teroWmts")
            .addQueryParameter("SERVICE", "WMTS")
            .addQueryParameter("REQUEST", "GetFeatureInfo")
            .addQueryParameter("VERSION", "1.0.0")
            .addQueryParameter("LAYER", OSTIA_LAYER)
            .addQueryParameter("STYLE", "default")
            .addQueryParameter("FORMAT", "image/png")
            .addQueryParameter("TILEMATRIXSET", "EPSG:3857")
            .addQueryParameter("TILEMATRIX", zoom.toString())
            .addQueryParameter("TILEROW", row.toString())
            .addQueryParameter("TILECOL", col.toString())
            .addQueryParameter("I", i.toString())
            .addQueryParameter("J", j.toString())
            .addQueryParameter("INFOFORMAT", "application/json")
            .build()
    }
    /**
     * UX-19 / D8 底图源：**OpenFreeMap**（OpenStreetMap 数据，WGS84，与定位坐标系一致、无偏移；
     * 免费、无 Key、attribution 由样式自带）。
     *
     * 2026-11-13 由 CARTO 切换：`*.cartocdn.com`（Fastly）在实测网络下 TCP 443 全部 10s 超时，
     * 底图整片空白（定位与数据正常）；同网络的 `tiles.openfreemap.org` 1.5s 可达。
     * 三选语义不变（彩色/素色/深色），仅换提供方。
     */
    const val BASEMAP_COLOR_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    const val BASEMAP_PLAIN_STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

    const val BASEMAP_DARK_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
}
