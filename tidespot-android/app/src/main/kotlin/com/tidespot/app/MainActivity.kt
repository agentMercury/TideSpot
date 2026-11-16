package com.tidespot.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.tidespot.core.ui.theme.LocalSkin
import com.tidespot.core.ui.theme.TideSpotSkins
import com.tidespot.core.ui.theme.TideSpotTheme
import com.tidespot.core.ui.theme.VintageOutdoorTheme
import com.tidespot.core.ui.theme.paperGrain
import com.tidespot.core.ui.theme.rememberPaperGrainBrush
import com.tidespot.core.ui.zone.SpotZones
import com.tidespot.feature.detail.detail.DetailScreen
import com.tidespot.feature.detail.detail.DetailViewModel
import com.tidespot.feature.detail.detail.InlandCredentialGuideCard
import com.tidespot.feature.favorites.favorites.FavoritesInfoCardLogic
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
        // OPT-A-02：API Key 版块已下线，启动时一次性清理历史写入的本地键（不读取、不上传）
        AppSettings.clearLegacyApiKey(graph.settingsStore)
        setContent {
            // SKIN-2 / SKIN-3 / OPT-A-03：主题是唯一视觉入口——皮肤 + 明暗从设置读取，
            // 外壳页跨水体（海洋兜底），详情页按点位水体再包一层同 skin 主题。
            var settings by remember { mutableStateOf(AppSettings.read(graph.settingsStore)) }
            val skin = TideSpotSkins.byId(settings.skinId)
            val darkTheme = SettingsLogic.resolveDarkTheme(settings.themeMode, isSystemInDarkTheme())
            // 手动切深色时系统栏图标也要跟着翻转，否则深底上会出现深色状态栏图标
            val view = LocalView.current
            LaunchedEffect(darkTheme) {
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            TideSpotTheme(skin = skin, water = WaterBodyType.OCEAN, darkTheme = darkTheme) {
                AppRoot(
                    graph = graph,
                    settingsState = settings,
                    onSettingsChange = { next ->
                        // 三轴互不重置：persistSettings 逐键写回，切皮肤不动明暗、切明暗不动皮肤
                        persistSettings(graph, next)
                        settings = next
                    },
                )
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
private fun AppRoot(
    graph: AppGraph,
    settingsState: SettingsState,
    onSettingsChange: (SettingsState) -> Unit,
) {
    var navState by remember { mutableStateOf(AppNavState()) }
    // 外壳纸纹读 skin.texture；与主题注入同源同一 skinId（切皮肤即时更新）
    val skin = TideSpotSkins.byId(settingsState.skinId)
    // 明暗轴：供详情页皮肤再包一层时复用同一解析结果（与全局主题同口径）
    val darkTheme = SettingsLogic.resolveDarkTheme(settingsState.themeMode, isSystemInDarkTheme())
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
    /**
     * 「下一个最佳窗口」提示条数据（issue #3 / 对齐 Web v1.22.0）。
     *
     * 数据源复用已有跨点 Top10（`favoritesState.top10`，Room 读侧，端侧零额外网络）；
     * 挑选逻辑交给 `NextWindowFinder`（时间紧迫性优先，与 Top10 的 rank 排序刻意不同）。
     * 适配成 `feature:map` 的 `NextWindowStripData`，避免 :map 反向依赖 :favorites。
     */
    val nextWindowData = remember(favoritesState.top10) {
        val candidates = favoritesState.top10
        if (candidates.isEmpty()) {
            null
        } else {
            // 在所有跨点窗口里按时间紧迫性挑一个（不是取 top10 的第一个——那个是 rank 最高的）
            val allWindows = candidates.map { it.window }
            val pick = com.tidespot.core.domain.window.NextWindowFinder.findNext(
                allWindows,
                System.currentTimeMillis() / 1000L,
            )
            pick.window?.let { w ->
                // 回找该窗口所属的点（多点为同一窗口对象的概率极低，用引用相等 + 兜底首项）
                val owner = candidates.firstOrNull { it.window === w }
                    ?: candidates.firstOrNull { it.window.startEpochSec == w.startEpochSec }
                    ?: candidates.first()
                com.tidespot.feature.map.NextWindowStripData(
                    spotId = owner.spot?.id,
                    spotName = owner.spotName,
                    window = w,
                    zone = owner.zone,
                )
            }
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
        // SKIN-2：外壳页统一铺皮肤纸底（A = 做旧米纸），各页不再各自写底色
        containerColor = MaterialTheme.colorScheme.background,
        // OPT-V115-02 / GAP-03：edge-to-edge 收口——insets 下沉到各屏自管（地图全屏、控件单独 pad）
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        // SKIN-2 / SKIN-3：纸纹统一挂在外壳，强度与开关由当前皮肤 texture 决定。
        // 地图页始终跳过（真实瓦片底图，颗粒伤可读性）；详情页不再各自叠加，避免双层颗粒。
        val grain = rememberPaperGrainBrush(darkTheme)
        val showGrain = skin.texture.paperGrain && navState.page != AppPage.MAP
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(
                    if (showGrain) Modifier.paperGrain(grain, skin.texture.grainStrength) else Modifier,
                ),
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
                    // UX-19 + OPT-A-03 + OPT-A-10：底图 URL = 提供方 × 生效样式（唯一出口）
                    styleUrl = SettingsLogic.resolveBasemapUrl(settingsState, darkTheme),
                    mapProviderName = settingsState.mapProvider.displayName,
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
                    nextWindowData = nextWindowData,
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
                            // OPT-A-03：详情页与全局主题共用同一明暗解析（不再各自 isSystemInDarkTheme）
                            darkTheme = darkTheme,
                        )
                    }
                }

                AppPage.SETTINGS -> SettingsPage(
                    state = settingsState.copy(copernicusConfigured = copernicusConfigured),
                    onChange = onSettingsChange,
                    // OPT-A-06：请求日志（fetch_log 只读视图）
                    repository = graph.repository,
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
            // CHG-92：跳过判定缓存重跑一次（凭据/网络恢复后不必等 24h TTL）
            onReclassify = { p -> graph.reclassifyWaterBody(p.lat, p.lon) },
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
    /** OPT-A-03：明暗轴解析结果（SYSTEM/LIGHT/DARK → Boolean），与全局主题同口径。 */
    darkTheme: Boolean,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.result?.fetchedAtMillis) {
        onFetched(state.result?.fetchedAtMillis?.values?.maxOrNull())
    }
    // 手绘 A · 复古户外：详情页按点位水体再包一层同 skin 主题（仅换水体色板，不换皮肤）。
    // 水体显式取一次，保证色板、纸纹 tile、装饰母题三者同口径。
    val waterBody = state.result?.effectiveWaterBody ?: spot.waterBody
    val inland = waterBody == WaterBodyType.INLAND_WATER
    VintageOutdoorTheme(waterBody = waterBody, darkTheme = darkTheme) {
        // 纸纹由 AppRoot 外壳统一挂载（避免双层颗粒）；此处只铺本水体纸底 + 装饰母题
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // 鱼群水印：A 版「把装饰元素替换成鱼形」的母题，纯绘制、不拦截触摸；
            // F 蓝潮是干净白卡，不要任何手绘水印。
            if (LocalSkin.current.id != "f-bluetide") {
                FishWatermark(
                    inland = inland,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 46.dp),
                )
            }
            Column(modifier = Modifier.fillMaxSize()) {
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
}

/**
 * 右下角手绘水印：内陆 = 山 / 日 / 芦苇，海洋 = 浪 / 贝 / 海鸟，后接三条递减版画鱼。
 * 纯绘制、不参与测量、不消费触摸；透明度极低，只提供氛围不干扰读数。
 * 放在水印而非模型署名行：原详情页 fillMaxSize 会把署名行挤出视口，
 * 改动该布局会连带挤掉「最佳窗口 Top10」入口，因此不动布局。
 */
@Composable
private fun FishWatermark(inland: Boolean, modifier: Modifier = Modifier) {
    val isJournal = LocalSkin.current.id == "e-tidejournal"
    val tint = if (isJournal) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(
                if (inland) UiR.drawable.ic_mount_vintage else UiR.drawable.ic_wave_vintage,
            ),
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .width(56.dp)
                .height(34.dp)
                .graphicsLayer(alpha = if (isJournal) 0.3f else 0.22f),
        )
        Spacer(Modifier.width(4.dp))
        Row(
            modifier = Modifier.graphicsLayer(alpha = if (isJournal) 0.2f else 0.12f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(30.dp, 44.dp, 60.dp).forEach { h ->
                Icon(
                    painter = painterResource(UiR.drawable.ic_fish_vintage),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.width(h * 1.875f).height(h),
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(
    state: SettingsState,
    onChange: (SettingsState) -> Unit,
    repository: com.tidespot.core.data.repo.TideSpotRepository,
    credentialSection: (@Composable () -> Unit)? = null,
) {
    // OPT-A-06：请求日志弹层（数据由 repository 只读拉取，设置模块不依赖 core:data 实现）
    var showFetchLog by remember { mutableStateOf(false) }
    SettingsScreen(
        state = state,
        onUnitSystemChange = { unit -> onChange(SettingsLogic.withUnitSystem(state, unit)) },
        onDetailDaysChange = { days -> onChange(SettingsLogic.withDetailDays(state, days)) },
        onPrefetchChange = { enabled -> onChange(SettingsLogic.withPrefetch(state, enabled)) },
        onReverseGeoChange = { enabled -> onChange(SettingsLogic.withReverseGeo(state, enabled)) },
        // UX-19：地图样式三选（持久化 + 地图页即时生效）
        // OPT-A-12：地图样式四选（跟随主题 / 彩色 / 素色 / 深色）
        onMapStyleChange = { choice -> onChange(SettingsLogic.withMapStyleChoice(state, choice)) },
        // OPT-A-10：地图来源（提供方）
        onMapProviderChange = { provider -> onChange(SettingsLogic.withMapProvider(state, provider)) },
        // SKIN-3 / OPT-A-09：皮肤下拉（按名字选，只写 skinId）
        onSkinChange = { id -> onChange(SettingsLogic.withSkinId(state, id)) },
        // OPT-A-03：明暗三选（只写 themeMode，与皮肤正交）
        onThemeModeChange = { mode -> onChange(SettingsLogic.withThemeMode(state, mode)) },
        modifier = Modifier.fillMaxSize(),
        credentialSection = credentialSection,
        onOpenFetchLog = { showFetchLog = true },
    )
    if (showFetchLog) {
        FetchLogDialog(repository = repository, onDismiss = { showFetchLog = false })
    }
}

/** 设置写回：store 持久化（键名/清洗经 AppSettings 单源），UI 状态由调用方更新。 */
private fun persistSettings(graph: AppGraph, next: SettingsState) {
    val store = graph.settingsStore
    AppSettings.writeUnit(store, next.unitSystem)
    AppSettings.writeDetailDays(store, next.detailDays)
    AppSettings.writePrefetch(store, next.prefetchEnabled)
    AppSettings.writeReverseGeo(store, next.reverseGeoEnabled)
    AppSettings.writeMapStyle(store, next.mapStyle)
    AppSettings.writeMapStyleExplicit(store, next.mapStyleExplicit)
    AppSettings.writeMapProvider(store, next.mapProvider)
    // 三轴正交：皮肤与明暗各自写自己的键，互不覆盖
    AppSettings.writeSkinId(store, next.skinId)
    AppSettings.writeThemeMode(store, next.themeMode)
}

/**
 * OPT-M5-07 准星一键收藏主路径对话框（:app 侧；FavoritesScreen 内编辑对话框不适用新建+坐标流）：
 * - 名称预填逆地理 displayName（缺失/空白落 [AppSettings.fallbackSpotName]）；
 * - 坐标占位拒绝由 [FavoritesSaveRules.canCreate] 单源防御（禁 0.0/0.0 落库）；
 * - 保存链：upsertFavorite（真实 lat/lon）→ updateSpotWaterBody 回写确认选点流水线的
 *   waterBody（elevation/tzOffsetSec 传 null=保持原值，tzOffsetSec 由详情 refresh 回写）→
 *   收藏列表刷新 → OpenDetail 进详情页。
 * - CHG-92：水体类型显示本地化文案而非枚举名；「重新判定」按钮走 [onReclassify]
 *   （force 跳过缓存）即时刷新；UNKNOWN 时给出「数据源暂不可用」说明，避免像卡死。
 */
@Composable
private fun ConfirmFavoriteDialog(
    confirmed: SpotConfirmed,
    repository: TideSpotRepository,
    favoritesVm: FavoritesViewModel,
    onReclassify: suspend (GeoPoint) -> WaterBodyType,
    onSaved: (FavoriteSpot) -> Unit,
    onDismiss: () -> Unit,
) {
    val point = confirmed.point
    val scope = rememberCoroutineScope()
    var waterBody by remember(confirmed) { mutableStateOf(confirmed.waterBody) }
    var reclassifying by remember(confirmed) { mutableStateOf(false) }
    var name by remember(confirmed) {
        mutableStateOf(confirmed.place?.displayName?.trim().takeUnless { it.isNullOrEmpty() }
            ?: AppSettings.fallbackSpotName(point.lat, point.lon))
    }
    var saving by remember(confirmed) { mutableStateOf(false) }
    var error by remember(confirmed) { mutableStateOf<String?>(null) }
    val spotNameDesc = stringResource(R.string.ts_app_spot_name)
    val saveFailed = stringResource(R.string.ts_app_save_failed)
    val reclassifyFailed = stringResource(R.string.ts_app_reclassify_failed)
    val reclassifyLabel = stringResource(R.string.ts_app_reclassify_water_body)
    val reclassifyingLabel = stringResource(R.string.ts_app_reclassifying)
    val waterBodyLabel = FavoritesInfoCardLogic.waterBodyLabel(waterBody, localTideSpotStrings())
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.ts_app_water_body, waterBodyLabel),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TextButton(
                        enabled = !saving && !reclassifying,
                        onClick = {
                            reclassifying = true
                            scope.launch {
                                try {
                                    waterBody = onReclassify(point)
                                    error = null
                                } catch (e: Exception) {
                                    error = reclassifyFailed
                                } finally {
                                    reclassifying = false
                                }
                            }
                        },
                    ) {
                        Text(if (reclassifying) reclassifyingLabel else reclassifyLabel)
                    }
                }
                if (waterBody == WaterBodyType.UNKNOWN) {
                    Text(
                        stringResource(R.string.ts_app_water_body_unknown_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                            repository.updateSpotWaterBody(id, waterBody, elevation = null, tzOffsetSec = null)
                            favoritesVm.load()
                            onSaved(draft.copy(id = id, waterBody = waterBody))
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
