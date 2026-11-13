package com.tidespot.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
 *  - **皮肤**：本文件的数据类，用户可选，首版仅 [TideSpotSkins.VintageOutdoor]；
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
 * [mapLand] / [mapWater] / [mapRoad] 是设计稿的地图三色（CARTO 底图不重画，
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
    val handDrawnOutline: Boolean = true,
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
    surfaceContainerHighest = Color(0xFFCBB98E),
)

// ── 手绘 A：浅色 · 内陆水体（墨绿 + 苔绿纸）────────────────────────────────
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
    surfaceContainerHighest = Color(0xFFBFC99E),
)

// ── 手绘 A：深色 · 海洋 / 近海（独立调色，非浅色机械反转）───────────────────
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
    surfaceTint = Color(0xFF90CAF9),
    inverseSurface = Color(0xFFE6EDF5),
    inverseOnSurface = Color(0xFF0E1C2B),
    inversePrimary = Color(0xFF1B3A5C),
    errorContainer = Color(0xFF3A2A26),
    onErrorContainer = Color(0xFFFF8A65),
    tertiaryContainer = Color(0xFF1E344A),
    onTertiaryContainer = Color(0xFFE6EDF5),
    surfaceContainerLowest = Color(0xFF0E1C2B),
    surfaceContainerLow = Color(0xFF132538),
    surfaceContainer = Color(0xFF17293B),
    surfaceContainerHigh = Color(0xFF1E344A),
    surfaceContainerHighest = Color(0xFF26405A),
)

// ── 手绘 A：深色 · 内陆水体 ────────────────────────────────────────────────
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
    surfaceTint = Color(0xFFA5D6A7),
    inverseSurface = Color(0xFFE6F0E9),
    inverseOnSurface = Color(0xFF101D18),
    inversePrimary = Color(0xFF2E5A48),
    errorContainer = Color(0xFF3A2A24),
    onErrorContainer = Color(0xFFFFAB91),
    tertiaryContainer = Color(0xFF1E372A),
    onTertiaryContainer = Color(0xFFE6F0E9),
    surfaceContainerLowest = Color(0xFF101D18),
    surfaceContainerLow = Color(0xFF162A20),
    surfaceContainer = Color(0xFF172B21),
    surfaceContainerHigh = Color(0xFF1E372A),
    surfaceContainerHighest = Color(0xFF2A4736),
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
 * 字阶与基础 `TideSpotTypography` 逐项一致（字号 / 行高 / 字重全等），仅把字族换为系统衬线，
 * 避免字体缺失导致行高或换行位置变化。中文回退系统 serif（设计稿 KaiTi → serif 同样回退）。
 *
 * **不要为了「更像手绘」调字号或行高**——会破坏所有屏的换行与卡片高度。
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
        texture = SkinTexture(),
    )

    /** 全部内置皮肤（首版仅 A）；未来加皮肤只往这里追加。 */
    val all: List<TideSpotSkin> = listOf(VintageOutdoor)

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
