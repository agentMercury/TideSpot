package com.tidespot.app

import com.tidespot.feature.settings.SettingsKeys
import com.tidespot.feature.settings.SettingsLogic
import com.tidespot.feature.settings.SettingsState
import com.tidespot.feature.settings.SettingsStore
import com.tidespot.feature.settings.UnitSystem
import java.util.Locale

/**
 * 设置键值 ↔ [SettingsState] 映射（M6 批次 3）：纯函数（JVM 锁测试 [AppSettingsTest]），
 * [com.tidespot.app.SharedPreferencesSettingsStore] 只做键值读写，不承载语义。
 *
 * OPT-M5-07 收藏对话框名称兜底也收口在本对象（纯函数，禁 0.0/0.0 占位由
 * FavoritesSaveRules.canCreate 防御，此处只负责显示名格式化）。
 */
object AppSettings {

    // ────────────── 单位制序列化（显示层语义；内部恒 SI，冻结点 3） ──────────────

    const val UNIT_SI = "SI"
    const val UNIT_IMPERIAL = "IMPERIAL"

    fun unitName(unit: UnitSystem): String =
        if (unit == UnitSystem.SI) UNIT_SI else UNIT_IMPERIAL

    /** 非法/缺失值一律落 SI（保守默认）。 */
    fun parseUnit(raw: String?): UnitSystem =
        if (raw == UNIT_IMPERIAL) UnitSystem.IMPERIAL else UnitSystem.SI

    // ────────────── 读取（store → state） ──────────────

    fun read(store: SettingsStore): SettingsState = SettingsState(
        unitSystem = parseUnit(store.readString(SettingsKeys.UNIT_SYSTEM)),
        detailDays = SettingsLogic.coerceDays(
            store.readString(SettingsKeys.DETAIL_DAYS)?.toIntOrNull() ?: 7,
        ),
        prefetchEnabled = store.readBoolean(SettingsKeys.PREFETCH_ENABLED, default = true),
        reverseGeoEnabled = store.readBoolean(SettingsKeys.REVERSE_GEO_ENABLED, default = true),
        apiKey = store.readString(SettingsKeys.API_KEY)?.ifEmpty { null },
    )

    // ────────────── 写回（state 片段 → store） ──────────────

    fun writeUnit(store: SettingsStore, unit: UnitSystem) {
        store.writeString(SettingsKeys.UNIT_SYSTEM, unitName(unit))
    }

    fun writeDetailDays(store: SettingsStore, days: Int) {
        store.writeString(SettingsKeys.DETAIL_DAYS, SettingsLogic.coerceDays(days).toString())
    }

    fun writePrefetch(store: SettingsStore, enabled: Boolean) {
        store.writeBoolean(SettingsKeys.PREFETCH_ENABLED, enabled)
    }

    fun writeReverseGeo(store: SettingsStore, enabled: Boolean) {
        store.writeBoolean(SettingsKeys.REVERSE_GEO_ENABLED, enabled)
    }

    fun writeApiKey(store: SettingsStore, raw: String?) {
        store.writeString(SettingsKeys.API_KEY, raw?.trim()?.ifEmpty { null })
    }

    // ────────────── OPT-M5-07 收藏对话框名称兜底 ──────────────

    /**
     * 逆地理名称缺失/空白时的显示名兜底（固定 Locale.US 保证小数点不随设备区域漂移）。
     * 坐标合法性（0.0/0.0 占位拒绝）由 FavoritesSaveRules.canCreate 单源防御。
     */
    fun fallbackSpotName(lat: Double, lon: Double): String =
        String.format(Locale.US, "点位 %.5f, %.5f", lat, lon)
}
