package com.tidespot.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tidespot.core.domain.model.WaterBodyType
import com.tidespot.core.ui.R

/**
 * 皮肤 token 抽象层（OPT-A-09 / SKIN-1）。
 *
 * 心智模型（三轴正交，见 SKIN-DESIGN-SPEC.md §1）：
 *  - **皮肤**：本文件的数据类，用户可选，内置 [TideSpotSkins.VintageOutdoor] 与 [TideSpotSkins.FieldGuide]；
 *  - **水体**：由点位数据决定（海 / 内陆），用户不可切；
 *  - **明暗**：跟随系统 / 浅 / 深（OPT-A-03），与皮肤正交。
 *
 * 最终视觉 = `palettes.pick(water, dark)` 选中的那一套 token。
 *
 * 边界：
 *  - 皮肤只换「长什么样」。Tab 序、免责 5 条、五档色（`ScoreBand`）、六徽标色
 *    （`ConfidenceBadge`）、文案（`TideSpotStrings`）**不进皮肤**，直取冻结单源。
 *  - 本文件不引用任何业务 Composable、不改导航与状态；只提供 token 数据。
 *  - 业务层禁止 `Color(0x...)` 硬编码，一律从 `MaterialTheme.colorScheme` 取，
 *    否则换肤会漏屏（验收 §6.2 第 2 条）。
 */

/**
 * 单套色板（海 / 陆 × 浅 / 深 四选一）。
 *
 * [scheme] 是完整 Material3 [ColorScheme]——四套色板的全部槽位都是独立调过的，
 * 无法由十几个源色线性派生（深色的 surfaceContainer 梯度方向与浅色相反），
 * 因此这里直接持有成品 scheme，保证与试点期像素级一致。
 *
 * [mapLand] / [mapWater] / [mapRoad] 是设计稿的地图三色（真实瓦片底图不重画，
 * 仅用于地图周边装饰与缩略图等需要「呼应地图色」的地方）。
 * 取值来源：`design-handdrawn/generator/engine.py` 的 PALETTE（非编造值）。
 */
data class SkinPalette(
    val scheme: ColorScheme,
    val mapLand: Color,
    val mapWater: Color,
    val mapRoad: Color,
)

/**
 * 一款皮肤的 4 套色板。**每款皮肤必须自带海 + 陆两套**，再各自派生浅 / 深，
 * 否则「配色区分水体」的语义会在换肤后丢失（六重区分之配色冷暖）。
 */
data class SkinPalettes(
    val seaLight: SkinPalette,
    val seaDark: SkinPalette,
    val inlandLight: SkinPalette,
    val inlandDark: SkinPalette,
) {
    /** 运行时按点位水体 + 明暗选色板；[WaterBodyType.INLAND_WATER] 走内陆，其余走海洋（与试点口径一致）。 */
    fun pick(water: WaterBodyType, darkTheme: Boolean): SkinPalette {
        val inland = water == WaterBodyType.INLAND_WATER
        return when {
            darkTheme && inland -> inlandDark
            darkTheme -> seaDark
            inland -> inlandLight
            else -> seaLight
        }
    }
}

/**
 * 质感与装饰参数。当前由调用方直接调用 [paperGrain] / [printMisregister] /
 * [handDrawnOutline] 并传颜色，本结构只表达「这款皮肤要不要这些质感、强度多少」，
 * 供铺开阶段统一取值，避免每个页面各写一套常量。
 *
 * @param grainStrength 纸纹 alpha（1f = 设计稿 grain 0.16 的上限；收敛颗粒可降到 0.6）
 */
data class SkinTexture(
    val paperGrain: Boolean = true,
    val grainStrength: Float = 1f,
    val printMisregister: Boolean = true,
    /** 浅色下套印投影 alpha；手帐纸的硬墨边要更深，蓝潮则完全不要。 */
    val printShadowAlpha: Float = 0.18f,
    val handDrawnOutline: Boolean = true,
    /** 蓝潮的干净白卡只靠 1dp 柔和投影分层，不画墨线。 */
    val softShadow: Boolean = false,
    /** 蓝潮的干净白卡另加 1dp 浅色 hairline，替代手绘墨线。 */
    val softOutline: Boolean = false,
    /** 版块标题是否带母题图标 / 延展虚线；蓝潮为 false。 */
    val sectionOrnament: Boolean = true,
    /** 分隔线是否虚线；蓝潮为 false（实线 hairline）。 */
    val dashedDivider: Boolean = true,
    val fishMotifRes: Int = R.drawable.ic_fish_vintage,
    val waveMotifRes: Int = R.drawable.ic_wave_vintage,
    val mountMotifRes: Int = R.drawable.ic_mount_vintage,
    val tapeDecoration: Boolean = true,
) {
    /** 按水体取母题：海洋 = 浪/贝/海鸟，内陆 = 山/日/芦苇。 */
    fun motifFor(water: WaterBodyType): Int =
        if (water == WaterBodyType.INLAND_WATER) mountMotifRes else waveMotifRes
}

/**
 * 一款皮肤 = 4 套色板 + 形状 + 字阶 + 质感装饰。
 *
 * [name] 是设置页下拉里显示的中文名，**全皮肤唯一**（[TideSpotSkins] 构造时校验）；
 * [nameEn] 为英文名，同样唯一；[id] 是持久化用的稳定标识，一经发布不得修改。
 */
data class TideSpotSkin(
    val id: String,
    val name: String,
    val nameEn: String,
    val palettes: SkinPalettes,
    val shapes: Shapes,
    val typography: Typography,
    val texture: SkinTexture = SkinTexture(),
)

