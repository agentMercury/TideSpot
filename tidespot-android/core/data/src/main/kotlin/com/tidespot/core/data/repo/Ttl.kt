package com.tidespot.core.data.repo

/**
 * 数据源标识 + TTL（SRS §7.3 数据模型与 TTL 表）。
 *
 * | 数据集          | TTL   |
 * |-----------------|-------|
 * | 天气（小时/日）  | 3 h   |
 * | 海洋（浪/流/潮/SST）| 6 h |
 * | 水质（卫星/模型）| 24 h（M0 结论：走本地推导，无独立网络源，暂不建枚举项） |
 */
enum class DataSource(val wire: String, val ttlMillis: Long) {
    WEATHER("weather", 3L * 60 * 60 * 1000),
    MARINE("marine", 6L * 60 * 60 * 1000),
}

/** TTL 判定（纯函数，可确定性单测）。 */
object Ttl {

    /** §6.7 第 4 行：缓存时刻距今超过 48h → 视为不可用（不再据此评分）。 */
    const val UNUSABLE_AFTER_MILLIS: Long = 48L * 60 * 60 * 1000

    /**
     * 缓存是否仍新鲜：`lastFetchedAtMillis` 以最近一次成功（HTTP 200）拉取时刻为基准。
     * 无拉取记录（=0）视为不新鲜。
     */
    fun isFresh(source: DataSource, lastFetchedAtMillis: Long, nowMillis: Long): Boolean =
        lastFetchedAtMillis > 0 && nowMillis - lastFetchedAtMillis < source.ttlMillis

    /** 是否已过期（>0 表示过期毫秒数；用于「数据时间 X，已过期」文案，§6.7）。 */
    fun staleForMillis(source: DataSource, lastFetchedAtMillis: Long, nowMillis: Long): Long? =
        if (lastFetchedAtMillis <= 0) null
        else (nowMillis - lastFetchedAtMillis - source.ttlMillis).takeIf { it > 0 }

    /**
     * §6.7 第 4 行（CHG-13/N15）：缓存年龄 ≥48h → **不可用**（不再据此评分）。
     * 返回缓存年龄毫秒数；无拉取记录（=0）或年龄 <48h → null（仍可显示）。
     */
    fun unusableAgeMillis(lastFetchedAtMillis: Long, nowMillis: Long): Long? =
        if (lastFetchedAtMillis <= 0) null
        else (nowMillis - lastFetchedAtMillis).takeIf { it >= UNUSABLE_AFTER_MILLIS }
}
