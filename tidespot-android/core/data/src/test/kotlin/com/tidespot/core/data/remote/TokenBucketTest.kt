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

    // ────────────── OPT-V12-03（CHG-57）：优先级通道（用户操作 > 后台预取） ──────────────

    @Test
    fun `令牌恢复后优先分配给用户操作，后台预取让出`() = runTest {
        val bucket = TokenBucket(capacity = 6, periodMillis = 60_000, clock = { testScheduler.currentTime })
        repeat(6) { bucket.tryAcquire() }

        var backgroundDone = false
        var userDone = false
        val background = launch {
            bucket.acquire(TokenBucket.Priority.BACKGROUND)
            backgroundDone = true
        }
        runCurrent()
        val user = launch {
            bucket.acquire(TokenBucket.Priority.USER)
            userDone = true
        }
        runCurrent()

        advanceTimeBy(10_000); runCurrent()
        assertTrue(userDone, "令牌恢复后必须先分配给用户操作")
        assertFalse(backgroundDone, "后台预取应让出，不得抢占用户令牌")

        advanceTimeBy(10_000); runCurrent()
        assertTrue(backgroundDone, "用户取走后，下一补充周期归还后台")
        background.join(); user.join()
    }

    @Test
    fun `存在用户等待者时 tryAcquire 直接让出`() = runTest {
        var clockOffset = 0L
        val bucket = TokenBucket(
            capacity = 1,
            periodMillis = 60_000,
            clock = { testScheduler.currentTime + clockOffset },
        )
        assertTrue(bucket.tryAcquire())
        val user = launch { bucket.acquire(TokenBucket.Priority.USER) }
        runCurrent()

        // 时钟推进一个周期（令牌已补充），但用户等待者仍在排队 → 后台 tryAcquire 必须让出
        clockOffset = 60_000
        assertFalse(bucket.tryAcquire(), "用户操作在排队时 tryAcquire 不得抢令牌")

        advanceTimeBy(60_000); runCurrent(); user.join()
        assertFalse(bucket.tryAcquire(), "该令牌已被用户操作消耗")
    }

    @Test
    fun `用户优先不改变总速率（仍按 6 req min 排队）`() = runTest {
        val bucket = TokenBucket(capacity = 6, periodMillis = 60_000, clock = { testScheduler.currentTime })
        repeat(6) { bucket.tryAcquire() }
        val jobs = (1..4).map { launch { bucket.acquire(TokenBucket.Priority.USER) } }
        runCurrent()

        advanceTimeBy(39_999); runCurrent()
        assertEquals(3, jobs.count { it.isCompleted }, "40s 内只能补 3 个整令牌")
        advanceTimeBy(1); runCurrent()
        assertEquals(4, jobs.count { it.isCompleted }, "第 10/20/30/40s 各补 1 个，总速率不变")
        jobs.forEach { it.join() }
    }
}
