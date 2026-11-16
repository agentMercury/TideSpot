import { describe, expect, it } from 'vitest';
import { hourTableRows } from './hourRows';

describe('hourTableRows（G-04 数值表）', () => {
  it('小时补零、分数转字符串、档位取五档冻结值', () => {
    const rows = hourTableRows([
      { hour: 0, total: 0 },
      { hour: 9, total: 50 },
      { hour: 17, total: 65 },
      { hour: 23, total: 80 },
    ]);
    expect(rows).toEqual([
      { time: '00:00', total: '0', band: '别去' },
      { time: '09:00', total: '50', band: '可以' },
      { time: '17:00', total: '65', band: '很适合' },
      { time: '23:00', total: '80', band: '爆护窗口' },
    ]);
  });

  it('顺序与入参一致（不重排）', () => {
    const rows = hourTableRows([
      { hour: 5, total: 40 },
      { hour: 1, total: 90 },
    ]);
    expect(rows.map((r) => r.time)).toEqual(['05:00', '01:00']);
  });

  it('空输入 → 空数组', () => {
    expect(hourTableRows([])).toEqual([]);
  });
});