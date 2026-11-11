package com.tidespot.feature.detail.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tidespot.core.data.repo.DataSource
import com.tidespot.core.data.repo.SourceMetaRegistry
import com.tidespot.core.data.repo.Ttl
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.ui.badge.ConfidenceBadge
import com.tidespot.core.ui.badge.Kind
import com.tidespot.core.ui.banner.OfflineBanner
import com.tidespot.core.ui.band.BandTitles
import com.tidespot.core.ui.band.ScoreBand
import com.tidespot.core.ui.chart.HourChart
import com.tidespot.core.ui.chart.HourChartMode
import com.tidespot.core.ui.breakdown.FactorBreakdown
import com.tidespot.core.ui.breakdown.RawInputRow
import com.tidespot.core.ui.breakdown.factorLabel
import com.tidespot.core.ui.format.Format
import com.tidespot.core.ui.selector.DaySelector
import com.tidespot.core.ui.selector.DaySelectorLogic
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.strings.localDomainCopy
import com.tidespot.core.ui.strings.localTideSpotStrings
import com.tidespot.feature.detail.R
import androidx.compose.ui.res.stringResource
import com.tidespot.core.ui.R as UiR
import com.tidespot.core.ui.titles.WaterBodyTabTitles
import com.tidespot.core.domain.window.BestWindowFinder
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.delay

