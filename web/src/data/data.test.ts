/**
 * 数据层纯逻辑与客户端的确定性测试（不访问网络）。
 */
import { describe, expect, it, vi } from 'vitest';
import { classify, extractReason, DataError } from './errors';
import { RetryExecutor } from './retry';
import { TokenBucket } from './tokenBucket';
import { MinRefreshGate } from './refreshGate';
import { isFresh, staleForMillis, unusableAgeMillis, DATA_SOURCES } from './ttl';
import { joinSeries } from './seriesJoin';
import {
  MARINE_HOURLY,
  WEATHER_HOURLY,
  marineUrl,
  ostiaFeatureInfoUrl,
  photonReverseUrl,
  weatherUrl,
} from './urls';
import { isMarineAllNull, mapMarineHourly, mapWeatherHourly, parseEpochSec, parseEpochSecOrNull } from './dto';
import { OpenMeteoClient } from './client';

describe('ttl', () => {
  const now = 1_000_000_000;

  it('天气 3h / 海洋 6h', () => {
    expect(DATA_SOURCES.weather.ttlMillis).toBe(3 * 3600_000);
    expect(DATA_SOURCES.marine.ttlMillis).toBe(6 * 3600_000);
  });

  it('边界：刚好 TTL 不算新鲜', () => {
    expect(isFresh('weather', now - 3 * 3600_000 + 1, now)).toBe(true);
    expect(isFresh('weather', now - 3 * 3600_000, now)).toBe(false);
    expect(isFresh('marine', now - 6 * 3600_000 + 1, now)).toBe(true);
    expect(isFresh('marine', now - 6 * 3600_000, now)).toBe(false);
  });

  it('无拉取记录（0）不新鲜', () => {
    expect(isFresh('weather', 0, now)).toBe(false);
    expect(staleForMillis('weather', 0, now)).toBeNull();
    expect(unusableAgeMillis(0, now)).toBeNull();
  });

  it('过期与不可用阈值', () => {
    expect(staleForMillis('weather', now - 4 * 3600_000, now)).toBe(3600_000);
    expect(unusableAgeMillis(now - 48 * 3600_000, now)).toBe(48 * 3600_000);
    expect(unusableAgeMillis(now - 48 * 3600_000 + 1, now)).toBeNull();
  });
});

describe('MinRefreshGate', () => {
  it('同一 key 15min 内拒绝，成功后才占窗口', () => {
    const gate = new MinRefreshGate();
    expect(gate.tryBegin('spot:1', 0)).toBe(true);
    expect(gate.tryBegin('spot:1', 1)).toBe(false); // 在途
    gate.finishSuccess('spot:1', 0);
    expect(gate.tryBegin('spot:1', 14 * 60_000)).toBe(false);
    expect(gate.tryBegin('spot:1', 15 * 60_000)).toBe(true);
  });

  it('失败只释放，不占15min窗口', () => {
    const gate = new MinRefreshGate();
    expect(gate.tryBegin('a', 0)).toBe(true);
    gate.finishFailure('a');
    expect(gate.tryBegin('a', 1)).toBe(true);
    expect(gate.inFlightCount()).toBe(1);
    gate.finishFailure('a');
    expect(gate.inFlightCount()).toBe(0);
  });
});

describe('RetryExecutor', () => {
  it('按 1s/4s/10s 退避，最多 3 次重试', async () => {
    const waits: number[] = [];
    const retry = new RetryExecutor([1000, 4000, 10000], async (ms) => {
      waits.push(ms);
    });
    const block = vi.fn(async () => {
      throw new DataError('ServerError', 'boom', { httpStatus: 503 });
    });
    await expect(retry.execute(block)).rejects.toBeInstanceOf(DataError);
    expect(waits).toEqual([1000, 4000, 10000]);
    expect(block).toHaveBeenCalledTimes(4);
  });

  it('不可重试错误立即抛出', async () => {
    const waits: number[] = [];
    const retry = new RetryExecutor([1000, 4000, 10000], async (ms) => {
      waits.push(ms);
    });
    const block = vi.fn(async () => {
      throw new DataError('SourceParamError', 'bad param', { httpStatus: 400 });
    });
    await expect(retry.execute(block)).rejects.toBeInstanceOf(DataError);
    expect(waits).toEqual([]);
    expect(block).toHaveBeenCalledTimes(1);
  });

  it('成功后不再重试', async () => {
    const retry = new RetryExecutor([1000], async () => {});
    const block = vi.fn(async () => 42);
    await expect(retry.execute(block)).resolves.toBe(42);
    expect(block).toHaveBeenCalledTimes(1);
  });
});

