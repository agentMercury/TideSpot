package com.tidespot.feature.favorites.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tidespot.core.domain.model.BestWindow
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.window.BestWindowFinder
import com.tidespot.core.ui.format.Format
import com.tidespot.core.ui.zone.SpotZones
import java.time.ZoneId

/**
 * FavoritesScreen（SRS F-2.1~F-2.6）：列表（名称/坐标/标签 + 最佳窗口摘要副标题）|
 * 排序三选一 | 编辑/新建对话框（必填名称、可选备注/场景预设/标签）| 删除二次确认 | 跨点 Top10 入口。
 * 全部操作离线可用（F-2.4）；TalkBack 全交互元素带 contentDescription（N-7）；缺值 `—` 禁 0 填充。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onOpenSpot: (FavoriteSpot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    // OPT-M5-03（CHG-16⑯）：时刻渲染按 spot.tzOffsetSec → ZoneOffset（SpotZones 单源）；
    // null = 未回写 → 系统默认并在卡片注明「本机时区」。跨点 Top10 逐条目用各点时区（见 Top10Content）。

    var showEdit by remember { mutableStateOf(false) }
    var editingSpot by remember { mutableStateOf<FavoriteSpot?>(null) }
    var deleteTarget by remember { mutableStateOf<FavoriteSpot?>(null) }
    var showTop10 by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize()
            .semantics { contentDescription = "收藏列表：收藏点与最佳窗口摘要" },
    ) {
        item {
            // F-2.4 排序三选一
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SortChip("按名称", state.sortOrder == SortOrder.NAME) { viewModel.setSortOrder(SortOrder.NAME) }
                SortChip("按最近查看", state.sortOrder == SortOrder.LAST_VIEWED) { viewModel.setSortOrder(SortOrder.LAST_VIEWED) }
                SortChip("按分数", state.sortOrder == SortOrder.SCORE) { viewModel.setSortOrder(SortOrder.SCORE) }
            }
            Button(
                onClick = { showTop10 = true },
                modifier = Modifier.padding(horizontal = 12.dp)
                    .semantics { contentDescription = "打开全收藏点最佳窗口前十名" },
            ) {
                Text("全部收藏 Top10")
            }
            if (state.loading) Text("加载中…", Modifier.padding(12.dp))
            state.lastSaveError?.let {
                Text(it, Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        val sorted = FavoritesSortLogic.sort(state.spots, state.sortOrder, state.scoreBySpotId)
        items(sorted, key = { it.id }) { spot ->
            FavoriteCard(
                spot = spot,
                summaryLine = buildString {
                    append(
                        FavoritesSummaryFormatter.line(
                            state.summaries[spot.id] ?: FavoritesSpotSummary(),
                            SpotZones.zoneForSpot(spot),
                        ),
                    )
                    SpotZones.zoneNote(spot.tzOffsetSec)?.let { append(" · ") }
                },
                onOpen = {
                    viewModel.markViewed(spot.id)
                    onOpenSpot(spot)
                },
                onEdit = { editingSpot = spot; showEdit = true },
                onDelete = { deleteTarget = spot },
            )
        }
        if (!state.loading && sorted.isEmpty()) {
            item { Text("暂无收藏点", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (showEdit) {
        EditSpotDialog(
            existing = editingSpot,
            onDismiss = { showEdit = false; editingSpot = null },
            onSave = { name, memo, preset, tags ->
                viewModel.saveFavorite(editingSpot, name, memo, preset, tags)
                showEdit = false; editingSpot = null
            },
        )
    }

    // F-2.4 删除二次确认
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除收藏点") },
            text = { Text("确定删除「${target.name}」吗？该操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFavorite(target.id)
                    deleteTarget = null
                }, modifier = Modifier.semantics { contentDescription = "确认删除收藏点" }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    // F-2.6 跨点 Top10（Sheet 内独立滚动 = 单层 LazyColumn，无嵌套同向 LazyColumn，D02）
    if (showTop10) {
        ModalBottomSheet(onDismissRequest = { showTop10 = false }) {
            Top10Content(top10 = state.top10)
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.semantics { contentDescription = "排序方式：$label" },
    )
}

@Composable
private fun FavoriteCard(
    spot: FavoriteSpot,
    summaryLine: String,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics { contentDescription = "收藏点 ${spot.name}，坐标 ${spot.lat} ${spot.lon}，摘要 $summaryLine" },
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(spot.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${Format.value(spot.lat, 6)}, ${Format.value(spot.lon, 6)}",
                style = MaterialTheme.typography.bodySmall,
            )
            // F-2.1 标签（空标签不渲染，isEmpty() 判定）
            if (spot.tags.isNotEmpty()) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    spot.tags.take(4).forEach { tag -> AssistChip(onClick = {}, label = { Text(tag) }) }
                }
            }
            // F-2.5 最佳窗口摘要副标题
            Text(summaryLine, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = "编辑收藏点 ${spot.name}" }) { Text("编辑") }
                TextButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = "删除收藏点 ${spot.name}" }) { Text("删除") }
            }
        }
    }
}

/**
 * F-2.1 编辑/新建对话框：必填名称、可选备注、可选场景预设（中文字符串写 spot.preset，
 * 与 HourlyScoreStore.resolvePreset 标签解析兼容）、可选标签（顿号/逗号分隔）。
 */
