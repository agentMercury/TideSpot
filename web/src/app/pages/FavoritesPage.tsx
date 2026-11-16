/**
 * S08 收藏页（G-05）—— 对齐 Android `FavoritesScreen` 的核心能力：
 * 排序（名称/最近/分数/综合推荐）、搜索、分组折叠、窗口摘要、标签与分组编辑、批量选择/删除/改标签/改分组、刷新全部。
 *
 * 数据口径：摘要读本地 hourly_score（无网可用），不触发网络；只有「刷新全部」会发请求。
 */
import { useCallback, useEffect, useMemo, useState, type CSSProperties } from 'react';
import { repository } from '../container';
import { navigate } from '../router';
import { DetailPage } from './DetailPage';
import { useEnvironment } from '../environment/EnvironmentProvider';
import { BottomSheet, Button, Card, Chip, EmptyState } from '../../ui/primitives/primitives';
import { useSkin } from '../../ui/skin';
import { IcFishFieldguide, IcFishFieldnotes } from '../../ui/icons';
import { format, t, useLang } from '../../ui/i18n';
import { findWindows } from '../../domain/bestWindows';
import { resolveScoreBand } from '../../ui/tokens/bands';
import {
  displayGroupName,
  existingGroups,
  groupFavorites,
  invertSelection,
  isAllSelected,
  mergeTags,
  normalizeGroupName,
  parseTags,
  selectedIds,
  sortFavorites,
  toggleCollapsed,
  toggleSelection,
  type SortOrder,
} from '../../domain/favorites';
import type { SpotRow } from '../../data/db';

interface SpotBest {
  peak: number;
  rank: number;
  peakEpochSec: number;
  tier: string;
  tzSec: number;
}

const SORTS: Array<{ id: SortOrder; key: string }> = [
  { id: 'RECOMMENDED', key: 'ts_fav_sort_recommended' },
  { id: 'LAST_VIEWED', key: 'ts_fav_sort_recent' },
  { id: 'SCORE', key: 'ts_fav_sort_score' },
  { id: 'NAME', key: 'ts_fav_sort_name' },
];

/** 水体枚举 → 可读标签（避免直接把 OCEAN/UNKNOWN 这类协议值显示给用户） */
const WATER_LABEL: Record<string, { zh: string; en: string }> = {
  OCEAN: { zh: '外海', en: 'Ocean' },
  NEARSHORE: { zh: '近岸', en: 'Nearshore' },
  INLAND_WATER: { zh: '内陆水域', en: 'Inland' },
  LAND: { zh: '陆地', en: 'Land' },
};

const MONTH_EN = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

/** 「M月d日 HH:mm」（zh）/「Mon d, HH:mm」（en），按点位时区渲染。 */
function fmtLocal(epochSec: number, tzSec: number, lang: 'zh' | 'en'): string {
  const d = new Date((epochSec + tzSec) * 1000);
  const hh = `${pad2(d.getUTCHours())}:${pad2(d.getUTCMinutes())}`;
  if (lang === 'en') return `${MONTH_EN[d.getUTCMonth()]} ${d.getUTCDate()}, ${hh}`;
  return `${d.getUTCMonth() + 1}月${d.getUTCDate()}日 ${hh}`;
}

function maxFetchedAt(map: Partial<Record<string, number>> | undefined): number {
  if (!map) return 0;
  let best = 0;
  for (const v of Object.values(map)) if (typeof v === 'number' && v > best) best = v;
  return best;
}