describe('ApiErrorClassifier', () => {
  it('429 / 5xx 可重试', () => {
    expect(classify(429).kind).toBe('RateLimited');
    expect(classify(503, '{"reason":"down"}').kind).toBe('ServerError');
    expect(classify(503).isRetryable).toBe(true);
  });

  it('B9 参数错误与普通 4xx 不可重试', () => {
    const b9 = classify(400, '{"error":true,"reason":"Cannot initialize MultiDomains"}');
    expect(b9.kind).toBe('SourceParamError');
    expect(b9.isRetryable).toBe(false);
    expect(classify(400, '{"error":{"message":"Wrong Time Zone"}}').kind).toBe('SourceParamError');
    expect(classify(404, '{"reason":"nope"}').kind).toBe('BadRequest');
  });

  it('extractReason 兼容两种错误体与脏响应', () => {
    expect(extractReason('{"error":true,"reason":"x"}')).toBe('x');
    expect(extractReason('{"error":{"message":"y"}}')).toBe('y');
    expect(extractReason('plain text')).toBe('plain text');
    expect(extractReason('')).toBeNull();
  });
});

describe('TokenBucket', () => {
  it('容量 6：连取 6 次后需要等待', async () => {
    let now = 0;
    const waits: number[] = [];
    const bucket = new TokenBucket(6, 60_000, () => now, async (ms) => {
      waits.push(ms);
      now += ms;
    });

    for (let i = 0; i < 6; i += 1) await bucket.acquire('BACKGROUND');
    expect(await bucket.tryAcquire()).toBe(false);
    await bucket.acquire('BACKGROUND');
    expect(waits.length).toBe(1);
    expect(waits[0]).toBeGreaterThan(0);
  });

  it('用户等待时后台让出', async () => {
    let now = 0;
    const waits: number[] = [];
    const bucket = new TokenBucket(6, 60_000, () => now, async (ms) => {
      waits.push(ms);
      now += ms;
    });
    // 消耗到只剩 1 个令牌
    for (let i = 0; i < 5; i += 1) await bucket.acquire('BACKGROUND');

    let userDone = false;
    const user = bucket.acquire('USER').then(() => {
      userDone = true;
    });
    // 让用户先登记为等待者
    await Promise.resolve();
    await Promise.resolve();

    const background = bucket.acquire('BACKGROUND');
    await Promise.all([user, background]);
    expect(userDone).toBe(true);
  });
});

describe('urls', () => {
  it('天气 URL 含 21 项 hourly 与 7 日回看', () => {
    const u = weatherUrl('https://api.open-meteo.com', 36.06, 120.38);
    expect(u).toContain('/v1/forecast');
    expect(u).toContain('past_days=7');
    expect(u).toContain('forecast_days=10');
    expect(u).toContain('timezone=auto');
    for (const p of WEATHER_HOURLY) expect(u).toContain(p);
  });

  it('海洋 URL 的潮位字段必须是 sea_level_height_msl（写 mssh 会 400）', () => {
    const u = marineUrl('https://marine-api.open-meteo.com', 36.06, 120.38);
    expect(u).toContain('sea_level_height_msl');
    expect(u).not.toContain('mssh');
    expect(u).toContain('past_days=1');
    for (const p of MARINE_HOURLY) expect(u).toContain(p);
  });

  it('Photon 逆地理禁止 lang 参数', () => {
    const u = photonReverseUrl('https://photon.komoot.io', 36.06, 120.38);
    expect(u).not.toContain('lang=');
    expect(u).toContain('layer=locality');
    expect(u).toContain('limit=1');
  });

  it('OSTIA GetFeatureInfo 的瓦片号符合 Web Mercator', () => {
    const u = ostiaFeatureInfoUrl('https://wmts.marine.copernicus.eu', 36.06, 120.38);
    expect(u).toContain('TILEMATRIX=7');
    expect(u).toContain('TILECOL=106');
    expect(u).toContain('TILEROW=50');
    expect(u).toContain('INFOFORMAT=application%2Fjson');
  });
});

