import { describe, expect, it } from 'vitest';
import { CONFIDENCE_BADGES, SCORE_BANDS, confidenceBadge, resolveScoreBand } from './bands';

describe('五档色带（与 ScoreBands.kt 冻结值一致）', () => {
  it('5 档且合计覆盖 0–100', () => {
    expect(SCORE_BANDS).toHaveLength(5);
    expect(SCORE_BANDS[0].key).toBe('EXPLOSIVE');
    expect(SCORE_BANDS[4].key).toBe('BAD');
  });

  it('边界：闭区间', () => {
    expect(resolveScoreBand(100).key).toBe('EXPLOSIVE');
    expect(resolveScoreBand(80).key).toBe('EXPLOSIVE');
    expect(resolveScoreBand(79).key).toBe('GREAT');
    expect(resolveScoreBand(65).key).toBe('GREAT');
    expect(resolveScoreBand(64).key).toBe('OK');
    expect(resolveScoreBand(50).key).toBe('OK');
    expect(resolveScoreBand(49).key).toBe('POOR');
    expect(resolveScoreBand(35).key).toBe('POOR');
    expect(resolveScoreBand(34).key).toBe('BAD');
    expect(resolveScoreBand(0).key).toBe('BAD');
  });

  it('任意 0–100 都能取到档位', () => {
    for (let s = 0; s <= 100; s += 1) {
      expect(resolveScoreBand(s)).toBeTruthy();
    }
  });
});

describe('置信徽标', () => {
  it('6 种且 4dp 圆角语义', () => {
    expect(CONFIDENCE_BADGES).toHaveLength(6);
    expect(CONFIDENCE_BADGES.map((b) => b.key)).toContain('UNCALIBRATED');
  });

  it('UNAVAILABLE 归一到 UNCALIBRATED（不虚构更高置信）', () => {
    expect(confidenceBadge('UNAVAILABLE')?.key).toBe('UNCALIBRATED');
    expect(confidenceBadge('MODEL')?.key).toBe('MODEL');
    expect(confidenceBadge('NOPE')).toBeNull();
  });
});