@Composable
private fun EditSpotDialog(
    existing: FavoriteSpot?,
    onDismiss: () -> Unit,
    onSave: (name: String, memo: String?, preset: String?, tags: List<String>) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var memo by remember { mutableStateOf(existing?.memo ?: "") }
    var preset by remember { mutableStateOf(existing?.preset ?: "") }
    var tagsRaw by remember { mutableStateOf(existing?.tags?.joinToString("、") ?: "") }
    val nameError = name.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建收藏点" else "编辑收藏点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（必填）") },
                    isError = nameError,
                    supportingText = { if (nameError) Text("名称不能为空") },
                    modifier = Modifier.semantics { contentDescription = "收藏点名称输入框" },
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.semantics { contentDescription = "备注输入框" },
                )
                Text("场景预设（可选）", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FavoritesSortLogic.PRESET_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = preset == option,
                            onClick = { preset = if (preset == option) "" else option },
                            label = { Text(option) },
                            modifier = Modifier.semantics { contentDescription = "场景预设 $option" },
                        )
                    }
                }
                OutlinedTextField(
                    value = tagsRaw,
                    onValueChange = { tagsRaw = it },
                    label = { Text("标签（可选，顿号或逗号分隔）") },
                    modifier = Modifier.semantics { contentDescription = "标签输入框" },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, memo.ifBlank { null }, preset.ifBlank { null }, FavoritesSortLogic.parseTags(tagsRaw)) },
                enabled = !nameError,
                modifier = Modifier.semantics { contentDescription = "保存收藏点" },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** F-2.6 Top10 列表内容（OPT-M5-03：逐条目按归属点时区渲染；Sheet 内单层 LazyColumn 独立滚动）。 */
@Composable
private fun Top10Content(top10: List<CrossSpotTop10Entry>) {
    if (top10.isEmpty()) {
        Text("未加载", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        return
    }
    // 口径注明：任一条目时区未回写 → 按本机时区渲染并注明（SpotZones 口径）
    val deviceZoneUsed = top10.any { it.usesDeviceZone }
    Column(Modifier.padding(horizontal = 12.dp)) {
        if (deviceZoneUsed) {
            Text("注：部分点未回写时区，其时刻按${SpotZones.DEVICE_ZONE_NOTE}显示",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(vertical = 4.dp))
        }
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            items(top10.size) { i ->
                val entry = top10[i]
                val w = entry.window
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .semantics {
                            contentDescription = "第 ${i + 1} 名，${entry.spotName}，" +
                                "峰值 ${w.peakScore} 分 ${w.tier}，起始 ${FavoritesSummaryFormatter.time(w.startEpochSec, entry.zone)}"
                        },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("#${i + 1} ${entry.spotName}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "起 ${FavoritesSummaryFormatter.time(w.startEpochSec, entry.zone)} · " +
                                "峰 ${FavoritesSummaryFormatter.time(w.peakEpochSec, entry.zone)} · ${w.durationHours} 小时",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "${w.peakScore} 分 · ${w.tier}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        // Top2 主因（空 → —，禁 0 填充）
                        Text(
                            "主因 " + (if (w.topFactors.isEmpty()) Format.MISSING else w.topFactors.take(2).joinToString("、")),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        // 注意事项逐条（isEmpty() 判定，CHG-08 ④）
                        if (w.warnings.isNotEmpty()) {
                            w.warnings.forEach { Text("注意：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}

