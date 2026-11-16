import { describe, expect, it } from 'vitest';
import { findNextWindow, formatDuration, formatWindowRange, isInWindow, windowEndExclusive } from './nextWindow';
import type { WindowEntry } from './bestWindows';

const T0 = 1794560000; // 任意基准，仅用于相对计算

function win(startOffsetH: number, lengthH: number, peak = 80): WindowEntry {
  const startEpochSec = T0 + startOffsetH * 3600;
  return {
    startEpochSec,
    peakEpochSec: startEpochSec,
    endEpochSec: startEpochSec + (lengthH - 1) * 3600,
    peak,
    mean: peak - 5,
    lengthHours: lengthH,
    rank: peak,
    tier: '爆护窗口',
  };
}

describe('windowEndExclusive', () => {
  it('endEpochSec 是最后一小时起点，右端要 +3600', () => {
    const w = win(0, 3); // 覆盖 T0..T0+3h
    expect(w.endEpochSec).toBe(T0 + 2 * 3600);
    expect(windowEndExclusive(w)).toBe(T0 + 3 * 3600);
  });
});

describe('isInWindow', () => {
  it('左闭：窗口起点那一刻算在窗口内', () => {
    const w = win(0, 3);
    expect(isInWindow(w, T0)).toBe(true);
  });

  it('右开：最后一小时结束后不算', () => {
    const w = win(0, 3);
    expect(isInWindow(w, T0 + 3 * 3600 - 1)).toBe(true);
    expect(isInWindow(w, T0 + 3 * 3600)).toBe(false);
  });

  it('窗口前不算', () => {
    expect(isInWindow(win(2, 2), T0)).toBe(false);
  });
});

describe('findNextWindow', () => {
  it('空输入 → none', () => {
    expect(findNextWindow([], T0)).toEqual({ state: 'none', window: null, remainingSec: 0, remainingHours: 0 });
  });

  it('全部已过期 → none（不假装还有窗口）', () => {
    const past = win(-5, 2);
    expect(findNextWindow([past], T0).state).toBe('none');
  });

  it('正在窗口内 → in-window，剩余时间正确', () => {
    const w = win(-1, 3); // T0-1h 开始，覆盖到 T0+2h
    const r = findNextWindow([w], T0);
    expect(r.state).toBe('in-window');
    expect(r.window).toBe(w);
    expect(r.remainingSec).toBe(2 * 3600);
    expect(r.remainingHours).toBe(2);
  });

  it('多个窗口同时在窗口内 → 选最紧迫（剩余最少）的', () => {
    const soon = win(-2, 3); // 覆盖到 T0+1h，剩余 1h
    const later = win(-1, 5); // 覆盖到 T0+4h，剩余 4h
    const r = findNextWindow([later, soon], T0);
    expect(r.state).toBe('in-window');
    expect(r.remainingSec).toBe(1 * 3600);
  });

  it('窗口内但只剩几分钟 → remainingHours 至少为 1', () => {
    const near = { ...win(0, 1), startEpochSec: T0 - 3599, endEpochSec: T0 - 3599 };
    const r = findNextWindow([near], T0);
    expect(r.state).toBe('in-window');
    expect(r.remainingHours).toBe(1);
  });

  it('没有活跃窗口 → 选开始时间最近的未来窗口', () => {
    const far = win(10, 2);
    const near = win(3, 2);
    const r = findNextWindow([far, near], T0);
    expect(r.state).toBe('upcoming');
    expect(r.window).toBe(near);
    expect(r.remainingSec).toBe(3 * 3600);
    expect(r.remainingHours).toBe(0);
  });

  it('时间紧迫性优先于 rank 质量', () => {
    const lowRankButSoon = { ...win(1, 1, 70), rank: 70 };
    const highRankButLater = { ...win(8, 4, 95), rank: 95 };
    const r = findNextWindow([highRankButLater, lowRankButSoon], T0);
    expect(r.state).toBe('upcoming');
    expect(r.window?.peak).toBe(70);
  });

  it('混合：有活跃窗口时不考虑未来窗口', () => {
    const active = win(-1, 3);
    const future = win(5, 2);
    const r = findNextWindow([future, active], T0);
    expect(r.state).toBe('in-window');
    expect(r.window).toBe(active);
  });

  it('泛型保留子类型额外字段（SpotWindow 的 spotId/spotName 不丢）', () => {
    type SpotWindow = WindowEntry & { spotId: number; spotName: string; tzSec: number };
    const w: SpotWindow = { ...win(2, 2), spotId: 42, spotName: '青岛栈桥', tzSec: 8 * 3600 };
    const r = findNextWindow<SpotWindow>([w], T0);
    expect(r.window?.spotId).toBe(42);
    expect(r.window?.spotName).toBe('青岛栈桥');
  });
});

describe('formatDuration', () => {
  it('小时 + 分钟', () => {
    expect(formatDuration(2 * 3600 + 15 * 60)).toBe('2 小时 15 分');
  });
  it('整小时', () => {
    expect(formatDuration(3 * 3600)).toBe('3 小时');
  });
  it('不到一小时只剩分钟', () => {
    expect(formatDuration(45 * 60)).toBe('45 分钟');
  });
  it('不到一分钟', () => {
    expect(formatDuration(30)).toBe('不到 1 分钟');
  });
  it('0 或负数 → 0 分钟', () => {
    expect(formatDuration(0)).toBe('0 分钟');
    expect(formatDuration(-100)).toBe('0 分钟');
  });
});

describe('formatWindowRange', () => {
  it('按本地时区显示，收杆时刻用 endEpochSec+1h', () => {
    const tz = 8 * 3600;
    // 本地 09:00 起 3 小时 → 09:00–12:00
    const startEpochSec = Date.UTC(2026, 10, 16, 1, 0) / 1000; // UTC 01:00 = 本地 09:00 (UTC+8)
    const w: WindowEntry = {
      startEpochSec,
      peakEpochSec: startEpochSec,
      endEpochSec: startEpochSec + 2 * 3600,
      peak: 80, mean: 75, lengthHours: 3, rank: 80, tier: '爆护窗口',
    };
    expect(formatWindowRange(w, tz)).toBe('09:00–12:00');
  });
});
