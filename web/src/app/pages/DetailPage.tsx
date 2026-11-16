/**
 * 详情页（S02 指数 + S03 因子 + 其余 Tab 摘要）。
 * 数据来自 Repository：先读缓存立即渲染，再用真实数据刷新（失败保留旧值并挂降级）。
 */
import { useEffect, useMemo, useState } from 'react';
import { repository } from '../container';
import { navigate } from '../router';
import {
  BottomSheet,
  Button,
  Card,
  Chip,
  DataTable,
  DaySelector,
  ScoreCard,
  SegmentedTabs,
  type DayChip,
} from '../../ui/primitives/primitives';
import { HourChart, type HourPoint } from '../../ui/charts/HourChart';
import { hourTableRows } from '../../ui/charts/hourRows';
import { resolveScoreBand } from '../../ui/tokens/bands';
import { t, useLang } from '../../ui/i18n';
import type { SpotRow } from '../../data/db';
import type { WaterBodyType } from '../../domain/types';
import type { SpotDetail } from '../../data/repository';
import { FRESH_LURE, presetById } from '../../domain/presets';
import { setWater, useSkin } from '../../ui/skin';
import { IcFishFieldguide, IcFishVintage, IcWaveFieldguide, IcWaveFieldnotes } from '../../ui/icons';
import { FieldnotesSeabed } from '../../ui/fieldnotesMarks';
import { FactorBars } from '../../ui/factorBars';
import { TideTab, WaveTab, WaterQualityTab, WeatherTab } from './DetailTabs';

const TABS = [
  { id: 'score', label: 'ts_tab_score' },
  { id: 'tide', label: 'ts_tab_tide' },
  { id: 'weather', label: 'ts_tab_weather' },
  { id: 'water', label: 'ts_tab_water_quality' },
  { id: 'wave', label: 'ts_tab_wave' },
];

const FACTOR_ID: Record<string, string> = {
  oxygen: 'ts_factor_oxygen',
  pressure: 'ts_factor_pressure',
  solunar: 'ts_factor_solunar',
  tide: 'ts_factor_tide',
  wind: 'ts_factor_wind',
  weather: 'ts_factor_weather',
  daypart: 'ts_factor_daypart',
  tempMatch: 'ts_factor_temp_match',
  comfort: 'ts_factor_comfort',
  waterQuality: 'ts_factor_water_quality',
};

const DISCLAIMER_KEYS = [
  'ts_disclaimer_score',
  'ts_disclaimer_weather',
  'ts_disclaimer_water_quality',
  'ts_disclaimer_wave_tide',
  'ts_disclaimer_tide_correction',
];

const WEEKDAY = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

function tOr(key: string, fallback: string): string {
  const value = t(key);
  return value === key ? fallback : value;
}

function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

function fmtClock(epochSec: number, tzSec: number): string {
  const d = new Date((epochSec + tzSec) * 1000);
  return `${pad2(d.getUTCHours())}:${pad2(d.getUTCMinutes())}`;
}

function fmtCoord(v: number): string {
  return v.toFixed(6);
}

interface DayGroup {
  key: string;
  topLabel: string;
  weekdayLabel: string;
  peak: number;
  points: HourPoint[];
  peakEpoch: number;
  peakIndex: number;
}

