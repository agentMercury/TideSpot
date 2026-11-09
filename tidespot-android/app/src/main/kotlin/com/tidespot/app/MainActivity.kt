package com.tidespot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tidespot.core.data.repo.TideSpotRepository
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.theme.TideSpotTheme
import com.tidespot.feature.detail.detail.DetailScreen
import com.tidespot.feature.detail.detail.DetailViewModel
import com.tidespot.feature.favorites.favorites.FavoritesSaveRules
import com.tidespot.feature.favorites.favorites.FavoritesScreen
import com.tidespot.feature.favorites.favorites.FavoritesViewModel
import com.tidespot.feature.map.MapScreen
import com.tidespot.feature.map.model.SpotConfirmed
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
        val graph = AppGraph(this)
        setContent {
            TideSpotTheme {
                AppRoot(graph)
            }
        }
    }
}

/** 底部导航四项（顺序 = §8.1 主流程：地图选点 → 收藏 → 详情 → 设置）。 */
private val TAB_ORDER = listOf(AppPage.MAP, AppPage.FAVORITES, AppPage.DETAIL, AppPage.SETTINGS)

private val TAB_LABELS = mapOf(
    AppPage.MAP to "地图",
    AppPage.FAVORITES to "收藏",
    AppPage.DETAIL to "详情",
    AppPage.SETTINGS to "设置",
)

@Composable
private fun AppRoot(graph: AppGraph) {
    var navState by remember { mutableStateOf(AppNavState()) }
    var settingsState by remember { mutableStateOf(AppSettings.read(graph.settingsStore)) }
    // OPT-M5-07：确认选点 → 收藏对话框（预填逆地理名称）
    var pendingConfirm by remember { mutableStateOf<SpotConfirmed?>(null) }

    val favoritesVm = remember { FavoritesViewModel(graph.repository) }
    // CHG-12④b：ZoneId 由 DetailViewModel 构造器默认值按 spot.tzOffsetSec 单源解析；
    // 设置 detailDays 变化或换点 → 重建（缓存先行语义不变）
    val detailSpot = navState.detailSpot
    val detailVm = remember(detailSpot?.id, settingsState.detailDays) {
        detailSpot?.let { DetailViewModel(graph.repository, it, daysRange = settingsState.detailDays) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.semantics { contentDescription = "底部导航：地图、收藏、详情、设置" },
            ) {
                TAB_ORDER.forEach { page ->
                    NavigationBarItem(
                        icon = { Text(TAB_LABELS.getValue(page).take(1)) },
                        selected = navState.page == page,
                        onClick = { navState = AppNav.reduce(navState, AppNavEvent.TabSelected(page)) },
                        // 详情不是常驻 tab：无详情上下文时禁用（不变量 1）
                        enabled = page != AppPage.DETAIL || navState.detailSpot != null,
                        label = { Text(TAB_LABELS.getValue(page)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (navState.page) {
                AppPage.MAP -> MapScreen(
                    placeResolver = graph.placeResolver,
                    geocoder = graph.geocoder,
                    locationController = graph.locationController,
                    waterBodyResolver = graph.waterBodyResolver,
                    // N16：逆地理总开关（关 → MapScreen 内短路走 RG-D 兜底文案）
                    reverseGeoEnabled = settingsState.reverseGeoEnabled,
                    onSpotConfirmed = { confirmed -> pendingConfirm = confirmed },
                    modifier = Modifier.fillMaxSize(),
                )

                AppPage.FAVORITES -> FavoritesScreen(
                    viewModel = favoritesVm,
                    // 导航闭环：收藏列表 → 详情（markViewed 先行，F-2.4 最近查看排序）
                    onOpenSpot = { spot ->
                        favoritesVm.markViewed(spot.id)
                        navState = AppNav.reduce(navState, AppNavEvent.OpenDetail(spot))
                    },
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
                        )
                    }
                }

                AppPage.SETTINGS -> SettingsPage(
                    state = settingsState,
                    onChange = { next ->
                        persistSettings(graph, next)
                        settingsState = next
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
                navState = AppNav.reduce(navState, AppNavEvent.OpenDetail(saved))
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
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = "返回收藏列表" },
            ) {
                Text("← 返回收藏")
            }
        }
        DetailScreen(spot = spot, viewModel = viewModel, modifier = Modifier.weight(1f))
        Text(
            TideSpotStrings.MODEL_ATTRIBUTION,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SettingsPage(state: SettingsState, onChange: (SettingsState) -> Unit) {
    SettingsScreen(
        state = state,
        onUnitSystemChange = { unit -> onChange(SettingsLogic.withUnitSystem(state, unit)) },
        onDetailDaysChange = { days -> onChange(SettingsLogic.withDetailDays(state, days)) },
        onPrefetchChange = { enabled -> onChange(SettingsLogic.withPrefetch(state, enabled)) },
        onReverseGeoChange = { enabled -> onChange(SettingsLogic.withReverseGeo(state, enabled)) },
        onApiKeyChange = { raw -> onChange(SettingsLogic.withApiKey(state, raw)) },
        modifier = Modifier.fillMaxSize(),
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

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("确认收藏") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "收藏点名称" },
                )
                Text(
                    String.format(java.util.Locale.US, "坐标 %.5f, %.5f", point.lat, point.lon),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "水体：" + confirmed.waterBody.name,
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
            TextButton(
                enabled = !saving,
                onClick = {
                    if (!FavoritesSaveRules.canCreate(point.lat, point.lon)) {
                        error = FavoritesSaveRules.REJECT_MESSAGE
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
                            error = e.message ?: "保存失败，请重试"
                        } finally {
                            saving = false
                        }
                    }
                },
            ) {
                Text(if (saving) "保存中…" else "收藏")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
