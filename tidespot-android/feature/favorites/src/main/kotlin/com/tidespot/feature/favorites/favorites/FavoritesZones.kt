package com.tidespot.feature.favorites.favorites

import com.tidespot.core.domain.model.BestWindow
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.strings.ZhStrings
import com.tidespot.core.ui.zone.SpotZones
import java.time.ZoneId

/**
 * OPT-M5-07 UI 侧收口（CHG-16⑰）：新建收藏坐标防御纯函数。
 * 新建路径必须携带坐标（:app 准星主路径接线在批次 3）；lat/lon 为 null 或 (0.0, 0.0) 拒绝落库。
 * 编辑路径不受影响（patch 语义保留既有坐标）。
 */
object FavoritesSaveRules {

    /** 拒绝落库的提示（UI 显示；TalkBack 同读；OPT-V11-13 走文案层）。 */
    fun rejectMessage(strings: TideSpotStrings = ZhStrings): String = strings.errNoCoordinate

    /** 新建路径坐标防御：非 null 且不是 (0.0, 0.0) 才允许（单维为 0 是合法经/纬，放行）。 */
    fun canCreate(lat: Double?, lon: Double?): Boolean =
        lat != null && lon != null && !(lat == 0.0 && lon == 0.0)
}

/**
 * OPT-M5-03（CHG-16⑯）：跨点 Top10 时区回贴纯函数。
 * rankAcrossSpots 输出只携带点名称，同名多点时按「输入序 + 各点窗口数」精确回贴归属点
 * （稳定排序保证同名各点窗口在输出中保持输入相对顺序，队列计数法无歧义）。
 */
object FavoritesZones {

    /**
     * 跨点 Top10 排名条目 → 携带各点时区的渲染条目。
     * [spotsInInputOrder] = rankAcrossSpots 的输入（点名 to 窗口列表），顺序必须与之一致。
     */
    fun attachZones(
        ranked: List<Pair<String, BestWindow>>,
        spotsInInputOrder: List<Pair<FavoriteSpot, List<BestWindow>>>,
    ): List<CrossSpotTop10Entry> {
        // 每个点名的队列：元素 = (spot, 该点剩余未消费窗口数)；稳定排序保证同名先到先得
        val queues = HashMap<String, ArrayDeque<Pair<FavoriteSpot, Int>>>()
        for ((spot, windows) in spotsInInputOrder) {
            queues.getOrPut(spot.name) { ArrayDeque() }.addLast(spot to windows.size)
        }
        return ranked.map { (name, window) ->
            val queue = queues[name]
            if (queue == null || queue.isEmpty()) {
                // 防御：输入序与 ranked 不一致时不虚构时区（落系统默认口径）
                CrossSpotTop10Entry(name, null, window)
            } else {
                val (spot, remaining) = queue.first()
                val entry = CrossSpotTop10Entry(name, spot, window)
                if (remaining <= 1) queue.removeFirst() else queue[0] = spot to (remaining - 1)
                entry
            }
        }
    }
}
