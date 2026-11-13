package com.tidespot.core.ui

import androidx.compose.ui.graphics.toArgb
import com.tidespot.core.domain.model.WaterBodyType
import com.tidespot.core.ui.theme.TideSpotSkins
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
        // 深色海洋 / 内陆：独立色板（非浅色机械反转）
        assertScheme(
            p.seaDark, bg = 0xFF0E1C2B, primary = 0xFF90CAF9, surface = 0xFF17293B,
            surfaceVariant = 0xFF1E344A, onSurfaceVariant = 0xFF9FB0C0, outline = 0xFF3A4C5E,
            outlineVariant = 0xFF3A4C5E, error = 0xFFFF8A65, highest = 0xFF26405A,
        )
        assertScheme(
            p.inlandDark, bg = 0xFF101D18, primary = 0xFFA5D6A7, surface = 0xFF172B21,
            surfaceVariant = 0xFF1E372A, onSurfaceVariant = 0xFF9DB8A8, outline = 0xFF3A5346,
            outlineVariant = 0xFF3A5346, error = 0xFFFFAB91, highest = 0xFF2A4736,
        )
        // 地图三色（engine.py PALETTE 真值，非编造）
        assertEquals(0xFFD6C6A0.toInt(), p.seaLight.mapLand.toArgb())
        assertEquals(0xFF7FA98C.toInt(), p.inlandLight.mapWater.toArgb())
        assertEquals(0xFF123A55.toInt(), p.seaDark.mapWater.toArgb())
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
    }
}
