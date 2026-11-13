package com.tidespot.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.tidespot.core.domain.model.WaterBodyType

/**
 * TideSpot 全局主题——**唯一主题入口**（SKIN-2）。
 *
 * 最终视觉 = 皮肤 token(水体) × 明暗模式：
 *  - [skin]：用户偏好（首版仅 `TideSpotSkins.VintageOutdoor`），来自设置 `settings.skinId`；
 *  - [water]：点位数据决定（海 / 内陆），用户不可切。App 外壳页（地图/收藏/设置）跨水体，
 *    因此默认 [WaterBodyType.OCEAN]；进入详情页时由 `DetailPage` 按点位水体再包一层同 skin 主题覆盖；
 *  - [darkTheme]：`SYSTEM` / `LIGHT` / `DARK` 的解析结果（`SettingsLogic.resolveDarkTheme`）。
 *
 * 三轴各自持久化、互不重置；任一轴切换只影响本轴。
 *
 * 皮肤 token 是唯一色彩来源：所有 Composable 从 `MaterialTheme.colorScheme` /
 * `MaterialTheme.shapes` / `MaterialTheme.typography` 或 [LocalSkin] 取，禁止 `Color(0x...)`。
 * 冻结语义色（五档 `ScoreBand` / 六徽标 `ConfidenceBadge`）不经本主题，直取单源。
 */
@Composable
fun TideSpotTheme(
    skin: TideSpotSkin = TideSpotSkins.default,
    water: WaterBodyType = WaterBodyType.OCEAN,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = skin.palettes.pick(water, darkTheme)
    CompositionLocalProvider(LocalSkin provides skin) {
        MaterialTheme(
            colorScheme = palette.scheme,
            shapes = skin.shapes,
            typography = skin.typography,
            content = content,
        )
    }
}
