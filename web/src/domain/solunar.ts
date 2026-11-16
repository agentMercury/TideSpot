/**
 * 与 Android `algorithm/Solunar.kt` 逐行等价（SRS §6.2c，Meeus 简化）。
 */
import { clip01, jsMod, trap } from './mathkit';

export const SYNODIC_MONTH_DAYS = 29.530588861;

/** 月龄（天）；utcMillis 为该时刻真实 UTC 毫秒 */
export function lunarAge(utcMillis: number): number {
  const d = utcMillis / 86400000.0 + 2440587.5 - 2451545.0;
  const rad = Math.PI / 180.0;
  const Ls = 280.46 + 0.9856474 * d; // 太阳平黄经
  const Ms = (357.529 + 0.9856003 * d) * rad; // 太阳平近点角
  const lamS = Ls + 1.915 * Math.sin(Ms) + 0.02 * Math.sin(2 * Ms);
  const Lm = 218.316 + 13.176396 * d; // 月球平黄经
  const Mm = (134.963 + 13.064993 * d) * rad; // 月球平近点角
  const Dm = (Lm - lamS) * rad; // 月日平距差
  const lamM =
    Lm +
    6.289 * Math.sin(Mm) +
    2.1 * Math.sin(Dm) -
    1.274 * Math.sin((2 * (Lm - lamS) - Mm) * rad) +
    0.658 * Math.sin(2 * Dm) -
    0.186 * Math.sin(Ms) -
    0.114 * Math.sin(2 * (Lm - 13.915) * rad);

  let e = (lamM - lamS) % 360.0;
  if (e < 0) e += 360.0;
  return (e / 360.0) * SYNODIC_MONTH_DAYS;
}

export function illumination(ageDays: number): number {
  return (1 - Math.cos((2 * Math.PI * ageDays) / SYNODIC_MONTH_DAYS)) / 2;
}

/** 月上中天钟表时（小时）；tzOffsetHours 必须显式传入（FO-18） */
export function lunarTransitClock(ageDays: number, lonDeg: number, tzOffsetHours: number): number {
  const lmt = 12.0 + (50.47 / 60.0) * ageDays;
  const clock = lmt + (tzOffsetHours - lonDeg / 15.0);
  const P = 24.8412;
  return jsMod(clock, P);
}

export function score(hourLocal: number, transitHour: number, ageDays: number): number {
  const P = 12.4206;
  const dl = jsMod(hourLocal + 0.5 - transitHour, P);
  const majDist = Math.min(dl, P - dl);
  const minDist = Math.abs(dl - P / 2);
  const base = Math.max(
    trap(majDist, -0.2, 0.5, 1.2, 2.1),
    trap(minDist, 0.15, 0.7, 1.1, 1.7) * 0.75,
  );
  return clip01(base * (0.55 + 0.45 * illumination(ageDays)) + 0.05);
}