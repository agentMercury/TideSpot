package com.tidespot.feature.settings

/**
 * 设置键值存储接口（CHG-16⑱）：仅声明键值读写；:app 批次 3 以 SharedPreferences 实现。
 * 本模块不引 Android 框架类（保持 JVM 可测）。
 */
interface SettingsStore {

    fun readString(key: String): String?

    fun writeString(key: String, value: String?)

    fun readBoolean(key: String, default: Boolean): Boolean

    fun writeBoolean(key: String, value: Boolean)
}
