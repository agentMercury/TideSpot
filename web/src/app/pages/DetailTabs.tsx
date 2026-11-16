/**
 * S04 / S05 / S06 / S07 Tab 内容（移动端）。
 * 全部从真实 slices 派生；缺失 = null（禁止 0 填充），并在图上断开。
 */
import { useLang } from '../../ui/i18n';
import { useMemo } from 'react';
import { Card, Chip, ConfidenceBadge, DataTable } from '../../ui/primitives/primitives';
import { LineChart, type SeriesPoint } from '../../ui/charts/LineChart';
import { chartColor } from '../../ui/charts/palette';
import { fuse } from '../../domain/water/waterTemp';
import { doPotential, doRef, oxygenScore } from '../../domain/water/doPhysics';
import { turbidityIndex } from '../../domain/subScores';
import type { HourSlice } from '../../data/seriesJoin';
import type { ScenePreset } from '../../domain/types';

interface Props {
  slices: ReadonlyArray<HourSlice>;
  dayKey: string;
  tzSec: number;
  preset: ScenePreset;
  marineAllNullInland: boolean;
}

function inDay(epochSec: number, dayKey: string, tzSec: number): boolean {
  const d = new Date((epochSec + tzSec) * 1000);
  const key = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`;
  return key === dayKey;
}

function hourOf(epochSec: number, tzSec: number): number {
  return new Date((epochSec + tzSec) * 1000).getUTCHours();
}

function fmt(v: number | null | undefined, digits = 1, suffix = ''): string {
  return v == null ? '—' : `${v.toFixed(digits)}${suffix}`;
}

export function WeatherTab({ slices, dayKey, tzSec }: Props) {
  const points = useMemo(() => {
    const rows = slices.filter((s) => s.weather && inDay(s.timeEpochSec, dayKey, tzSec));
    const temp: SeriesPoint[] = [];
    const pressure: SeriesPoint[] = [];
    const wind: SeriesPoint[] = [];
    for (const row of rows) {
      const h = hourOf(row.timeEpochSec, tzSec);
      temp.push({ hour: h, value: row.weather?.temperatureC ?? null });
      pressure.push({ hour: h, value: row.weather?.pressureMslHpa ?? null });
      wind.push({ hour: h, value: row.weather?.windSpeedKmh ?? null });
    }
    return { temp, pressure, wind };
  }, [slices, dayKey, tzSec]);

  const temps = points.temp.map((p) => p.value).filter((v): v is number => v != null);
  const pressures = points.pressure.map((p) => p.value).filter((v): v is number => v != null);
  const winds = points.wind.map((p) => p.value).filter((v): v is number => v != null);

  return (
    <div className="stack">
      <Card ariaLabel="天气摘要">
        <strong>天气摘要</strong>
        <DataTable
          columns={[
            { key: 'k', label: '指标' },
            { key: 'v', label: '今日区间', align: 'right' },
          ]}
          rows={[
            {
              k: '气温',
              v: temps.length ? `${Math.min(...temps).toFixed(1)} – ${Math.max(...temps).toFixed(1)} °C` : '—',
            },
            {
              k: '气压',
              v: pressures.length ? `${Math.min(...pressures).toFixed(1)} – ${Math.max(...pressures).toFixed(1)} hPa` : '—',
            },
            {
              k: '风速',
              v: winds.length ? `${Math.min(...winds).toFixed(1)} – ${Math.max(...winds).toFixed(1)} km/h` : '—',
            },
          ]}
          emptyLabel="当日无天气样本"
        />
      </Card>

      <Card ariaLabel="气温曲线">
        <strong>气温</strong>
        <LineChart series={[{ label: '气温 °C', color: chartColor('--color-primary', '#1565C0'), points: points.temp }]} unit="°C" />
      </Card>

      <Card ariaLabel="气压曲线">
        <strong>气压</strong>
        <LineChart series={[{ label: '海平面气压 hPa', color: chartColor('--color-secondary', '#6A1B9A'), points: points.pressure }]} unit="hPa" />
      </Card>

      <Card ariaLabel="风速曲线">
        <strong>风速</strong>
        <LineChart series={[{ label: '风速 km/h', color: chartColor('--color-tertiary', '#00695C'), points: points.wind }]} unit="km/h" />
      </Card>
    </div>
  );
}

export function TideTab({ slices, dayKey, tzSec, marineAllNullInland }: Props) {
  const [lang] = useLang();
  const L = (zh: string, en: string) => (lang === 'en' ? en : zh);
  const tidePoints = useMemo<SeriesPoint[]>(() => {
    const rows = slices.filter((s) => s.marine && inDay(s.timeEpochSec, dayKey, tzSec));
    return rows.map((s) => ({ hour: hourOf(s.timeEpochSec, tzSec), value: s.marine?.seaLevelHeightMslM ?? null }));
  }, [slices, dayKey, tzSec]);

  if (marineAllNullInland) {
    return (
      <Card ariaLabel="无潮汐">
        <strong>水位（内陆水体）</strong>
        <p className="hint">该点无海况数据：潮汐维度不参与评分，显示为「水位不可用」。</p>
        <Chip tone="mint">内陆</Chip>
      </Card>
    );
  }

  return (
    <div className="stack">
      <Card ariaLabel="潮位曲线">
        <strong>潮位（MSL）</strong>
        <LineChart series={[{ label: '潮位 m', color: chartColor('--color-primary', '#1565C0'), points: tidePoints }]} unit="m" zeroLine />
      </Card>
      <Card ariaLabel="校准对照">
        <strong>现场校准对照</strong>
        <p className="hint">{L('暂时只能查看模型潮位；现场校潮与对照表将在后续版本提供。', 'Model tide heights only for now; on-site calibration and the reference table come in a later version.')}</p>
        <ConfidenceBadge kind="MODEL" label="未校准" />
      </Card>
    </div>
  );
}

export function WaveTab({ slices, dayKey, tzSec, marineAllNullInland }: Props) {
  const { wave, period } = useMemo(() => {
    const rows = slices.filter((s) => s.marine && inDay(s.timeEpochSec, dayKey, tzSec));
    return {
      wave: rows.map((s) => ({ hour: hourOf(s.timeEpochSec, tzSec), value: s.marine?.waveHeightM ?? null })) as SeriesPoint[],
      period: rows.map((s) => ({ hour: hourOf(s.timeEpochSec, tzSec), value: s.marine?.wavePeriodS ?? null })) as SeriesPoint[],
    };
  }, [slices, dayKey, tzSec]);

  if (marineAllNullInland) {
    return (
      <Card ariaLabel="湖面风">
        <strong>湖面风与水温</strong>
        <p className="hint">内陆点无浪高数据；风浪维度不参与评分，湖面风由天气风速近似。</p>
      </Card>
    );
  }

  return (
    <div className="stack">
      <Card ariaLabel="浪高曲线">
        <strong>浪高</strong>
        <LineChart series={[{ label: '有效波高 m', color: chartColor('--color-mint', '#FB8C00'), points: wave }]} unit="m" />
      </Card>
      <Card ariaLabel="周期曲线">
        <strong>浪周期</strong>
        <LineChart series={[{ label: '周期 s', color: chartColor('--color-secondary', '#00695C'), points: period }]} unit="s" />
      </Card>
    </div>
  );
}

export function WaterQualityTab({ slices, dayKey, tzSec, preset }: Props) {
  const [lang] = useLang();
  const L = (zh: string, en: string) => (lang === 'en' ? en : zh);
  const derived = useMemo(() => {
    const airTemps = slices.map((s) => s.weather?.temperatureC ?? null);
    const rows = slices
      .map((s, i) => ({ s, i }))
      .filter(({ s }) => inDay(s.timeEpochSec, dayKey, tzSec) && s.weather != null);

    let twSum = 0;
    let doSum = 0;
    let turbSum = 0;
    let n = 0;
    let lastTw: number | null = null;

    for (const { s, i } of rows) {
      const tw = fuse(airTemps, i, s.weather?.temperatureC ?? null, s.marine?.seaSurfaceTempC ?? null);
      if (tw != null) lastTw = tw;
      const p = s.weather?.pressureMslHpa ?? null;
      const doP = tw != null && p != null ? doPotential(tw, p, preset.salinityPpt) : null;
      const turb = turbidityIndex(s.weather?.cloudCoverPct ?? null, (s.weather?.rainMm ?? 0) + (s.weather?.showersMm ?? 0));

      if (tw != null) {
        twSum += tw;
        n += 1;
      }
      if (doP != null) doSum += oxygenScore(doP, doRef(tw ?? 15, preset.doBase));
      turbSum += turb;
    }

    return {
      waterTemp: n > 0 ? twSum / n : lastTw,
      oxygenScore: n > 0 ? doSum / n : null,
      turbidity: n > 0 ? turbSum / n : null,
      samples: n,
    };
  }, [slices, dayKey, tzSec, preset]);

  return (
    <div className="stack">
      <div className="wq-cards">
        <Card ariaLabel={L('水温', 'Water temp')}>
          <div className="row-between">
            <span className="wq-card__label">{L('水温（估算）', 'Water temp (est.)')}</span>
            <ConfidenceBadge kind="ESTIMATED" />
          </div>
          <div className="wq-card__value">{fmt(derived.waterTemp, 1, ' °C')}</div>
          <p className="hint">{L('气温滞后融合', 'Air-temp lag fusion')} · {derived.samples} {L('小时样本', 'hourly samples')}</p>
        </Card>

        <Card ariaLabel={L('溶氧', 'Dissolved oxygen')}>
          <span className="wq-card__label">{L('溶氧子分', 'Oxygen sub-score')}</span>
          <div className="wq-card__value">{fmt(derived.oxygenScore, 2)}</div>
          <p className="hint">
            {derived.oxygenScore == null
              ? L('当日无样本', 'No samples today')
              : derived.oxygenScore >= 0.75
                ? L('相对充足', 'Relatively ample')
                : derived.oxygenScore >= 0.5
                  ? L('一般', 'Fair')
                  : L('相对偏低', 'Relatively low')}
          </p>
        </Card>

        <Card ariaLabel={L('浑浊度', 'Turbidity')}>
          <span className="wq-card__label">{L('浑浊度代理', 'Turbidity proxy')}</span>
          <div className="wq-card__value">{fmt(derived.turbidity, 2)}</div>
          <p className="hint">
            {derived.turbidity == null
              ? L('当日无样本', 'No samples today')
              : derived.turbidity <= 0.3
                ? L('清澈', 'Clear')
                : derived.turbidity <= 0.6
                  ? L('一般', 'Fair')
                  : L('偏浑', 'Turbid')}
          </p>
        </Card>
      </div>
      <Card ariaLabel="水质说明">
        <strong>为什么是估算</strong>
        <p className="hint">
          {L('多数点位没有公开的实测水质数据，这里由天气序列在设备内推导，因此标注「估算」，也不会显示「实测」徽标；本期不提供 pH。', 'Most spots have no public in-situ water quality data, so values are derived on-device from the weather series and labelled “estimated” — never “measured”. pH is not provided.')}
        </p>
      </Card>
    </div>
  );
}