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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tidespot.core.domain.model.WaterBodyType

/**
 * 手绘 A · 复古户外 —— 详情页试点皮肤（OPT-A-09 换皮肤能力的前置落地）。
 *
 * 设计来源：design-handdrawn/A-vintage-outdoor/（spec.json 的 palette / SKIN-DESIGN-SPEC.md）。
 * 试点范围：仅 :app 的 DetailPage 一屏包裹本主题；其余 3 个顶层页仍用 TideSpotTheme，零视觉影响。
 *
 * 三轴正交（见 SKIN-DESIGN-SPEC.md）：
 *  - 皮肤 Skin：本文件，首版唯一内置款式
 *  - 水体 Water：由点位 effectiveWaterBody 决定海 / 内陆双色板，不可由用户切换
 *  - 明暗 Mode：跟随系统，与皮肤、水体两条轴独立
 *
 * 冻结契约（一律不动，勿在本文件内重定义）：
 *  - ScoreBands.kt 五档分数色、ConfidenceBadge.kt 六徽标色为语义色单源
 *  - 文案取 TideSpotStrings、Tab 序取 WaterBodyTabTitles、免责恒 5 条
 *  - 字号 / 行高与 TideSpotTypography 完全一致，仅换字族为衬线（零布局漂移）
 *  - 不含任何业务逻辑、导航、状态变更；本文件只提供视觉 token
 *
 * 试点边界：纸张颗粒、套印错位、鱼形矢量装饰尚未工程化（需 drawable / Canvas 资源），
 * 本轮先落地色板 / 形状 / 字阶三类可由 token 直接表达的视觉差异。
 */

// ── 浅色 · 海洋 / 近海（藏蓝 + 做旧米纸）────────────────────────────────
private val VintageSeaLight = lightColorScheme(
    primary = Color(0xFF1B3A5C),
    onPrimary = Color(0xFFF7F1DE),
    primaryContainer = Color(0xFFD8C9A5),
    onPrimaryContainer = Color(0xFF16324F),
    secondary = Color(0xFF2E6E7E),
    onSecondary = Color(0xFFF7F1DE),
    secondaryContainer = Color(0xFFE8DDBE),
    onSecondaryContainer = Color(0xFF16324F),
    tertiary = Color(0xFFD9A734),
    onTertiary = Color(0xFF16324F),
    background = Color(0xFFE4D9BC),
    onBackground = Color(0xFF16324F),
    surface = Color(0xFFF2EAD4),
    onSurface = Color(0xFF16324F),
    surfaceVariant = Color(0xFFE8DDBE),
    onSurfaceVariant = Color(0xFF5C5540),
    outline = Color(0xFF16324F),
    outlineVariant = Color(0xFF8A7C60),
    error = Color(0xFFC15A3A),
    onError = Color(0xFFF7F1DE),
    scrim = Color(0xFF16324F),
)

// ── 浅色 · 内陆水体（墨绿 + 苔绿纸）─────────────────────────────────────
private val VintageInlandLight = lightColorScheme(
    primary = Color(0xFF2E5A48),
    onPrimary = Color(0xFFF7F4E2),
    primaryContainer = Color(0xFFCDD6B0),
    onPrimaryContainer = Color(0xFF213D2A),
    secondary = Color(0xFF3E7A54),
    onSecondary = Color(0xFFF7F4E2),
    secondaryContainer = Color(0xFFE4E6C6),
    onSecondaryContainer = Color(0xFF213D2A),
    tertiary = Color(0xFFD9A734),
    onTertiary = Color(0xFF213D2A),
    background = Color(0xFFDCE2C4),
    onBackground = Color(0xFF213D2A),
    surface = Color(0xFFF1EED8),
    onSurface = Color(0xFF213D2A),
    surfaceVariant = Color(0xFFE4E6C6),
    onSurfaceVariant = Color(0xFF556045),
    outline = Color(0xFF213D2A),
    outlineVariant = Color(0xFF78815C),
    error = Color(0xFFC15A3A),
    onError = Color(0xFFF7F4E2),
    scrim = Color(0xFF213D2A),
)