export function DetailPage({ spotId, embedded = false }: { spotId: number; embedded?: boolean }) {
  const [spot, setSpot] = useState<SpotRow | null>(null);
  const [detail, setDetail] = useState<SpotDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState('score');
  const [day, setDay] = useState('');
  const [sheetOpen, setSheetOpen] = useState(false);
  /** G-04：逐时指数「图表 / 数值表」分段切换 */
  const [chartMode, setChartMode] = useState<'chart' | 'table'>('chart');
  const [lang] = useLang();
  const [skin] = useSkin();
  const L2 = (zh: string, en: string) => (lang === 'en' ? en : zh);
  /** 重试计数：>0 时以 force 重新拉取（绕过 4h 新鲜度，仍受 15min 频控约束） */
  const [reloadTick, setReloadTick] = useState(0);
  /** G-13：离线状态（状态条数据源之一） */
  const [online, setOnline] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine));

  useEffect(() => {
    const onOnline = () => setOnline(true);
    const onOffline = () => setOnline(false);
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    return () => {
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    };
  }, []);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);

    void (async () => {
      const row = await repository.getSpot(spotId);
      if (!alive) return;
      setSpot(row ?? null);

      const cached = await repository.readCachedDetail(spotId);
      if (cached && alive) {
        setDetail(cached);
        setLoading(false);
      }

      try {
        const fresh = await repository.getDetail(spotId, { force: reloadTick > 0 });
        if (alive) {
          setDetail(fresh);
          setLoading(false);
        }
      } catch (e) {
        if (alive) {
          setError(e instanceof Error ? e.message : String(e));
          setLoading(false);
        }
      }
    })();

    return () => {
      alive = false;
    };
  }, [spotId, reloadTick]);

  const tzSec = spot?.tzOffsetSec ?? 8 * 3600;

  // 水体由点位决定：内陆 → inland 皮肤色板；离开详情恢复 sea
  useEffect(() => {
    if (detail) setWater(detail.marineAllNullInland ? 'inland' : 'sea');
  }, [detail]);
  useEffect(() => () => setWater('sea'), []);
  // 水体类型回写（G-05）：新建收藏默认 UNKNOWN；首轮数据到达后按「海洋全空」判定并落库，
  // 否则收藏列表/地图永远显示 UNKNOWN（Android 由 WaterBodyResolver 在保存时判定）。
  useEffect(() => {
    if (!detail || !spot || spot.id == null) return;
    // 只有「海洋数据确实拉到且未降级」才判定；否则 marine 缺失会把所有点误标成 NEARSHORE
    const marineFetched = detail.fetchedAtMillis.marine != null;
    if (!marineFetched || detail.marineDegraded) return;
    // 与详情页同一判定源：marine 全 null = 内陆（§6.9）
    const next: WaterBodyType = detail.marineAllNullInland ? 'INLAND_WATER' : 'NEARSHORE';
    if (spot.waterBody === next) return;
    // 允许纠正历史误标（Web 暂无手动设置水体的入口，覆盖安全）
    void repository.updateFavorite(spot.id, { waterBody: next }).then(() => {
      setSpot((prev) => (prev ? { ...prev, waterBody: next } : prev));
    });
  }, [detail, spot]);

  const preset = useMemo(() => presetById(detail?.presetId ?? '') ?? FRESH_LURE, [detail]);

  const days = useMemo<DayGroup[]>(() => {
    const scores = detail?.scores ?? [];
    const map = new Map<string, DayGroup>();

    for (const s of scores) {
      const d = new Date((s.timeEpochSec + tzSec) * 1000);
      const key = `${d.getUTCFullYear()}-${pad2(d.getUTCMonth() + 1)}-${pad2(d.getUTCDate())}`;
      let group = map.get(key);
      if (!group) {
        group = {
          key,
          topLabel: `${pad2(d.getUTCMonth() + 1)}-${pad2(d.getUTCDate())}`,
          weekdayLabel: WEEKDAY[d.getUTCDay()],
          peak: -1,
          points: [],
          peakEpoch: s.timeEpochSec,
          peakIndex: 0,
        };
        map.set(key, group);
      }
      const hour = d.getUTCHours();
      group.points.push({ hour, total: s.total });
      if (s.total > group.peak) {
        group.peak = s.total;
        group.peakEpoch = s.timeEpochSec;
        group.peakIndex = group.points.length - 1;
      }
    }

    for (const group of map.values()) {
      group.points.sort((a, b) => a.hour - b.hour);
      group.peakIndex = group.points.findIndex((p) => p.total === group.peak);
      if (group.peakIndex < 0) group.peakIndex = 0;
    }

    return [...map.values()].sort((a, b) => a.key.localeCompare(b.key));
  }, [detail, tzSec]);

  const todayKey = useMemo(() => {
    const d = new Date(Date.now() + tzSec * 1000);
    return `${d.getUTCFullYear()}-${pad2(d.getUTCMonth() + 1)}-${pad2(d.getUTCDate())}`;
  }, [tzSec]);

  const activeDay = useMemo(() => {
    const key = day || todayKey;
    // 选了某天但没有数据 → 返回 null（显示该天空态），不擅自跳到别的天
    if (day) return days.find((d) => d.key === day) ?? null;
    return days.find((d) => d.key === key) ?? days[0] ?? null;
  }, [days, day, todayKey]);

  /**
   * 日期选择（与 Android 同层级：页面级、Tab 之上，所有 Tab 共用）。
   * chips 不再依赖 scores —— App 的 chips 由「今天 + 未来 N 天」生成，即使某天没数据也照常显示（分数留空）。
   */
  const dayChips = useMemo<DayChip[]>(() => {
    const byKey = new Map(days.map((d) => [d.key, d]));
    const base = new Date(Date.now() + tzSec * 1000);
    const count = Math.max(3, days.length);
    const out: DayChip[] = [];
    for (let i = 0; i < count; i += 1) {
      const d = new Date(base.getTime() + i * 86_400_000);
      const key = `${d.getUTCFullYear()}-${pad2(d.getUTCMonth() + 1)}-${pad2(d.getUTCDate())}`;
      const group = byKey.get(key);
      out.push({
        key,
        topLabel: key === todayKey ? tOr('ts_day_today_label', '今天') : `${d.getUTCMonth() + 1}/${d.getUTCDate()}`,
        weekdayLabel: WEEKDAY[d.getUTCDay()],
        score: group ? group.peak : null,
      });
    }
    return out;
  }, [days, todayKey, tzSec]);

  const peakScore = useMemo(() => {
    if (!detail || !activeDay || activeDay.points.length === 0) return null;
    return activeDay.points[activeDay.peakIndex];
  }, [detail, activeDay]);

  const peakResult = useMemo(() => {
    if (!detail || !activeDay) return null;
    return detail.scores.find((s) => s.timeEpochSec === activeDay.peakEpoch) ?? null;
  }, [detail, activeDay]);

  const band = peakScore ? resolveScoreBand(peakScore.total) : resolveScoreBand(0);
  const bestWindow =
    activeDay && peakScore
      ? `${peakScore.hour}:00–${Math.min(23, peakScore.hour + 1)}:00`
      : undefined;

  const sourceLine = detail
    ? [
        detail.fetchedAtMillis.weather
          ? `${tOr('ts_detail_weather_source', '天气')} ${fmtClock(detail.fetchedAtMillis.weather / 1000, tzSec)}`
          : null,
        detail.fetchedAtMillis.marine
          ? `${tOr('ts_detail_marine_source', '海洋')} ${fmtClock(detail.fetchedAtMillis.marine / 1000, tzSec)}`
          : null,
      ]
        .filter(Boolean)
        .join(' · ')
    : '';

  const statusItems = useMemo(() => {
    const items: Array<{ id: string; text: string; tone: 'warn' | 'dim' }> = [];
    if (!online) {
      items.push({ id: 'offline', text: tOr('ts_offline', '网络不可用，显示本地缓存数据'), tone: 'warn' });
    }
    if (detail && detail.unusable.length > 0) {
      items.push({
        id: 'expired',
        text: tOr('ts_detail_expired_48h', '缓存已超过 48 小时，数据不可用'),
        tone: 'warn',
      });
    }
    if (detail && (detail.weatherDegraded || detail.marineDegraded)) {
      items.push({
        id: 'degraded',
        text: tOr('ts_dimension_degraded', '部分维度不可用，已按有效权重重算'),
        tone: 'dim',
      });
    }
    if (error && detail && detail.servedFromCache.length > 0) {
      items.push({
        id: 'stale',
        text: tOr('ts_detail_stale_cache', '当前显示为最近缓存（重试失败保留旧数据）'),
        tone: 'dim',
      });
    }
    return items;
  }, [online, detail, error]);

  const factorRows = useMemo(() => {
    if (!peakResult) return [];
    return peakResult.factors.map((f) => ({
      name: tOr(FACTOR_ID[f.id] ?? '', f.id),
      sub: f.sub.toFixed(2),
      weight: f.weight.toFixed(2),
      contribution: (f.sub * f.weight).toFixed(3),
    }));
  }, [peakResult]);

  const tabProps = {
    slices: detail?.slices ?? [],
    dayKey: activeDay?.key ?? '',
    tzSec,
    preset,
    marineAllNullInland: detail?.marineAllNullInland ?? false,
  };

  /** 无任何评分（通常 = 天气源不可达）：给出明确原因与重试，而不是一片「暂无数据」 */
  const noScores = !loading && (detail?.scores.length ?? 0) === 0;

  const goBack = () => navigate('#/');

  return (
<div className={`detail-page${embedded ? ' detail-page--embedded' : ''}`}>

      <header className="detail-top">
        {!embedded && (
          <button type="button" className="ghost-button" onClick={goBack}>
            {tOr('ts_common_back', '返回')}
          </button>
        )}
        <div className="detail-top__title">
          <strong>{spot?.name ?? '…'}</strong>
          {spot && (
            <span className="hint">
              {fmtCoord(spot.lat)}, {fmtCoord(spot.lon)}
            </span>
          )}
        </div>
        <span className="chip detail-fav-chip" aria-label="已收藏">
          {tOr('ts_fav_title', '收藏')}
        </span>
        <span className="detail-stamp" aria-hidden="true">
          <IcFishFieldguide className="detail-stamp__guide" size={22} />
          <IcFishVintage className="detail-stamp__vintage" size={22} />
        </span>
      </header>

      {detail && (
        <div className="detail-water-row">
          <span
            className={`detail-water${detail.marineAllNullInland ? ' detail-water--inland' : ' detail-water--sea'}`}
            title={detail.marineAllNullInland ? 'INLAND' : 'COASTAL'}
          >
            <IcWaveFieldguide className="detail-water__ill" />
            <IcWaveFieldnotes className="detail-water__line" />
            <span>
              {detail.marineAllNullInland
                ? L2('内陆 / 湖泊', 'Inland / Lake')
                : L2('海洋 / 近海', 'Coastal / Sea')}
            </span>
            <i>{detail.marineAllNullInland ? 'INLAND' : 'COASTAL'}</i>
          </span>
        </div>
      )}

      {/* 吸顶区：日期选择 + 来源行 + Tab 一起常驻（App 中三者是连续层级） */}
      <div className="detail-sticky">
        <DaySelector days={dayChips} value={activeDay?.key ?? (day || todayKey)} onChange={setDay} />
        <p className="hint detail-source">
          {sourceLine || tOr('ts_detail_no_source', '暂无来源时间')}
        </p>
        <SegmentedTabs
          items={TABS.map((x) => ({ id: x.id, label: tOr(x.label, x.id) }))}
          value={tab}
          onChange={setTab}
          scrollable
        />
      </div>

      {statusItems.length > 0 && (
        <div className="detail-status" role="status">
          {statusItems.map((item) => (
            <span key={item.id} className={`detail-status__item detail-status__item--${item.tone}`}>
              {item.text}
            </span>
          ))}
        </div>
      )}

      <div className="detail-body">
        {noScores && (
          <Card ariaLabel={tOr('ts_detail_no_source', 'No data from this source')}>
            <strong>{tOr('ts_detail_refresh_failed', 'Refresh failed')}</strong>
            <p className="hint">
              {lang === 'en'
                ? 'Scores need both weather and marine sources. If a third-party request is blocked or timed out, this stays empty.'
                : '指数依赖天气与海洋两源；若浏览器拦截或超时，这里会一直为空。'}
            </p>
            <ul className="ts-hint">
              <li>{L2('天气源', 'Weather')}：{detail?.fetchedAtMillis.weather ? L2('已拉取', 'fetched') : L2('未拉取', 'not fetched')}{' '}
                {detail ? `· ${detail.slices.filter((s) => s.weather != null).length} h` : ''}</li>
              <li>{L2('海洋源', 'Marine')}：{detail?.fetchedAtMillis.marine ? L2('已拉取', 'fetched') : L2('未拉取', 'not fetched')}{' '}
                {detail ? `· ${detail.slices.filter((s) => s.marine != null).length} h` : ''}</li>
              {error && <li>{L2('错误', 'Error')}：{error}</li>}
            </ul>
            <Button variant="primary" size="sm" onClick={() => setReloadTick((v) => v + 1)} disabled={loading}>
              {tOr('ts_detail_retry', 'Retry')}
            </Button>
          </Card>
        )}
        {tab === 'score' && (
          <>
            {peakScore && (
              <ScoreCard
                total={peakScore.total}
                band={band}
                confidence={peakResult?.confidence ?? 'MODEL'}
                windowLabel={bestWindow ? `最佳窗 ${bestWindow}` : undefined}
                activeSegment={Math.min(4, Math.floor((peakScore.total / 100) * 5))}
              />
            )}

            <Card ariaLabel="逐时曲线">
              <div className="row-between">
                <strong>逐时指数</strong>
                <SegmentedTabs
                  items={[
                    { id: 'chart', label: tOr('ts_detail_chart', '图表') },
                    { id: 'table', label: tOr('ts_detail_table', '数值表') },
                  ]}
                  value={chartMode}
                  onChange={(id) => setChartMode(id === 'table' ? 'table' : 'chart')}
                />
              </div>
              {!activeDay || activeDay.points.length === 0 ? (
                <p className="hint">{loading ? '加载中…' : '暂无逐时数据'}</p>
              ) : chartMode === 'chart' ? (
                <HourChart points={activeDay.points} bandColor={band.color} />
              ) : (
                <DataTable
                  columns={[
                    { key: 'time', label: '时间' },
                    { key: 'total', label: '指数', align: 'right' },
                    { key: 'band', label: '档位' },
                  ]}
                  rows={hourTableRows(activeDay.points)}
                />
              )}
            </Card>

            <Card ariaLabel="最佳窗口">
              <strong>最佳窗口</strong>
              <p className="hint">
                {bestWindow
                  ? `${bestWindow} · 峰值 ${peakScore?.total ?? 0}`
                  : '当日无可用窗口（数据不足）'}
              </p>
              {peakResult && peakResult.vetoCodes.length > 0 && (
                <div className="row-gap">
                  {peakResult.vetoCodes.map((c) => (
                    <Chip key={c} tone="band">
                      {c === 'THUNDERSTORM' ? '雷暴否决' : '可作钓性衰减'}
                    </Chip>
                  ))}
                </div>
              )}
            </Card>

<Card ariaLabel="因子分解" className={skin === 'e-tidejournal' ? 'fn-factor-card' : ''}>
              <strong>{tOr('ts_factor_breakdown_title', '因子分解')}</strong>
              {factorRows.length > 0 ? (
                skin === 'e-tidejournal' ? (
                  <FactorBars rows={factorRows} />
                ) : (
                  <DataTable
                    columns={[
                      { key: 'name', label: '因子' },
                      { key: 'sub', label: '子分', align: 'right' },
                      { key: 'weight', label: '权重', align: 'right' },
                      { key: 'contribution', label: '贡献', align: 'right' },
                    ]}
                    rows={factorRows}
                  />
                )
              ) : (
                <p className="hint">暂无因子数据</p>
              )}
            </Card>
          </>
        )}

        {tab === 'tide' && <TideTab {...tabProps} />}
        {tab === 'weather' && <WeatherTab {...tabProps} />}
        {tab === 'water' && <WaterQualityTab {...tabProps} />}
        {tab === 'wave' && <WaveTab {...tabProps} />}
      </div>

      <FieldnotesSeabed />

      <button type="button" className="disclaimer-entry" onClick={() => setSheetOpen(true)}>
        {tOr('ts_disclaimer_entry', '免责声明（恒 5 条）')}
      </button>

      <BottomSheet open={sheetOpen} title={tOr('ts_disclaimer_title', '免责声明')} onClose={() => setSheetOpen(false)}>
        <ol className="disclaimer-list">
          {DISCLAIMER_KEYS.map((key) => (
            <li key={key}>{tOr(key, key)}</li>
          ))}
        </ol>
      </BottomSheet>

      {error && (
        <div className="detail-error" role="alert">
          <strong>刷新失败</strong>
          <p className="hint">{error}（若已有缓存，仍可查看旧数据）</p>
        </div>
      )}
    </div>
  );
}
