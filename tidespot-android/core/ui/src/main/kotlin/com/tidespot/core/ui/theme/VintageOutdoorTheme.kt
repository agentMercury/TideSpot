package com.tidespot.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.tidespot.core.domain.model.WaterBodyType

/**
 * 手绘 A · 复古户外 —— 详情页试点用的**兼容薄封装**（已退役为 token 派生）。
 *
 * 历史：SKIN-1 之前，本文件内写死 4 套 `ColorScheme` + 形状 + 字阶；
 * 现在这些 token 全部迁到 [TideSpotSkins.VintageOutdoor]（见 `TideSpotSkin.kt`），
 * 本函数只负责「按水体 + 明暗选一套色板并注入」，签名保持不变，
 * 以便 `MainActivity` 的详情页调用点零改动、试点视觉零回归。
 *
 * 新代码不要再调用本函数：统一用 `TideSpotTheme(skin, water, darkTheme)`（SKIN-2）。
 * 保留它只是为了在全局换肤铺开前，让详情页仍能单独运行同一套 token。
 */
@Composable
fun VintageOutdoorTheme(
    waterBody: WaterBodyType,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // 继承外壳当前皮肤（SKIN-2 后全局皮肤可能是 A 或 D），本函数只负责按点位水体换色板。
    val skin = LocalSkin.current
    val palette = skin.palettes.pick(waterBody, darkTheme)
    CompositionLocalProvider(LocalSkin provides skin) {
        MaterialTheme(
            colorScheme = palette.scheme,
            shapes = skin.shapes,
            typography = skin.typography,
            content = content,
        )
    }
}
