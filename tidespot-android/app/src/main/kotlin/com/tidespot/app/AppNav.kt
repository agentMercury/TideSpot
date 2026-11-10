package com.tidespot.app

import com.tidespot.core.domain.model.FavoriteSpot

/**
 * 导航状态机（M6 批次 3 + v1.1 UX-21；无 navigation 库）：四页 地图/收藏/详情/设置，
 * reducer 为纯函数（JVM 锁测试覆盖 [AppNavTest]），[MainActivity] 只做薄组装。
 *
 * 约定（CONTRACT「M6 子契约」批次 3 + OPT-V11-21）：
 * - **无底部 dock**（UX-20）：页面切换由地图浮动按钮 / 列表项 / 系统返回驱动；
 * - DETAIL 不是常驻页——仅当 [AppNavState.detailSpot] 非空时可达；
 * - 切往 MAP/FAVORITES/SETTINGS 时清除 detailSpot 与 [AppNavState.sourcePage]；
 * - [AppNavEvent.OpenDetail] 携带**来源页**（[AppNavEvent.OpenDetail.source]，默认收藏），
 *   [AppNavEvent.BackFromDetail] 回来源页（从地图进 → 回地图；从收藏进 → 回收藏）。
 */
enum class AppPage { MAP, FAVORITES, DETAIL, SETTINGS }

/** 导航状态：当前页 + 详情页负载（null = 无详情上下文）+ 详情来源页（仅 DETAIL 期间非空）。 */
data class AppNavState(
    val page: AppPage = AppPage.MAP,
    val detailSpot: FavoriteSpot? = null,
    val sourcePage: AppPage? = null,
)

/** 导航事件（UI 侧唯一入口；禁止直接改 [AppNavState]）。 */
sealed interface AppNavEvent {
    data class TabSelected(val page: AppPage) : AppNavEvent

    /**
     * 进入详情页：
     * - 地图确认选点收藏成功后（来源 = MAP）；
     * - 收藏列表点开（来源 = FAVORITES，调用方须先 markViewed，F-2.4 最近查看排序）。
     */
    data class OpenDetail(
        val spot: FavoriteSpot,
        val source: AppPage = AppPage.FAVORITES,
    ) : AppNavEvent

    /** 详情页返回：回**来源页**（UX-21；无来源记录时回落收藏页，兼容旧调用）。 */
    data object BackFromDetail : AppNavEvent
}

object AppNav {

    /**
     * 纯函数 reducer（无副作用、无时钟依赖，可 JVM 锁测试）。
     * 不变量：
     * 1. `page == DETAIL ⇔ detailSpot != null`；
     * 2. `sourcePage != null ⇒ page == DETAIL`；
     * 3. TabSelected(MAP/FAVORITES/SETTINGS) 清 detailSpot + sourcePage；
     * 4. OpenDetail 总是覆盖 detailSpot 并切 DETAIL，记录来源页；
     * 5. BackFromDetail 回来源页（默认 FAVORITES），清详情上下文。
     */
    fun reduce(state: AppNavState, event: AppNavEvent): AppNavState = when (event) {
        is AppNavEvent.TabSelected -> when (event.page) {
            AppPage.DETAIL ->
                if (state.detailSpot != null) state.copy(page = AppPage.DETAIL) else state
            AppPage.MAP -> state.copy(page = AppPage.MAP, detailSpot = null, sourcePage = null)
            AppPage.FAVORITES -> state.copy(page = AppPage.FAVORITES, detailSpot = null, sourcePage = null)
            AppPage.SETTINGS -> state.copy(page = AppPage.SETTINGS, detailSpot = null, sourcePage = null)
        }
        is AppNavEvent.OpenDetail ->
            state.copy(page = AppPage.DETAIL, detailSpot = event.spot, sourcePage = event.source)
        AppNavEvent.BackFromDetail -> {
            val back = state.sourcePage?.takeIf { it != AppPage.DETAIL } ?: AppPage.FAVORITES
            state.copy(page = back, detailSpot = null, sourcePage = null)
        }
    }
}