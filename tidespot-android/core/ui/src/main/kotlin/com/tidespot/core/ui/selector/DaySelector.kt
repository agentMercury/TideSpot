package com.tidespot.core.ui.selector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tidespot.core.ui.format.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * DaySelector 纯逻辑（F-3.1 / §8.2-1）：「今天 MM-dd」+「+1」…「+N」chip 序列。
 * 可选天数范围仅 7/10（设置项 M6 接入，非法值收敛到 7）；
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
        /** chip 主文案：今天 / +1 / +2 … */
        val chipLabel: String,
        /** chip 副文案：MM-dd（今天为「今天 11-06」合并态）。 */
        val dateLabel: String,
    ) {
        /** TalkBack 读法（N-7）。 */
        val contentDescription: String
            get() = if (offsetDays == 0) "今天，$dateLabel" else "未来第 $offsetDays 天，$dateLabel"
    }

    /**
     * 生成 chips。[nowEpochSec] 为当前时刻（epoch 秒）；「今天」= 该时刻在 [zone] 内的 LocalDate。
     * 固定输入可复现（JST 当地 23:30 / UTC+8 等跨时区边界用例直接构造 epoch 即可）。
     */
    fun chips(daysRange: Int, zone: ZoneId, nowEpochSec: Long): List<DayChip> {
        val n = coerceDaysRange(daysRange)
        val today = Instant.ofEpochSecond(nowEpochSec).atZone(zone).toLocalDate()
        return (0 until n).map { offset ->
            val d = today.plusDays(offset.toLong())
            DayChip(
                offsetDays = offset,
                localDate = d,
                chipLabel = if (offset == 0) "今天 ${Format.mmdd(d)}" else "+$offset",
                dateLabel = Format.mmdd(d),
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
                FilterChip(
                    selected = chip.offsetDays == selectedDay,
                    onClick = { onDaySelected(chip.offsetDays) },
                    label = { Text(chip.chipLabel) },
                    modifier = Modifier.semantics { contentDescription = chip.contentDescription },
                )
            }
        }
        infoSlot()
    }
}

/** 测试/JVM 便捷：UTC 固定时区。 */
val UTC: ZoneId = ZoneOffset.UTC

