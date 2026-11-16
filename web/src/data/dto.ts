/**
 * 与 Android `core/data/dto/OpenMeteoDtos.kt` 对齐的 DTO 与映射。
 * 源缺失一律 null，禁止 0 填充（SRS §5.0）；时间以 epoch 秒为唯一内部口径。
 */
import type { HourlyMarine, HourlyWeather } from '../domain/types';

export interface WeatherHourlyDto {
  time: string[];
  temperature_2m?: Array<number | null> | null;
  relative_humidity_2m?: Array<number | null> | null;
  dew_point_2m?: Array<number | null> | null;
  apparent_temperature?: Array<number | null> | null;
  pressure_msl?: Array<number | null> | null;
  surface_pressure?: Array<number | null> | null;
  cloud_cover?: Array<number | null> | null;
  cloud_cover_low?: Array<number | null> | null;
  cloud_cover_mid?: Array<number | null> | null;
  cloud_cover_high?: Array<number | null> | null;
  visibility?: Array<number | null> | null;
  precipitation?: Array<number | null> | null;
  rain?: Array<number | null> | null;
  showers?: Array<number | null> | null;
  snowfall?: Array<number | null> | null;
  weather_code?: Array<number | null> | null;
  wind_speed_10m?: Array<number | null> | null;
  wind_direction_10m?: Array<number | null> | null;
  wind_gusts_10m?: Array<number | null> | null;
  cape?: Array<number | null> | null;
  freezing_level_height?: Array<number | null> | null;
}

export interface WeatherDailyDto {
  time: string[];
  sunrise?: Array<string | null> | null;
  sunset?: Array<string | null> | null;
  temperature_2m_max?: Array<number | null> | null;
  temperature_2m_min?: Array<number | null> | null;
  precipitation_sum?: Array<number | null> | null;
  wind_speed_10m_max?: Array<number | null> | null;
  wind_gusts_10m_max?: Array<number | null> | null;
  weather_code?: Array<number | null> | null;
}

export interface WeatherResponseDto {
  latitude?: number | null;
  longitude?: number | null;
  elevation?: number | null;
  utc_offset_seconds?: number | null;
  timezone?: string | null;
  hourly?: WeatherHourlyDto | null;
  daily?: WeatherDailyDto | null;
}

export interface MarineHourlyDto {
  time: string[];
  wave_height?: Array<number | null> | null;
  wave_period?: Array<number | null> | null;
  wave_direction?: Array<number | null> | null;
  wave_peak_period?: Array<number | null> | null;
  wind_wave_height?: Array<number | null> | null;
  wind_wave_period?: Array<number | null> | null;
  wind_wave_direction?: Array<number | null> | null;
  swell_wave_height?: Array<number | null> | null;
  swell_wave_period?: Array<number | null> | null;
  swell_wave_direction?: Array<number | null> | null;
  secondary_swell_wave_height?: Array<number | null> | null;
  tertiary_swell_wave_height?: Array<number | null> | null;
  ocean_current_velocity?: Array<number | null> | null;
  ocean_current_direction?: Array<number | null> | null;
  sea_level_height_msl?: Array<number | null> | null;
  sea_surface_temperature?: Array<number | null> | null;
}

export interface MarineResponseDto {
  latitude?: number | null;
  longitude?: number | null;
  elevation?: number | null;
  utc_offset_seconds?: number | null;
  timezone?: string | null;
  hourly?: MarineHourlyDto | null;
}

/** `2026-11-02T17:00` + 时区偏移秒 → epoch 秒 */
export function parseEpochSec(localIso: string, utcOffsetSec: number): number {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(localIso.trim());
  if (!m) throw new Error(`invalid local time: ${localIso}`);
  const ms = Date.UTC(
    Number(m[1]),
    Number(m[2]) - 1,
    Number(m[3]),
    Number(m[4]),
    Number(m[5]),
    m[6] ? Number(m[6]) : 0,
  );
  return Math.trunc(ms / 1000) - utcOffsetSec;
}

export function parseEpochSecOrNull(localIso: string | null | undefined, utcOffsetSec: number): number | null {
  if (localIso == null || localIso === '') return null;
  try {
    return parseEpochSec(localIso, utcOffsetSec);
  } catch {
    return null;
  }
}

