/**
 * 逐时指数「数值表」行构造（G-04）——从图表点提取，便于单测与屏幕阅读器输出一致。
 * 档位一律走 `resolveScoreBand`（五档冻结值），禁止在此重复实现阈值。
 */
import type { HourPoint } from './HourChart';
import { resolveScoreBand } from '../tokens/bands';

export interface HourRow {
  time: string;
  total: string;
  band: string;
  /** DataTable 以 Record<string, ReactNode> 取列，需允许索引访问 */
  [key: string]: string;
}

function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

/** 按传入顺序输出（调用方已按小时排序）；空输入 → 空数组。 */
export function hourTableRows(points: ReadonlyArray<HourPoint>): HourRow[] {
  return points.map((p) => ({
    time: `${pad2(p.hour)}:00`,
    total: String(p.total),
    band: resolveScoreBand(p.total).label,
  }));
}