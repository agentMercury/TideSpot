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

    // ── UX-21（P1）：返回手势来源页 ──

    @Test
    fun 从地图进详情_返回回地图() {
        val detail = AppNav.reduce(
            AppNavState(page = AppPage.MAP),
            AppNavEvent.OpenDetail(spot(), source = AppPage.MAP),
        )
        assertEquals(AppPage.MAP, detail.sourcePage)
        val back = AppNav.reduce(detail, AppNavEvent.BackFromDetail)
        assertEquals(AppPage.MAP, back.page)
        assertNull(back.detailSpot)
        assertNull(back.sourcePage)
    }

    @Test
    fun 从收藏进详情_返回回收藏() {
        val detail = AppNav.reduce(
            AppNavState(page = AppPage.FAVORITES),
            AppNavEvent.OpenDetail(spot(), source = AppPage.FAVORITES),
        )
        assertEquals(AppPage.FAVORITES, detail.sourcePage)
        assertEquals(AppPage.FAVORITES, AppNav.reduce(detail, AppNavEvent.BackFromDetail).page)
    }

    @Test
    fun 切往其他页清除来源页_保持不变量() {
        val detail = AppNav.reduce(AppNavState(), AppNavEvent.OpenDetail(spot(), source = AppPage.MAP))
        val settings = AppNav.reduce(detail, AppNavEvent.TabSelected(AppPage.SETTINGS))
        assertNull(settings.sourcePage)
        assertNull(settings.detailSpot)
        assertEquals(AppPage.SETTINGS, settings.page)
    }

    @Test
    fun 默认来源页为收藏_返回回收藏() {
        // OpenDetail 的 source 默认 FAVORITES（旧调用零改动语义）
        val detail = AppNav.reduce(AppNavState(), AppNavEvent.OpenDetail(spot()))
        assertEquals(AppPage.FAVORITES, detail.sourcePage)
        assertEquals(AppPage.FAVORITES, AppNav.reduce(detail, AppNavEvent.BackFromDetail).page)
    }

    @Test
    fun 无来源页状态的详情返回仍回落收藏页_兼容旧状态() {
        // 旧状态对象可能无 sourcePage → 返回回落收藏
        val legacy = AppNavState(page = AppPage.DETAIL, detailSpot = spot(), sourcePage = null)
        assertEquals(AppPage.FAVORITES, AppNav.reduce(legacy, AppNavEvent.BackFromDetail).page)
    }
}
