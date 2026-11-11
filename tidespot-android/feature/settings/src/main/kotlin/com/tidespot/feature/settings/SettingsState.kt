package com.tidespot.feature.settings

import androidx.annotation.StringRes
import com.tidespot.core.data.remote.TideSpotUrls

/** 单位制（CHG-16⑱）：内部恒 SI（CONTRACT 冻结点 3），换算仅在 UI/设置层展示语义。 */
enum class UnitSystem { SI, IMPERIAL }

/**
 * 地图底图样式（UX-19 / OPT-V11-19；D8 三选）。
 * URL 全部收敛在 [TideSpotUrls]（不得回退点 3）；默认 [VOYAGER]；
 * **深色系统模式不覆盖用户选择**（用户显式选择优先，无自动切样式逻辑）。
 *
 * OPT-V11-13：展示名走资源（[labelRes]），[description] 为产品名（跨语言不变）。
 */
enum class MapStyle(@StringRes val labelRes: Int, val description: String, val url: String) {
    VOYAGER(R.string.ts_settings_style_color, "CARTO Voyager", TideSpotUrls.CARTO_VOYAGER_STYLE_URL),
    POSITRON(R.string.ts_settings_style_plain, "CARTO Positron", TideSpotUrls.CARTO_POSITRON_STYLE_URL),
    DARK_MATTER(R.string.ts_settings_style_dark, "CARTO Dark Matter", TideSpotUrls.CARTO_DARK_MATTER_STYLE_URL),
}

/**
 * 设置页状态（纯 data；M6 批次 2 无持久化接线，读写经 [SettingsStore] 键值由 :app 批次 3 组装）。
 */
data class SettingsState(
    val unitSystem: UnitSystem = UnitSystem.SI,
    /** 详情天数 7|10（SettingsLogic.coerceDays → DaySelectorLogic.coerceDaysRange 单源收敛）。 */
    val detailDays: Int = 7,
    /** 预取开关（后台预热；默认开）。 */
    val prefetchEnabled: Boolean = true,
    /** 逆地理总开关（RG-A BigDataCloud / RG-B Photon；关 → 全链落坐标文本兜底）。 */
    val reverseGeoEnabled: Boolean = true,
    /** 可选 API Key 占位（null=未设置；当前无消费方，M6 仅存储展示）。 */
    val apiKey: String? = null,
    /** CHG-25（P0-1）：Copernicus 凭据是否已配置（运行期由 :app 从凭据存储读取，不落本状态持久化）。 */
    val copernicusConfigured: Boolean = false,
    /** UX-19（OPT-V11-19）：地图底图样式（默认彩色 Voyager；系统深色不覆盖用户选择）。 */
    val mapStyle: MapStyle = MapStyle.VOYAGER,
)

/** 设置键名唯一清单（:app 批次 3 SharedPreferences 实现按此对齐）。 */
object SettingsKeys {
    const val UNIT_SYSTEM = "settings.unitSystem"
    const val DETAIL_DAYS = "settings.detailDays"
    const val PREFETCH_ENABLED = "settings.prefetchEnabled"
    const val REVERSE_GEO_ENABLED = "settings.reverseGeoEnabled"
    const val API_KEY = "settings.apiKey"
    const val MAP_STYLE = "settings.mapStyle"
}
