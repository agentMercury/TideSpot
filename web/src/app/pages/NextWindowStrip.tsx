/**
 * 首页「下一个最佳窗口」提示条（issue #2）。
 *
 * 定位：常驻地图页，回答「现在该不该出门 / 下一个窗口还有多久」。
 * 与 Top10Strip 的区别：Top10 回答「哪个最好」（按 rank），本组件回答「最近的是哪个」（按时间紧迫性）。
 *
 * 依赖 useTopWindows 已在内存中的聚合结果，不额外触发网络请求。
 */
import { useEffect, useMemo, useState } from 'react';
import { navigate } from '../router';
import { formatDuration, formatWindowRange, findNextWindow } from '../../domain/nextWindow';
import type { SpotWindow } from './Top10Panel';
import { useLang } from '../../ui/i18n';

/** 每 30 秒重算一次，让倒计时保持新鲜但不至于每帧刷新 */
const TICK_MS = 30_000;

export function NextWindowStrip({
  windows,
  loading,
}: {
  windows: SpotWindow[];
  loading: boolean;
}) {
  const [lang] = useLang();
  const L = (zh: string, en: string) => (lang === 'en' ? en : zh);
  const [nowSec, setNowSec] = useState(() => Math.floor(Date.now() / 1000));

  useEffect(() => {
    const id = window.setInterval(() => setNowSec(Math.floor(Date.now() / 1000)), TICK_MS);
    return () => window.clearInterval(id);
  }, []);

  const next = useMemo(() => findNextWindow(windows, nowSec), [windows, nowSec]);

  // 加载中或没有任何窗口时不占位（避免首页噪声）
  if (loading || next.state === 'none' || !next.window) return null;

  const w = next.window;
  const range = formatWindowRange(w, w.tzSec);

  const label =
    next.state === 'in-window'
      ? L('正在窗口内', 'In window')
      : L('下一个窗口', 'Next window');

  const detail =
    next.state === 'in-window'
      ? L(`${w.spotName} · ${range} · 还剩 ${formatDuration(next.remainingSec)}`,
          `${w.spotName} · ${range} · ${formatDuration(next.remainingSec)} left`)
      : L(`${w.spotName} · ${range} · 还有 ${formatDuration(next.remainingSec)}`,
          `${w.spotName} · ${range} · in ${formatDuration(next.remainingSec)}`);

  return (
    <button
      type="button"
      className={`next-window-strip ${next.state === 'in-window' ? 'is-active' : 'is-upcoming'}`}
      aria-label={detail}
      onClick={() => navigate(`#/spot/${w.spotId}`)}
    >
      <span className="next-window-strip__label">{label}</span>
      <span className="next-window-strip__detail">{detail}</span>
      <span className="next-window-strip__peak">{w.peak}</span>
    </button>
  );
}
