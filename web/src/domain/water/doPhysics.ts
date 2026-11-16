/**
 * 与 Android `water/DoPhysics.kt` 逐行等价（SRS §6.2a，附录 B1 黄金值）。
 */
import { clip01 } from '../mathkit';

/** 饱和溶解度 Cs(Tw,S) mg/L；S=盐度‰ */
export function doSaturation(waterTempC: number, salinityPpt: number): number {
  return (468.0 / (31.6 + waterTempC)) * (1.0 - 0.0075 * salinityPpt);
}

/** 饱和水汽压 pw(Tw) hPa */
export function vaporPressure(waterTempC: number): number {
  return 6.1078 * Math.exp((17.27 * waterTempC) / (waterTempC + 237.3));
}

export function doPotential(waterTempC: number, pressureMslHpa: number, salinityPpt: number): number {
  const pw = vaporPressure(waterTempC);
  const f = (pressureMslHpa - pw) / (1013.25 - pw);
  return doSaturation(waterTempC, salinityPpt) * f;
}

/** DO_ref = clamp(5.2, 8.5, base − 0.05·(Tw−15)) */
export function doRef(waterTempC: number, doBase: number): number {
  return Math.min(8.5, Math.max(5.2, doBase - 0.05 * (waterTempC - 15.0)));
}

export function oxygenScore(doPotentialMgL: number, doRefMgL: number): number {
  return clip01((doPotentialMgL - 4.0) / (doRefMgL - 4.0));
}