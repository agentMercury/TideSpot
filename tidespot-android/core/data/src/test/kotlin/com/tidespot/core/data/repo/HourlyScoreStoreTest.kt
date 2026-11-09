package com.tidespot.core.data.repo

import com.tidespot.core.data.dto.OpenMeteoTime
import com.tidespot.core.data.dto.WeatherDailyDto
import com.tidespot.core.data.mock.FakeHourlyScoreDao
import com.tidespot.core.data.mock.MockFixtures
import com.tidespot.core.domain.FishScoreCalculator
import com.tidespot.core.domain.algorithm.Presets
import com.tidespot.core.domain.model.FactorScore
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather
import com.tidespot.core.domain.model.WaterBodyType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M4 必修 8：hourly_score 落库映射（§7.3 / CHG-08 ④）——纯 JVM 层。
 * DAO 真机项对齐 RoomDaoTest/RoomMigrationTest @Ignore 先例；本类覆盖映射语义与 PK REPLACE。
 */
class HourlyScoreStoreTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ─────────────── 档位（§6.3） ───────────────

    @Test
    fun `band 五档映射`() {
        assertEquals("爆护窗口", HourlyScoreStore.band(80))
        assertEquals("爆护窗口", HourlyScoreStore.band(100))
        assertEquals("很适合", HourlyScoreStore.band(79))
        assertEquals("很适合", HourlyScoreStore.band(65))
        assertEquals("可以", HourlyScoreStore.band(64))
        assertEquals("可以", HourlyScoreStore.band(50))
        assertEquals("较差", HourlyScoreStore.band(49))
        assertEquals("较差", HourlyScoreStore.band(35))
        assertEquals("别去", HourlyScoreStore.band(34))
        assertEquals("别去", HourlyScoreStore.band(0))
    }

    // ─────────────── CHG-08 ④：vetoReasons 非空 List ↔ SQL 可空列 ───────────────

    @Test
    fun `领域空vetoReasons落库映射为NULL`() {
        assertNull(HourlyScoreStore.vetoReasonsToColumn(emptyList()), "CHG-08 ④：空列表 → NULL")
    }

    @Test
    fun `非空vetoReasons落库JSON_读回映射为空列表或原列表`() {
        val reasons = listOf("雷暴（安全否决）", "可作钓性衰减 V=0.42 风30/45km/h")
        val col = HourlyScoreStore.vetoReasonsToColumn(reasons)
        assertTrue(col != null)
        assertEquals(reasons, HourlyScoreStore.columnToVetoReasons(col), "JSON 数组串无损往返")
    }

    @Test
    fun `读回NULL或脏串映射为空列表_领域层永不出现null列表`() {
        assertEquals(emptyList(), HourlyScoreStore.columnToVetoReasons(null), "NULL → 空列表（CHG-08 ④）")
        assertEquals(emptyList(), HourlyScoreStore.columnToVetoReasons("not-json"), "脏串 → 空列表（N-4 不抛）")
        assertTrue(HourlyScoreStore.columnToVetoReasons(null).isEmpty(), "判定用 isEmpty()（CHG-08 ④）")
    }

    // ─────────────── FO-18：时区解析与评分输入 ───────────────

    @Test
    fun `时区解析_utcOffsetSeconds优先_缺失回退IANA_都缺为null`() {
        val w = Json.decodeFromString<com.tidespot.core.data.dto.WeatherResponseDto>(
            """{"utc_offset_seconds":28800}"""
        )
        assertEquals(28800, HourlyScoreStore.resolveTzOffsetSec(w, null))
        val wIana = Json.decodeFromString<com.tidespot.core.data.dto.WeatherResponseDto>(
            """{"timezone":"Asia/Tokyo"}"""
        )
        assertEquals(9 * 3600, HourlyScoreStore.resolveTzOffsetSec(wIana, null), "IANA JST → 32400")
        val wNone = Json.decodeFromString<com.tidespot.core.data.dto.WeatherResponseDto>("""{"latitude":36.06}""")
        assertNull(HourlyScoreStore.resolveTzOffsetSec(wNone, null), "都缺 → null（禁默认 +8，FO-18）")
    }

    @Test
    fun `buildScoreInputs_按时间戳join_sr_ss按当地日期映射_tz显式传入`() {
        val dtoJson = Json { ignoreUnknownKeys = true }
        val weather = DtoMappers.toHourlyWeatherList(
            dtoJson.decodeFromString<com.tidespot.core.data.dto.WeatherResponseDto>(MockFixtures.weatherJson()),
        )
        val marine = DtoMappers.toHourlyMarineList(
            dtoJson.decodeFromString<com.tidespot.core.data.dto.MarineResponseDto>(MockFixtures.marineJson()),
        )
        val daily = WeatherDailyDto(
            time = listOf(OpenMeteoTime.localIso(MockFixtures.BASE_EPOCH_SEC, MockFixtures.TZ_OFFSET_SEC).substringBefore("T")),
            sunrise = listOf("2026-10-30T06:30"),
            sunset = listOf("2026-10-30T17:20"),
        )
        val inputs = HourlyScoreStore.buildScoreInputs(weather, marine, daily, MockFixtures.TZ_OFFSET_SEC)

        assertEquals(weather.size, inputs.size)
        // join：海洋 5h 从 T0+3h 起 → 前 3 小时 marine 为 null（禁下标对齐，§5.0）
        assertNull(inputs[0].marine)
        assertEquals(0.3, inputs[3].marine?.waveHeightM)
        // sr/ss 按当地日期映射（夹具基时刻当地日期 2026-10-30）
        assertEquals(6.5, inputs[0].sunriseLocalHour)
        assertEquals(17.0 + 20.0 / 60.0, inputs[0].sunsetLocalHour!!, 1e-9)
        // FO-18：tzOffsetHours 显式传入 = tzSec/3600
        assertTrue(inputs.all { it.tzOffsetHours == 8.0 })
    }

    @Test
    fun `presetFor_预设字符串优先_水体类型兜底`() {
        assertEquals(
            Presets.SEA_SHORE,
            HourlyScoreStore.presetFor(FavoriteSpot(0, "点", 36.0, 120.0, preset = "海水岸边")),
        )
        assertEquals(
            Presets.FRESH_BAIT,
            HourlyScoreStore.presetFor(FavoriteSpot(0, "点", 36.0, 120.0, preset = "淡水台钓")),
        )
        assertEquals(
            Presets.FRESH_LURE,
            HourlyScoreStore.presetFor(FavoriteSpot(0, "点", 36.0, 120.0, preset = "淡水路亚")),
        )
        assertEquals(
            Presets.SEA_SHORE,
            HourlyScoreStore.presetFor(
                FavoriteSpot(0, "点", 36.0, 120.0, waterBody = WaterBodyType.NEARSHORE),
            ),
            "无预设 → NEARSHORE 兜底海水",
        )
        assertEquals(
            Presets.FRESH_LURE,
            HourlyScoreStore.presetFor(FavoriteSpot(0, "点", 36.0, 120.0)),
            "无预设 + LAND → 淡水路亚",
        )
    }

    // ─────────────── toEntities + DAO PK REPLACE（Fake 对齐 Room 语义） ───────────────

    @Test
    fun `toEntities_hourLocal与band与algoVersion_vetoReason空列表为NULL`() = runTest {
        val epoch = MockFixtures.BASE_EPOCH_SEC
        val inputs = listOf(
            FishScoreCalculator.HourlyScoreInput(
                epoch,
                HourlyWeather(timeEpochSec = epoch, temperatureC = 18.0),
                tzOffsetHours = 8.0,
            ),
        )
        val results = listOf(
            com.tidespot.core.domain.model.FishScoreResult(
                timeEpochSec = epoch,
                total = 83,
                factors = listOf(FactorScore("solunar", 0.7, 0.17)),
                vetoReasons = emptyList(),
                confidence = com.tidespot.core.domain.model.Confidence.MODEL,
            ),
        )
        val rows = HourlyScoreStore.toEntities(7L, inputs, results, MockFixtures.TZ_OFFSET_SEC)
        assertEquals(1, rows.size)
        val row = rows[0]
        assertEquals(7L, row.spotId)
        assertEquals(epoch, row.hourEpoch)
        assertEquals(OpenMeteoTime.localIso(epoch, MockFixtures.TZ_OFFSET_SEC), row.hourLocal, "F-3.8 当地墙串")
        assertEquals(83.0, row.score)
        assertEquals("爆护窗口", row.band)
        assertNull(row.vetoReason, "CHG-08 ④：领域空列表 → SQL NULL")
        assertEquals(FishScoreCalculator.ALGO_VERSION, row.algoVersion)
        val parsed = json.parseToJsonElement(row.factorsJson).jsonArray
        assertEquals(1, parsed.size)
        assertEquals("solunar", parsed[0].jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `FakeHourlyScoreDao_PK_REPLACE_同键覆盖_不同algoVersion并存`() = runTest {
        val dao = FakeHourlyScoreDao()
        val e1 = com.tidespot.core.data.local.HourlyScoreEntity(
            spotId = 1, hourEpoch = 100, hourLocal = "2026-10-30T13:00",
            score = 50.0, band = "可以", vetoReason = null,
            factorsJson = "[]", algoVersion = 1,
        )
        dao.insertAll(listOf(e1))
        dao.insertAll(listOf(e1.copy(score = 66.0, band = "很适合")))
        assertEquals(1, dao.rows.size, "同 PK(spotId,hourEpoch,algoVersion) 冲突 → 覆盖（CHG-03/N-2）")
        assertEquals(66.0, dao.rows[0].score)
        dao.insertAll(listOf(e1.copy(algoVersion = 2, score = 70.0)))
        assertEquals(2, dao.rows.size, "不同 algoVersion 并存（N-10 升级可整体重算）")
        assertEquals(2, dao.forSpot(1, algoVersion = 1).size + dao.forSpot(1, algoVersion = 2).size)
        dao.deleteForSpot(1)
        assertTrue(dao.rows.isEmpty())
    }
}
