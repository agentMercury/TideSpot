/**
 * 日综合分（对齐 Android `DayScoreCalculator.kt`，v7 口径）。
 *
 * ## 为什么不用峰值
 * 详情页大卡原本取当日最高小时分。问题：上午 10 点打开，大卡显示 91（06:00 的峰值），
 * 但此刻可能只有 68 分——用户看到 91 出门，实际是「很适合」甚至「别去」。
 *
 * ## 算法
 * ```
 * 可钓时段   = 05:00–21:00（按选点时区判定本地小时）
 * 基础分     = 可钓时段内逐小时分数的算术平均
 * 高光分     = 可钓时段内「最佳连续 3 小时」的平均（不足 3h 用全部）
 * 高光小时数 = 可钓时段中 ≥75 分的小时数
 * 可持续性   = clamp((高光小时数 − 1) / 7, 0, 1)    // 8h 及以上算满分
 * 提升量     = max(0, 高光分 − 基础分) × (0.5 + 0.5·可持续性) × 0.6
 * 日综合分   = 基础分 + 提升量
 * ```
 *
 * 三个设计取舍（与 Android 完全一致，由 `dayScoreParity.test.ts` 对拍守护）：
 * 1. **均值打底**——回答「今天整体怎么样」。全天 70 分应高于「只有 2 小时 90 分」。
 * 2. **高光加成但打折**——「有没有值得专程去的时候」也重要，但短时高光不能冒充好天。
 * 3. **安全封顶**——当天出现任意 ≤10 分的小时（通常来自雷暴硬否决 ×0.05）→ 整日分封顶
 *    `STORM_CAP`，否则雷暴日会被均值的其他时段拉成「可以」，误导用户出门。
 */

/** 可钓时段起止（本地小时，含端点）。5:00–21:00 共 17 小时。 */
export const FISHABLE_START_HOUR = 5;
export const FISHABLE_END_HOUR = 21;

/** 高光判定的分数阈值（「值得专程」的下限 75）。 */
export const HIGHLIGHT_MIN_SCORE = 75;

/** 可持续性满分所需的高光小时数。 */
export const SUSTAIN_FULL_HOURS = 8;

/** 安全封顶：出现严重小时（≤ SEVERE_SCORE）时整日分不超过此值。 */
export const STORM_CAP = 35.0;

/** 严重小时阈值：≤ 此分视为「当日存在危险/不可用时段」。 */
export const SEVERE_SCORE = 10;

/** 计算所需的最小结构（与 `FishScoreResult` 兼容）。 */
export interface DayScoreInput {
  timeEpochSec: number;
  total: number;
}

/** 本地小时（0–23），按给定时区偏移换算。 */
function localHourOf(epochSec: number, zoneOffsetSec: number): number {
  const local = epochSec + zoneOffsetSec;
  const secOfDay = ((local % 86400) + 86400) % 86400;
  return Math.floor(secOfDay / 3600);
}

/**
 * 计算日综合分。
 *
 * @param scores 该日逐小时评分（顺序不限）
 * @param zoneOffsetSec 选点时区偏移秒（用于判定「本地小时」是否落在可钓时段）
 * @returns 0–100 的日综合分；空输入或无可用小时返回 `null`（调用方显示「—」，禁编造 0）
 */
export function computeDayScore(
  scores: ReadonlyArray<DayScoreInput>,
  zoneOffsetSec: number,
): number | null {
  if (scores.length === 0) return null;

  const sorted = [...scores].sort((a, b) => a.timeEpochSec - b.timeEpochSec);

  // 按本地小时筛出可钓时段
  const fishable = sorted.filter((r) => {
    const h = localHourOf(r.timeEpochSec, zoneOffsetSec);
    return h >= FISHABLE_START_HOUR && h <= FISHABLE_END_HOUR;
  });
  if (fishable.length === 0) return null;

  const totals = fishable.map((r) => r.total);
  const base = totals.reduce((s, v) => s + v, 0) / totals.length;

  // 最佳连续 3 小时（不足 3h → 用全部）
  const windowSize = Math.min(3, totals.length);
  let best3 = Number.NEGATIVE_INFINITY;
  for (let i = 0; i + windowSize <= totals.length; i += 1) {
    let sum = 0;
    for (let k = 0; k < windowSize; k += 1) sum += totals[i + k];
    const avg = sum / windowSize;
    if (avg > best3) best3 = avg;
  }
  if (!Number.isFinite(best3)) best3 = base;

  const highlightHours = totals.filter((v) => v >= HIGHLIGHT_MIN_SCORE).length;
  const sustain = Math.min(
    1,
    Math.max(0, (highlightHours - 1) / (SUSTAIN_FULL_HOURS - 1)),
  );
  const lift = Math.max(0, best3 - base) * (0.5 + 0.5 * sustain) * 0.6;
  const raw = base + lift;

  // 安全封顶：当日存在严重小时（雷暴硬否决等）
  const hasSevere = sorted.some((r) => r.total <= SEVERE_SCORE);
  return hasSevere ? Math.min(raw, STORM_CAP) : raw;
}
