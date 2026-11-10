package com.tidespot.core.ui

import com.tidespot.core.ui.format.RelativeTimeFormatter
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OPT-V11-06（P1）RelativeTimeFormatter 纯函数锁：四个格式分支全覆盖 + 选点当地时区语义
 * （GWT：大阪点当地周六 23:30 的窗口显示「明天 07:00」而非设备时区）。
 */
class RelativeTimeFormatterTest {

    private val jst: ZoneId = ZoneOffset.ofHours(9)
    private val utc8: ZoneId = ZoneOffset.ofHours(8)

    private fun epochJst(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).toInstant(ZoneOffset.ofHours(9)).epochSecond

    @Test
    fun sameDay_isTodayWithClock() {
        val now = epochJst(2026, 11, 10, 8)
        assertEquals("今天 17:00", RelativeTimeFormatter.format(epochJst(2026, 11, 10, 17), jst, now))
    }

    @Test
    fun nextDay_isTomorrowWithClock() {
        // GWT：JST 周六 23:30 时查看次日 07:00 窗口 → 「明天 07:00」
        val now = epochJst(2026, 11, 7, 23, 30)
        assertEquals("明天 07:00", RelativeTimeFormatter.format(epochJst(2026, 11, 8, 7), jst, now))
    }

    @Test
    fun withinSixDays_isWeekdayWithClock() {
        val now = epochJst(2026, 11, 6, 12)
        // 2026-11-09 为周一（2~6 天区间）
        assertEquals("周一 06:30", RelativeTimeFormatter.format(epochJst(2026, 11, 9, 6, 30), jst, now))
        // 第 6 天（11-12 周四）仍属周 X 分支
        assertEquals("周四 06:30", RelativeTimeFormatter.format(epochJst(2026, 11, 12, 6, 30), jst, now))
    }

    @Test
    fun beyondSixDays_isDateWithClock() {
        val now = epochJst(2026, 11, 6, 12)
        // 第 7 天（11-13）→ 日期格式
        assertEquals("11月13日 06:30", RelativeTimeFormatter.format(epochJst(2026, 11, 13, 6, 30), jst, now))
        // 过去日期同样回落日期格式（不虚造「昨天」）
        assertEquals("11月5日 06:30", RelativeTimeFormatter.format(epochJst(2026, 11, 5, 6, 30), jst, now))
    }

    @Test
    fun zoneSemantics_followSpotZone_notDevice() {
        // 同一 epoch：在 UTC+9 是「明天」，在 UTC+8 仍是「今天」（23:30 边界）
        val now = epochJst(2026, 11, 7, 23, 30)
        val target = epochJst(2026, 11, 8, 7)
        assertEquals("明天 07:00", RelativeTimeFormatter.format(target, jst, now))
        // UTC+8 下 now=11-07 22:30、target=11-08 06:00 → 仍为明天（跨日一致）
        assertEquals("明天 06:00", RelativeTimeFormatter.format(target, utc8, now))
        // 反例：换 UTC（now=11-07 14:30 / target=11-07 22:00）→ 同日「今天」，证明按注入时区判定
        assertEquals("今天 22:00", RelativeTimeFormatter.format(target, ZoneOffset.UTC, now))
    }
}