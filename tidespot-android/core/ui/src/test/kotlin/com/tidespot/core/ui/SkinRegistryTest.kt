package com.tidespot.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tidespot.core.domain.model.WaterBodyType
import com.tidespot.core.ui.theme.TideSpotSkins
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 皮肤注册表锁测试（SKIN-1）。
 *
 * 锁三件事：
 *  1. 注册表自检为空——每款皮肤有唯一 id / 中文名 / 英文名，且海陆浅深四套色板齐全；
 *  2. 查找与兜底语义（`byId` 未知 id 落 default，冷启动不崩）；
 *  3. A 的四套关键色值与试点期一致——防止后续改皮肤时误改 token 导致详情页视觉回归。
 */
class SkinRegistryTest {

    @Test
    fun registry_hasNoValidationErrors() {
        assertEquals(emptyList(), TideSpotSkins.validationErrors())
    }

    @Test
    fun skinIdsAndNames_areUnique() {
        val ids = TideSpotSkins.all.map { it.id }
        val names = TideSpotSkins.all.map { it.name }
        val namesEn = TideSpotSkins.all.map { it.nameEn }
        assertEquals(ids.size, ids.toSet().size, "皮肤 id 必须唯一")
        assertEquals(names.size, names.toSet().size, "皮肤中文名必须唯一")
        assertEquals(namesEn.size, namesEn.toSet().size, "皮肤英文名必须唯一")
        assertTrue(TideSpotSkins.all.isNotEmpty())
        assertTrue(TideSpotSkins.all.none { it.name.isBlank() })
    }

    @Test
    fun lookup_fallsBackToDefault() {
        assertSame(TideSpotSkins.VintageOutdoor, TideSpotSkins.default)
        assertSame(TideSpotSkins.VintageOutdoor, TideSpotSkins.byId("a-vintage"))
        assertSame(TideSpotSkins.VintageOutdoor, TideSpotSkins.byId(null))
        assertSame(TideSpotSkins.VintageOutdoor, TideSpotSkins.byId("does-not-exist"))
        assertSame(TideSpotSkins.VintageOutdoor, TideSpotSkins.byName("复古户外"))
        assertSame(TideSpotSkins.VintageOutdoor, TideSpotSkins.byName("不存在"))
    }

    @Test
    fun fieldGuide_isRegisteredAndSelectable() {
        assertEquals(4, TideSpotSkins.all.size)
        assertSame(TideSpotSkins.FieldGuide, TideSpotSkins.byId("d-fieldguide"))
        assertSame(TideSpotSkins.FieldGuide, TideSpotSkins.byName("路亚图鉴"))
        assertSame(TideSpotSkins.TideJournal, TideSpotSkins.byId("e-tidejournal"))
        assertSame(TideSpotSkins.TideJournal, TideSpotSkins.byName("潮汐手帐"))
        assertSame(TideSpotSkins.BlueTide, TideSpotSkins.byId("f-bluetide"))
        assertSame(TideSpotSkins.BlueTide, TideSpotSkins.byName("蓝潮"))
        // 新增皮肤不得改变缺省皮肤（冷启动缺省仍落 A）
        assertSame(TideSpotSkins.VintageOutdoor, TideSpotSkins.default)
    }

    @Test
    fun palettesPick_byWaterAndDarkness() {
        val p = TideSpotSkins.VintageOutdoor.palettes
        assertSame(p.seaLight, p.pick(WaterBodyType.OCEAN, darkTheme = false))
        assertSame(p.seaDark, p.pick(WaterBodyType.OCEAN, darkTheme = true))
        assertSame(p.seaLight, p.pick(WaterBodyType.NEARSHORE, darkTheme = false))
        assertSame(p.inlandLight, p.pick(WaterBodyType.INLAND_WATER, darkTheme = false))
        assertSame(p.inlandDark, p.pick(WaterBodyType.INLAND_WATER, darkTheme = true))
        // 非水体（锚点落在陆地 / 未知）沿用海洋色板兜底，与试点口径一致
        assertSame(p.seaLight, p.pick(WaterBodyType.LAND, darkTheme = false))
    }

