package com.tidespot.core.ui.format

import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.strings.ZhStrings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * F-2.5 / OPT-V11-06（v1.1 P1）相对时间格式化纯函数。
 *
 * 口径（N-9 / F-3.8：一律按**选点当地时区**判定，「今天/明天」的边界用 zone 墙钟）：
 * - 同日 → `今天 HH:mm`
 * - 次日 → `明天 HH:mm`
 * - 2~6 天 → `周X HH:mm`
 * - 更远（含过去日期）→ `M月d日 HH:mm`
 *
 * 全部输入为 epoch 秒 + 注入的 [nowEpochSec]，无系统时钟依赖（JVM 锁可确定性断言）。
 * 时区未回写（tzOffsetSec == null）时调用方仍以 SpotZones 给出的 ZoneId 传入，
 * 并沿用既有「本机时区」口径注明，不在本函数内另造分支。
 *
 * OPT-V11-13（N-8）：星期标签与日期模式串由 [TideSpotStrings] 注入
 * （默认 [ZhStrings] 零行为漂移；Android 侧传 `ResStrings`，英文模式 `MMM d, HH:mm`）。
 */
object RelativeTimeFormatter {

    private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

    /** 星期标签（周一…周日）；`dayOfWeek.value` 1..7 与 ISO 口径一致。 */
    fun weekdayLabel(date: LocalDate, strings: TideSpotStrings = ZhStrings): String =
        when (date.dayOfWeek.value) {
            1 -> strings.weekdayMon
            2 -> strings.weekdayTue
            3 -> strings.weekdayWed
            4 -> strings.weekdayThu
            5 -> strings.weekdayFri
            6 -> strings.weekdaySat
            else -> strings.weekdaySun
        }

    /** epoch 秒 → 相对时间文案（当地时区语义）。 */
    fun format(
        epochSec: Long,
        zone: ZoneId,
        nowEpochSec: Long,
        strings: TideSpotStrings = ZhStrings,
        locale: Locale = Locale.getDefault(),
    ): String {
        val target = Instant.ofEpochSecond(epochSec).atZone(zone)
        val today = Instant.ofEpochSecond(nowEpochSec).atZone(zone).toLocalDate()
        val dayDiff = ChronoUnit.DAYS.between(today, target.toLocalDate())
        val clock = HH_MM.format(target)
        return when (dayDiff) {
            0L -> strings.today(clock)
            1L -> strings.tomorrow(clock)
            in 2L..6L -> "${weekdayLabel(target.toLocalDate(), strings)} $clock"
            else -> DateTimeFormatter.ofPattern(strings.timePattern, locale).format(target)
        }
    }
}