// ── 手绘 A · 复古户外：浅色 · 海洋 / 近海（藏蓝 + 做旧米纸）──────────────────
private val VintageSeaLight = lightColorScheme(
    primary = Color(0xFF1B3A5C),
    onPrimary = Color(0xFFF7F1DE),
    primaryContainer = Color(0xFFD8C9A5),
    onPrimaryContainer = Color(0xFF16324F),
    secondary = Color(0xFF2E6E7E),
    onSecondary = Color(0xFFF7F1DE),
    // D2 / 2026-11-16：M3 FilterChip 选中底走 secondaryContainer；
    // 与纸卡反差过小的米色改为藏蓝实底，选中/未选中一眼可分。
    secondaryContainer = Color(0xFF1B3A5C),
    onSecondaryContainer = Color(0xFFF7F1DE),
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
    surfaceTint = Color(0xFF1B3A5C),
    inverseSurface = Color(0xFF16324F),
    inverseOnSurface = Color(0xFFF7F1DE),
    inversePrimary = Color(0xFF90CAF9),
    errorContainer = Color(0xFFE8DDBE),
    onErrorContainer = Color(0xFF8A3B2E),
    tertiaryContainer = Color(0xFFE8DDBE),
    onTertiaryContainer = Color(0xFF16324F),
    surfaceContainerLowest = Color(0xFFF7F1DE),
    surfaceContainerLow = Color(0xFFF2EAD4),
    surfaceContainer = Color(0xFFE8DDBE),
    surfaceContainerHigh = Color(0xFFD8C9A5),
    // D1（真机对拍 2026-11-13）：M3 Card 默认容器 = surfaceContainerHighest；
    // 原 #CBB98E 比纸底更深，卡片看起来像一块脏斑。设计稿是「浅纸贴米纸」，改为纸白。
    surfaceContainerHighest = Color(0xFFF2EAD4),
)

// ── 手绘 A：浅色 · 内陆水体（墨绿 + 苔绿纸）────────────────────────────────
private val VintageInlandLight = lightColorScheme(
    primary = Color(0xFF2E5A48),
    onPrimary = Color(0xFFF7F4E2),
    primaryContainer = Color(0xFFCDD6B0),
    onPrimaryContainer = Color(0xFF213D2A),
    secondary = Color(0xFF3E7A54),
    onSecondary = Color(0xFFF7F4E2),
    // D2：同上，墨绿实底，避免苔绿纸底上选中态不可辨
    secondaryContainer = Color(0xFF2E5A48),
    onSecondaryContainer = Color(0xFFF7F4E2),
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
    surfaceTint = Color(0xFF2E5A48),
    inverseSurface = Color(0xFF213D2A),
    inverseOnSurface = Color(0xFFF7F4E2),
    inversePrimary = Color(0xFFA5D6A7),
    errorContainer = Color(0xFFE4E6C6),
    onErrorContainer = Color(0xFF8A3B2E),
    tertiaryContainer = Color(0xFFE4E6C6),
    onTertiaryContainer = Color(0xFF213D2A),
    surfaceContainerLowest = Color(0xFFF7F4E2),
    surfaceContainerLow = Color(0xFFF1EED8),
    surfaceContainer = Color(0xFFE4E6C6),
    surfaceContainerHigh = Color(0xFFCDD6B0),
    // D1：同 seaLight，Card 底改纸白
    surfaceContainerHighest = Color(0xFFF1EED8),
)

// ── 手绘 A：深色 · 海洋 / 近海（深蓝纸 + 奶油墨线 + 珊瑚强调）──────────────
private val VintageSeaDark = darkColorScheme(
    primary = Color(0xFFE9DCBE),
    onPrimary = Color(0xFF15243A),
    primaryContainer = Color(0xFF243547),
    onPrimaryContainer = Color(0xFFECE3CD),
    secondary = Color(0xFF7FB3AB),
    onSecondary = Color(0xFF15243A),
    secondaryContainer = Color(0xFFE9DCBE),
    onSecondaryContainer = Color(0xFF15243A),
    tertiary = Color(0xFFD9A734),
    onTertiary = Color(0xFF15243A),
    background = Color(0xFF101A24),
    onBackground = Color(0xFFECE3CD),
    surface = Color(0xFF1A2836),
    onSurface = Color(0xFFECE3CD),
    surfaceVariant = Color(0xFF243547),
    onSurfaceVariant = Color(0xFFA89F88),
    outline = Color(0xFFE9DCBE),
    outlineVariant = Color(0xFF6E6552),
    error = Color(0xFFE0805E),
    onError = Color(0xFF1A120E),
    scrim = Color(0xFF101A24),
    surfaceTint = Color(0xFFE9DCBE),
    inverseSurface = Color(0xFFECE3CD),
    inverseOnSurface = Color(0xFF15243A),
    inversePrimary = Color(0xFF1B3A5C),
    errorContainer = Color(0xFF3A2A26),
    onErrorContainer = Color(0xFFF0B49C),
    tertiaryContainer = Color(0xFF3A3222),
    onTertiaryContainer = Color(0xFFF0D79A),
    surfaceContainerLowest = Color(0xFF0C141C),
    surfaceContainerLow = Color(0xFF141F2B),
    surfaceContainer = Color(0xFF1A2836),
    surfaceContainerHigh = Color(0xFF243547),
    // 深色卡 = 纸卡（与 Web A 深色一致），层次靠奶油描边
    surfaceContainerHighest = Color(0xFF1A2836),
)

