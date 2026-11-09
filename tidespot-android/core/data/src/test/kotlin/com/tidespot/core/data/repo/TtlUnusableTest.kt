package com.tidespot.core.data.repo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** CHG-13/N15：§6.7 第 4 行「>48h 视为不可用（不再据此评分）」三态判定。 */
class TtlUnusableTest {

    private val fetch: Long = 1_762_387_200_000L
    private val hour: Long = 3_600_000L

    @Test
    fun fresh_returnsNull() { // TTL 内（<3h）
        assertNull(Ttl.unusableAgeMillis(fetch, fetch + hour))
    }

    @Test
    fun staleWithin48h_stillUsable_returnsNull() { // 过期可看（黄条）但 <48h：不触发不可用
        assertNull(Ttl.unusableAgeMillis(fetch, fetch + 47 * hour))
        // 恰好 48h（边界含等于）→ 不可用
        assertEquals(48 * hour, Ttl.unusableAgeMillis(fetch, fetch + 48 * hour))
    }

    @Test
    fun staleBeyond48h_unusable() { // >48h → 不可用，返回缓存年龄
        assertEquals(49 * hour, Ttl.unusableAgeMillis(fetch, fetch + 49 * hour))
        assertEquals(30L * 24 * hour, Ttl.unusableAgeMillis(fetch, fetch + 30L * 24 * hour))
    }

    @Test
    fun noFetchRecord_returnsNull() { // 无拉取记录（=0）不判不可用（走「无数据」语义）
        assertNull(Ttl.unusableAgeMillis(0L, fetch + 100L * hour))
    }
}
