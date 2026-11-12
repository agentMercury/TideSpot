package com.tidespot.feature.map.crosshair

import com.tidespot.feature.map.model.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrosshairStateMachineTest {

    private val a = GeoPoint(29.6, 119.038)
    private val b = GeoPoint(29.61, 119.05)

    @Test
    fun idle_ignoresConfirmCancel_andProgrammaticMove() {
        val m = CrosshairStateMachine()
        assertEquals(CrosshairState.Idle, m.onConfirm())
        assertEquals(CrosshairState.Idle, m.onCancel())
        // 程序驱动 idle（样式重建/定位动画收尾）默认不创建预览
        m.onMapMoved(b)
        assertEquals(CrosshairState.Idle, m.state.value)
    }

    // ── CHG-35：Idle + 用户手势拖动 → Preview（修复信息条不更新） ──

    @Test
    fun idle_userGestureMove_entersPreviewAtCenter() {
        val m = CrosshairStateMachine()
        m.onMapMoved(b, fromUserGesture = true)
        val s = m.state.value
        assertTrue(s is CrosshairState.Preview)
        assertEquals(b, s.center)
        assertEquals(b, s.movedFrom)
        // 继续拖动 → 只更新 center，movedFrom 保持首个拖动点
        m.onMapMoved(a, fromUserGesture = true)
        val s2 = m.state.value as CrosshairState.Preview
        assertEquals(a, s2.center)
        assertEquals(b, s2.movedFrom)
    }

    @Test
    fun longPress_entersPreviewAtPoint() {
        val m = CrosshairStateMachine()
        m.onLongPress(a)
        val s = m.state.value
        assertTrue(s is CrosshairState.Preview)
        assertEquals(a, s.center)
        assertEquals(a, s.movedFrom) // 无历史确认点时 movedFrom = 长按点
    }

    @Test
    fun preview_mapMoved_updatesCenterKeepsMovedFrom() {
        val m = CrosshairStateMachine()
        m.onLongPress(a)
        m.onMapMoved(b)
        val s = m.state.value as CrosshairState.Preview
        assertEquals(b, s.center)
        assertEquals(a, s.movedFrom)
    }

    @Test
    fun confirm_commitsPreviewCenter() {
        val m = CrosshairStateMachine()
        m.onLongPress(a)
        m.onMapMoved(b)
        val committed = m.onConfirm()
        assertEquals(CrosshairState.Confirmed(b), committed)
        assertEquals(CrosshairState.Confirmed(b), m.state.value)
    }

    @Test
    fun confirmed_moved_entersPreview_thenConfirmSwitchesData() { // F-1.4 + CHG-09③（NEW-05）
        val m = CrosshairStateMachine()
        m.onLongPress(a)
        m.onConfirm()
        // Confirmed 态拖动 → 进 Preview（movedFrom = 已确认点，信息条走「移动中，点确定切换」）
        m.onMapMoved(b)
        val s = m.state.value as CrosshairState.Preview
        assertEquals(b, s.center)
        assertEquals(a, s.movedFrom)
        // Preview 期间已确认数据不切换（确认前 state 非 Confirmed，消费方不提交）
        // confirm 后数据切换到新点
        assertEquals(CrosshairState.Confirmed(b), m.onConfirm())
        assertEquals(CrosshairState.Confirmed(b), m.state.value)
    }

    @Test
    fun confirmed_movedSameSpot_staysConfirmed() { // CHG-09③ 边界：idle 回送同点不抖动
        val m = CrosshairStateMachine()
        m.onLongPress(a)
        m.onConfirm()
        m.onMapMoved(a) // 相机 idle 回到已确认点（如长按飞行结束）→ 保持 Confirmed
        assertEquals(CrosshairState.Confirmed(a), m.state.value)
    }

    @Test
    fun longPressFromConfirmed_movedFromIsPreviousSpot() {
        val m = CrosshairStateMachine()
        m.onLongPress(a)
        m.onConfirm()
        m.onLongPress(b)
        val s = m.state.value as CrosshairState.Preview
        assertEquals(b, s.center)
        assertEquals(a, s.movedFrom)
    }

    @Test
    fun cancelFromPreview_restoresPreviousConfirmed() {
        val m = CrosshairStateMachine()
        m.onLongPress(a)
        m.onConfirm()
        m.onLongPress(b)
        assertEquals(CrosshairState.Confirmed(a), m.onCancel())
    }

    @Test
    fun cancelFromPreviewWithoutHistory_goesIdle() {
        val m = CrosshairStateMachine()
        m.onLongPress(a)
        assertEquals(CrosshairState.Idle, m.onCancel())
    }

    @Test
    fun afterCancel_confirmIsNoOp() {
        val m = CrosshairStateMachine()
        m.onLongPress(a)
        m.onConfirm()
        m.onLongPress(b)
        m.onCancel()
        assertEquals(CrosshairState.Confirmed(a), m.onConfirm())
    }
}