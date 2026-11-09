package com.tidespot.core.data.repo

import com.tidespot.core.data.dto.MarineHourlyDto
import com.tidespot.core.data.dto.MarineResponseDto
import com.tidespot.core.data.dto.OpenMeteoTime
import com.tidespot.core.data.dto.WeatherDailyDto
import com.tidespot.core.data.dto.WeatherHourlyDto
import com.tidespot.core.data.dto.WeatherResponseDto
import com.tidespot.core.data.local.HourlyMetricEntity
import com.tidespot.core.data.local.SpotEntity
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather
import com.tidespot.core.domain.model.WaterBodyType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** 源字段名常量（与 §5.1/§5.2 契约及 hourly_metric.field 一致）。 */
object WeatherFieldNames {
    const val TEMPERATURE_2M = "temperature_2m"
    const val RELATIVE_HUMIDITY_2M = "relative_humidity_2m"
    const val DEW_POINT_2M = "dew_point_2m"
    const val APPARENT_TEMPERATURE = "apparent_temperature"
    const val PRESSURE_MSL = "pressure_msl"
    const val SURFACE_PRESSURE = "surface_pressure"
    const val CLOUD_COVER = "cloud_cover"
    const val CLOUD_COVER_LOW = "cloud_cover_low"
    const val CLOUD_COVER_MID = "cloud_cover_mid"
    const val CLOUD_COVER_HIGH = "cloud_cover_high"
    const val VISIBILITY = "visibility"
    const val PRECIPITATION = "precipitation"
    const val RAIN = "rain"
    const val SHOWERS = "showers"
    const val SNOWFALL = "snowfall"
    const val WEATHER_CODE = "weather_code"
    const val WIND_SPEED_10M = "wind_speed_10m"
    const val WIND_DIRECTION_10M = "wind_direction_10m"
    const val WIND_GUSTS_10M = "wind_gusts_10m"
    const val CAPE = "cape"
    const val FREEZING_LEVEL_HEIGHT = "freezing_level_height"

    /** 21 项，与 OpenMeteoClient.WEATHER_HOURLY 一致（含 domain 模型未承载的湿度）。 */
    val ALL = listOf(
        TEMPERATURE_2M, RELATIVE_HUMIDITY_2M, DEW_POINT_2M, APPARENT_TEMPERATURE,
        PRESSURE_MSL, SURFACE_PRESSURE, CLOUD_COVER, CLOUD_COVER_LOW, CLOUD_COVER_MID,
        CLOUD_COVER_HIGH, VISIBILITY, PRECIPITATION, RAIN, SHOWERS, SNOWFALL,
        WEATHER_CODE, WIND_SPEED_10M, WIND_DIRECTION_10M, WIND_GUSTS_10M, CAPE,
        FREEZING_LEVEL_HEIGHT,
    )
}

object MarineFieldNames {
    const val WAVE_HEIGHT = "wave_height"
    const val WAVE_PERIOD = "wave_period"
    const val WAVE_DIRECTION = "wave_direction"
    const val WAVE_PEAK_PERIOD = "wave_peak_period"
    const val WIND_WAVE_HEIGHT = "wind_wave_height"
    const val WIND_WAVE_PERIOD = "wind_wave_period"
    const val WIND_WAVE_DIRECTION = "wind_wave_direction"
    const val SWELL_WAVE_HEIGHT = "swell_wave_height"
    const val SWELL_WAVE_PERIOD = "swell_wave_period"
    const val SWELL_WAVE_DIRECTION = "swell_wave_direction"
    const val SECONDARY_SWELL_WAVE_HEIGHT = "secondary_swell_wave_height"
    const val TERTIARY_SWELL_WAVE_HEIGHT = "tertiary_swell_wave_height"
    const val OCEAN_CURRENT_VELOCITY = "ocean_current_velocity"
    const val OCEAN_CURRENT_DIRECTION = "ocean_current_direction"
    const val SEA_LEVEL_HEIGHT_MSL = "sea_level_height_msl"
    const val SEA_SURFACE_TEMPERATURE = "sea_surface_temperature"

