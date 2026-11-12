package com.tidespot.core.data.local

/**
 * 格点缓存键（§5.8 逆地理 30 d 缓存 / §6.9 水体判定 30 d 缓存共用口径）。
 */
internal object CacheKeys {

    /** 经纬度四舍五入到 0.005°（×200 取整再除回），形如 `"29.55,118.95"`。 */
    fun spotKey(lat: Double, lon: Double): String {
        val rLat = Math.round(lat * 200) / 200.0
        val rLon = Math.round(lon * 200) / 200.0
        return "$rLat,$rLon"
    }
}
