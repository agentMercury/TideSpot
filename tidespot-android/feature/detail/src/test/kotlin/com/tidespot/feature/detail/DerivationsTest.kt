package com.tidespot.feature.detail

import com.tidespot.core.data.repo.HourSlice
import com.tidespot.core.data.repo.SpotDetailResult
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather
import com.tidespot.core.domain.model.WaterBodyType
import com.tidespot.feature.detail.detail.Derivations
import com.tidespot.feature.detail.detail.DetailStateHolder
import com.tidespot.feature.detail.detail.DetailTab
import com.tidespot.core.domain.algorithm.Presets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ViewModel/派生层状态逻辑（Tab 切换不重算 / 分维降级互不影响 / null 不补 0 / 当日摘要极值）。 */
class DetailStateHolderTest {

    private val zone: ZoneId = ZoneOffset.ofHours(9) // JST（F-3.8 大阪点口径）
    private val today: LocalDate = LocalDate.of(2026, 11, 6)

    /** JST 2026-11-06 00:00 起 48 小时（epoch 基准）。 */
    private fun epoch(h: Int): Long = LocalDateTime.of(2026, 11, 6, 0, 0)
        .toInstant(ZoneOffset.ofHours(9)).epochSecond + h * 3600L

    private fun weather(h: Int, temp: Double? = 20.0, wavePres: Double? = 1010.0) = HourlyWeather(
        timeEpochSec = epoch(h),
        temperatureC = temp,
        pressureMslHpa = wavePres,
        windSpeedKmh = 10.0,
        windGustKmh = 20.0,
        cloudCoverPct = 50.0,
        precipMm = 0.0,
        weatherCode = 1,
    )

    private fun marine(h: Int, wave: Double? = 1.2, level: Double? = 1.0) = HourlyMarine(
        timeEpochSec = epoch(h),
        waveHeightM = wave,
        wavePeriodS = 6.0,
        waveDirectionDeg = 148.0,
        seaLevelHeightMslM = level,
        oceanCurrentVelocityKmh = 2.0,
        seaSurfaceTempC = 18.0,
    )

    private fun result(
        weather: List<HourlyWeather>,
        marine: List<HourlyMarine>,
        weatherDegraded: Boolean = false,
        marineDegraded: Boolean = false,
        marineAllNullInland: Boolean = false,
    ) = SpotDetailResult(
        spotId = 1L,
        slices = weather.map { w -> HourSlice(w.timeEpochSec, w, marine.firstOrNull { it.timeEpochSec == w.timeEpochSec }) },
        weather = weather,
        marine = marine,
        daily = null,
        weatherDegraded = weatherDegraded,
        marineDegraded = marineDegraded,
        marineAllNullInland = marineAllNullInland,
        errors = emptyMap(),
        servedFromCache = emptySet(),
        fetchedAtMillis = mapOf(
            com.tidespot.core.data.repo.DataSource.WEATHER to 1762387200000L,
            com.tidespot.core.data.repo.DataSource.MARINE to 1762387200000L,
        ),
    )

    private fun holder() = DetailStateHolder(zone, daysRange = 7, lonDeg = 135.0, preset = Presets.FRESH_LURE)

    /** CHG-13/N15：与 fixture `fetchedAtMillis`（1762387200000）配套的「当前时刻」＝拉取后 1 分钟（新鲜）。 */
    private val now: Long = 1762387200000L + 60_000L

    @Test
    fun tabSwitch_doesNotRecomputeDerivedSeries() { // N-2 Tab 切换不重算
        val h = holder()
        val r = result((0..23).map { weather(it) }, (0..23).map { marine(it) })
        h.onResult(r, now)
        val before = h.state.value.scores
        h.selectTab(DetailTab.SCORE)
        h.selectTab(DetailTab.TIDE)
        assertEquals(before, h.state.value.scores) // 同一实例，未重算
        assertEquals(DetailTab.TIDE, h.state.value.selectedTab)
        assertEquals(null, h.state.value.selectedHour) // 切 Tab 清空小时选中
    }

