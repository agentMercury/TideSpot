/**
 * 与 Android `core/domain/model/Models.kt` 对齐的类型（只增不改语义）。
 * 单位约定：内部一律 SI —— °C / km/h / m / hPa / epoch 秒（UTC）。
 */

export type Confidence = 'MEASURED' | 'SATELLITE' | 'MODEL' | 'ESTIMATED' | 'UNAVAILABLE';

/** 水体类型（SRS §6.9 判定输出） */
export type WaterBodyType = 'OCEAN' | 'NEARSHORE' | 'INLAND_WATER' | 'LAND' | 'UNKNOWN';

export type VetoCode = 'THUNDERSTORM' | 'FISHABILITY_DECAY';

export type FactorId =
  | 'OXYGEN'
  | 'PRESSURE'
  | 'SOLUNAR'
  | 'TIDE'
  | 'WIND'
  | 'WEATHER'
  | 'DAYPART'
  | 'TEMP_MATCH'
  | 'COMFORT'
  | 'WATER_QUALITY';

export interface HourlyWeather {
  timeEpochSec: number;
  temperatureC?: number | null;
  dewPointC?: number | null;
  apparentC?: number | null;
  pressureMslHpa?: number | null;
  surfacePressureHpa?: number | null;
  cloudCoverPct?: number | null;
  cloudLowPct?: number | null;
  cloudMidPct?: number | null;
  cloudHighPct?: number | null;
  visibilityM?: number | null;
  precipMm?: number | null;
  rainMm?: number | null;
  showersMm?: number | null;
  snowMm?: number | null;
  weatherCode?: number | null;
  windSpeedKmh?: number | null;
  windDirDeg?: number | null;
  windGustKmh?: number | null;
  cape?: number | null;
  freezingLevelM?: number | null;
}

export interface HourlyMarine {
  timeEpochSec: number;
  waveHeightM?: number | null;
  wavePeriodS?: number | null;
  waveDirectionDeg?: number | null;
  wavePeakPeriodS?: number | null;
  windWaveHeightM?: number | null;
  windWavePeriodS?: number | null;
  windWaveDirectionDeg?: number | null;
  swellWaveHeightM?: number | null;
  swellWavePeriodS?: number | null;
  swellWaveDirectionDeg?: number | null;
  secondarySwellHeightM?: number | null;
  tertiarySwellHeightM?: number | null;
  oceanCurrentVelocityKmh?: number | null;
  oceanCurrentDirectionDeg?: number | null;
  seaLevelHeightMslM?: number | null;
  seaSurfaceTempC?: number | null;
}

export interface FactorScore {
  id: string;
  /** 0–1 子分 */
  sub: number;
  weight: number;
  detail?: string | null;
}

export interface FishScoreResult {
  timeEpochSec: number;
  total: number;
  factors: FactorScore[];
  vetoReasons: string[];
  confidence: Confidence;
  vetoCodes: VetoCode[];
}

export interface ScenePreset {
  id: string;
  label: string;
  /** 盐度 ‰：淡水 0 / 海水 30 */
  salinityPpt: number;
  nightTarget: boolean;
  tempOptC: number;
  tempSigmaC: number;
  doBase: number;
  weights: Partial<Record<FactorId, number>>;
}

export interface HourlyScoreInput {
  timeEpochSec: number;
  weather: HourlyWeather;
  marine?: HourlyMarine | null;
  sunriseLocalHour?: number | null;
  sunsetLocalHour?: number | null;
  tzOffsetHours: number;
}

export function weightSum(preset: ScenePreset): number {
  return Object.values(preset.weights).reduce<number>((acc, w) => acc + (w ?? 0), 0);
}

/** Kotlin `roundToInt`（四舍五入，正数语义） */
export function roundToInt(x: number): number {
  return Math.round(x);
}