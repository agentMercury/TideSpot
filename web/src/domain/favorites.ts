/**
 * 收藏列表纯函数层 —— 与 Android `feature/favorites/favorites/Favorites*Logic.kt` 对齐。
 * 只做排序 / 分组 / 标签 / 多选；UI 与文案在页面层，保持可单测。
 *
 * 红线：
 * - 排序一律稳定（并列保持输入序）；
 * - 缺值（未加载 / 无窗口 / lastViewedAt=0）排最后，不按 0 参与比较（§5.0 禁 0 填充）；
 * - 组内顺序沿用调用方传入顺序（排序与分组正交）。
 */
export type SortOrder = 'NAME' | 'LAST_VIEWED' | 'SCORE' | 'RECOMMENDED';

export interface FavoriteLike {
  id?: number;
  name: string;
  lastViewedAt: number;
  groupName?: string | null;
  tags?: string[];
}

/** 默认组内部键：不落库、不翻译；展示名由调用方注入（`ungroupedLabel`）。 */
export const UNGROUPED_KEY = '__ungrouped__';

export interface FavoriteGroup<T> {
  name: string;
  spots: T[];
  isDefault: boolean;
}

/** 升/降序比较，null|undefined 恒排最后且互不比较。 */
function compareMissingLast(a: number | null | undefined, b: number | null | undefined): number {
  const aMissing = a == null || a === 0;
  const bMissing = b == null || b === 0;
  if (aMissing && bMissing) return 0;
  if (aMissing) return 1;
  if (bMissing) return -1;
  return (b as number) - (a as number);
}

export function sortFavorites<T extends FavoriteLike>(
  spots: ReadonlyArray<T>,
  order: SortOrder,
  scoreBySpotId: ReadonlyMap<number, number> = new Map(),
  rankBySpotId: ReadonlyMap<number, number> = new Map(),
): T[] {
  const list = [...spots];
  switch (order) {
    case 'NAME':
      return list.sort((a, b) => a.name.localeCompare(b.name));
    case 'LAST_VIEWED':
      return list.sort((a, b) => compareMissingLast(a.lastViewedAt, b.lastViewedAt));
    case 'SCORE':
      return list.sort((a, b) =>
        compareMissingLast(
          a.id == null ? undefined : scoreBySpotId.get(a.id),
          b.id == null ? undefined : scoreBySpotId.get(b.id),
        ),
      );
    case 'RECOMMENDED':
      return list.sort((a, b) => {
        const aRank = a.id == null ? undefined : rankBySpotId.get(a.id);
        const bRank = b.id == null ? undefined : rankBySpotId.get(b.id);
        const byRank = compareMissingLast(aRank, bRank);
        if (aRank == null || bRank == null || byRank !== 0) return byRank;
        // 同 rank → 名称升序（与 Android 一致）
        return a.name.localeCompare(b.name);
      });
  }
}

/**
 * 标签输入解析：`,` `，` `、` 空格分隔 → trim → 去空 → 去重（保持首次出现序）。
 * 空白输入 → 空数组（非 null 契约）。
 */
export function parseTags(raw: string): string[] {
  const parts = raw.split(/[,，、\s]+/);
  const out: string[] = [];
  for (const part of parts) {
    const v = part.trim();
    if (v && !out.includes(v)) out.push(v);
  }
  return out;
}

/** 单点归属组名（null/空白 → 默认组内部键）。 */
export function groupNameOf(spot: FavoriteLike): string {
  const v = spot.groupName?.trim();
  return v && v.length > 0 ? v : UNGROUPED_KEY;
}

/** 组名 → 展示名（默认组用传入文案；自定义组原样）。 */
export function displayGroupName(groupName: string, ungroupedLabel: string): string {
  return groupName === UNGROUPED_KEY ? ungroupedLabel : groupName;
}