// ── 手绘 A：深色 · 内陆水体（深苔纸 + 奶油墨线 + 琥珀/珊瑚）────────────────
private val VintageInlandDark = darkColorScheme(
    primary = Color(0xFFE6DFC0),
    onPrimary = Color(0xFF15241A),
    primaryContainer = Color(0xFF26382C),
    onPrimaryContainer = Color(0xFFECE6D2),
    secondary = Color(0xFF8FBF82),
    onSecondary = Color(0xFF15241A),
    secondaryContainer = Color(0xFFE6DFC0),
    onSecondaryContainer = Color(0xFF15241A),
    tertiary = Color(0xFFD9A734),
    onTertiary = Color(0xFF15241A),
    background = Color(0xFF121B16),
    onBackground = Color(0xFFECE6D2),
    surface = Color(0xFF1C2A21),
    onSurface = Color(0xFFECE6D2),
    surfaceVariant = Color(0xFF26382C),
    onSurfaceVariant = Color(0xFFA9B09B),
    outline = Color(0xFFE6DFC0),
    outlineVariant = Color(0xFF6B7763),
    error = Color(0xFFE08A6A),
    onError = Color(0xFF1A120E),
    scrim = Color(0xFF121B16),
    surfaceTint = Color(0xFFE6DFC0),
    inverseSurface = Color(0xFFECE6D2),
    inverseOnSurface = Color(0xFF15241A),
    inversePrimary = Color(0xFF2E5A48),
    errorContainer = Color(0xFF3A2A24),
    onErrorContainer = Color(0xFFF0B49C),
    tertiaryContainer = Color(0xFF3A3222),
    onTertiaryContainer = Color(0xFFF0D79A),
    surfaceContainerLowest = Color(0xFF0E1712),
    surfaceContainerLow = Color(0xFF16241B),
    surfaceContainer = Color(0xFF1C2A21),
    surfaceContainerHigh = Color(0xFF26382C),
    // 深色卡 = 苔纸卡
    surfaceContainerHighest = Color(0xFF1C2A21),
)

/**
 * 复古户外的非对称圆角：四角半径刻意不齐，模拟手工裁纸 / 版画套印的轻微歪斜。
 * 半径量级与基础 `TideSpotShapes`（8/12/16/20）一致，不改变任何组件高度。
 */
private val VintageShapes = Shapes(
    extraSmall = RoundedCornerShape(topStart = 6.dp, topEnd = 9.dp, bottomEnd = 6.dp, bottomStart = 10.dp),
    small = RoundedCornerShape(topStart = 10.dp, topEnd = 14.dp, bottomEnd = 10.dp, bottomStart = 15.dp),
    medium = RoundedCornerShape(topStart = 14.dp, topEnd = 18.dp, bottomEnd = 14.dp, bottomStart = 19.dp),
    large = RoundedCornerShape(topStart = 18.dp, topEnd = 23.dp, bottomEnd = 18.dp, bottomStart = 24.dp),
    extraLarge = RoundedCornerShape(topStart = 20.dp, topEnd = 26.dp, bottomEnd = 20.dp, bottomStart = 27.dp),
)

/**
 * CHG-93：A 皮肤中文衬线字族。设计稿 `engine.py` 用 KaiTi/STKaiti + Georgia，
 * 而 `FontFamily.Serif` 在 Android 对中文一律回落到无衬线 CJK——这是「不像设计稿」的主因之一。
 * 现改用 Noto Serif SC（SIL OFL 1.1）的 645 KB 子集，由 `gen_font_subset.py` 从工程真实字符生成；
 * 可变轴固定到 wght=400，Bold/Medium 由系统合成，不改变任何字号/行高/字重。
 *
 * **不要为了「更像手绘」调字号或行高**——会破坏所有屏的换行与卡片高度。
 * D 皮肤保持自己的字族，不受影响（换肤仍只换 token）。
 */
private val VintageFontFamily = FontFamily(Font(R.font.noto_serif_sc_subset))

