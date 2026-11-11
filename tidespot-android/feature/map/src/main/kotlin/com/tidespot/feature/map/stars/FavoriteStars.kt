package com.tidespot.feature.map.stars

import java.util.Locale

/**
 * OPT-V12-01（CHG-57）：地图收藏星标条目（纯数据；`:feature:map` 不持有文案/不触网络）。
 *
 * [summary] = 最近最佳窗口摘要（由 :app 用 `FavoritesSummaryFormatter` 生成，读库现成字段；
 * null = 未加载 → 气泡卡只显示坐标，禁编造）。
 */
data class FavoriteStar(
    val id: Long,
    val name: String,
    val lat: Double,
    val lon: Double,
    val summary: String? = null,
)

/**
 * OPT-V12-01：星标图层的 GeoJSON 构造与命中解析（**纯函数 + JVM 锁测试**，组装层盲区教训）。
 *
 * 数据全本地（Room `spot` 表 → :app 注入），端侧零额外网络请求；不做聚合（≤100 条）。
 */
object FavoriteStarLayer {

    const val SOURCE_ID = "tidespot-favorite-stars"
    const val LAYER_ID = "tidespot-favorite-stars-layer"
    const val ICON_ID = "tidespot-star"

    /** 收藏点 → GeoJSON FeatureCollection（`properties.spotId` 供点击命中解析）。 */
    fun geoJson(stars: List<FavoriteStar>): String = buildString {
        append("{\"type\":\"FeatureCollection\",\"features\":[")
        stars.forEachIndexed { index, star ->
            if (index > 0) append(',')
            append("{\"type\":\"Feature\",\"id\":").append(star.id)
            append(",\"properties\":{\"spotId\":").append(star.id)
            append(",\"name\":\"").append(escape(star.name)).append("\"}")
            append(",\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
            append(coordinate(star.lon)).append(',').append(coordinate(star.lat))
            append("]}}")
        }
        append("]}")
    }

    /**
     * 命中 → spotId 解析（MapLibre 的 `getNumberProperty` 可能返回 Double/Float/Long → 统一 Long）。
     * 非法/缺失 → null（不抛异常，N-4）。
     */
    fun starIdOf(raw: Number?): Long? = raw?.toLong()

    /** 坐标序列化：固定 6 位小数（与收藏详情卡坐标口径一致，无科学计数法）。 */
    fun coordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

    /** JSON 字符串转义（名称可能含引号/反斜杠/换行）。 */
    fun escape(value: String): String = buildString {
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}