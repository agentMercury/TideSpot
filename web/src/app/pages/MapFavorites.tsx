/**
 * G-02 收藏星标图层：把 IndexedDB 里的收藏点画到地图上，点击弹出星标气泡。
 * 气泡摘要读本地 hourly_score 缓存（未来 24h 最高分），不发网络请求。
 */
import { useEffect, useRef, useState } from 'react';
import { Marker, type Map as MapLibreMap } from 'maplibre-gl';
import type { SpotRow } from '../../data/db';
import { repository } from '../container';
import { format, t, useLang } from '../../ui/i18n';
import { Button } from '../../ui/primitives/primitives';

function starElement(name: string, onClick: () => void): HTMLButtonElement {
  const el = document.createElement('button');
  el.type = 'button';
  el.className = 'map-star';
  el.textContent = '★';
  el.title = name;
  el.setAttribute('aria-label', name);
  el.addEventListener('click', (e) => {
    e.stopPropagation();
    onClick();
  });
  return el;
}

export function MapFavorites({
  map,
  onOpenDetail,
}: {
  map: MapLibreMap | null;
  onOpenDetail: (spotId: number) => void;
}) {
  const [lang] = useLang();
  const [spots, setSpots] = useState<SpotRow[]>([]);
  const [selected, setSelected] = useState<SpotRow | null>(null);
  const [summary, setSummary] = useState<string | null>(null);
  const [loadingSummary, setLoadingSummary] = useState(false);
  const selectedRef = useRef<SpotRow | null>(null);

  // 收藏变化后重画（进入地图页时读一次；详情页新增后返回也会重读）
  useEffect(() => {
    let alive = true;
    void repository.listFavorites().then((rows) => {
      if (alive) setSpots(rows.filter((s) => s.id != null));
    });
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    if (!map) return;
    const markers = spots.map((spot) => {
      const el = starElement(spot.name, () => {
        selectedRef.current = spot;
        setSelected(spot);
      });
      return new Marker({ element: el, anchor: 'bottom' })
        .setLngLat([spot.lon, spot.lat])
        .addTo(map);
    });
    return () => {
      for (const m of markers) m.remove();
    };
  }, [map, spots]);

  useEffect(() => {
    selectedRef.current = selected;
    if (!selected || selected.id == null) {
      setSummary(null);
      return;
    }
    let alive = true;
    setLoadingSummary(true);
    setSummary(null);
    void repository
      .readScores(selected.id)
      .then((rows) => {
        if (!alive) return;
        const now = Math.floor(Date.now() / 1000);
        const next = rows.filter(
          (r) => r.timeEpochSec >= now && r.timeEpochSec < now + 86400,
        );
        if (next.length === 0) {
          setSummary(null);
          return;
        }
        const best = Math.max(...next.map((r) => r.total));
        setSummary(format('ts_fav_window_peak', [best, t('ts_detail_filter_24h', lang)], lang));
      })
      .catch(() => {
        if (alive) setSummary(null);
      })
      .finally(() => {
        if (alive) setLoadingSummary(false);
      });
    return () => {
      alive = false;
    };
  }, [selected, lang]);

  if (!selected) return null;

  const id = selected.id;
  return (
    <div className="map-star-pop" role="dialog" aria-label={selected.name}>
      <div className="map-star-pop__head">
        <strong className="map-star-pop__name">{selected.name}</strong>
        <button
          type="button"
          className="map-star-pop__close"
          aria-label={t('ts_map_star_close', lang)}
          onClick={() => setSelected(null)}
        >
          ×
        </button>
      </div>
      <div className="map-star-pop__meta">
        {selected.lat.toFixed(5)}, {selected.lon.toFixed(5)}
        {selected.waterBody && selected.waterBody !== 'UNKNOWN' && (
          <span className="map-star-pop__tag">{selected.waterBody}</span>
        )}
      </div>
      <p className="map-star-pop__summary">
        {loadingSummary
          ? t('ts_detail_loading', lang)
          : (summary ?? t('ts_map_star_no_summary', lang))}
      </p>
      <div className="map-star-pop__actions">
        <Button
          variant="primary"
          size="sm"
          disabled={id == null}
          onClick={() => {
            if (id == null) return;
            setSelected(null);
            onOpenDetail(id);
          }}
        >
          {t('ts_map_star_detail', lang)}
        </Button>
      </div>
    </div>
  );
}