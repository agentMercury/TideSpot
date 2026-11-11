package com.tidespot.feature.favorites.favorites

import com.tidespot.core.domain.model.FavoriteSpot

/**
 * 收藏列表排序纯函数层（F-2.4；JVM 单测锁：三序 + 稳定性 + lastViewedAt=0 排最后）。
 *
 * 红线自我约束：
 * - 排序一律 sortedWith（稳定），并列项保持原序（N-9：比较/排序用 epoch）；
 * - 无分数/无查看记录的点排最后（缺值禁 0 填充，§5.0）——不与真实 0 值并列。
 */
object FavoritesSortLogic {

    /**
     * @param spots 收藏点列表（保持原序输入）
     * @param order 四选一排序方式（OPT-V11-14 新增「综合推荐」）
     * @param scoreBySpotId 各点最高窗口 peakScore（加载后填充；缺 key = 未加载，排最后）
     * @param rankBySpotId 各点最高窗口 rank（OPT-V11-14「综合推荐」排序依据；缺 key = 未加载，排最后）
     */
    fun sort(
        spots: List<FavoriteSpot>,
        order: SortOrder,
        scoreBySpotId: Map<Long, Int> = emptyMap(),
        rankBySpotId: Map<Long, Double> = emptyMap(),
    ): List<FavoriteSpot> = when (order) {
        SortOrder.NAME -> spots.sortedWith(compareBy { it.name })
        SortOrder.LAST_VIEWED -> spots.sortedWith(
            Comparator { a, b ->
                val av = a.lastViewedAtEpochSec
                val bv = b.lastViewedAtEpochSec
                when {
                    av == 0L && bv == 0L -> 0
                    av == 0L -> 1    // 0 = 从未查看 → 排最后
                    bv == 0L -> -1
                    else -> bv.compareTo(av)  // 最近查看 = 降序
                }
            },
        )
        SortOrder.SCORE -> spots.sortedWith(
            Comparator { a, b ->
                val aScore = scoreBySpotId[a.id]
                val bScore = scoreBySpotId[b.id]
                when {
                    aScore == null && bScore == null -> 0
                    aScore == null -> 1    // 未加载/无窗口 → 排最后
                    bScore == null -> -1
                    else -> bScore.compareTo(aScore)  // 分数降序
                }
            },
        )
        // OPT-V11-14（P2/CHG-37）：综合推荐 = BestWindow.rank 降序；同 rank 按 name 字典序（稳定、无旁路）
        SortOrder.RECOMMENDED -> spots.sortedWith(
            Comparator { a, b ->
                val aRank = rankBySpotId[a.id]
                val bRank = rankBySpotId[b.id]
                when {
                    aRank == null && bRank == null -> a.name.compareTo(b.name)
                    aRank == null -> 1      // 未加载/无窗口 → 排最后（缺值禁 0 填充）
                    bRank == null -> -1
                    aRank != bRank -> bRank.compareTo(aRank)  // rank 降序
                    else -> a.name.compareTo(b.name)          // 同 rank → 名称升序
                }
            },
        )
    }

    /** 场景预设可选项（F-2.1；**写入 DB 的协议值**，与 HourlyScoreStore.resolvePreset 中文标签解析兼容，禁翻译）。 */
    val PRESET_OPTIONS: List<String> = listOf("淡水路亚", "海水岸边", "淡水台钓") // i18n-exempt: spot.preset 数据协议值

    /**
     * 标签输入解析：按 `,` `，` `、` 空格切分 → trim → 去空 → 去重（保持首次出现序）。
     * 空白输入 → 空列表（非空 List 契约，CHG-08 ④）。
     */
    fun parseTags(raw: String): List<String> =
        raw.split(',', '，', '、', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}