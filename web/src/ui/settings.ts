/**
 * 用户设置（localStorage）—— 与 Android `SettingsLogic` 的持久化口径对齐的子集。
 * 只用英文标识符；读取失败一律回落默认值（隐私模式/损坏值时不让页面崩）。
 */
import { useEffect, useState } from 'react';

export type MapProvider = 'openfreemap' | 'carto';
export type MapStyleId = 'color' | 'plain' | 'dark';

export interface AppSettings {
  /** G-07 底图提供方 */
  mapProvider: MapProvider;
  /** 固定样式；auto 由主题决定（B5） */
  mapStyle: MapStyleId;
}

const STORAGE_KEY = 'tidespot.settings';
const DEFAULTS: AppSettings = { mapProvider: 'openfreemap', mapStyle: 'color' };

const listeners = new Set<() => void>();

function read(): AppSettings {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULTS };
    const parsed = JSON.parse(raw) as Partial<AppSettings>;
    return {
      mapProvider: parsed.mapProvider === 'carto' ? 'carto' : 'openfreemap',
      mapStyle: parsed.mapStyle === 'plain' || parsed.mapStyle === 'dark' ? parsed.mapStyle : 'color',
    };
  } catch {
    return { ...DEFAULTS };
  }
}

let current: AppSettings = read();

export function getSettings(): AppSettings {
  return current;
}

export function updateSettings(patch: Partial<AppSettings>): void {
  current = { ...current, ...patch };
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(current));
  } catch {
    // 忽略
  }
  for (const listener of listeners) listener();
}

/** 订阅设置；返回当前值（组件会在 updateSettings 后重渲染）。 */
export function useSettings(): AppSettings {
  const [value, setValue] = useState<AppSettings>(current);
  useEffect(() => {
    const listener = () => setValue(current);
    listeners.add(listener);
    if (value !== current) setValue(current);
    return () => {
      listeners.delete(listener);
    };
  }, [value]);
  return value;
}