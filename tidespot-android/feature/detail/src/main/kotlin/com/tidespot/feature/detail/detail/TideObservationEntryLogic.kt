package com.tidespot.feature.detail.detail

import com.tidespot.core.domain.tide.TideObservationType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * OPT-V11-07（P1）现场观测录入纯函数层（时刻补记 + 模型对照）。
 *
 * - 时刻默认当前，可改（补记）；接受范围 = 当前 ±[MAX_OFFSET_HOURS] 小时（防误填年月）；
 * - 模型对照：取同类型 ±6h 内最近标记时刻（与 [com.tidespot.core.domain.tide.TideCalibrator.fitManual]
 *   的匹配窗一致），给出「你记录早/晚 Xh」提示，辅助对齐。
 */
object TideObservationEntryLogic {

    private val FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    /** 补记窗口（±72h）。 */
    const val MAX_OFFSET_HOURS: Long = 72L

    /** 模型匹配窗（与 fitManual 一致，§6.2b 规则 5）。 */
    const val MATCH_WINDOW_HOURS: Long = 6L

    enum class ParseError { BAD_FORMAT, OUT_OF_RANGE }

    data class Parsed(val epochSec: Long?, val error: ParseError?) {
        val ok: Boolean get() = epochSec != null
    }

    /** 输入框默认值：当前时刻（当地墙上时间 `yyyy-MM-dd HH:mm`）。 */
    fun defaultText(nowEpochSec: Long, zone: ZoneId): String =
        FMT.format(Instant.ofEpochSecond(nowEpochSec).atZone(zone))

    /** 解析用户输入（纯函数，JVM 可锁）。 */
    fun parse(text: String, zone: ZoneId, nowEpochSec: Long): Parsed {
        val local = runCatching { LocalDateTime.parse(text.trim(), FMT) }.getOrNull()
            ?: return Parsed(null, ParseError.BAD_FORMAT)
        val epoch = local.atZone(zone).toInstant().epochSecond
        if (abs(epoch - nowEpochSec) > MAX_OFFSET_HOURS * 3600) {
            return Parsed(null, ParseError.OUT_OF_RANGE)
        }
        return Parsed(epoch, null)
    }

    /** 同类型最近模型标记时刻（±6h；无匹配 → null）。 */
    fun nearestMarker(
        markers: Map<TideObservationType, List<Long>>,
        type: TideObservationType,
        epochSec: Long,
    ): Long? = markers[type].orEmpty()
        .filter { abs(it - epochSec) <= MATCH_WINDOW_HOURS * 3600 }
        .minByOrNull { abs(it - epochSec) }

    /**
     * 模型对照行：「模型高潮 06:00 · 你记录晚 0.5h」；无匹配 → null（禁编造）。
     */
    fun comparisonLine(
        markers: Map<TideObservationType, List<Long>>,
        type: TideObservationType,
        epochSec: Long,
        zone: ZoneId,
    ): String? {
        val marker = nearestMarker(markers, type, epochSec) ?: return null
        val modelText = HM.format(Instant.ofEpochSecond(marker).atZone(zone))
        val deltaHours = (epochSec - marker) / 3600.0
        val delta = when {
            abs(deltaHours) < 0.25 -> "基本一致"
            deltaHours > 0 -> "晚 ${oneDecimal(deltaHours)}h"
            else -> "早 ${oneDecimal(-deltaHours)}h"
        }
        return "模型${type.label} $modelText · 你记录$delta"
    }

    /** 已记录条目展示行：「高潮 · 11月10日 06:10」。 */
    fun observationLine(type: TideObservationType?, epochSec: Long, zone: ZoneId): String {
        val hm = DateTimeFormatter.ofPattern("M月d日 HH:mm")
            .format(Instant.ofEpochSecond(epochSec).atZone(zone))
        return "${type?.label ?: "高低潮"} · $hm"
    }

    fun errorText(error: ParseError): String = when (error) {
        ParseError.BAD_FORMAT -> "时刻格式应为 yyyy-MM-dd HH:mm"
        ParseError.OUT_OF_RANGE -> "时刻超出可补记范围（当前 ±${MAX_OFFSET_HOURS}h）"
    }

    private fun oneDecimal(v: Double): String = String.format(Locale.ROOT, "%.1f", v)
}