package com.tidespot.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
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
import com.tidespot.core.ui.theme.TideSpotSkins

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
    onMapStyleChange: (MapStyle) -> Unit,
    /** SKIN-3 / OPT-A-09：选皮肤（只写 skinId，不动明暗）。 */
    onSkinChange: (String) -> Unit,
    /** OPT-A-03：明暗三选（只写 themeMode，不动皮肤）。 */
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    /** CHG-25（P0-1）：Copernicus 凭据版块槽位（:app 注入 [CopernicusCredentialSection]；null = 不渲染，零行为漂移）。 */
    credentialSection: (@Composable () -> Unit)? = null,
) {
    val screenDesc = stringResource(R.string.ts_settings_content_desc)
    Column(
        modifier = modifier.fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .semantics { contentDescription = screenDesc }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.ts_settings_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 14.dp),
        )

        // OPT-V115-02 / S09 C16：六版块收进一张卡片（设计 366×700 rx16）
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ── 显示 ──
        SectionTitle(stringResource(R.string.ts_settings_display))
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
        // UX-19（OPT-V11-19 / D8 + OPT-A-03）：地图样式三选。
        // 未改过时高亮「跟明暗自动切」的那一项；点任意项即固定为显式选择。
        Text(stringResource(R.string.ts_settings_map_style), style = MaterialTheme.typography.titleSmall)
        val darkTheme = SettingsLogic.resolveDarkTheme(state.themeMode, isSystemInDarkTheme())
        val activeMapStyle = SettingsLogic.mapStyleChipSelection(state, darkTheme)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MapStyle.entries.forEach { style ->
                val label = stringResource(style.labelRes)
                val styleDesc = stringResource(
                    R.string.ts_settings_map_style_desc,
                    label,
                    style.description,
                )
                FilterChip(
                    selected = activeMapStyle == style,
                    onClick = { onMapStyleChange(style) },
                    label = { Text(label) },
                    modifier = Modifier.semantics { contentDescription = styleDesc },
                )
            }
        }
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
        // ── 外观 · 皮肤（SKIN-3 / OPT-A-09）：按皮肤名下拉选择 ──
        Text(stringResource(R.string.ts_settings_skin), style = MaterialTheme.typography.titleSmall)
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
        SectionDivider()

        // ── 数据与网络 ──
        SectionTitle(stringResource(R.string.ts_settings_data_network))
        val privacyLines = SettingsLogic.privacyLines(state.reverseGeoEnabled, state.prefetchEnabled)
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
        SectionDivider()

        // OPT-A-01 / OPT-A-02（2026-11-12 产品决策）：下线「用例·物种·权重」与「API Key」两块。
        // 算法侧场景预设与 F-3.4 权重逻辑保持不变，仅移除用户可见/可改入口。
        // 历史 Key 的一次性清理见 AppSettings.clearLegacyApiKey（启动时执行，不入库不上传）。

        // ── 开源许可 ──
        SectionTitle(stringResource(R.string.ts_settings_licenses))
        SettingsLogic.LICENSES.forEach { lic ->
            Text(lic.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${stringResource(lic.roleRes)} · ${stringResource(lic.licenseRes)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(lic.attribution, style = MaterialTheme.typography.labelSmall)
        }
        SectionDivider()

        // ── 隐私逐源披露 ──
        SectionTitle(stringResource(R.string.ts_settings_privacy))
        privacyLines.forEach { line ->
            Text(stringResource(line.sourceRes), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.ts_settings_privacy_sends, stringResource(line.sendsRes)),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(stringResource(line.toggleNoteRes), style = MaterialTheme.typography.labelSmall)
        }

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
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
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