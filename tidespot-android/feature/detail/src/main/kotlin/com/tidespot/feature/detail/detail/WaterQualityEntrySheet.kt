package com.tidespot.feature.detail.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tidespot.core.data.repo.WaterQualityObservation
import com.tidespot.core.data.repo.WaterQualityObservationKind
import com.tidespot.core.ui.R as UiR
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * OPT-V11-02（CHG-27）：水质实测录入 Sheet（详情页水质 Tab 入口）。
 *
 * 布局口径（真机冒烟修正 CHG-27）：**操作按钮置顶**（标题下方），表单与列表在后段——
 * ModalBottomSheet 半展开/底部手势条区域会让贴底按钮不可点（GAP-03），故不以底部为主要操作区；
 * Sheet 由调用方以 `skipPartiallyExpanded = true` 全展开。
 *
 * - 三项可选填（水温 / 溶解氧 / 浊度，SI 单位），范围校验走 [WaterQualityEntryLogic] 纯函数；
 * - 录入时刻默认当前、可改（±24h 内，当地时区 N-9）；离线可录入；
 * - 水温参与 §6.1.3 融合（±6h，0.6·实测+0.4·模型，徽标「实测」）；
 * - 已录入列表展示原值与录入时刻（核对用）。
 */
@Composable
fun WaterQualityEntrySheet(
    viewModel: DetailViewModel,
    zone: ZoneId,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val timeFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    var tempText by remember { mutableStateOf("") }
    var doText by remember { mutableStateOf("") }
    var turbText by remember { mutableStateOf("") }
    var timeText by remember {
        mutableStateOf(LocalDateTime.now(zone).withSecond(0).withNano(0).format(timeFmt))
    }
    var message by remember { mutableStateOf<String?>(null) }
    var savedList by remember { mutableStateOf<List<WaterQualityObservation>>(emptyList()) }
    // 资源文案预取（onClick 内非 Composable 上下文不可直接 stringResource）
    val errNumber = stringResource(UiR.string.ts_wq_err_number)
    val errRange = stringResource(UiR.string.ts_wq_err_range)
    val errTimeFormat = stringResource(UiR.string.ts_wq_err_time_format)
    val errTimeWindow = stringResource(UiR.string.ts_wq_err_time_window)
    val errAtLeastOne = stringResource(UiR.string.ts_wq_at_least_one)
    val savedHint = stringResource(UiR.string.ts_wq_saved)
    val errSavePrefix = stringResource(UiR.string.ts_wq_err_save, "")
    val savedListTitle = stringResource(UiR.string.ts_wq_saved_list)
    val emptyListText = stringResource(UiR.string.ts_wq_empty_list)

    LaunchedEffect(Unit) {
        savedList = runCatching { viewModel.waterQualityObservations() }.getOrDefault(emptyList())
    }

    /** 保存逻辑（按钮置顶，逻辑内聚；错误优先展示在标题下方）。 */
    val doSave: () -> Unit = {
        val entries = mutableListOf<Pair<WaterQualityObservationKind, Double>>()
        var error: String? = null
        fun parseTo(kind: WaterQualityObservationKind, text: String) {
            when (val r = WaterQualityEntryLogic.parse(kind, text)) {
                null -> Unit
                is WaterQualityEntryLogic.ValueResult.Ok -> entries += kind to r.value
                is WaterQualityEntryLogic.ValueResult.Err -> if (error == null) {
                    error = when (r.error) {
                        WaterQualityEntryLogic.Error.NOT_A_NUMBER ->
                            "${WaterQualityEntryLogic.label(kind)}：$errNumber"
                        WaterQualityEntryLogic.Error.OUT_OF_RANGE ->
                            "${WaterQualityEntryLogic.label(kind)}：$errRange"
                    }
                }
            }
        }
        parseTo(WaterQualityObservationKind.WATER_TEMP, tempText)
        parseTo(WaterQualityObservationKind.DO, doText)
        parseTo(WaterQualityObservationKind.TURBIDITY, turbText)
        val observedAt = runCatching { LocalDateTime.parse(timeText.trim(), timeFmt) }
            .getOrNull()?.atZone(zone)?.toEpochSecond()
        when {
            error != null -> message = error
            observedAt == null -> message = errTimeFormat
            !WaterQualityEntryLogic.isTimeWithinWindow(observedAt, System.currentTimeMillis() / 1000L) ->
                message = errTimeWindow
            entries.isEmpty() -> message = errAtLeastOne
            else -> scope.launch {
                runCatching {
                    viewModel.saveWaterQualitySelections(entries, observedAt)
                    savedList = viewModel.waterQualityObservations()
                }.onSuccess {
                    message = savedHint
                    tempText = ""; doText = ""; turbText = ""
                }.onFailure { e -> message = errSavePrefix + (e.message ?: "") }
            }
        }
    }

    Column(
        Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(UiR.string.ts_wq_title), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = doSave,
                modifier = Modifier.semantics { contentDescription = "保存水质实测" }) {
                Text(stringResource(UiR.string.ts_wq_save))
            }
            TextButton(onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = "取消录入" }) {
                Text(stringResource(UiR.string.ts_wq_cancel))
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = tempText, onValueChange = { tempText = it },
            label = { Text(stringResource(UiR.string.ts_wq_temp_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "水温实测输入" },
        )
        OutlinedTextField(
            value = doText, onValueChange = { doText = it },
            label = { Text(stringResource(UiR.string.ts_wq_do_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "溶解氧实测输入" },
        )
        OutlinedTextField(
            value = turbText, onValueChange = { turbText = it },
            label = { Text(stringResource(UiR.string.ts_wq_turb_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "浊度实测输入" },
        )
        OutlinedTextField(
            value = timeText, onValueChange = { timeText = it },
            label = { Text(stringResource(UiR.string.ts_wq_time_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "录入时刻输入" },
        )
        Text(savedListTitle, style = MaterialTheme.typography.titleSmall)
        if (savedList.isEmpty()) {
            Text(emptyListText, style = MaterialTheme.typography.labelSmall)
        } else {
            savedList.forEach { o ->
                val kind = runCatching { WaterQualityObservationKind.valueOf(o.kind) }.getOrNull()
                val label = kind?.let { WaterQualityEntryLogic.label(it) } ?: o.kind
                val unit = kind?.let { WaterQualityEntryLogic.unit(it) } ?: ""
                Text(
                    "$label ${o.value ?: "—"} $unit · ${Top10Formatter.time(o.observedAtEpochSec, zone)}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}