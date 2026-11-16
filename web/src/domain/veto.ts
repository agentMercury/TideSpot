/**
 * 与 Android `algorithm/VetoEvaluator.kt` 逐行等价（SRS §6.5）。
 * 连续 sigmoid，禁止硬阈值；缺测走保守档位（理由见 Kotlin 侧 KDoc）。
 *
 * 注意：reasons 这里只产出稳定 key，M2 由 i18n 映射为中文/英文文案；
 * 判定与筛选一律只看 codes（FO-01）。
 */
import { clip01, sig } from './mathkit';
import type { VetoCode } from './types';

const MISSING_WIND_KMH = 34.0;
const MISSING_GUST_KMH = 45.0;
const MISSING_DP3_HPA = -2.5;
const MISSING_RAIN_MM = 6.0;

export interface VetoOutcome {
  value: number;
  reasons: string[];
  codes: VetoCode[];
}

export interface VetoInput {
  wmoCode?: number | null;
  windSpeedKmh?: number | null;
  gustKmh?: number | null;
  dP3?: number | null;
  rainMmPerH?: number | null;
  waveHeightM?: number | null;
  doPotentialMgL?: number | null;
}

export function evaluate(input: VetoInput): VetoOutcome {
  const wind = input.windSpeedKmh ?? MISSING_WIND_KMH;
  const gust = input.gustKmh ?? MISSING_GUST_KMH;
  const d3 = input.dP3 ?? MISSING_DP3_HPA;
  const rain = input.rainMmPerH ?? MISSING_RAIN_MM;

  let veto = 1.0;
  const reasons: string[] = [];
  const codes: VetoCode[] = [];

  if (input.wmoCode != null && input.wmoCode >= 95) {
    veto *= 0.05; // 雷暴：唯一硬否决
    reasons.push('veto.thunderstorm');
    codes.push('THUNDERSTORM');
  }

  veto *= Math.max(0.12, sig(wind, 34.0, 0.3)); // 持续风
  veto *= Math.max(0.1, sig(gust, 45.0, 0.18)); // 阵风
  veto *= Math.max(0.4, 1 - clip01((-d3 - 2.5) / 3) * 0.55); // ΔP3 起点 −2.5
  veto *= Math.max(0.2, 1 - clip01((rain - 2) / 8) * 0.7); // 雨强

  if (input.waveHeightM != null) {
    veto *= Math.max(0.08, sig(input.waveHeightM, 1.6, 1.8));
  }
  if (input.doPotentialMgL != null) {
    veto *= Math.max(0.18, 1 - clip01((4.6 - input.doPotentialMgL) / 1.6) * 0.75);
  }

  if (veto < 0.75) {
    const details: string[] = [];
    if (wind > 30 || gust > 45) details.push(`veto.wind:${wind.toFixed(1)}:${gust.toFixed(1)}`);
    if (d3 < -2.5) details.push(`veto.pressure:${d3.toFixed(1)}`);
    if (rain > 2) details.push(`veto.rain:${rain.toFixed(1)}`);
    if (input.waveHeightM != null && input.waveHeightM > 1.6) details.push(`veto.wave:${input.waveHeightM}`);
    if (input.doPotentialMgL != null && input.doPotentialMgL < 4.6) {
      details.push(`veto.lowOxygen:${input.doPotentialMgL.toFixed(1)}`);
    }
    reasons.push(`veto.decay:${veto.toFixed(2)}:${details.join('|')}`);
    codes.push('FISHABILITY_DECAY');
  }

  return { value: veto, reasons, codes };
}