package com.tidespot.core.data.mock

import com.tidespot.core.data.dto.OpenMeteoTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 测试夹具（SRS 附录 B 实测行为的最小仿真）。
 *
 * FO-26（M4 必修 6）：本文件已从 `src/main` 迁至 `src/test`（同包名，生产零引用），
 * 不再被打进生产 AAR/APK。测试侧引用方：ApiErrorClassifierTest / MapperTest / TideSpotRepositoryTest。
 *
 * 设计要点（对齐 6 态测试）：
 * - 天气序列 8h 从 `BASE_EPOCH_SEC` 起，海洋序列 5h 从 `BASE_EPOCH_SEC + 3h` 起——
 *   起始时间错开 3 小时，专门用来验证 §5.0「禁止下标对齐、必须按时间戳 join」；
 * - 天气 `precipitation[2]` 显式 null，验证 §5.0 null 禁止当 0；
 * - 海洋 `allNull=true` 变体对应附录 B8 内陆点行为（200 + 全 null）；
 * - `ianaTz` 变体注入 IANA `timezone` 字段（FO-18 必修 4 解析路径）；
 * - `omitFields` 变体缺失部分 hourly 数组键（§10.2「部分字段缺失」态）；
 * - B9 文案常量来自附录 B9 实测响应。
 */
object MockFixtures {

    /** 时区偏移：UTC+8（覆盖 F-3.8 当地墙时间轴）。 */
    const val TZ_OFFSET_SEC = 8 * 3600

    /** 固定基准 epoch（值本身无特殊含义，测试只依赖各序列的相对关系；测试专用常量）。 */
    const val BASE_EPOCH_SEC = 1793366400L

    /** 天气序列默认 8 小时。 */
    const val WEATHER_HOURS = 8

    /** 海洋序列默认 5 小时（= 天气第 4~8 小时）。 */
    const val MARINE_HOURS = 5

    // ────────────────────────── B9 错误响应（附录 B9 实测） ──────────────────────────

    /** marine 传错 models（ncep_gfsocean016）的 400 响应。 */
    const val OPEN_METEO_400_B9_MODELS =
        """{"error":true,"reason":"Cannot initialize MultiDomains","version":"2026-01-01"}"""

    /** 潮位字段拼写错误（sea_level_height_mssh）的 400 响应。 */
    const val OPEN_METEO_400_B9_FIELD =
        """{"error":true,"reason":"Cannot initialize SurfacePressureAndHeightVariable on range 2026-10-26 to 2026-11-04","version":"2026-01-01"}"""

    /** 普通 400（非 B9 参数拼写错误）。 */
    const val OPEN_METEO_400_GENERIC =
        """{"error":true,"reason":"Latitude must be in range of [-90,90]","version":"2026-01-01"}"""

    /** NOAA 壳错误（§5.4 校正参考备用，验证 extractReason 兼容性）。 */
    const val NOAA_400_WRONG_TZ = """{"error":{"message":"Wrong Time Zone"}}"""

    // ────────────────────────── §5.1 天气夹具 ──────────────────────────

    /**
     * 天气响应：`temperature_2m[i]=10+i`、`wind_gusts_10m[i]=15+i` 等（值随时序变化以便断言对齐）。
     * @param ianaTz 注入 IANA `timezone` 字段（FO-18）；null = 不注入。
     * @param omitFields 从 hourly 中剔除的键（§10.2「部分字段缺失」态；`time` 不可剔除）。
     */
    fun weatherJson(
        hourCount: Int = WEATHER_HOURS,
        baseEpochSec: Long = BASE_EPOCH_SEC,
        tzOffsetSec: Int = TZ_OFFSET_SEC,
        ianaTz: String? = null,
        omitFields: Set<String> = emptySet(),
    ): String {
        fun dArr(f: (Int) -> Double?) = JsonArray((0 until hourCount).map { i ->
            f(i)?.let { JsonPrimitive(it) } ?: JsonNull
        })
        fun iArr(f: (Int) -> Int?) = JsonArray((0 until hourCount).map { i ->
            f(i)?.let { JsonPrimitive(it) } ?: JsonNull
        })
        fun has(f: String) = f !in omitFields
        val hourly = buildJsonObject {
            put("time", JsonArray((0 until hourCount).map { i ->
                JsonPrimitive(OpenMeteoTime.localIso(baseEpochSec + i * 3600L, tzOffsetSec))
            }))
            if (has("temperature_2m")) put("temperature_2m", dArr { 10.0 + it })
            if (has("relative_humidity_2m")) put("relative_humidity_2m", dArr { 60.0 + it })
            if (has("dew_point_2m")) put("dew_point_2m", dArr { 6.0 + it * 0.1 })
            if (has("apparent_temperature")) put("apparent_temperature", dArr { 9.0 + it })
            if (has("pressure_msl")) put("pressure_msl", dArr { 1013.0 + it * 0.1 })
            if (has("surface_pressure")) put("surface_pressure", dArr { 1000.0 + it * 0.1 })
            if (has("cloud_cover")) put("cloud_cover", dArr { 50.0 + it })
            if (has("cloud_cover_low")) put("cloud_cover_low", dArr { 10.0 + it })
            if (has("cloud_cover_mid")) put("cloud_cover_mid", dArr { 20.0 + it })
            if (has("cloud_cover_high")) put("cloud_cover_high", dArr { 30.0 + it })
            if (has("visibility")) put("visibility", dArr { 20.0 + it * 0.1 }) // km；映射层 ×1000 → m
            if (has("precipitation")) put("precipitation", dArr { if (it == 2) null else 0.5 }) // 显式 null，禁止当 0
            if (has("rain")) put("rain", dArr { 0.0 })
            if (has("showers")) put("showers", dArr { 0.0 })
            if (has("snowfall")) put("snowfall", dArr { 0.0 })
            if (has("weather_code")) put("weather_code", iArr { it % 4 })
            if (has("wind_speed_10m")) put("wind_speed_10m", dArr { 5.0 + it })
            if (has("wind_direction_10m")) put("wind_direction_10m", dArr { 90.0 + it * 10 })
            if (has("wind_gusts_10m")) put("wind_gusts_10m", dArr { 15.0 + it })
            if (has("cape")) put("cape", dArr { 100.0 + it })
            if (has("freezing_level_height")) put("freezing_level_height", dArr { 3000.0 + it * 10 })
        }
        val date = OpenMeteoTime.localIso(baseEpochSec, tzOffsetSec).substringBefore('T')
        val daily = buildJsonObject {
            put("time", JsonArray(listOf(JsonPrimitive(date))))
            put("sunrise", JsonArray(listOf(JsonPrimitive("${date}T06:30"))))
            put("sunset", JsonArray(listOf(JsonPrimitive("${date}T17:20"))))
            put("temperature_2m_max", JsonArray(listOf(JsonPrimitive(18.5))))
            put("temperature_2m_min", JsonArray(listOf(JsonPrimitive(9.0))))
            put("precipitation_sum", JsonArray(listOf(JsonPrimitive(0.0))))
            put("wind_speed_10m_max", JsonArray(listOf(JsonPrimitive(12.0))))
            put("wind_gusts_10m_max", JsonArray(listOf(JsonPrimitive(22.0))))
            put("weather_code", JsonArray(listOf(JsonPrimitive(1))))
        }
        return buildJsonObject {
            put("latitude", JsonPrimitive(36.06))
            put("longitude", JsonPrimitive(120.38))
            put("elevation", JsonPrimitive(12.0))
            put("utc_offset_seconds", JsonPrimitive(tzOffsetSec))
            if (ianaTz != null) put("timezone", JsonPrimitive(ianaTz))
            put("hourly", hourly)
            put("daily", daily)
        }.toString()
    }

