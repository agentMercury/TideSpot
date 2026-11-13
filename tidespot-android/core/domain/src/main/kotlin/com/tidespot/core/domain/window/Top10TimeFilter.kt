package com.tidespot.core.domain.window

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Top10 时段筛选纯函数（OPT-V11-03 / CHG-27；JVM 单测锁）。
 *
 * 口径（N-9 / SRS §6.6）：
 * - 预设「接下来 24h」「本周末」与「自选」均产出 [TimeRange]（epoch 秒，**当地墙上时间**语义）；
 * - 时段只做**输出过滤**（[BestWindowFinder.find] 的 windowStart/End 参数，原窗相交判定），
 *   不改候选/rank/去重公式；ALL = null（零行为漂移）。
 */
object Top10TimeFilter {

    /** 半开区间 [startEpochSec, endEpochSec]（epoch 秒）。 */
    data class TimeRange(val startEpochSec: Long, val endEpochSec: Long)

    /** 预设筛选。 */
    enum class Preset { ALL, NEXT_24H, THIS_WEEKEND }

    /** 自选输入错误码（UI 映射资源文案）。 */
    enum class CustomError { BAD_DATE, BAD_TIME, END_NOT_AFTER_START }

    /** 自选解析结果（[range] 与 [error] 二选一）。 */
    data class CustomParse(val range: TimeRange?, val error: CustomError?)

    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    /** 预设 → 时段；[Preset.ALL] → null（不筛选）。 */
    fun presetRange(preset: Preset, nowEpochSec: Long, zone: ZoneId): TimeRange? = when (preset) {
        Preset.ALL -> null
        Preset.NEXT_24H -> TimeRange(nowEpochSec, nowEpochSec + 24 * 3600L)
        Preset.THIS_WEEKEND -> weekendRange(nowEpochSec, zone)
    }

    /**
     * 本周末（当地时区）：最近一个周六 00:00 → 周日 24:00；
     * 若当前已在周六/周日，则取**当前**这个周末（周六已过则从今天算起仍属本周末）。
     */
    fun weekendRange(nowEpochSec: Long, zone: ZoneId): TimeRange {
        val today: LocalDate = Instant.ofEpochSecond(nowEpochSec).atZone(zone).toLocalDate()
        // 周日 → 当前周末从昨天（周六）开始；其余 → 前进到最近的周六（周六当天 = 今天）
        val saturday = if (today.dayOfWeek == DayOfWeek.SUNDAY) {
            today.minusDays(1)
        } else {
            today.plusDays(((DayOfWeek.SATURDAY.value - today.dayOfWeek.value + 7) % 7).toLong())
        }
        val start = saturday.atStartOfDay(zone).toEpochSecond()
        val end = saturday.plusDays(2).atStartOfDay(zone).toEpochSecond()
        return TimeRange(start, end)
    }

    /**
     * 自选解析：日期 `yyyy-MM-dd` + 起/止 `HH:mm`（同日，止 > 起）。
     *
     * @return [CustomParse.range] 非空 = 合法；否则 [CustomParse.error] 给出错误码
     */
    fun parseCustom(dateText: String, startHm: String, endHm: String, zone: ZoneId): CustomParse {
        val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
            ?: return CustomParse(null, CustomError.BAD_DATE)
        val start = runCatching { LocalTime.parse(startHm.trim(), HM) }.getOrNull()
            ?: return CustomParse(null, CustomError.BAD_TIME)
        val end = runCatching { LocalTime.parse(endHm.trim(), HM) }.getOrNull()
            ?: return CustomParse(null, CustomError.BAD_TIME)
        if (!end.isAfter(start)) return CustomParse(null, CustomError.END_NOT_AFTER_START)
        val startEpoch = date.atTime(start).atZone(zone).toEpochSecond()
        val endEpoch = date.atTime(end).atZone(zone).toEpochSecond()
        return CustomParse(TimeRange(startEpoch, endEpoch), null)
    }

    /**
     * 窗口与筛选时段是否相交（跨点 Top10 的**输出过滤**口径）。
     *
     * 与 detail 的差异：detail 把 [TimeRange] 传入 [BestWindowFinder.find] 重算候选；
     * 跨点 Top10 的窗口已算好（各点 topWindows），只能后过滤，故用半开区间相交判定。
     * [range] 为 null（ALL）时恒 true，零行为漂移。
     */
    fun intersects(range: TimeRange?, windowStartSec: Long, windowEndSec: Long): Boolean =
        range == null || (windowStartSec < range.endEpochSec && windowEndSec > range.startEpochSec)
}
