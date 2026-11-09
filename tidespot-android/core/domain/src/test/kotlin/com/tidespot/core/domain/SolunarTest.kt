package com.tidespot.core.domain

import com.tidespot.core.domain.algorithm.Solunar
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B2 月龄黄金值（SRS 附录 B2，容差 ±0.6 天；§10.1-2 SolunarTest）。
 * 10-20→9.1 | 10-27→16.1 | 11-03→23.6 | 11-09→0.4（新月）| 11-16→6.6 | 11-23→13.6
 */
class SolunarTest {

    /** 与参照实现一致的日期解析：本地墙钟日期按 +08:00 转 UTC 时刻。 */
    private fun ageOf(date: String): Double {
        val millis = LocalDateTime.parse("${date}T12:00").toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
        return Solunar.lunarAge(millis)
    }

    @Test
    fun `B2 月龄黄金值 6 组 容差 0_6 天`() {
        val golden = mapOf(
            "2026-10-20" to 9.1,
            "2026-10-27" to 16.1,
            "2026-11-03" to 23.6,
            "2026-11-09" to 0.4,     // 新月
            "2026-11-16" to 6.6,
            "2026-11-23" to 13.6,
        )
        for ((d, expected) in golden) {
            assertEquals(expected, ageOf(d), 0.6, "月龄 $d")
        }
    }

    @Test
    fun `2026-11-09 为新月`() {
        val age = ageOf("2026-11-09")
        assertTrue(age < 1.0 || age > Solunar.SYNODIC_MONTH_DAYS - 1.0, "11-09 应为新月（age=$age）")
    }

    @Test
    fun `照明度从新月到满月单调上升`() {
        var prev = -1.0
        for (day in 0..15) {
            val illum = Solunar.illumination(day.toDouble())
            assertTrue(illum >= prev, "illum 在月龄 $day 天应不下降: $illum < $prev")
            prev = illum
        }
        assertEquals(0.0, Solunar.illumination(0.0), 1e-9)
        assertEquals(1.0, Solunar.illumination(Solunar.SYNODIC_MONTH_DAYS / 2), 1e-9)
    }

    @Test
    fun `月上中天 青岛黄金值 11-02 为 5_5h 附近`() {
        // 附录 B3：青岛 2026-11-02 06:00 行 中天 5.5h（黄金值按该行月龄 21.8 计算）
        val millis = LocalDateTime.parse("2026-11-02T06:00").toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
        val age = Solunar.lunarAge(millis)
        val transit = Solunar.lunarTransitClock(age, 120.38, 8.0)
        assertEquals(5.5, transit, 0.2, "青岛 11-02 月上中天")
    }

    @Test
    fun `Solunar 子分处于 0-1 且在中天附近高`() {
        val near = Solunar.score(6.0, 5.5, 21.8)   // 中天附近
        val far = Solunar.score(12.0, 5.5, 21.8)   // 半周期对面（次窗）
        assertTrue(near in 0.0..1.0 && far in 0.0..1.0)
        assertTrue(near >= far, "中天附近 Solunar 应不低于半周期对面：near=$near far=$far")
    }
}
