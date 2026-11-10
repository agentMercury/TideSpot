package com.tidespot.feature.detail.detail

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * CHG-27（P0-2）锁测试：OPT-V11-03 时段筛选纯函数 + OPT-V11-25/26 显示格式化。
 *
 * 口径：
 * - ALL → null（不筛选，与全窗恒等）；NEXT_24H → [now, now+24h]；
 * - 本周末 = 当地最近一个周六 00:00 → 周日 24:00（N-9 墙钟语义）；
 * - 自选校验：日期 `yyyy-MM-dd`、时间 `HH:mm`、止 > 起（同日）；
 * - 爆护徽标阈值 ≥80（与 ScoreBand.EXPLOSIVE 同源）；持续标注 null = 不显示。
 */
class Top10TimeFilterTest {

    private val shanghai: ZoneId = ZoneOffset.ofHours(8)
    // 2026-11-10 是周二（CST）：本周六 = 11-14
    private val tuesdayNoon = LocalDateTime.of(2026, 11, 10, 12, 0).toEpochSecond(ZoneOffset.ofHours(8))

    @Test
    fun `ALL_不筛选返回null`() {
        assertNull(Top10TimeFilter.presetRange(Top10TimeFilter.Preset.ALL, tuesdayNoon, shanghai))
    }

    @Test
    fun `接下来24h_区间为now到now加24h`() {
        val range = Top10TimeFilter.presetRange(Top10TimeFilter.Preset.NEXT_24H, tuesdayNoon, shanghai)!!
        assertEquals(tuesdayNoon, range.startEpochSec)
        assertEquals(tuesdayNoon + 24 * 3600L, range.endEpochSec)
    }

    @Test
    fun `本周末_当地最近周六零点到周日24点`() {
        val range = Top10TimeFilter.weekendRange(tuesdayNoon, shanghai)
        val saturdayStart = LocalDateTime.of(2026, 11, 14, 0, 0).toEpochSecond(ZoneOffset.ofHours(8))
        val sundayEnd = LocalDateTime.of(2026, 11, 16, 0, 0).toEpochSecond(ZoneOffset.ofHours(8))
        assertEquals(saturdayStart, range.startEpochSec, "周二视角：本周六 00:00")
        assertEquals(sundayEnd, range.endEpochSec, "周日 24:00（周一 00:00）")
    }

    @Test
    fun `本周末_周日内仍取当前这个周末`() {
        // 2026-11-15 周日 10:00 → 仍应指向 11-14..11-16
        val sunday = LocalDateTime.of(2026, 11, 15, 10, 0).toEpochSecond(ZoneOffset.ofHours(8))
        val range = Top10TimeFilter.weekendRange(sunday, shanghai)
        assertEquals(LocalDateTime.of(2026, 11, 14, 0, 0).toEpochSecond(ZoneOffset.ofHours(8)), range.startEpochSec)
        assertEquals(LocalDateTime.of(2026, 11, 16, 0, 0).toEpochSecond(ZoneOffset.ofHours(8)), range.endEpochSec)
    }

    @Test
    fun `自选_合法输入解析为当地时段`() {
        val parsed = Top10TimeFilter.parseCustom("2026-11-14", "14:00", "20:00", shanghai)
        val range = assertNotNull(parsed.range)
        assertNull(parsed.error)
        assertEquals(LocalDateTime.of(2026, 11, 14, 14, 0).toEpochSecond(ZoneOffset.ofHours(8)), range.startEpochSec)
        assertEquals(LocalDateTime.of(2026, 11, 14, 20, 0).toEpochSecond(ZoneOffset.ofHours(8)), range.endEpochSec)
    }

    @Test
    fun `自选_错误码分类`() {
        assertEquals(
            Top10TimeFilter.CustomError.BAD_DATE,
            Top10TimeFilter.parseCustom("11/14", "14:00", "20:00", shanghai).error,
        )
        assertEquals(
            Top10TimeFilter.CustomError.BAD_TIME,
            Top10TimeFilter.parseCustom("2026-11-14", "2pm", "20:00", shanghai).error,
        )
        assertEquals(
            Top10TimeFilter.CustomError.END_NOT_AFTER_START,
            Top10TimeFilter.parseCustom("2026-11-14", "20:00", "14:00", shanghai).error,
        )
        assertEquals(
            Top10TimeFilter.CustomError.END_NOT_AFTER_START,
            Top10TimeFilter.parseCustom("2026-11-14", "14:00", "14:00", shanghai).error,
        )
    }

    @Test
    fun `爆护徽标_80边界与持续标注_null语义`() {
        val w80 = com.tidespot.core.domain.model.BestWindow(
            startEpochSec = 0, peakEpochSec = 0, durationHours = 3, peakScore = 80, tier = "爆护窗口",
            topFactors = emptyList(), warnings = emptyList(), truncatedUntilEpochSec = 7200,
        )
        assertEquals("爆护", Top10Formatter.burstBadge(w80))
        assertEquals("利好持续至 02:00", Top10Formatter.sustainedLine(w80, ZoneOffset.UTC))

        val w79 = w80.copy(peakScore = 79, tier = "很适合", truncatedUntilEpochSec = null)
        assertNull(Top10Formatter.burstBadge(w79), "79 分不得显示爆护")
        assertNull(Top10Formatter.sustainedLine(w79, ZoneOffset.UTC), "核心即原窗 → 不显示持续标注")
    }
}