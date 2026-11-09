package com.tidespot.feature.map.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 定位权限 UI 状态（纯 Kotlin）。 */
sealed interface PermissionUiState {
    data object NotDetermined : PermissionUiState
    data object Requesting : PermissionUiState
    data object Granted : PermissionUiState

    /** permanent=true：「不再询问」级拒绝 → 仅手动选点模式，永不再次弹窗（F-1.1 无死循环）。 */
    data class Denied(val permanent: Boolean) : PermissionUiState
}

/**
 * 权限状态机（纯 Kotlin，无 Android 依赖）。
 *
 * 死循环防御：仅 NotDetermined / Denied(soft) 允许进入 Requesting；
 * Denied(permanent) 下 [onRequestStarted] 恒返回 false 且状态不变。
 */
class PermissionStateMachine(initial: PermissionUiState = PermissionUiState.NotDetermined) {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<PermissionUiState> = _state.asStateFlow()

    /** 当前是否允许发起系统权限弹窗。 */
    val shouldRequest: Boolean
        get() = when (val s = _state.value) {
            is PermissionUiState.NotDetermined -> true
            is PermissionUiState.Denied -> !s.permanent
            else -> false
        }

    /**
     * CHG-09⑦（NEW-07/GAP-M3-04）：冷启动先查系统权限——已授权则直接置 Granted
     * （不再重复弹窗），未授权才走 [onRequestStarted] 请求。幂等：任意态均可安全调用。
     */
    fun onAlreadyGranted(): PermissionUiState {
        if (_state.value != PermissionUiState.Granted) _state.value = PermissionUiState.Granted
        return _state.value
    }

    /** 进入 Requesting；不允许时返回 false 且状态不变。 */
    fun onRequestStarted(): Boolean {
        if (!shouldRequest) return false
        _state.value = PermissionUiState.Requesting
        return true
    }

    /**
     * 系统弹窗回执；仅 Requesting 态受理。
     * @param granted 是否任一位置权限被授予
     * @param shouldShowRationale Android 侧 shouldShowRequestPermissionRationale 结果：
     *   false 且未授权 → 判为「不再询问」级永久拒绝
     */
    fun onPermissionResult(granted: Boolean, shouldShowRationale: Boolean): PermissionUiState {
        if (_state.value != PermissionUiState.Requesting) return _state.value
        _state.value = if (granted) {
            PermissionUiState.Granted
        } else {
            PermissionUiState.Denied(permanent = !shouldShowRationale)
        }
        return _state.value
    }
}