private val VintageTypography = Typography(
    titleLarge = TextStyle(fontFamily = VintageFontFamily, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = VintageFontFamily, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontFamily = VintageFontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = VintageFontFamily, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = VintageFontFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = VintageFontFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = VintageFontFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = VintageFontFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = VintageFontFamily, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
)

// ══════════════════════════════════════════════════════════════════════════
// 路亚图鉴（D · FIELD GUIDE）—— idea-explorations/v3-fieldguide 的工程化皮肤
//
// 设计语言：平涂纸底 + 近黑粗描边 + 硬投影贴纸 + 小圆角印章。
// 与 A 的差异只在「长什么样」：色板 / 形状 / 字阶；结构、文案、Tab 序、五档六徽标全部共用。
// 浅色四套源色取自 `screen.html` 的 CSS 变量（非编造）：
//   海洋 --bg #E9F3FA / --ink #101820 / --soft #456A84 / --accent2 #1E6FA8 / --accent #E5644C
//   内陆 --bg #E9F5EE / --ink #101820 / --soft #4A6B5E / --accent2 #2F7F6B / --accent #E9A13B
// 深色两套为独立调色（设计稿未给深色），按「同色系压暗 + 保留贴纸强调色」推导，
// 不是浅色的机械反转；对比度已按 WCAG ≥4.5:1 校验（见 SkinRegistryTest）。
// ══════════════════════════════════════════════════════════════════════════

// ── 路亚图鉴：浅色 · 海洋 / 近海（冷白纸 + 海蓝 + 珊瑚贴纸）──────────────
private val FieldGuideSeaLight = lightColorScheme(
    primary = Color(0xFF1E6FA8),
    onPrimary = Color(0xFFFFFDF7),
    primaryContainer = Color(0xFFB7DAEE),
    onPrimaryContainer = Color(0xFF101820),
    secondary = Color(0xFF33556B),
    onSecondary = Color(0xFFFFFDF7),
    secondaryContainer = Color(0xFFB7DAEE),
    onSecondaryContainer = Color(0xFF101820),
    tertiary = Color(0xFFE5644C),
    onTertiary = Color(0xFF101820),
    background = Color(0xFFE9F3FA),
    onBackground = Color(0xFF101820),
    surface = Color(0xFFFFFDF7),
    onSurface = Color(0xFF101820),
    surfaceVariant = Color(0xFFB7DAEE),
    // 原设计 --soft #456A84 在 #B7DAEE 上仅 3.9:1，压深一档到 5.4:1
    onSurfaceVariant = Color(0xFF33556B),
    outline = Color(0xFF101820),
    outlineVariant = Color(0xFF33556B),
    error = Color(0xFFC23B2A),
    onError = Color(0xFFFFFDF7),
    scrim = Color(0xFF101820),
    surfaceTint = Color(0xFF1E6FA8),
    inverseSurface = Color(0xFF101820),
    inverseOnSurface = Color(0xFFFFFDF7),
    inversePrimary = Color(0xFF8AC1E3),
    errorContainer = Color(0xFFF7D2CB),
    onErrorContainer = Color(0xFF7A2318),
    tertiaryContainer = Color(0xFFF7D9D2),
    onTertiaryContainer = Color(0xFF6B1F14),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFDF7),
    surfaceContainer = Color(0xFFF2F8FC),
    surfaceContainerHigh = Color(0xFFDCEBF5),
    // Card 默认容器：设计稿是「白纸 + 黑描边」，不能比纸底更暗
    surfaceContainerHighest = Color(0xFFFFFDF7),
)

// ── 路亚图鉴：浅色 · 内陆水体（薄荷纸 + 青绿 + 琥珀贴纸）────────────────
private val FieldGuideInlandLight = lightColorScheme(
    primary = Color(0xFF2F7F6B),
    onPrimary = Color(0xFFFFFDF7),
    primaryContainer = Color(0xFFB9E0CE),
    onPrimaryContainer = Color(0xFF101820),
    secondary = Color(0xFF3A5548),
    onSecondary = Color(0xFFFFFDF7),
    secondaryContainer = Color(0xFFB9E0CE),
    onSecondaryContainer = Color(0xFF101820),
    tertiary = Color(0xFFE9A13B),
    onTertiary = Color(0xFF101820),
    background = Color(0xFFE9F5EE),
    onBackground = Color(0xFF101820),
    surface = Color(0xFFFFFDF7),
    onSurface = Color(0xFF101820),
    surfaceVariant = Color(0xFFB9E0CE),
    // 原设计 --soft #4A6B5E 在 #B9E0CE 上仅 4.1:1，压深一档到 5.7:1
    onSurfaceVariant = Color(0xFF3A5548),
    outline = Color(0xFF101820),
    outlineVariant = Color(0xFF3A5548),
    error = Color(0xFFC23B2A),
    onError = Color(0xFFFFFDF7),
    scrim = Color(0xFF101820),
    surfaceTint = Color(0xFF2F7F6B),
    inverseSurface = Color(0xFF101820),
    inverseOnSurface = Color(0xFFFFFDF7),
    inversePrimary = Color(0xFF8CCBB4),
    errorContainer = Color(0xFFF7D2CB),
    onErrorContainer = Color(0xFF7A2318),
    tertiaryContainer = Color(0xFFF7DFB5),
    onTertiaryContainer = Color(0xFF4A2B08),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFDF7),
    surfaceContainer = Color(0xFFF1FBF5),
    surfaceContainerHigh = Color(0xFFDDF0E6),
    surfaceContainerHighest = Color(0xFFFFFDF7),
)

// ── 路亚图鉴：深色 · 海洋 / 近海（深海墨底 + 亮海蓝 + 珊瑚）──────────────
private val FieldGuideSeaDark = darkColorScheme(
    primary = Color(0xFF6FB6E8),
    onPrimary = Color(0xFF0B1620),
    primaryContainer = Color(0xFF17394C),
    onPrimaryContainer = Color(0xFFEAF2F7),
    secondary = Color(0xFF9FB6C4),
    onSecondary = Color(0xFF0B1620),
    secondaryContainer = Color(0xFF245A73),
    onSecondaryContainer = Color(0xFFEAF2F7),
    tertiary = Color(0xFFFF8A70),
    onTertiary = Color(0xFF2A0E08),
    background = Color(0xFF0B1620),
    onBackground = Color(0xFFF2EFE6),
    surface = Color(0xFF12222E),
    onSurface = Color(0xFFF2EFE6),
    surfaceVariant = Color(0xFF17394C),
    onSurfaceVariant = Color(0xFF9FB6C4),
    // V3 的「近黑描边」在深色下反转为暖白描边，保持贴纸轮廓
    outline = Color(0xFFF2EFE6),
    outlineVariant = Color(0xFF3A4A54),
    error = Color(0xFFFF8A70),
    onError = Color(0xFF2A0E08),
    scrim = Color(0xFF0B1620),
    surfaceTint = Color(0xFF6FB6E8),
    inverseSurface = Color(0xFFF2EFE6),
    inverseOnSurface = Color(0xFF0B1620),
    inversePrimary = Color(0xFF1E6FA8),
    errorContainer = Color(0xFF3A1F1A),
    onErrorContainer = Color(0xFFFFB4A2),
    tertiaryContainer = Color(0xFF3A2A12),
    onTertiaryContainer = Color(0xFFFFD9B0),
    surfaceContainerLowest = Color(0xFF0B1620),
    surfaceContainerLow = Color(0xFF0F1D28),
    surfaceContainer = Color(0xFF12222E),
    surfaceContainerHigh = Color(0xFF17394C),
    surfaceContainerHighest = Color(0xFF12222E),
)

