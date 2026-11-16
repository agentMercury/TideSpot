/**
 * 逐时指数柱状图（Canvas 2D，无第三方库）—— 与 Android `HourChart(mode = BAR)` 对齐：
 * - 固定 0–100 轴（不随当日数据缩放）；
 * - 柱色 = 五档冻结色带（ScoreBand）；
 * - ≥80 的连续小时在顶部叠「窗口」横条；
 * - x 轴每 3 小时一个当地小时标签。
 * 缺测小时不画柱（禁止 0 高度柱）。
 */
import { useEffect, useRef } from 'react';
import { resolveScoreBand } from '../tokens/bands';

export interface HourPoint {
  hour: number;
  total: number;
}

const PAD_L = 30;
const PAD_R = 10;
const PAD_T = 20;
const PAD_B = 24;
const WINDOW_THRESHOLD = 80;

export function HourChart({
  points,
  height = 200,
}: {
  points: ReadonlyArray<HourPoint>;
  /** 兼容旧调用：柱色已按档位逐柱取色，此参数不再参与绘制 */
  bandColor?: string;
  height?: number;
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
      const labelFont = isFieldGuide ? '10px ui-monospace, Consolas, monospace' : '10px system-ui, sans-serif';

      const plotW = w - PAD_L - PAD_R;
      const plotH = h - PAD_T - PAD_B;
      const n = Math.max(1, points.length);
      const slot = plotW / n;
      const barW = Math.max(2, slot * 0.7);
      const cx = (i: number) => PAD_L + slot * (i + 0.5);
      const y = (total: number) => PAD_T + (1 - Math.min(100, Math.max(0, total)) / 100) * plotH;

      // 横向网格（0/25/50/75/100）+ 刻度
      ctx.strokeStyle = outlineSkin ? ink : border;
      ctx.lineWidth = 1;
      ctx.font = labelFont;
      ctx.fillStyle = dim;
      ctx.textAlign = 'right';
      ctx.textBaseline = 'middle';
      if (outlineSkin) ctx.globalAlpha = isJournal ? 0.14 : 0.18;
      for (const v of [0, 25, 50, 75, 100]) {
        const yy = Math.round(y(v)) + 0.5;
        ctx.beginPath();
        ctx.moveTo(PAD_L, yy);
        ctx.lineTo(w - PAD_R, yy);
        ctx.stroke();
      }
      ctx.globalAlpha = 1;
      for (const v of [0, 25, 50, 75, 100]) {
        ctx.fillText(String(v), PAD_L - 6, y(v));
      }

      if (points.length === 0) return;

      // 柱体：逐柱按档位取色
      const bottom = y(0);
      points.forEach((p, i) => {
        const top = y(p.total);
        const band = resolveScoreBand(p.total);
        ctx.fillStyle = band.color;
        const left = cx(i) - barW / 2;
        const heightPx = Math.max(1, bottom - top);
        ctx.fillRect(left, top, barW, heightPx);
        // D：每根柱体加 1.5px 墨线描边，贴纸图鉴的色块感（A 保持原样）
        if (outlineSkin) {
          ctx.strokeStyle = ink;
          ctx.lineWidth = isJournal ? 1.6 : 1.5;
          ctx.strokeRect(left + 0.75, top + 0.75, Math.max(1, barW - 1.5), Math.max(1, heightPx - 1.5));
        }
      });

      // ≥80 连续段 → 顶部「窗口」横条（与 App 一致：EXPLOSIVE 冻结色 + onColor 文字）
      const strip = resolveScoreBand(100);
      let start = -1;
      for (let i = 0; i <= points.length; i += 1) {
        const high = i < points.length && points[i].total >= WINDOW_THRESHOLD;
        if (high && start < 0) start = i;
        if (!high && start >= 0) {
          const end = i - 1;
          const x1 = cx(start) - slot / 2;
          const x2 = cx(end) + slot / 2;
          const stripH = x2 - x1 >= 46 ? 14 : 5;
          ctx.fillStyle = strip.color;
          ctx.fillRect(x1, PAD_T - stripH - 4, x2 - x1, stripH);
          if (outlineSkin) {
            ctx.strokeStyle = ink;
            ctx.lineWidth = 1.5;
            ctx.strokeRect(x1 + 0.75, PAD_T - stripH - 4 + 0.75, x2 - x1 - 1.5, stripH - 1.5);
          }
          if (stripH > 5) {
            ctx.fillStyle = strip.onColor;
            ctx.font = labelFont;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText('窗口', (x1 + x2) / 2, PAD_T - stripH / 2 - 4);
          }
          start = -1;
        }
      }

      // x 轴：每 3 小时一个当地小时标签（与 App 同步）
      ctx.fillStyle = dim;
      ctx.font = labelFont;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'top';
      for (let i = 0; i < points.length; i += 3) {
        ctx.fillText(String(points[i].hour), cx(i), h - PAD_B + 6);
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
  }, [points, height]);

  return (
    <div className="hour-chart">
      <canvas ref={ref} role="img" aria-label="逐时指数柱状图（五档色带，80 分以上标窗口）" />
    </div>
  );
}