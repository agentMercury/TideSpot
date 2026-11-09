package com.tidespot.core.data.remote

import com.tidespot.core.data.DataError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** F-4.5：1s/4s/10s 三次退避 + 错误分类重试策略。 */
class RetryExecutorTest {

    @Test
    fun `三次退避 1s_4s_10s 后成功`() = runTest {
        val delays = mutableListOf<Long>()
        val executor = RetryExecutor(delayFn = { delays += it })
        var attempts = 0
        val result = executor.execute {
            if (attempts++ < 3) throw DataError.ServerError(500, "boom")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf(1000L, 4000L, 10000L), delays, "退避序列必须是 1s/4s/10s")
        assertEquals(4, attempts, "首次 + 3 次重试")
    }

    @Test
    fun `耗尽重试后抛出最后一次原始错误`() = runTest {
        val delays = mutableListOf<Long>()
        val executor = RetryExecutor(delayFn = { delays += it })
        var attempts = 0
        val e = assertFailsWith<DataError.ServerError> {
            executor.execute {
                attempts++
                throw DataError.ServerError(503, "down")
            }
        }
        assertEquals(503, e.httpStatus)
        assertEquals(4, attempts)
        assertEquals(listOf(1000L, 4000L, 10000L), delays)
    }

    @Test
    fun `429 可重试`() {
        assertTrue(DataError.RateLimited().isRetryable)
    }

    @Test
    fun `B9 参数错误不可重试_立即抛出`() = runTest {
        val delays = mutableListOf<Long>()
        val executor = RetryExecutor(delayFn = { delays += it })
        var attempts = 0
        val e = assertFailsWith<DataError.SourceParamError> {
            executor.execute {
                attempts++
                throw DataError.SourceParamError(400, "Cannot initialize MultiDomains")
            }
        }
        assertEquals("Cannot initialize MultiDomains", e.reason)
        assertEquals(1, attempts, "B9 参数拼写错误不得重试（打爆频控）")
        assertTrue(delays.isEmpty())
    }
}