    // ────────────────────────── §5.2 海洋夹具 ──────────────────────────

    /**
     * 海洋响应：`wave_height[i]=0.3+0.1i`、`sea_surface_temperature[i]=19.5-0.1i`。
     *
     * @param allNull 附录 B8 内陆点行为：HTTP 200 + 所有数组全 null。
     * @param ianaTz 注入 IANA `timezone` 字段（FO-18 必修 4：marine 补解析路径）。
     */
    fun marineJson(
        hourCount: Int = MARINE_HOURS,
        baseEpochSec: Long = BASE_EPOCH_SEC + 3 * 3600L,
        tzOffsetSec: Int = TZ_OFFSET_SEC,
        allNull: Boolean = false,
        ianaTz: String? = null,
    ): String {
        fun vals(f: (Int) -> Double): List<Double?> =
            if (allNull) List(hourCount) { null } else (0 until hourCount).map(f)
        fun arr(list: List<Double?>) = JsonArray(list.map { it?.let { v -> JsonPrimitive(v) } ?: JsonNull })
        val hourly = buildJsonObject {
            put("time", JsonArray((0 until hourCount).map { i ->
                JsonPrimitive(OpenMeteoTime.localIso(baseEpochSec + i * 3600L, tzOffsetSec))
            }))
            put("wave_height", arr(vals { 0.3 + it * 0.1 }))
            put("wave_period", arr(vals { 6.0 + it * 0.2 }))
            put("wave_direction", arr(vals { 120.0 + it }))
            put("wave_peak_period", arr(vals { 8.0 + it * 0.1 }))
            put("wind_wave_height", arr(vals { 0.2 + it * 0.05 }))
            put("wind_wave_period", arr(vals { 5.0 + it * 0.1 }))
            put("wind_wave_direction", arr(vals { 110.0 + it }))
            put("swell_wave_height", arr(vals { 0.25 + it * 0.05 }))
            put("swell_wave_period", arr(vals { 9.0 + it * 0.1 }))
            put("swell_wave_direction", arr(vals { 200.0 + it }))
            put("secondary_swell_wave_height", arr(vals { 0.1 }))
            put("tertiary_swell_wave_height", arr(vals { 0.05 }))
            put("ocean_current_velocity", arr(vals { 1.2 + it * 0.1 }))
            put("ocean_current_direction", arr(vals { 45.0 + it }))
            put("sea_level_height_msl", arr(vals { 0.17 + it * 0.05 }))
            put("sea_surface_temperature", arr(vals { 19.5 - it * 0.1 }))
        }
        return buildJsonObject {
            put("latitude", JsonPrimitive(29.6))
            put("longitude", JsonPrimitive(119.04))
            put("elevation", JsonPrimitive(99.0))
            put("utc_offset_seconds", JsonPrimitive(tzOffsetSec))
            if (ianaTz != null) put("timezone", JsonPrimitive(ianaTz))
            put("hourly", hourly)
        }.toString()
    }

    /** 空 hourly（极端防御：源返回缺 hourly 块）。 */
    fun emptyWeatherJson(tzOffsetSec: Int = TZ_OFFSET_SEC): String =
        JsonObject(emptyMap()).let {
            buildJsonObject {
                put("latitude", JsonPrimitive(36.06))
                put("longitude", JsonPrimitive(120.38))
                put("utc_offset_seconds", JsonPrimitive(tzOffsetSec))
            }
        }.toString()
}
