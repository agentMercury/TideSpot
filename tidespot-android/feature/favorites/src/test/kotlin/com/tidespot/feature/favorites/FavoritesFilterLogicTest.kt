package com.tidespot.feature.favorites

import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.feature.favorites.favorites.FavoritesFilterLogic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OPT-V11-10（P1）收藏搜索/筛选纯函数锁：名称/备注、标签、预设、交集与清空。 */
class FavoritesFilterLogicTest {

    private fun spot(
        id: Long,
        name: String,
        memo: String? = null,
        tags: List<String> = emptyList(),
        preset: String? = null,
    ) = FavoriteSpot(id = id, name = name, lat = 30.0, lon = 120.0, memo = memo, tags = tags, preset = preset)

    private val spots = listOf(
        spot(1, "千岛湖东南角", memo = "夜钓点", tags = listOf("矶钓", "夜钓"), preset = "淡水路亚"),
        spot(2, "舟山朱家尖", memo = "礁区", tags = listOf("矶钓"), preset = "海水岸边"),
        spot(3, "太湖", tags = listOf("船钓"), preset = "淡水台钓"),
    )

    @Test
    fun query_matchesNameOrMemo_caseInsensitive() {
        assertEquals(listOf(1L), FavoritesFilterLogic.apply(spots, FavoritesFilterLogic.Filter(query = "千岛")).map { it.id })
        assertEquals(listOf(1L), FavoritesFilterLogic.apply(spots, FavoritesFilterLogic.Filter(query = "夜钓")).map { it.id })
        // 大小写不敏感（英文名称）
        val en = listOf(spot(9, "QianDao Lake"))
        assertEquals(1, FavoritesFilterLogic.apply(en, FavoritesFilterLogic.Filter(query = "qiandao")).size)
    }

    @Test
    fun tagAndQueryCombineAsIntersection() {
        val f = FavoritesFilterLogic.Filter(query = "湖", tag = "矶钓")
        assertEquals(listOf(1L), FavoritesFilterLogic.apply(spots, f).map { it.id })
    }

    @Test
    fun presetFilter_matchesExactly() {
        val f = FavoritesFilterLogic.Filter(preset = "海水岸边")
        assertEquals(listOf(2L), FavoritesFilterLogic.apply(spots, f).map { it.id })
    }

    @Test
    fun emptyFilter_returnsInputUnchanged_andIsNotActive() {
        val f = FavoritesFilterLogic.Filter(query = "   ")
        assertTrue(!f.isActive)
        assertEquals(spots, FavoritesFilterLogic.apply(spots, f))
    }

    @Test
    fun availableTagsAndPresets_areDistinctSorted() {
        assertEquals(listOf("夜钓", "矶钓", "船钓"), FavoritesFilterLogic.availableTags(spots))
        assertEquals(3, FavoritesFilterLogic.availableTags(spots).size)
        assertEquals(listOf("海水岸边", "淡水台钓", "淡水路亚"), FavoritesFilterLogic.availablePresets(spots).sorted())
        assertEquals(emptyList(), FavoritesFilterLogic.availableTags(emptyList()))
    }

    @Test
    fun noMatch_returnsEmptyList() {
        assertTrue(FavoritesFilterLogic.apply(spots, FavoritesFilterLogic.Filter(query = "不存在的点")).isEmpty())
    }
}