package com.tidespot.core.data.remote

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 令牌桶 6 req/min（SRS §12 Open-Meteo 频控红线）——用测试虚拟时钟精确断言补充速率。 */
class TokenBucketTest {

    @Test
    fun `满桶可连取 6 次，第 7 次被拒`() = runTest {
        val bucket = TokenBucket(capacity = 6, periodMillis = 60_000, clock = { testScheduler.currentTime })
        repeat(6) { assertTrue(bucket.tryAcquire(), "第 ${it + 1} 次应成功") }
        assertFalse(bucket.tryAcquire(), "6 req/min 用尽后应拒绝")
    }

    @Test
    fun `按 0_1 token 每秒恒定速率补充`() = runTest {
        val bucket = TokenBucket(capacity = 6, periodMillis = 60_000, clock = { testScheduler.currentTime })
        repeat(6) { bucket.tryAcquire() }
        assertFalse(bucket.tryAcquire())

        // 10s → 补 1 个令牌
        advanceTimeBy(10_000); runCurrent()
        assertTrue(bucket.tryAcquire())
        assertFalse(bucket.tryAcquire())

        // 再 60s → 距起点累计 70s，未消费的补充量 = (70s-10s)*0.1 = 6 个（第 10s 已取走 1 个）
        advanceTimeBy(60_000); runCurrent()
        repeat(6) { assertTrue(bucket.tryAcquire()) }
        assertFalse(bucket.tryAcquire())
    }

    @Test
    fun `acquire 挂起至令牌恢复`() = runTest {
        val bucket = TokenBucket(capacity = 6, periodMillis = 60_000, clock = { testScheduler.currentTime })
        repeat(6) { bucket.tryAcquire() }
        val job = launch { bucket.acquire() }
        advanceTimeBy(10_000); runCurrent()
        job.join()
        assertTrue(testScheduler.currentTime >= 10_000, "应等待约 10s 补足 1 个令牌")
    }
}