    /** 16 项，与 OpenMeteoClient.MARINE_HOURLY 一致。 */
    val ALL = listOf(
        WAVE_HEIGHT, WAVE_PERIOD, WAVE_DIRECTION, WAVE_PEAK_PERIOD,
        WIND_WAVE_HEIGHT, WIND_WAVE_PERIOD, WIND_WAVE_DIRECTION,
        SWELL_WAVE_HEIGHT, SWELL_WAVE_PERIOD, SWELL_WAVE_DIRECTION,
        SECONDARY_SWELL_WAVE_HEIGHT, TERTIARY_SWELL_WAVE_HEIGHT,
        OCEAN_CURRENT_VELOCITY, OCEAN_CURRENT_DIRECTION,
        SEA_LEVEL_HEIGHT_MSL, SEA_SURFACE_TEMPERATURE,
    )
}

private fun List<Double?>?.at(i: Int): Double? = this?.getOrNull(i)
private fun List<Int?>?.at(i: Int): Int? = this?.getOrNull(i)

/**
 * DTO ↔ domain ↔ entity 映射。
 *
 * 单位规则（F-4.4 + CONTRACT 冻结点 3）：内部 SI——本层唯一换算点：
 * - `visibility`：源单位 km（Open-Meteo 默认）→ 统一为 m（domain 字段 visibilityM）；
 * - 其余字段（°C / km/h / hPa / m / mm / %）与源一致，不换算；
 * - null 一律保真传递，**禁止 0 填充**（§5.0）。
 */
object DtoMappers {

    /**
     * CHG-09⑥（NEW-06）：utcOffsetSeconds 缺失时回退 IANA timezone 推算偏移
     * （`ZoneId.of(id).rules.getOffset(Instant.now())`）；两者都缺 → null（调用方整窗跳过，
     * 同脏时间串语义）。KDoc 留痕：M4 将整体 ZoneId 化（附加必修 4），此处为止血版。
     */
    private fun WeatherResponseDto.resolveUtcOffsetSec(): Int? =
        utcOffsetSeconds ?: timezoneId?.let { id ->
            runCatching {
                java.time.ZoneId.of(id).rules.getOffset(java.time.Instant.now()).totalSeconds
            }.getOrNull()
        }

    /** §5.1 → [HourlyWeather]（按 hourly.time 同下标解包，越界/缺失 = null）。 */
    fun toHourlyWeatherList(dto: WeatherResponseDto): List<HourlyWeather> {
        val h: WeatherHourlyDto = dto.hourly ?: return emptyList()
        // CHG-09⑥（NEW-06，§5.0 同型）：缺失时回退 IANA timezone 推算；两者都缺 → 整窗跳过（禁当 0）
        val offset = dto.resolveUtcOffsetSec() ?: return emptyList()
        // BLK-06/N-4（CHG-07 修复 4）：时间解析失败 → 跳过该小时（影响最小选择：时间戳是 §5.0
        // join 主键，映射 null 会破坏时间轴；整行跳过等价于该小时缺测，不影响其余小时）
        return h.time.mapIndexedNotNull { i, local ->
            val epoch = OpenMeteoTime.parseEpochSecOrNull(local, offset) ?: return@mapIndexedNotNull null
            weatherAt(h, i, epoch)
        }
    }

    /** hourly.time[i] 的逐字段映射（供列表映射与整窗入库共用，保证跳行后不错位）。 */
    private fun weatherAt(h: WeatherHourlyDto, i: Int, epoch: Long): HourlyWeather =
        HourlyWeather(
            timeEpochSec = epoch,
            temperatureC = h.temperature2m.at(i),
            dewPointC = h.dewPoint2m.at(i),
            apparentC = h.apparentTemperature.at(i),
            pressureMslHpa = h.pressureMsl.at(i),
            surfacePressureHpa = h.surfacePressure.at(i),
            cloudCoverPct = h.cloudCover.at(i),
            cloudLowPct = h.cloudCoverLow.at(i),
            cloudMidPct = h.cloudCoverMid.at(i),
            cloudHighPct = h.cloudCoverHigh.at(i),
            visibilityM = h.visibility.at(i)?.let { it * 1000.0 }, // km → m（SI）
            precipMm = h.precipitation.at(i),
            rainMm = h.rain.at(i),
            showersMm = h.showers.at(i),
            snowMm = h.snowfall.at(i),
            weatherCode = h.weatherCode.at(i),
            windSpeedKmh = h.windSpeed10m.at(i),
            windDirDeg = h.windDirection10m.at(i),
            windGustKmh = h.windGusts10m.at(i),
            cape = h.cape.at(i),
            freezingLevelM = h.freezingLevelHeight.at(i),
        )