    @Test
    fun dimensionDegradations_areIndependent() { // F-3.6 互不影响
        val h = holder()
        // 天气失败且无数据，海洋正常
        h.onResult(result(emptyList(), (0..23).map { marine(it) }, weatherDegraded = true), now)
        assertTrue(Derivations.tabStatus(DetailTab.WEATHER, h.state.value.result!!, now) is com.tidespot.feature.detail.detail.DimensionStatus.Degraded)
        assertEquals("来源不可用", (Derivations.tabStatus(DetailTab.WEATHER, h.state.value.result!!, now) as com.tidespot.feature.detail.detail.DimensionStatus.Degraded).reason)
        assertEquals(com.tidespot.feature.detail.detail.DimensionStatus.Available, Derivations.tabStatus(DetailTab.WAVE, h.state.value.result!!, now))
        assertEquals(com.tidespot.feature.detail.detail.DimensionStatus.Available, Derivations.tabStatus(DetailTab.TIDE, h.state.value.result!!, now))
    }

    @Test
    fun inlandAllNullMarine_degradesWaveAndTide_only() { // §6.7 整维度 null / 内陆不显示假海况
        val h = holder()
        val allNull = (0..23).map { HourlyMarine(timeEpochSec = epoch(it)) } // 全 null marine
        h.onResult(result((0..23).map { weather(it) }, allNull, marineAllNullInland = true, marineDegraded = true), now)
        assertEquals("本点无该来源数据", (Derivations.tabStatus(DetailTab.WAVE, h.state.value.result!!, now) as com.tidespot.feature.detail.detail.DimensionStatus.Degraded).reason)
        assertEquals("本点无该来源数据", (Derivations.tabStatus(DetailTab.TIDE, h.state.value.result!!, now) as com.tidespot.feature.detail.detail.DimensionStatus.Degraded).reason)
        // 评分/水质走天气序列兜底链，内陆不影响
        assertEquals(com.tidespot.feature.detail.detail.DimensionStatus.Available, Derivations.tabStatus(DetailTab.SCORE, h.state.value.result!!, now))
        assertEquals(com.tidespot.feature.detail.detail.DimensionStatus.Available, Derivations.tabStatus(DetailTab.WATER_QUALITY, h.state.value.result!!, now))
    }

    @Test
    fun nullWaveNeverBecomesZero() { // null 保真：图表值/表格均为 —，禁 0 填充
        val h = holder()
        val ms = (0..23).map { marine(it, wave = if (it == 5) null else 1.2) }
        h.onResult(result((0..23).map { weather(it) }, ms), now)
        val daySlices = Derivations.slicesForDay(h.state.value.result!!.slices, 0, zone, today)
        val values = Derivations.chartValues(DetailTab.WAVE, daySlices, h.scoresByEpoch(), emptyMap(), h.state.value.tideRates)
        assertEquals(null, values[5])
        val row = Derivations.waveRows(daySlices, zone)[5]
        assertTrue(row.valueText.contains("—"))
        assertTrue(!row.valueText.contains("0.0 m /")) // 不得把 null 画成 0
    }

    @Test
    fun dailySummary_extremesWithTimestamps_allNullGivesNull() { // F-3.2③
        val pairs = listOf(100L to 1.0, 200L to 3.5, 300L to 2.0, 400L to null)
        val s = Derivations.summary(pairs)!!
        assertEquals(3.5, s.max); assertEquals(200L, s.maxEpochSec)
        assertEquals(1.0, s.min); assertEquals(100L, s.minEpochSec)
        assertEquals(null, Derivations.summary(listOf(1L to null, 2L to null)))
        assertEquals(null, Derivations.summary(emptyList()))
    }

    @Test
    fun slicesForDay_usesSpotLocalZone_notUtc() { // F-3.8 当地墙钟切日
        val slices = listOf(
            HourSlice(epoch(-1), weather(-1), null), // JST 11-05 23:00
            HourSlice(epoch(0), weather(0), null),   // JST 11-06 00:00
            HourSlice(epoch(24), weather(24), null), // JST 11-07 00:00
        )
        val day0 = Derivations.slicesForDay(slices, 0, zone, today)
        assertEquals(listOf(epoch(0)), day0.map { it.timeEpochSec })
        val day1 = Derivations.slicesForDay(slices, 1, zone, today)
        assertEquals(listOf(epoch(24)), day1.map { it.timeEpochSec })
    }

