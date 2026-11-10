package com.tidespot.feature.favorites.favorites

import com.tidespot.core.data.repo.DataSource
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.model.WaterBodyType
import com.tidespot.core.ui.format.Format
import com.tidespot.core.ui.strings.TideSpotStrings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * OPT-V11-15（P2/CHG-37）收藏信息卡片纯函数层（JVM 单测锁）。
 *
 * 卡片字段（规格原文）：坐标（6 位小数）/ 精度来源说明 / 海拔 / 水体类型徽标 / 最近一次数据拉取时间。
 * **不新增任何网络请求，全部读库**（`spot` 表 + `fetch_log` 最近成功时刻，后者由 ViewModel 传入）；
 * 离线时字段完整（缺值 → `—`，禁 0 填充，§5.0）。
 *
 * 交互口径（客户裁定 D16）：卡片由卡片内「信息」图标展开，**不占用长按**（长按 = 多选）。
 */
object FavoritesInfoCardLogic {

    private val MM_DD_HH_MM = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT)

    /** 信息卡片渲染字段（全部为成品文案，UI 直接渲染）。 */
    data class InfoCard(
        val coordinateLine: String,
        val accuracyNote: String,
        val elevationLine: String,
        val waterBodyBadge: String,
        val lastFetchedLine: String,
        val groupLine: String,
    )

    /**
     * @param fetchedAtMillis 逐源最近成功拉取时刻（[DataSource] → epoch 毫秒；空 = 无记录）
     * @param zone 渲染时区（选点当地，`SpotZones.zoneForSpot`）
     */
    fun build(
        spot: FavoriteSpot,
        fetchedAtMillis: Map<DataSource, Long> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): InfoCard {
        val latest = fetchedAtMillis.values.filter { it > 0 }.maxOrNull()
        return InfoCard(
            coordinateLine = "${TideSpotStrings.INFO_CARD_COORDINATE_PREFIX} " +
                "${Format.coordinate(spot.lat)}, ${Format.coordinate(spot.lon)}",
            accuracyNote = TideSpotStrings.INFO_CARD_ACCURACY_NOTE,
            elevationLine = "${TideSpotStrings.INFO_CARD_ELEVATION_PREFIX} " +
                if (spot.elevation == null) {
                    Format.MISSING
                } else {
                    Format.valueUnit(spot.elevation, "m", 1)
                },
            waterBodyBadge = waterBodyLabel(spot.waterBody),
            lastFetchedLine = "${TideSpotStrings.INFO_CARD_LAST_FETCHED_PREFIX} " +
                if (latest == null) {
                    Format.MISSING    // 无记录 → 禁编造（CONFLICT-03）
                } else {
                    MM_DD_HH_MM.format(Instant.ofEpochMilli(latest).atZone(zone))
                },
            groupLine = "${TideSpotStrings.INFO_CARD_GROUP_PREFIX} " +
                (spot.groupName?.trim()?.ifEmpty { null } ?: FavoritesGroupLogic.UNGROUPED),
        )
    }

    /** 水体类型徽标文案（设计包六枚置信徽标之外，属水体类型标注，不改 `ConfidenceBadge` 冻结取值）。 */
    fun waterBodyLabel(type: WaterBodyType): String = when (type) {
        WaterBodyType.OCEAN -> TideSpotStrings.WATER_BODY_OCEAN
        WaterBodyType.NEARSHORE -> TideSpotStrings.WATER_BODY_NEARSHORE
        WaterBodyType.INLAND_WATER -> TideSpotStrings.WATER_BODY_INLAND
        WaterBodyType.LAND -> TideSpotStrings.WATER_BODY_LAND
        WaterBodyType.UNKNOWN -> TideSpotStrings.WATER_BODY_UNKNOWN
    }
}