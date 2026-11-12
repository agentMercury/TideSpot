package com.tidespot.feature.map.infobar

import com.tidespot.core.domain.model.NamedPlace
import com.tidespot.core.domain.util.OpenLocationCode
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.strings.ZhStrings
import java.util.Locale

/** 信息条模式：已确认展示 / 预览（拖动中，不切换已确认数据，F-1.4）。 */
enum class InfoBarMode { CONFIRMED, PREVIEW }

data class InfoBarState(
    val mainText: String,
    val subText: String? = null,
    val hint: String? = null,
) {
    /** F-1.3 红线：信息条任何情况下不得为空白。 */
    val isNotBlank: Boolean get() = mainText.isNotBlank()
}

/** 坐标 6 位小数（Locale.ROOT，避免区域分隔符污染）：29.600000, 119.038000。 */
fun formatCoordinate(lat: Double, lon: Double): String =
    String.format(Locale.ROOT, "%.6f, %.6f", lat, lon)

/**
 * 信息条构建（纯函数，永不空白）：
 * - 逆地理开启：displayName · subLabel → 无名称退坐标 → 无坐标退「未命名水域」；
 * - 逆地理关闭（布尔位，N-5）：坐标 + plusCode（CHG-09⑤：本地编码统一走
 *   `com.tidespot.core.domain.util.OpenLocationCode` 单一实现，本模块浮点递除版已删除）；
 * - Preview 追加「移动中，点确定切换」。
 *
 * OPT-V11-13：兜底名与预览提示由 [TideSpotStrings] 注入（默认 [ZhStrings] 零行为漂移）。
 */
fun buildInfoBarState(
    mode: InfoBarMode,
    lat: Double?,
    lon: Double?,
    place: NamedPlace?,
    reverseGeoEnabled: Boolean,
    strings: TideSpotStrings = ZhStrings,
): InfoBarState {
    val coord = if (lat != null && lon != null) formatCoordinate(lat, lon) else null
    val main: String
    val sub: String?
    when {
        !reverseGeoEnabled -> {
            main = coord ?: strings.unnamedWater
            sub = place?.plusCode
                ?: if (lat != null && lon != null) OpenLocationCode.encode(lat, lon) else null
        }
        place != null && place.displayName.isNotBlank() -> {
            main = place.subLabel?.takeIf { it.isNotBlank() }
                ?.let { "${place.displayName} · $it" }
                ?: place.displayName
            sub = coord
        }
        else -> {
            main = coord ?: strings.unnamedWater
            sub = null
        }
    }
    return InfoBarState(
        mainText = main,
        subText = sub,
        hint = if (mode == InfoBarMode.PREVIEW) strings.previewHint else null,
    )
}
