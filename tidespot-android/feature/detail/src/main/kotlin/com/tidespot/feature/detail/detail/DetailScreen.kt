package com.tidespot.feature.detail.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tidespot.core.data.repo.DataSource
import com.tidespot.core.data.repo.Ttl
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.ui.badge.ConfidenceBadge
import com.tidespot.core.ui.badge.Kind
import com.tidespot.core.ui.chart.HourChart
import com.tidespot.core.ui.chart.HourChartMode
import com.tidespot.core.ui.breakdown.FactorBreakdown
import com.tidespot.core.ui.breakdown.RawInputRow
import com.tidespot.core.ui.breakdown.factorLabel
import com.tidespot.core.ui.format.Format
import com.tidespot.core.ui.selector.DaySelector
import com.tidespot.core.ui.selector.DaySelectorLogic
import com.tidespot.core.ui.strings.TideSpotStrings
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
) {
    val state by viewModel.state.collectAsState()
    val zone: ZoneId = viewModel.zoneId
    val titles = WaterBodyTabTitles.titlesFor(spot.waterBody)
    // M5（F-3.5 / §6.2b）：Top10 与现场校潮 Sheet（模块内接线；:app 导航 M6）
    var showTop10 by remember { mutableStateOf(false) }
    var showCalibration by remember { mutableStateOf(false) }
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
    val chips = remember(nowEpochSec, zone, state.daysRange) {
        DaySelectorLogic.chips(state.daysRange, zone, nowEpochSec)
    }

    LazyColumn(modifier = modifier.fillMaxSize().semantics { contentDescription = "详情页：五维度逐小时数据" }) {
        item {
            titles.notice?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
            TabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                modifier = Modifier.semantics { contentDescription = "五维度标签页" },
            ) {
                listOf(DetailTab.WAVE, DetailTab.WEATHER, DetailTab.SCORE, DetailTab.WATER_QUALITY, DetailTab.TIDE)
                    .forEachIndexed { i, tab ->
                        Tab(
                            selected = state.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = { Text(tabTitles[i]) },
                            modifier = Modifier.semantics { contentDescription = "${tabTitles[i]}标签页" },
                        )
                    }
            }
            DaySelector(
                chips = chips,
                selectedDay = state.selectedDay,
                onDaySelected = viewModel::selectDay,
                infoSlot = { sourceInfo(state.result?.fetchedAtMillis, zone) },
            )
        }

        item {
            val result = state.result
            if (result != null) {
                // §6.7：缓存超 TTL → 顶部黄条「数据时间 X，已过期」
                StaleBanner(result, zone)
            }
        }

        item {
            val result = state.result
            val status = result?.let { Derivations.tabStatus(state.selectedTab, it) } ?: DimensionStatus.Available
            if (status is DimensionStatus.Degraded) {
                // F-3.6：「来源不可用/本点无该来源数据 + 重试 + 最近缓存时间」；其余 Tab 照常
                Card(Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text(status.reason, style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error)
                        Text("最近缓存：${status.lastFetchedMillis?.let { Format.clockMillis(it, zone) } ?: "—"}",
                            style = MaterialTheme.typography.bodySmall)
                        Button(onClick = viewModel::retry,
                            modifier = Modifier.semantics { contentDescription = "重试加载该维度数据" }) {
                            Text("重试")
                        }
                    }
                }
            }
            if (state.loading) Text("加载中…", Modifier.padding(12.dp))
        }

        item {
            val result = state.result ?: return@item
            val today = LocalDate.now(zone)
            val daySlices = Derivations.slicesForDay(result.slices, state.selectedDay, zone, today)
            if (daySlices.isEmpty()) {
                Text("本点无该来源数据", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                return@item
            }

            var showTable by remember(state.selectedTab, state.selectedDay) { mutableStateOf(false) } // 默认图表
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !showTable, onClick = { showTable = false },
                    label = { Text("图表") },
                    modifier = Modifier.semantics { contentDescription = "切换为逐小时图表" })
                FilterChip(selected = showTable, onClick = { showTable = true },
                    label = { Text("数值表") },
                    modifier = Modifier.semantics { contentDescription = "切换为逐小时数值表（TalkBack 可读）" })
            }

            val scoresByEpoch = state.scores.associateBy { it.timeEpochSec }
            val qualityByEpoch = state.quality.associateBy { it.timeEpochSec }
            val values = Derivations.chartValues(state.selectedTab, daySlices, scoresByEpoch, qualityByEpoch, state.tideRates)

            // D14：x 轴当地小时标签（按选点时区/当地日切片派生，注入 HourChart）
            val dayLocalHours = daySlices.map { Derivations.hourLocal(it.timeEpochSec, zone) }

            if (!showTable) {
                HourChart(
                    values = values,
                    mode = if (state.selectedTab == DetailTab.SCORE) HourChartMode.BAR else HourChartMode.LINE,
                    unit = chartUnit(state.selectedTab),
                    selectedHour = state.selectedHour,
                    onHourSelected = viewModel::selectHour,
                    windowHours = if (state.selectedTab == DetailTab.SCORE)
                        com.tidespot.core.ui.chart.ChartMath.windowHours(values) else emptySet(),
                    localHours = dayLocalHours,
                )
            } else {
                val rows = Derivations.rowsFor(state.selectedTab, daySlices, zone, state)
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
                val dayExtremes = TideChartDerivations.dayExtremes(
                    TideChartDerivations.classifiedExtremes(result.marine), state.selectedDay, zone, today)
                TideChartDerivations.extremesLine(dayExtremes, zone)?.let {
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
                    TideChartDerivations.correctionCompare(dayExtremes, tc, zone)?.let { cmp ->
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                            Text(cmp.beforeLine, style = MaterialTheme.typography.bodySmall)
                            Text(cmp.afterLine, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // D10/OPT-M5-05（CHG-16⑫）：数值区徽标接 domain confidence；§6.7 权重降级 →「低置信」
            val presetWeightSum = viewModel.holderInternal().preset.weightSum
            if (state.selectedTab == DetailTab.SCORE) {
                val badgeScore = state.selectedHour?.let { idx ->
                    daySlices.getOrNull(idx)?.timeEpochSec?.let { scoresByEpoch[it] }
                } ?: daySlices.firstNotNullOfOrNull { scoresByEpoch[it.timeEpochSec] }
                ScoreBadgeDerivations.badgeKind(badgeScore, presetWeightSum)?.let { kind ->
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) { ConfidenceBadge(kind) }
                }
            }

            // ② 点小时 → FactorBreakdown（指数 Tab）/ 字段明细（其他 Tab）
            if (state.selectedTab == DetailTab.SCORE && state.selectedHour != null) {
                val epoch = daySlices.getOrNull(state.selectedHour!!)?.timeEpochSec
                val score = epoch?.let { scoresByEpoch[it] }
                if (epoch != null && score != null) {
                    val veto = Derivations.vetoRows(epoch, result.weather.associateBy { it.timeEpochSec },
                        result.marine.associateBy { it.timeEpochSec }, viewModel.holderInternal().doPotentialByEpoch())
                    FactorBreakdown(
                        rows = score.factors.map {
                            com.tidespot.core.ui.breakdown.FactorRow(it.id, factorLabel(it.id), it.sub, it.weight, it.detail)
                        },
                        vetoRows = veto,
                        rawInputs = holderRawInputs(viewModel, epoch),
                    )
                }
            }
            if (state.selectedTab == DetailTab.WATER_QUALITY) {
                // D10：禁硬编码 ESTIMATED——徽标档位按推导序列 domain confidence 透出
                val qConf = daySlices.firstNotNullOfOrNull { qualityByEpoch[it.timeEpochSec]?.confidence }
                qConf?.let { conf ->
                    Row(Modifier.padding(horizontal = 12.dp)) { ConfidenceBadge(Kind.fromConfidence(conf)) }
                }
            }

            // F-3.7 每 Tab 底部：来源与更新时间（由 SpotDetailResult 元数据生成，禁硬编码来源文案）
            SourceFooter(result, zone, state.selectedTab)
            // §9.6 免责（原文照用）
            Text(disclaimerFor(state.selectedTab), Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall)
        }

        item {
            // F-3.5 Top10 入口（窗口数据 = state.top10，onResult 时派生层现算，N-2 同源）
            Button(onClick = { showTop10 = true }, modifier = Modifier.padding(12.dp)
                .semantics { contentDescription = "打开未来最佳窗口前十名" }) {
                Text("最佳窗口 Top10")
            }
            // §6.2b 规则 5 现场校潮入口（潮 Tab 内；CHG-04）
            if (state.selectedTab == DetailTab.TIDE) {
                Button(onClick = { showCalibration = true }, modifier = Modifier.padding(horizontal = 12.dp)
                    .semantics { contentDescription = "打开现场校潮面板" }) {
                    Text("现场校潮")
                }
            }
            // D11：常驻免责入口（全 Tab 可见；Sheet 复用既有 ModalBottomSheet 模式）
            TextButton(
                onClick = { showDisclaimer = true },
                modifier = Modifier.padding(horizontal = 12.dp)
                    .semantics { contentDescription = "打开免责声明（共 5 条）" },
            ) {
                Text(TideSpotStrings.DISCLAIMER_ENTRY)
            }
        }
    }

    // M5（F-3.5）：Top10 Sheet——state.top10 渲染（Top10Formatter 纯函数格式化；单层 LazyColumn 不嵌套）
    if (showTop10) {
        ModalBottomSheet(onDismissRequest = { showTop10 = false }) {
            Top10SheetContent(state.top10, zone)
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
            Column(Modifier.padding(12.dp)) {
                Text(TideSpotStrings.DISCLAIMER_SHEET_TITLE, style = MaterialTheme.typography.titleMedium)
                TideSpotStrings.DISCLAIMER_ITEMS.forEachIndexed { i, line ->
                    Text("${i + 1}. $line", Modifier.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Top10 Sheet 内容（F-3.5）：有窗口逐条渲染（起始/峰值/时长/峰值分+档位/Top2 主因/注意事项），无窗口走兜底文案。 */
@Composable
private fun Top10SheetContent(output: BestWindowFinder.Output?, zone: ZoneId) {
    Column(Modifier.padding(12.dp)) {
        Text("未来最佳窗口 Top10", style = MaterialTheme.typography.titleMedium)
        when {
            output == null -> Text(
                "数据未到达，加载中…",
                Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            output.windows.isEmpty() -> Text(
                Top10Formatter.fallbackLine(output.fallbackMessage ?: "暂无 ≥60 分窗口", output.fallbackBestEpochSec, zone),
                Modifier.padding(vertical = 12.dp)
                    .semantics { contentDescription = "未来无达标窗口提示" },
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> LazyColumn(Modifier.padding(vertical = 8.dp)) {
                items(output.windows.size) { i ->
                    val w = output.windows[i]
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "#${i + 1}  ${Top10Formatter.peakScoreLine(w)}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "起始 ${Top10Formatter.time(w.startEpochSec, zone)} · " +
                                    "峰值 ${Top10Formatter.hm(w.peakEpochSec, zone)} · " +
                                    "时长 ${Top10Formatter.duration(w.durationHours)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "主因 ${Top10Formatter.topFactorsLine(w.topFactors)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            // 注意事项：非空才显示（isEmpty() 判定，CHG-08④）
                            if (!w.warnings.isEmpty()) {
                                w.warnings.forEach { reason ->
                                    Text(
                                        "注意 $reason",
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

/** 现场校潮 Sheet（§6.2b 规则 5）：记录观测 → fitManual（≥2 条有效才启用，UI 只透传）。 */
@Composable
private fun CalibrationSheetContent(
    viewModel: DetailViewModel,
    state: DetailUiState,
    zone: ZoneId,
) {
    val observations by viewModel.manualObservations.collectAsState()
    val marine = state.result?.marine ?: emptyList()
    // 观测数量或潮位数据变化时重算拟合（纯函数；持久化经 saveTideFit 走 repository，M6 起）
    val fit = remember(observations.size, observations.lastOrNull(), state.result) {
        TopWindowDerivations.manualFit(observations, marine)
    }
    Column(Modifier.padding(12.dp)) {
        Text("现场校潮", style = MaterialTheme.typography.titleMedium)
        Text(
            "在现场记录高潮时刻（≥2 条、与模型极值偏差 ±6h 内才启用；只校正滞后 lag）",
            Modifier.padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            if (observations.isEmpty()) "已记录 0 条" else
                "已记录 ${observations.size} 条，最近 ${Top10Formatter.time(observations.last(), zone)}",
            Modifier.padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.recordObservation() }) { Text("记录一条高潮观测") }
            if (!observations.isEmpty()) {
                Button(onClick = { viewModel.clearObservations() }) { Text("清空") }
            }
        }
        Text(
            "拟合结果：${Top10Formatter.calibrationLine(fit)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        // M6：拟合启用 → saveTideFit 持久化 + force 刷新（tideCorrection 透出 → 潮 Tab「校正前/后」对照）
        if (fit.adopted) {
            Button(
                onClick = { viewModel.saveTideFit(fit) },
                modifier = Modifier.padding(vertical = 8.dp)
                    .semantics { contentDescription = "保存并应用现场校潮" },
            ) { Text("保存并应用校潮") }
        }
    }
}

@Composable
private fun sourceInfo(fetched: Map<DataSource, Long>?, zone: ZoneId) {
    // §8.2-1 右侧槽位：「源更新 HH:mm · 本机拉取 HH:mm」；源端更新时刻数据层未透出（上报项）→ 源更新显示 —
    val pull = fetched?.values?.maxOrNull()?.let { Format.clockMillis(it, zone) } ?: "—"
    Text("源更新 ${Format.MISSING} · 本机拉取 $pull", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun StaleBanner(result: com.tidespot.core.data.repo.SpotDetailResult, zone: ZoneId) {
    val now = remember { System.currentTimeMillis() }
    // CHG-13/N15（§6.7 第 4 行）：三态 —— 新鲜 / 过期可看（黄条）/ ≥48h 不可用（不再据此评分）
    val weatherUnusable = Ttl.unusableAgeMillis(result.fetchedAtMillis[com.tidespot.core.data.repo.DataSource.WEATHER] ?: 0L, now)
    val marineUnusable = Ttl.unusableAgeMillis(result.fetchedAtMillis[com.tidespot.core.data.repo.DataSource.MARINE] ?: 0L, now)
    if (weatherUnusable != null || marineUnusable != null) {
        val ageH = maxOf(weatherUnusable ?: 0L, marineUnusable ?: 0L).let { (it + 3_599_999) / 3_600_000 }
        Text("数据不可用（已过期 $ageH h），不作为评分依据；最近数据时间 ${result.fetchedAtMillis.values.maxOrNull()?.let { Format.clockMillis(it, zone) } ?: "—"}",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp)
                .semantics { contentDescription = "缓存已超过48小时，数据不可用，不作为评分依据" })
    } else {
        val staleWeather = Ttl.staleForMillis(com.tidespot.core.data.repo.DataSource.WEATHER, result.fetchedAtMillis[com.tidespot.core.data.repo.DataSource.WEATHER] ?: 0L, now)
        val staleMarine = Ttl.staleForMillis(com.tidespot.core.data.repo.DataSource.MARINE, result.fetchedAtMillis[com.tidespot.core.data.repo.DataSource.MARINE] ?: 0L, now)
        if (staleWeather != null || staleMarine != null) {
            // 文案「数据时间 X，已过期」由 Ttl 计算，不硬编码
            Text("数据时间 ${result.fetchedAtMillis.values.maxOrNull()?.let { Format.clockMillis(it, zone) } ?: "—"}，已过期",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
                    .semantics { contentDescription = "缓存已过期警告：数据时间显示于正文" })
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
    val unit = chartUnit(tab)
    val summary = Derivations.summary(daySlices.map { it.timeEpochSec to values.getOrNull(daySlices.indexOf(it)) })
    if (summary == null) {
        Text("当日摘要：—（无有效数据）", Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall)
        return
    }
    Text(
        "当日摘要：最高 ${Format.valueUnit(summary.max, unit)}（${Format.hourLocal(summary.maxEpochSec, zone)}）" +
            " / 最低 ${Format.valueUnit(summary.min, unit)}（${Format.hourLocal(summary.minEpochSec, zone)}）",
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics { contentDescription = "当日摘要：最高 ${Format.value(summary.max)} 于 ${Format.hourLocal(summary.maxEpochSec, zone)}，最低 ${Format.value(summary.min)} 于 ${Format.hourLocal(summary.minEpochSec, zone)}" },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SourceFooter(result: com.tidespot.core.data.repo.SpotDetailResult, zone: ZoneId, tab: DetailTab) {
    val lines = buildList {
        val w = result.fetchedAtMillis[DataSource.WEATHER]?.takeIf { it > 0 }
        val m = result.fetchedAtMillis[DataSource.MARINE]?.takeIf { it > 0 }
        if (tab != DetailTab.SCORE && tab != DetailTab.WATER_QUALITY) {
            if (m != null) add("海洋来源 · 本机拉取 ${Format.clockMillis(m, zone)}")
        }
        if (w != null) add("天气来源 · 本机拉取 ${Format.clockMillis(w, zone)}")
        if (result.servedFromCache.isNotEmpty()) add("当前显示为最近缓存（重试失败保留旧数据）")
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        lines.forEach {
            Text(it, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun holderRawInputs(viewModel: DetailViewModel, epochSec: Long): List<RawInputRow> =
    viewModel.holderInternal().rawInputsFor(epochSec)

private fun chartUnit(tab: DetailTab): String = when (tab) {
    DetailTab.WAVE -> "m"
    DetailTab.WEATHER -> "°C"
    DetailTab.SCORE -> "分"
    DetailTab.WATER_QUALITY -> "°C"
    DetailTab.TIDE -> "m"
}

private fun disclaimerFor(tab: DetailTab): String = when (tab) {
    DetailTab.WAVE, DetailTab.TIDE -> TideSpotStrings.DISCLAIMER_WAVE_TIDE
    DetailTab.WEATHER -> TideSpotStrings.DISCLAIMER_WEATHER
    DetailTab.SCORE -> TideSpotStrings.DISCLAIMER_SCORE
    DetailTab.WATER_QUALITY -> TideSpotStrings.DISCLAIMER_WATER_QUALITY
}