    /** FO-18（M4 必修 4）：marine 同天气侧——utcOffsetSeconds 优先，缺失回退 IANA timezone 推算。 */
    private fun MarineResponseDto.resolveUtcOffsetSec(): Int? =
        utcOffsetSeconds ?: timezoneId?.let { id ->
            runCatching {
                java.time.ZoneId.of(id).rules.getOffset(java.time.Instant.now()).totalSeconds
            }.getOrNull()
        }

    /** §5.2 → [HourlyMarine]（内陆点全 null 原样保真，isAllNull 由 domain 判定）。 */
    fun toHourlyMarineList(dto: MarineResponseDto): List<HourlyMarine> {
        val h = dto.hourly ?: return emptyList()
        // FO-18（M4 必修 4）：UTC 缺失回退 IANA；两者都缺 → 整窗跳过（禁当 0）
        val offset = dto.resolveUtcOffsetSec() ?: return emptyList()
        // BLK-06/N-4（CHG-07 修复 4）：时间解析失败 → 跳过该小时（与天气侧同语义）
        return h.time.mapIndexedNotNull { i, local ->
            val epoch = OpenMeteoTime.parseEpochSecOrNull(local, offset) ?: return@mapIndexedNotNull null
            marineAt(h, i, epoch)
        }
    }

    /** hourly.time[i] 的逐字段映射（供列表映射与整窗入库共用）。 */
    private fun marineAt(h: MarineHourlyDto, i: Int, epoch: Long): HourlyMarine =
        HourlyMarine(
            timeEpochSec = epoch,
            waveHeightM = h.waveHeight.at(i),
            wavePeriodS = h.wavePeriod.at(i),
            waveDirectionDeg = h.waveDirection.at(i),
            wavePeakPeriodS = h.wavePeakPeriod.at(i),
            windWaveHeightM = h.windWaveHeight.at(i),
            windWavePeriodS = h.windWavePeriod.at(i),
            windWaveDirectionDeg = h.windWaveDirection.at(i),
            swellWaveHeightM = h.swellWaveHeight.at(i),
            swellWavePeriodS = h.swellWavePeriod.at(i),
            swellWaveDirectionDeg = h.swellWaveDirection.at(i),
            secondarySwellHeightM = h.secondarySwellWaveHeight.at(i),
            tertiarySwellHeightM = h.tertiarySwellWaveHeight.at(i),
            oceanCurrentVelocityKmh = h.oceanCurrentVelocity.at(i),
            oceanCurrentDirectionDeg = h.oceanCurrentDirection.at(i),
            seaLevelHeightMslM = h.seaLevelHeightMsl.at(i),
            seaSurfaceTempC = h.seaSurfaceTemperature.at(i),
        )

