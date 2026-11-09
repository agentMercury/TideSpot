package com.tidespot.core.data.remote

import com.tidespot.core.data.DataError
import kotlinx.coroutines.delay

/**
 * 退避重试执行器（SRS F-4.5：**1s / 4s / 10s 退避 3 次**，区分 429 / 5xx / 网络错误）。
 *
 * - [DataError.isRetryable]（429 / 5xx / 网络错误）→ 按退避序列重试；
 * - [DataError.SourceParamError]（B9）与 [BadRequest] → 立即抛出，不重试；
 * - 退避延迟通过可注入 [delayFn] 执行：生产为 kotlinx `delay`（测试在 runTest 下为虚拟时间，
 *   可精确断言 1s/4s/10s 时序）。
 *
 * 「重试期保留旧数据」由仓储层保证：重试失败抛出后，仓储回退读取缓存（F-2.3 / F-3.6）。
 */
class RetryExecutor(
    private val delaysMillis: List<Long> = listOf(1_000L, 4_000L, 10_000L),
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
) {

    /**
     * 执行 [block]：失败且可重试时按退避序列重试，最多 [delaysMillis.size] 次。
     * 退避序列耗尽仍失败 → 抛出最后一次的原始错误。
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: DataError) {
                if (!e.isRetryable || attempt >= delaysMillis.size) throw e
                delayFn(delaysMillis[attempt])
                attempt++
            }
        }
    }
}
