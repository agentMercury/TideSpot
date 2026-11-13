package com.tidespot.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

/**
 * Compose 色 → MapLibre 样式属性用的 `#RRGGBB` 字符串。
 *
 * 地图图层（`SymbolLayer` 的 `textColor` / `textHaloColor` 等）不经过 Compose 主题，
 * 必须把皮肤 token 显式转成字符串传入——否则换肤后地图层仍是写死色，属于「换肤漏屏」。
 *
 * 固定 `Locale.US`，避免十六进制大小写随设备区域漂移（MapLibre 解析大小写不敏感，
 * 但输出稳定更利于快照/日志比对）。
 */
fun Color.toHexRgb(): String = String.format(Locale.US, "#%06X", toArgb() and 0xFFFFFF)
