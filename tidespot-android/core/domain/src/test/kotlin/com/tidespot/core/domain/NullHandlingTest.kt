package com.tidespot.core.domain

import com.tidespot.core.domain.algorithm.Presets
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 缺失值语义（§10.1-6 NullHandlingTest；SRS §6.7 + §6.5 修正 3 / CHG-08：vetoReasons 非空 List，无理由 = 空列表，判定用 isEmpty()）。
 */
class NullHandlingTest {

    private val calc = FishScoreCalculator()

    private fun input(epochSec: Long, weather: HourlyWeather, marine: HourlyMarine?) =
        FishScoreCalculator.HourlyScoreInput(epochSec, weather, marine, tzOffsetHours = 8.0)

    private val dayBase = LocalDateTime.of(2026, 11, 1, 0, 0).toEpochSecond(ZoneOffset.ofHours(8))

    /** 2026-11-01T00:00 起第 h 小时（允许跨日，避免 HourOfDay 越界）。 */
    private fun hour(h: Int) = dayBase + h * 3600L

    @Test
    fun `内陆点海洋全 null - 不产生 wave=0 惩罚且 tide 因子剔除`() {
        // 25 小时良态内陆序列（跨日构造，避免 HourOfDay 越界）
        val inputs = (0 until 25).map { h ->
            input(
                hour(h),
                HourlyWeather(
                    timeEpochSec = 0, temperatureC = 18.0, pressureMslHpa = 1015.0,
                    apparentC = 16.0, windSpeedKmh = 10.0, windGustKmh = 18.0,
                    cloudCoverPct = 40.0, rainMm = 0.0, weatherCode = 0,
                ),
                marine = HourlyMarine(timeEpochSec = 0),   // 全 null（附录 B8 内陆响应）
            )
        }
        val results = calc.calculate(inputs, Presets.FRESH_LURE, lonDeg = 119.04)
        for (r in results.drop(24)) {   // 前 24h 用于 dP24 预热
            assertTrue(r.factors.none { it.id == "tide" }, "内陆不得出现 tide 因子")
            assertTrue(r.vetoReasons.isEmpty(), "良态内陆小时不应有否决原因（§10.1-6 / CHG-08：无理由 = 空列表，isEmpty() 判定；禁止 wave=0 类惩罚）")
            assertTrue(r.total > 50, "良态内陆小时分数不应被海洋缺失拖垮：${r.total}")
        }
    }

    @Test
    fun `无否决原因时为空列表而非 null 或空串 - UI 判 isEmpty`() {
        val inputs = (0 until 25).map { h ->
            input(
                hour(h),
                HourlyWeather(
                    timeEpochSec = 0, temperatureC = 18.0, pressureMslHpa = 1015.0,
                    apparentC = 16.0, windSpeedKmh = 10.0, windGustKmh = 18.0,
                    cloudCoverPct = 40.0, rainMm = 0.0, weatherCode = 0,
                ),
                null,
            )
        }
        val results = calc.calculate(inputs, Presets.FRESH_LURE, 119.04)
        for (r in results) {
            // §6.5 修正 3（CHG-08）：无理由时空列表，判定用 isEmpty()（禁止 null/空串/占位文本）
            assertTrue(r.vetoReasons.isEmpty())
        }
    }

    @Test
    fun `缺 past 数据算不了 ΔP3 - 取 0 且不崩`() {
        // 序列只有 3 小时：所有小时都无法形成完整 dP3 → dP3=0
        val inputs = (0 until 3).map { h ->
            input(
                hour(h),
                HourlyWeather(
                    timeEpochSec = 0, temperatureC = 18.0, pressureMslHpa = 1015.0 + h,
                    apparentC = 16.0, windSpeedKmh = 10.0, weatherCode = 0,
                ),
                null,
            )
        }
        val results = calc.calculate(inputs, Presets.FRESH_LURE, 119.04)
        assertEquals(3, results.size)
        assertTrue(results.all { it.total in 0..100 })
    }

    @Test
    fun `气温全缺失 - Tw 融合返回 null 不按 0 处理`() {
        assertEquals(null, com.tidespot.core.domain.water.WaterTempFusion.fuse(listOf(null, null, null), 2, null, null))
        // 有 SST 时直接采用（§6.1.1）
        assertEquals(19.7, com.tidespot.core.domain.water.WaterTempFusion.fuse(listOf(null), 0, null, 19.7)!!, 1e-9)
    }
}