    @Test
    fun vintagePalettes_keepPilotValues() {
        val p = TideSpotSkins.VintageOutdoor.palettes
        // 浅色海洋：做旧米纸 + 藏蓝墨
        assertScheme(
            p.seaLight, bg = 0xFFE4D9BC, primary = 0xFF1B3A5C, surface = 0xFFF2EAD4,
            surfaceVariant = 0xFFE8DDBE, onSurfaceVariant = 0xFF5C5540, outline = 0xFF16324F,
            outlineVariant = 0xFF8A7C60, error = 0xFFC15A3A, highest = 0xFFF2EAD4,
        )
        // 浅色内陆：苔绿纸 + 墨绿
        assertScheme(
            p.inlandLight, bg = 0xFFDCE2C4, primary = 0xFF2E5A48, surface = 0xFFF1EED8,
            surfaceVariant = 0xFFE4E6C6, onSurfaceVariant = 0xFF556045, outline = 0xFF213D2A,
            outlineVariant = 0xFF78815C, error = 0xFFC15A3A, highest = 0xFFF1EED8,
        )
        // 深色海洋 / 内陆：深蓝纸 / 深苔纸 + 奶油墨线 + 珊瑚/琥珀强调（对齐 Web A）
        assertScheme(
            p.seaDark, bg = 0xFF101A24, primary = 0xFFE9DCBE, surface = 0xFF1A2836,
            surfaceVariant = 0xFF243547, onSurfaceVariant = 0xFFA89F88, outline = 0xFFE9DCBE,
            outlineVariant = 0xFF6E6552, error = 0xFFE0805E, highest = 0xFF1A2836,
        )
        assertScheme(
            p.inlandDark, bg = 0xFF121B16, primary = 0xFFE6DFC0, surface = 0xFF1C2A21,
            surfaceVariant = 0xFF26382C, onSurfaceVariant = 0xFFA9B09B, outline = 0xFFE6DFC0,
            outlineVariant = 0xFF6B7763, error = 0xFFE08A6A, highest = 0xFF1C2A21,
        )
        // 地图三色（engine.py PALETTE 真值，非编造）
        assertEquals(0xFFD6C6A0.toInt(), p.seaLight.mapLand.toArgb())
        assertEquals(0xFF7FA98C.toInt(), p.inlandLight.mapWater.toArgb())
        assertEquals(0xFF123A55.toInt(), p.seaDark.mapWater.toArgb())
    }

    @Test
    fun fieldGuidePalettes_keepDesignValues() {
        val p = TideSpotSkins.FieldGuide.palettes
        // 浅色海洋：冷白纸 + 海蓝 + 珊瑚贴纸
        assertScheme(
            p.seaLight, bg = 0xFFE9F3FA, primary = 0xFF1E6FA8, surface = 0xFFFFFDF7,
            surfaceVariant = 0xFFB7DAEE, onSurfaceVariant = 0xFF33556B, outline = 0xFF101820,
            outlineVariant = 0xFF33556B, error = 0xFFC23B2A, highest = 0xFFFFFDF7,
        )
        // 浅色内陆：薄荷纸 + 青绿 + 琥珀贴纸
        assertScheme(
            p.inlandLight, bg = 0xFFE9F5EE, primary = 0xFF2F7F6B, surface = 0xFFFFFDF7,
            surfaceVariant = 0xFFB9E0CE, onSurfaceVariant = 0xFF3A5548, outline = 0xFF101820,
            outlineVariant = 0xFF3A5548, error = 0xFFC23B2A, highest = 0xFFFFFDF7,
        )
        // 深色海洋 / 内陆：独立色板（非浅色机械反转）
        assertScheme(
            p.seaDark, bg = 0xFF0B1620, primary = 0xFF6FB6E8, surface = 0xFF12222E,
            surfaceVariant = 0xFF17394C, onSurfaceVariant = 0xFF9FB6C4, outline = 0xFFF2EFE6,
            outlineVariant = 0xFF3A4A54, error = 0xFFFF8A70, highest = 0xFF12222E,
        )
        assertScheme(
            p.inlandDark, bg = 0xFF0C1A14, primary = 0xFF67C2A5, surface = 0xFF12261C,
            surfaceVariant = 0xFF16382A, onSurfaceVariant = 0xFF9DB8A8, outline = 0xFFF2EFE6,
            outlineVariant = 0xFF34483C, error = 0xFFFF8A70, highest = 0xFF12261C,
        )
        // V3 的珊瑚 / 琥珀强调色（贴纸色）不得被 M3 默认值覆盖
        assertEquals(0xFFE5644C.toInt(), p.seaLight.scheme.tertiary.toArgb())
        assertEquals(0xFFE9A13B.toInt(), p.inlandLight.scheme.tertiary.toArgb())
        assertEquals(0xFFFFC15E.toInt(), p.inlandDark.scheme.tertiary.toArgb())
    }