// ── 路亚图鉴：深色 · 内陆水体（深林墨底 + 亮青绿 + 琥珀）──────────────────
private val FieldGuideInlandDark = darkColorScheme(
    primary = Color(0xFF67C2A5),
    onPrimary = Color(0xFF0C1A14),
    primaryContainer = Color(0xFF16382A),
    onPrimaryContainer = Color(0xFFEAF4EE),
    secondary = Color(0xFF9DB8A8),
    onSecondary = Color(0xFF0C1A14),
    secondaryContainer = Color(0xFF1F5240),
    onSecondaryContainer = Color(0xFFEAF4EE),
    tertiary = Color(0xFFFFC15E),
    onTertiary = Color(0xFF2A1A05),
    background = Color(0xFF0C1A14),
    onBackground = Color(0xFFF2EFE6),
    surface = Color(0xFF12261C),
    onSurface = Color(0xFFF2EFE6),
    surfaceVariant = Color(0xFF16382A),
    onSurfaceVariant = Color(0xFF9DB8A8),
    outline = Color(0xFFF2EFE6),
    outlineVariant = Color(0xFF34483C),
    error = Color(0xFFFF8A70),
    onError = Color(0xFF2A0E08),
    scrim = Color(0xFF0C1A14),
    surfaceTint = Color(0xFF67C2A5),
    inverseSurface = Color(0xFFF2EFE6),
    inverseOnSurface = Color(0xFF0C1A14),
    inversePrimary = Color(0xFF2F7F6B),
    errorContainer = Color(0xFF3A1F1A),
    onErrorContainer = Color(0xFFFFB4A2),
    tertiaryContainer = Color(0xFF3A2A12),
    onTertiaryContainer = Color(0xFFFFE0AE),
    surfaceContainerLowest = Color(0xFF0C1A14),
    surfaceContainerLow = Color(0xFF0F2018),
    surfaceContainer = Color(0xFF12261C),
    surfaceContainerHigh = Color(0xFF16382A),
    surfaceContainerHighest = Color(0xFF12261C),
)

// ══════════════════════════════════════════════════════════════════════════
// 潮汐手帐（E · TIDE JOURNAL）—— 纸手帐语言：纸底 + 墨线 + 珊瑚贴纸
// 色板与 D 共用同一套源色（Web `skins.css` E 段），只把海洋 tertiary 换成珊瑚墨。
// ══════════════════════════════════════════════════════════════════════════
private val TideJournalSeaLight = FieldGuideSeaLight.copy(
    secondary = Color(0xFF456A84),
    // M3 FilterChip 选中态读 secondaryContainer；E 设计要求「墨底纸字」。
    secondaryContainer = Color(0xFF101820),
    onSecondaryContainer = Color(0xFFFFFDF7),
    tertiary = Color(0xFFE5644C),
    onTertiary = Color(0xFF101820),
    tertiaryContainer = Color(0xFFF7D2CB),
    onTertiaryContainer = Color(0xFF7A2318),
)

private val TideJournalSeaDark = FieldGuideSeaDark.copy(
    secondaryContainer = Color(0xFFF2EFE6),
    onSecondaryContainer = Color(0xFF12222E),
)

private val TideJournalInlandLight = FieldGuideInlandLight.copy(
    secondary = Color(0xFF4A6B5E),
    secondaryContainer = Color(0xFF101820),
    onSecondaryContainer = Color(0xFFFFFDF7),
)

private val TideJournalInlandDark = FieldGuideInlandDark.copy(
    secondaryContainer = Color(0xFFF2EFE6),
    onSecondaryContainer = Color(0xFF12261C),
)

// ══════════════════════════════════════════════════════════════════════════
// 蓝潮（F · BLUE TIDE）—— 干净白卡 + 蓝 / 薄荷双主色 + 大圆角 + 无纸纹
// 浅色取自 Web `skins.css` F 段；深色按同色系压暗独立调校。
// ══════════════════════════════════════════════════════════════════════════
private val BlueTideSeaLight = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E8F8),
    onPrimaryContainer = Color(0xFF10243A),
    secondary = Color(0xFF00695C),
    onSecondary = Color(0xFFFFFFFF),
    // M3 FilterChip 选中态：蓝潮统一用主色实心 + onPrimary。
    secondaryContainer = Color(0xFF1565C0),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF26A69A),
    onTertiary = Color(0xFF10243A),
    background = Color(0xFFEEF3F8),
    onBackground = Color(0xFF10243A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10243A),
    surfaceVariant = Color(0xFFEEF3F8),
    onSurfaceVariant = Color(0xFF556579),
    outline = Color(0xFFDCE5EE),
    outlineVariant = Color(0xFFDCE5EE),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    scrim = Color(0xFF10243A),
    surfaceTint = Color(0xFF1565C0),
    inverseSurface = Color(0xFF10243A),
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF90CAF9),
    errorContainer = Color(0xFFFDE7E7),
    onErrorContainer = Color(0xFF8E1F1F),
    tertiaryContainer = Color(0xFFE0F2F1),
    onTertiaryContainer = Color(0xFF004D40),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FAFD),
    surfaceContainer = Color(0xFFF2F6FA),
    surfaceContainerHigh = Color(0xFFE6EEF6),
    surfaceContainerHighest = Color(0xFFFFFFFF),
)

