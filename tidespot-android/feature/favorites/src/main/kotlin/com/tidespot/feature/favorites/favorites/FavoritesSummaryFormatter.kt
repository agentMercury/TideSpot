package com.tidespot.feature.favorites.favorites

import com.tidespot.core.domain.model.BestWindow
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * F-2.5 列表摘要格式化纯函数（JVM 单测锁：正常/未加载/跨日时刻）。
 *
 * - 时刻格式 "M月d日 HH:mm"（当地墙上时间，java.time）；
 * - 档位字符串直接用 [BestWindow.tier]（禁重复实现档位解析，CHG-13/D01 同源约束）；
 * - OPT-M5-03（CHG-16⑯）：时刻按 spot.tzOffsetSec → ZoneOffset（SpotZones 单源，调用方注入）；
 *   tzOffsetSec == null → 系统默认（ZoneId.systemDefault）渲染，调用处必须注明「本机时区」口径。
 */
object FavoritesSummaryFormatter {

    private val MD_HM = DateTimeFormatter.ofPattern("M月d日 HH:mm")

    /** epoch 秒 → "M月d日 HH:mm"（zone 可传 ZoneOffset.ofTotalSeconds(tzOffsetSec) 或系统默认）。 */
    fun time(epochSec: Long, zone: ZoneId): String =
        MD_HM.format(Instant.ofEpochSecond(epochSec).atZone(zone))

    /** 有窗口摘要行：「M月d日 HH:mm · 92 分 · 爆护窗口」（时刻 = 峰值时刻）。 */
    fun windowLine(window: BestWindow, zone: ZoneId): String =
        "${time(window.peakEpochSec, zone)} · ${window.peakScore} 分 · ${window.tier}"

    /** 兜底行：fallbackMessage + 「（M月d日 HH:mm）」（兜底最高分时刻；无时刻则只显示文案）。 */
    fun fallbackLine(message: String, fallbackBestEpochSec: Long?, zone: ZoneId): String =
        message + (fallbackBestEpochSec?.let { "（${time(it, zone)}）" } ?: "")

    /**
     * F-2.5 完整摘要副标题（唯一入口）：
     * - 有窗口 → [windowLine]；
     * - 无窗口但有兜底（含「有效数据不足 24 小时」禁用文案，CHG-15 偏差①后全过期也走此分支）→ [fallbackLine]；
     * - 其余（未算过/无库内行/无兜底）→ 「未加载」。
     */
    fun line(summary: FavoritesSpotSummary, zone: ZoneId = ZoneId.systemDefault()): String {
        val window = summary.window
        return when {
            window != null -> windowLine(window, zone)
            summary.fallbackMessage != null ->
                fallbackLine(summary.fallbackMessage, summary.fallbackBestEpochSec, zone)
            else -> "未加载"
        }
    }
}
