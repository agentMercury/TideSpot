/**
 * 跨点最佳窗口（对齐 Android BestWindowFinder 的口径）：
 * - 连续小时（相邻 3600s）且分数 ≥ 阈值（默认 65 = GREAT 下限）视为一个窗口
 * - rank = 0.62·峰值 + 0.23·均值 + 1.4·min(时长, 4)（FO-14）
 * - 时段筛选按峰值所在本地小时（策略与 Android Top10TimeFilter 一致）
 */
import { resolveScoreBand } from '../ui/tokens/bands';

export type TimeSlot = 'all' | 'morning' | 'day' | 'evening' | 'night';

export interface ScoreLike {
  timeEpochSec: number;
  total: number;
}

export interface WindowEntry {
  startEpochSec: number;
  peakEpochSec: number;
  endEpochSec: number;
  peak: number;
  mean: number;
  lengthHours: number;
  rank: number;
  tier: string;
}

export const WINDOW_THRESHOLD = 65;
export const MAX_WINDOWS = 10;

export function findWindows(
  scores: ReadonlyArray<ScoreLike>,
  options: { threshold?: number } = {},
): WindowEntry[] {
  const threshold = options.threshold ?? WINDOW_THRESHOLD;
  const sorted = [...scores].sort((a, b) => a.timeEpochSec - b.timeEpochSec);
  const out: WindowEntry[] = [];

  let current: ScoreLike[] = [];

  const flush = () => {
    if (current.length === 0) return;
    const totals = current.map((s) => s.total);
    const peak = Math.max(...totals);
    const mean = totals.reduce((a, b) => a + b, 0) / totals.length;
    const lengthHours = current.length;
    const peakPoint = current.find((s) => s.total === peak) ?? current[0];
    out.push({
      startEpochSec: current[0].timeEpochSec,
      peakEpochSec: peakPoint.timeEpochSec,
      endEpochSec: current[current.length - 1].timeEpochSec,
      peak,
      mean,
      lengthHours,
      rank: 0.62 * peak + 0.23 * mean + 1.4 * Math.min(lengthHours, 4),
      tier: resolveScoreBand(peak).label,
    });
    current = [];
  };

  for (const s of sorted) {
    if (s.total < threshold) {
      flush();
      continue;
    }
    if (current.length > 0 && s.timeEpochSec - current[current.length - 1].timeEpochSec !== 3600) {
      flush();
    }
    current.push(s);
  }
  flush();

  return out.sort((a, b) => b.rank - a.rank);
}

/** 峰值所在本地小时 → 时段 */
export function slotOf(peakEpochSec: number, tzSec: number): Exclude<TimeSlot, 'all'> {
  const hour = new Date((peakEpochSec + tzSec) * 1000).getUTCHours();
  if (hour >= 4 && hour < 9) return 'morning';
  if (hour >= 9 && hour < 16) return 'day';
  if (hour >= 16 && hour < 21) return 'evening';
  return 'night';
}

export function filterBySlot<T extends WindowEntry & { tzSec?: number }>(windows: ReadonlyArray<T>, slot: TimeSlot): T[] {
  if (slot === 'all') return [...windows];
  return windows.filter((w) => slotOf(w.peakEpochSec, w.tzSec ?? 8 * 3600) === slot);
}

/**
 * 按「窗口起点」的本地小时过滤（G-18 自定义时段）。
 * fromHour > toHour 视为跨午夜；两端为 null 表示不限。
 */
export function filterByHourRange<T extends WindowEntry & { tzSec?: number }>(
  windows: ReadonlyArray<T>,
  fromHour: number | null,
  toHour: number | null,
): T[] {
  if (fromHour == null && toHour == null) return [...windows];
  return windows.filter((w) => {
    const h = new Date((w.startEpochSec + (w.tzSec ?? 8 * 3600)) * 1000).getUTCHours();
    if (fromHour == null) return h < (toHour as number);
    if (toHour == null) return h >= fromHour;
    return fromHour <= toHour ? h >= fromHour && h < toHour : h >= fromHour || h < toHour;
  });
}

export const SLOT_LABELS: Record<TimeSlot, string> = {
  all: '全部',
  morning: '清晨',
  day: '白天',
  evening: '傍晚',
  night: '夜间',
};