package com.tidespot.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * SettingsScreen（CHG-16⑱ 薄组装）：所有状态经 [SettingsState] 注入、交互回调上抛，
 * 业务语义（天数收敛/单位换算/清洗）全部在 [SettingsLogic] 纯函数（本组件零逻辑）。
 *
 * 版块：显示（单位制/详情天数）｜数据与网络（预取/逆地理）｜物种与权重（占位 v1.1）｜
 * 可选 API Key（占位）｜开源许可｜隐私逐源披露。
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onUnitSystemChange: (UnitSystem) -> Unit,
    onDetailDaysChange: (Int) -> Unit,
    onPrefetchChange: (Boolean) -> Unit,
    onReverseGeoChange: (Boolean) -> Unit,
    onApiKeyChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    /** CHG-25（P0-1）：Copernicus 凭据版块槽位（:app 注入 [CopernicusCredentialSection]；null = 不渲染，零行为漂移）。 */
    credentialSection: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .semantics { contentDescription = "设置页：单位制、详情天数、数据与隐私、开源许可" }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.titleLarge)

        // ── 显示 ──
        SectionTitle("显示")
        Text("单位制", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = state.unitSystem == UnitSystem.SI,
                onClick = { onUnitSystemChange(UnitSystem.SI) },
                label = { Text("公制（°C · km/h · m）") },
                modifier = Modifier.semantics { contentDescription = "单位制：公制" },
            )
            FilterChip(
                selected = state.unitSystem == UnitSystem.IMPERIAL,
                onClick = { onUnitSystemChange(UnitSystem.IMPERIAL) },
                label = { Text("英制（°F · mph · ft）") },
                modifier = Modifier.semantics { contentDescription = "单位制：英制" },
            )
        }
        Text("详情页天数", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(7, 10).forEach { d ->
                FilterChip(
                    selected = state.detailDays == d,
                    onClick = { onDetailDaysChange(d) },
                    label = { Text("$d 天") },
                    modifier = Modifier.semantics { contentDescription = "详情页天数：$d 天" },
                )
            }
        }
        SectionDivider()

        // ── 数据与网络 ──
        SectionTitle("数据与网络")
        ToggleRow(
            title = "后台预取",
            subtitle = SettingsLogic.privacyLines(state.reverseGeoEnabled, state.prefetchEnabled)
                .first { it.source.startsWith("天气") }.toggleNote,
            checked = state.prefetchEnabled,
            onChange = onPrefetchChange,
            desc = "后台预取开关",
        )
        ToggleRow(
            title = "逆地理名称（RG-A BigDataCloud / RG-B Photon）",
            subtitle = "关 = 位置名称回退坐标文本；逐源发送内容见下方「隐私披露」",
            checked = state.reverseGeoEnabled,
            onChange = onReverseGeoChange,
            desc = "逆地理总开关",
        )
        credentialSection?.invoke()
        SectionDivider()

        // ── 用例 / 物种 / 权重（占位） ──
        SectionTitle("用例 · 物种 · 权重")
        Text("垂钓用例、物种预设与因子权重编辑入口于 v1.1 开放（当前沿收藏点预设生效）。",
            style = MaterialTheme.typography.bodySmall)
        SectionDivider()

        // ── 可选 API Key（占位） ──
        SectionTitle("API Key（可选）")
        var apiKeyDraft by remember(state.apiKey) { mutableStateOf(state.apiKey ?: "") }
        OutlinedTextField(
            value = apiKeyDraft,
            onValueChange = { apiKeyDraft = it },
            label = { Text("可选 API Key（当前无消费方，仅保存）") },
            modifier = Modifier.fillMaxWidth()
                .semantics { contentDescription = "可选 API Key 输入框" },
            supportingText = { Text("留空 = 未设置") },
        )
        androidx.compose.material3.Button(onClick = { onApiKeyChange(apiKeyDraft) }) {
            Text("保存 API Key")
        }
        SectionDivider()

        // ── 开源许可 ──
        SectionTitle("开源许可与数据署名")
        SettingsLogic.LICENSES.forEach { lic ->
            Text(lic.name, style = MaterialTheme.typography.titleSmall)
            Text("${lic.role} · ${lic.license}", style = MaterialTheme.typography.bodySmall)
            Text(lic.attribution, style = MaterialTheme.typography.labelSmall)
        }
        SectionDivider()

        // ── 隐私逐源披露 ──
        SectionTitle("隐私披露（逐源）")
        SettingsLogic.privacyLines(state.reverseGeoEnabled, state.prefetchEnabled).forEach { line ->
            Text(line.source, style = MaterialTheme.typography.titleSmall)
            Text("发送内容：${line.sends}", style = MaterialTheme.typography.bodySmall)
            Text(line.toggleNote, style = MaterialTheme.typography.labelSmall)
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
