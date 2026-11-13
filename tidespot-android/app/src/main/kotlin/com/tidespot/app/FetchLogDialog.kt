package com.tidespot.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tidespot.core.data.local.FetchLogEntity
import com.tidespot.core.data.repo.TideSpotRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * OPT-A-06：请求日志（fetch_log 只读视图 + 清空）。
 *
 * 展示口径（SRS §5.6 / N-6）：
 * - 字段：源、时刻、HTTP 状态、耗时、字节；**429 单独标为「频控命中」**（这是本入口的核心目的）；
 * - 不展示 URL（表里只有 `urlHash`），因此不存在密钥泄露面；
 * - 最多 50 条，跨点位倒序；清空仅删本地日志行，不动业务缓存。
 */
@Composable
fun FetchLogDialog(repository: TideSpotRepository, onDismiss: () -> Unit) {
    var logs by remember { mutableStateOf<List<FetchLogEntity>?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadTick) {
        logs = runCatching { repository.recentFetchLogs(50) }.getOrDefault(emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ts_fetch_log_title)) },
        text = {
            val data = logs
            when {
                data == null -> Text(stringResource(R.string.ts_fetch_log_loading))
                data.isEmpty() -> Text(stringResource(R.string.ts_fetch_log_empty))
                else -> Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    data.forEachIndexed { index, e ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        FetchLogRow(e)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ts_fetch_log_close)) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        runCatching { repository.clearFetchLogs() }
                        reloadTick += 1
                    }
                },
            ) { Text(stringResource(R.string.ts_fetch_log_clear)) }
        },
    )
}

@Composable
private fun FetchLogRow(e: FetchLogEntity) {
    val time = remember(e.fetchedAt) {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(e.fetchedAt))
    }
    val statusText = when (e.httpStatus) {
        null -> stringResource(R.string.ts_fetch_log_network_error)
        429 -> stringResource(R.string.ts_fetch_log_throttled)
        else -> "HTTP ${e.httpStatus}"
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${e.source} · $time",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.ts_fetch_log_cost, e.costMs, e.bytes ?: 0L),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            statusText,
            style = MaterialTheme.typography.labelMedium,
            color = if (e.httpStatus == 200) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}
