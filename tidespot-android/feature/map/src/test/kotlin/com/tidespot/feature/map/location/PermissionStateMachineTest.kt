package com.tidespot.feature.map.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionStateMachineTest {

    @Test
    fun firstGrant_granted() {
        val m = PermissionStateMachine()
        assertTrue(m.onRequestStarted())
        assertEquals(PermissionUiState.Granted, m.onPermissionResult(granted = true, shouldShowRationale = false))
    }

    @Test
    fun temporaryDeny_allowsRetry() {
        val m = PermissionStateMachine()
        m.onRequestStarted()
        val s = m.onPermissionResult(granted = false, shouldShowRationale = true)
        assertEquals(PermissionUiState.Denied(permanent = false), s)
        assertTrue(m.onRequestStarted()) // 软拒绝可再次请求
        assertEquals(PermissionUiState.Requesting, m.state.value)
    }

    @Test
    fun permanentDeny_goesManualModeAndNeverReasks() { // F-1.1 无死循环
        val m = PermissionStateMachine()
        m.onRequestStarted()
        val s = m.onPermissionResult(granted = false, shouldShowRationale = false)
        assertEquals(PermissionUiState.Denied(permanent = true), s)
        repeat(20) {
            assertFalse(m.onRequestStarted())
            assertEquals(PermissionUiState.Denied(permanent = true), m.state.value)
        }
    }

    @Test
    fun resultWithoutRequest_isIgnored() {
        val m = PermissionStateMachine()
        assertEquals(PermissionUiState.NotDetermined, m.onPermissionResult(true, false))
        assertTrue(m.onRequestStarted())
        m.onPermissionResult(true, false)
        m.onPermissionResult(false, false) // Granted 后再喂结果应被忽略
        assertEquals(PermissionUiState.Granted, m.state.value)
    }
}

// ─────────────── CHG-09⑦（NEW-07/GAP-M3-04）：冷启动已授权直连 ───────────────

class PermissionColdStartTest {

    @Test
    fun onAlreadyGranted_setsGrantedFromNotDetermined() {
        val m = PermissionStateMachine()
        assertEquals(PermissionUiState.Granted, m.onAlreadyGranted())
        // 已授权后不再发起系统弹窗（NEW-07：已授权直接可用，不重复请求）
        assertEquals(false, m.onRequestStarted())
    }

    @Test
    fun onAlreadyGranted_overridesSoftDenied() {
        val m = PermissionStateMachine(PermissionUiState.Denied(permanent = false))
        assertEquals(PermissionUiState.Granted, m.onAlreadyGranted())
    }

    @Test
    fun onAlreadyGranted_isIdempotent() {
        val m = PermissionStateMachine()
        m.onAlreadyGranted()
        assertEquals(PermissionUiState.Granted, m.onAlreadyGranted())
    }

    @Test
    fun coldStartNotGranted_stillRequestsViaExistingPath() {
        val m = PermissionStateMachine()
        // 未授权路径不变：onRequestStarted() → Requesting → 弹窗
        assertEquals(true, m.onRequestStarted())
        assertEquals(PermissionUiState.Requesting, m.state.value)
    }
}