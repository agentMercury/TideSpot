package com.tidespot.feature.map

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** §7.1：CARTO Positron 免费矢量样式（已验证 200），无任何 Key。 */
const val CARTO_POSITRON_STYLE_URL = com.tidespot.core.data.remote.TideSpotUrls.CARTO_POSITRON_STYLE_URL

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
    /** UX-19（OPT-V11-19）：底图样式 URL（默认 Voyager 彩色；URL 单点在 TideSpotUrls）。 */
    styleUrl: String = com.tidespot.core.data.remote.TideSpotUrls.CARTO_VOYAGER_STYLE_URL,
    /** UX-18（OPT-V11-18）：返回地图时的相机目标（上一查看点 / lastViewed 最新点；null = 世界视图）。 */
    initialCameraTarget: GeoPoint? = null,
    /** OPT-V115-02 / S01 C02：顶部工具浮层动作（收藏/设置/Top10 由 :app 注入；null = 不渲染）。 */
    onOpenFavorites: (() -> Unit)? = null,
    favoritesLabel: String = "",
    onOpenSettings: (() -> Unit)? = null,
    settingsLabel: String = "",
    onOpenTop10: (() -> Unit)? = null,
    top10Label: String = "",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

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
    /** UX-18：程序驱动相机移动（冷启动定位/返回定位）时抑制 idle → 准星状态机（F-1.4 唯一真源不受影响）。 */
    var suppressNextIdle by remember { mutableStateOf(false) }
    /** CHG-35：最近一次相机移动是否来自用户手势（决定 Idle 是否进入 Preview）。 */
    var lastMoveWasGesture by remember { mutableStateOf(false) }

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
        MapView(context)
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = mapView.onStart()
            override fun onResume(owner: LifecycleOwner) = mapView.onResume()
            override fun onPause(owner: LifecycleOwner) = mapView.onPause()
            override fun onStop(owner: LifecycleOwner) = mapView.onStop()
            override fun onDestroy(owner: LifecycleOwner) = mapView.onDestroy()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(mapView) {
        mapView.getMapAsync { map ->
            mapLibre = map
            // §12 红线：底图样式自带 attribution，由 MapLibre 默认控件渲染，禁止关闭或遮挡
            map.setStyle(Style.Builder().fromUri(styleUrl))
            appliedStyleUrl = styleUrl
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
                    // CHG-35：冷启动/返回地图（Idle 态）用户拖动 → 进入 Preview，信息条随中心更新
                    crosshair.onMapMoved(
                        GeoPoint(target.latitude, target.longitude),
                        fromUserGesture = lastMoveWasGesture,
                    )
                }
            }
        }
        onDispose { }
    }

    // UX-19（OPT-V11-19）：样式切换即刻生效（重进地图同样生效；attribution 由 MapLibre 默认控件保持）
    LaunchedEffect(styleUrl, mapLibre) {
        val map = mapLibre ?: return@LaunchedEffect
        if (appliedStyleUrl != styleUrl) {
            map.setStyle(Style.Builder().fromUri(styleUrl))
            appliedStyleUrl = styleUrl
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
            buildInfoBarState(InfoBarMode.CONFIRMED, cs.spot.lat, cs.spot.lon, confirmedPlace, reverseGeoEnabled)
        is CrosshairState.Preview ->
            if (confirmedSpot != null) {
                buildInfoBarState(InfoBarMode.PREVIEW, confirmedSpot?.lat, confirmedSpot?.lon, confirmedPlace, reverseGeoEnabled)
            } else {
                buildInfoBarState(InfoBarMode.PREVIEW, cs.center.lat, cs.center.lon, null, reverseGeoEnabled)
            }
        CrosshairState.Idle -> buildInfoBarState(InfoBarMode.CONFIRMED, null, null, null, reverseGeoEnabled)
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
                // OPT-V115-02 / S01 C02：顶部工具浮层（定位 / 搜索 / 收藏 / 设置，16dp 圆角卡片）
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (permissionState is PermissionUiState.Granted && locationController != null) {
                            TopBarAction("◎", stringResource(R.string.ts_map_my_location)) { locateMe() }
                        }
                        TopBarAction("⌕", stringResource(R.string.ts_map_search_hint)) {
                            searchExpanded = !searchExpanded
                        }
                        Spacer(Modifier.weight(1f))
                        if (onOpenFavorites != null && favoritesLabel.isNotEmpty()) {
                            TopBarAction("★", favoritesLabel) { onOpenFavorites.invoke() }
                        }
                        if (onOpenSettings != null && settingsLabel.isNotEmpty()) {
                            TopBarAction("⚙", settingsLabel) { onOpenSettings.invoke() }
                        }
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
                    PermissionUiState.Granted -> lastFix?.let { fix ->
                        TopHint(
                            stringResource(R.string.ts_map_location_ok, fix.accuracyMeters?.toInt() ?: 0),
                            isError = false,
                        )
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // OPT-V115-02 / S01 C19：Top10 入口（信息条上方，设计 366×48）
            if (onOpenTop10 != null && top10Label.isNotEmpty()) {
                Surface(
                    onClick = { onOpenTop10.invoke() },
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(top10Label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "›",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            InfoBarPanel(
                state = infoState,
                preview = crosshairState is CrosshairState.Preview,
                resolving = resolving,
                onConfirm = { confirmCurrent() },
                onCancel = { crosshair.onCancel() },
                // GAP-03：edge-to-edge 安全区（信息条不被手势条遮挡）
                modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(),
            )
        }
    }
}

/** OPT-V115-02 / S01 C02：顶部工具浮层单个动作（图标 + TalkBack 描述）。 */
@Composable
private fun TopBarAction(icon: String, description: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(icon, style = MaterialTheme.typography.titleLarge)
    }
}

/** 十字准星覆盖层：固定于屏幕中心（地图中心对准式）；Preview 态变琥珀色。 */
@Composable
private fun CrosshairOverlay(preview: Boolean, modifier: Modifier = Modifier) {
    val color = if (preview) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val gap = 26f
        val len = 44f
        val strokeWidth = 4f
        drawLine(color, Offset(cx - gap - len, cy), Offset(cx - gap, cy), strokeWidth)
        drawLine(color, Offset(cx + gap, cy), Offset(cx + gap + len, cy), strokeWidth)
        drawLine(color, Offset(cx, cy - gap - len), Offset(cx, cy - gap), strokeWidth)
        drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + gap + len), strokeWidth)
        drawCircle(color, radius = 5f, center = Offset(cx, cy))
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                state.mainText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.subText != null) {
                Text(
                    state.subText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            if (preview) {
                Row {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text(stringResource(UiR.string.ts_cancel)) }
                    Button(onClick = onConfirm) { Text(stringResource(R.string.ts_map_confirm)) }
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
