package com.tidespot.feature.favorites

import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.model.WaterBodyType
import com.tidespot.core.data.repo.DataSource
import com.tidespot.feature.favorites.favorites.FavoritesBatchLogic
import com.tidespot.feature.favorites.favorites.FavoritesGroupLogic
import com.tidespot.feature.favorites.favorites.FavoritesInfoCardLogic
import com.tidespot.feature.favorites.favorites.FavoritesSortLogic
import com.tidespot.feature.favorites.favorites.SortOrder
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v1.1 P2 纯函数锁（CHG-37）：
 * - OPT-V11-14 综合推荐排序（rank 降序 / 同 rank 名称序 / 未加载排最后）；
 * - OPT-V11-12 批量操作（选中切换 / 全选 / 反选 / 标签追加与替换 / 删除确认文案）；
 * - OPT-V11-16 分组（默认组「未分组」置末 / 组内保持序 / 折叠切换 / 空白归一）；
 * - OPT-V11-15 信息卡片（6 位坐标 / 缺值 `—` 禁 0 填充 / 最近拉取不编造 / 水体徽标）。
 */
class FavoritesP2LogicTest {

    private fun spot(
        id: Long,
        name: String,
        groupName: String? = null,
        tags: List<String> = emptyList(),
    ) = FavoriteSpot(
        id = id,
        name = name,
        lat = 29.55,
        lon = 118.95,
        tags = tags,
        groupName = groupName,
    )

    // ──────────────────────────── OPT-V11-14 综合推荐排序 ────────────────────────────

    @Test
    fun `综合推荐_按rank降序`() {
        val a = spot(1, "A")
        val b = spot(2, "B")
        val c = spot(3, "C")
        val rank = mapOf(1L to 92.0, 2L to 71.0, 3L to 88.0)
        val out = FavoritesSortLogic.sort(listOf(a, b, c), SortOrder.RECOMMENDED, rankBySpotId = rank)
        assertEquals(listOf(1L, 3L, 2L), out.map { it.id })
    }

    @Test
    fun `综合推荐_同rank按名称升序`() {
        val a = spot(1, "舟山外海")
        val b = spot(2, "大阪湾")
        val rank = mapOf(1L to 88.0, 2L to 88.0)
        val out = FavoritesSortLogic.sort(listOf(a, b), SortOrder.RECOMMENDED, rankBySpotId = rank)
        assertEquals(listOf(2L, 1L), out.map { it.id })   // 大阪湾 < 舟山外海（Unicode 序）
    }

    @Test
    fun `综合推荐_未加载rank排最后且禁0填充`() {
        val loaded = spot(1, "A")
        val missing = spot(2, "B")
        val out = FavoritesSortLogic.sort(
            listOf(missing, loaded),
            SortOrder.RECOMMENDED,
            rankBySpotId = mapOf(1L to 0.0),
        )
        assertEquals(listOf(1L, 2L), out.map { it.id })   // 真实 0.0 仍优于「缺 rank」
    }

    @Test
    fun `既有三序在新增字段下行为不变`() {
        val a = spot(1, "A", tags = listOf("夜钓"))
        val b = spot(2, "B")
        val byName = FavoritesSortLogic.sort(listOf(b, a), SortOrder.NAME)
        assertEquals(listOf(1L, 2L), byName.map { it.id })
    }

    // ──────────────────────────── OPT-V11-12 批量操作 ────────────────────────────

    @Test
    fun `多选切换_加入与移除`() {
        var sel = FavoritesBatchLogic.toggle(emptySet(), 7L)
        assertEquals(setOf(7L), sel)
        sel = FavoritesBatchLogic.toggle(sel, 7L)
        assertTrue(sel.isEmpty(), "再次切换应移除，空集即退出多选")
    }

    @Test
    fun `全选与反选_以可见域为界`() {
        val all = FavoritesBatchLogic.selectAll(listOf(1L, 2L, 3L))
        assertEquals(setOf(1L, 2L, 3L), all)
        assertTrue(FavoritesBatchLogic.isAllSelected(all, listOf(1L, 2L, 3L)))
        assertFalse(FavoritesBatchLogic.isAllSelected(emptySet(), emptyList()), "空域不得判为全选")

        val inverted = FavoritesBatchLogic.invertSelection(setOf(1L, 2L, 99L), listOf(1L, 2L, 3L))
        assertEquals(setOf(3L, 99L), inverted)   // 域内未选 → 选；域外选择保留
    }

    @Test
    fun `标签合并_追加保序去重`() {
        val merged = FavoritesBatchLogic.mergeTags(listOf("夜钓", "礁区"), listOf("礁区", "路亚"), replace = false)
        assertEquals(listOf("夜钓", "礁区", "路亚"), merged)
    }

