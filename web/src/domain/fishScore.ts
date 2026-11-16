/**
 * 与 Android `FishScoreCalculator.kt` 逐行等价（SRS §6）。
 *
 * 输入契约：内部 SI 单位；源缺失 = null（禁止 0 填充，§5.0）；
 * null 因子从加权分母剔除（§6.4），剔除后有效权重 <0.6 → 低置信（§6.7）。
 */
import { clip01 } from './mathkit';
import { lunarAge, lunarTransitClock, score as solunarScore } from './solunar';
import * as sub from './subScores';
import type {
  Confidence,
  FactorScore,
  FishScoreResult,
  HourlyMarine,
  HourlyScoreInput,
  HourlyWeather,
  ScenePreset,
} from './types';
import { roundToInt, weightSum } from './types';
import { evaluate as evaluateVeto } from './veto';
import { doPotential, doRef, oxygenScore } from './water/doPhysics';
import { fuse } from './water/waterTemp';
import { waterQualityScore } from './water/waterQuality';

export const ALGO_VERSION = 1;

/** 缺 past 小时（无法计算 ΔP3）的 pressure 降级权重（§6.7：0.14 → 0.08） */
const MISSING_TREND_PRESSURE_WEIGHT = 0.08;

function localHourOf(epochSec: number, tzOffsetHours: number): number {
  const d = new Date((epochSec + tzOffsetHours * 3600) * 1000);
  return d.getUTCHours() + d.getUTCMinutes() / 60.0;
}

/** §6.2j / FO-15：当前小时【前 24h】降水累计（mm）；窗口内无任何样本 → null */
function rain24hSum(index: number, all: ReadonlyArray<HourlyScoreInput>): number | null {
  let sum = 0;
  let any = false;
  for (let k = Math.max(0, index - 24); k < index; k += 1) {
    const w = all[k].weather;
    const parts = [w.rainMm, w.showersMm].filter((v): v is number => v != null);
    if (parts.length > 0) {
      any = true;
      sum += parts.reduce((a, b) => a + b, 0);
    }
  }
  return any ? sum : null;
}