describe('dto', () => {
  it('本地墙钟 + 偏移 → epoch', () => {
    expect(parseEpochSec('2026-11-02T17:00', 8 * 3600)).toBe(Date.UTC(2026, 10, 2, 9, 0) / 1000);
    expect(parseEpochSec('2026-11-02T17:00:30', 0)).toBe(Date.UTC(2026, 10, 2, 17, 0, 30) / 1000);
  });

  it('坏时间串返回 null（不抛）', () => {
    expect(parseEpochSecOrNull('', 0)).toBeNull();
    expect(parseEpochSecOrNull('2026/11/02 17:00', 0)).toBeNull();
    expect(parseEpochSecOrNull(null, 0)).toBeNull();
  });

  it('天气映射：km → m，缺失保持 null', () => {
    const hourly = mapWeatherHourly({
      utc_offset_seconds: 8 * 3600,
      hourly: {
        time: ['2026-11-02T17:00', 'bad'],
        temperature_2m: [18.5, 19],
        visibility: [24, 10],
        pressure_msl: [1013, 1012],
        weather_code: [2, 3],
      },
    });
    expect(hourly).toHaveLength(1);
    expect(hourly[0].temperatureC).toBe(18.5);
    expect(hourly[0].visibilityM).toBe(24000);
    expect(hourly[0].pressureMslHpa).toBe(1013);
    expect(hourly[0].rainMm).toBeNull();
    expect(hourly[0].timeEpochSec).toBe(parseEpochSec('2026-11-02T17:00', 8 * 3600));
  });

  it('海洋映射与内陆全 null 判定', () => {
    const marine = mapMarineHourly({
      utc_offset_seconds: 0,
      hourly: {
        time: ['2026-11-02T17:00'],
        wave_height: [null],
        sea_level_height_msl: [null],
        sea_surface_temperature: [null],
      },
    });
    expect(marine).toHaveLength(1);
    expect(isMarineAllNull(marine[0])).toBe(true);
  });
});

describe('seriesJoin', () => {
  it('按 epoch 并集升序，不按下标对齐', () => {
    const slices = joinSeries(
      [
        { timeEpochSec: 2, temperatureC: 1 },
        { timeEpochSec: 1, temperatureC: 2 },
        { timeEpochSec: 3, temperatureC: 3 },
      ],
      [{ timeEpochSec: 2, waveHeightM: 0.5 }],
    );
    expect(slices.map((s) => s.timeEpochSec)).toEqual([1, 2, 3]);
    expect(slices[0].weather?.temperatureC).toBe(2);
    expect(slices[1].marine?.waveHeightM).toBe(0.5);
    expect(slices[2].marine).toBeNull();
  });
});

describe('OpenMeteoClient（mock fetch）', () => {
  function jsonResponse(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
  }

  it('200 → 解析为内部逐小时', async () => {
    const fetchFn = vi.fn(async () =>
      jsonResponse({
        utc_offset_seconds: 0,
        hourly: { time: ['2026-11-02T00:00'], temperature_2m: [12.5], pressure_msl: [1010] },
      }),
    );
    const client = new OpenMeteoClient({ fetchFn: fetchFn as any });
    const hourly = await client.fetchWeatherHourly(1, 2);
    expect(hourly).toHaveLength(1);
    expect(hourly[0].temperatureC).toBe(12.5);
    expect(fetchFn).toHaveBeenCalledTimes(1);
  });

  it('429 重试后成功（注入零延迟）', async () => {
    let calls = 0;
    const fetchFn = vi.fn(async () => {
      calls += 1;
      if (calls === 1) return new Response('{"error":true,"reason":"rate"}', { status: 429 });
      return jsonResponse({ utc_offset_seconds: 0, hourly: { time: ['2026-11-02T00:00'], temperature_2m: [1] } });
    });
    const client = new OpenMeteoClient({ fetchFn: fetchFn as any, retryDelays: [0] });
    const hourly = await client.fetchWeatherHourly(1, 2);
    expect(hourly).toHaveLength(1);
    expect(calls).toBe(2);
  });

  it('400 参数错误不重试', async () => {
    const fetchFn = vi.fn(async () =>
      new Response('{"error":true,"reason":"Cannot initialize MultiDomains"}', { status: 400 }),
    );
    const client = new OpenMeteoClient({ fetchFn: fetchFn as any, retryDelays: [0, 0, 0] });
    await expect(client.fetchMarine(1, 2)).rejects.toMatchObject({ kind: 'SourceParamError' });
    expect(fetchFn).toHaveBeenCalledTimes(1);
  });

  it('脏 JSON → NetworkError', async () => {
    const fetchFn = vi.fn(async () => new Response('<<<not json>>>', { status: 200 }));
    const client = new OpenMeteoClient({ fetchFn: fetchFn as any });
    await expect(client.fetchWeather(1, 2)).rejects.toMatchObject({ kind: 'NetworkError' });
  });
});