    @Test
    fun tideRates_missingNeighborIsNull_neverZero() { // dh/dt 差分
        val rates = Derivations.tideRates(listOf(marine(0, level = 1.0), marine(1, level = 1.5), marine(2, level = null), marine(3, level = 2.0)))
        assertEquals(0.5, rates[epoch(1)])           // Δ=+0.5 m/h
        assertEquals(null, rates[epoch(2)])          // 邻侧缺测 → null
        assertEquals(null, rates[epoch(3)])          // 前侧 null → null
        assertEquals(null, rates[epoch(0)])          // 首小时无前邻 → null
    }

    @Test
    fun tzOffsetHours_explicit_notHardcodedEight() { // CONTRACT：显式传值
        assertEquals(9.0, Derivations.tzOffsetHours(epoch(0), zone))
        assertEquals(5.5, Derivations.tzOffsetHours(epoch(0), ZoneOffset.ofHoursMinutes(5, 30)))
    }
}

/** 兜底回归：FavoriteSpot 模型可用性（detail 依赖 domain 冻结模型）。 */
class DetailModelSanityTest {
    @Test
    fun favoriteSpotDefaults() {
        val spot = FavoriteSpot(id = 1L, name = "千岛湖", lat = 29.60, lon = 119.04)
        assertEquals(WaterBodyType.LAND, spot.waterBody)
        assertTrue(spot.tags.isEmpty())
    }
}

/** CHG-13 修复回归锁（M4改进意见01/02 · D03/D01/N15；M4 验收会话意见闭环）。 */
class Chg13RegressionTest {

    private val zone: ZoneId = ZoneOffset.ofHours(8)
    private val fetch: Long = 1_762_387_200_000L
    private val hour: Long = 3_600_000L

    private fun spot(preset: String?, waterBody: WaterBodyType = WaterBodyType.OCEAN) =
        FavoriteSpot(id = 1L, name = "测试点", lat = 30.0, lon = 122.0, preset = preset, waterBody = waterBody)

    // ── D03：预设解析唯一口径（数据层落库与 UI 派生恒等；id→中文标签→水体兜底） ──

    @Test
    fun resolvePreset_coversIdAndLabelAndWaterBodyFallback() {
        // 英文 id（M6 设置页将存的形态）
        assertEquals(Presets.SEA_SHORE, com.tidespot.core.data.repo.HourlyScoreStore.resolvePreset(spot("sea_shore")))
        assertEquals(Presets.FRESH_LURE, com.tidespot.core.data.repo.HourlyScoreStore.resolvePreset(spot("fresh_lure")))
        assertEquals(Presets.FRESH_BAIT, com.tidespot.core.data.repo.HourlyScoreStore.resolvePreset(spot("fresh_bait")))
        // 中文标签（Models.kt KDoc 契约形态；D03 根因：UI 层 byId 不认中文 → 回落淡水，海点 0.24 潮权重丢失）
        assertEquals(Presets.SEA_SHORE, com.tidespot.core.data.repo.HourlyScoreStore.resolvePreset(spot("海水岸边")))
        assertEquals(Presets.FRESH_BAIT, com.tidespot.core.data.repo.HourlyScoreStore.resolvePreset(spot("淡水台钓")))
        assertEquals(Presets.FRESH_LURE, com.tidespot.core.data.repo.HourlyScoreStore.resolvePreset(spot("淡水路亚")))
        // null / 未知串 → 水体类型兜底
        assertEquals(Presets.SEA_SHORE, com.tidespot.core.data.repo.HourlyScoreStore.resolvePreset(spot(null, WaterBodyType.OCEAN)))
        assertEquals(Presets.SEA_SHORE, com.tidespot.core.data.repo.HourlyScoreStore.resolvePreset(spot(null, WaterBodyType.NEARSHORE)))
        assertEquals(Presets.FRESH_LURE, com.tidespot.core.data.repo.HourlyScoreStore.resolvePreset(spot(null, WaterBodyType.INLAND_WATER)))
        assertEquals(Presets.FRESH_LURE, com.tidespot.core.data.repo.HourlyScoreStore.resolvePreset(spot("不存在", WaterBodyType.LAND)))
    }

