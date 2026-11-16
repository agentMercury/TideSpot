package com.tidespot.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tidespot.core.ui.strings.localTideSpotStrings
import com.tidespot.core.ui.theme.LocalSkin
import com.tidespot.core.ui.theme.TideSpotSkins
import com.tidespot.core.ui.theme.VintageDashedDivider
import com.tidespot.core.ui.theme.VintagePageHeader
import com.tidespot.core.ui.theme.VintageSectionCard

/**
 * SettingsScreen（CHG-16⑱ 薄组装）：所有状态经 [SettingsState] 注入、交互回调上抛，
 * 业务语义（天数收敛/单位换算/清洗）全部在 [SettingsLogic] 纯函数（本组件零逻辑）。
 *
 * 版块：显示（单位制/详情天数）｜数据与网络（预取/逆地理）｜物种与权重（占位 v1.1）｜
 * 可选 API Key（占位）｜开源许可｜隐私逐源披露。
 *
 * OPT-V11-13：全部用户可见文案走资源（`stringResource`），本文件名零中文字面量。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    state: SettingsState,
    onUnitSystemChange: (UnitSystem) -> Unit,
    onDetailDaysChange: (Int) -> Unit,
    onPrefetchChange: (Boolean) -> Unit,
    onReverseGeoChange: (Boolean) -> Unit,
    /** UX-19（OPT-V11-19）：地图样式三选（彩色/素色/深色）。 */
    /** UX-19 / OPT-A-12：地图样式四选（跟随主题 / 彩色 / 素色 / 深色）。 */
    onMapStyleChange: (MapStyleChoice) -> Unit,
    /** OPT-A-10：地图来源（OpenFreeMap / CARTO / 未来新增）。 */
    onMapProviderChange: (MapProvider) -> Unit,
    /** SKIN-3 / OPT-A-09：选皮肤（只写 skinId，不动明暗）。 */
    onSkinChange: (String) -> Unit,
    /** OPT-A-03：明暗三选（只写 themeMode，不动皮肤）。 */
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    /** CHG-25（P0-1）：Copernicus 凭据版块槽位（:app 注入 [CopernicusCredentialSection]；null = 不渲染，零行为漂移）。 */
    credentialSection: (@Composable () -> Unit)? = null,
    /** OPT-A-06：请求日志入口点击（null = 不渲染该行；实现与数据源由 :app 注入）。 */
    onOpenFetchLog: (() -> Unit)? = null,
) {
    val screenDesc = stringResource(R.string.ts_settings_content_desc)
    // CHG-93：副标题取皮肤英文名（数据值，非 UI 文案，不触发 i18n / 双端契约 N-5）
    val headerSubtitle = "TideSpot · ${LocalSkin.current.nameEn}"
    // 隐私行同时被「数据与网络」和「隐私逐源披露」两张卡消费，故提到卡片之外
    val privacyLines = SettingsLogic.privacyLines(state.reverseGeoEnabled, state.prefetchEnabled)
    Column(
        modifier = modifier.fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .semantics { contentDescription = screenDesc }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VintagePageHeader(stringResource(R.string.ts_settings_title), headerSubtitle)

        // CHG-93 / 路径 B：六版块拆成六张独立票据卡（原为一张大卡包全部，对齐 settings.png）
        VintageSectionCard(stringResource(R.string.ts_settings_display)) {
        // ── 显示 ──
        Text(stringResource(R.string.ts_settings_unit), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val siDesc = stringResource(R.string.ts_settings_unit_si_desc)
            val imperialDesc = stringResource(R.string.ts_settings_unit_imperial_desc)
            FilterChip(
                selected = state.unitSystem == UnitSystem.SI,
                onClick = { onUnitSystemChange(UnitSystem.SI) },
                label = { Text(stringResource(R.string.ts_settings_unit_si)) },
                modifier = Modifier.semantics { contentDescription = siDesc },
            )
            FilterChip(
                selected = state.unitSystem == UnitSystem.IMPERIAL,
                onClick = { onUnitSystemChange(UnitSystem.IMPERIAL) },
                label = { Text(stringResource(R.string.ts_settings_unit_imperial)) },
                modifier = Modifier.semantics { contentDescription = imperialDesc },
            )
        }
        Text(stringResource(R.string.ts_settings_detail_days), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(7, 10).forEach { d ->
                val daysLabel = stringResource(R.string.ts_settings_days, d)
                val daysDesc = stringResource(R.string.ts_settings_days_desc, d)
                FilterChip(
                    selected = state.detailDays == d,
                    onClick = { onDetailDaysChange(d) },
                    label = { Text(daysLabel) },
                    modifier = Modifier.semantics { contentDescription = daysDesc },
                )
            }
        }
        }
        VintageSectionCard(stringResource(R.string.ts_settings_map_source)) {
        // OPT-A-10：地图来源（提供方可切；未来新增源自动出现在下拉里）
        MapProviderDropdown(current = state.mapProvider, onSelect = onMapProviderChange)
        Text(
            stringResource(R.string.ts_settings_map_source_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // OPT-A-12：地图样式四选（跟随主题 / 彩色 / 素色 / 深色）。
        // 「跟随主题」= 不表态（深色切深色板、浅色切彩色板）；点具体样式即固定，深色不再覆盖。
        Text(stringResource(R.string.ts_settings_map_style), style = MaterialTheme.typography.titleSmall)
        MapStyleChips(
            choice = SettingsLogic.mapStyleChoice(state),
            providerName = state.mapProvider.displayName,
            onSelect = onMapStyleChange,
        )
        Text(
            stringResource(
                if (state.mapStyleExplicit) {
                    R.string.ts_settings_map_style_pinned
                } else {
                    R.string.ts_settings_map_style_auto
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
        // CHG-94：当前皮肤卡对齐设计稿的芥末黄高亮外框
        VintageSectionCard(stringResource(R.string.ts_settings_skin), highlight = true) {
        // ── 外观 · 皮肤（SKIN-3 / OPT-A-09）：按皮肤名下拉选择 ──
        SkinDropdown(currentId = state.skinId, onSelect = onSkinChange)
        Text(
            stringResource(R.string.ts_settings_skin_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // ── 深色模式（OPT-A-03）：跟随系统 / 浅色 / 深色，与皮肤正交 ──
        Text(stringResource(R.string.ts_settings_theme_mode), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ThemeMode.entries.forEach { mode ->
                val labelRes = when (mode) {
                    ThemeMode.SYSTEM -> R.string.ts_settings_theme_system
                    ThemeMode.LIGHT -> R.string.ts_settings_theme_light
                    ThemeMode.DARK -> R.string.ts_settings_theme_dark
                }
                FilterChip(
                    selected = state.themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
        }
        VintageSectionCard(stringResource(R.string.ts_settings_data_network)) {
        // ── 数据与网络 ──
        val prefetchDesc = stringResource(R.string.ts_settings_prefetch_toggle)
        val reverseGeoDesc = stringResource(R.string.ts_settings_reverse_geo_toggle)
        ToggleRow(
            title = stringResource(R.string.ts_settings_prefetch),
            subtitle = stringResource(
                privacyLines.first { it.sourceRes == R.string.ts_privacy_weather_source }.toggleNoteRes,
            ),
            checked = state.prefetchEnabled,
            onChange = onPrefetchChange,
            desc = prefetchDesc,
        )
        ToggleRow(
            title = stringResource(R.string.ts_settings_reverse_geo),
            subtitle = stringResource(R.string.ts_settings_reverse_geo_note),
            checked = state.reverseGeoEnabled,
            onChange = onReverseGeoChange,
            desc = reverseGeoDesc,
        )
        credentialSection?.invoke()
        // OPT-A-06：请求日志入口（源/时刻/状态/是否命中频控；不泄露密钥，仅有 URL hash）
        if (onOpenFetchLog != null) {
            val fetchLogDesc = stringResource(R.string.ts_settings_fetch_log_desc)
            Surface(
                onClick = onOpenFetchLog,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = fetchLogDesc },
            ) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ts_settings_fetch_log), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.ts_settings_fetch_log_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        }
        VintageSectionCard(stringResource(R.string.ts_settings_licenses)) {
        // ── 开源许可 ──
        SettingsLogic.LICENSES.forEach { lic ->
            Text(lic.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${stringResource(lic.roleRes)} · ${stringResource(lic.licenseRes)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(lic.attribution, style = MaterialTheme.typography.labelSmall)
        }
        }
        VintageSectionCard(stringResource(R.string.ts_settings_privacy)) {
        // ── 隐私逐源披露 ──
        privacyLines.forEach { line ->
            Text(stringResource(line.sourceRes), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.ts_settings_privacy_sends, stringResource(line.sendsRes)),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(stringResource(line.toggleNoteRes), style = MaterialTheme.typography.labelSmall)
        }
        }

        // OPT-V115-02 / S09 C06：底部常驻免责入口条（设计 366×56，与详情页同形态）
        SectionDivider()
        var showDisclaimer by remember { mutableStateOf(false) }
        val disclaimerText = localTideSpotStrings()
        Surface(
            onClick = { showDisclaimer = true },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
                .semantics { contentDescription = disclaimerText.disclaimerEntry },
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(disclaimerText.disclaimerEntry, style = MaterialTheme.typography.titleSmall)
                Text(
                    "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showDisclaimer) {
            ModalBottomSheet(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),onDismissRequest = { showDisclaimer = false }) {
                Column(Modifier.padding(12.dp).navigationBarsPadding()) {
                    Text(disclaimerText.disclaimerTitle, style = MaterialTheme.typography.titleMedium)
                    disclaimerText.disclaimerItems().forEachIndexed { i, line ->
                        Text(
                            "${i + 1}. $line",
                            Modifier.padding(vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
}
}

@Composable
private fun SectionDivider() {
    VintageDashedDivider(Modifier.fillMaxWidth().padding(vertical = 4.dp))
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    desc: String,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = desc },
        )
    }
}

/**
 * 地图样式四选（OPT-A-12）：[MapStyleChoice] 遍历渲染，**新增选项只往枚举加一项**。
 *
 * 用 FlowRow 而非 Row：「跟随主题」比其余三项长，窄屏或大字体缩放下 Row 会把最后一个 chip 顶出屏幕
 * （同类溢出此前在皮肤下拉上真机出现过，见 SKIN-DEV-STANDARD 反模式 12）。换行不影响语义分组。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapStyleChips(
    choice: MapStyleChoice,
    providerName: String,
    onSelect: (MapStyleChoice) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MapStyleChoice.entries.forEach { item ->
            val label = stringResource(item.labelRes)
            val desc = stringResource(R.string.ts_settings_map_style_desc, label, providerName)
            FilterChip(
                selected = choice == item,
                onClick = { onSelect(item) },
                label = { Text(label) },
                modifier = Modifier.semantics { contentDescription = desc },
            )
        }
    }
}

/**
 * 地图来源下拉（OPT-A-10）：按**提供方名**切换底图瓦片来源。
 *
 * - 遍历 [MapProvider.entries]，**新增提供方只往枚举里加一项**，本组件零改动；
 * - 切换只写 `settings.mapProvider`，不碰样式与明暗（三个维度各自独立）；
 * - 现有两项：OpenFreeMap（默认，实测可达）/ CARTO（海外或恢复后使用）。
 */
@Composable
private fun MapProviderDropdown(current: MapProvider, onSelect: (MapProvider) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val desc = stringResource(R.string.ts_settings_map_source_desc, current.displayName)
    Box(Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = desc },
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(current.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "▾",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            MapProvider.entries.forEach { provider ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (provider == current) "${provider.displayName}  ✓" else provider.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(provider)
                    },
                )
            }
        }
    }
}

/**
 * 皮肤下拉（OPT-A-09 简化形态）：不做独立选择页，设置页内按**皮肤名**直接切换。
 *
 * - 显示名取 `skin.name`；唯一性由 [TideSpotSkins.validationErrors] 在类加载时校验，
 *   重名会立即失败而不是让用户在两个同名项里瞎选；
 * - 首版只有 A 一款，下拉里仅一项；加皮肤只往 `TideSpotSkins.all` 追加，本组件零改动；
 * - 切换只写 `settings.skinId`，不碰明暗（三轴正交）。
 */
@Composable
private fun SkinDropdown(currentId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = TideSpotSkins.byId(currentId)
    val desc = stringResource(R.string.ts_settings_skin_desc, current.name)
    Box(Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = desc },
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(current.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "▾",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // D3（真机对拍）：默认宽度只包内容，会把下方说明文字“切”掉一半露出右侧；
            // 拉到接近整行，遮挡关系变成「整行被盖住」，视觉上更干净。
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            TideSpotSkins.all.forEach { skin ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (skin.id == current.id) "${skin.name}  ✓" else skin.name,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(skin.id)
                    },
                )
            }
        }
    }
}