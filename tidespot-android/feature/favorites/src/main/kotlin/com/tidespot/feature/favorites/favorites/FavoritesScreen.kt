package com.tidespot.feature.favorites.favorites
import com.tidespot.core.ui.theme.vintageCard
import com.tidespot.core.ui.theme.VintagePageHeader
import com.tidespot.core.ui.theme.VintageDashedDivider
import com.tidespot.core.ui.theme.LocalSkin
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.ui.band.BandTitles
import com.tidespot.core.domain.window.Top10TimeFilter
import com.tidespot.core.ui.R as UiR
import com.tidespot.core.ui.banner.OfflineBanner
import com.tidespot.core.ui.format.Format
import androidx.compose.ui.res.stringResource
import com.tidespot.core.ui.strings.localTideSpotStrings
import com.tidespot.feature.favorites.R
import com.tidespot.core.ui.zone.SpotZones
import java.time.ZoneId

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
    val strings = localTideSpotStrings()
    val screenDesc = stringResource(R.string.ts_fav_content_desc)
    // OPT-M5-03（CHG-16⑯）：时刻渲染按 spot.tzOffsetSec → ZoneOffset（SpotZones 单源）；
    // null = 未回写 → 系统默认并在卡片注明「本机时区」。跨点 Top10 逐条目用各点时区（见 Top10Content）。

    var showEdit by remember { mutableStateOf(false) }
    var editingSpot by remember { mutableStateOf<FavoriteSpot?>(null) }
    var deleteTarget by remember { mutableStateOf<FavoriteSpot?>(null) }
    var showTop10 by remember { mutableStateOf(false) }
    // CHG-95 / S2b：设计稿「全部分组 ▾」下拉。只是 UI 层可见域过滤，
    // 不进 FavoritesUiState / 不落库（排序与筛选的正交关系保持不变）。
    var selectedGroup by remember { mutableStateOf<String?>(null) }
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
    // CHG-95 / S2b：分组下拉只收窄可见域（内部组键），不改 ViewModel、不新增持久化键。
    val filtered = FavoritesFilterLogic.apply(state.spots, state.filter).let { base ->
        selectedGroup?.let { g -> base.filter { FavoritesGroupLogic.groupNameOf(it) == g } } ?: base
    }
    // 选项基于全量收藏点生成，避免「筛窄后选项消失」导致无法切回全部分组
    val groupOptions = FavoritesGroupLogic.groups(state.spots).map { it.name }
    val sorted = FavoritesSortLogic.sort(
        filtered,
        state.sortOrder,
        state.scoreBySpotId,
        state.rankBySpotId,
    )
    val groups = FavoritesGroupLogic.groups(sorted)

    // OPT-V12-02（CHG-57）：下拉刷新（非 force；TTL 命中 0 请求；切页即取消）
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { viewModel.refreshAll(sorted) },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .semantics { contentDescription = screenDesc },
        ) {
        // OPT-V115-01 #4（ts_offline）：离线横幅（不阻断离线数据，仅提示）
        item {
            VintagePageHeader(
                title = stringResource(R.string.ts_fav_page_title),
                subtitle = "TideSpot · ${LocalSkin.current.nameEn}",
                // CHG-93 r6 真机复拍：LazyColumn 的 item 没有水平 padding，
                // 页头曾贴边导致首字被裁；与卡片保持同一 12dp 左右边距
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        item { OfflineBanner() }
        item {
            // OPT-V11-10（P1）：搜索框（名称/备注实时过滤）+ 标签/预设 chips（先筛后排）
            val searchDesc = stringResource(R.string.ts_fav_search_desc)
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = { viewModel.setFilter(state.filter.copy(query = it)) },
                label = { Text(stringResource(R.string.ts_fav_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    .semantics { contentDescription = searchDesc },
            )
            val tags = FavoritesFilterLogic.availableTags(state.spots)
            val presets = FavoritesFilterLogic.availablePresets(state.spots)
            if (tags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(tags, key = { "tag-$it" }) { tag ->
                        val tagDesc = stringResource(R.string.ts_fav_tag_filter, tag)
                        FilterChip(
                            selected = state.filter.tag == tag,
                            onClick = {
                                viewModel.setFilter(
                                    state.filter.copy(tag = if (state.filter.tag == tag) null else tag),
                                )
                            },
                            label = { Text(tag) },
                            modifier = Modifier.semantics { contentDescription = tagDesc },
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
                        val presetDesc = stringResource(R.string.ts_fav_preset_filter, preset)
                        FilterChip(
                            selected = state.filter.preset == preset,
                            onClick = {
                                viewModel.setFilter(
                                    state.filter.copy(preset = if (state.filter.preset == preset) null else preset),
                                )
                            },
                            label = { Text(preset) },
                            modifier = Modifier.semantics { contentDescription = presetDesc },
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
                // CHG-95 / S2b：设计稿紧凑行表 —— 4 个排序 chip + 分组 chip 收敛成 2 个「当前值 + ▾」下拉。
                // 交互语义：4 个排序选项与全部分组在下拉内全部可达；长按进入多选（客户裁定 D16）
                // 保持不变，因此不新增「批量」按钮，避免与冻结裁决冲突。
                val sortLabels = listOf(
                    SortOrder.NAME to stringResource(R.string.ts_fav_sort_name),
                    SortOrder.RECOMMENDED to stringResource(R.string.ts_fav_sort_recommended),
                    SortOrder.LAST_VIEWED to stringResource(R.string.ts_fav_sort_recent),
                    SortOrder.SCORE to stringResource(R.string.ts_fav_sort_score),
                )
                val currentSort = sortLabels.first { it.first == state.sortOrder }.second
                val sortDesc = stringResource(R.string.ts_fav_sort_desc, currentSort)
                val allGroups = stringResource(R.string.ts_fav_group_all)
                val groupStrings = localTideSpotStrings()
                val currentGroup = selectedGroup
                    ?.let { FavoritesGroupLogic.displayName(it, groupStrings) } ?: allGroups
                val groupDesc = stringResource(R.string.ts_fav_group_filter_desc, currentGroup)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VintageDropdown(
                        label = currentSort,
                        desc = sortDesc,
                        options = sortLabels.map { (order, label) ->
                            label to { viewModel.setSortOrder(order) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    VintageDropdown(
                        label = currentGroup,
                        desc = groupDesc,
                        options = listOf(allGroups to { selectedGroup = null }) +
                            groupOptions.map { name ->
                                FavoritesGroupLogic.displayName(name, groupStrings) to { selectedGroup = name }
                            },
                        modifier = Modifier.weight(1f),
                    )
                    if (state.filter.isActive || selectedGroup != null) {
                        val clearDesc = stringResource(R.string.ts_fav_clear_desc)
                        TextButton(
                            onClick = {
                                viewModel.setFilter(FavoritesFilterLogic.Filter())
                                selectedGroup = null
                            },
                            modifier = Modifier.semantics { contentDescription = clearDesc },
                        ) { Text(stringResource(R.string.ts_fav_clear)) }
                    }
                }
            }
            if (!state.isSelecting) {
                val top10Desc = stringResource(R.string.ts_fav_top10_desc)
                val refreshAllDesc = stringResource(R.string.ts_fav_refresh_all_desc)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { showTop10 = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).semantics { contentDescription = top10Desc },
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // CHG-95 / S2b：设计稿横幅左侧鱼形序列（纯装饰，逐条缩小）
                            listOf(18.dp, 13.dp, 9.dp).forEach { fishWidth ->
                                Icon(
                                    painter = painterResource(UiR.drawable.ic_fish_vintage),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    modifier = Modifier.size(width = fishWidth, height = 9.dp),
                                )
                            }
                            Column(Modifier.weight(1f).padding(start = 2.dp)) {
                                Text(
                                    stringResource(UiR.string.ts_top10_all),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1,
                                )
                                Text(
                                    stringResource(R.string.ts_fav_top10_subtitle),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                    maxLines = 1,
                                )
                            }
                            Text(
                                "›",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    // OPT-V12-02：显式刷新入口（下拉不可及时仍可用；非 force，受 TTL/令牌桶约束）
                    OutlinedButton(
                        onClick = { viewModel.refreshAll(sorted) },
                        enabled = !state.refreshing && sorted.isNotEmpty(),
                        modifier = Modifier.semantics { contentDescription = refreshAllDesc },
                    ) {
                        Text(stringResource(R.string.ts_fav_refresh_all))
                    }
                }
            }
            if (state.loading) Text(stringResource(R.string.ts_fav_loading), Modifier.padding(12.dp))
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
                    TextButton(onClick = { viewModel.clearBatchMessage() }) { Text(stringResource(R.string.ts_fav_got_it)) }
                }
            }
            // OPT-V12-02：刷新进度 / 汇总 / 离线提示（失败项保留旧数据与陈旧标记，不隐藏真实状态）
            if (state.refreshing) {
                Text(
                    stringResource(R.string.ts_fav_refreshing, state.refreshDone, state.refreshTotal),
                    Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            state.refreshSummary?.let { summary ->
                Text(
                    if (summary.failed > 0) {
                        stringResource(
                            R.string.ts_fav_refresh_summary_failed,
                            summary.refreshed,
                            summary.upToDate,
                            summary.failed,
                        )
                    } else {
                        stringResource(R.string.ts_fav_refresh_summary, summary.refreshed, summary.upToDate)
                    },
                    Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (summary.failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            if (state.refreshOffline) {
                Text(
                    stringResource(R.string.ts_fav_refresh_offline),
                    Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.refreshSummary != null || state.refreshOffline) {
                LaunchedEffect(state.refreshSummary, state.refreshOffline) {
                    kotlinx.coroutines.delay(5_000)
                    viewModel.clearRefreshMessage()
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
                        strings = strings,
                    )
                    // OPT-V11-15：信息卡片（只读库；不新增网络请求）
                    val info = if (state.infoSpotId == spot.id) {
                        FavoritesInfoCardLogic.build(
                            spot = spot,
                            fetchedAtMillis = state.summaries[spot.id]?.fetchedAtMillis ?: emptyMap(),
                            zone = SpotZones.zoneForSpot(spot),
                            strings = strings,
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
                                    strings,
                                ),
                            )
                            // OPT-V11-11：副标题附数据时间
                            stale.dataTimeLabel?.let { append(" · ").append(it) }
                            SpotZones.zoneNote(spot.tzOffsetSec, strings)?.let { append(" · ") }
                        },
                        stale = stale,
                        selecting = state.isSelecting,
                        selected = state.selection.contains(spot.id),
                        score = state.scoreBySpotId[spot.id],
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
                        Text(stringResource(R.string.ts_fav_no_match), style = MaterialTheme.typography.bodySmall)
                        val clearDesc = stringResource(R.string.ts_fav_clear_desc)
                        TextButton(
                            onClick = { viewModel.setFilter(FavoritesFilterLogic.Filter()) },
                            modifier = Modifier.semantics { contentDescription = clearDesc },
                        ) { Text(stringResource(R.string.ts_fav_clear_filters)) }
                    }
                } else {
                    Text(stringResource(R.string.ts_fav_empty), Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall)
                    // OPT-V12-02：空态/失败态同样提供「刷新全部」（下拉不可及时仍可用）
                    val refreshAllDesc = stringResource(R.string.ts_fav_refresh_all_desc)
                    OutlinedButton(
                        onClick = { viewModel.refreshAll(state.spots) },
                        enabled = !state.refreshing && state.spots.isNotEmpty(),
                        modifier = Modifier.padding(horizontal = 12.dp)
                            .semantics { contentDescription = refreshAllDesc },
                    ) { Text(stringResource(R.string.ts_fav_refresh_all)) }
                }
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
        val confirmDesc = stringResource(R.string.ts_fav_delete_confirm_desc)
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.ts_fav_delete)) },
            text = { Text(stringResource(R.string.ts_fav_delete_confirm, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFavorite(target.id)
                    deleteTarget = null
                }, modifier = Modifier.semantics { contentDescription = confirmDesc }) {
                    Text(stringResource(R.string.ts_fav_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(UiR.string.ts_cancel)) }
            },
        )
    }

    // OPT-V11-12：批量删除二次确认（条数明示）
    if (showBatchDelete) {
        val batchConfirmDesc = stringResource(R.string.ts_fav_batch_delete_desc)
        AlertDialog(
            onDismissRequest = { showBatchDelete = false },
            title = { Text(stringResource(R.string.ts_fav_batch_delete)) },
            text = { Text(FavoritesBatchLogic.deleteConfirmMessage(state.selection.size, strings)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected(sorted)
                        showBatchDelete = false
                    },
                    modifier = Modifier.semantics { contentDescription = batchConfirmDesc },
                ) { Text(stringResource(R.string.ts_fav_delete_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDelete = false }) { Text(stringResource(UiR.string.ts_cancel)) }
            },
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
        ModalBottomSheet(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),onDismissRequest = { showTop10 = false }) {
            Column(Modifier.navigationBarsPadding()) {
                Top10Content(top10 = state.top10)
            }
        }
    }
}

/**
 * CHG-95 / S2b：设计稿的「当前值 + ▾」下拉，替代 M3 FilterChip。
 * 排序与分组共用；选项文案全部由调用方传入既有资源，不新增入口文案。
 * 已知例外：chip → 下拉会改变 dump 的可交互节点数与标签，按客户 2026-11-15
 * 裁决登记为红线已知例外，红线脚本本身不放宽（见 CONTRACT / PARITY_GAP）。
 */
@Composable
private fun VintageDropdown(
    label: String,
    desc: String,
    options: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Button(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = desc },
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text("▾", style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, onSelect) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { expanded = false; onSelect() },
                )
            }
        }
    }
}

/** OPT-V11-16：分组头（可折叠；显示组内条数，闭合后不再渲染成员）。 */
@Composable
private fun GroupHeader(
    group: FavoritesGroupLogic.Group,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val groupName = FavoritesGroupLogic.displayName(group.name, localTideSpotStrings())
    val headerDesc = stringResource(R.string.ts_fav_group_desc, groupName, group.spots.size) +
        stringResource(if (collapsed) R.string.ts_fav_collapsed else R.string.ts_fav_expand)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onToggle,
            modifier = Modifier.semantics { contentDescription = headerDesc },
        ) {
            Text(
                "${if (collapsed) "▸" else "▾"} $groupName (${group.spots.size})",
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
        val selectAllDesc = stringResource(if (allSelected) R.string.ts_fav_all_selected else R.string.ts_fav_select_all_desc)
        val exitDesc = stringResource(R.string.ts_fav_exit_desc)
        val deleteDesc = stringResource(R.string.ts_fav_batch_delete_sel_desc)
        val tagDesc = stringResource(R.string.ts_fav_batch_tag_desc)
        val groupDesc = stringResource(R.string.ts_fav_batch_group_desc)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ts_fav_selected_count, selectedCount),
                style = MaterialTheme.typography.labelLarge)
            TextButton(
                onClick = onSelectAll,
                modifier = Modifier.semantics { contentDescription = selectAllDesc },
            ) {
                Text(stringResource(if (allSelected) R.string.ts_fav_all_selected else R.string.ts_fav_select_all))
            }
            TextButton(onClick = onInvert) { Text(stringResource(R.string.ts_fav_invert)) }
            TextButton(
                onClick = onExit,
                modifier = Modifier.semantics { contentDescription = exitDesc },
            ) { Text(stringResource(R.string.ts_fav_exit)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.semantics { contentDescription = deleteDesc },
            ) { Text(stringResource(R.string.ts_fav_delete_action), color = MaterialTheme.colorScheme.error) }
            TextButton(
                onClick = onTags,
                modifier = Modifier.semantics { contentDescription = tagDesc },
            ) { Text(stringResource(R.string.ts_fav_batch_tag)) }
            TextButton(
                onClick = onGroup,
                modifier = Modifier.semantics { contentDescription = groupDesc },
            ) { Text(stringResource(R.string.ts_fav_batch_group)) }
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
    /** CHG-95 / S2b：设计稿右侧大号分数（peakScore，未加载 = null，不编造 0）。 */
    score: Int?,
    info: FavoritesInfoCardLogic.InfoCard?,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onToggleInfo: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = localTideSpotStrings()
    val staleBadge = FavoritesStaleLogic.badge(stale.level, strings)
    val itemDesc = stringResource(
        R.string.ts_fav_item_desc,
        spot.name,
        spot.lat.toString(),
        spot.lon.toString(),
        summaryLine,
    ) +
        (staleBadge?.let { stringResource(R.string.ts_fav_status_suffix, it) } ?: "") +
        if (selected) stringResource(R.string.ts_fav_selected_suffix) else ""
    val infoDesc = stringResource(
        if (info == null) R.string.ts_fav_expand_info else R.string.ts_fav_collapse_info,
        spot.name,
    )
    val dataStatusDesc = staleBadge?.let { stringResource(R.string.ts_fav_data_status, it) }
    val editDesc = stringResource(R.string.ts_fav_edit_desc, spot.name)
    val deleteDesc = stringResource(R.string.ts_fav_delete_item_desc, spot.name)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            // CHG-93 / 路径 B：统一票据卡骨架（描边 + 套印 + 非对称圆角）
            .vintageCard()
            // OPT-V11-11：陈旧/已过期整项降透明度
            .alpha(if (stale.level == StaleLevel.NONE) 1f else FavoritesStaleLogic.ALPHA_STALE)
            // OPT-V11-12：长按 = 进入多选（D16）；单点点击 = 多选态切换选择 / 非多选态进详情
            .combinedClickable(
                onClick = { if (selecting) onLongPress() else onOpen() },
                onLongClick = onLongPress,
            )
            .semantics { contentDescription = itemDesc },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // CHG-95 / S2b：设计稿紧凑票据行 —— 圆形选择圈 | 名称 + 水体 | 大号分数 + 档位 + 小鱼
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ringColor = if (selected && selecting) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
                // 非多选态纯装饰不可点；多选态点击 = 切换选择，语义与旧 Checkbox 完全一致
                Box(
                    Modifier.size(26.dp)
                        .clip(CircleShape)
                        .background(if (selected && selecting) ringColor else Color.Transparent)
                        .border(2.dp, ringColor, CircleShape)
                        .then(if (selecting) Modifier.clickable { onLongPress() } else Modifier),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(spot.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    // OPT-V115-02 / S08：水体类型行（设计：名称下方）
                    Text(
                        FavoritesInfoCardLogic.waterBodyLabel(spot.waterBody, strings),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            score?.toString() ?: Format.MISSING,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            painter = painterResource(UiR.drawable.ic_fish_vintage),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp).size(width = 18.dp, height = 10.dp),
                        )
                    }
                    score?.let {
                        Text(
                            BandTitles.fromDomainTier(it, strings),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            VintageDashedDivider(Modifier.fillMaxWidth().padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${Format.coordinate(spot.lat)}, ${Format.coordinate(spot.lon)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                // OPT-V11-11：陈旧 / 已过期徽标（NONE 不渲染，禁空串）
                staleBadge?.let { badge ->
                    AssistChip(
                        onClick = {},
                        label = { Text(badge) },
                        modifier = Modifier.semantics { contentDescription = dataStatusDesc ?: badge },
                    )
                }
            }
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
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = onToggleInfo,
                        modifier = Modifier.semantics { contentDescription = infoDesc },
                    ) {
                        Text(stringResource(if (info == null) R.string.ts_fav_info else R.string.ts_fav_collapse))
                    }
                    TextButton(onClick = onEdit,
                        modifier = Modifier.semantics { contentDescription = editDesc }) {
                        Text(stringResource(R.string.ts_fav_edit))
                    }
                    TextButton(onClick = onDelete,
                        modifier = Modifier.semantics { contentDescription = deleteDesc }) {
                        Text(stringResource(R.string.ts_fav_delete_action))
                    }
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
        title = {
            Text(stringResource(if (existing == null) R.string.ts_fav_new_title else R.string.ts_fav_edit_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val nameInputDesc = stringResource(R.string.ts_fav_name_input)
                val memoInputDesc = stringResource(R.string.ts_fav_memo_input)
                val tagsInputDesc = stringResource(R.string.ts_fav_tags_input)
                val groupInputDesc = stringResource(R.string.ts_fav_group_input)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ts_fav_name_label)) },
                    isError = nameError,
                    supportingText = { if (nameError) Text(stringResource(R.string.ts_fav_name_required)) },
                    modifier = Modifier.semantics { contentDescription = nameInputDesc },
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text(stringResource(R.string.ts_fav_memo_label)) },
                    modifier = Modifier.semantics { contentDescription = memoInputDesc },
                )
                Text(stringResource(R.string.ts_fav_preset_label), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FavoritesSortLogic.PRESET_OPTIONS.forEach { option ->
                        val presetDesc = stringResource(R.string.ts_fav_preset_option, option)
                        FilterChip(
                            selected = preset == option,
                            onClick = { preset = if (preset == option) "" else option },
                            label = { Text(option) },
                            modifier = Modifier.semantics { contentDescription = presetDesc },
                        )
                    }
                }
                OutlinedTextField(
                    value = tagsRaw,
                    onValueChange = { tagsRaw = it },
                    label = { Text(stringResource(R.string.ts_fav_tags_label)) },
                    modifier = Modifier.semantics { contentDescription = tagsInputDesc },
                )
                // OPT-V12-07（CHG-57）：高频标签预设 chips（点选加入/再点取消；与手输共存）
                Text(stringResource(R.string.ts_fav_tag_presets), style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TagPresets.DEFAULT, key = { "preset-$it" }) { preset ->
                        val presetDesc = stringResource(R.string.ts_fav_tag_preset_desc, preset)
                        FilterChip(
                            selected = TagPresets.selectedInRaw(tagsRaw, preset),
                            onClick = { tagsRaw = TagPresets.toggleRaw(tagsRaw, preset) },
                            label = { Text(preset) },
                            modifier = Modifier.semantics { contentDescription = presetDesc },
                        )
                    }
                }
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text(stringResource(R.string.ts_fav_group_label)) },
                    singleLine = true,
                    modifier = Modifier.semantics { contentDescription = groupInputDesc },
                )
                if (groupOptions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        groupOptions.forEach { option ->
                            val existingDesc = stringResource(R.string.ts_fav_existing_group, option)
                            FilterChip(
                                selected = group.trim() == option,
                                onClick = { group = option },
                                label = { Text(option) },
                                modifier = Modifier.semantics { contentDescription = existingDesc },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val saveDesc = stringResource(R.string.ts_fav_save_item)
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
                modifier = Modifier.semantics { contentDescription = saveDesc },
            ) { Text(stringResource(R.string.ts_fav_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ts_cancel)) }
        },
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
        title = { Text(stringResource(R.string.ts_fav_batch_tag_title, selectedCount)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val batchTagsDesc = stringResource(R.string.ts_fav_batch_tags_input)
                val appendDesc = stringResource(R.string.ts_fav_append_desc)
                val replaceDesc = stringResource(R.string.ts_fav_replace_desc)
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    label = { Text(stringResource(R.string.ts_fav_batch_tags_label)) },
                    modifier = Modifier.semantics { contentDescription = batchTagsDesc },
                )
                // OPT-V12-07：预设 chips（追加/替换语义不变；点选仅改变待写入标签串）
                Text(stringResource(R.string.ts_fav_tag_presets), style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TagPresets.DEFAULT, key = { "batch-preset-$it" }) { preset ->
                        val presetDesc = stringResource(R.string.ts_fav_tag_preset_desc, preset)
                        FilterChip(
                            selected = TagPresets.selectedInRaw(raw, preset),
                            onClick = { raw = TagPresets.toggleRaw(raw, preset) },
                            label = { Text(preset) },
                            modifier = Modifier.semantics { contentDescription = presetDesc },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !replace,
                        onClick = { replace = false },
                        label = { Text(stringResource(R.string.ts_fav_append)) },
                        modifier = Modifier.semantics { contentDescription = appendDesc },
                    )
                    FilterChip(
                        selected = replace,
                        onClick = { replace = true },
                        label = { Text(stringResource(R.string.ts_fav_replace)) },
                        modifier = Modifier.semantics { contentDescription = replaceDesc },
                    )
                }
                Text(
                    stringResource(if (replace) R.string.ts_fav_replace_hint else R.string.ts_fav_append_hint),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(raw, replace) }) { Text(stringResource(R.string.ts_fav_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ts_cancel)) }
        },
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
        title = { Text(stringResource(R.string.ts_fav_batch_group_title, selectedCount)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val batchGroupDesc = stringResource(R.string.ts_fav_batch_group_input)
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text(stringResource(R.string.ts_fav_group_name_label)) },
                    singleLine = true,
                    modifier = Modifier.semantics { contentDescription = batchGroupDesc },
                )
                if (groupOptions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        groupOptions.forEach { option ->
                            val existingDesc = stringResource(R.string.ts_fav_existing_group, option)
                            FilterChip(
                                selected = group.trim() == option,
                                onClick = { group = option },
                                label = { Text(option) },
                                modifier = Modifier.semantics { contentDescription = existingDesc },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(FavoritesGroupLogic.normalizeGroupName(group)) },
            ) { Text(stringResource(R.string.ts_fav_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ts_cancel)) } },
    )
}