    /** 整窗入库（CHG-03）：DTO 直接落 hourly_metric（含 domain 未承载的湿度）。 */
    fun weatherEntities(
        spotId: Long,
        dto: WeatherResponseDto,
        fetchedAtMillis: Long,
        source: String = "open-meteo",
    ): List<HourlyMetricEntity> {
        val h = dto.hourly ?: return emptyList()
        // CHG-09⑥：同 toHourlyWeatherList——回退 IANA，两者都缺 → 整窗跳过（禁当 0）
        val offset = dto.resolveUtcOffsetSec() ?: return emptyList()
        val rows = ArrayList<HourlyMetricEntity>(h.time.size * WeatherFieldNames.ALL.size)
        h.time.forEachIndexed { i, local ->
            // BLK-06/N-4（CHG-07 修复 4）：脏时间串 → 跳过该小时整块（不抛 DateTimeParseException）；
            // 逐小时独立映射（不复用过滤后的列表），保证 i 与源数组对齐、跳行不错位
            val epoch = OpenMeteoTime.parseEpochSecOrNull(local, offset) ?: return@forEachIndexed
            val v = weatherAt(h, i, epoch)
            fun row(field: String, value: Double?) =
                HourlyMetricEntity(spotId, epoch, local, DataSource.WEATHER.wire, field, value, source, fetchedAtMillis)

            rows += row(WeatherFieldNames.TEMPERATURE_2M, v?.temperatureC)
            rows += row(WeatherFieldNames.RELATIVE_HUMIDITY_2M, h.relativeHumidity2m.at(i))
            rows += row(WeatherFieldNames.DEW_POINT_2M, v?.dewPointC)
            rows += row(WeatherFieldNames.APPARENT_TEMPERATURE, v?.apparentC)
            rows += row(WeatherFieldNames.PRESSURE_MSL, v?.pressureMslHpa)
            rows += row(WeatherFieldNames.SURFACE_PRESSURE, v?.surfacePressureHpa)
            rows += row(WeatherFieldNames.CLOUD_COVER, v?.cloudCoverPct)
            rows += row(WeatherFieldNames.CLOUD_COVER_LOW, v?.cloudLowPct)
            rows += row(WeatherFieldNames.CLOUD_COVER_MID, v?.cloudMidPct)
            rows += row(WeatherFieldNames.CLOUD_COVER_HIGH, v?.cloudHighPct)
            rows += row(WeatherFieldNames.VISIBILITY, v?.visibilityM)
            rows += row(WeatherFieldNames.PRECIPITATION, v?.precipMm)
            rows += row(WeatherFieldNames.RAIN, v?.rainMm)
            rows += row(WeatherFieldNames.SHOWERS, v?.showersMm)
            rows += row(WeatherFieldNames.SNOWFALL, v?.snowMm)
            rows += row(WeatherFieldNames.WEATHER_CODE, v?.weatherCode?.toDouble())
            rows += row(WeatherFieldNames.WIND_SPEED_10M, v?.windSpeedKmh)
            rows += row(WeatherFieldNames.WIND_DIRECTION_10M, v?.windDirDeg)
            rows += row(WeatherFieldNames.WIND_GUSTS_10M, v?.windGustKmh)
            rows += row(WeatherFieldNames.CAPE, v?.cape)
            rows += row(WeatherFieldNames.FREEZING_LEVEL_HEIGHT, v?.freezingLevelM)
        }
        return rows
    }

