package com.tidespot.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tidespot.core.data.repo.TideSpotRepository
import com.tidespot.core.ui.R as UiR
import com.tidespot.core.data.water.CopernicusCredential
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.model.WaterBodyType
import com.tidespot.core.ui.strings.localTideSpotStrings
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.theme.TideSpotTheme
import com.tidespot.core.ui.theme.VintageOutdoorTheme
import com.tidespot.core.ui.zone.SpotZones
import com.tidespot.feature.detail.detail.DetailScreen
import com.tidespot.feature.detail.detail.DetailViewModel
import com.tidespot.feature.detail.detail.InlandCredentialGuideCard
import com.tidespot.feature.favorites.favorites.FavoritesSaveRules
import com.tidespot.feature.favorites.favorites.FavoritesScreen
import com.tidespot.feature.favorites.favorites.FavoritesSummaryFormatter
import com.tidespot.feature.favorites.favorites.FavoritesViewModel
import com.tidespot.feature.map.MapScreen
import com.tidespot.feature.map.model.GeoPoint
import com.tidespot.feature.map.model.SpotConfirmed
import com.tidespot.feature.map.stars.FavoriteStar
import com.tidespot.feature.settings.CopernicusCredentialSection
import com.tidespot.feature.settings.SettingsLogic
import com.tidespot.feature.settings.SettingsScreen
import com.tidespot.feature.settings.SettingsState
import kotlinx.coroutines.launch

/**
 * 单 Activity 宿主（M6 批次 3）：薄组装——导航状态机（[AppNav]）、DI（[AppGraph]）、
 * 四页切换与 OPT-M5-07 准星收藏主路径接线。业务语义全部在下游纯函数/模块内。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // OPT-V115-02 / GAP-03：edge-to-edge（Android 15+ 强制；统一显式关闭 decor fits，insets 由各屏自管）
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val graph = AppGraph(this)
        setContent {
            TideSpotTheme {
                AppRoot(graph)
            }
        }
    }
}

/** 底部导航四项（顺序 = §8.1 主流程：地图选点 → 收藏 → 详情 → 设置）。 */
/** CHG-25（P0-1）：Copernicus 首次引导卡「跳过」标记键（SharedPreferences；一次后不再显示）。 */
private const val GUIDE_SKIP_COPERNICUS = "guide.copernicus.skipped"

/** UX-21（OPT-V11-21）：地图页「2 秒内二次返回退出」窗口。 */
private const val EXIT_BACK_WINDOW_MILLIS = 2_000L