private val BlueTideSeaDark = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0E1721),
    primaryContainer = Color(0xFF1E3A5C),
    onPrimaryContainer = Color(0xFFDCEBFA),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF0E1721),
    secondaryContainer = Color(0xFF90CAF9),
    onSecondaryContainer = Color(0xFF0E1721),
    tertiary = Color(0xFF80CBC4),
    onTertiary = Color(0xFF0E1721),
    background = Color(0xFF0E1721),
    onBackground = Color(0xFFE6EDF5),
    surface = Color(0xFF16212E),
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Color(0xFF1E2C3A),
    onSurfaceVariant = Color(0xFF9FB0C0),
    outline = Color(0xFF2A3A4C),
    outlineVariant = Color(0xFF2A3A4C),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF3A1414),
    scrim = Color(0xFF0E1721),
    surfaceTint = Color(0xFF90CAF9),
    inverseSurface = Color(0xFFE6EDF5),
    inverseOnSurface = Color(0xFF0E1721),
    inversePrimary = Color(0xFF1565C0),
    errorContainer = Color(0xFF3A2020),
    onErrorContainer = Color(0xFFF7BDBD),
    tertiaryContainer = Color(0xFF12302C),
    onTertiaryContainer = Color(0xFFCFE9E5),
    surfaceContainerLowest = Color(0xFF0E1721),
    surfaceContainerLow = Color(0xFF121C28),
    surfaceContainer = Color(0xFF16212E),
    surfaceContainerHigh = Color(0xFF1E2C3A),
    surfaceContainerHighest = Color(0xFF16212E),
)

private val BlueTideInlandLight = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7F0EB),
    onPrimaryContainer = Color(0xFF10243A),
    secondary = Color(0xFF1565C0),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF00695C),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF26A69A),
    onTertiary = Color(0xFF10243A),
    background = Color(0xFFEDF6F4),
    onBackground = Color(0xFF10243A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10243A),
    surfaceVariant = Color(0xFFEDF6F4),
    onSurfaceVariant = Color(0xFF4F6B66),
    outline = Color(0xFFD6E7E2),
    outlineVariant = Color(0xFFD6E7E2),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    scrim = Color(0xFF10243A),
    surfaceTint = Color(0xFF00695C),
    inverseSurface = Color(0xFF10243A),
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF80CBC4),
    errorContainer = Color(0xFFFDE7E7),
    onErrorContainer = Color(0xFF8E1F1F),
    tertiaryContainer = Color(0xFFE0F2F1),
    onTertiaryContainer = Color(0xFF004D40),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FBFA),
    surfaceContainer = Color(0xFFF2F9F7),
    surfaceContainerHigh = Color(0xFFE4F0ED),
    surfaceContainerHighest = Color(0xFFFFFFFF),
)

private val BlueTideInlandDark = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF0E1721),
    primaryContainer = Color(0xFF12302C),
    onPrimaryContainer = Color(0xFFCFE9E5),
    secondary = Color(0xFF90CAF9),
    onSecondary = Color(0xFF0E1721),
    secondaryContainer = Color(0xFF80CBC4),
    onSecondaryContainer = Color(0xFF0E1721),
    tertiary = Color(0xFF80CBC4),
    onTertiary = Color(0xFF0E1721),
    background = Color(0xFF0C1B18),
    onBackground = Color(0xFFE6EDF5),
    surface = Color(0xFF132420),
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Color(0xFF1B302B),
    onSurfaceVariant = Color(0xFF9FB8B2),
    outline = Color(0xFF2C433D),
    outlineVariant = Color(0xFF2C433D),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF3A1414),
    scrim = Color(0xFF0C1B18),
    surfaceTint = Color(0xFF80CBC4),
    inverseSurface = Color(0xFFE6EDF5),
    inverseOnSurface = Color(0xFF0C1B18),
    inversePrimary = Color(0xFF00695C),
    errorContainer = Color(0xFF3A2020),
    onErrorContainer = Color(0xFFF7BDBD),
    tertiaryContainer = Color(0xFF12302C),
    onTertiaryContainer = Color(0xFFCFE9E5),
    surfaceContainerLowest = Color(0xFF0C1B18),
    surfaceContainerLow = Color(0xFF0F1F1B),
    surfaceContainer = Color(0xFF132420),
    surfaceContainerHigh = Color(0xFF1B302B),
    surfaceContainerHighest = Color(0xFF132420),
)

/** 手帐圆角：卡 12 / 按钮 10 / 标签 20 / 浮层 14。 */
private val TideJournalShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** 蓝潮圆角：卡 16 / 按钮 12 / 浮层 20，仍然不改变任何组件高度。 */
private val BlueTideShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** 手帐标题用衬线（KaiTi 的字面替身），正文保持系统无衬线，字号/行高与基线一致。 */
private val TideJournalTypography = Typography(
    titleLarge = TextStyle(fontFamily = VintageFontFamily, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = VintageFontFamily, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontFamily = VintageFontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
)

/** 蓝潮字阶：系统无衬线、标题 700，干净现代。 */
private val BlueTideTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
)

