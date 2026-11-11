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
 *
 * OPT-V12-03（CHG-57）：新增**优先级通道**。`USER`（用户显式操作：下拉刷新 / 重试）的等待者
 * 在令牌可用时优先于 `BACKGROUND`（后台预取）；`BACKGROUND` 在存在 `USER` 等待者时**主动让出**
 * （本轮不抢令牌，下一补充周期再评估），`tryAcquire()` 同样在有 `USER` 等待者时直接返回 false。
 * 总速率不变（capacity/period），仅改变**分配顺序**——§12 频控上限不违反。
 */
class TokenBucket(
    private val capacity: Int = 6,
    private val periodMillis: Long = 60_000L,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
) {

    /** 等待者优先级（OPT-V12-03）：用户操作 > 后台预取。 */
    enum class Priority { USER, BACKGROUND }

    private val mutex = Mutex()
    private var tokens = capacity.toDouble()
    private var lastRefill = clock()

    /** 当前在 `acquire(USER)` 中等待的调用数（>0 = 后台让出）。 */
    private var userWaiters = 0

    /** 单令牌补充间隔（后台让出时的再评估周期；避免"令牌可用但让出"变成忙等）。 */
    private val tokenIntervalMillis: Long = (periodMillis / capacity).coerceAtLeast(1L)

    /** 立即尝试取一个令牌；无可用令牌 **或存在用户操作等待者** 时返回 false。 */
    suspend fun tryAcquire(): Boolean = mutex.withLock {
        refill()
        if (userWaiters > 0) return@withLock false
        if (tokens >= 1.0) {
            tokens -= 1.0
            true
        } else {
            false
        }
    }

    /**
     * 挂起直至取到令牌（按速率计算等待并 [delay]）。
     *
     * @param priority [Priority.USER] 优先分配；[Priority.BACKGROUND] 在用户等待期间让出。
     */
    suspend fun acquire(priority: Priority = Priority.BACKGROUND) {
        if (priority == Priority.USER) mutex.withLock { userWaiters += 1 }
        try {
            while (true) {
                val waitMs = mutex.withLock {
                    refill()
                    val yieldedToUser = priority == Priority.BACKGROUND && userWaiters > 0
                    when {
                        tokens >= 1.0 && !yieldedToUser -> {
                            tokens -= 1.0
                            0L
                        }
                        // 令牌虽可用但用户操作在排队：让出本轮，待下一补充周期再取
                        tokens >= 1.0 -> tokenIntervalMillis
                        else -> (((1.0 - tokens) * periodMillis) / capacity).toLong().coerceAtLeast(1L)
                    }
                }
                if (waitMs == 0L) return
                delay(waitMs)
            }
        } finally {
            if (priority == Priority.USER) mutex.withLock { userWaiters -= 1 }
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