    /** 海洋整窗入库（16 字段 × 每小时，含显式 null 行，保真 §5.0 null 语义）。 */
    fun marineEntities(
        spotId: Long,
        dto: MarineResponseDto,
        fetchedAtMillis: Long,
        source: String = "open-meteo",
    ): List<HourlyMetricEntity> {
        val h = dto.hourly ?: return emptyList()
        // FO-18（M4 必修 4）：同 toHourlyMarineList——UTC 缺失回退 IANA；两者都缺 → 整窗跳过（禁当 0）
        val offset = dto.resolveUtcOffsetSec() ?: return emptyList()
        val rows = ArrayList<HourlyMetricEntity>(h.time.size * MarineFieldNames.ALL.size)
        h.time.forEachIndexed { i, local ->
            // BLK-06/N-4（CHG-07 修复 4）：脏时间串 → 跳过该小时整块（与天气侧同语义）
            val epoch = OpenMeteoTime.parseEpochSecOrNull(local, offset) ?: return@forEachIndexed
            val v = marineAt(h, i, epoch)
            fun row(field: String, value: Double?) =
                HourlyMetricEntity(spotId, epoch, local, DataSource.MARINE.wire, field, value, source, fetchedAtMillis)

            rows += row(MarineFieldNames.WAVE_HEIGHT, v?.waveHeightM)
            rows += row(MarineFieldNames.WAVE_PERIOD, v?.wavePeriodS)
            rows += row(MarineFieldNames.WAVE_DIRECTION, v?.waveDirectionDeg)
            rows += row(MarineFieldNames.WAVE_PEAK_PERIOD, v?.wavePeakPeriodS)
            rows += row(MarineFieldNames.WIND_WAVE_HEIGHT, v?.windWaveHeightM)
            rows += row(MarineFieldNames.WIND_WAVE_PERIOD, v?.windWavePeriodS)
            rows += row(MarineFieldNames.WIND_WAVE_DIRECTION, v?.windWaveDirectionDeg)
            rows += row(MarineFieldNames.SWELL_WAVE_HEIGHT, v?.swellWaveHeightM)
            rows += row(MarineFieldNames.SWELL_WAVE_PERIOD, v?.swellWavePeriodS)
            rows += row(MarineFieldNames.SWELL_WAVE_DIRECTION, v?.swellWaveDirectionDeg)
            rows += row(MarineFieldNames.SECONDARY_SWELL_WAVE_HEIGHT, v?.secondarySwellHeightM)
            rows += row(MarineFieldNames.TERTIARY_SWELL_WAVE_HEIGHT, v?.tertiarySwellHeightM)
            rows += row(MarineFieldNames.OCEAN_CURRENT_VELOCITY, v?.oceanCurrentVelocityKmh)
            rows += row(MarineFieldNames.OCEAN_CURRENT_DIRECTION, v?.oceanCurrentDirectionDeg)
            rows += row(MarineFieldNames.SEA_LEVEL_HEIGHT_MSL, v?.seaLevelHeightMslM)
            rows += row(MarineFieldNames.SEA_SURFACE_TEMPERATURE, v?.seaSurfaceTempC)
        }
        return rows
    }
}

/** hourly_metric 行 → domain 序列重建（离线回看 C7 / F-2.3 缓存渲染 / 重试失败回退旧数据）。 */
object EntityMappers {

    fun toHourlyWeather(rows: List<HourlyMetricEntity>): List<HourlyWeather> =
        rows.asSequence()
            .filter { it.kind == DataSource.WEATHER.wire }
            .groupBy { it.hourEpoch }
            .map { (epoch, rs) ->
                fun v(field: String) = rs.firstOrNull { it.field == field }?.value
                HourlyWeather(
                    timeEpochSec = epoch,
                    temperatureC = v(WeatherFieldNames.TEMPERATURE_2M),
                    dewPointC = v(WeatherFieldNames.DEW_POINT_2M),
                    apparentC = v(WeatherFieldNames.APPARENT_TEMPERATURE),
                    pressureMslHpa = v(WeatherFieldNames.PRESSURE_MSL),
                    surfacePressureHpa = v(WeatherFieldNames.SURFACE_PRESSURE),
                    cloudCoverPct = v(WeatherFieldNames.CLOUD_COVER),
                    cloudLowPct = v(WeatherFieldNames.CLOUD_COVER_LOW),
                    cloudMidPct = v(WeatherFieldNames.CLOUD_COVER_MID),
                    cloudHighPct = v(WeatherFieldNames.CLOUD_COVER_HIGH),
                    visibilityM = v(WeatherFieldNames.VISIBILITY),
                    precipMm = v(WeatherFieldNames.PRECIPITATION),
                    rainMm = v(WeatherFieldNames.RAIN),
                    showersMm = v(WeatherFieldNames.SHOWERS),
                    snowMm = v(WeatherFieldNames.SNOWFALL),
                    weatherCode = v(WeatherFieldNames.WEATHER_CODE)?.roundToInt(),
                    windSpeedKmh = v(WeatherFieldNames.WIND_SPEED_10M),
                    windDirDeg = v(WeatherFieldNames.WIND_DIRECTION_10M),
                    windGustKmh = v(WeatherFieldNames.WIND_GUSTS_10M),
                    cape = v(WeatherFieldNames.CAPE),
                    freezingLevelM = v(WeatherFieldNames.FREEZING_LEVEL_HEIGHT),
                )
            }
            .sortedBy { it.timeEpochSec }

