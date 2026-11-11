package com.tidespot.core.ui.banner

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tidespot.core.ui.R

/**
 * 全局离线横幅（OPT-V115-01 #4 / GAP-05 `ts_offline`）：
 * 由 [ConnectivityManager] 默认网络回调驱动，无网时显示「网络不可用，显示本地缓存数据」。
 *
 * 约束：
 * - **不阻断**离线数据展示（纯提示条，位于详情/收藏页顶部、与过期黄条同级）；
 * - 判定口径与 `:app` 既有 `isOnline` 一致（`NET_CAPABILITY_INTERNET`），不引入新依赖；
 * - 网络能力未知（无 activeNetwork）→ 按离线显示（宁可提示，不静默）。
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var online by remember(context) { mutableStateOf(isNetworkAvailable(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                online = true
            }

            override fun onLost(network: Network) {
                online = isNetworkAvailable(context)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }
    if (online) return
    val text = stringResource(R.string.ts_offline)
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics { contentDescription = text },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** 当前默认网络是否具备 INTERNET 能力（无默认网络 → false）。 */
internal fun isNetworkAvailable(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}