function at<T>(arr: Array<T | null> | null | undefined, i: number): T | null {
  if (arr == null) return null;
  const v = arr[i];
  return v === undefined ? null : v;
}

/** 能见度源单位 km → 内部 m（F-4.4 / SI） */
function kmToM(v: number | null): number | null {
  return v == null ? null : v * 1000;
}

/** §5.1 hourly 数组 → 内部逐小时（时间解析失败的整点跳过） */
export function mapWeatherHourly(dto: WeatherResponseDto): HourlyWeather[] {
  const hourly = dto.hourly;
  if (!hourly || !Array.isArray(hourly.time)) return [];
  const offset = dto.utc_offset_seconds ?? 0;

  const out: HourlyWeather[] = [];
  for (let i = 0; i < hourly.time.length; i += 1) {
    const epoch = parseEpochSecOrNull(hourly.time[i], offset);
    if (epoch == null) continue;
    out.push({
      timeEpochSec: epoch,
      temperatureC: at(hourly.temperature_2m, i),
      dewPointC: at(hourly.dew_point_2m, i),
      apparentC: at(hourly.apparent_temperature, i),
      pressureMslHpa: at(hourly.pressure_msl, i),
      surfacePressureHpa: at(hourly.surface_pressure, i),
      cloudCoverPct: at(hourly.cloud_cover, i),
      cloudLowPct: at(hourly.cloud_cover_low, i),
      cloudMidPct: at(hourly.cloud_cover_mid, i),
      cloudHighPct: at(hourly.cloud_cover_high, i),
      visibilityM: kmToM(at(hourly.visibility, i)),
      precipMm: at(hourly.precipitation, i),
      rainMm: at(hourly.rain, i),
      showersMm: at(hourly.showers, i),
      snowMm: at(hourly.snowfall, i),
      weatherCode: at(hourly.weather_code, i),
      windSpeedKmh: at(hourly.wind_speed_10m, i),
      windDirDeg: at(hourly.wind_direction_10m, i),
      windGustKmh: at(hourly.wind_gusts_10m, i),
      cape: at(hourly.cape, i),
      freezingLevelM: at(hourly.freezing_level_height, i),
    });
  }
  return out;
}

/** §5.2 hourly 数组 → 内部逐小时（含海温/潮位） */
export function mapMarineHourly(dto: MarineResponseDto): HourlyMarine[] {
  const hourly = dto.hourly;
  if (!hourly || !Array.isArray(hourly.time)) return [];
  const offset = dto.utc_offset_seconds ?? 0;

  const out: HourlyMarine[] = [];
  for (let i = 0; i < hourly.time.length; i += 1) {
    const epoch = parseEpochSecOrNull(hourly.time[i], offset);
    if (epoch == null) continue;
    out.push({
      timeEpochSec: epoch,
      waveHeightM: at(hourly.wave_height, i),
      wavePeriodS: at(hourly.wave_period, i),
      waveDirectionDeg: at(hourly.wave_direction, i),
      wavePeakPeriodS: at(hourly.wave_peak_period, i),
      windWaveHeightM: at(hourly.wind_wave_height, i),
      windWavePeriodS: at(hourly.wind_wave_period, i),
      windWaveDirectionDeg: at(hourly.wind_wave_direction, i),
      swellWaveHeightM: at(hourly.swell_wave_height, i),
      swellWavePeriodS: at(hourly.swell_wave_period, i),
      swellWaveDirectionDeg: at(hourly.swell_wave_direction, i),
      secondarySwellHeightM: at(hourly.secondary_swell_wave_height, i),
      tertiarySwellHeightM: at(hourly.tertiary_swell_wave_height, i),
      oceanCurrentVelocityKmh: at(hourly.ocean_current_velocity, i),
      oceanCurrentDirectionDeg: at(hourly.ocean_current_direction, i),
      seaLevelHeightMslM: at(hourly.sea_level_height_msl, i),
      seaSurfaceTempC: at(hourly.sea_surface_temperature, i),
    });
  }
  return out;
}

/** §6.9 内陆判定：marine 全 null（§5.2 附录 B8） */
export function isMarineAllNull(marine: HourlyMarine): boolean {
  return (
    marine.waveHeightM == null &&
    marine.wavePeriodS == null &&
    marine.seaLevelHeightMslM == null &&
    marine.oceanCurrentVelocityKmh == null &&
    marine.seaSurfaceTempC == null
  );
}