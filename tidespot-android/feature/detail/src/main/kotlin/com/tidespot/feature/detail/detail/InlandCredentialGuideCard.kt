package com.tidespot.feature.detail.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tidespot.core.data.remote.TideSpotUrls
import com.tidespot.core.ui.R as UiR
import com.tidespot.feature.detail.R

/**
 * 内陆水质「Copernicus 凭据」首次引导卡（CHG-25 P0-1 批次 B）。
 *
 * 展示条件（:app 组装层判定）：内陆点 + 水质 Tab + 未配置凭据 + 未跳过。
 * 一次可跳过（跳过标记由 :app 持久化）；不阻塞其余 Tab（F-3.6 分维降级）——卡片仅作说明与入口，
 * 未配置时水质 Tab 照常显示 4 项模型估算（估算徽标）。文案全部来自字符串资源（中/英）。
 */
@Composable
fun InlandCredentialGuideCard(
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardDesc = stringResource(R.string.ts_detail_guide_desc)
    val openDesc = stringResource(R.string.ts_detail_guide_open)
    val skipDesc = stringResource(R.string.ts_detail_guide_skip)
    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        .semantics { contentDescription = cardDesc }) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(UiR.string.ts_guide_inland_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(UiR.string.ts_guide_inland_body), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(UiR.string.ts_guide_inland_steps, TideSpotUrls.COPERNICUS_REGISTER_URL),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(stringResource(UiR.string.ts_guide_inland_privacy), style = MaterialTheme.typography.labelSmall)
            Row {
                TextButton(onClick = onOpenSettings,
                    modifier = Modifier.semantics { contentDescription = openDesc }) {
                    Text(stringResource(UiR.string.ts_guide_inland_open_settings))
                }
                TextButton(onClick = onSkip,
                    modifier = Modifier.semantics { contentDescription = skipDesc }) {
                    Text(stringResource(UiR.string.ts_guide_inland_skip))
                }
            }
        }
    }
}