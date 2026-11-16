package com.tidespot.core.ui.selector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import com.tidespot.core.ui.theme.LocalSkin
import com.tidespot.core.ui.theme.onSkinAccent
import com.tidespot.core.ui.theme.skinAccent
import com.tidespot.core.ui.theme.vintageCard
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
        /**
         * OPT-V12-04（CHG-57）：两行排版**上行**——`今天` / `11/12`。
         * 旧单行版（OPT-V11-24）由本字段 + [weekdayLabel] 组合取代。
         */
        val topLabel: String,
        /** OPT-V12-04：两行排版**下行**——`周三`（WeekdayLabel，当地时区）。 */
        val weekdayLabel: String,
        /** chip 主文案（单行合并态，保留给 TalkBack/既有锁测试）：`今天 周二` / `11/12 周三`。 */
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
            val dateText = Format.md(d)
            DayChip(
                offsetDays = offset,
                localDate = d,
                // OPT-V12-04：两行排版（上行日期/今天，下行星期）；DATE 语义仍按选点当地时区
                topLabel = if (offset == 0) strings.dayTodayLabel else dateText,
                weekdayLabel = weekday,
                // OPT-V11-24 单行合并态保留（TalkBack 单句与既有锁测试口径）
                chipLabel = if (offset == 0) {
                    strings.dayShortToday(weekday)
                } else {
                    "$dateText $weekday"
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
 * DaySelector 组件（§8.2-1 / OPT-V12-04）：横向 Chip 行（**两行 chip**：日期 / 星期，一行 ≥5 个）；
 * 「源更新 HH:mm · 本机拉取 HH:mm」等来源信息由 [infoSlot] 注入 **下方独立一行**
 * （设计 C22；:core:ui 不硬编码来源文案）。
 */
@Composable
fun DaySelector(
    chips: List<DaySelectorLogic.DayChip>,
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    infoSlot: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val skinId = LocalSkin.current.id
        val isJournal = skinId == "e-tidejournal"
        val isBlueTide = skinId == "f-bluetide"
        val chipShape = when {
            isJournal -> RoundedCornerShape(8.dp)
            isBlueTide -> RoundedCornerShape(12.dp)
            else -> RoundedCornerShape(18.dp)
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(chips, key = { it.offsetDays }) { chip ->
                // OPT-V115-02 / S02 C07：胶囊 chip（rx18）；选中态改读 MaterialTheme primary/onPrimary（皮肤可换）
                // OPT-V12-04：两行排版（52dp 高、最小 56dp 宽 → 一行 ≥5 个），TalkBack 合并为单句
                // E：墨底纸字 + 墨线；F：蓝底白字；A/D 保持强调色底。
                val selected = chip.offsetDays == selectedDay
                val selectedColor = when {
                    isJournal -> MaterialTheme.colorScheme.onSurface
                    isBlueTide -> MaterialTheme.colorScheme.primary
                    else -> skinAccent()
                }
                val selectedInk = when {
                    isJournal -> MaterialTheme.colorScheme.surface
                    isBlueTide -> MaterialTheme.colorScheme.onPrimary
                    else -> onSkinAccent()
                }
                Surface(
                    onClick = { onDaySelected(chip.offsetDays) },
                    shape = chipShape,
                    color = if (selected) selectedColor else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.height(52.dp).widthIn(min = 56.dp)
                        .vintageCard(
                            shape = chipShape,
                            outlineColor = if (selected && isBlueTide) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                androidx.compose.ui.graphics.Color.Unspecified
                            },
                        )
                        .semantics(mergeDescendants = true) { contentDescription = chip.contentDescription },
                ) {
                    Column(
                        Modifier.widthIn(min = 56.dp).padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            chip.topLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) selectedInk else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            chip.weekdayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) selectedInk.copy(alpha = 0.86f) else MaterialTheme.colorScheme.onSurfaceVariant,
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