    @Test
    fun tideJournalPalettes_matchWeb() {
        val p = TideSpotSkins.TideJournal.palettes
        // 与 D 共用纸/墨源色，海洋强调色换成珊瑚墨
        assertEquals(0xFFE9F3FA.toInt(), p.seaLight.scheme.background.toArgb())
        assertEquals(0xFFE5644C.toInt(), p.seaLight.scheme.tertiary.toArgb())
        assertEquals(0xFF456A84.toInt(), p.seaLight.scheme.secondary.toArgb())
        assertEquals(0xFFE9F5EE.toInt(), p.inlandLight.scheme.background.toArgb())
        assertEquals(0xFFE9A13B.toInt(), p.inlandLight.scheme.tertiary.toArgb())
        assertEquals(0xFF0B1620.toInt(), p.seaDark.scheme.background.toArgb())
        assertEquals(0xFF0C1A14.toInt(), p.inlandDark.scheme.background.toArgb())
        // M3 FilterChip 选中态：墨底纸字（深色下反转为纸底墨字）
        assertEquals(0xFF101820.toInt(), p.seaLight.scheme.secondaryContainer.toArgb())
        assertEquals(0xFFFFFDF7.toInt(), p.seaLight.scheme.onSecondaryContainer.toArgb())
        assertEquals(0xFFF2EFE6.toInt(), p.seaDark.scheme.secondaryContainer.toArgb())
        // 纸手帐：纸纹 + 墨线 + 实墨投影
        assertEquals(0.8f, TideSpotSkins.TideJournal.texture.grainStrength)
        assertEquals(0.9f, TideSpotSkins.TideJournal.texture.printShadowAlpha)
        assertTrue(TideSpotSkins.TideJournal.texture.handDrawnOutline)
    }

    @Test
    fun blueTidePalettes_matchWeb() {
        val p = TideSpotSkins.BlueTide.palettes
        // 浅色海洋：白卡 + 蓝 / 薄荷
        assertScheme(
            p.seaLight, bg = 0xFFEEF3F8, primary = 0xFF1565C0, surface = 0xFFFFFFFF,
            surfaceVariant = 0xFFEEF3F8, onSurfaceVariant = 0xFF556579, outline = 0xFFDCE5EE,
            outlineVariant = 0xFFDCE5EE, error = 0xFFC62828, highest = 0xFFFFFFFF,
        )
        assertEquals(0xFF00695C.toInt(), p.seaLight.scheme.secondary.toArgb())
        assertEquals(0xFF26A69A.toInt(), p.seaLight.scheme.tertiary.toArgb())
        // M3 FilterChip 选中态：主色实心 + onPrimary
        assertEquals(0xFF1565C0.toInt(), p.seaLight.scheme.secondaryContainer.toArgb())
        assertEquals(0xFFFFFFFF.toInt(), p.seaLight.scheme.onSecondaryContainer.toArgb())
        assertEquals(0xFF90CAF9.toInt(), p.seaDark.scheme.secondaryContainer.toArgb())
        // 浅色内陆：白卡 + 青绿 / 天蓝
        assertScheme(
            p.inlandLight, bg = 0xFFEDF6F4, primary = 0xFF00695C, surface = 0xFFFFFFFF,
            surfaceVariant = 0xFFEDF6F4, onSurfaceVariant = 0xFF4F6B66, outline = 0xFFD6E7E2,
            outlineVariant = 0xFFD6E7E2, error = 0xFFC62828, highest = 0xFFFFFFFF,
        )
        // 深色独立调校
        assertScheme(
            p.seaDark, bg = 0xFF0E1721, primary = 0xFF90CAF9, surface = 0xFF16212E,
            surfaceVariant = 0xFF1E2C3A, onSurfaceVariant = 0xFF9FB0C0, outline = 0xFF2A3A4C,
            outlineVariant = 0xFF2A3A4C, error = 0xFFEF9A9A, highest = 0xFF16212E,
        )
        assertScheme(
            p.inlandDark, bg = 0xFF0C1B18, primary = 0xFF80CBC4, surface = 0xFF132420,
            surfaceVariant = 0xFF1B302B, onSurfaceVariant = 0xFF9FB8B2, outline = 0xFF2C433D,
            outlineVariant = 0xFF2C433D, error = 0xFFEF9A9A, highest = 0xFF132420,
        )
        // 干净白卡：无纸纹 / 无墨线 / 无贴纸
        val texture = TideSpotSkins.BlueTide.texture
        assertTrue(!texture.paperGrain && !texture.printMisregister && !texture.handDrawnOutline)
        assertTrue(texture.softShadow && !texture.sectionOrnament && !texture.dashedDivider)
        assertTrue(texture.softOutline)
    }