/**
 * DetailScreen（§8.1）：Tab | DaySelector | 图表⇄表切换 | 摘要 | 分解 | 来源底栏 | 免责 | Top10 入口。
 * 全部交互元素带 contentDescription（N-7 TalkBack）；缺值 `—`，禁 0 填充（§6.7/F-3.3）。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DetailScreen(
    spot: FavoriteSpot,
    viewModel: DetailViewModel,
    modifier: Modifier = Modifier,
    /** OPT-V115-02 / S02 TOPBAR：返回回调（null = 不渲染返回位，零行为漂移）。 */
    onBack: (() -> Unit)? = null,
    /** OPT-V115-02 / S02 TOPBAR：返回 TalkBack 标签（由 :app 按来源页传入）。 */
    backLabel: String = "",
    /** CHG-25（P0-1）：内陆水质首次引导卡槽位（:app 判定条件后注入；null = 不渲染，零行为漂移）。 */
    credentialGuideSlot: (@Composable () -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    val zone: ZoneId = viewModel.zoneId
    // CHG-35：优先用详情自愈结果（历史误判 INLAND/UNKNOWN 的海洋点，首屏即恢复「潮流/NEARSHORE」）
    val effectiveWaterBody = state.result?.effectiveWaterBody ?: spot.waterBody
    val titles = WaterBodyTabTitles.titlesFor(effectiveWaterBody, localTideSpotStrings())
    // M5（F-3.5 / §6.2b）：Top10 与现场校潮 Sheet（模块内接线；:app 导航 M6）
    var showTop10 by remember { mutableStateOf(false) }
    var showCalibration by remember { mutableStateOf(false) }
    // OPT-V11-02（CHG-27）：水质实测录入 Sheet
    var showQualityEntry by remember { mutableStateOf(false) }
    // 全展开（避免半展开态内容被裁、贴底按钮不可点）；无条件声明以满足 Compose slot 表规则
    val qualityEntrySheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // D11：常驻免责入口（CHG-16⑬）
    var showDisclaimer by remember { mutableStateOf(false) }
    // CHG-13/D01：标题数组唯一来源（5 项、与 Tab 顺序一一对应）；禁止手组（原 6 项错位致潮流 Tab 显示「水质」）
    val tabTitles = WaterBodyTabTitles.titlesInTabOrder(titles)

    // 「今天」按选点当地时区判定（F-3.8）；chips 由当前时刻派生。
    // D14 跨零点自愈：心跳对比当地日（DaySelectorLogic.isSameLocalDay），仅跨零点才重算 chips。
    var nowEpochSec by remember(zone) { mutableStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(zone) {
        while (true) {
            delay(60_000)
            val next = System.currentTimeMillis() / 1000L
            if (!DaySelectorLogic.isSameLocalDay(nowEpochSec, next, zone)) nowEpochSec = next
        }
    }
    val dayStrings = localTideSpotStrings()
    val chips = remember(nowEpochSec, zone, state.daysRange, dayStrings) {
        DaySelectorLogic.chips(state.daysRange, zone, nowEpochSec, dayStrings)
    }

    val screenDesc = stringResource(R.string.ts_detail_content_desc)
    val tabsDesc = stringResource(R.string.ts_detail_tabs_desc)
    LazyColumn(modifier = modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().semantics { contentDescription = screenDesc }) {
        // OPT-V115-02 / S02 TOPBAR：返回 + 点位名称 + 坐标副标题（设计层 y=58, h=52）
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = backLabel },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text("‹", style = MaterialTheme.typography.titleLarge) }
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        spot.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        "${Format.coordinate(spot.lat)}, ${Format.coordinate(spot.lon)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        // C17（设计：离线/陈旧条插在 DaySelector 上方）
        item { OfflineBanner() }
        item {
            state.result?.let { StaleBanner(it, zone) }
        }

        item {
            titles.notice?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
            DaySelector(
                chips = chips,
                selectedDay = state.selectedDay,
                onDaySelected = viewModel::selectDay,
                // S02 C22：来源行独立一行（设计 y=152），不再挤在日期行右侧
                infoSlot = {},
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { sourceInfo(state.result?.fetchedAtMillis, zone) }
        }

        item {
            // OPT-V115-02 / S02 C08：设计稿分段控件（容器 358×40 rx12 #EAF1F7，选中白胶囊 rx10）
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(40.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(40.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    listOf(DetailTab.SCORE, DetailTab.TIDE, DetailTab.WEATHER, DetailTab.WATER_QUALITY, DetailTab.WAVE)
                        .forEachIndexed { i, tab ->
                            val tabDesc = stringResource(R.string.ts_detail_tab_desc, tabTitles[i])
                            val selected = state.selectedTab == tab
                            Surface(
                                onClick = { viewModel.selectTab(tab) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                                modifier = Modifier.fillMaxHeight()
                                    .semantics { contentDescription = tabDesc },
                            ) {
                                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    Text(
                                        tabTitles[i],
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                    )
                                }
                            }
                        }
                }
            }
        }

        item {
            val result = state.result
            val status = result?.let { Derivations.tabStatus(state.selectedTab, it, strings = localTideSpotStrings()) } ?: DimensionStatus.Available
            if (status is DimensionStatus.Degraded) {
                // F-3.6：「来源不可用/本点无该来源数据 + 重试 + 最近缓存时间」；其余 Tab 照常
                val retryDesc = stringResource(R.string.ts_detail_retry_desc)
                Card(Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text(status.reason, style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error)
                        Text(
                            stringResource(
                                R.string.ts_detail_last_cache,
                                status.lastFetchedMillis?.let { Format.clockMillis(it, zone) }
                                    ?: Format.MISSING,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = viewModel::retry,
                            modifier = Modifier.semantics { contentDescription = retryDesc }) {
                            Text(stringResource(R.string.ts_detail_retry))
                        }
                    }
                }
            }
            if (state.loading) Text(stringResource(R.string.ts_detail_loading), Modifier.padding(12.dp))
        }

        // OPT-V115-02 / S02 C10：今日指数主卡（设计 366×152，仅指数 Tab）
        item {
            if (state.selectedTab == DetailTab.SCORE) {
                val result = state.result
                if (result != null) {
                    val today = LocalDate.now(zone)
                    val daySlices = Derivations.slicesForDay(result.slices, state.selectedDay, zone, today)
                    val scoresByEpoch = state.scores.associateBy { it.timeEpochSec }
                    val cardValues = Derivations.chartValues(
                        DetailTab.SCORE, daySlices, scoresByEpoch, emptyMap(), state.tideRates,
                    )
                    ScoreMainCard(
                        daySlices = daySlices,
                        values = cardValues,
                        scoresByEpoch = scoresByEpoch,
                        presetWeightSum = viewModel.holderInternal().preset.weightSum,
                        zone = zone,
                    )
                }
            }
        }

        item {
            val result = state.result ?: return@item
            val today = LocalDate.now(zone)
            val daySlices = Derivations.slicesForDay(result.slices, state.selectedDay, zone, today)
            if (daySlices.isEmpty()) {
                Text(stringResource(R.string.ts_detail_no_source), Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall)
                return@item
            }

            var showTable by remember(state.selectedTab, state.selectedDay) { mutableStateOf(false) } // 默认图表
            // OPT-V115-01 #7（ts_chart_table_switch）：双 FilterChip → 单开关，标签=设计稿文案，状态说明沿用模块文案
            // OPT-V115-02 / S02 C09：设计稿分段胶囊切换（容器 #DCE5EE rx12，选中白胶囊 rx10）
            val chartSwitchDesc = stringResource(UiR.string.ts_chart_table_switch)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                // OPT-V115-01 #7：切换控件组语义标签（恢复 ts_chart_table_switch 引用）
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).height(36.dp)
                    .semantics { contentDescription = chartSwitchDesc },
            ) {
                Row(Modifier.fillMaxHeight().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(false to R.string.ts_detail_chart, true to R.string.ts_detail_table)
                        .forEach { (isTable, labelRes) ->
                            val selected = showTable == isTable
                            val label = stringResource(labelRes)
                            Surface(
                                onClick = { showTable = isTable },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                                modifier = Modifier.fillMaxHeight().semantics { contentDescription = label },
                            ) {
                                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                }
            }
            val scoresByEpoch = state.scores.associateBy { it.timeEpochSec }
            val qualityByEpoch = state.quality.associateBy { it.timeEpochSec }
            val values = Derivations.chartValues(state.selectedTab, daySlices, scoresByEpoch, qualityByEpoch, state.tideRates)

            // D14：x 轴当地小时标签（按选点时区/当地日切片派生，注入 HourChart）
            val dayLocalHours = daySlices.map { Derivations.hourLocal(it.timeEpochSec, zone) }

            if (!showTable) {
                // OPT-V115-02 / S02 C09：图表卡容器（设计 366×210）
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    HourChart(
                        values = values,
                        mode = if (state.selectedTab == DetailTab.SCORE) HourChartMode.BAR else HourChartMode.LINE,
                        unit = chartUnit(state.selectedTab, stringResource(R.string.ts_detail_unit_score)),
                        selectedHour = state.selectedHour,
                        onHourSelected = viewModel::selectHour,
                        windowHours = if (state.selectedTab == DetailTab.SCORE)
                            com.tidespot.core.ui.chart.ChartMath.windowHours(values) else emptySet(),
                        localHours = dayLocalHours,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                val rows = Derivations.rowsFor(state.selectedTab, daySlices, zone, state, localTideSpotStrings())
                // CHG-13/D02：外层已是 LazyColumn，此处**禁止**再嵌同向 LazyColumn（无限高约束直接崩溃）；
                // 24 行普通 Column 渲染，滚动由外层承担。
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    rows.forEach { row ->
                        Text(row.valueText, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { contentDescription = row.description })
                    }
                }
            }

            // ③ 当日摘要（极值 + 时刻）
            DailySummaryLine(state.selectedTab, values, daySlices, zone, state)
            // D12（CHG-16⑭）：潮 Tab 高低潮标记 + dt（dh/dt）副轴 + 校正对照
            if (state.selectedTab == DetailTab.TIDE) {
                // UX-23（OPT-V11-23）：海水点潮 Tab 顶部潮汛 chip（大潮/小潮 × 活汛/死汛；内陆不显示）
                if (effectiveWaterBody == com.tidespot.core.domain.model.WaterBodyType.OCEAN ||
                    effectiveWaterBody == com.tidespot.core.domain.model.WaterBodyType.NEARSHORE
                ) {
                    val chipDay = chips.getOrNull(state.selectedDay)?.localDate
                    if (chipDay != null) {
                        val phaseText = TidePhaseChipLogic.chipText(chipDay, zone, localDomainCopy())
                        val phaseDesc = stringResource(R.string.ts_detail_tide_phase, phaseText)
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                AssistChip(
                                    onClick = { },
                                    label = { Text(phaseText) },
                                    modifier = Modifier.semantics { contentDescription = phaseDesc },
                                )
                            }
                    }
                }
                val dayExtremes = TideChartDerivations.dayExtremes(
                    TideChartDerivations.classifiedExtremes(result.marine), state.selectedDay, zone, today)
                TideChartDerivations.extremesLine(dayExtremes, zone, localTideSpotStrings())?.let {
                    Text(it, Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
                HourChart(
                    values = TideChartDerivations.dtSeries(daySlices, state.tideRates),
                    mode = HourChartMode.LINE,
                    unit = "m/h",
                    selectedHour = state.selectedHour,
                    onHourSelected = viewModel::selectHour,
                    localHours = dayLocalHours,
                )
                result.tideCorrection?.let { tc ->
                    TideChartDerivations.correctionCompare(dayExtremes, tc, zone, localTideSpotStrings())?.let { cmp ->
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                            Text(cmp.beforeLine, style = MaterialTheme.typography.bodySmall)
                            Text(cmp.afterLine, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // ② 点小时 → FactorBreakdown（指数 Tab）/ 字段明细（其他 Tab）
            if (state.selectedTab == DetailTab.SCORE && state.selectedHour != null) {
                val epoch = daySlices.getOrNull(state.selectedHour!!)?.timeEpochSec
                val score = epoch?.let { scoresByEpoch[it] }
                if (epoch != null && score != null) {
                    val veto = Derivations.vetoRows(epoch, result.weather.associateBy { it.timeEpochSec },
                        result.marine.associateBy { it.timeEpochSec }, viewModel.holderInternal().doPotentialByEpoch())
                    // OPT-V115-02 / S03：因子分解卡（设计：因子行 / ΠV 明细 / 原始输入三区，卡内）
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        FactorBreakdown(
                            rows = score.factors.map {
                                com.tidespot.core.ui.breakdown.FactorRow(it.id, factorLabel(it.id), it.sub, it.weight, it.detail)
                            },
                            vetoRows = veto,
                            rawInputs = holderRawInputs(viewModel, epoch),
                        )
                    }
                }
            }
            // OPT-V115-02 / S05：天气 Tab 安全摘要卡（免责第 2 条原文完整展示）
            if (state.selectedTab == DetailTab.WEATHER) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.ts_detail_safety_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            localTideSpotStrings().disclaimerWeather,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.selectedTab == DetailTab.WATER_QUALITY) {
                // D10：禁硬编码 ESTIMATED——徽标档位按推导序列 domain confidence 透出。
                // OPT-V11-02（CHG-27）：当日**任一小时**含实测融合（MEASURED）即显示「实测」，否则取首个非空档位。
                val dayConfs = daySlices.mapNotNull { qualityByEpoch[it.timeEpochSec]?.confidence }
                val qConf = if (dayConfs.any { it == com.tidespot.core.domain.model.Confidence.MEASURED }) {
                    com.tidespot.core.domain.model.Confidence.MEASURED
                } else {
                    dayConfs.firstOrNull()
                }
                qConf?.let { conf ->
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        ConfidenceBadge(Kind.fromConfidence(conf))
                        // CHG-26：估算态旁注口径说明（实测融合态不显示，避免自相矛盾）
                        if (conf == com.tidespot.core.domain.model.Confidence.ESTIMATED) {
                            Text(
                                androidx.compose.ui.res.stringResource(UiR.string.ts_water_quality_estimate_note),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
                // OPT-V115-02 / S06 C15：水质 2×2 指标卡（水温/溶氧/浊度/pH；单卡高 112dp）
                val wqSample = daySlices.firstNotNullOfOrNull { qualityByEpoch[it.timeEpochSec] }
                if (wqSample != null) {
                    val wqKind = Kind.fromConfidence(wqSample.confidence)
                    // 客户裁定：删除 pH 指标（领域模型无字段，不虚构），保留三项一行三卡
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WaterQualityMetricCard(
                            stringResource(R.string.ts_detail_wq_label_temp),
                            wqSample.waterTempC?.let { Format.valueUnit(it, "°C") } ?: Format.MISSING,
                            wqKind,
                            Modifier.weight(1f),
                        )
                        WaterQualityMetricCard(
                            stringResource(R.string.ts_detail_wq_label_do),
                            wqSample.doMgL?.let { Format.valueUnit(it, "mg/L") } ?: Format.MISSING,
                            wqKind,
                            Modifier.weight(1f),
                        )
                        WaterQualityMetricCard(
                            stringResource(R.string.ts_detail_wq_label_turb),
                            wqSample.turbidityProxy?.let { Format.valueUnit(it, "") } ?: Format.MISSING,
                            wqKind,
                            Modifier.weight(1f),
                        )
                    }
                }
                // CHG-25（P0-1）：内陆点首次配置引导卡（:app 判定：内陆 + 未配置 + 未跳过）
                credentialGuideSlot?.invoke()
                // OPT-V11-02（CHG-27）：水质实测录入入口（离线可用；水温参与 §6.1.3 融合）
                val wqEntryDesc = stringResource(R.string.ts_detail_wq_entry_desc)
                Button(
                    onClick = { showQualityEntry = true },
                    modifier = Modifier.padding(horizontal = 12.dp)
                        .semantics { contentDescription = wqEntryDesc },
                ) { Text(androidx.compose.ui.res.stringResource(UiR.string.ts_wq_entry_open)) }
            }

            // F-3.7 每 Tab 底部：来源与更新时间（由 SpotDetailResult 元数据生成，禁硬编码来源文案）
            SourceFooter(result, zone, state.selectedTab)
            // §9.6 免责（原文照用）
            Text(disclaimerFor(state.selectedTab, localTideSpotStrings()), Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall)
        }

        item {
            // F-3.5 Top10 入口（窗口数据 = state.top10，onResult 时派生层现算，N-2 同源）
            val top10Desc = stringResource(R.string.ts_detail_top10_desc)
            val calibrationDesc = stringResource(R.string.ts_detail_calibration_desc)
            val disclaimerDesc = stringResource(R.string.ts_detail_disclaimer_desc)
            Button(onClick = { showTop10 = true }, modifier = Modifier.padding(12.dp)
                .semantics { contentDescription = top10Desc }) {
                Text(stringResource(R.string.ts_detail_top10))
            }
            // §6.2b 规则 5 现场校潮入口（潮 Tab 内；CHG-04）
            if (state.selectedTab == DetailTab.TIDE) {
                Button(onClick = { showCalibration = true }, modifier = Modifier.padding(horizontal = 12.dp)
                    .semantics { contentDescription = calibrationDesc }) {
                    Text(stringResource(R.string.ts_detail_calibration))
                }
            }
// OPT-V115-02 / S02 C06：底部常驻免责入口条（设计 366×56）
                Surface(
                    onClick = { showDisclaimer = true },
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .semantics { contentDescription = disclaimerDesc },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(localTideSpotStrings().disclaimerEntry, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "›",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
        }
    }

    // M5（F-3.5）：Top10 Sheet——state.top10 渲染（Top10Formatter 纯函数格式化；单层 LazyColumn 不嵌套）
    if (showTop10) {
        ModalBottomSheet(onDismissRequest = { showTop10 = false }) {
            Top10SheetContent(state.top10, zone, viewModel)
        }
    }
    // OPT-V11-02（CHG-27）：水质实测录入 Sheet（操作按钮置顶；全展开避免半展开态按钮不可点）
    if (showQualityEntry) {
        ModalBottomSheet(
            onDismissRequest = { showQualityEntry = false },
            sheetState = qualityEntrySheetState,
        ) {
            WaterQualityEntrySheet(viewModel = viewModel, zone = zone, onDismiss = { showQualityEntry = false })
        }
    }
    // M5（§6.2b 规则 5）：现场校潮 Sheet（观测 Session 级存 DetailViewModel；持久化登记 M6）
    if (showCalibration) {
        ModalBottomSheet(onDismissRequest = { showCalibration = false }) {
            CalibrationSheetContent(viewModel, state, zone)
        }
    }
    // D11：免责 Sheet（§9.6 共 5 条，含新增第 5 条「潮汐校正」；TideSpotStrings 单源）
    if (showDisclaimer) {
        ModalBottomSheet(onDismissRequest = { showDisclaimer = false }) {
            Column(Modifier.padding(12.dp).navigationBarsPadding()) {
                Text(localTideSpotStrings().disclaimerTitle, style = MaterialTheme.typography.titleMedium)
                localTideSpotStrings().disclaimerItems().forEachIndexed { i, line ->
                    Text("${i + 1}. $line", Modifier.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Top10 Sheet 内容（F-3.5）：有窗口逐条渲染（起始/峰值/时长/峰值分+档位/Top2 主因/注意事项），无窗口走兜底文案。 */
@Composable
private fun Top10SheetContent(output: BestWindowFinder.Output?, zone: ZoneId, viewModel: DetailViewModel) {
    // OPT-V11-03（CHG-27）：时段筛选（ALL=不筛选 → 与全窗恒等；预设/自选走 top10Filtered 重算）
    var preset by remember { mutableStateOf(Top10TimeFilter.Preset.ALL) }
    var customRange by remember { mutableStateOf<Top10TimeFilter.TimeRange?>(null) }
    var showCustom by remember { mutableStateOf(false) }
    // OPT-V11-06：相对时间标签的「现在」（Sheet 打开时定格，避免逐帧抖动）
    val nowEpochSec = remember { System.currentTimeMillis() / 1000L }
    val shown: BestWindowFinder.Output? = when {
        output == null -> null
        preset == Top10TimeFilter.Preset.ALL -> output
        preset == Top10TimeFilter.Preset.THIS_WEEKEND || preset == Top10TimeFilter.Preset.NEXT_24H -> {
            val range = Top10TimeFilter.presetRange(preset, System.currentTimeMillis() / 1000L, zone)
            if (range == null) output else viewModel.top10Filtered(range.startEpochSec, range.endEpochSec)
        }
        else -> customRange?.let { viewModel.top10Filtered(it.startEpochSec, it.endEpochSec) }
            ?: output
    }
    Column(Modifier.padding(12.dp).navigationBarsPadding()) {
        Text(stringResource(UiR.string.ts_top10_this_spot), style = MaterialTheme.typography.titleMedium)
        val allDesc = stringResource(R.string.ts_detail_filter_all_desc)
        val h24Desc = stringResource(R.string.ts_detail_filter_24h_desc)
        val weekendDesc = stringResource(R.string.ts_detail_filter_weekend_desc)
        val customDesc = stringResource(R.string.ts_detail_filter_custom_desc)
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = preset == Top10TimeFilter.Preset.ALL,
                    onClick = { preset = Top10TimeFilter.Preset.ALL },
                    label = { Text(stringResource(R.string.ts_detail_filter_all)) },
                    modifier = Modifier.semantics { contentDescription = allDesc })
                FilterChip(selected = preset == Top10TimeFilter.Preset.NEXT_24H,
                    onClick = { preset = Top10TimeFilter.Preset.NEXT_24H },
                    label = { Text(stringResource(R.string.ts_detail_filter_24h)) },
                    modifier = Modifier.semantics { contentDescription = h24Desc })
                FilterChip(selected = preset == Top10TimeFilter.Preset.THIS_WEEKEND,
                    onClick = { preset = Top10TimeFilter.Preset.THIS_WEEKEND },
                    label = { Text(stringResource(R.string.ts_detail_filter_weekend)) },
                    modifier = Modifier.semantics { contentDescription = weekendDesc })
                FilterChip(selected = customRange != null,
                    onClick = { showCustom = true },
                    label = { Text(stringResource(R.string.ts_detail_filter_custom)) },
                    modifier = Modifier.semantics { contentDescription = customDesc })
            }
        }
        if (showCustom) {
            CustomTimeRangeDialog(
                zone = zone,
                onDismiss = { showCustom = false },
                onApply = { range ->
                    preset = Top10TimeFilter.Preset.ALL
                    customRange = range
                    showCustom = false
                },
            )
        }
        when {
            shown == null -> Text(
                stringResource(R.string.ts_detail_data_pending),
                Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            shown.windows.isEmpty() -> {
                val noWindowDesc = stringResource(R.string.ts_detail_no_window_desc)
                Text(
                    Top10Formatter.fallbackLine(
                        shown.fallbackMessage ?: stringResource(UiR.string.ts_top10_empty),
                        shown.fallbackBestEpochSec,
                        zone,
                    ),
                    Modifier.padding(vertical = 12.dp)
                        .semantics { contentDescription = noWindowDesc },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> LazyColumn(Modifier.padding(vertical = 8.dp)) {
                items(shown.windows.size) { i ->
                    val w = shown.windows[i]
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    "#${i + 1}  ${Top10Formatter.peakScoreLine(w, localTideSpotStrings())}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                // OPT-V11-26（CHG-27）：≥80 爆护标识（档位色复用 ScoreBands，禁新硬编码色值）
                                Top10Formatter.burstBadge(w, localTideSpotStrings())?.let { badge ->
                                    Box(
                                        Modifier.padding(start = 6.dp)
                                            .background(Color(ScoreBand.EXPLOSIVE.argb))
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(ScoreBand.EXPLOSIVE.onArgb),
                                        )
                                    }
                                }
                            }
                            Text(
                                Top10Formatter.startLine(w, zone, nowEpochSec, localTideSpotStrings()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            // OPT-V11-25（CHG-27）：核心段短于原窗时标注利好延续时刻（null 不显示）
                            Top10Formatter.sustainedLine(w, zone, localTideSpotStrings())?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall)
                            }
                            Text(
                                stringResource(R.string.ts_detail_top_factor, Top10Formatter.topFactorsLine(w.topFactors)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            // 注意事项：非空才显示（isEmpty() 判定，CHG-08④）
                            if (!w.warnings.isEmpty()) {
                                w.warnings.forEach { reason ->
                                    Text(
                                        stringResource(R.string.ts_detail_warning, reason),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * OPT-V11-03（CHG-27）：自选时段对话框（日期 yyyy-MM-dd + 起/止 HH:mm，当地时区语义）。
 * 校验走 [Top10TimeFilter.parseCustom] 纯函数（错误码 → 提示）。
 */
@Composable
private fun CustomTimeRangeDialog(
    zone: ZoneId,
    onDismiss: () -> Unit,
    onApply: (Top10TimeFilter.TimeRange) -> Unit,
) {
    val today = java.time.LocalDate.now(zone).toString()
    var dateText by remember { mutableStateOf(today) }
    var startText by remember { mutableStateOf("06:00") }
    var endText by remember { mutableStateOf("20:00") }
    var error by remember { mutableStateOf<Top10TimeFilter.CustomError?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ts_detail_custom_title)) },
        text = {
            Column {
                val dateDesc = stringResource(R.string.ts_detail_custom_date_desc)
                val startDesc = stringResource(R.string.ts_detail_custom_start_desc)
                val endDesc = stringResource(R.string.ts_detail_custom_end_desc)
                OutlinedTextField(value = dateText, onValueChange = { dateText = it },
                    label = { Text(stringResource(R.string.ts_detail_custom_date)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = dateDesc })
                OutlinedTextField(value = startText, onValueChange = { startText = it },
                    label = { Text(stringResource(R.string.ts_detail_custom_start)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = startDesc })
                OutlinedTextField(value = endText, onValueChange = { endText = it },
                    label = { Text(stringResource(R.string.ts_detail_custom_end)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = endDesc })
                error?.let {
                    Text(
                        when (it) {
                            Top10TimeFilter.CustomError.BAD_DATE -> stringResource(R.string.ts_detail_err_date_format)
                            Top10TimeFilter.CustomError.BAD_TIME -> stringResource(R.string.ts_detail_err_time_format)
                            Top10TimeFilter.CustomError.END_NOT_AFTER_START ->
                                stringResource(R.string.ts_detail_err_end_before_start)
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = Top10TimeFilter.parseCustom(dateText, startText, endText, zone)
                if (parsed.range == null) error = parsed.error else onApply(parsed.range)
            }) { Text(stringResource(R.string.ts_detail_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ts_cancel)) } },
    )
}

/**
 * 现场校潮 Sheet（§6.2b 规则 5 / OPT-V11-07）：
 * 四类观测（高潮/低潮/开始涨/开始退）+ 时刻可补记 + 模型标记对照 → fitManual（≥2 条有效才启用，UI 只透传）。
 * 操作按钮置顶（GAP-03 教训：Sheet 底部手势条区域触摸不可靠）。
 */
@Composable
private fun CalibrationSheetContent(
    viewModel: DetailViewModel,
    state: DetailUiState,
    zone: ZoneId,
) {
    val observations by viewModel.manualObservations.collectAsState()
    val marine = state.result?.marine ?: emptyList()
    val markers = remember(marine) { TopWindowDerivations.tideMarkers(marine) }
    // 观测（类型 + 时刻）变化或潮位数据变化时重算拟合（纯函数；持久化经 repository 落库）
    val fit = remember(observations, state.result) {
        TopWindowDerivations.manualFitTyped(observations.map { it.type to it.epochSec }, marine)
    }
    val nowEpochSec = remember { System.currentTimeMillis() / 1000L }
    var selectedType by remember { mutableStateOf(com.tidespot.core.domain.tide.TideObservationType.HIGH) }
    var timeText by remember(zone) {
        mutableStateOf(TideObservationEntryLogic.defaultText(nowEpochSec, zone))
    }
    var error by remember { mutableStateOf<TideObservationEntryLogic.ParseError?>(null) }
    val parsed = TideObservationEntryLogic.parse(timeText, zone, nowEpochSec)

    Column(Modifier.padding(12.dp).navigationBarsPadding()) {
        Text(stringResource(R.string.ts_detail_calibration), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.ts_detail_calib_body),
            Modifier.padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        // ① 操作区置顶：类型 chips
        Text(
            stringResource(R.string.ts_detail_obs_type),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.tidespot.core.domain.tide.TideObservationType.entries.forEach { type ->
                val typeLabel = localDomainCopy().observationTypeLabel(type)
                val typeDesc = stringResource(R.string.ts_detail_obs_type_desc, typeLabel)
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(typeLabel) },
                    modifier = Modifier.semantics { contentDescription = typeDesc },
                )
            }
        }
        // ② 时刻（默认当前，可补记）
        val obsTimeDesc = stringResource(R.string.ts_detail_obs_time_input)
        OutlinedTextField(
            value = timeText,
            onValueChange = { timeText = it; error = null },
            label = { Text(stringResource(R.string.ts_detail_obs_time)) },
            singleLine = true,
            isError = error != null,
            supportingText = { error?.let { Text(TideObservationEntryLogic.errorText(it)) } },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = obsTimeDesc },
        )
        // ③ 模型对照（辅助对齐；无 ±6h 匹配 → 不显示，禁编造）
        parsed.epochSec?.let { epoch ->
            TideObservationEntryLogic.comparisonLine(markers, selectedType, epoch, zone)?.let {
                Text(it, Modifier.padding(vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        val obsRecordDesc = stringResource(R.string.ts_detail_obs_record)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val value = parsed.epochSec
                    if (value == null) error = parsed.error else viewModel.recordObservation(selectedType, value)
                },
                modifier = Modifier.semantics { contentDescription = obsRecordDesc },
            ) { Text(stringResource(R.string.ts_detail_obs_record_short)) }
            if (!observations.isEmpty()) {
                Button(onClick = { viewModel.clearObservations() }) { Text(stringResource(R.string.ts_detail_obs_clear)) }
            }
        }
        Text(
            stringResource(R.string.ts_detail_obs_count, observations.size) +
                observations.lastOrNull()?.let {
                    stringResource(R.string.ts_detail_obs_latest, Top10Formatter.time(it.epochSec, zone, localTideSpotStrings()))
                }.orEmpty(),
            Modifier.padding(vertical = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        // OPT-V115-01 #3（ts_record_high_tide）：校潮 Sheet 空态引导句（替代空白）
        if (observations.isEmpty()) {
            val emptyHint = stringResource(UiR.string.ts_record_high_tide)
            Text(
                emptyHint,
                Modifier.padding(vertical = 2.dp).semantics { contentDescription = emptyHint },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // ④ 已记录列表（类型 + 时刻）
        observations.takeLast(5).reversed().forEach { o ->
            Text(
                TideObservationEntryLogic.observationLine(o.type, o.epochSec, zone),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            stringResource(R.string.ts_detail_fit_result, Top10Formatter.calibrationLine(fit, localTideSpotStrings())),
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        // M6：拟合启用 → saveTideFit 持久化 + force 刷新（tideCorrection 透出 → 潮 Tab「校正前/后」对照）
        if (fit.adopted) {
            val saveDesc = stringResource(R.string.ts_detail_calib_save)
            Button(
                onClick = { viewModel.saveTideFit(fit) },
                modifier = Modifier.padding(vertical = 8.dp)
                    .semantics { contentDescription = saveDesc },
            ) { Text(stringResource(R.string.ts_detail_calib_save_short)) }
        }
    }
}

@Composable
private fun sourceInfo(fetched: Map<DataSource, Long>?, zone: ZoneId) {
    // §8.2-1 右侧槽位：「源更新 HH:mm · 本机拉取 HH:mm」；源端更新时刻数据层未透出（上报项）→ 源更新显示 —
    val pull = fetched?.values?.maxOrNull()?.let { Format.clockMillis(it, zone) } ?: Format.MISSING
    Text(
        stringResource(R.string.ts_detail_source_update, Format.MISSING, pull),
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun StaleBanner(result: com.tidespot.core.data.repo.SpotDetailResult, zone: ZoneId) {
    val now = remember { System.currentTimeMillis() }
    // CHG-13/N15（§6.7 第 4 行）：三态 —— 新鲜 / 过期可看（黄条）/ ≥48h 不可用（不再据此评分）
    val weatherUnusable = Ttl.unusableAgeMillis(result.fetchedAtMillis[com.tidespot.core.data.repo.DataSource.WEATHER] ?: 0L, now)
    val marineUnusable = Ttl.unusableAgeMillis(result.fetchedAtMillis[com.tidespot.core.data.repo.DataSource.MARINE] ?: 0L, now)
    if (weatherUnusable != null || marineUnusable != null) {
        val ageH = maxOf(weatherUnusable ?: 0L, marineUnusable ?: 0L).let { (it + 3_599_999) / 3_600_000 }
        val expiredDesc = stringResource(R.string.ts_detail_expired_48h)
        Text(
            stringResource(
                R.string.ts_detail_unusable,
                ageH,
                result.fetchedAtMillis.values.maxOrNull()?.let { Format.clockMillis(it, zone) } ?: Format.MISSING,
            ),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp)
                .semantics { contentDescription = expiredDesc },
        )
    } else {
        val staleWeather = Ttl.staleForMillis(com.tidespot.core.data.repo.DataSource.WEATHER, result.fetchedAtMillis[com.tidespot.core.data.repo.DataSource.WEATHER] ?: 0L, now)
        val staleMarine = Ttl.staleForMillis(com.tidespot.core.data.repo.DataSource.MARINE, result.fetchedAtMillis[com.tidespot.core.data.repo.DataSource.MARINE] ?: 0L, now)
        if (staleWeather != null || staleMarine != null) {
            // 文案「数据时间 X，已过期」由 Ttl 计算，不硬编码
            val staleDesc = stringResource(R.string.ts_detail_expired_desc)
            Text(
                // OPT-V115-01 #5（ts_stale）：黄条统一为「数据已过期 · 显示上次成功拉取结果 · 数据时间 X」
                stringResource(
                    UiR.string.ts_stale_with_time,
                    stringResource(UiR.string.ts_stale),
                    result.fetchedAtMillis.values.maxOrNull()?.let { Format.clockMillis(it, zone) } ?: Format.MISSING,
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
                    .semantics { contentDescription = staleDesc },
            )
        }
    }
}

@Composable
private fun DailySummaryLine(
    tab: DetailTab,
    values: List<Double?>,
    daySlices: List<com.tidespot.core.data.repo.HourSlice>,
    zone: ZoneId,
    state: DetailUiState,
) {
    val unit = chartUnit(tab, stringResource(R.string.ts_detail_unit_score))
    val summary = Derivations.summary(daySlices.map { it.timeEpochSec to values.getOrNull(daySlices.indexOf(it)) })
    if (summary == null) {
        Text(stringResource(R.string.ts_detail_summary_empty), Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall)
        return
    }
    val summaryDesc = stringResource(
        R.string.ts_detail_summary_plain,
        Format.value(summary.max),
        Format.hourLocal(summary.maxEpochSec, zone),
        Format.value(summary.min),
        Format.hourLocal(summary.minEpochSec, zone),
    )
    Text(
        stringResource(
            R.string.ts_detail_summary_unit,
            Format.valueUnit(summary.max, unit),
            Format.hourLocal(summary.maxEpochSec, zone),
            Format.valueUnit(summary.min, unit),
            Format.hourLocal(summary.minEpochSec, zone),
        ),
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics { contentDescription = summaryDesc },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SourceFooter(result: com.tidespot.core.data.repo.SpotDetailResult, zone: ZoneId, tab: DetailTab) {
    val w = result.fetchedAtMillis[DataSource.WEATHER]?.takeIf { it > 0 }
    val m = result.fetchedAtMillis[DataSource.MARINE]?.takeIf { it > 0 }
    // OPT-V11-04（CHG-27）：模型名/分辨率/粒度取契约映射表（禁 Screen 硬编码）；禁编造源更新时间
    val marineLine = m?.let {
        stringResource(
            R.string.ts_detail_marine_source,
            SourceMetaRegistry.line(DataSource.MARINE, localDomainCopy()),
            Format.clockMillis(it, zone),
        )
    }
    val weatherLine = w?.let {
        stringResource(
            R.string.ts_detail_weather_source,
            SourceMetaRegistry.line(DataSource.WEATHER, localDomainCopy()),
            Format.clockMillis(it, zone),
        )
    }
    val cacheLine = if (result.servedFromCache.isNotEmpty()) stringResource(R.string.ts_detail_stale_cache) else null
    val lines = buildList {
        if (tab != DetailTab.SCORE && tab != DetailTab.WATER_QUALITY && marineLine != null) add(marineLine)
        if (weatherLine != null) add(weatherLine)
        if (cacheLine != null) add(cacheLine)
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        lines.forEach {
            Text(it, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun holderRawInputs(viewModel: DetailViewModel, epochSec: Long): List<RawInputRow> =
    viewModel.holderInternal().rawInputsFor(epochSec)

private fun chartUnit(tab: DetailTab, scoreUnit: String): String = when (tab) {
    DetailTab.WAVE -> "m"
    DetailTab.WEATHER -> "°C"
    DetailTab.SCORE -> scoreUnit
    DetailTab.WATER_QUALITY -> "°C"
    DetailTab.TIDE -> "m"
}

private fun disclaimerFor(tab: DetailTab, strings: TideSpotStrings): String = when (tab) {
    DetailTab.WAVE, DetailTab.TIDE -> strings.disclaimerWaveTide
    DetailTab.WEATHER -> strings.disclaimerWeather
    DetailTab.SCORE -> strings.disclaimerScore
    DetailTab.WATER_QUALITY -> strings.disclaimerWaterQuality
}
/**
 * OPT-V115-02 / S02 C10：今日指数主卡（设计 366×152 · 深蓝 #0D47A1 · rx16）。
 * 内部：大号分数 + 档位文字 + 档位胶囊（冻结 ScoreBand 色）+ 五段色带图例。
 */
@Composable
private fun ScoreMainCard(
    daySlices: List<com.tidespot.core.data.repo.HourSlice>,
    values: List<Double?>,
    scoresByEpoch: Map<Long, com.tidespot.core.domain.model.FishScoreResult>,
    presetWeightSum: Double,
    zone: ZoneId,
) {
    val validIdx = values.indices.filter { values[it] != null }
    val peakIdx = validIdx.maxByOrNull { values[it]!! }
    val score = peakIdx?.let { values[it] }?.toInt()?.coerceIn(0, 100)
    val band = score?.let { ScoreBand.of(it) }
    val bandLabel = score?.let { BandTitles.fromDomainTier(it, localTideSpotStrings()) }
    val kind = peakIdx
        ?.let { daySlices.getOrNull(it)?.timeEpochSec }
        ?.let { scoresByEpoch[it] }
        ?.let { ScoreBadgeDerivations.badgeKind(it, presetWeightSum) }

    Surface(
        color = Color(0xFF0D47A1),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).height(152.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column {
                    Text(
                        score?.toString() ?: Format.MISSING,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 48.sp, fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    if (bandLabel == null) {
                        Text(
                            stringResource(R.string.ts_detail_summary_empty),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                if (band != null && bandLabel != null) {
                    Surface(color = Color(band.argb), shape = RoundedCornerShape(12.dp)) {
                        Text(
                            bandLabel,
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(band.onArgb),
                        )
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            if (kind != null) {
                ConfidenceBadge(kind)
                if (kind == Kind.LOW_CONFIDENCE) {
                    // OPT-V115-01 #6（ts_dimension_degraded）：低置信 → 有效权重重算说明
                    Text(
                        stringResource(UiR.string.ts_dimension_degraded),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ScoreBand.entries.reversed().forEach { b ->
                    Box(
                        Modifier.weight(1f).height(8.dp)
                            .background(Color(b.argb), RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
    }
}
/** OPT-V115-02 / S06 C15：水质指标卡（标签 / 值 / 置信徽标；单卡高 112dp，禁裁切）。 */
@Composable
private fun WaterQualityMetricCard(
    label: String,
    value: String,
    kind: Kind?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.height(112.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.titleLarge)
            if (kind != null) ConfidenceBadge(kind)
        }
    }
}