package com.tidespot.core.data.repo

import com.tidespot.core.data.mock.MockFixtures
import com.tidespot.core.domain.FishScoreCalculator
import com.tidespot.core.domain.model.Confidence
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.model.HourlyWeather
import com.tidespot.core.domain.model.VetoCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M6（CHG-16①⑥）锁测试：veto_codes 列双向映射。
 * 写侧：领域 List<VetoCode> → JSON 数组串（VetoCode.name）；空列表 → '[]'（NOT NULL 列，
 * 与 vetoReason 空列表→NULL 的可空列语义**对称**）。
 * 读侧：'[]'/脏串/未知名 → 空列表（领域层永不出现 null 列表，CHG-08 ④ 口径）。
 */
class VetoCodesColumnTest {

    @Test
    fun `写侧_空列表映射为空数组串_非空映射name数组`() {
        assertEquals("[]", HourlyScoreStore.vetoCodesToColumn(emptyList()), "空列表 → '[]'（NOT NULL DEFAULT '[]' 口径）")
        val col = HourlyScoreStore.vetoCodesToColumn(listOf(VetoCode.THUNDERSTORM, VetoCode.FISHABILITY_DECAY))
        val names = Json.parseToJsonElement(col).jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("THUNDERSTORM", "FISHABILITY_DECAY"), names, "元素 = VetoCode.name")
    }

    @Test
    fun `读侧_空数组与脏串与未知名归一为空列表_往返保真`() {
        assertEquals(emptyList(), HourlyScoreStore.columnToVetoCodes("[]"))
        assertEquals(emptyList(), HourlyScoreStore.columnToVetoCodes(null))
        assertEquals(emptyList(), HourlyScoreStore.columnToVetoCodes("not json"))
        // 未知名跳过（向前兼容新增码），已知名保留
        assertEquals(listOf(VetoCode.THUNDERSTORM), HourlyScoreStore.columnToVetoCodes("""["THUNDERSTORM","FUTURE_CODE"]"""))
        // 往返
        val codes = listOf(VetoCode.FISHABILITY_DECAY)
        assertEquals(codes, HourlyScoreStore.columnToVetoCodes(HourlyScoreStore.vetoCodesToColumn(codes)))
    }

    @Test
    fun `toEntities_写vetoCodes列_与vetoReason对称`() {
        val epoch = MockFixtures.BASE_EPOCH_SEC
        val inputs = (0 until 2).map { i ->
            FishScoreCalculator.HourlyScoreInput(
                timeEpochSec = epoch + i * 3600L,
                weather = HourlyWeather(timeEpochSec = epoch + i * 3600L),
                tzOffsetHours = 8.0,
            )
        }
        val results = listOf(
            FishScoreResult(
                timeEpochSec = epoch,
                total = 60,
                factors = emptyList(),
                vetoReasons = emptyList(),
                confidence = Confidence.MODEL,
            ),
            FishScoreResult(
                timeEpochSec = epoch + 3600L,
                total = 10,
                factors = emptyList(),
                vetoReasons = listOf("雷暴（安全否决）"),
                confidence = Confidence.MODEL,
                vetoCodes = listOf(VetoCode.THUNDERSTORM),
            ),
        )
        val rows = HourlyScoreStore.toEntities(7L, inputs, results, 8 * 3600)
        assertEquals(2, rows.size)
        assertEquals("[]", rows[0].vetoCodes, "无否决 → '[]'（NOT NULL，禁 NULL）")
        assertNull(rows[0].vetoReason, "无否决 → vetoReason NULL（对称口径）")
        assertTrue(rows[1].vetoCodes.contains("THUNDERSTORM"), "机器码随行落库")
        assertTrue(rows[1].vetoReason!!.contains("雷暴"), "文案列保持既有语义")
    }
}