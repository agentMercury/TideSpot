import { describe, expect, it } from 'vitest';
import { filterByHourRange, filterBySlot, findWindows, slotOf, type ScoreLike, type WindowEntry } from './bestWindows';

const T0 = 1794560000;

function series(totals: number[], start = T0): ScoreLike[] {
  return totals.map((total, i) => ({ timeEpochSec: start + i * 3600, total }));
}

describe('findWindows', () => {
  it('连续高分聚成一个窗口，峰值/均值/rank 正确', () => {
    const windows = findWindows(series([40, 70, 80, 72, 40, 90, 66]));
    expect(windows).toHaveLength(2);

    const first = windows.find((w) => w.peak === 80)!;
    expect(first.lengthHours).toBe(3);
    expect(first.startEpochSec).toBe(T0 + 3600);
    expect(first.endEpochSec).toBe(T0 + 3 * 3600);
    expect(first.mean).toBeCloseTo((70 + 80 + 72) / 3, 6);
    expect(first.rank).toBeCloseTo(0.62 * 80 + 0.23 * ((70 + 80 + 72) / 3) + 1.4 * 3, 6);
    expect(first.tier).toBe('爆护窗口'); // 80 → EXPLOSIVE
  });

  it('不连续的高分不合并（缺一小时即断开）', () => {
    const windows = findWindows(series([70, 70, 40, 70]));
    expect(windows).toHaveLength(2);
    expect(windows.map((w) => w.lengthHours).sort()).toEqual([1, 2]);
  });

  it('全部低于阈值 → 空数组（不兜底造假）', () => {
    expect(findWindows(series([20, 30, 40]))).toEqual([]);
  });

  it('空输入 → 空数组', () => {
    expect(findWindows([])).toEqual([]);
  });

  it('自定义阈值', () => {
    expect(findWindows(series([50, 50]), { threshold: 50 })).toHaveLength(1);
    expect(findWindows(series([50, 50]), { threshold: 51 })).toHaveLength(0);
  });

  it('rank 排序：峰值更高/更长者在前', () => {
    const windows = findWindows(series([70, 70, 70, 40, 95]));
    expect(windows[0].peak).toBe(95);
  });
});

describe('时段筛选', () => {
  it('按本地小时归入 4 段', () => {
    const tz = 8 * 3600;
    const epochAtLocalHour = (hour: number) => Date.UTC(2026, 10, 13, hour, 0) / 1000 - tz;
    expect(slotOf(epochAtLocalHour(5), tz)).toBe('morning');
    expect(slotOf(epochAtLocalHour(8), tz)).toBe('morning');
    expect(slotOf(epochAtLocalHour(9), tz)).toBe('day');
    expect(slotOf(epochAtLocalHour(15), tz)).toBe('day');
    expect(slotOf(epochAtLocalHour(16), tz)).toBe('evening');
    expect(slotOf(epochAtLocalHour(20), tz)).toBe('evening');
    expect(slotOf(epochAtLocalHour(21), tz)).toBe('night');
    expect(slotOf(epochAtLocalHour(3), tz)).toBe('night');
    expect(slotOf(epochAtLocalHour(4), tz)).toBe('morning');
  });

  it('all 不过滤；其它按时段过滤', () => {
    const windows = findWindows(series([70, 70]));
    expect(filterBySlot(windows, 'all')).toHaveLength(1);
    const matched = filterBySlot(windows, slotOf(windows[0].peakEpochSec, 8 * 3600));
    expect(matched).toHaveLength(1);
    const other = (['morning', 'day', 'evening', 'night'] as const).find(
      (s) => s !== slotOf(windows[0].peakEpochSec, 8 * 3600),
    )!;
    expect(filterBySlot(windows, other)).toHaveLength(0);
  });
});
describe('filterByHourRange（G-18 自定义时段）', () => {
  const TZ = 8 * 3600;
  // 基准 UTC 2026-01-01 00:00 → 加 TZ 后本地 08:00；win(n) 的本地小时为 (8 + n) % 24
  const base = Date.UTC(2026, 0, 1, 0, 0, 0) / 1000;
  const win = (startHour: number): WindowEntry & { tzSec: number } => ({
    startEpochSec: base + startHour * 3600,
    peakEpochSec: base + startHour * 3600,
    endEpochSec: base + (startHour + 1) * 3600,
    peak: 70,
    mean: 70,
    lengthHours: 1,
    rank: 50,
    tier: '可钓',
    tzSec: TZ,
  });
  const windows = [win(0), win(4), win(12), win(20)];
  const hours = (list: Array<WindowEntry & { tzSec: number }>) =>
    list.map((w) => new Date((w.startEpochSec + TZ) * 1000).getUTCHours());

  it('两端都不限时原样返回', () => {
    expect(filterByHourRange(windows, null, null)).toHaveLength(4);
  });

  it('只给起点 → 取 h ≥ 起点', () => {
    expect(hours(filterByHourRange(windows, 8, null))).toEqual([8, 12, 20]);
  });

  it('只给终点 → 取 h < 终点', () => {
    expect(hours(filterByHourRange(windows, null, 13))).toEqual([8, 12, 4]);
  });

  it('起点 < 终点 → 同一天区间（左闭右开）', () => {
    expect(hours(filterByHourRange(windows, 8, 13))).toEqual([8, 12]);
  });

  it('起点 > 终点 → 跨午夜区间', () => {
    expect(hours(filterByHourRange(windows, 20, 12))).toEqual([8, 20, 4]);
  });

  it('不修改入参', () => {
    const copy = [...windows];
    filterByHourRange(windows, 8, null);
    expect(windows).toEqual(copy);
  });
});
