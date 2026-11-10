package com.tidespot.feature.favorites.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.ui.format.Format
import com.tidespot.core.ui.zone.SpotZones

/**
 * FavoritesScreen（SRS F-2.1~F-2.6 + v1.1 P2）：
 * - 列表（名称/坐标/标签 + 最佳窗口摘要副标题）| 排序四选一（含 OPT-V11-14「综合推荐」）|
 *   编辑/新建对话框 | 删除二次确认 | 跨点 Top10 入口；
 * - OPT-V11-16 分组折叠（默认组「未分组」恒排最后）；
 * - OPT-V11-12 批量操作：**长按卡片进入多选**（全选/反选/批量删除/批量改标签/批量改分组，不做导出）；
 * - OPT-V11-15 信息卡片：卡片内「信息」按钮展开（**不占长按**，客户裁定 D16；字段全读库）。
 *
 * 全部操作离线可用（F-2.4）；TalkBack 全交互元素带 contentDescription（N-7）；缺值 `—` 禁 0 填充。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onOpenSpot: (FavoriteSpot) -> Unit,
    modifier: Modifier = Modifier,
    /** UX-20（OPT-V11-20）：从地图浮动「Top10」按钮进入时自动展开跨点 Top10 Sheet。 */
    openTop10OnStart: Boolean = false,
    onTop10Opened: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    // OPT-M5-03（CHG-16⑯）：时刻渲染按 spot.tzOffsetSec → ZoneOffset（SpotZones 单源）；
    // null = 未回写 → 系统默认并在卡片注明「本机时区」。跨点 Top10 逐条目用各点时区（见 Top10Content）。

    var showEdit by remember { mutableStateOf(false) }
    var editingSpot by remember { mutableStateOf<FavoriteSpot?>(null) }
    var deleteTarget by remember { mutableStateOf<FavoriteSpot?>(null) }
    var showTop10 by remember { mutableStateOf(false) }
    // OPT-V11-12：批量操作对话框/确认
    var showBatchDelete by remember { mutableStateOf(false) }
    var showBatchTags by remember { mutableStateOf(false) }
    var showBatchGroup by remember { mutableStateOf(false) }
    // UX-20：地图浮动 Top10 入口 → 自动展开（消费后回调复位，避免重复打开）
    LaunchedEffect(openTop10OnStart) {
        if (openTop10OnStart) {
            showTop10 = true
            onTop10Opened()
        }
    }

    // OPT-V11-10：「先筛后排」——过滤纯函数与排序纯函数正交；OPT-V11-16：分组在排序结果上切分
    val filtered = FavoritesFilterLogic.apply(state.spots, state.filter)
    val sorted = FavoritesSortLogic.sort(
        filtered,
        state.sortOrder,
        state.scoreBySpotId,
        state.rankBySpotId,
    )
    val groups = FavoritesGroupLogic.groups(sorted)

    LazyColumn(
        modifier = modifier.fillMaxSize()
            .semantics { contentDescription = "收藏列表：收藏点与最佳窗口摘要" },
    ) {
        item {
            // OPT-V11-10（P1）：搜索框（名称/备注实时过滤）+ 标签/预设 chips（先筛后排）
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = { viewModel.setFilter(state.filter.copy(query = it)) },
                label = { Text("搜索名称或备注") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    .semantics { contentDescription = "搜索收藏点" },
            )
            val tags = FavoritesFilterLogic.availableTags(state.spots)
            val presets = FavoritesFilterLogic.availablePresets(state.spots)
            if (tags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(tags, key = { "tag-$it" }) { tag ->
                        FilterChip(
                            selected = state.filter.tag == tag,
                            onClick = {
                                viewModel.setFilter(
                                    state.filter.copy(tag = if (state.filter.tag == tag) null else tag),
                                )
                            },
                            label = { Text(tag) },
                            modifier = Modifier.semantics { contentDescription = "标签筛选：$tag" },
                        )
                    }
                }
            }
            if (presets.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(presets, key = { "preset-$it" }) { preset ->
                        FilterChip(
                            selected = state.filter.preset == preset,
                            onClick = {
                                viewModel.setFilter(
                                    state.filter.copy(preset = if (state.filter.preset == preset) null else preset),
                                )
                            },
                            label = { Text(preset) },
                            modifier = Modifier.semantics { contentDescription = "场景筛选：$preset" },
                        )
                    }
                }
            }
            if (state.isSelecting) {
                // OPT-V11-12：多选操作栏（置顶，避 GAP-03 底部手势条遮挡）
                BatchActionBar(
                    selectedCount = state.selection.size,
                    allSelected = FavoritesBatchLogic.isAllSelected(
                        state.selection,
                        sorted.map { it.id },
                    ),
                    onSelectAll = { viewModel.selectAllVisible(sorted) },
                    onInvert = { viewModel.invertSelection(sorted) },
                    onExit = { viewModel.clearSelection() },
                    onDelete = { showBatchDelete = true },
                    onTags = { showBatchTags = true },
                    onGroup = { showBatchGroup = true },
                )
            } else {
                // F-2.4 排序四选一（OPT-V11-14 增「综合推荐」）
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { SortChip("按名称", state.sortOrder == SortOrder.NAME) { viewModel.setSortOrder(SortOrder.NAME) } }
                    item { SortChip("综合推荐", state.sortOrder == SortOrder.RECOMMENDED) { viewModel.setSortOrder(SortOrder.RECOMMENDED) } }
                    item { SortChip("按最近查看", state.sortOrder == SortOrder.LAST_VIEWED) { viewModel.setSortOrder(SortOrder.LAST_VIEWED) } }
                    item { SortChip("按分数", state.sortOrder == SortOrder.SCORE) { viewModel.setSortOrder(SortOrder.SCORE) } }
                    if (state.filter.isActive) {
                        item {
                            TextButton(
                                onClick = { viewModel.setFilter(FavoritesFilterLogic.Filter()) },
                                modifier = Modifier.semantics { contentDescription = "清除搜索与筛选条件" },
                            ) { Text("清除") }
                        }
                    }
                }
            }
            if (!state.isSelecting) {
                Button(
                    onClick = { showTop10 = true },
                    modifier = Modifier.padding(horizontal = 12.dp)
                        .semantics { contentDescription = "打开全收藏点最佳窗口前十名" },
                ) {
                    Text("全部收藏 Top10")
                }
            }
            if (state.loading) Text("加载中…", Modifier.padding(12.dp))
            state.lastSaveError?.let {
                Text(it, Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.batchMessage?.let {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { viewModel.clearBatchMessage() }) { Text("知道了") }
                }
            }
        }

        // OPT-V11-16：分组渲染（组内沿用排序序；默认组「未分组」恒在最后）
        groups.forEach { group ->
            item(key = "group-${group.name}") {
                GroupHeader(
                    group = group,
                    collapsed = FavoritesGroupLogic.isCollapsed(group.name, state.collapsedGroups),
                    onToggle = { viewModel.toggleGroup(group.name) },
                )
            }
            if (!FavoritesGroupLogic.isCollapsed(group.name, state.collapsedGroups)) {
                items(group.spots, key = { it.id }) { spot ->
                    // OPT-V11-11：列表级陈旧标记（TTL 判定复用 Ttl；无记录不编造数据时间）
                    val stale = FavoritesStaleLogic.mark(
                        fetchedAtMillis = state.summaries[spot.id]?.fetchedAtMillis ?: emptyMap(),
                        nowMillis = state.nowEpochSec * 1000L,
                        zone = SpotZones.zoneForSpot(spot),
                    )
                    // OPT-V11-15：信息卡片（只读库；不新增网络请求）
                    val info = if (state.infoSpotId == spot.id) {
                        FavoritesInfoCardLogic.build(
                            spot = spot,
                            fetchedAtMillis = state.summaries[spot.id]?.fetchedAtMillis ?: emptyMap(),
                            zone = SpotZones.zoneForSpot(spot),
                        )
                    } else {
                        null
                    }
                    FavoriteCard(
                        spot = spot,
                        summaryLine = buildString {
                            append(
                                FavoritesSummaryFormatter.line(
                                    state.summaries[spot.id] ?: FavoritesSpotSummary(),
                                    SpotZones.zoneForSpot(spot),
                                    // OPT-V11-06：相对时间（今天/明天/周X）；now=0（未加载）回落绝对格式
                                    state.nowEpochSec.takeIf { it > 0 },
                                ),
                            )
                            // OPT-V11-11：副标题附数据时间
                            stale.dataTimeLabel?.let { append(" · ").append(it) }
                            SpotZones.zoneNote(spot.tzOffsetSec)?.let { append(" · ") }
                        },
                        stale = stale,
                        selecting = state.isSelecting,
                        selected = state.selection.contains(spot.id),
                        info = info,
                        onOpen = {
                            viewModel.markViewed(spot.id)
                            onOpenSpot(spot)
                        },
                        onLongPress = { viewModel.toggleSelection(spot.id) },
                        onToggleInfo = { viewModel.toggleInfo(spot.id) },
                        onEdit = { editingSpot = spot; showEdit = true },
                        onDelete = { deleteTarget = spot },
                    )
                }
            }
        }
        if (!state.loading && sorted.isEmpty()) {
            item {
                if (state.filter.isActive) {
                    Column(Modifier.padding(12.dp)) {
                        Text("无匹配收藏", style = MaterialTheme.typography.bodySmall)
                        TextButton(
                            onClick = { viewModel.setFilter(FavoritesFilterLogic.Filter()) },
                            modifier = Modifier.semantics { contentDescription = "清除搜索与筛选条件" },
                        ) { Text("清除筛选") }
                    }
                } else {
                    Text("暂无收藏点", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showEdit) {
        EditSpotDialog(
            existing = editingSpot,
            onDismiss = { showEdit = false; editingSpot = null },
            onSave = { name, memo, preset, tags, groupName ->
                viewModel.saveFavorite(
                    existing = editingSpot,
                    name = name,
                    memo = memo,
                    preset = preset,
                    tags = tags,
                    groupName = groupName,
                )
                showEdit = false; editingSpot = null
            },
            groupOptions = FavoritesBatchLogic.existingGroups(state.spots),
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

    // OPT-V11-12：批量删除二次确认（条数明示）
    if (showBatchDelete) {
        AlertDialog(
            onDismissRequest = { showBatchDelete = false },
            title = { Text("批量删除") },
            text = { Text(FavoritesBatchLogic.deleteConfirmMessage(state.selection.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected(sorted)
                        showBatchDelete = false
                    },
                    modifier = Modifier.semantics { contentDescription = "确认批量删除" },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showBatchDelete = false }) { Text("取消") } },
        )
    }

    // OPT-V11-12：批量改标签（追加 / 替换）
    if (showBatchTags) {
        BatchTagsDialog(
            selectedCount = state.selection.size,
            onDismiss = { showBatchTags = false },
            onConfirm = { raw, replace ->
                viewModel.saveSelectedTags(raw, replace, sorted)
                showBatchTags = false
            },
        )
    }

    // OPT-V11-16：批量改分组
    if (showBatchGroup) {
        BatchGroupDialog(
            selectedCount = state.selection.size,
            groupOptions = FavoritesBatchLogic.existingGroups(state.spots),
            onDismiss = { showBatchGroup = false },
            onConfirm = { name ->
                viewModel.saveSelectedGroup(name, sorted)
                showBatchGroup = false
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

/** OPT-V11-16：分组头（可折叠；显示组内条数，闭合后不再渲染成员）。 */
@Composable
private fun GroupHeader(
    group: FavoritesGroupLogic.Group,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onToggle,
            modifier = Modifier.semantics {
                contentDescription = "分组 ${group.name}，${group.spots.size} 个收藏点，" +
                    if (collapsed) "已折叠，点击展开" else "点击折叠"
            },
        ) {
            Text(
                "${if (collapsed) "▸" else "▾"} ${group.name}（${group.spots.size}）",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** OPT-V11-12：多选操作栏。 */
@Composable
private fun BatchActionBar(
    selectedCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onExit: () -> Unit,
    onDelete: () -> Unit,
    onTags: () -> Unit,
    onGroup: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("已选 $selectedCount 项", style = MaterialTheme.typography.labelLarge)
            TextButton(
                onClick = onSelectAll,
                modifier = Modifier.semantics { contentDescription = if (allSelected) "已全选" else "全选当前列表" },
            ) { Text(if (allSelected) "已全选" else "全选") }
            TextButton(onClick = onInvert) { Text("反选") }
            TextButton(
                onClick = onExit,
                modifier = Modifier.semantics { contentDescription = "退出多选模式" },
            ) { Text("退出") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.semantics { contentDescription = "批量删除已选收藏点" },
            ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            TextButton(
                onClick = onTags,
                modifier = Modifier.semantics { contentDescription = "批量修改标签" },
            ) { Text("改标签") }
            TextButton(
                onClick = onGroup,
                modifier = Modifier.semantics { contentDescription = "批量修改分组" },
            ) { Text("改分组") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteCard(
    spot: FavoriteSpot,
    summaryLine: String,
    stale: StaleMark,
    selecting: Boolean,
    selected: Boolean,
    info: FavoritesInfoCardLogic.InfoCard?,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onToggleInfo: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val staleBadge = FavoritesStaleLogic.badge(stale.level)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            // OPT-V11-11：陈旧/已过期整项降透明度
            .alpha(if (stale.level == StaleLevel.NONE) 1f else FavoritesStaleLogic.ALPHA_STALE)
            // OPT-V11-12：长按 = 进入多选（D16）；单点点击 = 多选态切换选择 / 非多选态进详情
            .combinedClickable(
                onClick = { if (selecting) onLongPress() else onOpen() },
                onLongClick = onLongPress,
            )
            .semantics {
                contentDescription = "收藏点 ${spot.name}，坐标 ${spot.lat} ${spot.lon}，摘要 $summaryLine" +
                    (staleBadge?.let { "，状态 $it" } ?: "") +
                    if (selected) "，已选中" else ""
            },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    Checkbox(checked = selected, onCheckedChange = { onLongPress() })
                }
                Text(spot.name, style = MaterialTheme.typography.titleMedium)
                // OPT-V11-11：陈旧 / 已过期徽标（NONE 不渲染，禁空串）
                staleBadge?.let { badge ->
                    AssistChip(
                        onClick = {},
                        label = { Text(badge) },
                        modifier = Modifier.padding(start = 6.dp)
                            .semantics { contentDescription = "数据状态：$badge" },
                    )
                }
                if (!selecting) {
                    TextButton(
                        onClick = onToggleInfo,
                        modifier = Modifier.semantics {
                            contentDescription = if (info == null) "展开信息卡片 ${spot.name}" else "收起信息卡片 ${spot.name}"
                        },
                    ) { Text(if (info == null) "信息" else "收起") }
                }
            }
            Text(
                "${Format.coordinate(spot.lat)}, ${Format.coordinate(spot.lon)}",
                style = MaterialTheme.typography.bodySmall,
            )
            // F-2.1 标签（空标签不渲染，isEmpty() 判定）
            if (spot.tags.isNotEmpty()) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    spot.tags.take(4).forEach { tag -> AssistChip(onClick = {}, label = { Text(tag) }) }
                }
            }
            // F-2.5 最佳窗口摘要副标题（OPT-V11-11：已过期置灰）
            Text(
                summaryLine,
                style = MaterialTheme.typography.bodySmall,
                color = if (stale.level == StaleLevel.EXPIRED) {
                    MaterialTheme.colorScheme.outline
                } else {
                    Color.Unspecified
                },
                modifier = Modifier.padding(top = 4.dp),
            )
            // OPT-V11-15：信息卡片（只读库；离线完整）
            info?.let { card ->
                Column(Modifier.padding(top = 6.dp)) {
                    Text(card.coordinateLine, style = MaterialTheme.typography.bodySmall)
                    Text(card.waterBodyBadge, style = MaterialTheme.typography.bodySmall)
                    Text(card.elevationLine, style = MaterialTheme.typography.bodySmall)
                    Text(card.lastFetchedLine, style = MaterialTheme.typography.bodySmall)
                    Text(card.groupLine, style = MaterialTheme.typography.bodySmall)
                    Text(
                        card.accuracyNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (!selecting) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = "编辑收藏点 ${spot.name}" }) { Text("编辑") }
                    TextButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = "删除收藏点 ${spot.name}" }) { Text("删除") }
                }
            }
        }
    }
}

/**
 * F-2.1 编辑/新建对话框：必填名称、可选备注、可选场景预设（中文字符串写 spot.preset，
 * 与 HourlyScoreStore.resolvePreset 标签解析兼容）、可选标签（顿号/逗号分隔）、
 * OPT-V11-16 可选分组（空白 = 未分组）。
 */
@Composable
private fun EditSpotDialog(
    existing: FavoriteSpot?,
    groupOptions: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, memo: String?, preset: String?, tags: List<String>, groupName: String?) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var memo by remember { mutableStateOf(existing?.memo ?: "") }
    var preset by remember { mutableStateOf(existing?.preset ?: "") }
    var tagsRaw by remember { mutableStateOf(existing?.tags?.joinToString("、") ?: "") }
    var group by remember { mutableStateOf(existing?.groupName ?: "") }
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
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text("分组（可选，留空 = 未分组）") },
                    singleLine = true,
                    modifier = Modifier.semantics { contentDescription = "分组输入框" },
                )
                if (groupOptions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        groupOptions.forEach { option ->
                            FilterChip(
                                selected = group.trim() == option,
                                onClick = { group = option },
                                label = { Text(option) },
                                modifier = Modifier.semantics { contentDescription = "已有分组 $option" },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name,
                        memo.ifBlank { null },
                        preset.ifBlank { null },
                        FavoritesSortLogic.parseTags(tagsRaw),
                        FavoritesGroupLogic.normalizeGroupName(group),
                    )
                },
                enabled = !nameError,
                modifier = Modifier.semantics { contentDescription = "保存收藏点" },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** OPT-V11-12：批量改标签对话框（追加 / 替换二选一）。 */
@Composable
private fun BatchTagsDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (raw: String, replace: Boolean) -> Unit,
) {
    var raw by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量改标签（$selectedCount 项）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    label = { Text("标签（顿号或逗号分隔）") },
                    modifier = Modifier.semantics { contentDescription = "批量标签输入框" },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !replace,
                        onClick = { replace = false },
                        label = { Text("追加") },
                        modifier = Modifier.semantics { contentDescription = "追加标签" },
                    )
                    FilterChip(
                        selected = replace,
                        onClick = { replace = true },
                        label = { Text("替换") },
                        modifier = Modifier.semantics { contentDescription = "替换标签" },
                    )
                }
                Text(
                    if (replace) "将覆盖所选点的全部标签（输入为空即清空）" else "保留原标签并追加新标签",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(raw, replace) }) { Text("应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** OPT-V11-16：批量改分组对话框（空白 = 移回未分组）。 */
@Composable
private fun BatchGroupDialog(
    selectedCount: Int,
    groupOptions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (groupName: String?) -> Unit,
) {
    var group by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量改分组（$selectedCount 项）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text("分组名（留空 = 未分组）") },
                    singleLine = true,
                    modifier = Modifier.semantics { contentDescription = "批量分组输入框" },
                )
                if (groupOptions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        groupOptions.forEach { option ->
                            FilterChip(
                                selected = group.trim() == option,
                                onClick = { group = option },
                                label = { Text(option) },
                                modifier = Modifier.semantics { contentDescription = "已有分组 $option" },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(FavoritesGroupLogic.normalizeGroupName(group)) },
            ) { Text("应用") }
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