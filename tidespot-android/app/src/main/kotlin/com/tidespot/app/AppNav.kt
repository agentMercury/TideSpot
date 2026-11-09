package com.tidespot.app

import com.tidespot.core.domain.model.FavoriteSpot

/**
 * 导航状态机（M6 批次 3；无 navigation 库）：四页 地图/收藏/详情/设置，
 * reducer 为纯函数（JVM 锁测试覆盖 [AppNavTest]），[MainActivity] 只做薄组装。
 *
 * 约定（CONTRACT「M6 子契约」批次 3）：
 * - 底部导航四项；DETAIL 不是常驻 tab——仅当 [AppNavState.detailSpot] 非空时可达，
 *   否则 TabSelected(DETAIL) 为 no-op（不闪页）；
 * - 切往 MAP/FAVORITES/SETTINGS 时清除 detailSpot（详情上下文随之销毁）；
 * - [AppNavEvent.BackFromDetail] 固定回收藏页（导航闭环：详情返回收藏）。
 */
enum class AppPage { MAP, FAVORITES, DETAIL, SETTINGS }

/** 导航状态：当前页 + 详情页负载（null = 无详情上下文）。 */
data class AppNavState(
    val page: AppPage = AppPage.MAP,
    val detailSpot: FavoriteSpot? = null,
)

/** 导航事件（UI 侧唯一入口；禁止直接改 [AppNavState]）。 */
sealed interface AppNavEvent {
    data class TabSelected(val page: AppPage) : AppNavEvent

    /**
     * 进入详情页：
     * - 地图确认选点收藏成功后（OPT-M5-07 主路径）；
     * - 收藏列表点开（调用方须先 markViewed，F-2.4 最近查看排序）。
     */
    data class OpenDetail(val spot: FavoriteSpot) : AppNavEvent

    /** 详情页返回（固定回收藏页）。 */
    data object BackFromDetail : AppNavEvent
}

object AppNav {

    /**
     * 纯函数 reducer（无副作用、无时钟依赖，可 JVM 锁测试）。
     * 不变量：
     * 1. `page == DETAIL ⇔ detailSpot != null`（除 TabSelected(DETAIL) 无负载被拒绝外恒成立）；
     * 2. TabSelected(MAP/FAVORITES/SETTINGS) 清 detailSpot；
     * 3. OpenDetail 总是覆盖 detailSpot 并切 DETAIL。
     */
    fun reduce(state: AppNavState, event: AppNavEvent): AppNavState = when (event) {
        is AppNavEvent.TabSelected -> when (event.page) {
            AppPage.DETAIL ->
                if (state.detailSpot != null) state.copy(page = AppPage.DETAIL) else state
            AppPage.MAP -> state.copy(page = AppPage.MAP, detailSpot = null)
            AppPage.FAVORITES -> state.copy(page = AppPage.FAVORITES, detailSpot = null)
            AppPage.SETTINGS -> state.copy(page = AppPage.SETTINGS, detailSpot = null)
        }
        is AppNavEvent.OpenDetail -> state.copy(page = AppPage.DETAIL, detailSpot = event.spot)
        AppNavEvent.BackFromDetail -> state.copy(page = AppPage.FAVORITES, detailSpot = null)
    }
}
