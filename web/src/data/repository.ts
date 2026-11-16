/**
 * Repository 门面 —— 与 Android `core/data/repo/TideSpotRepository.kt` 对齐。
 * 职责：TTL 命中 → 缓存回退 → 并行拉取（≤4）→ 写库 → 按时间戳 join → 评分 → 降级标记。
 */
import { calculate } from '../domain/fishScore';
import { FRESH_LURE, presetById } from '../domain/presets';
import type {
  FishScoreResult,
  HourlyMarine,
  HourlyWeather,
  ScenePreset,
  WaterBodyType,
} from '../domain/types';
import type { OpenMeteoClient } from './client';
import { getDb, type FetchLogRow, type SpotRow } from './db';
import { isMarineAllNull } from './dto';
import { MinRefreshGate } from './refreshGate';
import { joinSeries, type HourSlice } from './seriesJoin';
import { isFresh, unusableAgeMillis, type DataSourceId } from './ttl';

export interface SpotDetail {
  spotId: number;
  slices: HourSlice[];
  scores: FishScoreResult[];
  weatherDegraded: boolean;
  marineDegraded: boolean;
  marineAllNullInland: boolean;
  servedFromCache: string[];
  fetchedAtMillis: Partial<Record<DataSourceId, number>>;
  presetId: string;
  /** 缓存年龄 ≥48h 的源（不再据此评分，UI 挂「数据过期」） */
  unusable: string[];
}

export interface RepositoryDeps {
  client: OpenMeteoClient;
  now?: () => number;
  gate?: MinRefreshGate;
  /** 单次刷新整体预算（毫秒）；超时按失败降级，避免多源重试叠加成分钟级等待 */
  networkDeadlineMillis?: number;
}

export interface AddFavoriteInput {
  name: string;
  lat: number;
  lon: number;
  preset?: string | null;
  waterBody?: WaterBodyType;
  tags?: string[];
  memo?: string | null;
  groupName?: string | null;
  elevation?: number | null;
}

/** 给 Promise 加整体超时；超时 rejected → 走降级分支（缓存优先），不阻塞首屏。 */
function withDeadline<T>(promise: Promise<T>, millis: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`deadline ${millis}ms exceeded`)), millis);
    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (err) => {
        clearTimeout(timer);
        reject(err);
      },
    );
  });
}

interface CachedSeries {
  weather: HourlyWeather[];
  marine: HourlyMarine[];
  fetchedAt: Partial<Record<DataSourceId, number>>;
}

export class TideSpotRepository {
  private readonly client: OpenMeteoClient;
  private readonly now: () => number;
  private readonly gate: MinRefreshGate;
  private readonly networkDeadlineMillis: number;
  /** 同点并发刷新共享同一个 Promise（React StrictMode 双执行时，第二次不再拿到空缓存） */
  private readonly refreshes = new Map<string, Promise<void>>();

  constructor(deps: RepositoryDeps) {
    this.client = deps.client;
    this.now = deps.now ?? (() => Date.now());
    this.gate = deps.gate ?? new MinRefreshGate();
    this.networkDeadlineMillis = deps.networkDeadlineMillis ?? 15_000;
  }

  // ---------------- 收藏 ----------------

  async listFavorites(): Promise<SpotRow[]> {
    const rows = await getDb().spots.toArray();
    return rows.sort((a, b) => b.lastViewedAt - a.lastViewedAt);
  }

  async getSpot(id: number): Promise<SpotRow | undefined> {
    return getDb().spots.get(id);
  }

  async addFavorite(input: AddFavoriteInput): Promise<number> {
    const at = this.now();
    return getDb().spots.add({
      name: input.name,
      lat: input.lat,
      lon: input.lon,
      preset: input.preset ?? null,
      tags: input.tags ?? [],
      waterBody: input.waterBody ?? 'UNKNOWN',
      createdAt: at,
      lastViewedAt: at,
      memo: input.memo ?? null,
      groupName: input.groupName ?? null,
      elevation: input.elevation ?? null,
    });
  }

  /** 局部更新收藏（标签/分组/备注等）；不存在的 id 静默忽略（Dexie update 语义）。 */
  async updateFavorite(
    id: number,
    patch: Partial<Pick<SpotRow, 'name' | 'tags' | 'groupName' | 'memo' | 'waterBody' | 'preset'>>,
  ): Promise<void> {
    await getDb().spots.update(id, patch);
  }

