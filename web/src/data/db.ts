/**
 * IndexedDB（Dexie）schema —— 与 Android Room 表语义对齐（字段名尽量一致）。
 * 表：spots / hourly_metric / hourly_score / fetch_log / manual_observation / manual_tide_fit / meta
 *
 * 迁移规则：只增版本 + upgrade()，禁止删表（同 CONTRACT 只增不改精神）。
 */
import Dexie, { type Table } from 'dexie';
import type { WaterBodyType } from '../domain/types';

export interface SpotRow {
  id?: number;
  name: string;
  lat: number;
  lon: number;
  preset?: string | null;
  tags?: string[];
  waterBody: WaterBodyType;
  createdAt: number;
  lastViewedAt: number;
  tzOffsetSec?: number | null;
  memo?: string | null;
  groupName?: string | null;
  elevation?: number | null;
}

export type MetricKind = 'weather' | 'marine';

/** 逐时指标：一行 = 一个点 + 一个源 + 一个小时（payload 为 HourlyWeather/HourlyMarine 的 JSON） */
export interface HourlyMetricRow {
  spotId: number;
  kind: MetricKind;
  time: number;
  payload: string;
  fetchedAt: number;
}

export interface HourlyScoreRow {
  spotId: number;
  time: number;
  payload: string;
  algoVersion: number;
}

export interface FetchLogRow {
  id?: number;
  spotId: number | null;
  source: string;
  ts: number;
  httpStatus: number | null;
  bytes: number | null;
  costMs: number;
}

export interface ManualObservationRow {
  id?: number;
  spotId: number;
  ts: number;
  payload: string;
}

export interface ManualTideFitRow {
  id?: number;
  spotId: number;
  payload: string;
}

export class TideSpotDb extends Dexie {
  spots!: Table<SpotRow, number>;
  hourly_metric!: Table<HourlyMetricRow, [number, string, number]>;
  hourly_score!: Table<HourlyScoreRow, [number, number]>;
  fetch_log!: Table<FetchLogRow, number>;
  manual_observation!: Table<ManualObservationRow, number>;
  manual_tide_fit!: Table<ManualTideFitRow, number>;
  meta!: Table<{ key: string; value: string }, string>;

  constructor(name = 'tidespot') {
    super(name);
    this.version(1).stores({
      spots: '++id, name, waterBody, lastViewedAt, createdAt',
      hourly_metric: '[spotId+kind+time], spotId, kind, fetchedAt',
      hourly_score: '[spotId+time], spotId, time',
      fetch_log: '++id, spotId, source, ts',
      manual_observation: '++id, spotId, ts',
      manual_tide_fit: '++id, spotId',
      meta: 'key',
    });
  }
}

/** 生产单例（懒打开，避免 SSR/测试副作用） */
let instance: TideSpotDb | null = null;
export function getDb(): TideSpotDb {
  if (!instance) instance = new TideSpotDb();
  return instance;
}

/** 测试用：替换实例 */
export function setDbForTest(db: TideSpotDb | null): void {
  instance = db;
}