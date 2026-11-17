package com.tidespot.feature.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tidespot.core.domain.model.BestWindow
import com.tidespot.core.domain.window.NextWindowFinder
import com.tidespot.core.ui.theme.LocalSkin
import com.tidespot.core.ui.theme.vintageCard
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

/**
 * 「下一个最佳窗口」提示条的输入数据。
 *
 * **为什么不直接复用** `feature:favorites` 的 `CrossSpotTop10Entry`：
 * `feature:map` 不依赖 `feature:favorites`（模块边界由 CONTRACT 冻结），
 * 由 `:app` 组装层把跨点窗口数据适配成本类型后传入。
 *
 * @property spotId 收藏点 id（点击进详情用；null = 不可点击）
 * @property spotName 点名称（展示用）
 * @property window 命中的窗口
 * @property zone 该点时区（渲染时刻用；null = 系统默认）
 */
data class NextWindowStripData(
    val spotId: Long?,
    val spotName: String,
    val window: BestWindow,
    val zone: ZoneId? = null,
)

/**
 * 首页「下一个最佳窗口」提示条（对齐 Web `NextWindowStrip.tsx`）。
 *
 * 与 Top10 横幅的分工：
 * - Top10 横幅回答「哪个窗口**最好**」——按 rank 排序，点击进收藏页看全部
 * - **本提示条**回答「现在**该不该出门**」——按时间紧迫性，点击直达该点详情
 *
 * 三种状态（与 Web 一致）：
 * - 正在窗口内 → 高亮，提示还剩多久
 * - 即将到来 → 沉稳色，提示还有多久
 * - 无窗口 → **不渲染**（避免首页噪声）
 *
 * 数据由 `:app` 组装层从跨点 Top10 结果适配传入（见 [NextWindowStripData]）。
 *
 * @param data 提示条数据（null = 无可用窗口 → 不渲染）
 * @param onOpenDetail 点击 → 进该点详情，同时带上**被点击的窗口**（供详情页自动定位）
 */
@Composable
fun NextWindowStrip(
    data: NextWindowStripData?,
    onOpenDetail: ((NextWindowStripData) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (data == null) return

    // 30 秒刷新一次倒计时（与 Web 的 TICK_MS 对齐；不用每秒刷新以免无谓重组）
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowSec = System.currentTimeMillis() / 1000L
        }
    }

    val window = data.window
    val result = remember(data, nowSec) { NextWindowFinder.findNext(listOf(window), nowSec) }
    if (result.state == NextWindowFinder.State.NONE) return

    val zone = data.zone ?: ZoneId.systemDefault()
    val range = localHour(window.startEpochSec, zone) + "–" +
        localHour(NextWindowFinder.windowEndExclusive(window), zone)
    val duration = NextWindowFinder.formatDuration(result.remainingSec)

    val isInWindow = result.state == NextWindowFinder.State.IN_WINDOW
    val label = if (isInWindow) "正在窗口内" else "下一个窗口"
    // 两行布局：第一行 = 状态 + 点名称（主信息）；第二行 = 时段 + 剩余 + 分数（细节）
    // 单行会把最关键的「还剩多久」截断掉（真机 AAK-AN00 实测）
    val title = "$label · ${data.spotName}"
    val subtitle = if (isInWindow) "$range · 还剩 $duration" else "$range · 还有 $duration"

    val isBlueTide = LocalSkin.current.id == "f-bluetide"
    val shape = if (isBlueTide) RoundedCornerShape(14.dp) else MaterialTheme.shapes.medium
    // 正在窗口内 → 更醒目（对齐 Web 的 .is-active 高亮）
    val container = when {
        isBlueTide && isInWindow -> Color(0xFF00897B)
        isBlueTide -> Color(0xFF1B3A5C)
        isInWindow -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when {
        isBlueTide -> Color.White
        isInWindow -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = { if (data.spotId != null) onOpenDetail?.invoke(data) },
        enabled = onOpenDetail != null && data.spotId != null,
        color = container,
        contentColor = content,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .vintageCard(shape = shape),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = content.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                window.peakScore.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = content,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

/** 本地整点 `HH:00`。 */
private fun localHour(epochSec: Long, zone: ZoneId): String {
    val hour = Instant.ofEpochSecond(epochSec).atZone(zone).hour
    return (if (hour < 10) "0$hour" else "$hour") + ":00"
}
