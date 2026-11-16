package com.tidespot.feature.map
import com.tidespot.core.ui.theme.vintageCard
import com.tidespot.core.ui.theme.LocalSkin
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.tidespot.core.domain.model.NamedPlace
import com.tidespot.core.ui.R as UiR
import com.tidespot.core.ui.strings.localTideSpotStrings
import com.tidespot.core.ui.format.Format
import com.tidespot.core.ui.theme.toHexRgb
import com.tidespot.core.domain.model.WaterBodyType
import com.tidespot.feature.map.crosshair.CrosshairState
import com.tidespot.feature.map.crosshair.CrosshairStateMachine
import com.tidespot.feature.map.geo.GeocoderApi
import com.tidespot.feature.map.geo.PlaceResolver
import com.tidespot.feature.map.geo.WaterBodyResolver
import com.tidespot.feature.map.infobar.InfoBarMode
import com.tidespot.feature.map.infobar.InfoBarState
import com.tidespot.feature.map.infobar.buildInfoBarState
import com.tidespot.feature.map.location.LocationController
import com.tidespot.feature.map.location.LocationFix
import com.tidespot.feature.map.location.PermissionUiState
import com.tidespot.feature.map.location.PermissionStateMachine
import com.tidespot.feature.map.model.GeoPoint
import com.tidespot.feature.map.model.SpotConfirmed
import com.tidespot.feature.map.search.SearchPanel
import com.tidespot.feature.map.stars.FavoriteStar
import com.tidespot.feature.map.stars.FavoriteStarLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.net.HttpURLConnection
import java.net.URL

/**
 * OPT-A-11：底图样式源探活（6s 超时，IO 线程）。
 *
 * 只取样式 JSON（不下载瓦片），能拿到 2xx/3xx 就认为底图源可达。
 * 目的不是精确判定渲染结果，而是**避免「一片白却什么都不说」**：源不可达时给用户
 * 一个明确提示 + 重试入口（2026-11-13 CARTO 事故场景）。
 *
 * 失败一律吞掉异常返回 false——探测绝不能影响地图本身。
 */
private suspend fun probeBasemapStyle(url: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 6_000
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        try {
            conn.responseCode in 200..399
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)
}

/** 默认底图样式 URL（OpenFreeMap 彩色；真实值单点在 [com.tidespot.core.data.remote.TideSpotUrls]，由 :app 经 SettingsLogic 注入）。 */
const val DEFAULT_BASEMAP_STYLE_URL = com.tidespot.core.data.remote.TideSpotUrls.OPENFREEMAP_COLOR_STYLE_URL

/**
 * M3 地图首屏：MapLibre 底图 + 中心准星 + 信息条 + 搜索 + 定位。
 *
 * 选点交互（地图中心对准式）：
 * - 长按地图任意处 / 搜索候选 / 定位成功 → 设预览点并飞行居中（进 Preview，待用户点「确定」）；
 * - 拖动地图 → 相机 idle 后准星跟随（CHG-09③：只在 idle 采样，飞行/手势中间帧不喂状态机）；
 * - 点「确定」→ 提交选点并触发流水线（CHG-09①）：逆地理解析（§5.8）+ 水体判定（§6.9，
 *   marineHasValue 未知走探针路径）→ 包装 [SpotConfirmed] 随回调下传。
 *
 * @param waterBodyResolver 水体判定注入点（与 [placeResolver] 同风格）；null 时判定结果恒为
 *   UNKNOWN「待判定」（M6 组装时经 DataWaterBodyResolver 注入）。
 * @param reverseGeoEnabled 逆地理总开关（N-5）：关闭时信息条只显示坐标 + 本地 plusCode。
 * @param locationController 定位控制器；null 表示不提供「我的位置」按钮。
 * @param onSpotConfirmed 确认回调（CHG-09① 改签名：(GeoPoint, NamedPlace?) -> (SpotConfirmed) -> Unit；
 *   :app/M6 当前无消费者，变更安全）。
 */
