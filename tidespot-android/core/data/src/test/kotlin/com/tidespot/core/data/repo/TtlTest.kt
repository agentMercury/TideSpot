package com.tidespot.core.data.repo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** §7.3 TTL：天气 3h / 海洋 6h。 */
class TtlTest {

    private val t0 = 1_700_000_000_000L

    @Test
    fun `天气 3h 内新鲜_3h 整过期`() {
        assertTrue(Ttl.isFresh(DataSource.WEATHER, t0, t0 + 3 * 3600_000 - 1))
        assertFalse(Ttl.isFresh(DataSource.WEATHER, t0, t0 + 3 * 3600_000), "TTL=3h 整点即过期")
        assertFalse(Ttl.isFresh(DataSource.WEATHER, t0, t0 + 4 * 3600_000))
    }

    @Test
    fun `海洋 6h 内新鲜_6h 过期`() {
        assertTrue(Ttl.isFresh(DataSource.MARINE, t0, t0 + 5 * 3600_000))
        assertTrue(Ttl.isFresh(DataSource.MARINE, t0, t0 + 6 * 3600_000 - 1))
        assertFalse(Ttl.isFresh(DataSource.MARINE, t0, t0 + 6 * 3600_000))
    }

    @Test
    fun `无拉取记录视为不新鲜`() {
        assertFalse(Ttl.isFresh(DataSource.WEATHER, 0, t0))
    }

    @Test
    fun `过期毫秒数用于陈旧标记`() {
        val stale = Ttl.staleForMillis(DataSource.WEATHER, t0, t0 + 3 * 3600_000 + 60_000)
        assertEquals(60_000L, stale)
        assertNull(Ttl.staleForMillis(DataSource.WEATHER, t0, t0 + 3600_000), "未过期返回 null")
    }
}