function scoreOne(
  input: HourlyScoreInput,
  index: number,
  preset: ScenePreset,
  lonDeg: number,
  amp: number,
  dh: number,
  airTemps: ReadonlyArray<number | null | undefined>,
  pressures: ReadonlyArray<number | null | undefined>,
  all: ReadonlyArray<HourlyScoreInput>,
): FishScoreResult {
  const w: HourlyWeather = input.weather;
  const m: HourlyMarine | null | undefined = input.marine;

  const hourLocal = localHourOf(input.timeEpochSec, input.tzOffsetHours);

  // §6.1 水温融合 Tw
  const sst = m?.seaSurfaceTempC;
  const tw = fuse(airTemps, index, w.temperatureC, sst);

  // §6.2d 气压趋势（缺 past → dP3=null + 权重降级）
  const p = w.pressureMslHpa;
  const prevP3 = index >= 3 ? pressures[index - 3] : null;
  const trendMissing = p != null && (index < 3 || prevP3 == null);
  const dP3: number | null = p == null || trendMissing ? null : p - (prevP3 as number);
  const dP24 = p != null && index >= 24 ? p - (pressures[index - 24] ?? p) : 0;

  // §6.2a 溶氧
  const doP = tw != null && p != null ? doPotential(tw, p, preset.salinityPpt) : null;
  const sOxygen = doP != null && tw != null ? oxygenScore(doP, doRef(tw, preset.doBase)) : null;

  // §6.2c Solunar
  const age = lunarAge(input.timeEpochSec * 1000);
  const transit = lunarTransitClock(age, lonDeg, input.tzOffsetHours);
  const sSolunar = solunarScore(hourLocal, transit, age);

  // 其余子分（null → 从分母剔除）
  const rainParts = [w.rainMm, w.showersMm].filter((v): v is number => v != null);
  const rain: number | null = rainParts.length === 0 ? null : rainParts.reduce((a, b) => a + b, 0);

  const sPressure = p != null ? sub.pressure(p, dP3, dP24) : null;
  const sWind = w.windSpeedKmh != null ? sub.wind(w.windSpeedKmh, w.windGustKmh) : null;
  const sWeather =
    w.weatherCode != null ? sub.weather(w.weatherCode, rain, w.cloudCoverPct) : null;
  const sDaypart = sub.daypart(hourLocal, input.sunriseLocalHour, input.sunsetLocalHour, preset.nightTarget);
  const sTempMatch = tw != null ? sub.tempMatch(tw, preset.tempOptC, preset.tempSigmaC) : null;
  const sComfort = w.apparentC != null ? sub.comfort(w.apparentC) : null;

  const sl = m?.seaLevelHeightMslM;
  const sTide = sl != null ? sub.tide(dh, amp) : null;

  // §6.3 合成：null 因子剔除分母
  const subById: Record<string, number> = {
    OXYGEN: sOxygen ?? NaN,
    PRESSURE: sPressure ?? NaN,
    SOLUNAR: sSolunar,
    WIND: sWind ?? NaN,
    WEATHER: sWeather ?? NaN,
    DAYPART: sDaypart,
    TEMP_MATCH: sTempMatch ?? NaN,
    COMFORT: sComfort ?? NaN,
  };
  if (sTide != null) subById.TIDE = sTide;

  const effWeights: Partial<Record<keyof typeof subById, number>> = { ...preset.weights };
  if (trendMissing && sPressure != null) {
    effWeights.PRESSURE = MISSING_TREND_PRESSURE_WEIGHT;
  }

  let acc = 0;
  let wSum = 0;
  for (const [factor, weight] of Object.entries(effWeights)) {
    const s = subById[factor];
    if (s != null && Number.isFinite(s)) {
      acc += s * (weight ?? 0);
      wSum += weight ?? 0;
    }
  }
  const baseScore = wSum > 0 ? clip01(acc / wSum) : 0;

  // §6.5 安全衰减 ΠV
  const veto = evaluateVeto({
    wmoCode: w.weatherCode,
    windSpeedKmh: w.windSpeedKmh,
    gustKmh: w.windGustKmh,
    dP3,
    rainMmPerH: rain,
    waveHeightM: m?.waveHeightM,
    doPotentialMgL: doP,
  });
  const total = roundToInt(baseScore * veto.value * 100.0);

  const effectivePressureWeight = trendMissing
    ? MISSING_TREND_PRESSURE_WEIGHT
    : (preset.weights.PRESSURE ?? 0);

  const factors: FactorScore[] = [];
  const push = (id: string, value: number | null, weight: number, detail?: string | null) => {
    if (value == null) return;
    factors.push({ id, sub: value, weight, detail: detail ?? null });
  };

  push('oxygen', sOxygen, preset.weights.OXYGEN ?? 0);
  push('pressure', sPressure, effectivePressureWeight, trendMissing ? 'trendInsufficient' : null);
  factors.push({ id: 'solunar', sub: sSolunar, weight: preset.weights.SOLUNAR ?? 0, detail: null });
  push('tide', sTide, preset.weights.TIDE ?? 0);
  push('wind', sWind, preset.weights.WIND ?? 0, w.windGustKmh != null ? String(w.windGustKmh) : null);
  push('weather', sWeather, preset.weights.WEATHER ?? 0);
  factors.push({ id: 'daypart', sub: sDaypart, weight: preset.weights.DAYPART ?? 0, detail: null });
  push('tempMatch', sTempMatch, preset.weights.TEMP_MATCH ?? 0);
  push('comfort', sComfort, preset.weights.COMFORT ?? 0);

  const sWaterQ =
    sOxygen != null
      ? waterQualityScore(sOxygen, w.cloudCoverPct, rain24hSum(index, all), null, tw)
      : null;
  if (sWaterQ != null) {
    factors.push({ id: 'waterQuality', sub: sWaterQ, weight: 0, detail: 'waterQualityProxyNote' });
  }

  const confidence: Confidence =
    wSum / weightSum(preset) < 0.6 ? 'ESTIMATED' : 'MODEL';

  return {
    timeEpochSec: input.timeEpochSec,
    total,
    factors,
    vetoReasons: veto.reasons,
    confidence,
    vetoCodes: veto.codes,
  };
}

export function calculate(
  inputs: ReadonlyArray<HourlyScoreInput>,
  preset: ScenePreset,
  lonDeg: number,
): FishScoreResult[] {
  if (inputs.length === 0) return [];

  const sorted = [...inputs].sort((a, b) => a.timeEpochSec - b.timeEpochSec);

  const slSeries = sorted.map((it) => it.marine?.seaLevelHeightMslM ?? null);
  const slValues = slSeries.filter((v): v is number => v != null);
  const amp = slValues.length > 0 ? Math.max(...slValues) - Math.min(...slValues) : 0;

  let prevSl: number | null = null;
  const dhByIndex = new Map<number, number>();
  slSeries.forEach((sl, i) => {
    if (sl != null) {
      dhByIndex.set(i, prevSl == null ? 0 : sl - prevSl);
      prevSl = sl;
    }
  });

  const airTemps = sorted.map((it) => it.weather.temperatureC);
  const pressures = sorted.map((it) => it.weather.pressureMslHpa);

  return sorted.map((input, i) =>
    scoreOne(input, i, preset, lonDeg, amp, dhByIndex.get(i) ?? 0, airTemps, pressures, sorted),
  );
}