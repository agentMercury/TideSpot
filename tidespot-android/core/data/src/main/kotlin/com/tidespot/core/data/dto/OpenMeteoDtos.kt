package com.tidespot.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Open-Meteo DTO（SRS §5.1 / §5.2 契约，精确到字段名）。
 *
 * 规则（CONTRACT.md 冻结点 4 + SRS §5.0）：
 * - 源缺值一律可空（`Double?` / `Int?`），**禁止默认 0 填充**；
 * - 数组与 `hourly.time` 按同下标对应（DTO 侧只保真，join 在 repo 层按时间戳做）；
 * - `ignoreUnknownKeys`，未知字段直接忽略（源可能新增字段）。
 */

// ────────────────────────────── §5.1 天气 ──────────────────────────────

@Serializable
data class WeatherResponseDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** 海拔 m（§6.9 水体类型判定辅助判据；注意 B5：近岸可能取到陆地格点，仅作参考）。 */
    val elevation: Double? = null,
    /** 当地时区偏移秒（timezone=auto 时返回），用于把当地墙串还原为 epoch。 */
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    /** CHG-09⑥（NEW-06，只增）：IANA 时区 id；utcOffsetSeconds 缺失时映射层据此推算偏移。 */
    @SerialName("timezone") val timezoneId: String? = null,
    val hourly: WeatherHourlyDto? = null,
    val daily: WeatherDailyDto? = null,
)

@Serializable
data class WeatherHourlyDto(
    /** 当地墙上时间，形如 `2026-11-02T17:00`（§5.0：无偏移，靠 utc_offset_seconds 还原）。 */
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperature2m: List<Double?>? = null,
    @SerialName("relative_humidity_2m") val relativeHumidity2m: List<Double?>? = null,
    @SerialName("dew_point_2m") val dewPoint2m: List<Double?>? = null,
    @SerialName("apparent_temperature") val apparentTemperature: List<Double?>? = null,
    @SerialName("pressure_msl") val pressureMsl: List<Double?>? = null,
    @SerialName("surface_pressure") val surfacePressure: List<Double?>? = null,
    @SerialName("cloud_cover") val cloudCover: List<Double?>? = null,
    @SerialName("cloud_cover_low") val cloudCoverLow: List<Double?>? = null,
    @SerialName("cloud_cover_mid") val cloudCoverMid: List<Double?>? = null,
    @SerialName("cloud_cover_high") val cloudCoverHigh: List<Double?>? = null,
    /** 能见度，源单位 km（映射层统一换算为 m，SI，F-4.4）。 */
    val visibility: List<Double?>? = null,
    val precipitation: List<Double?>? = null,
    val rain: List<Double?>? = null,
    val showers: List<Double?>? = null,
    val snowfall: List<Double?>? = null,
    @SerialName("weather_code") val weatherCode: List<Int?>? = null,
    @SerialName("wind_speed_10m") val windSpeed10m: List<Double?>? = null,
    @SerialName("wind_direction_10m") val windDirection10m: List<Double?>? = null,
    @SerialName("wind_gusts_10m") val windGusts10m: List<Double?>? = null,
    val cape: List<Double?>? = null,
    @SerialName("freezing_level_height") val freezingLevelHeight: List<Double?>? = null,
)

@Serializable
data class WeatherDailyDto(
    val time: List<String> = emptyList(),
    val sunrise: List<String?>? = null,
    val sunset: List<String?>? = null,
    @SerialName("temperature_2m_max") val temperature2mMax: List<Double?>? = null,
    @SerialName("temperature_2m_min") val temperature2mMin: List<Double?>? = null,
    @SerialName("precipitation_sum") val precipitationSum: List<Double?>? = null,
    @SerialName("wind_speed_10m_max") val windSpeed10mMax: List<Double?>? = null,
    @SerialName("wind_gusts_10m_max") val windGusts10mMax: List<Double?>? = null,
    @SerialName("weather_code") val weatherCode: List<Int?>? = null,
)

