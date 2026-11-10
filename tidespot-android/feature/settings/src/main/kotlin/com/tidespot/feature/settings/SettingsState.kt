package com.tidespot.feature.settings

/** 单位制（CHG-16⑱）：内部恒 SI（CONTRACT 冻结点 3），换算仅在 UI/设置层展示语义。 */
enum class UnitSystem { SI, IMPERIAL }

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
)

/** 设置键名唯一清单（:app 批次 3 SharedPreferences 实现按此对齐）。 */
object SettingsKeys {
    const val UNIT_SYSTEM = "settings.unitSystem"
    const val DETAIL_DAYS = "settings.detailDays"
    const val PREFETCH_ENABLED = "settings.prefetchEnabled"
    const val REVERSE_GEO_ENABLED = "settings.reverseGeoEnabled"
    const val API_KEY = "settings.apiKey"
}
