package com.tidespot.core.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

/**
 * 令牌桶限流（SRS §12 Open-Meteo 红线：免费档 <1 万次/日 → **6 req/min**）。
 *
 * - `tryAcquire()`：立即可取则取走一个令牌，否则返回 false（不等待）；
 * - `acquire()`：挂起直至令牌恢复（等待时长按恒定补充速率计算）。
 *
 * 时钟注入为 `() -> Long`（毫秒）：生产传系统时钟，测试可绑虚拟时钟精确断言补充行为。
 */
class TokenBucket(
    private val capacity: Int = 6,
    private val periodMillis: Long = 60_000L,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {

    private val mutex = Mutex()
    private var tokens = capacity.toDouble()
    private var lastRefill = clock()

    /** 立即尝试取一个令牌；无可用令牌返回 false。 */
    suspend fun tryAcquire(): Boolean = mutex.withLock {
        refill()
        if (tokens >= 1.0) {
            tokens -= 1.0
            true
        } else {
            false
        }
    }

    /** 挂起直至取到令牌（按速率计算等待并 [delay]）。 */
    suspend fun acquire() {
        while (true) {
            val waitMs = mutex.withLock {
                refill()
                if (tokens >= 1.0) {
                    tokens -= 1.0
                    0L
                } else {
                    // 距离凑足 1 个令牌还需多久：恒定速率 capacity/period
                    (((1.0 - tokens) * periodMillis) / capacity).toLong().coerceAtLeast(1L)
                }
            }
            if (waitMs == 0L) return
            delay(waitMs)
        }
    }

    /** 按经过时间补充令牌（持锁调用）。 */
    private fun refill() {
        val now = clock()
        val elapsed = (now - lastRefill).coerceAtLeast(0L)
        if (elapsed > 0L) {
            tokens = min(capacity.toDouble(), tokens + elapsed.toDouble() * capacity / periodMillis)
            lastRefill = now
        }
    }
}
