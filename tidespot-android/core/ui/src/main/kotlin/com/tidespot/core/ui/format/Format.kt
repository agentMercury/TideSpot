package com.tidespot.core.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 显示层格式化（F-3.3：显示值 = 源值仅做单位换算与格式化，禁止平滑/取整到 0；
 * §6.7：缺值显示 `—`，禁止把 null 补成 0）。
 *
 * 单位换算仅在 UI 层（F-4.4）；本文件只做「格式化」，换算由调用方完成。
 */
object Format {

    /** §6.7 缺失值占位符（原文）。 */
    const val MISSING = "—"

    private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")
    private val MM_DD = DateTimeFormatter.ofPattern("MM-dd")
    private val M_D = DateTimeFormatter.ofPattern("M/d")

    /**
     * 数值格式化（F-3.3 验收口径：0.42 → "0.4"；null → "—"）。
     * [digits] 位小数，HALF_UP；非 null 值即使格式化后为 "0.0" 也按原样显示（源值就是它，
     * 不是无解释吞 0；合法 0 值显示 "0.0" 属于源值保真）。
     */
    fun value(v: Double?, digits: Int = 1): String {
        if (v == null) return MISSING
        val scale = digits.coerceIn(0, 3)
        return String.format(Locale.ROOT, "%.${scale}f", v)
    }

    /** 数值 + 单位（"0.4 m"；null → "—"，不带单位——缺值不显示假单位）。 */
    fun valueUnit(v: Double?, unit: String, digits: Int = 1): String {
        if (v == null) return MISSING
        return "${value(v, digits)} $unit"
    }

    /** 角度（"148°"；null → "—"）。 */
    fun deg(v: Double?): String = if (v == null) MISSING else "${v.roundToInt0()}°"

    /** 选点当地墙钟 HH:mm（F-3.8：小时轴用选点当地墙上时间）。 */
    fun hourLocal(epochSec: Long, zone: ZoneId): String =
        HH_MM.format(Instant.ofEpochSecond(epochSec).atZone(zone))

    /** 本机拉取时刻 HH:mm（F-3.7；epoch 毫秒 ≤0 = 无记录 → "—"，禁回填 1970）。 */
    fun clockMillis(epochMillis: Long, zone: ZoneId): String =
        if (epochMillis <= 0) MISSING
        else HH_MM.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** 日期 MM-dd（DaySelector 副标签）。 */
    fun mmdd(date: LocalDate): String = MM_DD.format(date)

    /** 紧凑日期 M/d（OPT-V11-24 DaySelector 主标签：「11/12 周三」，无前导零）。 */
    fun md(date: LocalDate): String = M_D.format(date)

    private fun Double.roundToInt0(): Int {
        val r = kotlin.math.round(this)
        return if (r.isNaN()) 0 else r.toInt()
    }
}
