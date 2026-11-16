/**
 * 与 Android `algorithm/ScenePreset.kt` 中的 Presets 逐行等价（SRS §6.4 表）。
 * 权重口径必须与 Kotlin 完全一致；权重和用于低置信判定。
 */
import type { ScenePreset } from './types';

/** 淡水·路亚（鲈/翘嘴） */
export const FRESH_LURE: ScenePreset = {
  id: 'fresh_lure',
  label: '淡水·路亚',
  salinityPpt: 0.0,
  nightTarget: false,
  tempOptC: 22.0,
  tempSigmaC: 7.0,
  doBase: 8.0,
  weights: {
    OXYGEN: 0.21,
    PRESSURE: 0.14,
    SOLUNAR: 0.17,
    WIND: 0.1,
    WEATHER: 0.1,
    DAYPART: 0.08,
    TEMP_MATCH: 0.14,
    COMFORT: 0.06,
  },
};

/** 海水·岸边/矶钓（黑鲷/海鲈），tide 权重最高 0.24 */
export const SEA_SHORE: ScenePreset = {
  id: 'sea_shore',
  label: '海水·岸边/矶钓',
  salinityPpt: 30.0,
  nightTarget: true,
  tempOptC: 20.0,
  tempSigmaC: 6.0,
  doBase: 7.4,
  weights: {
    OXYGEN: 0.14,
    PRESSURE: 0.11,
    SOLUNAR: 0.13,
    TIDE: 0.24,
    WIND: 0.11,
    WEATHER: 0.09,
    DAYPART: 0.06,
    TEMP_MATCH: 0.06,
    COMFORT: 0.06,
  },
};

/** 淡水·台钓（鲫） */
export const FRESH_BAIT: ScenePreset = {
  id: 'fresh_bait',
  label: '淡水·台钓',
  salinityPpt: 0.0,
  nightTarget: false,
  tempOptC: 18.0,
  tempSigmaC: 9.0,
  doBase: 7.6,
  weights: {
    OXYGEN: 0.22,
    PRESSURE: 0.13,
    SOLUNAR: 0.16,
    WIND: 0.1,
    WEATHER: 0.11,
    DAYPART: 0.1,
    TEMP_MATCH: 0.12,
    COMFORT: 0.06,
  },
};

export const PRESETS: ScenePreset[] = [FRESH_LURE, SEA_SHORE, FRESH_BAIT];

export function presetById(id: string): ScenePreset | null {
  return PRESETS.find((p) => p.id === id) ?? null;
}