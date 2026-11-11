package com.tidespot.feature.map.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.tidespot.feature.map.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tidespot.feature.map.geo.GeocoderApi
import com.tidespot.core.domain.model.GeocodeCandidate
import kotlinx.coroutines.delay

/** 候选副标题：admin2, admin1（缺省自动跳过）。 */
fun candidateSubtitle(candidate: GeocodeCandidate): String =
    listOfNotNull(candidate.admin2, candidate.admin1).joinToString(", ")

/**
 * F-1.5 搜索选点：关键词 → 候选列表（name + admin2, admin1 副标题）→ 选中回调。
 * 选中后的地图飞行与设准星由调用方（MapScreen）完成。
 */
@Composable
fun SearchPanel(
    geocoder: GeocoderApi,
    onSelect: (GeocodeCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeocodeCandidate>>(emptyList()) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300) // 输入防抖
        results = runCatching { geocoder.search(q) }.getOrDefault(emptyList())
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ts_map_search_clear))
                    }
                }
            },
            placeholder = { Text(stringResource(R.string.ts_map_search_hint)) },
        )
        if (results.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column {
                    results.forEachIndexed { index, candidate ->
                        ListItem(
                            headlineContent = { Text(candidate.name) },
                            supportingContent = {
                                candidateSubtitle(candidate).takeIf { it.isNotBlank() }
                                    ?.let { Text(it) }
                            },
                            modifier = Modifier.clickable { onSelect(candidate) },
                        )
                        if (index != results.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}