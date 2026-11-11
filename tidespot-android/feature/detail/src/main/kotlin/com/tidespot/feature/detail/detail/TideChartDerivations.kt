package com.tidespot.feature.detail.detail

import com.tidespot.core.data.repo.HourSlice
import com.tidespot.core.data.repo.TideCorrectionSummary
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.ui.format.Format
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.strings.ZhStrings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * D12 潮 Tab 副轴/高低潮/校正对照纯派生层（全纯 Kotlin，JVM 可测；CHG-16⑭）。
 *
 * - dt = dh 一阶差分/时间间隔（[Derivations.tideRates] 已有 m/h 差分，本层只做当日序列对齐）；
 * - 高低潮 = 序列局部极值：复用 [TopWindowDerivations.tideRatesFromMarine] 的差分，
 *   在符号翻转共享点（+→− 高潮、−→+ 低潮）判定——不重实现差分（D01 单源教训）；
 * - 校正对照：[TideCorrectionSummary] 非 null 时，「校正后」极值 = 「校正前」极值整体平移 lag
 *   （M'(h)=M(h−lag) ⇒ h=t0+lag 处现峰值），纯 lag 平移派生、不改数值来源。
 */
object TideChartDerivations {

    enum class ExtremeType { HIGH, LOW }

    data class TideExtreme(val type: ExtremeType, val epochSec: Long)

    /** 当日 dt 序列（与 daySlices 逐点对齐；缺测/首小时 → null，禁补 0）。 */
    fun dtSeries(daySlices: List<HourSlice>, tideRates: Map<Long, Double?>): List<Double?> =
        daySlices.map { tideRates[it.timeEpochSec] }

    /**
     * 高低潮分类极值（§6.2b）：差分符号翻转点，翻转仅判在间隔恰 3600s 的共享小时上
     * （缺测断开不判定、恒 0 差分平潮不产生极值）。
     */
    fun classifiedExtremes(marine: List<HourlyMarine>): List<TideExtreme> {
        val rates = TopWindowDerivations.tideRatesFromMarine(marine)
        val times = rates.keys.sorted()
        val out = mutableListOf<TideExtreme>()
        for (i in 1 until times.size) {
            val t0 = times[i - 1]
            val t1 = times[i]
            if (t1 - t0 != 3600L) continue
            val r0 = rates.getValue(t0)
            val r1 = rates.getValue(t1)
            when {
                r0 > 0 && r1 < 0 -> out += TideExtreme(ExtremeType.HIGH, t0)
                r0 < 0 && r1 > 0 -> out += TideExtreme(ExtremeType.LOW, t0)
            }
        }
        return out
    }

    /** 按当地日过滤极值（与 Derivations.slicesForDay 同一当地 LocalDate 口径）。 */
    fun dayExtremes(
        extremes: List<TideExtreme>,
        dayIndex: Int,
        zone: ZoneId,
        today: LocalDate,
    ): List<TideExtreme> {
        val target = today.plusDays(dayIndex.toLong())
        return extremes
            .filter { Instant.ofEpochSecond(it.epochSec).atZone(zone).toLocalDate() == target }
            .sortedBy { it.epochSec }
    }

    /** 校正后极值 = 校正前极值整体平移 lagHours（lag=0 → 原样返回；lag>0 极值延后）。 */
    fun shiftByLag(extremes: List<TideExtreme>, lagHours: Int): List<TideExtreme> =
        if (lagHours == 0) extremes
        else extremes.map { it.copy(epochSec = it.epochSec + lagHours * 3600L) }

    /**
     * 「校正前/后」对照行（tideCorrection 非 null 且当日有极值才产对照；null = 不渲染）。
     * 行文案：「校正前：高潮 a / 低潮 b」「校正后（滞后 Xh）：…」。
     */
    data class CorrectionCompare(val lagHours: Int, val adopted: Boolean, val beforeLine: String, val afterLine: String)

    fun correctionCompare(
        beforeDayExtremes: List<TideExtreme>,
        correction: TideCorrectionSummary,
        zone: ZoneId,
        strings: TideSpotStrings = ZhStrings,
    ): CorrectionCompare? {
        if (beforeDayExtremes.isEmpty()) return null
        val before = extremesLine(beforeDayExtremes, zone, strings) ?: return null
        val after = extremesLine(shiftByLag(beforeDayExtremes, correction.lagHours), zone, strings) ?: return null
        return CorrectionCompare(
            lagHours = correction.lagHours,
            adopted = correction.adopted,
            beforeLine = strings.tideBefore(before),
            afterLine = strings.tideAfter(correction.lagHours, after),
        )
    }

    /**
     * 极值 → 文案行：「高潮 03:00 / 15:00　低潮 09:00 / 21:00」；
     * 无极值 → null（不渲染；时刻用选点当地墙钟，Format.hourLocal 单源）。
     */
    fun extremesLine(
        extremes: List<TideExtreme>,
        zone: ZoneId,
        strings: TideSpotStrings = ZhStrings,
    ): String? {
        if (extremes.isEmpty()) return null
        val highs = extremes.filter { it.type == ExtremeType.HIGH }.map { Format.hourLocal(it.epochSec, zone) }
        val lows = extremes.filter { it.type == ExtremeType.LOW }.map { Format.hourLocal(it.epochSec, zone) }
        val parts = buildList {
            if (highs.isNotEmpty()) add(strings.tideHigh(highs.joinToString(" / ")))
            if (lows.isNotEmpty()) add(strings.tideLow(lows.joinToString(" / ")))
        }
        return if (parts.isEmpty()) null else parts.joinToString("　")
    }
}
