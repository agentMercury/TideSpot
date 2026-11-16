/**
 * 多序列折线（Canvas 2D）。缺失点断开，支持零点参考线与单位后缀。
 */
import { useEffect, useRef } from 'react';

export interface SeriesPoint {
  hour: number;
  value: number | null;
}

export interface Series {
  label: string;
  color: string;
  points: SeriesPoint[];
}

const PAD_L = 34;
const PAD_R = 10;
const PAD_T = 12;
const PAD_B = 24;

export function LineChart({
  series,
  unit,
  height = 180,
  zeroLine = false,
}: {
  series: ReadonlyArray<Series>;
  unit?: string;
  height?: number;
  zeroLine?: boolean;
}) {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current;
    const parent = canvas?.parentElement;
    if (!canvas || !parent) return;

    const draw = () => {
      const dpr = Math.min(2, window.devicePixelRatio || 1);
      const w = Math.max(160, parent.clientWidth);
      const h = height;
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
      canvas.style.width = `${w}px`;
      canvas.style.height = `${h}px`;

      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, w, h);

      const styles = getComputedStyle(document.documentElement);
      const border = styles.getPropertyValue('--color-border').trim() || '#dce5ee';
      const dim = styles.getPropertyValue('--color-text-dim').trim() || '#556579';
      const isFieldGuide = document.documentElement.dataset.skin === 'd-fieldguide';
      const isJournal = document.documentElement.dataset.skin === 'e-tidejournal';
      const outlineSkin = isFieldGuide || isJournal;
      const ink = styles.getPropertyValue('--color-outline').trim() || '#101820';
      /** series.color 允许传 `var(--color-primary)`；Canvas 不认 var()，这里解析成实际色值 */
      const resolve = (color: string): string => {
        const match = /^var\((--[^)]+)\)$/.exec(color.trim());
        return match ? styles.getPropertyValue(match[1]).trim() || color : color;
      };

      const values = series.flatMap((s) => s.points.map((p) => p.value).filter((v): v is number => v != null));
      const min = values.length ? Math.min(...values, zeroLine ? 0 : Infinity) : 0;
      const max = values.length ? Math.max(...values, zeroLine ? 0 : -Infinity) : 1;
      const span = max - min || 1;
      const lo = min - span * 0.1;
      const hi = max + span * 0.1;

      const plotW = w - PAD_L - PAD_R;
      const plotH = h - PAD_T - PAD_B;
      const x = (hour: number) => PAD_L + (Math.min(23, Math.max(0, hour)) / 23) * plotW;
      const y = (v: number) => PAD_T + (1 - (v - lo) / (hi - lo)) * plotH;

      ctx.font = isFieldGuide ? '10px ui-monospace, Consolas, monospace' : '10px system-ui, sans-serif';
      ctx.strokeStyle = border;
      ctx.lineWidth = 1;

      // 3 条水平网格（墨线皮肤：淡墨实线）
      if (outlineSkin) {
        ctx.strokeStyle = ink;
        ctx.globalAlpha = isJournal ? 0.14 : 0.18;
      }
      for (const v of [hi, (lo + hi) / 2, lo]) {
        const yy = Math.round(y(v)) + 0.5;
        ctx.beginPath();
        ctx.moveTo(PAD_L, yy);
        ctx.lineTo(w - PAD_R, yy);
        ctx.stroke();
      }
      ctx.globalAlpha = 1;

      if (zeroLine && lo < 0 && hi > 0) {
        ctx.setLineDash([4, 4]);
        ctx.strokeStyle = dim;
        ctx.beginPath();
        ctx.moveTo(PAD_L, y(0));
        ctx.lineTo(w - PAD_R, y(0));
        ctx.stroke();
        ctx.setLineDash([]);
      }

      // 纵轴上下界
      ctx.fillStyle = dim;
      ctx.textAlign = 'right';
      ctx.textBaseline = 'middle';
      ctx.fillText(hi.toFixed(1), PAD_L - 6, y(hi));
      ctx.fillText(lo.toFixed(1), PAD_L - 6, y(lo));

      // 曲线
      for (const s of series) {
        const color = resolve(s.color);
        ctx.strokeStyle = color;
        ctx.lineWidth = isJournal ? 2.2 : isFieldGuide ? 2.6 : 2;
        ctx.lineJoin = 'round';
        ctx.beginPath();
        let started = false;
        for (const p of s.points) {
          if (p.value == null) {
            started = false;
            continue;
          }
          const px = x(p.hour);
          const py = y(p.value);
          if (!started) {
            ctx.moveTo(px, py);
            started = true;
          } else {
            ctx.lineTo(px, py);
          }
        }
        ctx.stroke();

        // 末点
        const last = [...s.points].reverse().find((p) => p.value != null);
        if (last && last.value != null) {
          ctx.beginPath();
          ctx.arc(x(last.hour), y(last.value), isFieldGuide ? 4 : 3, 0, Math.PI * 2);
          ctx.fillStyle = color;
          ctx.fill();
          if (outlineSkin) {
            ctx.strokeStyle = ink;
            ctx.lineWidth = 1.5;
            ctx.stroke();
          }
        }
      }

      // 横轴
      ctx.fillStyle = dim;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'top';
      for (const hour of [0, 6, 12, 18, 23]) ctx.fillText(`${hour}时`, x(hour), h - PAD_B + 6);

      if (unit) {
        ctx.textAlign = 'left';
        ctx.fillText(unit, PAD_L, 0);
      }
    };

    draw();
    const ro = new ResizeObserver(draw);
    ro.observe(parent);
    const mo = new MutationObserver(draw);
    mo.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'data-skin', 'data-water'] });
    window.addEventListener('resize', draw);
    return () => {
      ro.disconnect();
      mo.disconnect();
      window.removeEventListener('resize', draw);
    };
  }, [series, unit, height, zeroLine]);

  return (
    <div className="line-chart">
      <canvas ref={ref} role="img" aria-label={series.map((s) => s.label).join('、')} />
      <div className="line-chart__legend">
        {series.map((s) => (
          <span key={s.label}>
            <i style={{ background: s.color }} />
            {s.label}
          </span>
        ))}
      </div>
    </div>
  );
}