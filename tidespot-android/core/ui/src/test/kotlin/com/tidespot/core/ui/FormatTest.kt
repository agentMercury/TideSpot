package com.tidespot.core.ui

import com.tidespot.core.ui.format.Format
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/** F-3.3 格式化（源值仅格式化，禁止平滑/吞 0）+ §6.7 缺值 `—`。 */
class FormatTest {

    @Test
    fun value_042shows04_notZero() {
        assertEquals("0.4", Format.value(0.42))          // F-3.3 验收原文
        assertEquals("0.4 m", Format.valueUnit(0.42, "m"))
        assertEquals("—", Format.value(null))            // §6.7：缺值 —，禁补 0
        assertEquals("—", Format.valueUnit(null, "m"))   // 缺值不带假单位
        assertEquals("0.0 m", Format.valueUnit(0.04, "m")) // 源值保真（非无解释吞 0：表格同行的周期/方向仍显示）
        assertEquals("0.0 m", Format.valueUnit(0.0, "m"))  // 合法源值 0
    }

    @Test
    fun deg_missingShowsDash() {
        assertEquals("148°", Format.deg(148.4))
        assertEquals("—", Format.deg(null))
    }

    @Test
    fun hourLocal_usesSpotZone() {
        // JST 大阪点：UTC 06:00 = 当地 15:00
        val epoch = LocalDateTime.of(2026, 11, 6, 15, 0)
            .toInstant(ZoneOffset.ofHours(9)).epochSecond
        assertEquals("15:00", Format.hourLocal(epoch, ZoneOffset.ofHours(9)))
    }

    @Test
    fun clockMillis_neverShows1970() {
        assertEquals("—", Format.clockMillis(0L, ZoneOffset.UTC))        // 无记录 → —（FO-17 禁回填 1970）
        assertEquals("—", Format.clockMillis(-1L, ZoneOffset.UTC))
        val ts = LocalDateTime.of(2026, 11, 6, 8, 5).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("08:05", Format.clockMillis(ts, ZoneOffset.UTC))
    }

    @Test
    fun mmdd_fixedFormat() {
        assertEquals("11-06", Format.mmdd(LocalDate.of(2026, 11, 6)))
    }
}
