package com.tidespot.feature.favorites

import com.tidespot.feature.favorites.favorites.TagPresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OPT-V12-07（CHG-57）锁测试：标签预设的归一/去重/不自动翻译。
 */
class TagPresetsTest {

    @Test
    fun `点选预设加入当前标签集合`() {
        val raw = TagPresets.toggleRaw("", "夜钓")
        assertEquals("夜钓", raw)
        assertEquals("夜钓、礁区", TagPresets.toggleRaw(raw, "礁区"))
    }

    @Test
    fun `再次点选同一预设即取消`() {
        val raw = TagPresets.toggleRaw("夜钓、礁区", "夜钓")
        assertEquals("礁区", raw)
    }

    @Test
    fun `同名（大小写与空格差异）不产生重复项`() {
        // 存量手输 night 与预设 night 视为同一项：不重复追加，点选即移除（保留去重语义）
        val raw = TagPresets.toggleRaw(" night ", "night")
        assertEquals("", raw)
        assertEquals(1, TagPresets.toggleRaw("Night", "夜钓").split("、").count { it.trim() == "夜钓" })
    }

    @Test
    fun `选中态忽略大小写与首尾空格`() {
        assertTrue(TagPresets.selectedInRaw(" night、船钓", "Night"))
        assertTrue(TagPresets.selectedInRaw("夜钓", " 夜钓 "))
        assertFalse(TagPresets.selectedInRaw("夜钓", "礁区"))
    }

    @Test
    fun `不自动翻译存量英文标签`() {
        // 存量 night 原样保留：追加预设后仍是 night（不改写用户数据）
        val raw = TagPresets.toggleRaw("night", "夜钓")
        assertTrue(raw.contains("night"), "存量英文标签必须原样保留")
        assertEquals("night、夜钓", raw)
    }

    @Test
    fun `预设集合为规定七项`() {
        assertEquals(listOf("夜钓", "礁区", "船钓", "库湾", "防波堤", "闸口", "入海口"), TagPresets.DEFAULT)
    }
}