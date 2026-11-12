package com.tidespot.feature.map.crosshair

import com.tidespot.feature.map.model.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 准星状态机（纯 Kotlin）：Idle → Preview(center, movedFrom) → Confirmed(spot)。
 *
 * F-1.4 语义：
 * - Preview 期间已确认点不变、不切换任何已提交数据；
 * - 仅 [onConfirm] 提交新点；[onCancel] 回退到上一确认点（无则回 Idle）。
 *
 * 事件入口由 MapScreen 的地图监听（长按 / 相机移动）与按钮（确定 / 取消）驱动。
 */
class CrosshairStateMachine(initial: CrosshairState = CrosshairState.Idle) {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<CrosshairState> = _state.asStateFlow()

    private var lastConfirmed: GeoPoint? = (initial as? CrosshairState.Confirmed)?.spot

    /** 长按设置预览点；若此前已有确认点，movedFrom = 上一确认点，否则 = 长按点本身。 */
    fun onLongPress(point: GeoPoint) {
        val previous = (_state.value as? CrosshairState.Confirmed)?.spot ?: point
        _state.value = CrosshairState.Preview(center = point, movedFrom = previous)
    }

    /**
     * 拖动地图，准星（地图中心）跟随（CHG-09③ / NEW-05 修正；CHG-35 补 Idle）：
     * - Preview 态：更新 center（movedFrom 不变）；
     * - Confirmed 态：**也进 Preview**（movedFrom = 已确认点，保留语义——从已确认点移动），
     *   信息条走「移动中，点确定切换」；已确认数据在确认前不切换（F-1.4）；
     * - Idle 态：**用户手势拖动时进入 Preview**（CHG-35：修复「冷启动/返回地图后拖动不更新信息条」）；
     *   程序驱动 idle（样式重建、冷启动定位/返回定位动画）传 `fromUserGesture=false`，不凭空创建预览。
     */
    fun onMapMoved(point: GeoPoint, fromUserGesture: Boolean = false) {
        when (val current = _state.value) {
            is CrosshairState.Preview -> if (current.center != point) {
                _state.value = current.copy(center = point)
            }
            is CrosshairState.Confirmed -> {
                if (current.spot != point) {
                    _state.value = CrosshairState.Preview(center = point, movedFrom = current.spot)
                }
            }
            CrosshairState.Idle ->
                if (fromUserGesture) {
                    _state.value = CrosshairState.Preview(center = point, movedFrom = point)
                }
        }
    }

    /** 确认提交：仅 Preview 态生效，返回提交后的状态（非 Preview 时原样返回）。 */
    fun onConfirm(): CrosshairState {
        val current = _state.value
        if (current is CrosshairState.Preview) {
            lastConfirmed = current.center
            val next = CrosshairState.Confirmed(current.center)
            _state.value = next
            return next
        }
        return current
    }

    /** 取消预览：回上一确认点；无历史确认点则回 Idle。 */
    fun onCancel(): CrosshairState {
        val current = _state.value
        if (current is CrosshairState.Preview) {
            val next = lastConfirmed?.let { CrosshairState.Confirmed(it) } ?: CrosshairState.Idle
            _state.value = next
            return next
        }
        return current
    }
}

/** 准星状态（纯数据）。 */
sealed interface CrosshairState {
    data object Idle : CrosshairState

    /** movedFrom：进入 Preview 前的准星来源点（上一确认点或长按点）。 */
    data class Preview(val center: GeoPoint, val movedFrom: GeoPoint) : CrosshairState
    data class Confirmed(val spot: GeoPoint) : CrosshairState
}