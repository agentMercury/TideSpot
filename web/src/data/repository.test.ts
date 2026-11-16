/**
 * Repository 编排与缓存/降级语义（fake-indexeddb，不访问网络）。
 */
import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getDb, setDbForTest, TideSpotDb } from './db';
import { TideSpotRepository } from './repository';
import type { OpenMeteoClient } from './client';
import type { HourlyMarine, HourlyWeather } from '../domain/types';

const BASE = 1794560000;

function weatherSeries(hours = 24): HourlyWeather[] {
  return Array.from({ length: hours }, (_, i) => ({
    timeEpochSec: BASE + i * 3600,
    temperatureC: 18 + i * 0.1,
    pressureMslHpa: 1015 - i * 0.05,
    cloudCoverPct: 40,
    rainMm: 0,
    showersMm: 0,
    weatherCode: 2,
    windSpeedKmh: 12,
    windGustKmh: 20,
    apparentC: 17,
  }));
}

function marineSeries(hours = 24, allNull = false): HourlyMarine[] {
  return Array.from({ length: hours }, (_, i) => ({
    timeEpochSec: BASE + i * 3600,
    waveHeightM: allNull ? null : 0.8,
    seaLevelHeightMslM: allNull ? null : 1.0 + 0.4 * Math.sin(i * 0.5),
    seaSurfaceTempC: allNull ? null : 19.5,
  }));
}

function mockClient() {
  return {
    fetchWeatherHourly: vi.fn(async () => weatherSeries()),
    fetchMarineHourly: vi.fn(async () => marineSeries()),
  } as unknown as OpenMeteoClient & {
    fetchWeatherHourly: ReturnType<typeof vi.fn>;
    fetchMarineHourly: ReturnType<typeof vi.fn>;
  };
}

let dbName = 0;
function freshDb(): TideSpotDb {
  dbName += 1;
  const db = new TideSpotDb(`tidespot-test-${dbName}`);
  setDbForTest(db);
  return db;
}

describe('收藏 CRUD', () => {
  beforeEach(() => {
    freshDb();
  });

  it('新增 → 列表按最近查看倒序 → 删除', async () => {
    let now = 1000;
    const repo = new TideSpotRepository({ client: mockClient(), now: () => now });
    const a = await repo.addFavorite({ name: 'A', lat: 1, lon: 2 });
    now += 1;
    const b = await repo.addFavorite({ name: 'B', lat: 3, lon: 4 });
    now += 1;

    let list = await repo.listFavorites();
    expect(list.map((s) => s.name)).toEqual(['B', 'A']);

    now += 1;
    await repo.markViewed(a);
    list = await repo.listFavorites();
    expect(list.map((s) => s.name)).toEqual(['A', 'B']);

    await repo.removeFavorite(a);
    list = await repo.listFavorites();
    expect(list.map((s) => s.name)).toEqual(['B']);
    expect(b).toBeGreaterThan(a);
  });

  it('删除收藏会级联清理缓存', async () => {
    const client = mockClient();
    const repo = new TideSpotRepository({ client, now: () => BASE * 1000 });
    const id = await repo.addFavorite({ name: 'A', lat: 36.06, lon: 120.38 });
    await repo.getDetail(id);
    
    const rowsBefore = await getDb().hourly_metric.count();
    expect(rowsBefore).toBeGreaterThan(0);

    await repo.removeFavorite(id);
    expect(await getDb().hourly_metric.count()).toBe(0);
    expect(await getDb().hourly_score.count()).toBe(0);
  });
});