// ────────────────────────────── §5.2 海洋 ──────────────────────────────

/**
 * Marine API 响应。字段名陷阱（§5.2）：潮位是 `sea_level_height_msl`，
 * 写成 `sea_level_height_mssh` 返回 400（B9 → [com.tidespot.core.data.DataError.SourceParamError]）。
 * 内陆点行为（附录 B8）：HTTP 200 但所有数组元素为 null → 上层以「全 null」判定无海况并降级。
 */
@Serializable
data class MarineResponseDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val elevation: Double? = null,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    /** FO-18（M4 必修 4，只增）：IANA 时区 id；utcOffsetSeconds 缺失时映射层据此推算偏移（同天气侧 CHG-09⑥）。 */
    @SerialName("timezone") val timezoneId: String? = null,
    val hourly: MarineHourlyDto? = null,
)

@Serializable
data class MarineHourlyDto(
    val time: List<String> = emptyList(),
    @SerialName("wave_height") val waveHeight: List<Double?>? = null,
    @SerialName("wave_period") val wavePeriod: List<Double?>? = null,
    @SerialName("wave_direction") val waveDirection: List<Double?>? = null,
    @SerialName("wave_peak_period") val wavePeakPeriod: List<Double?>? = null,
    @SerialName("wind_wave_height") val windWaveHeight: List<Double?>? = null,
    @SerialName("wind_wave_period") val windWavePeriod: List<Double?>? = null,
    @SerialName("wind_wave_direction") val windWaveDirection: List<Double?>? = null,
    @SerialName("swell_wave_height") val swellWaveHeight: List<Double?>? = null,
    @SerialName("swell_wave_period") val swellWavePeriod: List<Double?>? = null,
    @SerialName("swell_wave_direction") val swellWaveDirection: List<Double?>? = null,
    @SerialName("secondary_swell_wave_height") val secondarySwellWaveHeight: List<Double?>? = null,
    @SerialName("tertiary_swell_wave_height") val tertiarySwellWaveHeight: List<Double?>? = null,
    /** 流速 km/h（§5.4 直取项）。 */
    @SerialName("ocean_current_velocity") val oceanCurrentVelocity: List<Double?>? = null,
    @SerialName("ocean_current_direction") val oceanCurrentDirection: List<Double?>? = null,
    /** 潮位 m（MSL，含潮汐，§5.4）。 */
    @SerialName("sea_level_height_msl") val seaLevelHeightMsl: List<Double?>? = null,
    /** 海表温度 °C（SST）。 */
    @SerialName("sea_surface_temperature") val seaSurfaceTemperature: List<Double?>? = null,
)

/**
 * 时间换算（§5.0 / F-3.8 / N-9）：请求带 `timezone=auto`，返回的是选点当地墙上时间（无偏移）；
 * 内部一律存 epoch 秒，比较与排序用 epoch。精度到小时（源即逐小时）。
 */
object OpenMeteoTime {

    /** `2026-11-02T17:00` + 时区偏移秒 → epoch 秒。 */
    fun parseEpochSec(localIso: String, utcOffsetSec: Int): Long =
        LocalDateTime.parse(localIso).toEpochSecond(ZoneOffset.ofTotalSeconds(utcOffsetSec))

    /**
     * [parseEpochSec] 的安全变体（BLK-06/N-4，CHG-07 修复 4）：脏时间串（非数字/截断/空）
     * 返回 null 而不抛 DateTimeParseException，由映射层按「跳过该小时」降级（N-4 不得 crash）。
     */
    fun parseEpochSecOrNull(localIso: String?, utcOffsetSec: Int): Long? =
        if (localIso.isNullOrEmpty()) null
        else runCatching { parseEpochSec(localIso, utcOffsetSec) }.getOrNull()

    /** epoch 秒 + 时区偏移秒 → 当地墙上时间串（测试夹具与显示用）。 */
    fun localIso(epochSec: Long, utcOffsetSec: Int): String =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSec), ZoneOffset.ofTotalSeconds(utcOffsetSec))
            .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}
