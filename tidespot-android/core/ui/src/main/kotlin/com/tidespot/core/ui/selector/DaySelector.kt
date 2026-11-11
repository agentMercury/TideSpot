package com.tidespot.core.ui.selector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tidespot.core.ui.format.Format
import com.tidespot.core.ui.format.RelativeTimeFormatter
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.strings.ZhStrings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * DaySelector 纯逻辑（F-3.1 / §8.2-1 / OPT-V11-24）：首位「今天 周二」，其余「11/12 周三」…
 * （日期 + 星期，当地时区语义，横向滚动）。可选天数范围仅 7/10（设置项 M6 接入，非法值收敛到 7）；
 * 「今天」按**选点当地时区**判定（F-3.8 / N-9：比较用 epoch、边界用 zone 墙钟）。
 */
object DaySelectorLogic {

    const val MIN_DAYS = 7
    const val MAX_DAYS = 10

    /** 设置项收敛：≥10 → 10，否则一律 7（可选范围只允许 7 或 10）。 */
    fun coerceDaysRange(settingDays: Int): Int = if (settingDays >= MAX_DAYS) MAX_DAYS else MIN_DAYS

    /**
     * D14 跨零点「今天」自愈（CHG-16⑮）：两个 epoch 是否属同一当地日。
     * 调用方以心跳 epoch 对比，false = 已跨零点 → chips 必须重算（「今天」窗口自愈）；
     * 边界一律用 zone 墙钟判定（禁固定偏移，F-3.8/N-9 同口径）。
     */
    fun isSameLocalDay(prevEpochSec: Long, newEpochSec: Long, zone: ZoneId): Boolean =
        Instant.ofEpochSecond(prevEpochSec).atZone(zone).toLocalDate() ==
            Instant.ofEpochSecond(newEpochSec).atZone(zone).toLocalDate()
    /** DaySelector chip（F-3.1：今天 + 未来 1~N-1 天，共 N 个）。 */
    data class DayChip(
        val offsetDays: Int,
        val localDate: LocalDate,
        /** chip 主文案：`今天 周二` / `11/12 周三`（OPT-V11-24：日期 + 星期）。 */
        val chipLabel: String,
        /** chip 副文案：MM-dd（TalkBack 与跨月核对用）。 */
        val dateLabel: String,
        /** TalkBack 读法（N-7；含星期，与主文案同源；OPT-V11-13 由 [chips] 按文案源生成）。 */
        val contentDescription: String,
    )

    /**
     * 生成 chips。[nowEpochSec] 为当前时刻（epoch 秒）；「今天」= 该时刻在 [zone] 内的 LocalDate。
     * 固定输入可复现（JST 当地 23:30 / UTC+8 等跨时区边界用例直接构造 epoch 即可）。
     *
     * @param strings 文案来源（默认 [ZhStrings] 零行为漂移；Android 侧传 `ResStrings`）。
     */
    fun chips(
        daysRange: Int,
        zone: ZoneId,
        nowEpochSec: Long,
        strings: TideSpotStrings = ZhStrings,
    ): List<DaySelectorLogic.DayChip> {
        val n = coerceDaysRange(daysRange)
        val today = Instant.ofEpochSecond(nowEpochSec).atZone(zone).toLocalDate()
        return (0 until n).map { offset ->
            val d = today.plusDays(offset.toLong())
            val weekday = RelativeTimeFormatter.weekdayLabel(d, strings)
            val dateLabel = Format.mmdd(d)
            DayChip(
                offsetDays = offset,
                localDate = d,
                // OPT-V11-24：去 +N 相对格式 → 日期 + 星期（今天为「今天 周二」合并态）
                chipLabel = if (offset == 0) {
                    strings.dayShortToday(weekday)
                } else {
                    "${Format.md(d)} $weekday"
                },
                dateLabel = dateLabel,
                contentDescription = if (offset == 0) {
                    strings.dayToday(weekday, dateLabel)
                } else {
                    strings.dayFuture(offset, weekday, dateLabel)
                },
            )
        }
    }
}

/**
 * DaySelector 组件（§8.2-1）：横向 Chip 行；右侧「源更新 HH:mm · 本机拉取 HH:mm」由
 * [infoSlot] 槽位注入（来源文案由调用方按 F-3.7 元数据生成，:core:ui 不硬编码）。
 */
@Composable
fun DaySelector(
    chips: List<DaySelectorLogic.DayChip>,
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    infoSlot: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(chips, key = { it.offsetDays }) { chip ->
                // OPT-V115-02 / S02 C07：设计稿胶囊 chip（rx18；选中 #1565C0 填充白字，其余白底 1dp 描边）
                val selected = chip.offsetDays == selectedDay
                Surface(
                    onClick = { onDaySelected(chip.offsetDays) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) Color(0xFF1565C0) else MaterialTheme.colorScheme.surface,
                    border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.height(36.dp).semantics { contentDescription = chip.contentDescription },
                ) {
                    Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            chip.chipLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        infoSlot()
    }
}

/** 测试/JVM 便捷：UTC 固定时区。 */
val UTC: ZoneId = ZoneOffset.UTC

