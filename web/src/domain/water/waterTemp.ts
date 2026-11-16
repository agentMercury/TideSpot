/**
 * 与 Android `water/WaterTemp.kt` 逐行等价（SRS §6.1）。
 * 海洋点直接采用 SST；内陆点用 31h 气温滞后 + α=0.70 混合。
 */
export const WINDOW_HOURS = 30;
export const ALPHA = 0.7;

export function fuse(
  airTemps: ReadonlyArray<number | null | undefined>,
  index: number,
  airTempNow: number | null | undefined,
  sstC: number | null | undefined,
): number | null {
  if (sstC != null) return sstC;

  let sum = 0;
  let wSum = 0;
  let any = false;

  const start = Math.max(0, index - WINDOW_HOURS);
  for (let k = start; k <= index; k += 1) {
    const t = airTemps[k];
    if (t == null) continue;
    const w = 1.0 / (1.0 + index - k);
    sum += t * w;
    wSum += w;
    any = true;
  }

  if (!any) return null;
  const tlag = sum / wSum;
  const now = airTempNow ?? tlag;
  return ALPHA * tlag + (1 - ALPHA) * now;
}