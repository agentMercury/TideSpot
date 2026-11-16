/**
 * 与 Android `core/data/repo/Ttl.kt` 对齐。
 * TTL：天气 3h / 海洋 6h；缓存年龄 ≥48h 视为不可用（§6.7 第 4 条）。
 */

export type DataSourceId = 'weather' | 'marine';

export const DATA_SOURCES: Record<DataSourceId, { wire: string; ttlMillis: number }> = {
  weather: { wire: 'weather', ttlMillis: 3 * 60 * 60 * 1000 },
  marine: { wire: 'marine', ttlMillis: 6 * 60 * 60 * 1000 },
};

export const UNUSABLE_AFTER_MILLIS = 48 * 60 * 60 * 1000;

/** 缓存是否仍新鲜；lastFetchedAt <= 0 视为不新鲜 */
export function isFresh(source: DataSourceId, lastFetchedAtMillis: number, nowMillis: number): boolean {
  return lastFetchedAtMillis > 0 && nowMillis - lastFetchedAtMillis < DATA_SOURCES[source].ttlMillis;
}

/** 过期毫秒数（>0 = 已过期）；无拉取记录 → null */
export function staleForMillis(
  source: DataSourceId,
  lastFetchedAtMillis: number,
  nowMillis: number,
): number | null {
  if (lastFetchedAtMillis <= 0) return null;
  const stale = nowMillis - lastFetchedAtMillis - DATA_SOURCES[source].ttlMillis;
  return stale > 0 ? stale : null;
}

/** 缓存年龄 ≥48h → 返回年龄（不可用）；否则 null */
export function unusableAgeMillis(lastFetchedAtMillis: number, nowMillis: number): number | null {
  if (lastFetchedAtMillis <= 0) return null;
  const age = nowMillis - lastFetchedAtMillis;
  return age >= UNUSABLE_AFTER_MILLIS ? age : null;
}