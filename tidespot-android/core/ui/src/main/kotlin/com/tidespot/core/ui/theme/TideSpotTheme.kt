package com.tidespot.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * TideSpot 全局主题（M6/CHG-16⑲ 深色主题）：Material3 明/暗 token，浅色档主色与
 * 置信徽标「实测」蓝（ConfidenceBadge.MEASURED 0xFF1565C0）同源，暗色档提亮保对比。
 *
 * - 规格冻结色（ScoreBand 5 档 / ConfidenceBadge 6 档）不在本主题内重定义——它们是
 *   语义色 token，色值禁改（ScoreBands.kt / ConfidenceBadge.kt 单源）；
 * - :feature:* 内既有硬编码色值本期只报告不改动（负责人批次 3 裁量，避免越界）；
 * - :feature:* 组装层接入：@Composable 包一层 TideSpotTheme { ... } 即可。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    secondary = Color(0xFF00695C),
    tertiary = Color(0xFF6A1B9A),
    error = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFCE93D8),
    error = Color(0xFFEF9A9A),
)

@Composable
fun TideSpotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
