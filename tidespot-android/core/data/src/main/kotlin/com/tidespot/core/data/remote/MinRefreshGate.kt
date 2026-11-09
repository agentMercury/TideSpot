package com.tidespot.core.data.remote

/**
 * 单点最小刷新间隔节流（SRS §12：令牌桶 6 req/min + 单点最小刷新 15 min）。
 *
 * 与 TTL（§7.3）互补：TTL 决定缓存是否有效，本节流保证同一 spot 两次真实网络刷新至少间隔 15 min，
 * 防止 TTL 刚过期的点被高频点击时打爆频控。
 *
 * FO-17（M4 必修 3）三修：
 * - **线程安全**：HashMap → synchronized 复合操作，「校验+占用」原子化（[tryBegin]）；
 * - **仅成功后记录**：[finishSuccess] 才写入 15min 窗口；[finishFailure] 只释放在途占用，
 *   失败不占窗（可立即重试）——旧实现在网络请求前就 record，失败也占 15min；
 * - **并发去重**：同一 key 存在在途刷新时 [tryBegin] 拒绝 → 同点并发刷新无重复请求。
 *
 * 时间戳显式传入（纯逻辑，可确定性单测）。进程级内存态（重启清零）与 FO-17 原文一致。
 */
class MinRefreshGate(
    private val minIntervalMillis: Long = 15 * 60_000L,
) {

    private val lock = Any()
    private val lastSuccess = HashMap<String, Long>()
    private val inFlight = HashSet<String>()

    /**
     * 原子「校验 + 占用」：距上次成功刷新已满 [minIntervalMillis] 且无在途刷新时，
     * 占用该 key 并返回 true；在途刷新存在或间隔不足 → false（不占用）。
     * 占用后必须以 [finishSuccess] / [finishFailure] 收尾，否则该点永久阻塞。
     */
    fun tryBegin(key: String, atMillis: Long): Boolean = synchronized(lock) {
        if (key in inFlight) return false
        val prev = lastSuccess[key]
        if (prev != null && atMillis - prev < minIntervalMillis) return false
        inFlight.add(key)
        true
    }

    /** 记录一次成功刷新时刻（进入 15min 窗口）并释放在途占用。 */
    fun finishSuccess(key: String, atMillis: Long) {
        synchronized(lock) {
            lastSuccess[key] = atMillis
            inFlight.remove(key)
        }
    }

    /** 失败收尾：只释放在途占用，不进入 15min 窗口（失败可立即重试）。 */
    fun finishFailure(key: String) {
        synchronized(lock) { inFlight.remove(key) }
    }
}
