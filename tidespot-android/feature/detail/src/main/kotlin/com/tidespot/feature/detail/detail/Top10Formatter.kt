package com.tidespot.feature.detail.detail

import com.tidespot.core.domain.model.BestWindow
import com.tidespot.core.domain.tide.TideCalibrator
import com.tidespot.core.ui.format.Format
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Top10 / 现场校潮显示格式化纯函数（F-3.5 / §6.6 / §6.2b；JVM 单测锁）。
 *
 * - 时刻 = 选点当地墙上时间（tz 由调用侧注入：spot.tzOffsetSec → ZoneOffset.ofTotalSeconds）；
 * - 缺值显示 `—` 禁 0 填充（§6.7）；档位直接用 BestWindow.tier（禁重复实现档位解析）。
 */
object Top10Formatter {

    private val MD_HM = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

    /** epoch 秒 → "M月d日 HH:mm"（起始时刻用，含跨日）。 */
    fun time(epochSec: Long, zone: ZoneId): String =
        MD_HM.format(Instant.ofEpochSecond(epochSec).atZone(zone))

    /** epoch 秒 → "HH:mm"（峰值时刻与起始同日的紧凑显示）。 */
    fun hm(epochSec: Long, zone: ZoneId): String =
        HH_MM.format(Instant.ofEpochSecond(epochSec).atZone(zone))

    /** 时长（durationHours 整数小时）→ "2 小时"。 */
    fun duration(durationHours: Int): String = "${durationHours} 小时"

    /** 峰值分 + 档位 → "92 分 · 爆护窗口"（tier 直接透传）。 */
    fun peakScoreLine(window: BestWindow): String = "${window.peakScore} 分 · ${window.tier}"

    /** Top2 主因拼接 → "A、B"；空 → "—"（isEmpty() 判定，禁 0 填充）。 */
    fun topFactorsLine(topFactors: List<String>): String =
        if (topFactors.isEmpty()) Format.MISSING else topFactors.take(2).joinToString("、")

    /**
     * 兜底文案行：fallbackMessage + 「（M月d日 HH:mm）」（fallbackBestEpochSec 渲染；无时刻则仅文案）。
     * validHours <24 的禁用文案（find 内置）走同一路径（兜底时刻为 null → 直接展示文案）。
     */
    fun fallbackLine(message: String, fallbackBestEpochSec: Long?, zone: ZoneId): String =
        message + (fallbackBestEpochSec?.let { "（${time(it, zone)}）" } ?: "")

    /** 现场校潮结果行：adopted → "已启用：滞后 Xh"；未启用 → fitManual message 透传。 */
    fun calibrationLine(fit: TideCalibrator.ManualTideFit): String =
        if (fit.adopted) "已启用：滞后 ${fit.lagHours}h" else fit.message ?: "未启用"
}