package com.tidespot.feature.detail

import com.tidespot.feature.detail.detail.UserRefreshGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OPT-V12-03（CHG-57）锁测试：同点下拉去重——1s 内两次下拉只产生一个任务（令牌只扣 1）。
 */
class UserRefreshGateTest {

    @Test
    fun `进行中再次下拉复用任务，任务结束后放行`() = runTest {
        val gate = UserRefreshGate()
        var runs = 0

        val first = gate.start(this) { delay(1_000); runs++ }
        val second = gate.start(this) { runs++ }

        assertTrue(first, "首次刷新应真正启动")
        assertFalse(second, "进行中再次下拉应复用任务（去重）")
        assertTrue(gate.isRunning, "进行中应暴露进度态")

        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, runs, "同一时刻只允许一个刷新任务执行")
        assertFalse(gate.isRunning)

        assertTrue(gate.start(this) { runs++ }, "任务结束后应放行下一次刷新")
        runCurrent()
        assertEquals(2, runs)
    }
}