/**
 * 路亚图鉴的形状：小圆角印章感（3 / 8 / 12 / 14 / 18），
 * 对应设计稿卡片 12px、Top10 条 8px、标签 20px 胶囊。
 * 仍是矩形圆角，不引入任何组件高度变化。
 */
private val FieldGuideShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

/**
 * 路亚图鉴的字阶：字号 / 行高与 `TideSpotTypography` 逐项全等，只换字族与字重——
 * 标题与标签用系统黑体更粗一档（Black / Bold），模拟设计稿的粗黑贴纸字；
 * 正文保持 Normal。**不改字号与行高**，避免换行位置与卡片高度漂移。
 */
private val FieldGuideTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Normal),
)

/**
 * 皮肤注册表——**唯一扩展点**（SKIN-DEV-STANDARD §4.2）。
 *
 * 加一套皮肤只允许做两件事：在 [all] 里追加一个 [TideSpotSkin] 实例、补对应资源；
 * **不得改任何业务 Composable**。若加皮肤必须改某个屏的代码，说明那个屏有硬编码色/写死样式，
 * 先治理它，再归入皮肤。
 *
 * [name] / [nameEn] / [id] 三者各自全局唯一，由 [validationErrors] 在类加载时校验；
 * 设置页下拉按 [name] 展示，靠唯一性保证「选中的就是用户看到的那一款」。
 */
object TideSpotSkins {

    val VintageOutdoor = TideSpotSkin(
        id = "a-vintage",
        // 皮肤显示名=数据值（注册表按名选择，与 preset 标签同类；品牌名不随语言翻译）
        name = "复古户外", // i18n-exempt: skin display name is data, not UI copy
        nameEn = "VINTAGE OUTDOOR",
        palettes = SkinPalettes(
            seaLight = SkinPalette(
                scheme = VintageSeaLight,
                mapLand = Color(0xFFD6C6A0),
                mapWater = Color(0xFF9FB8B4),
                mapRoad = Color(0xFFEFE6CC),
            ),
            seaDark = SkinPalette(
                scheme = VintageSeaDark,
                mapLand = Color(0xFF1B3044),
                mapWater = Color(0xFF123A55),
                mapRoad = Color(0xFF2A4358),
            ),
            inlandLight = SkinPalette(
                scheme = VintageInlandLight,
                mapLand = Color(0xFFCBDBA8),
                mapWater = Color(0xFF7FA98C),
                mapRoad = Color(0xFFEEF0D2),
            ),
            inlandDark = SkinPalette(
                scheme = VintageInlandDark,
                mapLand = Color(0xFF1C3326),
                mapWater = Color(0xFF123C2E),
                mapRoad = Color(0xFF2C4A38),
            ),
        ),
        shapes = VintageShapes,
        typography = VintageTypography,
        // CHG-93：收敛纸纹强度（设计稿是高频细噪，旧 1f 在真机形成可见网格）
        texture = SkinTexture(grainStrength = 0.65f),
    )

    /**
     * 路亚图鉴（D）：粗黑描边 + 硬投影贴纸 + 平涂纸底。
     *
     * 复用 A 的鱼形/母题 drawable（同一套单色可 tint 矢量），因此新增本皮肤**不需要**
     * 新资源、不需要改 `SettingsScreen` / `AppSettings` / 任何 `feature:*` 屏。
     * `paperGrain = false`：设计稿是干净平涂，不叠纸颗粒（消费点在后续铺开时接管）。
     */
    val FieldGuide = TideSpotSkin(
        id = "d-fieldguide",
        name = "路亚图鉴", // i18n-exempt: skin display name is data, not UI copy
        nameEn = "LURE FIELD GUIDE",
        palettes = SkinPalettes(
            seaLight = SkinPalette(
                scheme = FieldGuideSeaLight,
                mapLand = Color(0xFFDDE9D5),
                mapWater = Color(0xFFB7DAEE),
                mapRoad = Color(0xFFFFFDF7),
            ),
            seaDark = SkinPalette(
                scheme = FieldGuideSeaDark,
                mapLand = Color(0xFF16303F),
                mapWater = Color(0xFF17394C),
                mapRoad = Color(0xFF2A4A5C),
            ),
            inlandLight = SkinPalette(
                scheme = FieldGuideInlandLight,
                mapLand = Color(0xFFD9E8D0),
                mapWater = Color(0xFFB9E0CE),
                mapRoad = Color(0xFFFFFDF7),
            ),
            inlandDark = SkinPalette(
                scheme = FieldGuideInlandDark,
                mapLand = Color(0xFF18382A),
                mapWater = Color(0xFF16382A),
                mapRoad = Color(0xFF26503E),
            ),
        ),
        shapes = FieldGuideShapes,
        typography = FieldGuideTypography,
        texture = SkinTexture(
            paperGrain = false,
            grainStrength = 0.45f,
            printMisregister = true,
            handDrawnOutline = true,
            tapeDecoration = false,
        ),
    )