    @Test
    fun `标签合并_替换覆盖且空输入即清空`() {
        val replaced = FavoritesBatchLogic.mergeTags(listOf("夜钓", "礁区"), listOf("路亚"), replace = true)
        assertEquals(listOf("路亚"), replaced)
        val cleared = FavoritesBatchLogic.mergeTags(listOf("夜钓"), emptyList(), replace = true)
        assertTrue(cleared.isEmpty(), "替换 + 空输入 = 显式清空")
    }

    @Test
    fun `标签合并_空输入追加不变`() {
        assertEquals(listOf("夜钓"), FavoritesBatchLogic.mergeTags(listOf("夜钓"), emptyList(), replace = false))
    }

    @Test
    fun `删除确认文案_明示条数`() {
        assertTrue(FavoritesBatchLogic.deleteConfirmMessage(5).contains("5"))
    }

    @Test
    fun `选中id按可见序输出`() {
        val visible = listOf(spot(1, "A"), spot(2, "B"), spot(3, "C"))
        val ids = FavoritesBatchLogic.selectedIds(visible, setOf(3L, 1L))
        assertEquals(listOf(1L, 3L), ids)
    }

    // ──────────────────────────── OPT-V11-16 分组 ────────────────────────────

    @Test
    fun `分组_自定义按名称升序且未分组置末`() {
        val spots = listOf(
            spot(1, "A", groupName = "计划去"),
            spot(2, "B"),
            spot(3, "C", groupName = "常去"),
        )
        val groups = FavoritesGroupLogic.groups(spots)
        assertEquals(listOf("常去", "计划去", FavoritesGroupLogic.UNGROUPED_KEY), groups.map { it.name })
        assertTrue(groups.last().isDefault)
    }

    @Test
    fun `分组_组内保持输入序`() {
        val spots = listOf(spot(3, "C", "常去"), spot(1, "A", "常去"))
        val groups = FavoritesGroupLogic.groups(spots)
        assertEquals(listOf(3L, 1L), groups.single().spots.map { it.id })
    }

    @Test
    fun `分组_空白串归一为未分组`() {
        assertEquals(FavoritesGroupLogic.UNGROUPED_KEY, FavoritesGroupLogic.groupNameOf(spot(1, "A", groupName = "  ")))
        assertNull(FavoritesGroupLogic.normalizeGroupName("   "))
        assertEquals("常去", FavoritesGroupLogic.normalizeGroupName(" 常去 "))
    }

    @Test
    fun `分组折叠切换与判定`() {
        var collapsed = FavoritesGroupLogic.toggleCollapsed("常去", emptySet())
        assertTrue(FavoritesGroupLogic.isCollapsed("常去", collapsed))
        collapsed = FavoritesGroupLogic.toggleCollapsed("常去", collapsed)
        assertFalse(FavoritesGroupLogic.isCollapsed("常去", collapsed))
    }

    @Test
    fun `空列表不产出空组`() {
        assertTrue(FavoritesGroupLogic.groups(emptyList()).isEmpty())
    }

    // ──────────────────────────── OPT-V11-15 信息卡片 ────────────────────────────

    @Test
    fun `信息卡片_坐标6位且缺值占位符`() {
        val card = FavoritesInfoCardLogic.build(
            spot = spot(1, "A"),
            fetchedAtMillis = emptyMap(),
            zone = ZoneOffset.ofHours(8),
        )
        assertTrue(card.coordinateLine.contains("29.550000"))
        assertTrue(card.coordinateLine.contains("118.950000"))
        assertTrue(card.elevationLine.endsWith("—"), "null 海拔禁 0 填充")
        assertTrue(card.lastFetchedLine.endsWith("—"), "无拉取记录禁编造时间")
        assertTrue(card.groupLine.endsWith("未分组"))
        assertTrue(card.accuracyNote.isNotEmpty())
    }

    @Test
    fun `信息卡片_字段与spot一致`() {
        val rich = spot(1, "A").copy(
            elevation = 12.34,
            waterBody = WaterBodyType.NEARSHORE,
            groupName = "常去",
        )
        val card = FavoritesInfoCardLogic.build(
            spot = rich,
            fetchedAtMillis = mapOf(DataSource.WEATHER to 1_700_000_000_000L),
            zone = ZoneOffset.ofHours(8),
        )
        assertEquals("海拔 12.3 m", card.elevationLine)
        assertEquals("近岸", card.waterBodyBadge)
        assertEquals("分组 常去", card.groupLine)
        assertFalse(card.lastFetchedLine.endsWith("—"), "有拉取记录须显示时刻")
    }

    @Test
    fun `水体徽标_五类全覆盖无空串`() {
        WaterBodyType.entries.forEach { type ->
            assertTrue(FavoritesInfoCardLogic.waterBodyLabel(type).isNotBlank())
        }
    }
}