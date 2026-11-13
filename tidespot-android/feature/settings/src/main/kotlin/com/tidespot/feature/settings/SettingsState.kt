package com.tidespot.feature.settings

import androidx.annotation.StringRes
import com.tidespot.core.data.remote.TideSpotUrls
import com.tidespot.core.ui.theme.TideSpotSkins

/** 单位制（CHG-16⑱）：内部恒 SI（CONTRACT 冻结点 3），换算仅在 UI/设置层展示语义。 */
enum class UnitSystem { SI, IMPERIAL }

/**
 * 明暗模式（OPT-A-03）：**与皮肤正交的独立轴**（SKIN-DESIGN-SPEC §1）。
 * `SYSTEM` = 跟随系统；`LIGHT` / `DARK` = 应用内显式选择，与系统设置解耦。
 * 持久化键 `settings.themeMode`；切换明暗不得重置皮肤，反之亦然。
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 地图底图样式（UX-19 / OPT-V11-19；D8 三选）。
 * URL 全部收敛在 [TideSpotUrls]（不得回退点 3）。
 *
 * 取值规则（2026-11-13 产品决策，OPT-A-03 与 D8 的冲突收口）：
 * - **用户从未改过**（`mapStyleExplicit = false`）：跟随明暗轴——深色 [DARK_MATTER]、浅色 [VOYAGER]；
 * - **用户改过**（`mapStyleExplicit = true`）：**用户显式选择优先**，明暗切换不再覆盖。
 * 解析唯一入口 [SettingsLogic.resolveMapStyle]（纯函数，JVM 可测）。
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
    /** CHG-25（P0-1）：Copernicus 凭据是否已配置（运行期由 :app 从凭据存储读取，不落本状态持久化）。 */
    val copernicusConfigured: Boolean = false,
    /** UX-19（OPT-V11-19）：地图底图样式；是否生效见 [mapStyleExplicit]。 */
    val mapStyle: MapStyle = MapStyle.VOYAGER,
    /**
     * 用户是否**显式改过**地图样式（持久化键 `settings.mapStyleExplicit`）。
     * `false` = 未改过 → 底图跟随明暗轴自动切（深色 Dark Matter / 浅色 Voyager）；
     * `true` = 改过 → [mapStyle] 说了算，明暗切换不覆盖（D8 用户选择优先）。
     */
    val mapStyleExplicit: Boolean = false,
    /** SKIN-3：当前皮肤 id（默认 A；冷启动读回时未知 id 归一为默认，不崩）。 */
    val skinId: String = TideSpotSkins.default.id,
    /** OPT-A-03：应用内明暗三选（独立于皮肤与水体）。 */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

/** 设置键名唯一清单（:app 批次 3 SharedPreferences 实现按此对齐）。 */
object SettingsKeys {
    const val UNIT_SYSTEM = "settings.unitSystem"
    const val DETAIL_DAYS = "settings.detailDays"
    const val PREFETCH_ENABLED = "settings.prefetchEnabled"
    const val REVERSE_GEO_ENABLED = "settings.reverseGeoEnabled"
    const val MAP_STYLE = "settings.mapStyle"

    /** 是否显式改过地图样式（决定「跟随明暗自动切」还是「用户选择优先」）。 */
    const val MAP_STYLE_EXPLICIT = "settings.mapStyleExplicit"

    /** 三轴正交（SKIN-DESIGN-SPEC §1）：皮肤与明暗各自独立键，互不覆盖。 */
    const val SKIN_ID = "settings.skinId"
    const val THEME_MODE = "settings.themeMode"
}
