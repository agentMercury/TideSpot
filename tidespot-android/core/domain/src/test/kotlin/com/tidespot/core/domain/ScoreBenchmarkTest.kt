package com.tidespot.core.domain

import com.tidespot.core.domain.algorithm.Presets
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OPT-05/FO-21（M6 CHG-16）：JVM 基准锁测试——240h × 全因子（含否决输入：雷暴小时 + 大风衰减
 * 小时）全量 [FishScoreCalculator] 计算，预热后 3 次取**中位数**断言 <40ms（SRS 硬指标）。
 *
 * 说明：沙箱 CI 波动余量用预热吸收（JIT + 内联缓存）；断言仍按 <40ms 不放宽——
 * 连续失败说明算法复杂度回退（如 O(n²) 引入），属应报错的结构性回归。
 */
class ScoreBenchmarkTest {

    private val base = LocalDateTime.of(2026, 11, 2, 0, 0).toEpochSecond(ZoneOffset.ofHours(8))

    /** 240h 全因子输入：天气 21 项主字段 + 海洋（浪/潮位/SST）+ sr/ss；含否决输入。 */
    private fun inputs(): List<FishScoreCalculator.HourlyScoreInput> {
        fun weather(i: Int) = HourlyWeather(
            timeEpochSec = base + i * 3600L,
            temperatureC = 16.0 + 6.0 * sin(2 * PI * i / 24.0),
            dewPointC = 11.0,
            apparentC = 15.0 + 6.0 * sin(2 * PI * i / 24.0),
            pressureMslHpa = 1013.0 + 1.2 * sin(2 * PI * i / 48.0),
            surfacePressureHpa = 1000.0 + 1.2 * sin(2 * PI * i / 48.0),
            cloudCoverPct = 40.0 + 20.0 * sin(2 * PI * i / 24.0),
            cloudLowPct = 20.0, cloudMidPct = 15.0, cloudHighPct = 10.0,
            visibilityM = 20_000.0,
            precipMm = if (i % 13 == 0) 1.5 else 0.0,
            rainMm = if (i % 13 == 0) 1.2 else 0.0,
            showersMm = 0.0, snowMm = 0.0,
            // 否决输入：每 40h 一场雷暴（硬否决）+ 第 100~106h 持续大风（衰减明细）
            weatherCode = when { i % 40 == 0 -> 95; else -> 1 },
            windSpeedKmh = if (i in 100..106) 44.0 else 12.0,
            windDirDeg = 135.0,
            windGustKmh = if (i in 100..106) 62.0 else 20.0,
            cape = 320.0, freezingLevelM = 3200.0,
        )
        fun marine(i: Int) = HourlyMarine(
            timeEpochSec = base + i * 3600L,
            waveHeightM = 0.6 + 0.4 * sin(2 * PI * i / 12.42),
            wavePeriodS = 6.5, waveDirectionDeg = 120.0, wavePeakPeriodS = 9.0,
            windWaveHeightM = 0.3, windWavePeriodS = 5.0, windWaveDirectionDeg = 130.0,
            swellWaveHeightM = 0.5, swellWavePeriodS = 9.5, swellWaveDirectionDeg = 115.0,
            secondarySwellHeightM = 0.2, tertiarySwellHeightM = 0.1,
            oceanCurrentVelocityKmh = 1.1,
            oceanCurrentDirectionDeg = 40.0,
            seaLevelHeightMslM = 1.2 + 0.9 * sin(2 * PI * i / 12.42),
            seaSurfaceTempC = 20.5 + 0.8 * sin(2 * PI * i / 24.0),
        )
        return (0 until 240).map { i ->
            FishScoreCalculator.HourlyScoreInput(
                timeEpochSec = base + i * 3600L,
                weather = weather(i),
                marine = marine(i),
                sunriseLocalHour = 6.3,
                sunsetLocalHour = 17.4,
                tzOffsetHours = 8.0,
            )
        }
    }

    @Test
    fun `OPT05_240h全因子含否决输入_预热后3次中位数小于40ms`() {
        val calc = FishScoreCalculator()
        val preset = Presets.SEA_SHORE   // 海水全因子（含 tide 0.24）
        val inputs = inputs()

        // 预热 3 次：吸收 JIT/内联缓存波动（沙箱余量）
        repeat(3) { calc.calculate(inputs, preset, 121.5) }

        // 计时 3 次取中位数
        val samples = (1..3).map {
            val t0 = System.nanoTime()
            val out = calc.calculate(inputs, preset, 121.5)
            val t1 = System.nanoTime()
            assertEquals(240, out.size, "全窗 240h 输出")
            (t1 - t0) / 1_000_000.0
        }
        val median = samples.sorted()[1]
        assertTrue(median < 40.0, "240h×全因子 中位耗时 ${"%.1f".format(median)}ms ≥ 40ms（3 次样本：$samples）")
    }

    @Test
    fun `OPT05_基准输入确实覆盖否决与全因子路径`() {
        val calc = FishScoreCalculator()
        val out = calc.calculate(inputs(), Presets.SEA_SHORE, 121.5)
        // 覆盖有效性：序列内确有雷暴硬否决与衰减小时（否则基准跑的是空否决路径）
        assertTrue(out.any { it.vetoCodes.contains(com.tidespot.core.domain.model.VetoCode.THUNDERSTORM) })
        assertTrue(out.any { it.vetoCodes.contains(com.tidespot.core.domain.model.VetoCode.FISHABILITY_DECAY) })
        assertTrue(out.first().factors.map { it.id }.containsAll(
            listOf("oxygen", "pressure", "solunar", "tide", "wind", "weather", "daypart", "tempMatch", "comfort"),
        ), "全因子路径（海水点 9 因子）")
    }
}