package com.tidespot.core.domain

import com.tidespot.core.domain.algorithm.FactorId
import com.tidespot.core.domain.algorithm.SpeciesPresets
import com.tidespot.core.domain.model.WaterBodyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OPT-19（M6 CHG-16）锁测试：8 物种预设库加载入口。
 * 红线：场景预设解析唯一入口仍是 `HourlyScoreStore.resolvePreset`，物种预设
 * **零改变**既有场景预设行为（Presets 三场景对象不变，本测试同时守护）。
 */
class SpeciesPresetTest {

    @Test
    fun `资源加载_恰好8物种_字段完整`() {
        val all = SpeciesPresets.load()
        assertEquals(8, all.size, "8 物种（鲫鱼/鲤鱼/草鱼/翘嘴鲌/鳜鱼/大口黑鲈/黑鲷/海鲈）")
        assertTrue(all.all { it.speciesId.isNotBlank() }, "speciesId 非空")
        assertTrue(all.all { it.name.isNotBlank() }, "名称非空")
        assertTrue(all.all { it.waterBodies.isNotEmpty() }, "适用水体非空")
        assertTrue(all.all { it.weightOverrides.isNotEmpty() }, "权重覆盖非空")
        assertEquals(
            listOf("crucian_carp", "common_carp", "grass_carp", "topmouth_culter", "mandarin_fish", "largemouth_bass", "black_porgy", "sea_bass"),
            all.map { it.speciesId },
        )
    }

    @Test
    fun `权重覆盖只含合法FactorId且权重在0到1内`() {
        SpeciesPresets.load().forEach { sp ->
            sp.weightOverrides.forEach { (f, w) ->
                assertTrue(f in FactorId.entries, "合法 FactorId：${sp.speciesId}/$f")
                assertTrue(w in 0.0..1.0, "权重 ∈ [0,1]：${sp.speciesId}/$f=$w")
            }
        }
    }

    @Test
    fun `黑鲷海鲷组含TIDE覆盖且淡水种无TIDE`() {
        val byId = { id: String -> SpeciesPresets.load().first { it.speciesId == id } }
        assertTrue(FactorId.TIDE in byId("black_porgy").weightOverrides, "黑鲷海水种覆盖 tide 权重")
        assertTrue(byId("black_porgy").waterBodies.contains(WaterBodyType.NEARSHORE))
        assertTrue(byId("crucian_carp").waterBodies == setOf(WaterBodyType.INLAND_WATER), "鲫鱼纯淡水")
        assertTrue(FactorId.TIDE !in byId("crucian_carp").weightOverrides, "淡水种无 tide 覆盖")
    }

    @Test
    fun `byId查找与未命中null`() {
        assertEquals("鲫鱼", SpeciesPresets.byId("crucian_carp")?.name)
        assertEquals(null, SpeciesPresets.byId("nonexistent"))
    }

    @Test
    fun `脏JSON与缺species返回空列表_不抛异常_N4`() {
        assertTrue(SpeciesPresets.parse("not json at all").isEmpty(), "脏串 → 空列表")
        assertTrue(SpeciesPresets.parse("""{"species":[{"name":"缺id"}]}""").isEmpty())
        assertTrue(SpeciesPresets.parse("""{"species":[]}""").isEmpty())
        // 部分行脏 → 跳过脏行保留好行
        val partial = SpeciesPresets.parse(
            """{"species":[{"speciesId":"a","name":"A","waterBodies":["INLAND_WATER"],"weightOverrides":{"OXYGEN":0.2}},{"name":"脏行缺id"}]}""",
        )
        assertEquals(1, partial.size)
        assertEquals("a", partial.first().speciesId)
    }

    @Test
    fun `既有场景预设行为不受物种库影响`() {
        // 守护：三个内置场景对象与 byId 解析零漂移（OPT-19 纯增量）
        assertEquals("fresh_lure", com.tidespot.core.domain.algorithm.Presets.byId("fresh_lure")?.id)
        assertEquals("sea_shore", com.tidespot.core.domain.algorithm.Presets.byId("sea_shore")?.id)
        assertEquals("fresh_bait", com.tidespot.core.domain.algorithm.Presets.byId("fresh_bait")?.id)
        assertEquals(null, com.tidespot.core.domain.algorithm.Presets.byId("crucian_carp"), "物种 id 不得混入场景预设解析")
    }
}