/** 分组：自定义组按名称升序，默认组恒排最后；空组不产出；组内保持输入序。 */
export function groupFavorites<T extends FavoriteLike>(sortedSpots: ReadonlyArray<T>): Array<FavoriteGroup<T>> {
  if (sortedSpots.length === 0) return [];
  const buckets = new Map<string, T[]>();
  for (const spot of sortedSpots) {
    const key = groupNameOf(spot);
    const bucket = buckets.get(key);
    if (bucket) bucket.push(spot);
    else buckets.set(key, [spot]);
  }
  const custom = [...buckets.keys()].filter((k) => k !== UNGROUPED_KEY).sort((a, b) => a.localeCompare(b));
  const ordered = buckets.has(UNGROUPED_KEY) ? [...custom, UNGROUPED_KEY] : custom;
  return ordered.map((name) => ({
    name,
    spots: buckets.get(name) as T[],
    isDefault: name === UNGROUPED_KEY,
  }));
}

/** 折叠切换（纯函数）。 */
export function toggleCollapsed(groupName: string, collapsed: ReadonlySet<string>): Set<string> {
  const next = new Set(collapsed);
  if (next.has(groupName)) next.delete(groupName);
  else next.add(groupName);
  return next;
}

/** 改分组写入值归一：空白 → null（= 移回未分组）。 */
export function normalizeGroupName(raw: string | null | undefined): string | null {
  const v = raw?.trim();
  return v && v.length > 0 ? v : null;
}

// ---------------- 多选（D16：长按进入多选） ----------------

export function toggleSelection(selection: ReadonlySet<number>, id: number): Set<number> {
  const next = new Set(selection);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  return next;
}

export function selectAll(ids: ReadonlyArray<number>): Set<number> {
  return new Set(ids);
}

/** 反选：可见域内翻转，域外既有选择保留。 */
export function invertSelection(selection: ReadonlySet<number>, ids: ReadonlyArray<number>): Set<number> {
  const visible = new Set(ids);
  const next = new Set<number>();
  for (const id of selection) if (!visible.has(id)) next.add(id);
  for (const id of visible) if (!selection.has(id)) next.add(id);
  return next;
}

/** 空域 → false（防「0 项全选」误显示）。 */
export function isAllSelected(selection: ReadonlySet<number>, ids: ReadonlyArray<number>): boolean {
  return ids.length > 0 && ids.every((id) => selection.has(id));
}

/**
 * 批量改标签：replace=true 覆盖（空输入 = 清空）；false 追加（existing 优先，输入去重）。
 */
export function mergeTags(existing: ReadonlyArray<string>, input: ReadonlyArray<string>, replace: boolean): string[] {
  const normalizedInput: string[] = [];
  for (const raw of input) {
    const v = raw.trim();
    if (v && !normalizedInput.includes(v)) normalizedInput.push(v);
  }
  if (replace) return normalizedInput;
  const normalizedExisting: string[] = [];
  for (const raw of existing) {
    const v = raw.trim();
    if (v && !normalizedExisting.includes(v)) normalizedExisting.push(v);
  }
  const merged = [...normalizedExisting];
  for (const v of normalizedInput) if (!merged.includes(v)) merged.push(v);
  return merged;
}

/** 批量删除的 id（按可见序，保证与用户所见一致）。 */
export function selectedIds(visible: ReadonlyArray<FavoriteLike>, selection: ReadonlySet<number>): number[] {
  const out: number[] = [];
  for (const spot of visible) {
    if (spot.id != null && selection.has(spot.id)) out.push(spot.id);
  }
  return out;
}

/** 批量改分组的候选（既有分组名去重排序；空 → 空数组）。 */
export function existingGroups(spots: ReadonlyArray<FavoriteLike>): string[] {
  const set = new Set<string>();
  for (const spot of spots) {
    const v = spot.groupName?.trim();
    if (v) set.add(v);
  }
  return [...set].sort((a, b) => a.localeCompare(b));
}