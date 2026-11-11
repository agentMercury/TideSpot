package com.tidespot.feature.detail.detail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * OPT-V12-03（CHG-57）：详情页手动刷新的「同点去重」单任务门。
 *
 * 同一 spot 已有刷新任务在执行时，再次下拉**复用进行中的任务**（不重复发请求、不叠加令牌消耗），
 * UI 仅显示「进行中」；任务结束（成功/失败/取消）后自动放行下一次刷新。
 */
internal class UserRefreshGate {

    private var job: Job? = null

    /** 是否有刷新任务进行中（UI 进度态真源）。 */
    val isRunning: Boolean get() = job?.isActive == true

    /**
     * 启动刷新；已有任务进行中则空转。
     * @return true = 本次真正启动；false = 复用了进行中任务（去重）。
     */
    fun start(scope: CoroutineScope, block: suspend () -> Unit): Boolean {
        if (job?.isActive == true) return false
        job = scope.launch { block() }
        return true
    }
}