package com.tidespot.feature.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
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
import com.tidespot.core.ui.R as UiR
import com.tidespot.core.ui.strings.localTideSpotStrings

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
    onApiKeyChange: (String?) -> Unit,
    /** UX-19（OPT-V11-19）：地图样式三选（彩色/素色/深色）。 */
    onMapStyleChange: (MapStyle) -> Unit,
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
        // UX-19（OPT-V11-19 / D8）：地图样式三选（默认彩色；深色系统不覆盖用户选择）
        Text(stringResource(R.string.ts_settings_map_style), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MapStyle.entries.forEach { style ->
                val label = stringResource(style.labelRes)
                val styleDesc = stringResource(
                    R.string.ts_settings_map_style_desc,
                    label,
                    style.description,
                )
                FilterChip(
                    selected = state.mapStyle == style,
                    onClick = { onMapStyleChange(style) },
                    label = { Text(label) },
                    modifier = Modifier.semantics { contentDescription = styleDesc },
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

        // ── 用例 / 物种 / 权重（占位） ──
        SectionTitle(stringResource(R.string.ts_settings_use_cases))
        Text(
            stringResource(UiR.string.ts_placeholder_v11),
            style = MaterialTheme.typography.bodySmall,
        )
        SectionDivider()

        // ── 可选 API Key（占位） ──
        SectionTitle(stringResource(R.string.ts_settings_api_key))
        var apiKeyDraft by remember(state.apiKey) { mutableStateOf(state.apiKey ?: "") }
        val apiKeyInputDesc = stringResource(R.string.ts_settings_api_key_input)
        OutlinedTextField(
            value = apiKeyDraft,
            onValueChange = { apiKeyDraft = it },
            label = { Text(stringResource(R.string.ts_settings_api_key_optional)) },
            modifier = Modifier.fillMaxWidth()
                .semantics { contentDescription = apiKeyInputDesc },
            supportingText = { Text(stringResource(R.string.ts_settings_api_key_empty)) },
        )
        androidx.compose.material3.Button(onClick = { onApiKeyChange(apiKeyDraft) }) {
            Text(stringResource(R.string.ts_settings_api_key_save))
        }
        SectionDivider()

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
            ModalBottomSheet(onDismissRequest = { showDisclaimer = false }) {
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