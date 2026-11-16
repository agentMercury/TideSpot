/**
 * S01 地图页（MapLibre GL JS）。
 * 契约：准星是唯一选点真源（F-1.3）；点击/长按只是移动地图，选点坐标始终取地图中心。
 * 底图三样式与 Android 共用 OpenFreeMap；OSTIA 为可选叠加层。
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Map as MapLibreMap, Marker, NavigationControl, setWorkerUrl } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import {
  CARTO_COLOR_STYLE_URL,
  CARTO_DARK_STYLE_URL,
  CARTO_PLAIN_STYLE_URL,
  OPENFREEMAP_COLOR_STYLE_URL,
  OPENFREEMAP_DARK_STYLE_URL,
  OPENFREEMAP_PLAIN_STYLE_URL,
  ostiaTileTemplate,
} from '../../data/urls';
import { getSettings, useSettings, updateSettings, type MapProvider } from '../../ui/settings';
import { reverseGeocode, type PlaceName } from '../../data/geo';
import { repository } from '../container';
import { navigate } from '../router';
import { BottomSheet, Button, Card, Chip } from '../../ui/primitives/primitives';
import { useEnvironment } from '../environment/EnvironmentProvider';
import { Top10List, Top10Sheet, Top10Strip, useTopWindows } from './Top10Panel';
import { NextWindowStrip } from './NextWindowStrip';
import { MapSearch } from './MapSearch';
import { MapFavorites } from './MapFavorites';
import { t, useLang } from '../../ui/i18n';
import { IcCrosshairFieldguide, IcFishFieldguide } from '../../ui/icons';
import type { TimeSlot } from '../../domain/bestWindows';

type StyleId = 'color' | 'plain' | 'dark';

const STYLE_LABELS: Record<StyleId, { zh: string; en: string }> = {
  color: { zh: '彩色', en: 'Color' },
  plain: { zh: '素色', en: 'Plain' },
  dark: { zh: '深色', en: 'Dark' },
};

/** G-07：底图提供方（与 Android `MapProvider` 同义，可整组替换） */
const STYLE_URLS: Record<MapProvider, Record<StyleId, string>> = {
  openfreemap: {
    color: OPENFREEMAP_COLOR_STYLE_URL,
    plain: OPENFREEMAP_PLAIN_STYLE_URL,
    dark: OPENFREEMAP_DARK_STYLE_URL,
  },
  carto: {
    color: CARTO_COLOR_STYLE_URL,
    plain: CARTO_PLAIN_STYLE_URL,
    dark: CARTO_DARK_STYLE_URL,
  },
};

const DEFAULT_CENTER = { lat: 36.06, lon: 120.38 };

// 打包后 maplibre 的 worker URL 会失效：显式指向 public 下的副本（随构建输出）
setWorkerUrl(`${import.meta.env.BASE_URL}maplibre-gl-worker.mjs`);

