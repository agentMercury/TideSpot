/**
 * 皮肤运行时（SKIN-1）：皮肤 × 水体 × 明暗 三维正交。
 * - 皮肤：用户可选（设置页），持久化 localStorage
 * - 水体：由点位数据决定（海 / 内陆），不可切
 * - 明暗：跟随系统 / 手动（见 ui/theme.ts）
 */
import { useCallback, useEffect, useState } from 'react';

export type SkinId = 'f-bluetide' | 'a-vintage' | 'd-fieldguide' | 'e-tidejournal';
export type WaterKind = 'sea' | 'inland';

export interface SkinMeta {
  id: SkinId;
  name: string;
  nameEn: string;
  /** 皮肤风格一句话（设置页选择卡展示） */
  tagline: string;
}

export const SKINS: SkinMeta[] = [
  { id: 'f-bluetide', name: '蓝潮', nameEn: 'BLUE TIDE', tagline: '白卡 · 薄荷' },
  { id: 'a-vintage', name: '复古户外', nameEn: 'VINTAGE OUTDOOR', tagline: '做旧纸 · 套印' },
  { id: 'd-fieldguide', name: '路亚图鉴', nameEn: 'LURE FIELD GUIDE', tagline: '冷白纸 · 贴纸' },
  { id: 'e-tidejournal', name: '潮汐手帐', nameEn: 'TIDE JOURNAL', tagline: '楷体 · 空心分数' },
];

const SKIN_IDS = SKINS.map((s) => s.id);

/** 默认皮肤：蓝潮（design-handoff v3 的干净基线） */
export const DEFAULT_SKIN: SkinId = 'f-bluetide';

const STORAGE_KEY = 'tidespot.skin';
const WATER_KEY = 'tidespot.water';

export function normalizeSkin(raw: string | null | undefined): SkinId {
  // 兼容旧 id：已选过「清潮」的用户平滑迁移到「蓝潮」
  if (raw === 'f-cleartide') return 'f-bluetide';
  return raw != null && (SKIN_IDS as string[]).includes(raw) ? (raw as SkinId) : DEFAULT_SKIN;
}

export function getSkin(): SkinId {
  try {
    return normalizeSkin(window.localStorage.getItem(STORAGE_KEY));
  } catch {
    return DEFAULT_SKIN;
  }
}

export function applySkin(skin: SkinId, water: WaterKind = getWater()): void {
  document.documentElement.dataset.skin = skin;
  document.documentElement.dataset.water = water;
}

export function setSkin(skin: SkinId): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, skin);
  } catch {
    // 忽略
  }
  applySkin(skin);
}

export function getWater(): WaterKind {
  try {
    return window.localStorage.getItem(WATER_KEY) === 'inland' ? 'inland' : 'sea';
  } catch {
    return 'sea';
  }
}

/** 水体由点位决定：详情页/地图选点后调用；返回上一页时调用方恢复 sea */
export function setWater(water: WaterKind): void {
  try {
    window.localStorage.setItem(WATER_KEY, water);
  } catch {
    // 忽略
  }
  document.documentElement.dataset.water = water;
}

export function initSkin(): void {
  applySkin(getSkin(), getWater());
}

export function skinMeta(id: SkinId): SkinMeta {
  return SKINS.find((s) => s.id === id) ?? SKINS[0];
}

export function useSkin(): [SkinId, (id: SkinId) => void] {
  const [skin, setState] = useState<SkinId>(getSkin);

  useEffect(() => {
    applySkin(skin);
  }, [skin]);

  const change = useCallback((next: SkinId) => {
    setState(next);
    setSkin(next);
  }, []);

  return [skin, change];
}