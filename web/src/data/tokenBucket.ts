/**
 * 与 Android `core/data/remote/TokenBucket.kt` 对齐（SRS §12：6 req/min）。
 * 用户操作优先于后台预取；后台在用户等待时主动让出一个补充周期。
 * 时钟与 delay 均可注入，便于确定性单测。
 */
export type Priority = 'USER' | 'BACKGROUND';

export type ClockFn = () => number;

export class TokenBucket {
  private tokens: number;
  private lastRefill: number;
  private userWaiters = 0;
  private tail: Promise<unknown> = Promise.resolve();
  private readonly tokenIntervalMillis: number;

  constructor(
    private readonly capacity: number = 6,
    private readonly periodMillis: number = 60_000,
    private readonly clock: ClockFn = () => Date.now(),
    private readonly delayFn: (ms: number) => Promise<void> = (ms) =>
      new Promise<void>((resolve) => setTimeout(resolve, ms)),
  ) {
    this.tokens = capacity;
    this.lastRefill = clock();
    this.tokenIntervalMillis = Math.max(1, Math.trunc(periodMillis / capacity));
  }

  private withLock<T>(fn: () => T): Promise<T> {
    const run = this.tail.then(fn, fn);
    this.tail = run.then(
      () => undefined,
      () => undefined,
    );
    return run;
  }

  async tryAcquire(): Promise<boolean> {
    return this.withLock(() => {
      this.refill();
      if (this.userWaiters > 0) return false;
      if (this.tokens >= 1) {
        this.tokens -= 1;
        return true;
      }
      return false;
    });
  }

  async acquire(priority: Priority = 'BACKGROUND'): Promise<void> {
    if (priority === 'USER') {
      await this.withLock(() => {
        this.userWaiters += 1;
      });
    }
    try {
      for (;;) {
        const waitMs = await this.withLock(() => {
          this.refill();
          const yieldedToUser = priority === 'BACKGROUND' && this.userWaiters > 0;
          if (this.tokens >= 1 && !yieldedToUser) {
            this.tokens -= 1;
            return 0;
          }
          if (this.tokens >= 1) return this.tokenIntervalMillis;
          return Math.max(1, Math.trunc(((1 - this.tokens) * this.periodMillis) / this.capacity));
        });
        if (waitMs === 0) return;
        await this.delayFn(waitMs);
      }
    } finally {
      if (priority === 'USER') {
        await this.withLock(() => {
          this.userWaiters -= 1;
        });
      }
    }
  }

  private refill(): void {
    const now = this.clock();
    const elapsed = Math.max(0, now - this.lastRefill);
    if (elapsed > 0) {
      this.tokens = Math.min(this.capacity, this.tokens + (elapsed * this.capacity) / this.periodMillis);
      this.lastRefill = now;
    }
  }

  /** 测试用：当前可用令牌数（会先补桶） */
  available(): number {
    this.refill();
    return this.tokens;
  }
}