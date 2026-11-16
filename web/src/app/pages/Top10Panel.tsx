/**
 * 跨点 Top10（S01 叠加层）：横向条 + Sheet + 桌面侧栏列表。
 * 数据来自本地 hourly_score 缓存；不主动为 Top10 触发网络请求，避免请求风暴。
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { repository } from '../container';
import { navigate } from '../router';
import { BottomSheet, Button, Card, Chip } from '../../ui/primitives/primitives';
import { SLOT_LABELS, filterByHourRange, filterBySlot, findWindows, type TimeSlot, type WindowEntry } from '../../domain/bestWindows';

export interface SpotWindow extends WindowEntry {
  spotId: number;
  spotName: string;
  tzSec: number;
}

export function useTopWindows() {
  const [windows, setWindows] = useState<SpotWindow[]>([]);
  const [loading, setLoading] = useState(true);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const spots = await repository.listFavorites();
      const all: SpotWindow[] = [];
      for (const spot of spots) {
        if (spot.id == null) continue;
        const scores = await repository.readScores(spot.id);
        if (scores.length === 0) continue;
        const tzSec = spot.tzOffsetSec ?? 8 * 3600;
        for (const w of findWindows(scores)) {
          all.push({ ...w, spotId: spot.id, spotName: spot.name, tzSec });
        }
      }
      all.sort((a, b) => b.rank - a.rank);
      setWindows(all.slice(0, 10));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { windows, loading, reload };
}

function fmtWindow(w: SpotWindow): string {
  const tz = w.tzSec;
  const hm = (epoch: number) => {
    const d = new Date((epoch + tz) * 1000);
    return `${String(d.getUTCHours()).padStart(2, '0')}:00`;
  };
  return `${hm(w.startEpochSec)}–${hm(w.endEpochSec)}`;
}

export function Top10Strip({ windows, onOpen }: { windows: SpotWindow[]; onOpen: () => void }) {
  if (windows.length === 0) return null;
  return (
    <button type="button" className="top10-strip" onClick={onOpen}>
      <span className="top10-strip__label">Top10</span>
      <span className="top10-strip__item">
        {windows[0].spotName} · {fmtWindow(windows[0])} · {windows[0].peak}
      </span>
      <span className="top10-strip__more">查看全部 ›</span>
    </button>
  );
}

export function Top10List({
  windows,
  slot,
  onSlotChange,
  onSelect,
  loading,
  onReload,
}: {
  windows: SpotWindow[];
  slot: TimeSlot;
  onSlotChange: (slot: TimeSlot) => void;
  onSelect: (spotId: number) => void;
  loading: boolean;
  onReload: () => void;
}) {
  // G-18 自定义时段：按窗口「起点」所在本地小时过滤；from > to 视为跨午夜
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const filtered = useMemo(() => {
    const fromHour = from ? Number(from.slice(0, 2)) : null;
    const toHour = to ? Number(to.slice(0, 2)) : null;
    return filterByHourRange(filterBySlot(windows, slot), fromHour, toHour);
  }, [windows, slot, from, to]);

  return (
    <div className="top10-list">
      <div className="row-between">
        <strong>跨点 Top10</strong>
        <Button variant="quiet" size="sm" onClick={onReload} disabled={loading}>
          刷新
        </Button>
      </div>
      <div className="row-gap">
        {(Object.keys(SLOT_LABELS) as TimeSlot[]).map((s) => (
          <button
            key={s}
            type="button"
            className={`ghost-button ${slot === s ? 'is-active' : ''}`}
            onClick={() => onSlotChange(s)}
          >
            {SLOT_LABELS[s]}
          </button>
        ))}
      </div>

      <div className="top10-range">
        <span className="top10-range__label">自定义</span>
        <input
          type="time"
          step={3600}
          className="top10-range__input"
          aria-label="自定义起始时间"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
        />
        <span className="top10-range__sep">–</span>
        <input
          type="time"
          step={3600}
          className="top10-range__input"
          aria-label="自定义结束时间"
          value={to}
          onChange={(e) => setTo(e.target.value)}
        />
        {(from || to) && (
          <button
            type="button"
            className="ghost-button"
            onClick={() => {
              setFrom('');
              setTo('');
            }}
          >
            清除
          </button>
        )}
      </div>

      {loading ? (
        <p className="hint">读取本地评分缓存…</p>
      ) : filtered.length === 0 ? (
        <Card ariaLabel="空态">
          <p className="hint">暂无适合窗口（峰值需 ≥65）。打开收藏点详情并等待一次刷新后，这里会按 rank 排序。</p>
        </Card>
      ) : (
        <ol className="top10-rows">
          {filtered.map((w) => (
            <li key={`${w.spotId}-${w.startEpochSec}`}>
              <button type="button" className="top10-row" onClick={() => onSelect(w.spotId)}>
                <span className="top10-row__name">{w.spotName}</span>
                <span className="top10-row__window">{fmtWindow(w)}</span>
                <Chip>{w.tier}</Chip>
                <span className="top10-row__peak">{w.peak}</span>
              </button>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

export function Top10Sheet({
  open,
  windows,
  onClose,
  loading,
  onReload,
}: {
  open: boolean;
  windows: SpotWindow[];
  onClose: () => void;
  loading: boolean;
  onReload: () => void;
}) {
  const [slot, setSlot] = useState<TimeSlot>('all');
  return (
    <BottomSheet open={open} title="跨点 Top10" onClose={onClose}>
      <Top10List
        windows={windows}
        slot={slot}
        onSlotChange={setSlot}
        onSelect={(id) => {
          onClose();
          navigate(`#/spot/${id}`);
        }}
        loading={loading}
        onReload={onReload}
      />
    </BottomSheet>
  );
}