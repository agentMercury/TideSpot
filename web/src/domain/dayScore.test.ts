import { describe, expect, it } from 'vitest';
import { computeDayScore, FISHABLE_START_HOUR, FISHABLE_END_HOUR, STORM_CAP } from './dayScore';

/**
 * 日综合分（v7）单测——与 Android `DayScoreCalculatorTest` 逐条对齐。
 *
 * 重点：11 种典型日型的严格排序 + 雷暴日安全封顶。
 */

const TZ = 8 * 3600;

/** 本地 2026-11-17 00:00 的 epoch（已按 UTC+8 换算好，不要再减 TZ）。 */
const DAY_START = 1794844800;

/** 用 24 小时分数构造序列（下标 = 本地小时）。 */
function day(hourly: number[], dayOffset = 0) {
  return hourly.map((total, h) => ({
    timeEpochSec: DAY_START + dayOffset * 86400 + h * 3600,
    total,
  }));
}

const score = (hourly: number[]) => computeDayScore(day(hourly), TZ);

describe('日综合分 · 基础语义', () => {
  it('空输入返回 null（禁编造 0）', () => {
    expect(computeDayScore([], TZ)).toBeNull();
  });

  it('全天均匀 → 日分等于该分', () => {
    expect(score(Array(24).fill(70))!).toBeCloseTo(70, 1);
    expect(score(Array(24).fill(55))!).toBeCloseTo(55, 1);
    expect(score(Array(24).fill(85))!).toBeCloseTo(85, 1);
  });

  it('平均值口径：只统计可钓时段 5–21 时', () => {
    // 可钓时段 17 小时全是 70，深夜 7 小时全是 20
    const hourly = Array(24).fill(70);
    for (const h of [0, 1, 2, 3, 4, 22, 23]) hourly[h] = 20;
    // 若用全 24h 平均 ≈ 55.4；用可钓时段应为 70（高光加成后 ≥70）
    expect(score(hourly)!).toBeGreaterThanOrEqual(70);
  });

  it('可钓时段边界：5 时和 21 时都算入', () => {
    // 只有 5 时和 21 时有分，其余为 0（但 >SEVERE 避免封顶）
    const hourly = Array(24).fill(11);
    hourly[FISHABLE_START_HOUR] = 90;
    hourly[FISHABLE_END_HOUR] = 90;
    const v = score(hourly)!;
    expect(v).toBeGreaterThan(11);
  });
});

describe('日综合分 · 高光加成', () => {
  it('长时高光优于短时高光（差距显著）', () => {
    const long = Array(24).fill(40);
    for (let h = 6; h <= 13; h += 1) long[h] = 90;   // 8 小时
    const short = Array(24).fill(40);
    for (let h = 18; h <= 19; h += 1) short[h] = 90; // 2 小时
    const vl = score(long)!;
    const vs = score(short)!;
    expect(vl).toBeGreaterThan(vs);
    expect(vl - vs).toBeGreaterThan(15);
  });

  it('单峰窄窗不冒充好天——低于全天均匀 70', () => {
    const narrow = Array(24).fill(40);
    for (let h = 18; h <= 19; h += 1) narrow[h] = 92;
    const steady = Array(24).fill(70);
    expect(score(narrow)!).toBeLessThan(score(steady)!);
  });
});

describe('日综合分 · 安全封顶', () => {
  it('雷暴日封顶到 35——即使其他时段好', () => {
    const storm = [
      45, 42, 40, 38, 42, 55, 70, 72, 68, 60, 55, 50,
      5, 5, 5, 50, 60, 65, 70, 68, 60, 52, 48, 45,
    ];
    expect(score(storm)!).toBeCloseTo(STORM_CAP, 1);
  });

  it('无雷暴不封顶', () => {
    const good = [
      45, 42, 40, 38, 42, 55, 91, 88, 75, 68, 62, 58,
      55, 52, 50, 60, 72, 88, 90, 85, 78, 70, 60, 52,
    ];
    expect(score(good)!).toBeGreaterThan(STORM_CAP);
  });

  it('封顶阈值边界——恰好 10 分触发，11 分不触发', () => {
    const atEdge = Array(24).fill(80);
    atEdge[12] = 10;
    expect(score(atEdge)!).toBeCloseTo(STORM_CAP, 1);

    const aboveEdge = Array(24).fill(80);
    aboveEdge[12] = 11;
    expect(score(aboveEdge)!).toBeGreaterThan(STORM_CAP);
  });
});

describe('日综合分 · 11 种日型严格排序', () => {
  it('全天85 > 双峰 > 长高光 > 单峰 > 全天70 > 短高光 > 全天55 > 差天', () => {
    const all85 = Array(24).fill(85);
    const peak91 = [
      45, 42, 40, 38, 42, 55, 91, 88, 75, 68, 62, 58,
      55, 52, 50, 60, 72, 88, 90, 85, 78, 70, 60, 52,
    ];
    const longHigh = Array(24).fill(40);
    for (let h = 6; h <= 13; h += 1) longHigh[h] = 90;
    const singlePeak = [
      30, 28, 25, 25, 30, 40, 50, 55, 58, 60, 55, 50,
      45, 42, 40, 55, 70, 85, 92, 90, 78, 65, 50, 40,
    ];
    const steady70 = Array(24).fill(70);
    const shortHigh = Array(24).fill(40);
    for (let h = 18; h <= 19; h += 1) shortHigh[h] = 90;
    const steady55 = Array(24).fill(55);
    const bad = [
      25, 22, 20, 20, 25, 30, 35, 38, 40, 38, 35, 32,
      30, 28, 26, 30, 35, 38, 42, 45, 40, 35, 28, 25,
    ];

    const scores = [all85, peak91, longHigh, singlePeak, steady70, shortHigh, steady55, bad]
      .map((h) => score(h)!);

    for (let i = 0; i < scores.length - 1; i += 1) {
      expect(scores[i], `第 ${i + 1} 项(${scores[i]}) 应大于第 ${i + 2} 项(${scores[i + 1]})`)
        .toBeGreaterThan(scores[i + 1]);
    }
  });
});

describe('日综合分 · 跨日与时区', () => {
  it('不同日期互不影响', () => {
    expect(computeDayScore(day(Array(24).fill(80), 0), TZ)!).toBeCloseTo(80, 1);
    expect(computeDayScore(day(Array(24).fill(40), 1), TZ)!).toBeCloseTo(40, 1);
  });

  it('时区偏移生效——同一 epoch 在不同时区落在不同可钓时段', () => {
    const hourly = Array(24).fill(30);
    for (let h = 5; h <= 21; h += 1) hourly[h] = 75;
    const d = day(hourly);
    const vUtc8 = computeDayScore(d, TZ)!;
    const vUtc = computeDayScore(d, 0)!;
    expect(vUtc8).not.toBe(vUtc);
  });
});