    fun toHourlyMarine(rows: List<HourlyMetricEntity>): List<HourlyMarine> =
        rows.asSequence()
            .filter { it.kind == DataSource.MARINE.wire }
            .groupBy { it.hourEpoch }
            .map { (epoch, rs) ->
                fun v(field: String) = rs.firstOrNull { it.field == field }?.value
                HourlyMarine(
                    timeEpochSec = epoch,
                    waveHeightM = v(MarineFieldNames.WAVE_HEIGHT),
                    wavePeriodS = v(MarineFieldNames.WAVE_PERIOD),
                    waveDirectionDeg = v(MarineFieldNames.WAVE_DIRECTION),
                    wavePeakPeriodS = v(MarineFieldNames.WAVE_PEAK_PERIOD),
                    windWaveHeightM = v(MarineFieldNames.WIND_WAVE_HEIGHT),
                    windWavePeriodS = v(MarineFieldNames.WIND_WAVE_PERIOD),
                    windWaveDirectionDeg = v(MarineFieldNames.WIND_WAVE_DIRECTION),
                    swellWaveHeightM = v(MarineFieldNames.SWELL_WAVE_HEIGHT),
                    swellWavePeriodS = v(MarineFieldNames.SWELL_WAVE_PERIOD),
                    swellWaveDirectionDeg = v(MarineFieldNames.SWELL_WAVE_DIRECTION),
                    secondarySwellHeightM = v(MarineFieldNames.SECONDARY_SWELL_WAVE_HEIGHT),
                    tertiarySwellHeightM = v(MarineFieldNames.TERTIARY_SWELL_WAVE_HEIGHT),
                    oceanCurrentVelocityKmh = v(MarineFieldNames.OCEAN_CURRENT_VELOCITY),
                    oceanCurrentDirectionDeg = v(MarineFieldNames.OCEAN_CURRENT_DIRECTION),
                    seaLevelHeightMslM = v(MarineFieldNames.SEA_LEVEL_HEIGHT_MSL),
                    seaSurfaceTempC = v(MarineFieldNames.SEA_SURFACE_TEMPERATURE),
                )
            }
            .sortedBy { it.timeEpochSec }
}

// 收藏实体 ↔ domain（顶级扩展，供仓储直接使用；tags 以 JSON 数组串持久化）
private val spotJson = Json { ignoreUnknownKeys = true }
private val tagsSerializer = ListSerializer(String.serializer())

fun SpotEntity.toDomain(): FavoriteSpot = FavoriteSpot(
    id = id,
    name = name,
    lat = lat,
    lon = lon,
    memo = note,
    preset = groupPreset,
    tags = runCatching { spotJson.decodeFromString(tagsSerializer, tagsJson) }.getOrDefault(emptyList()),
    waterBody = runCatching { WaterBodyType.valueOf(waterBodyType) }.getOrDefault(WaterBodyType.LAND),
    createdAtEpochSec = createdAtEpochSec,
    // M5（CHG-14）：最近查看时刻映射（F-2.5 列表摘要排序依据）
    lastViewedAtEpochSec = lastViewedAt,
    // M6（CHG-16⑨/OPT-M5-03）：选点时区偏移秒映射（列已存在；编辑路径由 D07 patch 合并保护）
    tzOffsetSec = tzOffsetSec,
)

fun FavoriteSpot.toEntity(nowEpochSec: Long): SpotEntity = SpotEntity(
    id = id,
    lat = lat,
    lon = lon,
    name = name,
    waterBodyType = waterBody.name,
    note = memo,
    groupPreset = preset,
    tagsJson = spotJson.encodeToString(tagsSerializer, tags),
    updatedAt = nowEpochSec,
    // M5（CHG-14）：lastViewedAt 随 domain 映射；upsertFavorite 更新路径由 D07 patch 合并保护（不被缺省 0 冲掉）
    lastViewedAt = lastViewedAtEpochSec,
    // M6（CHG-16⑨）：tzOffsetSec 随 domain 映射；更新路径同样由 D07 patch 合并清单保护
    //（elevation/tzOffsetSec/speciesId 等派生列以库内旧值为准，toEntity 携带值不回退派生列）
    tzOffsetSec = tzOffsetSec,
    createdAtEpochSec = if (createdAtEpochSec > 0) createdAtEpochSec else nowEpochSec,
)
