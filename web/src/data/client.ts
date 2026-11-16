/**
 * 与 Android `core/data/remote/OpenMeteoClient.kt` 对齐。
 * - 令牌桶 6 req/min（§12）
 * - 详情页并行 ≤4（F-4.1）
 * - 重试 1s/4s/10s 三次退避，仅 429/5xx/网络错误
 * - 整窗 240h（past_days + forecast_days=10）
 * - 响应解析失败 → NetworkError（不裸抛 SyntaxError）
 */
import { DataError, classify } from './errors';
import { RetryExecutor } from './retry';
import { TokenBucket } from './tokenBucket';
import { MARINE_BASE, WEATHER_BASE, marineUrl, weatherUrl } from './urls';
import { mapMarineHourly, mapWeatherHourly, type MarineResponseDto, type WeatherResponseDto } from './dto';

export interface FetchLogEntry {
  source: string;
  urlHash: number;
  httpStatus: number | null;
  bytes: number | null;
  costMs: number;
  fetchedAt: number;
}

export interface OpenMeteoClientOptions {
  weatherBase?: string;
  marineBase?: string;
  clock?: () => number;
  fetchFn?: typeof fetch;
  retryDelays?: ReadonlyArray<number>;
  callTimeoutMillis?: number;
  onLog?: (entry: FetchLogEntry) => void;
}

function hashString(value: string): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < value.length; i += 1) {
    h ^= value.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}

export class OpenMeteoClient {
  private readonly weatherBase: string;
  private readonly marineBase: string;
  private readonly clock: () => number;
  private readonly fetchFn: typeof fetch;
  private readonly callTimeoutMillis: number;
  private readonly onLog: (entry: FetchLogEntry) => void;
  private readonly retry: RetryExecutor;
  private readonly bucket: TokenBucket;

  private active = 0;
  private readonly waiters: Array<() => void> = [];
  private readonly parallelLimit = 4;

  constructor(options: OpenMeteoClientOptions = {}) {
    this.weatherBase = options.weatherBase ?? WEATHER_BASE;
    this.marineBase = options.marineBase ?? MARINE_BASE;
    this.clock = options.clock ?? (() => Date.now());
    // 必须 bind(globalThis)：存成属性后直接调用会让 this 变成实例，浏览器抛 Illegal invocation
    this.fetchFn = options.fetchFn ?? fetch.bind(globalThis);
    // 与 Android `tideSpotHttpClient` 的 10s 口径一致：30s 会让首屏「加载中」长达分钟级
    this.callTimeoutMillis = options.callTimeoutMillis ?? 10_000;
    this.onLog = options.onLog ?? (() => {});
    this.retry = new RetryExecutor(options.retryDelays ?? [1_000, 4_000, 10_000]);
    this.bucket = new TokenBucket(6, 60_000, this.clock);
  }

  /** §5.1 天气整窗（返回 DTO；映射用 mapWeatherHourly） */
  async fetchWeather(lat: number, lon: number): Promise<WeatherResponseDto> {
    return this.withPermit(async () => {
      const url = weatherUrl(this.weatherBase, lat, lon);
      const body = await this.executeGet('weather', url);
      return this.decode<WeatherResponseDto>('weather', body);
    });
  }

  /** §5.2 海洋整窗（内陆点 200 + 全 null，仍算成功） */
  async fetchMarine(lat: number, lon: number): Promise<MarineResponseDto> {
    return this.withPermit(async () => {
      const url = marineUrl(this.marineBase, lat, lon);
      const body = await this.executeGet('marine', url);
      return this.decode<MarineResponseDto>('marine', body);
    });
  }

  /** 便捷方法：直接得到内部逐小时模型 */
  async fetchWeatherHourly(lat: number, lon: number) {
    return mapWeatherHourly(await this.fetchWeather(lat, lon));
  }

  async fetchMarineHourly(lat: number, lon: number) {
    return mapMarineHourly(await this.fetchMarine(lat, lon));
  }

  private decode<T>(source: string, body: string): T {
    try {
      return JSON.parse(body) as T;
    } catch (e) {
      throw new DataError('NetworkError', `${source} 响应解析失败（脏 JSON）`, { cause: e });
    }
  }

  private async executeGet(source: string, url: string): Promise<string> {
    return this.retry.execute(async () => {
      await this.bucket.acquire();
      const startedAt = this.clock();
      let response: Response;
      try {
        response = await this.request(url);
      } catch (e) {
        this.onLog({
          source,
          urlHash: hashString(url),
          httpStatus: null,
          bytes: null,
          costMs: this.clock() - startedAt,
          fetchedAt: this.clock(),
        });
        if (e instanceof DataError) throw e;
        throw new DataError('NetworkError', `网络异常: ${(e as Error).message}`, { cause: e });
      }

      const text = await response.text();
      this.onLog({
        source,
        urlHash: hashString(url),
        httpStatus: response.status,
        bytes: text.length,
        costMs: this.clock() - startedAt,
        fetchedAt: this.clock(),
      });

      if (response.status === 200) return text;
      throw classify(response.status, text);
    });
  }

  private async request(url: string): Promise<Response> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.callTimeoutMillis);
    try {
      return await this.fetchFn(url, {
        method: 'GET',
        signal: controller.signal,
        redirect: 'follow',
      });
    } finally {
      clearTimeout(timer);
    }
  }

  private withPermit<T>(fn: () => Promise<T>): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      const run = () => {
        this.active += 1;
        fn()
          .then(resolve, reject)
          .finally(() => {
            this.active -= 1;
            const next = this.waiters.shift();
            if (next) next();
          });
      };
      if (this.active < this.parallelLimit) run();
      else this.waiters.push(run);
    });
  }
}