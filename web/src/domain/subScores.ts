/**
 * 与 Android `algorithm/SubScores.kt` 逐行等价（SRS §6.2）。
 * 全部返回 [0,1]；缺失语义由调用方（fishScore）按 §6.7 处理。
 */
import { clip01, gauss } from './mathkit';

export function tide(dhPerHour: number, tidalRangeM: number): number {
  const moving = clip01(Math.abs(dhPerHour) / 0.16); // m/h
  let v = 0.22 + 0.58 * moving + 0.2 * clip01(tidalRangeM / 1.8);
  if (Math.abs(dhPerHour) < 0.02) v *= 0.72; // 平潮/满潮「停流」
  return clip01(v);
}

export function pressure(pressureHpa: number, dP3: number | null | undefined, dP24: number): number {
  const d3 = dP3 ?? 0;
  const trend = d3 >= 0 ? 0.55 + clip01(d3 / 5) * 0.45 : 0.55 - clip01(-d3 / 6) * 0.55;
  return clip01(
    0.45 * trend + 0.32 * gauss(pressureHpa, 1020.0, 10.0) + 0.23 * clip01(0.5 + dP24 / 12.0),
  );
}

export function wind(windSpeedKmh: number, gustKmh: number | null | undefined): number {
  const ideal = gauss(windSpeedKmh, 15.0, 13.0);
  const castable = windSpeedKmh <= 28 ? 1 : clip01(1 - (windSpeedKmh - 28) / 20);
  let gustPen = 1;
  if (gustKmh != null) {
    if (gustKmh > 45) gustPen = 0.45;
    else if (gustKmh > 35) gustPen = 0.8;
  }
  return clip01((0.55 * ideal + 0.45 * castable) * gustPen);
}

export function weather(
  wmoCode: number,
  rainMmPerH: number | null | undefined,
  cloudCoverPct: number | null | undefined,
): number {
  let base: number;
  switch (wmoCode) {
    case 0: base = 1.0; break;
    case 1: base = 0.97; break;
    case 2: base = 0.92; break;
    case 3: base = 0.86; break;
    case 45: base = 0.55; break;
    case 48: base = 0.45; break;
    case 51: base = 0.82; break;
    case 53: base = 0.7; break;
    case 55: base = 0.5; break;
    case 61: base = 0.62; break;
    case 63: base = 0.44; break;
    case 65: base = 0.22; break;
    case 80: base = 0.64; break;
    case 81: base = 0.46; break;
    case 82: base = 0.28; break;
    case 85: base = 0.25; break;
    case 86: base = 0.2; break;
    case 95:
    case 96:
    case 99:
      base = 0.0;
      break;
    default:
      base = 0.7;
  }

  const lightRain = (wmoCode >= 51 && wmoCode <= 65) || (wmoCode >= 80 && wmoCode <= 82);
  let bonus = 0;
  if (lightRain && rainMmPerH != null) {
    if (rainMmPerH < 0.6) bonus = 0.12;
    else if (rainMmPerH < 2.0) bonus = 0.04;
    else bonus = -0.12;
  }

  const cloudTerm = cloudCoverPct != null ? 0.12 * gauss(cloudCoverPct, 55.0, 45.0) : 0;
  return clip01(base + bonus + cloudTerm);
}

export function daypart(
  hourLocal: number,
  sunriseH: number | null | undefined,
  sunsetH: number | null | undefined,
  nightTarget: boolean,
): number {
  const sr = sunriseH ?? 6.3;
  const ss = sunsetH ?? 17.4;
  const cp = Math.max(gauss(hourLocal, sr + 0.4, 1.1), gauss(hourLocal, ss - 0.3, 1.1));
  const daytime = hourLocal > sr + 1.5 && hourLocal < ss - 1.5;
  const night = hourLocal < sr - 1 || hourLocal > ss + 1.2;
  let v = 0.3 + 0.6 * cp;
  if (daytime) v = 0.3 + 0.2 * (1 - cp);
  if (night) v = nightTarget ? 0.55 + 0.25 * cp : 0.28;
  return clip01(v + 0.2 * cp);
}

export function tempMatch(waterTempC: number, optC: number, sigmaC: number): number {
  return clip01(gauss(waterTempC, optC, sigmaC) + 0.04);
}

export function comfort(apparentC: number): number {
  return clip01(gauss(apparentC, 22.0, 11.0) * 0.9 + 0.1);
}

export function turbidityIndex(cloudCoverPct: number | null | undefined, rain24hMm: number): number {
  const cloud = cloudCoverPct != null ? 0.25 * gauss(cloudCoverPct, 60.0, 40.0) : 0;
  return clip01(0.5 + cloud - 0.05 * rain24hMm);
}

export function chlorophyllIndex(
  chlMgm3: number | null | undefined,
  waterTempC: number | null | undefined,
  cloudCoverPct: number | null | undefined,
): number {
  if (chlMgm3 != null) return clip01((chlMgm3 - 0.05) / 0.35);
  const tempTerm = waterTempC != null ? gauss(waterTempC, 22.0, 12.0) : 0.5;
  const cloudTerm = cloudCoverPct != null ? 0.3 * gauss(cloudCoverPct, 55.0, 45.0) : 0.15;
  return clip01(0.35 * tempTerm + cloudTerm);
}

export function waterQuality(sOxygen: number, turbidityIdx: number, chlIdx: number): number {
  return 0.55 * sOxygen + 0.25 * turbidityIdx + 0.2 * chlIdx;
}