    /** 潮汐手帐（E）：纸手帐底 + 墨线 + 珊瑚贴纸，标题衬线、正文无衬线。 */
    val TideJournal = TideSpotSkin(
        id = "e-tidejournal",
        name = "潮汐手帐", // i18n-exempt: skin display name is data, not UI copy
        nameEn = "TIDE JOURNAL",
        palettes = SkinPalettes(
            seaLight = SkinPalette(
                scheme = TideJournalSeaLight,
                mapLand = Color(0xFFDDE9D5),
                mapWater = Color(0xFFB7DAEE),
                mapRoad = Color(0xFFFFFDF7),
            ),
            seaDark = SkinPalette(
                scheme = TideJournalSeaDark,
                mapLand = Color(0xFF16303F),
                mapWater = Color(0xFF17394C),
                mapRoad = Color(0xFF2A4A5C),
            ),
            inlandLight = SkinPalette(
                scheme = TideJournalInlandLight,
                mapLand = Color(0xFFD9E8D0),
                mapWater = Color(0xFFB9E0CE),
                mapRoad = Color(0xFFFFFDF7),
            ),
            inlandDark = SkinPalette(
                scheme = TideJournalInlandDark,
                mapLand = Color(0xFF18382A),
                mapWater = Color(0xFF16382A),
                mapRoad = Color(0xFF26503E),
            ),
        ),
        shapes = TideJournalShapes,
        typography = TideJournalTypography,
        texture = SkinTexture(
            paperGrain = true,
            grainStrength = 0.8f,
            printMisregister = true,
            // 设计稿是 4px 实心墨影：浅色几乎全不透明，深色略收。
            printShadowAlpha = 0.9f,
            handDrawnOutline = true,
            tapeDecoration = true,
        ),
    )

    /** 蓝潮（F）：干净白卡 + 蓝 / 薄荷 + 大圆角，无纸纹、无墨线、无贴纸。 */
    val BlueTide = TideSpotSkin(
        id = "f-bluetide",
        name = "蓝潮", // i18n-exempt: skin display name is data, not UI copy
        nameEn = "BLUE TIDE",
        palettes = SkinPalettes(
            seaLight = SkinPalette(
                scheme = BlueTideSeaLight,
                mapLand = Color(0xFFE8F0F7),
                mapWater = Color(0xFFCDE3F4),
                mapRoad = Color(0xFFFFFFFF),
            ),
            seaDark = SkinPalette(
                scheme = BlueTideSeaDark,
                mapLand = Color(0xFF142433),
                mapWater = Color(0xFF17384F),
                mapRoad = Color(0xFF2A4358),
            ),
            inlandLight = SkinPalette(
                scheme = BlueTideInlandLight,
                mapLand = Color(0xFFE7F2EF),
                mapWater = Color(0xFFCBE7E2),
                mapRoad = Color(0xFFFFFFFF),
            ),
            inlandDark = SkinPalette(
                scheme = BlueTideInlandDark,
                mapLand = Color(0xFF142A25),
                mapWater = Color(0xFF163A33),
                mapRoad = Color(0xFF2A4A42),
            ),
        ),
        shapes = BlueTideShapes,
        typography = BlueTideTypography,
        texture = SkinTexture(
            paperGrain = false,
            grainStrength = 0f,
            printMisregister = false,
            printShadowAlpha = 0f,
            handDrawnOutline = false,
            softShadow = true,
            softOutline = true,
            sectionOrnament = false,
            dashedDivider = false,
            tapeDecoration = false,
        ),
    )

    /** 全部内置皮肤（A 复古户外 + D 路亚图鉴 + E 潮汐手帐 + F 蓝潮）。 */
    val all: List<TideSpotSkin> = listOf(VintageOutdoor, FieldGuide, TideJournal, BlueTide)

    /** 缺省皮肤（`skinId` 缺失/非法时落回）。 */
    val default: TideSpotSkin = VintageOutdoor

    init {
        val errors = validationErrors()
        require(errors.isEmpty()) {
            // 开发期断言消息，不上屏（i18n 门禁：技术诊断用英文）
            "invalid skin registry: ${errors.joinToString("; ")}"
        }
    }

    /** 按持久化的 `skinId` 取皮肤；null / 未知 id 一律落 [default]（冷启动不崩）。 */
    fun byId(id: String?): TideSpotSkin =
        all.firstOrNull { it.id == id } ?: default

    /** 按中文名取皮肤（设置页下拉回填用）；找不到落 [default]。 */
    fun byName(name: String?): TideSpotSkin =
        all.firstOrNull { it.name == name } ?: default

    /**
     * 注册表自检：id / 中文名 / 英文名各自唯一且非空白，色板四件套齐全。
     * 新皮肤接入时单测必须断言本方法返回空列表。
     */
    fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        fun duplicated(values: List<String>): List<String> =
            values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.toList()

        // 以下均为开发期断言消息（不上屏），按 i18n 门禁口径用英文
        duplicated(all.map { it.id }).forEach { errors += "duplicated id: $it" }
        duplicated(all.map { it.name }).forEach { errors += "duplicated name: $it" }
        duplicated(all.map { it.nameEn }).forEach { errors += "duplicated nameEn: $it" }
        all.filter { it.id.isBlank() }.forEach { errors += "blank id" }
        all.filter { it.name.isBlank() }.forEach { errors += "blank name" }
        all.filter { it.nameEn.isBlank() }.forEach { errors += "blank nameEn" }
        all.forEach {
            if (it.palettes.seaLight.scheme == it.palettes.seaDark.scheme) {
                errors += "${it.id}: sea light/dark schemes are identical (dark must be tuned independently)"
            }
            if (it.palettes.inlandLight.scheme == it.palettes.inlandDark.scheme) {
                errors += "${it.id}: inland light/dark schemes are identical (dark must be tuned independently)"
            }
        }
        return errors
    }
}