function todayUtcDate(): string {
  const d = new Date();
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`;
}

export function MapPage() {
  const { env } = useEnvironment();
  const [lang] = useLang();
  const L = (zh: string, en: string) => (lang === 'en' ? en : zh);
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const [map, setMap] = useState<MapLibreMap | null>(null);
  const [mapLoaded, setMapLoaded] = useState(false);
  const markerRef = useRef<Marker | null>(null);
  const [center, setCenter] = useState(DEFAULT_CENTER);
  const [place, setPlace] = useState<PlaceName | null>(null);
  const settings = useSettings();
  const [styleId, setStyleId] = useState<StyleId>(getSettings().mapStyle);
  const [ostiaOn, setOstiaOn] = useState(false);
  const [busy, setBusy] = useState(false);
  const [hint, setHint] = useState<string | null>(null);
  /** 收藏弹窗（App：ts_favorite_dialog_title，名称可编辑、留空用坐标） */
  const [saveOpen, setSaveOpen] = useState(false);
  const [draftName, setDraftName] = useState('');
  const [topOpen, setTopOpen] = useState(false);
  const [slot, setSlot] = useState<TimeSlot>('all');
  const top = useTopWindows();
  const geoSeq = useRef(0);

  // 初始化地图
  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const map = new MapLibreMap({
      container: containerRef.current,
      style: STYLE_URLS[getSettings().mapProvider].color,
      center: [DEFAULT_CENTER.lon, DEFAULT_CENTER.lat],
      zoom: 11,
      attributionControl: { compact: true },
    });
    map.addControl(new NavigationControl({ showCompass: false }), 'top-right');
    mapRef.current = map;
    setMap(map);

    const syncCenter = () => {
      const c = map.getCenter();
      setCenter({ lat: c.lat, lon: c.lng });
    };
    map.on('moveend', syncCenter);
    map.on('load', syncCenter);
    // Marker 必须在 style 就绪后再挂，否则首次投影落在 (0,0)
    map.on('load', () => {
      setMapLoaded(true);
      // UX-18：返回地图时回到 lastViewed 最新点（从收藏页回来不再停在默认位置）
      void repository.listFavorites().then((rows) => {
        const latest = rows[0];
        if (!latest || !mapRef.current) return;
        mapRef.current.jumpTo({ center: [latest.lon, latest.lat], zoom: 12 });
        setCenter({ lat: latest.lat, lon: latest.lon });
      });
    });

    return () => {
      map.remove();
      mapRef.current = null;
      setMap(null);
      setMapLoaded(false);
    };
  }, []);

  // 中心变化 → 逆地理（防抖）
  useEffect(() => {
    const seq = geoSeq.current + 1;
    geoSeq.current = seq;
    const timer = setTimeout(() => {
      void reverseGeocode(center.lat, center.lon).then((p) => {
        if (geoSeq.current === seq) setPlace(p);
      });
    }, 500);
    return () => clearTimeout(timer);
  }, [center]);

  // G-07：提供方变化 → 用新提供方的同名样式重载
  const providerRef = useRef(getSettings().mapProvider);
  useEffect(() => {
    if (providerRef.current === settings.mapProvider) return;
    providerRef.current = settings.mapProvider;
    mapRef.current?.setStyle(STYLE_URLS[settings.mapProvider][styleId]);
  }, [settings.mapProvider, styleId]);

  // 样式切换
  const applyStyle = useCallback((next: StyleId) => {
    const map = mapRef.current;
    if (!map) return;
    setStyleId(next);
    setOstiaOn(false);
    map.setStyle(STYLE_URLS[getSettings().mapProvider][next]);
    updateSettings({ mapStyle: next });
  }, []);

  // OSTIA 叠加
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const onStyle = () => {
      if (!ostiaOn || !map.getStyle()) return;
      if (!map.getSource('ostia')) {
        map.addSource('ostia', {
          type: 'raster',
          tiles: [ostiaTileTemplate(todayUtcDate())],
          tileSize: 256,
        });
      }
      if (!map.getLayer('ostia-layer')) {
        map.addLayer({
          id: 'ostia-layer',
          type: 'raster',
          source: 'ostia',
          paint: { 'raster-opacity': 0.55 },
        });
      }
    };

    if (!ostiaOn) {
      if (map.getLayer('ostia-layer')) map.removeLayer('ostia-layer');
      if (map.getSource('ostia')) map.removeSource('ostia');
      return;
    }
    map.on('styledata', onStyle);
    onStyle();
    return () => {
      map.off('styledata', onStyle);
    };
  }, [ostiaOn, styleId]);

  // 长按选点：把地图中心移到按下位置（准星仍是唯一真源）
  useEffect(() => {
    const el = containerRef.current;
    const map = mapRef.current;
    if (!el || !map) return;
    let timer: ReturnType<typeof setTimeout> | null = null;
    let origin: { x: number; y: number } | null = null;

    const start = (e: MouseEvent | TouchEvent) => {
      const point = 'touches' in e ? e.touches[0] : (e as MouseEvent);
      if (!point) return;
      origin = { x: point.clientX, y: point.clientY };
      timer = setTimeout(() => {
        const rect = el.getBoundingClientRect();
        const lngLat = map.unproject([point.clientX - rect.left, point.clientY - rect.top]);
        map.easeTo({ center: lngLat, duration: 240 });
        setHint(L('已按长按位置居中，准星即选点', 'Centered on long-press; the crosshair is the pick'));
        timer = null;
      }, 600);
    };

    const cancel = () => {
      if (timer != null) {
        clearTimeout(timer);
        timer = null;
      }
      origin = null;
    };

    const move = (e: MouseEvent | TouchEvent) => {
      const point = 'touches' in e ? e.touches[0] : (e as MouseEvent);
      if (!point || !origin) return;
      if (Math.abs(point.clientX - origin.x) > 8 || Math.abs(point.clientY - origin.y) > 8) cancel();
    };

    el.addEventListener('mousedown', start);
    el.addEventListener('touchstart', start, { passive: true });
    el.addEventListener('mousemove', move);
    el.addEventListener('touchmove', move, { passive: true });
    el.addEventListener('mouseup', cancel);
    el.addEventListener('touchend', cancel);
    el.addEventListener('mouseleave', cancel);

    return () => {
      el.removeEventListener('mousedown', start);
      el.removeEventListener('touchstart', start);
      el.removeEventListener('mousemove', move);
      el.removeEventListener('touchmove', move);
      el.removeEventListener('mouseup', cancel);
      el.removeEventListener('touchend', cancel);
      el.removeEventListener('mouseleave', cancel);
      cancel();
    };
  }, []);

  // 标记（当前中心）
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    if (!markerRef.current) {
      markerRef.current = new Marker({ color: '#1565C0', className: 'map-center-pin' }).setLngLat([center.lon, center.lat]).addTo(map);
    } else {
      markerRef.current.setLngLat([center.lon, center.lat]);
    }
  }, [center]);

  const locate = useCallback(() => {
    if (!navigator.geolocation) {
      setHint(L('浏览器不支持定位', 'Geolocation is unavailable'));
      return;
    }
    setBusy(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const { latitude, longitude } = pos.coords;
        mapRef.current?.flyTo({ center: [longitude, latitude], zoom: 13, duration: 800 });
        setHint(L('已定位到当前位置', 'Located your position'));
        setBusy(false);
      },
      () => {
        setHint(L('定位失败或被拒绝（可手动拖动地图）', 'Location failed or denied — drag the map manually'));
        setBusy(false);
      },
      { enableHighAccuracy: true, timeout: 8000 },
    );
  }, []);

  const saveAndOpen = useCallback(async () => {
    setBusy(true);
    try {
      const fallback = `${center.lat.toFixed(5)}, ${center.lon.toFixed(5)}`;
      const name = draftName.trim() || fallback;
      const id = await repository.addFavorite({
        name,
        lat: center.lat,
        lon: center.lon,
        preset: 'sea_shore',
        waterBody: 'UNKNOWN',
      });
      setSaveOpen(false);
      navigate(`#/spot/${id}`);
    } catch (e) {
      setHint(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }, [center, draftName]);

  const coordText = useMemo(
    () => `${center.lat.toFixed(6)}, ${center.lon.toFixed(6)}`,
    [center],
  );

  return (
    <div className={`map-page${env.kind === 'desktop' ? ' map-page--desk' : ''}`}>
      <div className="map-stage">
      <div className="map-canvas" ref={containerRef}>
        <div className="crosshair" aria-hidden="true">
          <span className="crosshair__ring" />
          <span className="crosshair__dot" />
          <IcCrosshairFieldguide className="crosshair__ticks" />
          <span className="crosshair__pin">
            <IcFishFieldguide size={24} />
          </span>
        </div>
      </div>

      <MapFavorites map={mapLoaded ? map : null} onOpenDetail={(id) => navigate(`#/spot/${id}`)} />

      <div className="map-hud">
        <div className="map-hud__left">
          <MapSearch
            onPick={(p) => {
              mapRef.current?.flyTo({ center: [p.lon, p.lat], zoom: 12, duration: 700 });
              setHint(p.label);
            }}
          />
          <div className="map-hud__styles" role="group" aria-label={L('底图样式', 'Basemap')}>
          {(Object.keys(STYLE_LABELS) as StyleId[]).map((id) => (
            <button
              key={id}
              type="button"
              className={`map-style-btn ${styleId === id ? 'is-active' : ''}`}
              onClick={() => applyStyle(id)}
            >
              {STYLE_LABELS[id][lang]}
            </button>
          ))}
          <button
            type="button"
            className={`map-style-btn ${ostiaOn ? 'is-active' : ''}`}
            onClick={() => setOstiaOn((v) => !v)}
          >
            {L('海温', 'Sea temp')}
          </button>
          </div>
        </div>
        <div className="map-hud__actions">
          <Button variant="primary" size="sm" onClick={locate} disabled={busy}>
            {t('ts_map_my_location', lang)}
          </Button>
        </div>
      </div>

      <div className="map-info">
        <div className="map-info__main">
          <div className="map-info__name">
            {place?.displayName ?? L('定位中…', 'Locating…')}
            {place?.source === 'coordinate' && <Chip tone="mint">坐标名</Chip>}
          </div>
          <div className="map-info__coord">{coordText}</div>
          {place?.subLabel && <div className="map-info__sub">{place.subLabel}</div>}
        </div>
        <div className="map-info__actions">
          <Button variant="quiet" size="sm" onClick={() => navigate('#/favorites')}>
            {L('查看收藏', 'Saved spots')}
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={() => {
              setDraftName(place?.displayName ?? '');
              setSaveOpen(true);
            }}
            disabled={busy}
          >
            {L('收藏并查看', 'Save & open')}
          </Button>
        </div>
      </div>

      {hint && (
        <div className="map-hint" role="status">
          {hint}
          <button type="button" className="map-hint__close" onClick={() => setHint(null)}>
            ×
          </button>
        </div>
      )}

      </div>

      <NextWindowStrip windows={top.windows} loading={top.loading} />

      <Top10Strip windows={top.windows} onOpen={() => setTopOpen(true)} />

      {env.kind === 'desktop' && (
        <aside className="map-aside" aria-label={L('跨点 Top10', 'Top 10 across spots')}>
          <Card ariaLabel={L('地图说明', 'Map notes')}>
            <p className="hint">{L('拖动地图，把十字准星对准目标水域，再点「收藏并查看」。', 'Drag the map so the crosshair sits on the target water, then tap Save & open.')}</p>
          </Card>
          <Top10List
            windows={top.windows}
            slot={slot}
            onSlotChange={setSlot}
            onSelect={(id) => navigate(`#/spot/${id}`)}
            loading={top.loading}
            onReload={top.reload}
          />
        </aside>
      )}

      <BottomSheet
        open={saveOpen}
        title={L('收藏该点位', 'Save this spot')}
        onClose={() => {
          if (!busy) setSaveOpen(false);
        }}
      >
        <div className="stack" style={{ gap: 10 }}>
          <label className="stack" style={{ gap: 6 }}>
            <span className="hint">{L('名称', 'Name')}</span>
            <input
              className="fav-editor-input"
              value={draftName}
              autoFocus
              placeholder={L('留空则使用坐标作为名称', 'Leave blank to use coordinates')}
              aria-label={L('收藏点名称', 'Spot name')}
              onChange={(e) => setDraftName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !busy) void saveAndOpen();
              }}
            />
          </label>
          <p className="hint" style={{ margin: 0 }}>
            {L('坐标', 'Coordinates')} {center.lat.toFixed(5)}, {center.lon.toFixed(5)}
          </p>
          <p className="hint" style={{ margin: 0 }}>
            {L('水体：未判定（打开详情后自动判定）', 'Water body: undetermined (resolved after opening details)')}
          </p>
          <div className="row-gap">
            <Button variant="primary" size="sm" onClick={saveAndOpen} disabled={busy}>
              {busy ? L('保存中…', 'Saving…') : L('确认收藏', 'Confirm')}
            </Button>
            <Button variant="quiet" size="sm" onClick={() => setSaveOpen(false)} disabled={busy}>
              {L('取消', 'Cancel')}
            </Button>
          </div>
        </div>
      </BottomSheet>

      <Top10Sheet
        open={topOpen}
        windows={top.windows}
        onClose={() => setTopOpen(false)}
        loading={top.loading}
        onReload={top.reload}
      />
    </div>
  );
}