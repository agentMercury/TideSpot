package com.tidespot.app

import com.tidespot.core.domain.model.FavoriteSpot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 导航状态机锁测试（M6 批次 3；纯函数 reducer，JVM 可测）：
 * 覆盖 CONTRACT「M6 子契约」批次 3 的导航闭环——地图确认选点→详情、收藏列表→详情、详情返回收藏。
 */
class AppNavTest {

    private fun spot(id: Long = 1L): FavoriteSpot =
        FavoriteSpot(id = id, name = "测试点", lat = 30.5, lon = 120.3)

    @Test
    fun 初始态为地图页且无详情上下文() {
        val s = AppNav.reduce(AppNavState(), AppNavEvent.TabSelected(AppPage.MAP))
        assertEquals(AppPage.MAP, s.page)
        assertNull(s.detailSpot)
    }

    @Test
    fun 地图确认选点后进详情页并携带负载() {
        val s = AppNav.reduce(AppNavState(), AppNavEvent.OpenDetail(spot()))
        assertEquals(AppPage.DETAIL, s.page)
        assertEquals(1L, s.detailSpot?.id)
    }

    @Test
    fun 收藏列表进入详情同样携带负载() {
        val s = AppNav.reduce(
            AppNavState(page = AppPage.FAVORITES),
            AppNavEvent.OpenDetail(spot(9)),
        )
        assertEquals(AppPage.DETAIL, s.page)
        assertEquals(9L, s.detailSpot?.id)
    }

    @Test
    fun 无详情上下文时点详情Tab为noOp不闪页() {
        val base = AppNavState(page = AppPage.FAVORITES)
        val s = AppNav.reduce(base, AppNavEvent.TabSelected(AppPage.DETAIL))
        assertEquals(base, s)
    }

    @Test
    fun 有详情上下文时点详情Tab可回到详情() {
        val withSpot = AppNavState(page = AppPage.SETTINGS, detailSpot = spot())
        val s = AppNav.reduce(withSpot, AppNavEvent.TabSelected(AppPage.DETAIL))
        assertEquals(AppPage.DETAIL, s.page)
        assertEquals(spot().id, s.detailSpot?.id)
    }

    @Test
    fun 详情返回收藏固定回收藏页并清负载() {
        val s = AppNav.reduce(
            AppNavState(page = AppPage.DETAIL, detailSpot = spot()),
            AppNavEvent.BackFromDetail,
        )
        assertEquals(AppPage.FAVORITES, s.page)
        assertNull(s.detailSpot)
    }

    @Test
    fun 切往地图或设置清除详情上下文保持不变量() {
        val base = AppNavState(page = AppPage.DETAIL, detailSpot = spot())
        assertEquals(AppPage.MAP, AppNav.reduce(base, AppNavEvent.TabSelected(AppPage.MAP)).page)
        assertNull(AppNav.reduce(base, AppNavEvent.TabSelected(AppPage.MAP)).detailSpot)
        assertEquals(AppPage.SETTINGS, AppNav.reduce(base, AppNavEvent.TabSelected(AppPage.SETTINGS)).page)
        assertNull(AppNav.reduce(base, AppNavEvent.TabSelected(AppPage.SETTINGS)).detailSpot)
    }

    @Test
    fun 收藏Tab不清除其他状态时往返幂等() {
        var s = AppNavState()
        s = AppNav.reduce(s, AppNavEvent.TabSelected(AppPage.FAVORITES))
        s = AppNav.reduce(s, AppNavEvent.TabSelected(AppPage.SETTINGS))
        s = AppNav.reduce(s, AppNavEvent.TabSelected(AppPage.FAVORITES))
        assertEquals(AppPage.FAVORITES, s.page)
        assertNull(s.detailSpot)
    }
}