@Composable
private fun AppRoot(graph: AppGraph) {
    var navState by remember { mutableStateOf(AppNavState()) }
    var settingsState by remember { mutableStateOf(AppSettings.read(graph.settingsStore)) }
    // CHG-25（P0-1）：Copernicus 凭据配置态（运行期读凭据存储；保存/清除即时刷新）
    var copernicusConfigured by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { copernicusConfigured = graph.hasCopernicusCredential() }
    // 首次引导卡「跳过」标记（一次后不再显示）
    var credentialGuideSkipped by remember {
        mutableStateOf(graph.settingsStore.readBoolean(GUIDE_SKIP_COPERNICUS, false))
    }
    // OPT-M5-07：确认选点 → 收藏对话框（预填逆地理名称）
    var pendingConfirm by remember { mutableStateOf<SpotConfirmed?>(null) }
    // OPT-V115-02 / S01 C22：最近一次详情拉取时刻（地图来源行透传；真实值，无数据显示「—」）
    var lastFetchedMillis by remember { mutableStateOf<Long?>(null) }

    val favoritesVm = remember { FavoritesViewModel(graph.repository, networkAvailable = { graph.isNetworkAvailable() }) }
    // OPT-V12-01（CHG-57）：地图星标数据源（Room 读侧 → 收藏点 + 最佳窗口摘要；端侧零额外网络）
    val favoritesState by favoritesVm.state.collectAsState()
    val tideStrings = localTideSpotStrings()
    val favoriteStars = remember(favoritesState.spots, favoritesState.summaries, favoritesState.nowEpochSec, tideStrings) {
        favoritesState.spots.map { spot ->
            FavoriteStar(
                id = spot.id,
                name = spot.name,
                lat = spot.lat,
                lon = spot.lon,
                summary = favoritesState.summaries[spot.id]?.let { summary ->
                    FavoritesSummaryFormatter.line(
                        summary,
                        SpotZones.zoneForSpot(spot),
                        favoritesState.nowEpochSec.takeIf { it > 0 },
                        tideStrings,
                    )
                },
            )
        }
    }
    // OPT-V11-05（P1）：进入收藏列表 → 后台预刷新最近查看 5 条（TTL/2 + 令牌桶；失败静默、无 UI 状态）
    LaunchedEffect(navState.page, settingsState.prefetchEnabled) {
        if (navState.page == AppPage.FAVORITES && settingsState.prefetchEnabled) {
            val spots = runCatching { graph.repository.favorites() }.getOrDefault(emptyList())
            if (spots.isNotEmpty()) {
                graph.prefetcher.prefetch(spots)
                // 预取落库 → 静默刷新摘要（不显示「加载中」）
                favoritesVm.load(showLoading = false)
            }
        }
    }
    // OPT-V12-02（CHG-57）：离开收藏页即取消进行中的刷新（切页取消，无残留进度/ANR）
    LaunchedEffect(navState.page) {
        if (navState.page != AppPage.FAVORITES) favoritesVm.cancelRefresh()
    }
    // CHG-12④b：ZoneId 由 DetailViewModel 构造器默认值按 spot.tzOffsetSec 单源解析；
    // 设置 detailDays 变化或换点 → 重建（缓存先行语义不变）
    val detailSpot = navState.detailSpot
    val detailVm = remember(detailSpot?.id, settingsState.detailDays) {
        detailSpot?.let { DetailViewModel(graph.repository, it, daysRange = settingsState.detailDays, networkAvailable = { graph.isNetworkAvailable() }) }
    }

    // ── v1.1 UX 批次（18/20/21）：浮动按钮 + 返回手势 + 返回地图定位 ──
    val context = LocalContext.current
    /** UX-18：返回地图的相机目标（上一查看点 → 否则 lastViewedAt 最新；null = 世界视图）。 */
    var pendingMapTarget by remember { mutableStateOf<GeoPoint?>(null) }
    /** OPT-V12-01：地图相机中心（星标 → 详情 → 返回地图时保持位置不变）。 */
    var lastMapTarget by remember { mutableStateOf<GeoPoint?>(null) }
    /** UX-20：地图浮动「Top10」→ 进收藏并自动展开 Top10 Sheet。 */
    var openFavoritesTop10 by remember { mutableStateOf(false) }
    var lastViewedSpot by remember { mutableStateOf<FavoriteSpot?>(null) }
    var lastBackAt by remember { mutableStateOf(0L) }

    fun latestViewedSpot(): FavoriteSpot? =
        favoritesVm.state.value.spots.filter { it.lastViewedAtEpochSec > 0 }
            .maxByOrNull { it.lastViewedAtEpochSec }

    // UX-21（OPT-V11-21）：系统返回——详情→来源页；收藏/设置→地图；地图 2s 内二次返回退出
    BackHandler(enabled = navState.page != AppPage.MAP) {
        navState = if (navState.page == AppPage.DETAIL) {
            AppNav.reduce(navState, AppNavEvent.BackFromDetail)
        } else {
            val target = lastViewedSpot ?: latestViewedSpot()
            pendingMapTarget = target?.let { GeoPoint(it.lat, it.lon) }
            AppNav.reduce(navState, AppNavEvent.TabSelected(AppPage.MAP))
        }
    }
    BackHandler(enabled = navState.page == AppPage.MAP) {
        val now = System.currentTimeMillis()
        if (now - lastBackAt <= EXIT_BACK_WINDOW_MILLIS) {
            (context as? android.app.Activity)?.finish()
        } else {
            lastBackAt = now
            Toast.makeText(context, context.getString(R.string.ts_app_exit_hint), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // OPT-V115-02 / GAP-03：edge-to-edge 收口——insets 下沉到各屏自管（地图全屏、控件单独 pad）
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // OPT-V12-01（CHG-57）：地图页（页面切换沿用原导航结构；MapView 生命周期问题另案登记）
            when (navState.page) {
                AppPage.MAP -> MapScreen(
                    placeResolver = graph.placeResolver,
                    geocoder = graph.geocoder,
                    locationController = graph.locationController,
                    waterBodyResolver = graph.waterBodyResolver,
                    // N16：逆地理总开关（关 → MapScreen 内短路走 RG-D 兜底文案）
                    reverseGeoEnabled = settingsState.reverseGeoEnabled,
                    onSpotConfirmed = { confirmed -> pendingConfirm = confirmed },
                    // UX-19：底图样式（默认 Voyager；设置三选持久化）
                    styleUrl = settingsState.mapStyle.url,
                    // UX-18：返回地图 → 上一查看点 / lastViewed 最新（冷启动 null → 定位/世界视图）
                    initialCameraTarget = pendingMapTarget,
                    // OPT-V115-02 / S01：顶部工具浮层（定位/搜索/收藏/设置）+ 信息条上方 Top10 入口
                    onOpenFavorites = {
                        openFavoritesTop10 = false
                        navState = AppNav.reduce(navState, AppNavEvent.TabSelected(AppPage.FAVORITES))
                    },
                    favoritesLabel = stringResource(R.string.ts_app_favorites),
                    onOpenSettings = {
                        navState = AppNav.reduce(navState, AppNavEvent.TabSelected(AppPage.SETTINGS))
                    },
                    settingsLabel = stringResource(R.string.ts_app_settings),
                    onOpenTop10 = {
                        openFavoritesTop10 = true
                        navState = AppNav.reduce(navState, AppNavEvent.TabSelected(AppPage.FAVORITES))
                    },
                    top10Label = stringResource(UiR.string.ts_top10_all),
                    sourceFetchedAtMillis = lastFetchedMillis,
                    // OPT-V12-01：收藏星标 + 气泡卡（点击星标 → 气泡 → 查看详情；不移动准星）
                    favoriteStars = favoriteStars,
                    onOpenFavorite = { id ->
                        favoritesVm.state.value.spots.firstOrNull { it.id == id }?.let { spot ->
                            lastViewedSpot = spot
                            favoritesVm.markViewed(spot.id)
                            // 返回地图时相机回到点星标前的位置
                            pendingMapTarget = lastMapTarget
                            navState = AppNav.reduce(
                                navState,
                                AppNavEvent.OpenDetail(spot, source = AppPage.MAP),
                            )
                        }
                    },
                    onCameraTargetChanged = { lastMapTarget = it },
                    modifier = Modifier.fillMaxSize(),
                )

                AppPage.FAVORITES -> FavoritesScreen(
                    viewModel = favoritesVm,
                    // 导航闭环：收藏列表 → 详情（markViewed 先行，F-2.4 最近查看排序；来源页 = 收藏）
                    onOpenSpot = { spot ->
                        lastViewedSpot = spot
                        favoritesVm.markViewed(spot.id)
                        navState = AppNav.reduce(
                            navState,
                            AppNavEvent.OpenDetail(spot, source = AppPage.FAVORITES),
                        )
                    },
                    openTop10OnStart = openFavoritesTop10,
                    onTop10Opened = { openFavoritesTop10 = false },
                    modifier = Modifier.fillMaxSize(),
                )

                AppPage.DETAIL -> {
                    val spot = navState.detailSpot
                    val vm = detailVm
                    if (spot != null && vm != null) {
                        DetailPage(
                            spot = spot,
                            viewModel = vm,
                            onBack = { navState = AppNav.reduce(navState, AppNavEvent.BackFromDetail) },
                            onFetched = { lastFetchedMillis = it },
                            // UX-21：详情返回按来源页呈现（从地图进 → 回地图）
                            backLabel = if (navState.sourcePage == AppPage.MAP) stringResource(R.string.ts_app_back_map) else stringResource(R.string.ts_app_back_favorites),
                            showCredentialGuide = spot.waterBody == WaterBodyType.INLAND_WATER &&
                                !copernicusConfigured && !credentialGuideSkipped,
                            onOpenCredentialSettings = {
                                navState = AppNav.reduce(navState, AppNavEvent.TabSelected(AppPage.SETTINGS))
                            },
                            onSkipCredentialGuide = {
                                credentialGuideSkipped = true
                                graph.settingsStore.writeBoolean(GUIDE_SKIP_COPERNICUS, true)
                            },
                        )
                    }
                }

                AppPage.SETTINGS -> SettingsPage(
                    state = settingsState.copy(copernicusConfigured = copernicusConfigured),
                    onChange = { next ->
                        persistSettings(graph, next)
                        settingsState = next.copy(copernicusConfigured = copernicusConfigured)
                    },
                    credentialSection = {
                        CopernicusCredentialSection(
                            configured = copernicusConfigured,
                            onCheckAndSave = { username, password ->
                                graph.checkAndApply(CopernicusCredential(username, password))
                            },
                            onClear = { graph.clearCopernicusCredential() },
                            onConfiguredChanged = { copernicusConfigured = it },
                        )
                    },
                )
            }
        }
    }

    pendingConfirm?.let { confirmed ->
        ConfirmFavoriteDialog(
            confirmed = confirmed,
            repository = graph.repository,
            favoritesVm = favoritesVm,
            onSaved = { saved ->
                pendingConfirm = null
                lastViewedSpot = saved
                // UX-21：来源页 = 地图（返回手势/按钮回地图）
                navState = AppNav.reduce(navState, AppNavEvent.OpenDetail(saved, source = AppPage.MAP))
            },
            onDismiss = { pendingConfirm = null },
        )
    }
}

/**
 * 详情页容器：返回收藏行 + DetailScreen + F-3.7 模型署名底栏（部分口径——
 * 「维度来源 · 本机拉取 HH:mm」已由 DetailScreen SourceFooter 透出；DTO 级
 * 模型名/网格分辨率透出登记 v1.1）。
 */
@Composable
private fun DetailPage(
    spot: FavoriteSpot,
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    backLabel: String,
    /** OPT-V115-02：详情数据拉取时刻上报（地图 C22 来源行用）。 */
    onFetched: (Long?) -> Unit = {},
    showCredentialGuide: Boolean,
    onOpenCredentialSettings: () -> Unit,
    onSkipCredentialGuide: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.result?.fetchedAtMillis) {
        onFetched(state.result?.fetchedAtMillis?.values?.maxOrNull())
    }
    // 手绘 A · 复古户外：详情页一屏试点皮肤（仅视觉 token，参数/回调/导航零改动）
    VintageOutdoorTheme(
        waterBody = state.result?.effectiveWaterBody ?: spot.waterBody,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            DetailScreen(
                spot = spot,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                // OPT-V115-02 / S02 TOPBAR：返回位收进详情屏顶栏（原独立 TextButton 行移除）
                onBack = onBack,
                backLabel = backLabel,
                credentialGuideSlot = if (showCredentialGuide) {
                    {
                        InlandCredentialGuideCard(
                            onOpenSettings = onOpenCredentialSettings,
                            onSkip = onSkipCredentialGuide,
                        )
                    }
                } else {
                    null
                },
            )
            Text(
                localTideSpotStrings().modelAttribution,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SettingsPage(
    state: SettingsState,
    onChange: (SettingsState) -> Unit,
    credentialSection: (@Composable () -> Unit)? = null,
) {
    SettingsScreen(
        state = state,
        onUnitSystemChange = { unit -> onChange(SettingsLogic.withUnitSystem(state, unit)) },
        onDetailDaysChange = { days -> onChange(SettingsLogic.withDetailDays(state, days)) },
        onPrefetchChange = { enabled -> onChange(SettingsLogic.withPrefetch(state, enabled)) },
        onReverseGeoChange = { enabled -> onChange(SettingsLogic.withReverseGeo(state, enabled)) },
        onApiKeyChange = { raw -> onChange(SettingsLogic.withApiKey(state, raw)) },
        // UX-19：地图样式三选（持久化 + 地图页即时生效）
        onMapStyleChange = { style -> onChange(SettingsLogic.withMapStyle(state, style)) },
        modifier = Modifier.fillMaxSize(),
        credentialSection = credentialSection,
    )
}

/** 设置写回：store 持久化（键名/清洗经 AppSettings 单源），UI 状态由调用方更新。 */
private fun persistSettings(graph: AppGraph, next: SettingsState) {
    val store = graph.settingsStore
    AppSettings.writeUnit(store, next.unitSystem)
    AppSettings.writeDetailDays(store, next.detailDays)
    AppSettings.writePrefetch(store, next.prefetchEnabled)
    AppSettings.writeReverseGeo(store, next.reverseGeoEnabled)
    AppSettings.writeApiKey(store, next.apiKey)
    AppSettings.writeMapStyle(store, next.mapStyle)
}

/**
 * OPT-M5-07 准星一键收藏主路径对话框（:app 侧；FavoritesScreen 内编辑对话框不适用新建+坐标流）：
 * - 名称预填逆地理 displayName（缺失/空白落 [AppSettings.fallbackSpotName]）；
 * - 坐标占位拒绝由 [FavoritesSaveRules.canCreate] 单源防御（禁 0.0/0.0 落库）；
 * - 保存链：upsertFavorite（真实 lat/lon）→ updateSpotWaterBody 回写确认选点流水线的
 *   waterBody（elevation/tzOffsetSec 传 null=保持原值，tzOffsetSec 由详情 refresh 回写）→
 *   收藏列表刷新 → OpenDetail 进详情页。
 */
@Composable
private fun ConfirmFavoriteDialog(
    confirmed: SpotConfirmed,
    repository: TideSpotRepository,
    favoritesVm: FavoritesViewModel,
    onSaved: (FavoriteSpot) -> Unit,
    onDismiss: () -> Unit,
) {
    val point = confirmed.point
    val scope = rememberCoroutineScope()
    var name by remember(confirmed) {
        mutableStateOf(confirmed.place?.displayName?.trim().takeUnless { it.isNullOrEmpty() }
            ?: AppSettings.fallbackSpotName(point.lat, point.lon))
    }
    var saving by remember(confirmed) { mutableStateOf(false) }
    var error by remember(confirmed) { mutableStateOf<String?>(null) }
    val spotNameDesc = stringResource(R.string.ts_app_spot_name)
    val saveFailed = stringResource(R.string.ts_app_save_failed)
    val coord1 = String.format(java.util.Locale.US, "%.5f", point.lat)
    val coord2 = String.format(java.util.Locale.US, "%.5f", point.lon)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(UiR.string.ts_favorite_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ts_app_name)) },
                    singleLine = true,
                    supportingText = {
                        Text(stringResource(UiR.string.ts_favorite_name_hint))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = spotNameDesc },
                )
                Text(
                    stringResource(R.string.ts_app_coordinate, coord1, coord2),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    stringResource(R.string.ts_app_water_body, confirmed.waterBody.name),
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            val rejectMessage = FavoritesSaveRules.rejectMessage(localTideSpotStrings())
            TextButton(
                enabled = !saving,
                onClick = {
                    if (!FavoritesSaveRules.canCreate(point.lat, point.lon)) {
                        error = rejectMessage
                        return@TextButton
                    }
                    saving = true
                    scope.launch {
                        try {
                            val finalName = name.trim().ifEmpty {
                                AppSettings.fallbackSpotName(point.lat, point.lon)
                            }
                            val draft = FavoriteSpot(
                                id = 0,
                                name = finalName,
                                lat = point.lat,
                                lon = point.lon,
                            )
                            val id = repository.upsertFavorite(draft)
                            // waterBody 走确认选点流水线回写；elevation/tzOffsetSec 传 null=保持原值（tzOffsetSec 由详情 refresh 回写）
                            repository.updateSpotWaterBody(id, confirmed.waterBody, elevation = null, tzOffsetSec = null)
                            favoritesVm.load()
                            onSaved(draft.copy(id = id, waterBody = confirmed.waterBody))
                        } catch (e: Exception) {
                            error = e.message ?: saveFailed
                        } finally {
                            saving = false
                        }
                    }
                },
            ) {
                Text(if (saving) stringResource(R.string.ts_app_saving) else stringResource(UiR.string.ts_confirm_favorite))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.ts_cancel)) }
        },
    )
}
