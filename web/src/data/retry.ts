/**
 * 与 Android `core/data/remote/RetryExecutor.kt` 对齐：1s / 4s / 10s 三次退避。
 * 仅 429 / 5xx / 网络错误重试；参数错误与其余 4xx 立即抛出。
 */
import { DataError } from './errors';

export type DelayFn = (ms: number) => Promise<void>;

export const defaultDelay: DelayFn = (ms) => new Promise<void>((resolve) => setTimeout(resolve, ms));

export class RetryExecutor {
  constructor(
    private readonly delaysMillis: ReadonlyArray<number> = [1_000, 4_000, 10_000],
    private readonly delayFn: DelayFn = defaultDelay,
  ) {}

  async execute<T>(block: () => Promise<T>): Promise<T> {
    let attempt = 0;
    for (;;) {
      try {
        return await block();
      } catch (e) {
        if (!(e instanceof DataError) || !e.isRetryable || attempt >= this.delaysMillis.length) {
          throw e;
        }
        await this.delayFn(this.delaysMillis[attempt]);
        attempt += 1;
      }
    }
  }
}