    @Test
    fun resolvePreset_identityWithDataLayerPresetFor_allMatrix() { // 库内分 == UI 分的前提：两侧同一函数
        for (preset in listOf(null, "sea_shore", "海水岸边", "淡水路亚", "未知串")) {
            for (water in WaterBodyType.entries) {
                val s = spot(preset, water)
                assertEquals(
                    com.tidespot.core.data.repo.HourlyScoreStore.presetFor(s),
                    com.tidespot.core.data.repo.HourlyScoreStore.resolvePreset(s),
                    "preset=$preset water=$water 两侧解析必须恒等（D03）",
                )
            }
        }
    }

    // ── N15：§6.7 第 4 行 >48h 不可用 → 评分链路短路 ──

    private fun weather(h: Int) = HourlyWeather(
        timeEpochSec = fetch / 1000 + h * 3600L,
        temperatureC = 20.0, pressureMslHpa = 1010.0, windSpeedKmh = 10.0,
        windGustKmh = 20.0, cloudCoverPct = 50.0, precipMm = 0.0, weatherCode = 1,
    )

    private fun result(fetchedAtMillis: Long) = SpotDetailResult(
        spotId = 1L,
        slices = (0..23).map { w -> HourSlice(fetch / 1000 + w * 3600L, weather(w), null) },
        weather = (0..23).map { weather(it) },
        marine = emptyList(),
        daily = null,
        weatherDegraded = false,
        marineDegraded = false,
        marineAllNullInland = true,
        errors = emptyMap(),
        servedFromCache = emptySet(),
        fetchedAtMillis = mapOf(
            com.tidespot.core.data.repo.DataSource.WEATHER to fetchedAtMillis,
            com.tidespot.core.data.repo.DataSource.MARINE to fetchedAtMillis,
        ),
    )

    @Test
    fun onResult_staleWithin48h_stillScores() { // 过期可看（<48h）：照常评分
        val h = DetailStateHolder(zone, daysRange = 7, lonDeg = 122.0, preset = Presets.FRESH_LURE)
        h.onResult(result(fetch + 47 * hour), nowMillis = fetch + 47 * hour)
        assertTrue(h.state.value.scores.isNotEmpty(), "<48h 过期缓存仍可显示与评分（§6.7 黄条语义）")
    }

    @Test
    fun onResult_staleBeyond48h_shortCircuitsScoringAndMarksUnusable() { // >48h：不再据此评分
        val h = DetailStateHolder(zone, daysRange = 7, lonDeg = 122.0, preset = Presets.FRESH_LURE)
        val now = fetch + 49 * hour
        h.onResult(result(fetch), nowMillis = now)
        assertTrue(h.state.value.scores.isEmpty(), "≥48h 缓存不得再产出评分（§6.7 第 4 行）")
        val status = Derivations.tabStatus(DetailTab.SCORE, h.state.value.result!!, now)
        assertTrue(status is com.tidespot.feature.detail.detail.DimensionStatus.Degraded)
        assertTrue((status as com.tidespot.feature.detail.detail.DimensionStatus.Degraded).reason.contains("数据不可用"))
        assertTrue(status.reason.contains("不作为评分依据"))
    }

    @Test
    fun tabStatus_inlandNoSource_takesPrecedenceOverUnusable() { // 内陆全 null：语义是「无来源」而非「过期」
        val now = fetch + 100 * hour
        val status = Derivations.tabStatus(DetailTab.WAVE, result(fetch), now)
        assertTrue(status is com.tidespot.feature.detail.detail.DimensionStatus.Degraded)
        assertEquals("本点无该来源数据", (status as com.tidespot.feature.detail.detail.DimensionStatus.Degraded).reason)
    }
}
