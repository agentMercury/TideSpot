/**
 * 与 Android `core/data/remote/MinRefreshGate.kt` 对齐。
 * 同点两次真实网络刷新至少间隔 15min；仅成功后才写入窗口；失败立即释放。
 * JS 单线程，方法本身即原子；这里仍用「检查+占用」同步完成。
 */
export class MinRefreshGate {
  private readonly lastSuccess = new Map<string, number>();
  private readonly inFlight = new Set<string>();

  constructor(private readonly minIntervalMillis: number = 15 * 60_000) {}

  tryBegin(key: string, atMillis: number): boolean {
    if (this.inFlight.has(key)) return false;
    const prev = this.lastSuccess.get(key);
    if (prev != null && atMillis - prev < this.minIntervalMillis) return false;
    this.inFlight.add(key);
    return true;
  }

  finishSuccess(key: string, atMillis: number): void {
    this.lastSuccess.set(key, atMillis);
    this.inFlight.delete(key);
  }

  finishFailure(key: string): void {
    this.inFlight.delete(key);
  }

  /** 测试/调试用：当前在途 key 数 */
  inFlightCount(): number {
    return this.inFlight.size;
  }
}