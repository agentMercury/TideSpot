/**
 * 主题（跟随系统 / 手动浅色 / 手动深色）。
 * 与 Android `follow_system_dark = true` 对齐；用户选择存 localStorage。
 */
import { useCallback, useEffect, useState } from 'react';

export type ThemeMode = 'auto' | 'light' | 'dark';
export type ResolvedTheme = 'light' | 'dark';

const STORAGE_KEY = 'tidespot.theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';

export function getThemeMode(): ThemeMode {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    return raw === 'light' || raw === 'dark' ? raw : 'auto';
  } catch {
    return 'auto';
  }
}

export function resolveTheme(mode: ThemeMode, prefersDark: boolean): ResolvedTheme {
  if (mode === 'light') return 'light';
  if (mode === 'dark') return 'dark';
  return prefersDark ? 'dark' : 'light';
}

export function applyTheme(mode: ThemeMode, prefersDark = window.matchMedia(DARK_QUERY).matches): ResolvedTheme {
  const resolved = resolveTheme(mode, prefersDark);
  document.documentElement.dataset.theme = resolved;
  document.documentElement.style.colorScheme = resolved;
  return resolved;
}

export function setThemeMode(mode: ThemeMode): ResolvedTheme {
  try {
    if (mode === 'auto') window.localStorage.removeItem(STORAGE_KEY);
    else window.localStorage.setItem(STORAGE_KEY, mode);
  } catch {
    // 隐私模式忽略
  }
  return applyTheme(mode);
}

/** 启动时调用一次（main.tsx） */
export function initTheme(): void {
  applyTheme(getThemeMode());
}

export function useThemeMode(): [ThemeMode, (mode: ThemeMode) => void, ResolvedTheme] {
  const [mode, setModeState] = useState<ThemeMode>(getThemeMode);
  const [resolved, setResolved] = useState<ResolvedTheme>(() => applyTheme(getThemeMode()));

  const setMode = useCallback((next: ThemeMode) => {
    setModeState(next);
    setResolved(setThemeMode(next));
  }, []);

  useEffect(() => {
    const mq = window.matchMedia(DARK_QUERY);
    const onChange = () => {
      if (mode === 'auto') setResolved(applyTheme('auto', mq.matches));
    };
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, [mode]);

  return [mode, setMode, resolved];
}