package com.tidespot.core.ui.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 当前皮肤（SKIN-1）。
 *
 * 由 [TideSpotTheme]（全局）或 [VintageOutdoorTheme]（详情页试点兼容层）提供；
 * 业务 Composable 只读，不写。
 *
 * 用途：`MaterialTheme` 只能暴露 ColorScheme / Shapes / Typography，
 * 拿不到「纸纹强度」「鱼形母题」「皮肤 id/名称」这类扩展 token，
 * 需要它们的组件从这里取，例如：
 * ```
 * val skin = LocalSkin.current
 * val grain = rememberPaperGrainBrush(darkTheme)
 * Box(Modifier.paperGrain(grain, skin.texture.grainStrength))
 * ```
 *
 * 用 `staticCompositionLocalOf`：皮肤切换频率极低（用户手动选择），
 * 换肤时整棵子树重组正是期望行为，且省掉每次读取的订阅开销。
 */
val LocalSkin: ProvidableCompositionLocal<TideSpotSkin> =
    staticCompositionLocalOf { TideSpotSkins.default }
