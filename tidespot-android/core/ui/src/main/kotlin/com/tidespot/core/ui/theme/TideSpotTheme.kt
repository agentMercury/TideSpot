package com.tidespot.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    // OPT-V115-02 / design-tokens.json surface：画布 #EEF3F8 / 卡片 #FFFFFF / 描边 #DCE5EE（非冻结色）
    background = Color(0xFFEEF3F8),
    onBackground = Color(0xFF10243A),
    onSurface = Color(0xFF10243A),
    // 设计稿 #8A9DB0 在浅底仅 2.2–2.8:1，不满足 DoD ≥4.5:1；压深为 #556579（4.68–5.96:1，色相一致）
    onSurfaceVariant = Color(0xFF556579),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF3F8),
    outline = Color(0xFFDCE5EE),
    outlineVariant = Color(0xFFDCE5EE),
    secondary = Color(0xFF00695C),
    tertiary = Color(0xFF6A1B9A),
    error = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    background = Color(0xFF0E1721),
    onBackground = Color(0xFFE6EDF5),
    onSurface = Color(0xFFE6EDF5),
    onSurfaceVariant = Color(0xFF9FB0C0),
    surface = Color(0xFF16212E),
    surfaceVariant = Color(0xFF1E2C3A),
    outline = Color(0xFF2A3A4C),
    outlineVariant = Color(0xFF2A3A4C),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFCE93D8),
    error = Color(0xFFEF9A9A),
)

/**
 * 形状 token（OPT-V115-02 / design-tokens.json `radius`）：
 * card 16 · button 12 · chip 18 · sheet/dialog 20。只动圆角，不触色值。
 */
private val TideSpotShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/**
 * 字阶 token（OPT-V115-02 / design-tokens.json `typography.scale`）：
 * titleLarge 20/ titleMedium 16/ titleSmall 14 · bodyMedium 13 · labelSmall 10 · labelLarge 12（chip）。
 * 未内置设计字族 → 回退系统 sans（不得因字体缺失改行高）。
 */
private val TideSpotTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
)

@Composable
fun TideSpotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = TideSpotShapes,
        typography = TideSpotTypography,
        content = content,
    )
}