export function FavoritesPage() {
  const { env } = useEnvironment();
  const [lang] = useLang();
  const [skin] = useSkin();
  const [spots, setSpots] = useState<SpotRow[]>([]);
  const [bests, setBests] = useState<Map<number, SpotBest>>(new Map());
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [order, setOrder] = useState<SortOrder>('RECOMMENDED');
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
  const [selection, setSelection] = useState<Set<number>>(new Set());
  const [editing, setEditing] = useState<{ kind: 'tag' | 'group'; ids: number[] } | null>(null);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  /** 桌面档：右侧详情选择（不跳路由，左侧列表保持不动） */
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [progress, setProgress] = useState({ done: 0, total: 0 });
  const [message, setMessage] = useState<string | null>(null);
  const isDesktop = env.kind === 'desktop';

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const list = await repository.listFavorites();
      setSpots(list);
      const map = new Map<number, SpotBest>();
      for (const spot of list) {
        if (spot.id == null) continue;
        const rows = await repository.readScores(spot.id);
        const windows = findWindows(rows);
        const best = windows[0];
        if (best) {
          map.set(spot.id, {
            peak: best.peak,
            rank: best.rank,
            peakEpochSec: best.peakEpochSec,
            tier: best.tier,
            tzSec: spot.tzOffsetSec ?? 8 * 3600,
          });
        }
      }
      setBests(map);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return spots;
    return spots.filter(
      (s) => s.name.toLowerCase().includes(q) || (s.memo ?? '').toLowerCase().includes(q),
    );
  }, [spots, query]);

  const scoreMap = useMemo(() => {
    const m = new Map<number, number>();
    for (const [id, b] of bests) m.set(id, b.peak);
    return m;
  }, [bests]);

  const rankMap = useMemo(() => {
    const m = new Map<number, number>();
    for (const [id, b] of bests) m.set(id, b.rank);
    return m;
  }, [bests]);

  const sorted = useMemo(() => sortFavorites(filtered, order, scoreMap, rankMap), [filtered, order, scoreMap, rankMap]);
  const groups = useMemo(() => groupFavorites(sorted), [sorted]);
  const visibleIds = useMemo(
    () => sorted.map((s) => s.id).filter((id): id is number => id != null),
    [sorted],
  );

  const ungroupedLabel = t('ts_fav_ungrouped', lang);
  const selecting = selection.size > 0;

  const summaryOf = useCallback(
    (spot: SpotRow): string => {
      const best = spot.id == null ? undefined : bests.get(spot.id);
      if (!best) return t('ts_fav_summary_not_loaded', lang);
      // A / D / E / F：分数与档位移到卡片右列，摘要只留「最佳窗口时间」
      if (skin === 'a-vintage' || skin === 'd-fieldguide' || skin === 'e-tidejournal' || skin === 'f-bluetide') {
        return fmtLocal(best.peakEpochSec, best.tzSec, lang);
      }
      return format(
        'ts_fav_summary_line',
        [fmtLocal(best.peakEpochSec, best.tzSec, lang), best.peak, best.tier],
        lang,
      );
    },
    [bests, lang, skin],
  );

  const removeOne = async (id: number) => {
    if (!window.confirm(format('ts_fav_delete_confirm', [spots.find((s) => s.id === id)?.name ?? ''], lang))) {
      return;
    }
    await repository.removeFavorite(id);
    setSelection((prev) => {
      const next = new Set(prev);
      next.delete(id);
      return next;
    });
    await load();
  };

  const refreshAll = async () => {
    if (typeof navigator !== 'undefined' && !navigator.onLine) {
      setMessage(t('ts_fav_refresh_offline', lang));
      return;
    }
    const total = spots.length;
    let done = 0;
    let refreshed = 0;
    let upToDate = 0;
    let failed = 0;
    setRefreshing(true);
    setProgress({ done: 0, total });
    setMessage(null);
    for (const spot of spots) {
      if (spot.id == null) {
        done += 1;
        setProgress({ done, total });
        continue;
      }
      try {
        const before = await repository.readCachedDetail(spot.id);
        const beforeAt = maxFetchedAt(before?.fetchedAtMillis);
        const after = await repository.getDetail(spot.id);
        const afterAt = maxFetchedAt(after.fetchedAtMillis);
        if (afterAt > beforeAt) refreshed += 1;
        else upToDate += 1;
      } catch {
        failed += 1;
      }
      done += 1;
      setProgress({ done, total });
    }
    setMessage(
      failed > 0
        ? format('ts_fav_refresh_summary_failed', [refreshed, upToDate, failed], lang)
        : format('ts_fav_refresh_summary', [refreshed, upToDate], lang),
    );
    setRefreshing(false);
    await load();
  };

  const openEditor = (kind: 'tag' | 'group', ids: number[]) => {
    setEditing({ kind, ids });
    setDraft('');
  };

  const applyEditor = async () => {
    if (!editing || editing.ids.length === 0) return;
    setBusy(true);
    setMessage(null);
    try {
      for (const id of editing.ids) {
        const spot = spots.find((s) => s.id === id);
        if (!spot) continue;
        if (editing.kind === 'tag') {
          const patch = mergeTags(spot.tags ?? [], parseTags(draft), false);
          await repository.updateFavorite(id, { tags: patch });
        } else {
          await repository.updateFavorite(id, { groupName: normalizeGroupName(draft) });
        }
      }
      setEditing(null);
      setDraft('');
      await load();
    } catch {
      setMessage(
        t(editing.kind === 'tag' ? 'ts_fav_err_batch_tag' : 'ts_fav_err_batch_group', lang),
      );
    } finally {
      setBusy(false);
    }
  };

  const batchDelete = async () => {
    const ids = selectedIds(sorted, selection);
    if (ids.length === 0) return;
    if (!window.confirm(format('ts_fav_batch_delete_confirm', [ids.length], lang))) return;
    setBusy(true);
    try {
      for (const id of ids) await repository.removeFavorite(id);
      setSelection(new Set());
      await load();
    } catch {
      setMessage(t('ts_fav_err_batch_delete', lang));
    } finally {
      setBusy(false);
    }
  };

  /** 整卡点击进入详情：手机/平板跳路由，桌面在右栏内嵌（App：点卡片任意处即进详情） */
  const openDetail = (id: number) => {
    if (isDesktop) setSelectedId(id);
    else navigate(`#/spot/${id}`);
  };

  const renderCard = (spot: SpotRow) => {
    const id = spot.id;
    const selected = id != null && selection.has(id);
    const best = id != null ? bests.get(id) : undefined;
    const band = best ? resolveScoreBand(best.peak) : null;
    return (
      <article
        key={id}
        className={`fav-card${selected ? ' is-selected' : ''}`}
        role="button"
        tabIndex={id == null ? -1 : 0}
        aria-label={`${spot.name}，${summaryOf(spot)}`}
        style={band ? ({ '--fav-band': band.color } as CSSProperties) : undefined}
        onClick={(e) => {
          if (id == null) return;
          if (e.target instanceof HTMLElement && e.target.closest('button')) return; // 操作按钮不触发进详情
          openDetail(id);
        }}
        onKeyDown={(e) => {
          if (id == null) return;
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            openDetail(id);
          }
        }}
      >
        <button
          type="button"
          className={`fav-card__check${selected ? ' is-on' : ''}`}
          aria-label={spot.name}
          aria-pressed={selected}
          disabled={id == null}
          onClick={() => id != null && setSelection((prev) => toggleSelection(prev, id))}
        >
          {selected ? '✓' : ''}
        </button>
        <div className="fav-card__main">
          <div className="fav-card__title">
            <strong>{spot.name}</strong>
            {spot.waterBody && spot.waterBody !== 'UNKNOWN' && <Chip>{WATER_LABEL[spot.waterBody]?.[lang] ?? spot.waterBody}</Chip>}
            {spot.groupName && <Chip tone="mint">{spot.groupName}</Chip>}
          </div>
          <p className="fav-card__coord">
            {spot.lat.toFixed(4)}, {spot.lon.toFixed(4)}
          </p>
          <p className="fav-card__summary">{summaryOf(spot)}</p>
          {(spot.tags ?? []).length > 0 && (
            <div className="fav-card__tags">
              {(spot.tags ?? []).map((tag) => (
                <span key={tag} className="fav-tag">
                  {tag}
                </span>
              ))}
            </div>
          )}
        </div>
        <div className="fav-card__score" aria-label={best ? `峰值 ${best.peak}，${best.tier}` : undefined}>
          <b>{best ? best.peak : '—'}</b>
          <span>{best ? best.tier : t('ts_fav_summary_not_loaded', lang)}</span>
        </div>
        <IcFishFieldguide className="fav-card__fish" size={30} />
        <IcFishFieldnotes className="fav-card__fish--notes" size={22} />
        <div className="fav-card__actions">
          <Button variant="quiet" size="sm" onClick={() => id != null && openDetail(id)}>
            {t('ts_map_star_detail', lang)}
          </Button>
          <Button variant="quiet" size="sm" onClick={() => id != null && openEditor('tag', [id])}>
            {t('ts_fav_batch_tag', lang)}
          </Button>
          <Button variant="quiet" size="sm" onClick={() => id != null && openEditor('group', [id])}>
            {t('ts_fav_batch_group', lang)}
          </Button>
          <Button variant="quiet" size="sm" onClick={() => id != null && removeOne(id)} disabled={busy}>
            {t('ts_fav_delete_action', lang)}
          </Button>
        </div>
      </article>
    );
  };

  return (
    <div className={`fav-page${isDesktop ? ' fav-page--desk' : ''}`}>
      <div className="fav-list-col">
      <div className="fav-toolbar">
        <input
          className="fav-search"
          type="search"
          value={query}
          placeholder={t('ts_fav_search_hint', lang)}
          aria-label={t('ts_fav_search_desc', lang)}
          onChange={(e) => setQuery(e.target.value)}
        />
        <div className="row-gap" role="group" aria-label={format('ts_fav_sort_desc', [''], lang)}>
          {SORTS.map((s) => (
            <button
              key={s.id}
              type="button"
              className={`ghost-button ${order === s.id ? 'is-active' : ''}`}
              onClick={() => setOrder(s.id)}
            >
              {t(s.key, lang)}
            </button>
          ))}
        </div>
        <Button variant="primary" size="sm" onClick={refreshAll} disabled={refreshing || spots.length === 0}>
          {refreshing
            ? format('ts_fav_refreshing', [progress.done, progress.total], lang)
            : t('ts_fav_refresh_all', lang)}
        </Button>
      </div>

      {selecting && (
        <div className="fav-batch" role="toolbar" aria-label={t('ts_fav_selected_count', lang)}>
          <strong>
            {format('ts_fav_selected_count', [selection.size], lang)}
            {isAllSelected(selection, visibleIds) ? ` · ${t('ts_fav_all_selected', lang)}` : ''}
          </strong>
          <Button variant="quiet" size="sm" onClick={() => setSelection(new Set(visibleIds))}>
            {t('ts_fav_select_all', lang)}
          </Button>
          <Button variant="quiet" size="sm" onClick={() => setSelection((prev) => invertSelection(prev, visibleIds))}>
            {t('ts_fav_invert', lang)}
          </Button>
          <Button variant="quiet" size="sm" onClick={() => openEditor('tag', [...selection])}>
            {t('ts_fav_batch_tag', lang)}
          </Button>
          <Button variant="quiet" size="sm" onClick={() => openEditor('group', [...selection])}>
            {t('ts_fav_batch_group', lang)}
          </Button>
          <Button variant="quiet" size="sm" onClick={batchDelete} disabled={busy}>
            {t('ts_fav_batch_delete', lang)}
          </Button>
          <Button variant="quiet" size="sm" onClick={() => setSelection(new Set())}>
            {t('ts_map_star_close', lang)}
          </Button>
        </div>
      )}

      {message && (
        <p className="fav-message" role="status">
          {message}
        </p>
      )}

      {loading ? (
        <p className="hint">{t('ts_detail_loading', lang)}</p>
      ) : spots.length === 0 ? (
        <Card ariaLabel={t('ts_fav_empty', lang)}>
          <EmptyState title={t('ts_fav_empty', lang)} />
        </Card>
      ) : sorted.length === 0 ? (
        <Card ariaLabel={t('ts_fav_no_match', lang)}>
          <EmptyState title={t('ts_fav_no_match', lang)} />
        </Card>
      ) : (
        groups.map((group) => {
          const isCollapsed = collapsed.has(group.name);
          return (
            <section key={group.name} className="fav-group">
              <button
                type="button"
                className="fav-group__head"
                aria-expanded={!isCollapsed}
                onClick={() => setCollapsed((prev) => toggleCollapsed(group.name, prev))}
              >
                <span className="fav-group__caret">{isCollapsed ? '▸' : '▾'}</span>
                <strong>{displayGroupName(group.name, ungroupedLabel)}</strong>
                <span className="hint">
                  {format('ts_fav_group_desc', [displayGroupName(group.name, ungroupedLabel), group.spots.length], lang)}
                </span>
              </button>
              {!isCollapsed && <div className="fav-group__body">{group.spots.map(renderCard)}</div>}
            </section>
          );
        })
      )}

      </div>

      {isDesktop && (
        <div className="fav-detail-col">
          {selectedId != null ? (
            <DetailPage spotId={selectedId} embedded />
          ) : (
            <Card ariaLabel="选择收藏点">
              <p className="hint">
                {lang === 'en' ? 'Select a spot on the left to see its details.' : '从左侧选择收藏点查看详情。'}
              </p>
            </Card>
          )}
        </div>
      )}

      <BottomSheet
        open={editing != null}
        title={
          editing == null
            ? ''
            : editing.kind === 'tag'
              ? format('ts_fav_batch_tag_title', [editing.ids.length], lang)
              : format('ts_fav_batch_group_title', [editing.ids.length], lang)
        }
        onClose={() => setEditing(null)}
      >
        <label className="stack" style={{ gap: 8 }}>
          <span className="hint">
            {editing?.kind === 'tag'
              ? t('ts_fav_batch_tags_label', lang)
              : t('ts_fav_group_name_label', lang)}
          </span>
          <input
            className="fav-editor-input"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder={editing?.kind === 'tag' ? '鲈鱼、路亚' : ungroupedLabel}
            aria-label={editing?.kind === 'tag' ? t('ts_fav_batch_tags_input', lang) : t('ts_fav_batch_group_input', lang)}
          />
          {editing?.kind === 'group' && existingGroups(spots).length > 0 && (
            <div className="row-gap">
              {existingGroups(spots).map((g) => (
                <button key={g} type="button" className="ghost-button" onClick={() => setDraft(g)}>
                  {format('ts_fav_existing_group', [g], lang)}
                </button>
              ))}
            </div>
          )}
          <div className="row-gap">
            <Button variant="primary" size="sm" onClick={applyEditor} disabled={busy}>
              {t('ts_map_confirm', lang)}
            </Button>
            <Button variant="quiet" size="sm" onClick={() => setEditing(null)}>
              {t('ts_map_star_close', lang)}
            </Button>
          </div>
        </label>
      </BottomSheet>
    </div>
  );
}