describe('详情缓存与降级', () => {
  beforeEach(() => {
    freshDb();
  });

  it('首次拉取：两源各请求 1 次，写库并产出评分', async () => {
    const client = mockClient();
    const now = BASE * 1000;
    const repo = new TideSpotRepository({ client, now: () => now });
    const id = await repo.addFavorite({ name: 'A', lat: 36.06, lon: 120.38, preset: 'sea_shore' });

    const detail = await repo.getDetail(id);
    expect(client.fetchWeatherHourly).toHaveBeenCalledTimes(1);
    expect(client.fetchMarineHourly).toHaveBeenCalledTimes(1);
    expect(detail.slices).toHaveLength(24);
    expect(detail.scores).toHaveLength(24);
    expect(detail.weatherDegraded).toBe(false);
    expect(detail.marineDegraded).toBe(false);
    expect(detail.presetId).toBe('sea_shore');
    expect(detail.servedFromCache).toEqual([]);
  });

  it('TTL 内二次进入：零请求，标记 servedFromCache', async () => {
    const client = mockClient();
    const now = BASE * 1000;
    const repo = new TideSpotRepository({ client, now: () => now });
    const id = await repo.addFavorite({ name: 'A', lat: 36.06, lon: 120.38 });
    await repo.getDetail(id);
    const second = await repo.getDetail(id);

    expect(client.fetchWeatherHourly).toHaveBeenCalledTimes(1);
    expect(client.fetchMarineHourly).toHaveBeenCalledTimes(1);
    expect(second.servedFromCache.sort()).toEqual(['marine', 'weather']);
  });

  it('TTL 过期：重新拉取', async () => {
    const client = mockClient();
    let now = BASE * 1000;
    const repo = new TideSpotRepository({ client, now: () => now });
    const id = await repo.addFavorite({ name: 'A', lat: 36.06, lon: 120.38 });
    await repo.getDetail(id);

    now += 4 * 3600 * 1000; // 天气 3h 已过、海洋 6h 未过
    await repo.getDetail(id);
    expect(client.fetchWeatherHourly).toHaveBeenCalledTimes(2);
    expect(client.fetchMarineHourly).toHaveBeenCalledTimes(1);
  });

  it('刷新失败：回退缓存并置 degraded，评分仍可用', async () => {
    const client = mockClient();
    let now = BASE * 1000;
    const repo = new TideSpotRepository({ client, now: () => now });
    const id = await repo.addFavorite({ name: 'A', lat: 36.06, lon: 120.38 });
    const first = await repo.getDetail(id);
    expect(first.scores).toHaveLength(24);

    client.fetchWeatherHourly.mockRejectedValueOnce(new Error('offline'));
    client.fetchMarineHourly.mockRejectedValueOnce(new Error('offline'));
    now += 7 * 3600 * 1000; // 两源都过期，且越过 15min 门控窗口

    const detail = await repo.getDetail(id, { force: true });
    expect(detail.weatherDegraded).toBe(true);
    expect(detail.marineDegraded).toBe(true);
    expect(detail.scores).toHaveLength(24);
  });

  it('内陆点：marine 全 null → marineAllNullInland', async () => {
    const client = {
      fetchWeatherHourly: vi.fn(async () => weatherSeries()),
      fetchMarineHourly: vi.fn(async () => marineSeries(24, true)),
    } as unknown as OpenMeteoClient;
    const repo = new TideSpotRepository({ client, now: () => BASE * 1000 });
    const id = await repo.addFavorite({ name: '湖', lat: 29.6, lon: 119.04 });
    const detail = await repo.getDetail(id);
    expect(detail.marineAllNullInland).toBe(true);
    expect(detail.scores.every((s) => s.factors.every((f) => f.id !== 'tide'))).toBe(true);
  });

  it('readCachedDetail 不发请求', async () => {
    const client = mockClient();
    const repo = new TideSpotRepository({ client, now: () => BASE * 1000 });
    const id = await repo.addFavorite({ name: 'A', lat: 1, lon: 2 });
    expect(await repo.readCachedDetail(id)).toBeNull();
    await repo.getDetail(id);
    client.fetchWeatherHourly.mockClear();
    const cached = await repo.readCachedDetail(id);
    expect(cached?.scores.length).toBe(24);
    expect(client.fetchWeatherHourly).not.toHaveBeenCalled();
  });

  it('不存在的 spot 抛错', async () => {
    const repo = new TideSpotRepository({ client: mockClient(), now: () => 0 });
    await expect(repo.getDetail(999)).rejects.toThrow(/not found/);
  });
});