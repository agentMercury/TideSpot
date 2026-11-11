package com.tidespot.feature.favorites.favorites

import com.tidespot.core.data.repo.DataSource
import com.tidespot.core.data.repo.Ttl
import com.tidespot.core.ui.format.Format
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.strings.ZhStrings
import java.time.Instant
import java.time.ZoneId

/**
 * OPT-V11-11（P1）列表级陈旧标记纯函数（判定**复用 [Ttl]**，禁另造阈值）：
 *
 * - [StaleLevel.STALE]：任一源「距上次成功拉取 > 该源 TTL」→ 整项降透明度 + `陈旧` 徽标；
 * - [StaleLevel.EXPIRED]：最近数据时间距今 ≥48h（[Ttl.unusableAgeMillis]，CHG-13/N15 语义）→ `已过期`；
 * - [StaleLevel.NONE]：无拉取记录或全部新鲜。
 *
 * 离线可判定（数据全来自 fetch_log 读库）。
 */
enum class StaleLevel { NONE, STALE, EXPIRED }

/** 列表项陈旧状态 + 副标题「数据时间」文案（null = 无任何拉取记录，不编造时间）。 */
data class StaleMark(
    val level: StaleLevel,
    val dataTimeLabel: String?,
)

object FavoritesStaleLogic {

    /** 陈旧项透明度（整项降透明；EXPIRED 复用同值并置灰摘要）。 */
    const val ALPHA_STALE: Float = 0.6f

    /** 徽标文案（level = NONE → null，禁空串）。 */
    fun badge(level: StaleLevel, strings: TideSpotStrings = ZhStrings): String? = when (level) {
        StaleLevel.STALE -> strings.staleLabel
        StaleLevel.EXPIRED -> strings.expiredLabel
        StaleLevel.NONE -> null
    }

    /**
     * @param fetchedAtMillis 逐源最近成功拉取时刻（epoch 毫秒；0 = 该源无记录）
     * @param nowMillis 当前时刻（注入，可 JVM 锁）
     */
    fun mark(
        fetchedAtMillis: Map<DataSource, Long>,
        nowMillis: Long,
        zone: ZoneId,
        strings: TideSpotStrings = ZhStrings,
    ): StaleMark {
        val latest = fetchedAtMillis.values.maxOrNull() ?: 0L
        if (latest <= 0L) return StaleMark(StaleLevel.NONE, dataTimeLabel = null)
        val local = Instant.ofEpochMilli(latest).atZone(zone)
        val label = strings.dataPrefix(Format.mmdd(local.toLocalDate()), Format.hourLocal(latest / 1000, zone))
        val expired = Ttl.unusableAgeMillis(latest, nowMillis) != null
        val stale = fetchedAtMillis.any { (src, millis) -> Ttl.staleForMillis(src, millis, nowMillis) != null }
        val level = when {
            expired -> StaleLevel.EXPIRED
            stale -> StaleLevel.STALE
            else -> StaleLevel.NONE
        }
        return StaleMark(level, label)
    }
}