    @Test
    fun allSkins_textPairsMeetContrast() {
        val pairs: List<Pair<String, (ColorScheme) -> Pair<Color, Color>>> = listOf(
            "onBackground/background" to { s -> s.onBackground to s.background },
            "onSurface/surface" to { s -> s.onSurface to s.surface },
            "onSurfaceVariant/surfaceVariant" to { s -> s.onSurfaceVariant to s.surfaceVariant },
            "onPrimary/primary" to { s -> s.onPrimary to s.primary },
            "onPrimaryContainer/primaryContainer" to { s -> s.onPrimaryContainer to s.primaryContainer },
            "onSecondary/secondary" to { s -> s.onSecondary to s.secondary },
            "onSecondaryContainer/secondaryContainer" to { s -> s.onSecondaryContainer to s.secondaryContainer },
            "onTertiary/tertiary" to { s -> s.onTertiary to s.tertiary },
            "onTertiaryContainer/tertiaryContainer" to { s -> s.onTertiaryContainer to s.tertiaryContainer },
            // `onError/error` 不在此列：A 的 error 是填充色（浅纸底文字对比 3.1–3.7），
            // 标准 §4.8 明确它不作文本色，状态文字走 onErrorContainer（下方已锁）。
            "onErrorContainer/errorContainer" to { s -> s.onErrorContainer to s.errorContainer },
        )
        TideSpotSkins.all.forEach { skin ->
            skin.palettes.let { p ->
                listOf(
                    "sea-light" to p.seaLight,
                    "sea-dark" to p.seaDark,
                    "inland-light" to p.inlandLight,
                    "inland-dark" to p.inlandDark,
                ).forEach { (label, palette) ->
                    pairs.forEach { (pairName, select) ->
                        val (fg, bg) = select(palette.scheme)
                        val ratio = contrastRatio(fg, bg)
                        assertTrue(
                            ratio >= 4.5,
                            "${skin.id}/$label $pairName 对比度 ${"%.2f".format(ratio)} < 4.5",
                        )
                    }
                }
            }
        }
    }

    /** WCAG 2.1 相对亮度对比度（1..21）；Compose Color 的 red/green/blue 为 sRGB 分量。 */
    private fun contrastRatio(fg: Color, bg: Color): Double {
        val a = relativeLuminance(fg)
        val b = relativeLuminance(bg)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun assertScheme(
        palette: com.tidespot.core.ui.theme.SkinPalette,
        bg: Long,
        primary: Long,
        surface: Long,
        surfaceVariant: Long,
        onSurfaceVariant: Long,
        outline: Long,
        outlineVariant: Long,
        error: Long,
        highest: Long,
    ) {
        val s = palette.scheme
        assertEquals(bg.toInt(), s.background.toArgb(), "background")
        assertEquals(primary.toInt(), s.primary.toArgb(), "primary")
        assertEquals(surface.toInt(), s.surface.toArgb(), "surface")
        assertEquals(surfaceVariant.toInt(), s.surfaceVariant.toArgb(), "surfaceVariant")
        assertEquals(onSurfaceVariant.toInt(), s.onSurfaceVariant.toArgb(), "onSurfaceVariant")
        assertEquals(outline.toInt(), s.outline.toArgb(), "outline")
        assertEquals(outlineVariant.toInt(), s.outlineVariant.toArgb(), "outlineVariant")
        assertEquals(error.toInt(), s.error.toArgb(), "error")
        assertEquals(highest.toInt(), s.surfaceContainerHighest.toArgb(), "surfaceContainerHighest")
    }

    @Test
    fun texture_motifFollowsWaterBody() {
        val texture = TideSpotSkins.VintageOutdoor.texture
        assertEquals(texture.waveMotifRes, texture.motifFor(WaterBodyType.OCEAN))
        assertEquals(texture.mountMotifRes, texture.motifFor(WaterBodyType.INLAND_WATER))
        assertTrue(texture.grainStrength in 0f..1f)
        // CHG-93：纸纹强度与 spec.json visual_tokens 声明一致（防漂移）
        assertEquals(0.65f, texture.grainStrength)
    }
}
