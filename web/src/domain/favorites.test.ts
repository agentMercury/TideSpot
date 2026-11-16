import { describe, expect, it } from 'vitest';
import {
  UNGROUPED_KEY,
  displayGroupName,
  existingGroups,
  groupFavorites,
  groupNameOf,
  invertSelection,
  isAllSelected,
  mergeTags,
  normalizeGroupName,
  parseTags,
  selectAll,
  selectedIds,
  sortFavorites,
  toggleCollapsed,
  toggleSelection,
  type FavoriteLike,
} from './favorites';

interface F extends FavoriteLike {
  id: number;
}

const spot = (id: number, name: string, lastViewedAt = 0, groupName?: string | null): F => ({
  id,
  name,
  lastViewedAt,
  groupName,
  tags: [],
});

describe('sortFavorites', () => {
  it('NAME：按名称升序，稳定', () => {
    const list = [spot(1, 'B'), spot(2, 'A'), spot(3, 'C')];
    expect(sortFavorites(list, 'NAME').map((s) => s.name)).toEqual(['A', 'B', 'C']);
  });

  it('LAST_VIEWED：最近在前，lastViewedAt=0 排最后（缺值不按 0 参与）', () => {
    const list = [spot(1, 'a', 0), spot(2, 'b', 2000), spot(3, 'c', 1000)];
    expect(sortFavorites(list, 'LAST_VIEWED').map((s) => s.id)).toEqual([2, 3, 1]);
  });

  it('SCORE：分数降序，缺 key 排最后', () => {
    const list = [spot(1, 'a'), spot(2, 'b'), spot(3, 'c')];
    const scores = new Map<number, number>([
      [1, 60],
      [3, 90],
    ]);
    expect(sortFavorites(list, 'SCORE', scores).map((s) => s.id)).toEqual([3, 1, 2]);
  });

  it('SCORE：真实 0 分也按缺值排最后（id 3 分数 0）', () => {
    const list = [spot(1, 'a'), spot(3, 'c')];
    const scores = new Map<number, number>([
      [1, 55],
      [3, 0],
    ]);
    expect(sortFavorites(list, 'SCORE', scores).map((s) => s.id)).toEqual([1, 3]);
  });

  it('RECOMMENDED：rank 降序，同 rank 按名称升序，缺 rank 最后', () => {
    const list = [spot(1, 'Zed'), spot(2, 'Amy'), spot(3, 'Bob')];
    const ranks = new Map<number, number>([
      [1, 80],
      [2, 80],
    ]);
    expect(sortFavorites(list, 'RECOMMENDED', new Map(), ranks).map((s) => s.id)).toEqual([2, 1, 3]);
  });

  it('稳定：并列时保持输入顺序', () => {
    const list = [spot(1, 'X', 100), spot(2, 'Y', 100), spot(3, 'Z', 100)];
    expect(sortFavorites(list, 'LAST_VIEWED').map((s) => s.id)).toEqual([1, 2, 3]);
  });

  it('不修改入参', () => {
    const list = [spot(1, 'B'), spot(2, 'A')];
    const copy = [...list];
    sortFavorites(list, 'NAME');
    expect(list).toEqual(copy);
  });
});

describe('parseTags / mergeTags', () => {
  it('多种分隔符 + trim + 去空 + 去重保序', () => {
    expect(parseTags(' 鲈鱼， 路亚、海钓  鲈鱼 ')).toEqual(['鲈鱼', '路亚', '海钓']);
  });

  it('空白 → 空数组', () => {
    expect(parseTags('   ,，、  ')).toEqual([]);
  });

  it('replace 覆盖（空输入 = 清空）', () => {
    expect(mergeTags(['a', 'b'], ['c'], true)).toEqual(['c']);
    expect(mergeTags(['a'], [], true)).toEqual([]);
  });

  it('追加：existing 优先 + 去重', () => {
    expect(mergeTags(['a', 'b'], ['b', 'c'], false)).toEqual(['a', 'b', 'c']);
  });
});

describe('分组', () => {
  it('自定义组按名升序，未分组恒最后', () => {
    const list = [spot(1, 'x', 0, 'B组'), spot(2, 'y', 0, null), spot(3, 'z', 0, 'A组')];
    const groups = groupFavorites(list);
    expect(groups.map((g) => g.name)).toEqual(['A组', 'B组', UNGROUPED_KEY]);
    expect(groups[2]?.isDefault).toBe(true);
  });

  it('组内保持输入序', () => {
    const list = [spot(3, 'z', 0, 'A组'), spot(1, 'x', 0, 'A组')];
    expect(groupFavorites(list)[0]?.spots.map((s) => s.id)).toEqual([3, 1]);
  });

  it('空输入 → 空数组；空白组名归默认组', () => {
    expect(groupFavorites([])).toEqual([]);
    expect(groupNameOf({ name: 'a', lastViewedAt: 1, groupName: '   ' })).toBe(UNGROUPED_KEY);
  });

  it('默认组展示名随语言，自定义组原样', () => {
    expect(displayGroupName(UNGROUPED_KEY, '未分组')).toBe('未分组');
    expect(displayGroupName('水库', '未分组')).toBe('水库');
  });

  it('折叠切换幂等', () => {
    const a = toggleCollapsed('A', new Set());
    expect(a.has('A')).toBe(true);
    expect(toggleCollapsed('A', a).has('A')).toBe(false);
  });

  it('normalizeGroupName：空白 → null', () => {
    expect(normalizeGroupName('  ')).toBeNull();
    expect(normalizeGroupName(' 水库 ')).toBe('水库');
  });

  it('existingGroups 去重排序', () => {
    expect(existingGroups([spot(1, 'a', 0, 'B'), spot(2, 'b', 0, 'A'), spot(3, 'c', 0, 'B')])).toEqual([
      'A',
      'B',
    ]);
  });
});

describe('多选', () => {
  it('toggle 加入/移除', () => {
    const s = toggleSelection(new Set(), 5);
    expect([...s]).toEqual([5]);
    expect([...toggleSelection(s, 5)]).toEqual([]);
  });

  it('全选 / 反选 / 全选判定', () => {
    expect([...selectAll([1, 2])]).toEqual([1, 2]);
    const inverted = invertSelection(new Set([1]), [1, 2, 3]);
    expect([...inverted].sort()).toEqual([2, 3]);
    expect(isAllSelected(new Set([1, 2]), [1, 2])).toBe(true);
    expect(isAllSelected(new Set(), [])).toBe(false);
  });

  it('反选保留域外选择', () => {
    const inverted = invertSelection(new Set([9]), [1, 2]);
    expect([...inverted].sort((a, b) => a - b)).toEqual([1, 2, 9]);
  });

  it('selectedIds 按可见序输出', () => {
    expect(selectedIds([spot(3, 'c'), spot(1, 'a')], new Set([1, 3]))).toEqual([3, 1]);
  });
});