  async removeFavorite(id: number): Promise<void> {
    const db = getDb();
    await db.transaction('rw', db.spots, db.hourly_metric, db.hourly_score, async () => {
      await db.spots.delete(id);
      await db.hourly_metric.where('spotId').equals(id).delete();
      await db.hourly_score.where('spotId').equals(id).delete();
    });
  }

  async markViewed(id: number): Promise<void> {
    await getDb().spots.update(id, { lastViewedAt: this.now() });
  }

  // ---------------- 详情 ----------------

  /** 只读缓存（用于首屏立即渲染，不发网络） */
  async readCachedDetail(spotId: number): Promise<SpotDetail | null> {
    const spot = await this.getSpot(spotId);
    if (!spot) return null;
    const cached = await this.readCached(spotId);
    if (cached.weather.length === 0 && cached.marine.length === 0) return null;
    return this.buildDetail(spot, cached, [], [], []);
  }

  async getDetail(spotId: number, options: { force?: boolean } = {}): Promise<SpotDetail> {
    const spot = await this.getSpot(spotId);
    if (!spot) throw new Error(`spot ${spotId} not found`);

    const now = this.now();
    const cached = await this.readCached(spotId);
    const wFetched = cached.fetchedAt.weather ?? 0;
    const mFetched = cached.fetchedAt.marine ?? 0;
    const wFresh = isFresh('weather', wFetched, now);
    const mFresh = isFresh('marine', mFetched, now);
    const force = options.force === true;

    const served: string[] = [];
    let weather = cached.weather;
    let marine = cached.marine;
    let weatherDegraded = false;
    let marineDegraded = false;

    const needWeather = force || !wFresh;
    const needMarine = force || !mFresh;

    if (needWeather || needMarine) {
      const key = `spot:${spotId}`;
      const began = this.gate.tryBegin(key, now);
      if (!began) {
        // 在途：等同一次真实刷新结果（StrictMode 双执行时第二次会走到这里，不能直接返回空）
        const running = this.refreshes.get(key);
        if (running) await running.catch(() => undefined);
        const latest = await this.readCached(spotId);
        weather = latest.weather;
        marine = latest.marine;
        if (weather.length > 0) served.push('weather');
        if (marine.length > 0) served.push('marine');
      } else {
        let succeeded = false;
        const job = (async () => {
          const [wRes, mRes] = await Promise.allSettled([
            needWeather
              ? withDeadline(this.client.fetchWeatherHourly(spot.lat, spot.lon), this.networkDeadlineMillis)
              : Promise.resolve(weather),
            needMarine
              ? withDeadline(this.client.fetchMarineHourly(spot.lat, spot.lon), this.networkDeadlineMillis)
              : Promise.resolve(marine),
          ]);

          if (wRes.status === 'fulfilled' && wRes.value.length > 0) {
            weather = wRes.value;
            await this.writeWeather(spotId, weather, this.now());
          } else if (weather.length > 0) {
            weatherDegraded = true;
          } else {
            weatherDegraded = true;
          }

          if (mRes.status === 'fulfilled' && mRes.value.length > 0) {
            marine = mRes.value;
            await this.writeMarine(spotId, marine, this.now());
          } else if (marine.length > 0) {
            marineDegraded = true;
          } else {
            marineDegraded = true;
          }

          succeeded = wRes.status === 'fulfilled' && mRes.status === 'fulfilled';
        })();
        this.refreshes.set(key, job);
        try {
          await job;
        } finally {
          this.refreshes.delete(key);
          if (succeeded) this.gate.finishSuccess(key, this.now());
          else this.gate.finishFailure(key);
        }
      }
    } else {
      if (weather.length > 0) served.push('weather');
      if (marine.length > 0) served.push('marine');
    }

    const detail = this.buildDetail(spot, { weather, marine, fetchedAt: cached.fetchedAt }, served, [
      weatherDegraded ? 'weather' : '',
      marineDegraded ? 'marine' : '',
    ].filter(Boolean), []);

    await this.writeScores(spotId, detail.scores);
    await this.markViewed(spotId);
    return detail;
  }

