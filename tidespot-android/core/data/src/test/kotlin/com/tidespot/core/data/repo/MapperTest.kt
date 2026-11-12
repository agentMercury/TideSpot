package com.tidespot.core.data.repo

import com.tidespot.core.data.dto.MarineResponseDto
import com.tidespot.core.data.dto.OpenMeteoTime
import com.tidespot.core.data.dto.WeatherResponseDto
import com.tidespot.core.data.mock.MockFixtures
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.model.WaterBodyType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 映射层：null 保真（§5.0）、SI 换算（F-4.4）、实体重建往返一致性。 */
class MapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `天气解析_字段值与源一致`() {
        val dto = json.decodeFromString<WeatherResponseDto>(MockFixtures.weatherJson())
        val list = DtoMappers.toHourlyWeatherList(dto)

        assertEquals(MockFixtures.WEATHER_HOURS, list.size)
        assertEquals(10.0, list[0].temperatureC)
        assertEquals(17.0, list[7].temperatureC)
        assertEquals(15.0, list[0].windGustKmh)
        assertEquals(100.0, list[0].cape)
        assertEquals(1, list[1].weatherCode)
        // epoch 还原：tz=+8h，当地 08:00 = 00:00Z
        assertEquals(MockFixtures.BASE_EPOCH_SEC, list[0].timeEpochSec)
        assertEquals(MockFixtures.BASE_EPOCH_SEC + 7 * 3600, list[7].timeEpochSec)
    }

    @Test
    fun `null 禁止当 0_显式 null 保真`() {
        val dto = json.decodeFromString<WeatherResponseDto>(MockFixtures.weatherJson())
        val list = DtoMappers.toHourlyWeatherList(dto)

        assertNull(list[2].precipMm, "源为 null 必须保持 null（§5.0），禁止 0 填充")
        assertEquals(0.5, list[0].precipMm, "真实的 0.5 值保留")
        // rain 源为 0.0（真实零值）→ 保持 0.0，不是 null
        assertEquals(0.0, list[0].rainMm)
    }

    @Test
    fun `visibility km 转 m（SI）`() {
        val dto = json.decodeFromString<WeatherResponseDto>(MockFixtures.weatherJson())
        val list = DtoMappers.toHourlyWeatherList(dto)
        assertEquals(20_000.0, list[0].visibilityM, "源 20.0 km → 20000 m（F-4.4 SI）")
    }

    @Test
    fun `海洋解析与内陆全 null 判定`() {
        val dto = json.decodeFromString<MarineResponseDto>(MockFixtures.marineJson())
        val list = DtoMappers.toHourlyMarineList(dto)

        assertEquals(MockFixtures.MARINE_HOURS, list.size)
        assertEquals(0.3, list[0].waveHeightM)
        assertEquals(19.5, list[0].seaSurfaceTempC)
        assertEquals(0.17, list[0].seaLevelHeightMslM)
        assertEquals(1.2, list[0].oceanCurrentVelocityKmh)
        assertTrue(list.none { it.isAllNull })

        val inland = json.decodeFromString<MarineResponseDto>(
            MockFixtures.marineJson(allNull = true)
        )
        val inlandList = DtoMappers.toHourlyMarineList(inland)
        assertTrue(inlandList.all { it.isAllNull }, "附录 B8：200+全 null → isAllNull 触发降级")
        assertNull(inlandList[0].waveHeightM, "禁止把 null 读成 0（附录 B8/§6.9）")
    }

    // ─────────────── BLK-06/N-4（CHG-07 修复 4）：脏时间串不抛异常 ───────────────

    /** 脏时间串夹具：3 小时中第 0 条非数字、第 2 条为空串。 */
    private fun dirtyTimeWeatherJson(): String =
        """
        {"latitude":36.06,"longitude":120.38,"utc_offset_seconds":28800,
         "hourly":{"time":["not-a-time","2026-11-02T09:00",""],
          "temperature_2m":[10.0,11.0,12.0],"pressure_msl":[1013.0,1013.1,1013.2],
          "rain":[0.0,0.0,0.0],"showers":[0.0,0.0,0.0]}}
        """.trimIndent()

    @Test
    fun `脏时间串_跳过该小时不抛异常_其余小时保真`() {
        val dto = json.decodeFromString<WeatherResponseDto>(dirtyTimeWeatherJson())
        // 不抛 DateTimeParseException（N-4 强制异常用例）
        val list = DtoMappers.toHourlyWeatherList(dto)
        assertEquals(1, list.size, "仅可解析的第 1 小时保留（脏行整行跳过）")
        assertEquals(11.0, list[0].temperatureC, "保留行的字段值保真")
    }

    @Test
    fun `脏时间串_整窗入库同步跳过且字段与源对齐`() {
        val dto = json.decodeFromString<WeatherResponseDto>(dirtyTimeWeatherJson())
        // 不抛异常；逐小时独立映射 → 跳行后字段不错位
        val entities = DtoMappers.weatherEntities(spotId = 1, dto = dto, fetchedAtMillis = 123L)
        assertEquals(WeatherFieldNames.ALL.size, entities.size, "只入库可解析的 1 小时 × 21 字段")
        val temp = entities.first { it.field == WeatherFieldNames.TEMPERATURE_2M }
        assertEquals(11.0, temp.value, "对齐保真：跳过 index0/2 后 index1 值不串行")
    }

    // ─────────────── CHG-09⑥（NEW-06）：utcOffset 缺失禁当 0 ───────────────

    /** 无 utc_offset_seconds、仅有 IANA timezone 的响应（CHG-09⑥ 回退路径夹具）。 */
    private fun tzOnlyWeatherJson(): String =
        """
        {"latitude":36.06,"longitude":120.38,"timezone":"Asia/Shanghai",
         "hourly":{"time":["2026-11-02T09:00"],"temperature_2m":[10.0],"pressure_msl":[1013.0],
          "rain":[0.0],"showers":[0.0]}}
        """.trimIndent()

    @Test
    fun `无utcOffsetSeconds但有timezoneId_回退IANA推算正确epoch`() {
        val dto = json.decodeFromString<WeatherResponseDto>(tzOnlyWeatherJson())
        val list = DtoMappers.toHourlyWeatherList(dto)
        assertEquals(1, list.size)
        assertEquals(
            OpenMeteoTime.parseEpochSec("2026-11-02T09:00", MockFixtures.TZ_OFFSET_SEC),
            list[0].timeEpochSec,
            "Asia/Shanghai → +8h 推算，与 utc_offset_seconds 直供同值",
        )
        // 整窗入库走同一回退
        val entities = DtoMappers.weatherEntities(spotId = 1, dto = dto, fetchedAtMillis = 1L)
        assertEquals(WeatherFieldNames.ALL.size, entities.size)
    }

    @Test
    fun `utcOffset与timezone都缺_整窗跳过不抛异常_禁当0`() {
        val raw = """{"latitude":36.06,"hourly":{"time":["2026-11-02T09:00"],"temperature_2m":[10.0]}}"""
        val dto = json.decodeFromString<WeatherResponseDto>(raw)
        // 不抛异常；该小时跳过（同脏时间串语义，整窗降级），绝不按 offset=0 解析出假 epoch
        assertTrue(DtoMappers.toHourlyWeatherList(dto).isEmpty())
        assertTrue(DtoMappers.weatherEntities(spotId = 1, dto = dto, fetchedAtMillis = 1L).isEmpty())
        // 海洋侧同语义（本批未加 timezone 字段 → 缺失即跳过）
        val marineRaw = """{"latitude":36.06,"hourly":{"time":["2026-11-02T09:00"],"wave_height":[0.3]}}"""
        val marine = json.decodeFromString<MarineResponseDto>(marineRaw)
        assertTrue(DtoMappers.toHourlyMarineList(marine).isEmpty())
        assertTrue(DtoMappers.marineEntities(spotId = 1, dto = marine, fetchedAtMillis = 1L).isEmpty())
    }

    @Test
    fun `实体写入与重建往返一致`() {
        val dto = json.decodeFromString<WeatherResponseDto>(MockFixtures.weatherJson())
        val entities = DtoMappers.weatherEntities(spotId = 1, dto = dto, fetchedAtMillis = 123L)
        // 8h × 21 字段（含 domain 未承载的湿度）全部入库
        assertEquals(MockFixtures.WEATHER_HOURS * WeatherFieldNames.ALL.size, entities.size)

        val rebuilt = EntityMappers.toHourlyWeather(entities)
        val direct = DtoMappers.toHourlyWeatherList(dto)
        assertEquals(direct, rebuilt, "entity 落库再重建应与直接映射完全一致（Int 字段 round-trip）")
    }

    // ─────────────── FO-18（M4 必修 4）：marine IANA 回退 + §10.1-12 TzTest ───────────────

    @Test
    fun `海洋响应IANA时区回退解析_epoch正确`() {
        val raw = """{"latitude":36.06,"timezone":"Asia/Tokyo",
          "hourly":{"time":["2026-11-02T09:00"],"wave_height":[0.3],"sea_level_height_msl":[0.17]}}"""
        val dto = json.decodeFromString<MarineResponseDto>(raw)
        val list = DtoMappers.toHourlyMarineList(dto)
        assertEquals(1, list.size, "marine 补 timezone 字段后可解析（此前缺失即整窗跳过）")
        assertEquals(
            OpenMeteoTime.parseEpochSec("2026-11-02T09:00", 9 * 3600),
            list[0].timeEpochSec,
            "Asia/Tokyo → +9h",
        )
    }

    @Test
    fun `TzTest_JST加9_当地23_30请求日期正确`() {
        // §10.1-12：当地（JST +9）23:30 的"今天"边界——墙串往返日期不串轴，UTC 差恰为 -1h
        val epoch2330 = OpenMeteoTime.parseEpochSec("2026-11-09T23:30", 9 * 3600)
        assertTrue(OpenMeteoTime.localIso(epoch2330, 9 * 3600).startsWith("2026-11-09T23:30"), "JST 当地 23:30 往返日期不串轴")
        assertEquals(
            OpenMeteoTime.parseEpochSec("2026-11-09T23:30", MockFixtures.TZ_OFFSET_SEC) - 3600L,
            epoch2330,
            "同一当地墙钟 JST(+9) 比 +8 早 1h 进 UTC",
        )
        // 当地 00:30（已跨日）→ 往返仍为当地新日期
        val epoch0030 = OpenMeteoTime.parseEpochSec("2026-11-10T00:30", 9 * 3600)
        assertTrue(OpenMeteoTime.localIso(epoch0030, 9 * 3600).startsWith("2026-11-10T00:30"), "当地 00:30 跨日往返日期正确")
    }

    @Test
    fun `TzTest_加5_30半点时区_当地23_30日期正确`() {
        val epoch = OpenMeteoTime.parseEpochSec("2026-11-09T23:30", 19800)
        assertTrue(OpenMeteoTime.localIso(epoch, 19800).startsWith("2026-11-09T23:30"), "+5:30 半点偏移往返无损")
        assertEquals(
            OpenMeteoTime.parseEpochSec("2026-11-09T23:30", MockFixtures.TZ_OFFSET_SEC) + 9000L,
            epoch,
            "+5:30 与 +8 相差 2.5h",
        )
    }

    @Test
    fun `收藏实体与 domain 往返`() {
        val spot = FavoriteSpot(
            id = 0, name = "千岛湖", lat = 29.6, lon = 119.04,
            memo = "库湾", preset = "淡水路亚", tags = listOf("夜钓", "翘嘴"),
            waterBody = WaterBodyType.INLAND_WATER, createdAtEpochSec = 100,
        )
        val entity = EntityMappers.run { spot.toEntity(nowEpochSec = 200) }
        assertTrue("夜钓" in entity.tagsJson && "翘嘴" in entity.tagsJson, "tags 序列化为 JSON 数组")
        val back = EntityMappers.run { entity.copy(id = 7).toDomain() }
        assertEquals(7, back.id)
        assertEquals("千岛湖", back.name)
        assertEquals("库湾", back.memo)
        assertEquals("淡水路亚", back.preset)
        assertEquals(listOf("夜钓", "翘嘴"), back.tags)
        assertEquals(WaterBodyType.INLAND_WATER, back.waterBody)
        assertEquals(100, back.createdAtEpochSec, "创建时刻不被覆盖")
    }

    @Test
    fun `M6_tzOffsetSec映射_toEntity与toDomain双向_null保真`() {
        // CHG-16⑨（OPT-M5-03）：选点时区偏移秒随 domain 映射（列已存在，此前未映射）
        val spot = FavoriteSpot(
            id = 0, name = "东京湾", lat = 35.65, lon = 139.75,
            tzOffsetSec = 9 * 3600,
        )
        val entity = EntityMappers.run { spot.toEntity(nowEpochSec = 200) }
        assertEquals(9 * 3600, entity.tzOffsetSec, "toEntity 映射 tzOffsetSec")
        val back = EntityMappers.run { entity.copy(id = 7).toDomain() }
        assertEquals(9 * 3600, back.tzOffsetSec, "toDomain 映射 tzOffsetSec")
        // null 语义：未回写（选点未刷新前）→ null，禁 0 填充（§5.0）
        val noTz = FavoriteSpot(id = 0, name = "x", lat = 0.0, lon = 0.0)
        assertNull(EntityMappers.run { noTz.toEntity(nowEpochSec = 1) }.tzOffsetSec)
        assertNull(EntityMappers.run { noTz.toEntity(nowEpochSec = 1).toDomain() }.tzOffsetSec)
    }
}
