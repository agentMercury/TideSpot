package com.tidespot.core.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
 */
object RelativeTimeFormatter {

    private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")
    private val M_D_HM = DateTimeFormatter.ofPattern("M月d日 HH:mm")

    /** 星期标签（周一…周日）；`dayOfWeek.value` 1..7 与 ISO 口径一致。 */
    fun weekdayLabel(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }

    /** epoch 秒 → 相对时间文案（当地时区语义）。 */
    fun format(epochSec: Long, zone: ZoneId, nowEpochSec: Long): String {
        val target = Instant.ofEpochSecond(epochSec).atZone(zone)
        val today = Instant.ofEpochSecond(nowEpochSec).atZone(zone).toLocalDate()
        val dayDiff = ChronoUnit.DAYS.between(today, target.toLocalDate())
        val clock = HH_MM.format(target)
        return when (dayDiff) {
            0L -> "今天 $clock"
            1L -> "明天 $clock"
            in 2L..6L -> "${weekdayLabel(target.toLocalDate())} $clock"
            else -> M_D_HM.format(target)
        }
    }
}