  private buildDetail(
    spot: SpotRow,
    cached: CachedSeries,
    servedFromCache: string[],
    degradedSources: string[],
    extraUnusable: string[],
  ): SpotDetail {
    const now = this.now();
    const slices = joinSeries(cached.weather, cached.marine);
    const preset = this.resolvePreset(spot.preset);
    const marineAllNullInland = cached.marine.length > 0 && cached.marine.every(isMarineAllNull);

    const inputs = slices
      .filter((s): s is HourSlice & { weather: HourlyWeather } => s.weather != null)
      .map((s) => ({
        timeEpochSec: s.timeEpochSec,
        weather: s.weather,
        marine: s.marine,
        sunriseLocalHour: null,
        sunsetLocalHour: null,
        tzOffsetHours: spot.tzOffsetSec != null ? spot.tzOffsetSec / 3600 : 8,
      }));

    const scores = calculate(inputs, preset, spot.lon);

    const unusable = [...extraUnusable];
    for (const source of ['weather', 'marine'] as DataSourceId[]) {
      const at = cached.fetchedAt[source] ?? 0;
      if (unusableAgeMillis(at, now) != null && !unusable.includes(source)) unusable.push(source);
    }

    return {
      spotId: spot.id as number,
      slices,
      scores,
      weatherDegraded: degradedSources.includes('weather'),
      marineDegraded: degradedSources.includes('marine'),
      marineAllNullInland,
      servedFromCache,
      fetchedAtMillis: cached.fetchedAt,
      presetId: preset.id,
      unusable,
    };
  }

  private resolvePreset(presetId: string | null | undefined): ScenePreset {
    return (presetId ? presetById(presetId) : null) ?? FRESH_LURE;
  }

  // ---------------- 缓存读写 ----------------

  private async readCached(spotId: number): Promise<CachedSeries> {
    const rows = await getDb().hourly_metric.where('spotId').equals(spotId).toArray();
    const weather: HourlyWeather[] = [];
    const marine: HourlyMarine[] = [];
    const fetchedAt: Partial<Record<DataSourceId, number>> = {};

    for (const row of rows) {
      try {
        const parsed = JSON.parse(row.payload);
        if (row.kind === 'weather') weather.push(parsed as HourlyWeather);
        else marine.push(parsed as HourlyMarine);
        const prev = fetchedAt[row.kind] ?? 0;
        if (row.fetchedAt > prev) fetchedAt[row.kind] = row.fetchedAt;
      } catch {
        // 脏行跳过，不影响整体
      }
    }

    weather.sort((a, b) => a.timeEpochSec - b.timeEpochSec);
    marine.sort((a, b) => a.timeEpochSec - b.timeEpochSec);
    return { weather, marine, fetchedAt };
  }

  private async writeWeather(spotId: number, rows: HourlyWeather[], fetchedAt: number): Promise<void> {
    const db = getDb();
    await db.transaction('rw', db.hourly_metric, async () => {
      await db.hourly_metric
        .where('spotId')
        .equals(spotId)
        .and((r) => r.kind === 'weather')
        .delete();
      await db.hourly_metric.bulkPut(
        rows.map((row) => ({
          spotId,
          kind: 'weather' as const,
          time: row.timeEpochSec,
          payload: JSON.stringify(row),
          fetchedAt,
        })),
      );
    });
  }

  private async writeMarine(spotId: number, rows: HourlyMarine[], fetchedAt: number): Promise<void> {
    const db = getDb();
    await db.transaction('rw', db.hourly_metric, async () => {
      await db.hourly_metric
        .where('spotId')
        .equals(spotId)
        .and((r) => r.kind === 'marine')
        .delete();
      await db.hourly_metric.bulkPut(
        rows.map((row) => ({
          spotId,
          kind: 'marine' as const,
          time: row.timeEpochSec,
          payload: JSON.stringify(row),
          fetchedAt,
        })),
      );
    });
  }

  private async writeScores(spotId: number, scores: FishScoreResult[]): Promise<void> {
    if (scores.length === 0) return;
    const db = getDb();
    await db.hourly_score.bulkPut(
      scores.map((s) => ({
        spotId,
        time: s.timeEpochSec,
        payload: JSON.stringify(s),
        algoVersion: 1,
      })),
    );
  }

  async logFetch(entry: Omit<FetchLogRow, 'id'>): Promise<void> {
    const db = getDb();
    await db.fetch_log.add(entry);
    const count = await db.fetch_log.count();
    if (count > 200) {
      const overflow = await db.fetch_log.orderBy('id').limit(count - 200).primaryKeys();
      await db.fetch_log.bulkDelete(overflow as number[]);
    }
  }

  async readScores(spotId: number): Promise<FishScoreResult[]> {
    const rows = await getDb().hourly_score.where('spotId').equals(spotId).toArray();
    return rows
      .map((r) => {
        try {
          return JSON.parse(r.payload) as FishScoreResult;
        } catch {
          return null;
        }
      })
      .filter((v): v is FishScoreResult => v != null)
      .sort((a, b) => a.timeEpochSec - b.timeEpochSec);
  }
}