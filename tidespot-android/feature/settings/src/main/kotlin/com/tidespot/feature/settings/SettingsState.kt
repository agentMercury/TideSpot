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
 * 地图**样式**（UX-19 / OPT-V11-19；D8 三选）——「长什么样」。
 * 与 [MapProvider]（谁提供瓦片）正交：2 个提供方 × 3 个样式 = 6 种组合。
 *
 * 取值规则（2026-11-13 产品决策，OPT-A-03 与 D8 的冲突收口）：
 * - **用户从未改过**（`mapStyleExplicit = false`）：跟随明暗轴——深色 [DARK_MATTER]、浅色 [VOYAGER]；
 * - **用户改过**（`mapStyleExplicit = true`）：**用户显式选择优先**，明暗切换不再覆盖。
 * 解析唯一入口 [SettingsLogic.resolveMapStyle]（纯函数，JVM 可测）。
 *
 * **枚举名是持久化值**（`settings.mapStyle` 存 `name`），禁止改名。
 */
enum class MapStyle(@StringRes val labelRes: Int) {
    VOYAGER(R.string.ts_settings_style_color),
    POSITRON(R.string.ts_settings_style_plain),
    DARK_MATTER(R.string.ts_settings_style_dark),
}

/**
 * 地图**提供方**（2026-11-13 新增，OPT-A-10）——「瓦片从哪来」。
 *
 * 为什么要可切：CARTO（Fastly）在部分网络下 443 全超时 → 底图空白；OpenFreeMap 同数据同坐标系但可达。
 * 默认 [OPENFREEMAP]（实测可达），保留 [CARTO] 供海外网络或 CARTO 恢复后使用。
 *
 * **新增一个提供方** = 这里加一项 + [styleUrl] 补三条映射 + `TideSpotUrls` 补三个常量 + 两条资源名。
 * 业务屏零改动（URL 经 [SettingsLogic.resolveBasemapUrl] 唯一出口）。
 */
enum class MapProvider(val displayName: String) {
    // i18n-exempt: 提供方品牌名（产品名跨语言不变，与皮肤 name 同类）
    OPENFREEMAP("OpenFreeMap"),
    CARTO("CARTO");

    /** 提供方 × 样式 → 样式 URL（字面量只在 `TideSpotUrls`，本函数只做映射）。 */
    fun styleUrl(style: MapStyle): String = when (this) {
        OPENFREEMAP -> when (style) {
            MapStyle.VOYAGER -> TideSpotUrls.OPENFREEMAP_COLOR_STYLE_URL
            MapStyle.POSITRON -> TideSpotUrls.OPENFREEMAP_PLAIN_STYLE_URL
            MapStyle.DARK_MATTER -> TideSpotUrls.OPENFREEMAP_DARK_STYLE_URL
        }
        CARTO -> when (style) {
            MapStyle.VOYAGER -> TideSpotUrls.CARTO_COLOR_STYLE_URL
            MapStyle.POSITRON -> TideSpotUrls.CARTO_PLAIN_STYLE_URL
            MapStyle.DARK_MATTER -> TideSpotUrls.CARTO_DARK_STYLE_URL
        }
    }
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
    /** OPT-A-10：底图提供方（默认 OpenFreeMap——CARTO 在实测网络不可达）。 */
    val mapProvider: MapProvider = MapProvider.OPENFREEMAP,
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

    /** OPT-A-10：底图提供方（OpenFreeMap / CARTO / 未来新增）。 */
    const val MAP_PROVIDER = "settings.mapProvider"

    /** 三轴正交（SKIN-DESIGN-SPEC §1）：皮肤与明暗各自独立键，互不覆盖。 */
    const val SKIN_ID = "settings.skinId"
    const val THEME_MODE = "settings.themeMode"
}
