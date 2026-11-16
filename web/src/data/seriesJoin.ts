/**
 * 与 Android `core/data/repo/SeriesJoiner.kt` 对齐：
 * 天气（past_days=7，408h）与海洋（past_days=1，264h）起始时间不同，
 * 禁止按下标对齐，必须按 epoch 秒 join 成升序并集。
 */
import type { HourlyMarine, HourlyWeather } from '../domain/types';

export interface HourSlice {
  timeEpochSec: number;
  weather: HourlyWeather | null;
  marine: HourlyMarine | null;
}

export function joinSeries(weather: ReadonlyArray<HourlyWeather>, marine: ReadonlyArray<HourlyMarine>): HourSlice[] {
  const w = new Map<number, HourlyWeather>();
  const m = new Map<number, HourlyMarine>();
  for (const item of weather) w.set(item.timeEpochSec, item);
  for (const item of marine) m.set(item.timeEpochSec, item);

  const keys = [...new Set([...w.keys(), ...m.keys()])].sort((a, b) => a - b);
  return keys.map((t) => ({
    timeEpochSec: t,
    weather: w.get(t) ?? null,
    marine: m.get(t) ?? null,
  }));
}