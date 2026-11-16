package com.tidespot.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 皮肤强调色语义（A / D 共用结构，但强调色角色不同）。
 *
 * - A · 复古户外：砖红是「评分 / 选中日期 / 标签」的强调色，取 `error`（浅 #C15A3A / 深 #E0805E）。
 * - D · 路亚图鉴：珊瑚是贴纸强调色，取 `tertiary`；日期选中与评分同源。
 *
 * 只读 ColorScheme token，不新增硬编码色值，也不改变冻结的五档/六徽标色。
 */
@Composable
fun skinAccent(): Color =
    if (LocalSkin.current.id == TideSpotSkins.VintageOutdoor.id) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary
    }

/** 强调色上的前景色：A 用 onError，D 用 onTertiary。 */
@Composable
fun onSkinAccent(): Color =
    if (LocalSkin.current.id == TideSpotSkins.VintageOutdoor.id) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onTertiary
    }

/**
 * 小字号强调文字：A 的 error 在纸底仅 ~3.1–3.9:1，不能直接作文本色；
 * 用同色系更深的 onErrorContainer。D 继续用 primary（蓝墨/青墨）。
 */
@Composable
fun skinAccentInk(): Color =
    if (LocalSkin.current.id == TideSpotSkins.VintageOutdoor.id) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.primary
    }