@Composable
fun MapScreen(
    placeResolver: PlaceResolver,
    geocoder: GeocoderApi,
    locationController: LocationController? = null,
    waterBodyResolver: WaterBodyResolver? = null,
    reverseGeoEnabled: Boolean = true,
    onSpotConfirmed: (SpotConfirmed) -> Unit = {},
    /** UX-19（OPT-V11-19）：底图样式 URL（默认彩色；URL 单点在 TideSpotUrls）。 */
    styleUrl: String = DEFAULT_BASEMAP_STYLE_URL,
    /**
     * OPT-A-10：当前底图**提供方**名（OpenFreeMap / CARTO…），仅用于署名行。
     * 署名必须随来源变——CARTO 的瓦片同样要求署名，写死一个提供方等于给错来源。
     */
    mapProviderName: String,
    /** UX-18（OPT-V11-18）：返回地图时的相机目标（上一查看点 / lastViewed 最新点；null = 世界视图）。 */
    initialCameraTarget: GeoPoint? = null,
    /** OPT-V115-02 / S01 C02：顶部工具浮层动作（收藏/设置/Top10 由 :app 注入；null = 不渲染）。 */
    onOpenFavorites: (() -> Unit)? = null,
    favoritesLabel: String = "",
    onOpenSettings: (() -> Unit)? = null,
    settingsLabel: String = "",
    onOpenTop10: (() -> Unit)? = null,
    top10Label: String = "",
    /**
     * 「下一个最佳窗口」提示条数据（:app 从跨点 Top10 结果适配注入；null = 无可用窗口 → 不渲染）。
     * 与 Top10 横幅并列：横幅回答「哪个最好」，提示条回答「现在该不该出门」。
     */
    nextWindowData: NextWindowStripData? = null,
/** OPT-V115-02 / S01 C22：最近一次数据拉取时刻（:app 透传；null → 显示「—」，禁编造）。 */
    sourceFetchedAtMillis: Long? = null,
    /** OPT-V12-01（CHG-57）：收藏点星标（Room 读侧由 :app 注入；空 = 不显示星标图层）。 */
    favoriteStars: List<FavoriteStar> = emptyList(),
    /** OPT-V12-01：气泡卡「查看详情」→ 进详情（null = 不渲染该入口）。 */
    onOpenFavorite: ((Long) -> Unit)? = null,
    /** OPT-V12-01：相机中心回报（:app 用于「返回地图相机位置不变」）。 */
    onCameraTargetChanged: (GeoPoint) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // SKIN-2：地图 SymbolLayer 不读 Compose 主题——皮肤 token 必须显式转 hex 传入，
    // 否则换肤后星标文字仍是写死的藏蓝/白（换肤漏屏，验收 §6.2 第 2 条）。
    val starTextHex = MaterialTheme.colorScheme.primary.toHexRgb()
    val starHaloHex = MaterialTheme.colorScheme.onPrimary.toHexRgb()

    val crosshair = remember { CrosshairStateMachine() }
    val crosshairState by crosshair.state.collectAsState()

    val permission = remember { PermissionStateMachine() }
    val permissionState by permission.state.collectAsState()

    var confirmedSpot by remember { mutableStateOf<GeoPoint?>(null) }
    var confirmedPlace by remember { mutableStateOf<NamedPlace?>(null) }
    var resolving by remember { mutableStateOf(false) }
    var lastFix by remember { mutableStateOf<LocationFix?>(null) }
    var mapLibre by remember { mutableStateOf<MapLibreMap?>(null) }
    /** CHG-09①：判定辅助输入（§6.9）；搜索候选携带 elevation，长按/定位暂无来源 → null。 */
    var pendingElevation by remember { mutableStateOf<Double?>(null) }
    /** UX-19：当前已生效的样式 URL（切换时重建 style；防重复 setStyle）。 */
    var appliedStyleUrl by remember { mutableStateOf<String?>(null) }
    /**
     * OPT-A-11：底图源是否不可达（探活失败 → 顶部提示 + 重试）。
     * 与点位/潮汐/收藏等数据链路**完全无关**：瓦片拿不到不影响任何业务功能。
     */
    var basemapUnreachable by remember { mutableStateOf(false) }
    /** OPT-A-11：重试计数（自增即触发重新探活并强制重设样式）。 */
    var basemapRetryTick by remember { mutableStateOf(0) }
    /** UX-18：程序驱动相机移动（冷启动定位/返回定位）时抑制 idle → 准星状态机（F-1.4 唯一真源不受影响）。 */
    var suppressNextIdle by remember { mutableStateOf(false) }
    /** CHG-35：最近一次相机移动是否来自用户手势（决定 Idle 是否进入 Preview）。 */
    var lastMoveWasGesture by remember { mutableStateOf(false) }
    /** OPT-V12-01：命中星标的气泡卡目标（null = 未选中；点击星标不移动准星/不发起选点请求）。 */
    var tappedStar by remember { mutableStateOf<FavoriteStar?>(null) }
    /** OPT-V12-01：星标图层所在样式（样式切换/重建时更新 GeoJSON）。 */
    var starStyle by remember { mutableStateOf<Style?>(null) }
    // 地图点击监听只注册一次 → 星标集合/相机回报用 rememberUpdatedState 取最新值（防闭包捕获旧值）
    val latestStars by rememberUpdatedState(favoriteStars)
    val latestCameraTarget by rememberUpdatedState(onCameraTargetChanged)

    /** OPT-V12-01：安装星标图层（图标 + GeoJSON 源 + SymbolLayer；重复调用幂等）。 */
    fun installStarLayer(style: Style) {
        starStyle = style
        // N-4：图层属性不受支持/样式异常时不得崩溃（星标为增强项，失败即不显示）
        runCatching {
            if (style.getImage(FavoriteStarLayer.ICON_ID) == null) {
                style.addImage(FavoriteStarLayer.ICON_ID, starBitmap(72))
            }
            if (style.getSource(FavoriteStarLayer.SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(FavoriteStarLayer.SOURCE_ID, FavoriteStarLayer.geoJson(latestStars)))
            }
            if (style.getLayer(FavoriteStarLayer.LAYER_ID) == null) {
                val layer = SymbolLayer(FavoriteStarLayer.LAYER_ID, FavoriteStarLayer.SOURCE_ID)
                // 先只装图标（保证星标本体必然可见），文本属性单独兜底：字体/表达式不支持时不影响星标
                layer.setProperties(
                    PropertyFactory.iconImage(FavoriteStarLayer.ICON_ID),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconSize(1.0f),
                )
                style.addLayer(layer)
                // 名称：街道级（z≥14）才显示，避免全国视图文字过密（GWT 设计要求）
                runCatching {
                    layer.setProperties(
                        PropertyFactory.textField(Expression.toString(Expression.get("name"))),
                        PropertyFactory.textSize(10.5f),
                        PropertyFactory.textColor(starTextHex),
                        PropertyFactory.textHaloColor(starHaloHex),
                        PropertyFactory.textHaloWidth(1.5f),
                        PropertyFactory.textOffset(arrayOf(0f, 1.1f)),
                        PropertyFactory.textAnchor("top"),
                        PropertyFactory.textOpacity(
                            Expression.interpolate(
                                Expression.exponential(1f),
                                Expression.zoom(),
                                Expression.stop(13, 0f),
                                Expression.stop(14, 1f),
                            ),
                        ),
                    )
                }
            }
        }
    }

    // F-1.1 + CHG-09⑦（NEW-07）：冷启动先查系统权限——已授权直接可用（不重复弹窗），
    // 未授权才发请求；Denied(permanent) 后状态机不再放行（无死循环）
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val showRationale = (context as? Activity)
            ?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION) }
            ?: true
        permission.onPermissionResult(granted, showRationale)
    }

    LaunchedEffect(Unit) {
        val alreadyGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            permission.onAlreadyGranted()
        } else if (permission.onRequestStarted()) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    val mapView = remember(context) {
        MapLibre.getInstance(context)
        // OPT-V12-01：onCreate 必须在视图挂载前完成（重进地图时 MapView 立即被 attach，延迟到
        // DisposableEffect 会晚于挂载 → 渲染器不初始化，真机表现为纯色空白）
        MapView(context).also { it.onCreate(null) }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        // 离开地图页时 MapView 已 onDestroy → 后续 Activity 生命周期回调不得再转发（N-4 防崩溃）
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { runCatching { mapView.onStart() } }
            override fun onResume(owner: LifecycleOwner) { runCatching { mapView.onResume() } }
            override fun onPause(owner: LifecycleOwner) { runCatching { mapView.onPause() } }
            override fun onStop(owner: LifecycleOwner) { runCatching { mapView.onStop() } }
            override fun onDestroy(owner: LifecycleOwner) { runCatching { mapView.onDestroy() } }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(mapView) {
        mapView.getMapAsync { map ->
            mapLibre = map
            // OPT-V12-01：样式统一由下方 LaunchedEffect 应用（单点入口，避免重复 setStyle 导致星标图层丢失）
            // OPT-V12-01（CHG-57）：点击星标 → 气泡卡（只消费命中点击；不移动准星、不发选点请求）
            map.addOnMapClickListener { latLng ->
                val screenPoint = map.projection.toScreenLocation(latLng)
                val id = runCatching {
                    map.queryRenderedFeatures(screenPoint, FavoriteStarLayer.LAYER_ID)
                        .firstOrNull()
                        ?.let { FavoriteStarLayer.starIdOf(it.getNumberProperty("spotId")) }
                }.getOrNull()
                val star = id?.let { spotId -> latestStars.firstOrNull { it.id == spotId } }
                if (star != null) {
                    tappedStar = star
                    true
                } else {
                    false
                }
            }
            map.addOnMapLongClickListener { latLng ->
                crosshair.onLongPress(GeoPoint(latLng.latitude, latLng.longitude))
                map.animateCamera(CameraUpdateFactory.newLatLng(latLng))
                true
            }
            map.addOnCameraMoveStartedListener { reason ->
                // CHG-35：仅用户手势导致的相机移动才允许 Idle → Preview（程序动画不误建预览）。
                // MapLibre 11.8 常量：REASON_API_GESTURE=1（手势）/ REASON_DEVELOPER_ANIMATION=2 / REASON_API_ANIMATION=3
                lastMoveWasGesture = reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
            }
            // CHG-09③（NEW-05）：相机坐标只在 **onCameraIdle** 采样（最小正确方案）。
            // animateCamera 飞行与手势拖动的中间帧都不再喂给状态机（onCameraMoveListener 已移除），
            // 飞行结束 idle 时 target 恰为动画目标坐标 → 等价于「飞行期间 center 锁定到目标」；
            // 长按/搜索/定位先同步设好 Preview 点，idle 回调送同一坐标 → no-op，不会抖动。
            map.addOnCameraIdleListener {
                // UX-18：程序驱动定位/返回动画不产生选点状态变化（准星唯一真源 F-1.4 不破坏）
                if (suppressNextIdle) {
                    suppressNextIdle = false
                    return@addOnCameraIdleListener
                }
                val target = map.cameraPosition.target
                if (target != null) {
                    // OPT-V12-01：回报相机中心（:app 用于「星标 → 详情 → 返回地图」相机位置不变）
                    latestCameraTarget(GeoPoint(target.latitude, target.longitude))
                    // CHG-35：冷启动/返回地图（Idle 态）用户拖动 → 进入 Preview，信息条随中心更新
                    crosshair.onMapMoved(
                        GeoPoint(target.latitude, target.longitude),
                        fromUserGesture = lastMoveWasGesture,
                    )
                }
            }
        }
        // 不在离开组合时销毁 MapView：MapView.onDestroy 仅随 Activity 生命周期调用；
        // 提前销毁会让下一次重建的 MapView 渲染器不出图（真机复现纯色空白）
        onDispose { }
    }

    // OPT-V12-01（CHG-57）：重进地图首帧强制重绘（MapView 重建后渲染器偶发不出图，真机复现纯色空白；
// 零时长相机移动只触发重绘，不改变位置/准星语义）
    LaunchedEffect(mapLibre) {
        val map = mapLibre ?: return@LaunchedEffect
        repeat(3) { attempt ->
            kotlinx.coroutines.delay(if (attempt == 0) 400L else 900L)
            runCatching {
                map.cameraPosition.target?.let { target ->
                    map.animateCamera(CameraUpdateFactory.newLatLng(target), 0)
                }
            }
        }
    }

    // UX-19（OPT-V11-19）：样式切换即刻生效（重进地图同样生效；attribution 由 MapLibre 默认控件保持）
    // OPT-V12-01：**唯一样式应用入口**——首次建图与切换样式都走这里（带回调安装星标图层），
    // 避免"建图时 setStyle + 该效果再 setStyle"的竞态（第二次 setStyle 会丢弃星标图层）。
    LaunchedEffect(styleUrl, mapLibre, basemapRetryTick) {
        val map = mapLibre ?: return@LaunchedEffect
        basemapUnreachable = false
        if (basemapRetryTick > 0) appliedStyleUrl = null
        if (appliedStyleUrl != styleUrl) {
            // §12 红线：底图样式自带 attribution，由 MapLibre 默认控件渲染，禁止关闭或遮挡
            map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                installStarLayer(style)
                // 重进地图的首帧刷新：MapView 重建后渲染器可能不立即出图（真机复现纯色空白），
                // 以零时长相机移动强制重绘（位置不变；触摸/准星语义不受影响）
                map.cameraPosition.target?.let { target ->
                    runCatching { map.animateCamera(CameraUpdateFactory.newLatLng(target), 0) }
                }
            }
            appliedStyleUrl = styleUrl
        }
        // OPT-A-11：**先让地图起步再探活**（探测有 6s 超时，绝不能挡首帧）。
        // 判据取「样式源探活」，与瓦片渲染无耦合：源不可达时用户看到的就是空白，
        // 提示只是把"为什么白"讲清楚，并给一个重试动作。
        kotlinx.coroutines.delay(1_500)
        if (!probeBasemapStyle(styleUrl)) {
            basemapUnreachable = true
        }
    }

    // OPT-V12-01（CHG-57）：收藏点增删 → GeoJSON 即时更新（全本地读侧，端侧零网络请求）
    // 注意：`starStyle` 必须作为 key——样式异步加载完成前本效果会早退，样式就绪后需重跑补数据
    LaunchedEffect(favoriteStars, mapLibre, starStyle) {
        val style = starStyle ?: return@LaunchedEffect
        val source = style.getSourceAs<GeoJsonSource>(FavoriteStarLayer.SOURCE_ID) ?: return@LaunchedEffect
        source.setGeoJson(FavoriteStarLayer.geoJson(favoriteStars))
        // 已删除的点：气泡卡同步消失（GWT：删除 1 条星标即时消失）
        tappedStar?.let { tapped ->
            if (favoriteStars.none { it.id == tapped.id }) tappedStar = null
        }
    }

    // UX-18（OPT-V11-18）①：冷启动已授权 → 相机动画到当前位置（≤800ms；不设准星/不进 Preview）
    var autoLocated by remember { mutableStateOf(false) }
    LaunchedEffect(permissionState, mapLibre, locationController) {
        val map = mapLibre ?: return@LaunchedEffect
        if (autoLocated || initialCameraTarget != null) return@LaunchedEffect
        val controller = locationController ?: return@LaunchedEffect
        if (permissionState !is PermissionUiState.Granted) return@LaunchedEffect
        autoLocated = true
        controller.requestCurrentLocation { fix ->
            fix?.let {
                lastFix = it
                suppressNextIdle = true
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), 12.0), 800)
            }
        }
    }

    // UX-18（OPT-V11-18）②：返回地图 → 相机回到上一查看点 / lastViewed 最新点（无目标维持世界视图）
    LaunchedEffect(initialCameraTarget, mapLibre) {
        val map = mapLibre ?: return@LaunchedEffect
        val target = initialCameraTarget ?: return@LaunchedEffect
        suppressNextIdle = true
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lon), 11.0), 800)
    }

    /** CHG-09①：水体判定（§6.9）。marineHasValue=null（M3 无海洋数据 → 探针路径）；异常不中断确认。 */
    suspend fun classifyWaterBody(spot: GeoPoint): WaterBodyType =
        waterBodyResolver?.let { resolver ->
            runCatching { resolver.classify(spot.lat, spot.lon, pendingElevation) }.getOrNull()
        } ?: WaterBodyType.UNKNOWN

    fun confirmCurrent() {
        val committed = crosshair.onConfirm()
        if (committed is CrosshairState.Confirmed) {
            val spot = committed.spot
            confirmedSpot = spot
            if (!reverseGeoEnabled) {
                // 水体判定与逆地理无关（§6.9 红线：判定只看海洋字段/探针，禁用逆地理结果反推）
                confirmedPlace = null
                scope.launch {
                    val water = classifyWaterBody(spot)
                    onSpotConfirmed(SpotConfirmed(point = spot, place = null, waterBody = water))
                }
                return
            }
            // §5.8/F-1.4：逆地理仅在用户显式确认选点后发起，预览期间不发请求
            resolving = true
            scope.launch {
                val place = runCatching { placeResolver.resolve(spot.lat, spot.lon) }.getOrNull()
                confirmedPlace = place
                val water = classifyWaterBody(spot)
                resolving = false
                onSpotConfirmed(SpotConfirmed(point = spot, place = place, waterBody = water))
            }
        }
    }

    fun locateMe() {
        val controller = locationController ?: return
        controller.requestCurrentLocation { fix ->
            lastFix = fix
            fix?.let {
                // CHG-09⑦（NEW-07）：定位成功 → 坐标送准星进 Preview（与长按同链路）。
                // 不自动确认（F-1.4 语义一致：提交必须经用户点「确定」，预览期间不切换已确认数据）。
                pendingElevation = null
                crosshair.onLongPress(GeoPoint(it.lat, it.lon))
                mapLibre?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), 14.0),
                )
            }
        }
    }

    // 信息条（永不空白）：Preview 且已有确认点时仍显示已确认数据（F-1.4）
    val infoState: InfoBarState = when (val cs = crosshairState) {
        is CrosshairState.Confirmed ->
            buildInfoBarState(InfoBarMode.CONFIRMED, cs.spot.lat, cs.spot.lon, confirmedPlace, reverseGeoEnabled, localTideSpotStrings())
        is CrosshairState.Preview ->
            if (confirmedSpot != null) {
                buildInfoBarState(InfoBarMode.PREVIEW, confirmedSpot?.lat, confirmedSpot?.lon, confirmedPlace, reverseGeoEnabled, localTideSpotStrings())
            } else {
                buildInfoBarState(InfoBarMode.PREVIEW, cs.center.lat, cs.center.lon, null, reverseGeoEnabled, localTideSpotStrings())
            }
        CrosshairState.Idle -> buildInfoBarState(InfoBarMode.CONFIRMED, null, null, null, reverseGeoEnabled, localTideSpotStrings())
    }

    // OPT-V115-02 / S01 C02：搜索面板展开态（默认收起，点顶部「搜索」展开）
    var searchExpanded by remember { mutableStateOf(false) }

    // OPT-V115-01 #1（ts_reverse_geo_off）：逆地理关闭 → 信息条上方一次性提示，5s 自动隐藏
    var showReverseGeoOffHint by remember(reverseGeoEnabled) { mutableStateOf(!reverseGeoEnabled) }
    LaunchedEffect(reverseGeoEnabled) {
        if (reverseGeoEnabled) {
            showReverseGeoOffHint = false
        } else {
            showReverseGeoOffHint = true
            kotlinx.coroutines.delay(5_000)
            showReverseGeoOffHint = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        CrosshairOverlay(
            preview = crosshairState is CrosshairState.Preview,
            modifier = Modifier.fillMaxSize(),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
                // OPT-V115-02 / S01 C02：设计稿顶部工具浮层——搜索框 206×46 rx12 + 3 个 44×46 rx12 方块按钮
                val searchHintText = stringResource(R.string.ts_map_search_hint)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        onClick = { searchExpanded = !searchExpanded },
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                            .vintageCard(shape = RoundedCornerShape(12.dp))
                            .semantics { contentDescription = searchHintText },
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_search_vintage),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                searchHintText,
                                Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    if (permissionState is PermissionUiState.Granted && locationController != null) {
                        // 定位：设计稿为实心主色方块（选中/激活态）
                        TopBarButton(UiR.drawable.ic_target_vintage, stringResource(R.string.ts_map_my_location), filled = true) { locateMe() }
                    }
                    if (onOpenFavorites != null && favoritesLabel.isNotEmpty()) {
                        TopBarButton(UiR.drawable.ic_star_vintage, favoritesLabel, filled = false) { onOpenFavorites.invoke() }
                    }
                    if (onOpenSettings != null && settingsLabel.isNotEmpty()) {
                        TopBarButton(UiR.drawable.ic_settings_vintage, settingsLabel, filled = false) { onOpenSettings.invoke() }
                    }
                }
                if (searchExpanded) {
                    SearchPanel(
                        geocoder = geocoder,
                        onSelect = { candidate ->
                            // F-1.5：选中候选 → 地图飞行并设准星（进入 Preview，待确认）；
                            // CHG-09①：候选自带海拔 → 透出给水体判定
                            pendingElevation = candidate.elevation
                            crosshair.onLongPress(GeoPoint(candidate.lat, candidate.lon))
                            mapLibre?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(LatLng(candidate.lat, candidate.lon), 13.0),
                            )
                            searchExpanded = false
                        },
                    )
                }
                when (val ps = permissionState) {
                    is PermissionUiState.Denied ->
                        TopHint(stringResource(UiR.string.ts_location_denied), isError = ps.permanent)
                    else -> {}
                }
                // OPT-A-11：底图源不可达 → 明确告知 + 一键重试（不给用户看无名白屏）
                if (basemapUnreachable) {
                    val failText = stringResource(R.string.ts_map_basemap_failed)
                    val retryText = stringResource(R.string.ts_map_basemap_retry)
                    Surface(
                        onClick = { basemapRetryTick += 1 },
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .semantics { contentDescription = "$failText $retryText" },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                failText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                retryText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 「下一个最佳窗口」提示条（时间紧迫性优先；与 Top10 横幅互补）
            NextWindowStrip(
                data = nextWindowData,
                onOpenDetail = onOpenFavorite,
            )

            // OPT-V115-02 / S01 C19：Top10 入口（信息条上方，设计 366×48）
            if (onOpenTop10 != null && top10Label.isNotEmpty()) {
                val isBlueTide = LocalSkin.current.id == "f-bluetide"
                val bannerShape = if (isBlueTide) RoundedCornerShape(14.dp) else MaterialTheme.shapes.medium
                Surface(
                    onClick = { onOpenTop10.invoke() },
                    color = if (isBlueTide) Color(0xFF0D47A1) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (isBlueTide) Color.White else MaterialTheme.colorScheme.onSurface,
                    shape = bannerShape,
                    // CHG-93 / 路径 B：A/D/E 双线纸卡；F 回到 handoff 的深蓝实心横幅。
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        .vintageCard(shape = bannerShape),
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_fish_vintage),
                            contentDescription = null,
                            tint = if (isBlueTide) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(width = 24.dp, height = 13.dp),
                        )
                        Text(
                            top10Label,
                            Modifier.padding(start = 8.dp).weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isBlueTide) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "›",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isBlueTide) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            if (showReverseGeoOffHint) {
                val geoOffHint = stringResource(UiR.string.ts_reverse_geo_off)
                Text(
                    geoOffHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .semantics { contentDescription = geoOffHint },
                )
            }

            // OPT-V12-01（CHG-57）：收藏星标气泡卡（名称 + 最佳窗口摘要；不进详情也可关闭）
            tappedStar?.let { star ->
                StarBubbleCard(
                    star = star,
                    canOpenDetail = onOpenFavorite != null,
                    onOpenDetail = {
                        tappedStar = null
                        onOpenFavorite?.invoke(star.id)
                    },
                    onDismiss = { tappedStar = null },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
            }

            InfoBarPanel(
                state = infoState,
                preview = crosshairState is CrosshairState.Preview,
                resolving = resolving,
                onConfirm = { confirmCurrent() },
                onCancel = { crosshair.onCancel() },
                // GAP-03：edge-to-edge 安全区（信息条不被手势条遮挡）
                modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(),
            )

 
            // OPT-V115-02 / S01 C22：来源行 + 署名行
            // 叠在真实瓦片上必须有纸底衬，否则会与地图 POI 注记互相压字；
            // 40dp 底垫把整块抬到 MapLibre 自带 logo 上方。
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 40.dp),
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        stringResource(
                            R.string.ts_map_source_line,
                            Format.MISSING,
                            sourceFetchedAtMillis?.let { Format.clockMillis(it, java.time.ZoneId.systemDefault()) } ?: Format.MISSING,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // OPT-A-10：署名随提供方切换（OSM 数据恒署名，瓦片提供方另署）
                    Text(
                        stringResource(R.string.ts_map_attribution, mapProviderName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** OPT-V115-02 / S01 C02：顶部工具浮层方块按钮（44×46 rx12；filled 为主色实心，其余白底主色图标）。 */
@Composable
private fun TopBarButton(iconRes: Int, description: String, filled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(44.dp, 46.dp).vintageCard(shape = RoundedCornerShape(12.dp)).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 十字准星覆盖层：固定于屏幕中心（地图中心对准式）；Preview 态变琥珀色。 */
@Composable
private fun CrosshairOverlay(preview: Boolean, modifier: Modifier = Modifier) {
    val skinId = LocalSkin.current.id
    val isJournal = skinId == "e-tidejournal"
    val isBlueTide = skinId == "f-bluetide"
    val ring = when {
        isJournal -> MaterialTheme.colorScheme.onSurface
        isBlueTide -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    val dot = when {
        isBlueTide -> MaterialTheme.colorScheme.secondary
        preview -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val paper = MaterialTheme.colorScheme.surface
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = 52.dp.toPx()
        if (!isJournal) {
            drawCircle(
                color = paper,
                radius = 8.dp.toPx(),
                center = Offset(cx, cy),
            )
        }
        drawCircle(
            color = ring,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(
                width = if (isBlueTide) 2.dp.toPx() else 1.6.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            ),
        )
        if (isJournal) {
            // 手帐准星：没有实心点，只有墨线环 + 四向刻度。
            val tick = 7.dp.toPx()
            val gap = 6.dp.toPx()
            listOf(
                Offset(cx, cy - radius - gap) to Offset(cx, cy - radius - gap - tick),
                Offset(cx, cy + radius + gap) to Offset(cx, cy + radius + gap + tick),
                Offset(cx - radius - gap, cy) to Offset(cx - radius - gap - tick, cy),
                Offset(cx + radius + gap, cy) to Offset(cx + radius + gap + tick, cy),
            ).forEach { (start, end) ->
                drawLine(color = ring, start = start, end = end, strokeWidth = 1.6.dp.toPx())
            }
        } else {
            drawCircle(
                color = ring,
                radius = 4.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 1.2.dp.toPx()),
            )
            drawCircle(
                color = dot,
                radius = if (isBlueTide) 5.dp.toPx() else 3.dp.toPx(),
                center = Offset(cx, cy),
            )
        }
    }
}

/** 底部信息条（§8.1）：经纬度 · 名称 · [确定]/[取消]；不阻塞地图手势区域之外的操作。 */
@Composable
private fun InfoBarPanel(
    state: InfoBarState,
    preview: Boolean,
    resolving: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // OPT-V115-02 / S01 C03：设计稿 = 236×48 浮条（名称+坐标）＋ 独立操作面板
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 236.dp).vintageCard(shape = RoundedCornerShape(16.dp)),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    state.mainText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                state.subText?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (resolving) {
                    Text(
                        stringResource(UiR.string.ts_resolving),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.hint?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (preview) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text(stringResource(UiR.string.ts_cancel))
                    }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.ts_map_confirm))
                    }
                }
            }
        }
    }
}
/** 顶部提示浮层（权限拒绝 / 定位精度）。 */
@Composable
private fun TopHint(text: String, isError: Boolean) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * OPT-V12-01（CHG-57）：收藏星标气泡卡（设计：先弹卡、再进详情；不移动准星）。
 * 摘要缺失时显示「暂无窗口摘要」+ 坐标，禁编造数据。
 */
@Composable
private fun StarBubbleCard(
    star: FavoriteStar,
    canOpenDetail: Boolean,
    onOpenDetail: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.semantics { contentDescription = star.name },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                star.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                star.summary ?: stringResource(R.string.ts_map_star_no_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ts_map_star_close))
                }
                if (canOpenDetail) {
                    TextButton(onClick = onOpenDetail) {
                        Text(stringResource(R.string.ts_map_star_detail))
                    }
                }
            }
        }
    }
}

/** OPT-V12-01：星形图标 bitmap（琥珀色，与预览准星同色系；不占用冻结的档位色值）。 */
private fun starBitmap(sizePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFC107.toInt()
        style = Paint.Style.FILL
        setShadowLayer(2f, 0f, 1f, 0x33000000)
    }
    val path = Path()
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val outer = sizePx * 0.46f
    val inner = outer * 0.42f
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = Math.toRadians((-90 + i * 36).toDouble())
        val x = cx + (radius * Math.cos(angle)).toFloat()
        val y = cy + (radius * Math.sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    canvas.drawPath(path, paint)
    return bitmap
}