// ── 深色 · 海洋 / 近海 ───────────────────────────────────────────────────
private val VintageSeaDark = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0E1C2B),
    primaryContainer = Color(0xFF132538),
    onPrimaryContainer = Color(0xFFE6EDF5),
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color(0xFF0E1C2B),
    secondaryContainer = Color(0xFF1E344A),
    onSecondaryContainer = Color(0xFFE6EDF5),
    tertiary = Color(0xFF80CBC4),
    onTertiary = Color(0xFF0E1C2B),
    background = Color(0xFF0E1C2B),
    onBackground = Color(0xFFE6EDF5),
    surface = Color(0xFF17293B),
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Color(0xFF1E344A),
    onSurfaceVariant = Color(0xFF9FB0C0),
    outline = Color(0xFF3A4C5E),
    outlineVariant = Color(0xFF3A4C5E),
    error = Color(0xFFFF8A65),
    onError = Color(0xFF0E1C2B),
    scrim = Color(0xFF0E1C2B),
)

// ── 深色 · 内陆水体 ──────────────────────────────────────────────────────
private val VintageInlandDark = darkColorScheme(
    primary = Color(0xFFA5D6A7),
    onPrimary = Color(0xFF101D18),
    primaryContainer = Color(0xFF162A20),
    onPrimaryContainer = Color(0xFFE6F0E9),
    secondary = Color(0xFF66BB6A),
    onSecondary = Color(0xFF101D18),
    secondaryContainer = Color(0xFF1E372A),
    onSecondaryContainer = Color(0xFFE6F0E9),
    tertiary = Color(0xFF80CBC4),
    onTertiary = Color(0xFF101D18),
    background = Color(0xFF101D18),
    onBackground = Color(0xFFE6F0E9),
    surface = Color(0xFF172B21),
    onSurface = Color(0xFFE6F0E9),
    surfaceVariant = Color(0xFF1E372A),
    onSurfaceVariant = Color(0xFF9DB8A8),
    outline = Color(0xFF3A5346),
    outlineVariant = Color(0xFF3A5346),
    error = Color(0xFFFFAB91),
    onError = Color(0xFF101D18),
    scrim = Color(0xFF101D18),
)

/**
 * 复古户外的非对称圆角：四角半径刻意不齐，模拟手工裁纸 / 版画套印的轻微歪斜。
 * 半径量级与 TideSpotShapes（8/12/16/20）保持一致，不改变任何组件高度。
 */
private val VintageShapes = Shapes(
    extraSmall = RoundedCornerShape(topStart = 6.dp, topEnd = 9.dp, bottomEnd = 6.dp, bottomStart = 10.dp),
    small = RoundedCornerShape(topStart = 10.dp, topEnd = 14.dp, bottomEnd = 10.dp, bottomStart = 15.dp),
    medium = RoundedCornerShape(topStart = 14.dp, topEnd = 18.dp, bottomEnd = 14.dp, bottomStart = 19.dp),
    large = RoundedCornerShape(topStart = 18.dp, topEnd = 23.dp, bottomEnd = 18.dp, bottomStart = 24.dp),
    extraLarge = RoundedCornerShape(topStart = 20.dp, topEnd = 26.dp, bottomEnd = 20.dp, bottomStart = 27.dp),
)

/**
 * 字阶与 TideSpotTypography 逐项一致（字号 / 行高 / 字重全等），仅把字族换为系统衬线，
 * 避免字体缺失导致行高或换行位置变化。中文回退系统 serif（设计稿 KaiTi → serif 同样回退）。
 */
private val VintageTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
)

/**
 * 详情页局部皮肤。嵌套在全局 TideSpotTheme 内，只覆盖详情页子树，
 * 不影响 MAP / FAVORITES / SETTINGS 三页，也不改变任何 Composable 的参数与行为。
 *
 * @param waterBody 点位有效水体类型；INLAND_WATER 走内陆墨绿色板，其余走海洋藏蓝（配色兜底，
 *                  不改变 WaterBodyTabTitles 的 Tab 语义判定）
 */
@Composable
fun VintageOutdoorTheme(
    waterBody: WaterBodyType,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val inland = waterBody == WaterBodyType.INLAND_WATER
    val colors = when {
        darkTheme && inland -> VintageInlandDark
        darkTheme -> VintageSeaDark
        inland -> VintageInlandLight
        else -> VintageSeaLight
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = VintageShapes,
        typography = VintageTypography,
        content = content,
    )
}
