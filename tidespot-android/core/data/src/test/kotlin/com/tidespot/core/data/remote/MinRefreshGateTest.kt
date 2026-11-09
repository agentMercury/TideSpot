package com.tidespot.core.data.remote

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * §12 单点最小刷新 15 min 节流。
 * FO-17（M4 必修 3）：tryBegin 原子「校验+占用」+ 仅成功后 finishSuccess 记录 + 并发去重。
 */
class MinRefreshGateTest {

    @Test
    fun `未刷新过允许刷新`() {
        val gate = MinRefreshGate()
        assertTrue(gate.tryBegin("spot:1", atMillis = 1_000_000L))
    }

    @Test
    fun `成功后15分钟内拒绝_到点放行`() {
        val gate = MinRefreshGate()
        val t0 = 1_000_000_000L
        gate.finishSuccess("spot:1", t0)

        assertFalse(gate.tryBegin("spot:1", t0 + 1), "刚成功即拒绝")
        assertFalse(gate.tryBegin("spot:1", t0 + 14 * 60_000 - 1), "14:59 仍拒绝")
        assertTrue(gate.tryBegin("spot:1", t0 + 15 * 60_000), "15:00 整放行")
        gate.finishSuccess("spot:1", t0 + 15 * 60_000)
        assertTrue(gate.tryBegin("spot:1", t0 + 30 * 60_000), "成功后释放在途占用，下一窗可再次放行")
    }

    @Test
    fun `不同点位互不影响`() {
        val gate = MinRefreshGate()
        gate.finishSuccess("spot:1", 1_000_000L)
        assertTrue(gate.tryBegin("spot:2", 1_000_001L))
    }

    @Test
    fun `在途刷新占用_并发tryBegin拒绝_释放后放行`() {
        val gate = MinRefreshGate()
        assertTrue(gate.tryBegin("spot:1", 1_000L))
        assertFalse(gate.tryBegin("spot:1", 1_001L), "在途占用 → 并发刷新被拒（同点无重复请求）")
        gate.finishFailure("spot:1")
        assertTrue(gate.tryBegin("spot:1", 1_002L), "失败释放 → 可立即重试")
    }

    @Test
    fun `失败不进入15min窗口_成功才记录`() {
        val gate = MinRefreshGate()
        assertTrue(gate.tryBegin("spot:1", 1_000L))
        gate.finishFailure("spot:1")
        assertTrue(gate.tryBegin("spot:1", 1_001L), "失败不 record → 1ms 后即可重试")
        gate.finishSuccess("spot:1", 1_001L)
        assertFalse(gate.tryBegin("spot:1", 1_002L), "成功才进入 15min 窗口")
    }

    @Test
    fun `并发线程安全_多线程竞争同一key只放行一个`() {
        val gate = MinRefreshGate()
        var winners = 0
        val lock = Any()
        val threads = (0 until 16).map {
            Thread {
                if (gate.tryBegin("spot:1", 1_000L)) {
                    synchronized(lock) { winners++ }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertTrue(winners == 1, "并发竞争同一 key：16 线程仅 1 个 tryBegin 获胜，实际 $winners")
    }
}
