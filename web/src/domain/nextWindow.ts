/**
 * 首页「下一个最佳窗口」提示条（#2）：在已聚合的跨点窗口里挑出「此刻最该关注的那个」。
 *
 * 口径与 bestWindows.findWindows 对齐：
 * - 窗口以 [startEpochSec, endEpochSec] 为闭区间，endEpochSec 表示**最后一小时**的起点，
 *   因此实际覆盖到 endEpochSec + 3600。
 * - 纯函数，不读时钟：nowEpochSec 由调用方注入，便于单测与时区无关地验证。
 */
import type { WindowEntry } from './bestWindows';

export type NextWindowState = 'in-window' | 'upcoming' | 'none';

export interface NextWindowResult<T extends WindowEntry> {
  state: NextWindowState;
  /** 命中的窗口；state 为 none 时为 null */
  window: T | null;
  /** 
   * 剩余秒数：
   * - in-window：距离窗口结束还剩多久
   * - upcoming：距离窗口开始还有多久
   * - none：0
   */
  remainingSec: number;
  /** in-window 时窗口内剩余小时数（含当前小时，向上取整，最小 1） */
  remainingHours: number;
}

/** 窗口实际覆盖的最后一秒（endEpochSec 是最后一小时起点，故 +3600 为开区间右端） */
export function windowEndExclusive(w: WindowEntry): number {
  return w.endEpochSec + 3600;
}

export function isInWindow(w: WindowEntry, nowEpochSec: number): boolean {
  return nowEpochSec >= w.startEpochSec && nowEpochSec < windowEndExclusive(w);
}

function empty(): NextWindowResult<never> {
  return { state: 'none', window: null, remainingSec: 0, remainingHours: 0 };
}

/**
 * 从一组窗口里挑出「下一个该关注的」。
 *
 * 优先级：
 * 1. 正在窗口内 → 选剩余时间最短（即最接近结束）的那个，提醒用户尽快出发
 * 2. 否则选**开始时间最近**的未来窗口
 * 3. 都没有 → none
 *
 * 说明：这里刻意不按 rank 选。rank 用于「哪个窗口最好」，而提示条回答的是
 * 「现在/接下来该不该出门」，时间紧迫性优先于质量。
 */
export function findNextWindow<T extends WindowEntry>(
  windows: ReadonlyArray<T>,
  nowEpochSec: number,
): NextWindowResult<T> {
  if (windows.length === 0) return empty();

  const active = windows.filter((w) => isInWindow(w, nowEpochSec));
  if (active.length > 0) {
    // 剩余最短者优先（最紧迫）
    const pick = active.reduce((best, w) =>
      windowEndExclusive(w) - nowEpochSec < windowEndExclusive(best) - nowEpochSec ? w : best,
    );
    const remainingSec = windowEndExclusive(pick) - nowEpochSec;
    return {
      state: 'in-window',
      window: pick,
      remainingSec,
      remainingHours: Math.max(1, Math.ceil(remainingSec / 3600)),
    };
  }

  const upcoming = windows.filter((w) => w.startEpochSec > nowEpochSec);
  if (upcoming.length > 0) {
    const pick = upcoming.reduce((best, w) => (w.startEpochSec < best.startEpochSec ? w : best));
    return {
      state: 'upcoming',
      window: pick,
      remainingSec: pick.startEpochSec - nowEpochSec,
      remainingHours: 0,
    };
  }

  return empty();
}

/** 秒 → 「2 小时 15 分」/「15 分钟」/「不到 1 分钟」 */
export function formatDuration(totalSec: number): string {
  if (!Number.isFinite(totalSec) || totalSec <= 0) return '0 分钟';
  if (totalSec < 60) return '不到 1 分钟';
  const totalMin = Math.floor(totalSec / 60);
  const hours = Math.floor(totalMin / 60);
  const mins = totalMin % 60;
  if (hours === 0) return `${mins} 分钟`;
  if (mins === 0) return `${hours} 小时`;
  return `${hours} 小时 ${mins} 分`;
}

/** 本地时钟 HH:00（按给定时区偏移） */
export function formatLocalHour(epochSec: number, tzSec: number): string {
  const d = new Date((epochSec + tzSec) * 1000);
  return `${String(d.getUTCHours()).padStart(2, '0')}:00`;
}

/** 窗口区间文案：09:00–12:00（endEpochSec 为最后一小时起点，显示时 +1h 表示收杆时刻） */
export function formatWindowRange(w: WindowEntry, tzSec: number): string {
  return `${formatLocalHour(w.startEpochSec, tzSec)}–${formatLocalHour(windowEndExclusive(w), tzSec)}`;
}