/**
 * F-2.6 Top10 列表内容（OPT-M5-03：逐条目按归属点时区渲染；Sheet 内单层 LazyColumn 独立滚动）。
 *
 * OPT-A-04（2026-11-13）：补设计稿 OV01 的**时段筛选 + ≥60 分门槛 + 空态**——
 * 标题「全部收藏 Top10 · 时段筛选」、四 Chip（全部/接下来 24h/本周末/自选）、
 * 无合格窗口时明确显示空态（禁止兜底造假）。
 */
@Composable
private fun Top10Content(top10: List<CrossSpotTop10Entry>) {
    val strings = localTideSpotStrings()
    if (top10.isEmpty()) {
        Text(stringResource(R.string.ts_fav_not_loaded), Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium)
        return
    }
    // 时段筛选状态：custom != null 表示「自选」（优先于 preset）；preset 三选与设计稿一致
    var preset by remember { mutableStateOf(Top10TimeFilter.Preset.ALL) }
    var customRange by remember { mutableStateOf<Top10TimeFilter.TimeRange?>(null) }
    var showCustomDialog by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val nowSec = remember { System.currentTimeMillis() / 1000L }
    val range = customRange ?: Top10TimeFilter.presetRange(preset, nowSec, zone)
    val shown = CrossTop10Filter.filter(top10, range)

    // 口径注明：任一条目时区未回写 → 按本机时区渲染并注明（SpotZones 口径）
    val deviceZoneUsed = top10.any { it.usesDeviceZone }
    Column(Modifier.padding(horizontal = 12.dp)) {
        Text(
            stringResource(R.string.ts_fav_top10_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Top10FilterChip(
                label = stringResource(R.string.ts_fav_filter_all),
                selected = preset == Top10TimeFilter.Preset.ALL && customRange == null,
                onClick = {
                    customRange = null
                    preset = Top10TimeFilter.Preset.ALL
                },
            )
            Top10FilterChip(
                label = stringResource(R.string.ts_fav_filter_next24h),
                selected = preset == Top10TimeFilter.Preset.NEXT_24H && customRange == null,
                onClick = {
                    customRange = null
                    preset = Top10TimeFilter.Preset.NEXT_24H
                },
            )
            Top10FilterChip(
                label = stringResource(R.string.ts_fav_filter_weekend),
                selected = preset == Top10TimeFilter.Preset.THIS_WEEKEND && customRange == null,
                onClick = {
                    customRange = null
                    preset = Top10TimeFilter.Preset.THIS_WEEKEND
                },
            )
            Top10FilterChip(
                label = stringResource(R.string.ts_fav_filter_custom),
                selected = customRange != null,
                onClick = { showCustomDialog = true },
            )
        }
        if (deviceZoneUsed) {
            Text(stringResource(R.string.ts_fav_zone_note, strings.deviceZoneNote),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(vertical = 4.dp))
        }
        if (shown.isEmpty()) {
            // 设计稿红线：无 ≥60 分窗口时显示空态，禁止兜底造假
            Text(
                stringResource(R.string.ts_fav_top10_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            return@Column
        }
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            items(shown.size) { i ->
                val entry = shown[i]
                val w = entry.window
                val startText = FavoritesSummaryFormatter.time(w.startEpochSec, entry.zone, strings)
                val peakText = FavoritesSummaryFormatter.time(w.peakEpochSec, entry.zone, strings)
                val rankDesc = stringResource(
                    R.string.ts_fav_rank_desc,
                    i + 1,
                    entry.spotName,
                    w.peakScore,
                    w.tier,
                    startText,
                )
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).vintageCard()
                        .semantics { contentDescription = rankDesc },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("#${i + 1} ${entry.spotName}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(
                                        R.string.ts_fav_window_start,
                                        startText,
                                        peakText,
                                        w.durationHours,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            // 设计稿：右侧大号分数（Top10 的核心可比信息）
                            Text(
                                w.peakScore.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            stringResource(R.string.ts_fav_window_peak, w.peakScore, w.tier),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        // Top2 主因（空 → —，禁 0 填充）
                        Text(
                            stringResource(R.string.ts_fav_main_factor) + " " +
                                if (w.topFactors.isEmpty()) Format.MISSING
                                else w.topFactors.take(2).joinToString("、"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        // 注意事项逐条（isEmpty() 判定，CHG-08 ④）
                        if (w.warnings.isNotEmpty()) {
                            w.warnings.forEach {
                                Text(stringResource(R.string.ts_fav_warning, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        CustomTop10RangeDialog(
            zone = zone,
            onDismiss = { showCustomDialog = false },
            onApply = { applied ->
                customRange = applied
                preset = Top10TimeFilter.Preset.ALL
                showCustomDialog = false
            },
        )
    }
}

/** OPT-A-04：Top10 时段 Chip（带无障碍描述）。 */
@Composable
private fun Top10FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/** OPT-A-04：自选时段对话框（复用 core:domain 的解析与错误码，不重造校验）。 */
@Composable
private fun CustomTop10RangeDialog(
    zone: ZoneId,
    onDismiss: () -> Unit,
    onApply: (Top10TimeFilter.TimeRange) -> Unit,
) {
    var dateText by remember { mutableStateOf(java.time.LocalDate.now(zone).toString()) }
    var startText by remember { mutableStateOf("06:00") }
    var endText by remember { mutableStateOf("20:00") }
    var error by remember { mutableStateOf<Top10TimeFilter.CustomError?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ts_fav_filter_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text(stringResource(R.string.ts_fav_filter_custom_date)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text(stringResource(R.string.ts_fav_filter_custom_start)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text(stringResource(R.string.ts_fav_filter_custom_end)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        when (it) {
                            Top10TimeFilter.CustomError.BAD_DATE ->
                                stringResource(R.string.ts_fav_filter_err_date)
                            Top10TimeFilter.CustomError.BAD_TIME ->
                                stringResource(R.string.ts_fav_filter_err_time)
                            Top10TimeFilter.CustomError.END_NOT_AFTER_START ->
                                stringResource(R.string.ts_fav_filter_err_order)
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = Top10TimeFilter.parseCustom(dateText, startText, endText, zone)
                val applied = parsed.range
                if (applied != null) onApply(applied) else error = parsed.error
            }) { Text(stringResource(R.string.ts_fav_filter_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ts_fav_filter_cancel)) }
        },
    )
}
