package fixture

import com.tidespot.core.domain.FishScoreCalculator
import com.tidespot.core.domain.algorithm.MathKit
import com.tidespot.core.domain.algorithm.Presets
import com.tidespot.core.domain.algorithm.ScenePreset
import com.tidespot.core.domain.algorithm.Solunar
import com.tidespot.core.domain.algorithm.SubScores
import com.tidespot.core.domain.algorithm.VetoEvaluator
import com.tidespot.core.domain.model.Confidence
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather
import com.tidespot.core.domain.water.DoPhysics
import com.tidespot.core.domain.water.WaterQualityProxy
import com.tidespot.core.domain.water.WaterTempFusion
import kotlin.math.sin

/**
 * 生成 Web 对拍 fixture（只读 Android 源码：本文件位于 web/.tmp，不进入任何工程）。
 * 输出：JSON 到 stdout，由 web/scripts 重定向为 test/fixtures/domain.json
 */

private fun n(x: Double?): String {
    if (x == null || x.isNaN() || x.isInfinite()) return "null"
    return if (x == 0.0) "0" else String.format(java.util.Locale.US, "%.10g", x)
}

private fun s(v: String): String {
    val sb = StringBuilder("\"")
    for (c in v) {
        when (c) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
    }
    return sb.append('"').toString()
}

private fun arr(block: StringBuilder.() -> Unit): String = buildString {
    append('[')
    block()
    if (length > 1 && this[length - 1] == ',') setLength(length - 1)
    append(']')
}

fun main() {
    val out = StringBuilder()
    out.append('{')

    // ---------- MathKit ----------
    out.append("\"mathkit\":{")
    out.append("\"clip01\":[").append(listOf(-1.0, 0.0, 0.5, 1.0, 2.0).joinToString(",") { n(MathKit.clip01(it)) }).append("],")
    out.append("\"gauss\":[").append(listOf(0.0, 15.0, 30.0, 45.0).joinToString(",") { n(MathKit.gauss(it, 15.0, 13.0)) }).append("],")
    out.append("\"trap\":[").append(listOf(-0.3, 0.0, 0.3, 0.8, 1.2, 1.5, 2.0, 2.2).joinToString(",") { n(MathKit.trap(it, -0.2, 0.5, 1.2, 2.1)) }).append("],")
    out.append("\"sig\":[").append(listOf(0.0, 20.0, 34.0, 45.0, 60.0).joinToString(",") { n(MathKit.sig(it, 34.0, 0.30)) }).append("],")
    out.append("\"jsMod\":[").append(listOf(-25.0, -12.4, 0.0, 3.0, 24.0, 30.0).joinToString(",") { n(MathKit.jsMod(it, 12.4206)) }).append("]")
    out.append("},")

    // ---------- SubScores ----------
    val dhList = (-10..10).map { it * 0.05 }
    val ampList = listOf(0.2, 0.6, 1.0, 1.8, 3.0)
    out.append("\"subScores\":{")

    out.append("\"tide\":")
    out.append(arr {
        for (dh in dhList) for (amp in ampList) {
            append("[").append(n(dh)).append(',').append(n(amp)).append(',').append(n(SubScores.tide(dh, amp))).append("],")
        }
    })
    out.append(",")

    out.append("\"pressure\":")
    out.append(arr {
        for (p in listOf(985.0, 1000.0, 1010.0, 1013.25, 1020.0, 1035.0)) {
            for (d3 in listOf(null, -6.0, -3.0, -2.5, 0.0, 2.0, 5.0)) {
                for (d24 in listOf(-6.0, 0.0, 6.0)) {
                    append("[").append(n(p)).append(',').append(n(d3)).append(',').append(n(d24)).append(',')
                        .append(n(SubScores.pressure(p, d3, d24))).append("],")
                }
            }
        }
    })
    out.append(",")

    out.append("\"wind\":")
    out.append(arr {
        for (sp in 0..60 step 5) {
            for (g in listOf(null, 30.0, 40.0, 46.0, 60.0)) {
                append("[").append(sp).append(',').append(n(g)).append(',').append(n(SubScores.wind(sp.toDouble(), g))).append("],")
            }
        }
    })
    out.append(",")

    val codes = listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 61, 63, 65, 80, 81, 82, 85, 86, 95, 96, 99, 7)
    out.append("\"weather\":")
    out.append(arr {
        for (code in codes) {
            for (rain in listOf(null, 0.3, 1.0, 3.0)) {
                for (cloud in listOf(null, 0.0, 55.0, 100.0)) {
                    append("[").append(code).append(',').append(n(rain)).append(',').append(n(cloud)).append(',')
                        .append(n(SubScores.weather(code, rain, cloud))).append("],")
                }
            }
        }
    })
    out.append(",")

    out.append("\"daypart\":")
    out.append(arr {
        for (h in 0..23) {
            for ((sr, ss) in listOf(null to null, 6.3 to 17.4, 5.0 to 19.0)) {
                for (night in listOf(false, true)) {
                    append("[").append(n(h.toDouble())).append(',').append(n(sr)).append(',').append(n(ss)).append(',')
                        .append(night).append(',').append(n(SubScores.daypart(h.toDouble(), sr, ss, night))).append("],")
                }
            }
        }
    })
    out.append(",")

    out.append("\"tempMatch\":")
    out.append(arr {
        for (tw in -5..35 step 5) {
            for ((opt, sig) in listOf(22.0 to 7.0, 20.0 to 6.0, 18.0 to 9.0)) {
                append("[").append(tw).append(',').append(n(opt)).append(',').append(n(sig)).append(',')
                    .append(n(SubScores.tempMatch(tw.toDouble(), opt, sig))).append("],")
            }
        }
    })
    out.append(",")

    out.append("\"comfort\":")
    out.append(arr { for (a in -10..40 step 5) append("[").append(a).append(',').append(n(SubScores.comfort(a.toDouble()))).append("],") })
    out.append(",")

    out.append("\"waterQuality\":")
    out.append(arr {
        for (so in listOf(0.0, 0.3, 0.6, 1.0)) {
            for (cloud in listOf(null, 20.0, 60.0, 100.0)) {
                for (rain in listOf(0.0, 5.0, 20.0)) {
                    for (tw in listOf(null, 15.0, 25.0)) {
                        append("[").append(n(so)).append(',').append(n(cloud)).append(',').append(n(rain)).append(',')
                            .append(n(tw)).append(',').append(n(WaterQualityProxy.score(so, cloud, rain, null, tw))).append("],")
                    }
                }
            }
        }
    })
    out.append("},")

    // ---------- Solunar ----------
    val epochs = listOf(1794560000L, 1794000000L, 1790400000L, 1762560000L)
    out.append("\"solunar\":{")
    out.append("\"age\":").append(arr {
        for (e in epochs) append("[").append(e).append(',').append(n(Solunar.lunarAge(e * 1000))).append("],")
    }).append(',')
    out.append("\"illumination\":").append(arr {
        for (a in listOf(0.0, 3.7, 7.4, 11.1, 14.8, 22.2, 29.5)) append("[").append(n(a)).append(',').append(n(Solunar.illumination(a))).append("],")
    }).append(',')
    out.append("\"transit\":").append(arr {
        for (age in listOf(0.0, 7.0, 14.8, 22.0)) for (lon in listOf(0.0, 120.38)) for (tz in listOf(8.0, 0.0)) {
            append("[").append(n(age)).append(',').append(n(lon)).append(',').append(n(tz)).append(',')
                .append(n(Solunar.lunarTransitClock(age, lon, tz))).append("],")
        }
    }).append(',')
    out.append("\"score\":").append(arr {
        for (h in 0..23) for (tr in listOf(0.0, 6.2, 12.4, 18.6)) for (age in listOf(0.4, 7.4, 14.8)) {
            append("[").append(h).append(',').append(n(tr)).append(',').append(n(age)).append(',')
                .append(n(Solunar.score(h.toDouble(), tr, age))).append("],")
        }
    })
    out.append("},")

    // ---------- DoPhysics / WaterTemp ----------
    out.append("\"doPhysics\":{")
    out.append("\"potential\":").append(arr {
        for (tw in listOf(0.0, 10.0, 15.0, 20.0, 25.0, 30.0)) for (p in listOf(990.0, 1013.25, 1030.0)) for (sal in listOf(0.0, 30.0)) {
            append("[").append(n(tw)).append(',').append(n(p)).append(',').append(n(sal)).append(',')
                .append(n(DoPhysics.doPotential(tw, p, sal))).append("],")
        }
    }).append(',')
    out.append("\"doRef\":").append(arr {
        for (tw in listOf(5.0, 10.0, 15.0, 20.0, 25.0, 30.0)) for (base in listOf(7.4, 7.6, 8.0)) {
            append("[").append(n(tw)).append(',').append(n(base)).append(',').append(n(DoPhysics.doRef(tw, base))).append("],")
        }
    }).append(',')
    out.append("\"oxygenScore\":").append(arr {
        for (dop in listOf(2.0, 4.0, 5.0, 6.0, 7.5, 9.0)) for (ref in listOf(5.2, 6.5, 8.5)) {
            append("[").append(n(dop)).append(',').append(n(ref)).append(',').append(n(DoPhysics.oxygenScore(dop, ref))).append("],")
        }
    })
    out.append("},")

    out.append("\"waterTemp\":")
    out.append(arr {
        val series = (0..40).map { 18.0 + 0.2 * it }
        for (idx in listOf(0, 1, 10, 30, 40)) {
            for (sst in listOf(null, 19.5)) {
                append("[").append(idx).append(',').append(n(sst)).append(',')
                    .append(n(WaterTempFusion.fuse(series, idx, 18.0, sst))).append("],")
            }
        }
        // 全缺测
        append("[").append(5).append(',').append("null,").append(n(WaterTempFusion.fuse(listOf(null, null), 5, null, null))).append("],")
    })
    out.append(",")

    // ---------- Veto ----------
    out.append("\"veto\":")
    out.append(arr {
        val wmoList = listOf(null, 2, 61, 95)
        val windList = listOf(null, 10.0, 34.0, 50.0)
        val gustList = listOf(null, 40.0, 50.0)
        val dp3List = listOf(null, -3.0, 0.0)
        val rainList = listOf(null, 1.0, 6.0)
        val waveList = listOf(null, 2.0)
        val doList = listOf(null, 3.0, 8.0)
        for (wmo in wmoList) for (wind in windList) for (gust in gustList) for (dp3 in dp3List)
            for (rain in rainList) for (wave in waveList) for (dop in doList) {
                val r = VetoEvaluator.evaluate(wmo, wind, gust, dp3, rain, wave, dop)
                append("[").append(n(wmo?.toDouble())).append(',').append(n(wind)).append(',').append(n(gust)).append(',')
                    .append(n(dp3)).append(',').append(n(rain)).append(',').append(n(wave)).append(',').append(n(dop)).append(',')
                    .append(n(r.value)).append(',').append(r.codes.size).append("],")
            }
    })
    out.append(",")

    // ---------- FishScoreCalculator ----------
    val baseEpoch = 1794560000L
    val wSeries = (0 until 25).map { i ->
        HourlyWeather(
            timeEpochSec = baseEpoch + i * 3600L,
            temperatureC = 18.0 + i * 0.2,
            pressureMslHpa = 1015.0 - i * 0.1,
            cloudCoverPct = 40.0,
            rainMm = if (i % 5 == 0) 0.2 else 0.0,
            showersMm = 0.0,
            weatherCode = if (i == 10) 61 else 2,
            windSpeedKmh = 14.0 + i * 0.3,
            windGustKmh = 22.0,
            apparentC = 17.0,
        )
    }
    val mSeries = (0 until 25).map { i ->
        HourlyMarine(
            timeEpochSec = baseEpoch + i * 3600L,
            waveHeightM = 0.8 + i * 0.01,
            seaLevelHeightMslM = 1.2 + 0.5 * sin(i * 0.5),
            seaSurfaceTempC = 19.5,
        )
    }

    fun dumpCase(name: String, preset: ScenePreset, marine: List<HourlyMarine>?) {
        val inputs = wSeries.indices.map { i ->
            FishScoreCalculator.HourlyScoreInput(
                timeEpochSec = wSeries[i].timeEpochSec,
                weather = wSeries[i],
                marine = marine?.getOrNull(i),
                sunriseLocalHour = 6.2,
                sunsetLocalHour = 17.3,
                tzOffsetHours = 8.0,
            )
        }
        val results = FishScoreCalculator().calculate(inputs, preset, 120.38)
        out.append('"').append(name).append("\":{")
        out.append("\"preset\":").append(s(preset.id)).append(',')
        out.append("\"lonDeg\":120.38,")
        out.append("\"inputs\":")
        out.append(arr {
            for (i in wSeries.indices) {
                append("[").append(wSeries[i].timeEpochSec).append(',')
                    .append(n(wSeries[i].temperatureC)).append(',')
                    .append(n(wSeries[i].pressureMslHpa)).append(',')
                    .append(n(wSeries[i].cloudCoverPct)).append(',')
                    .append(n(wSeries[i].rainMm)).append(',')
                    .append(n(wSeries[i].showersMm)).append(',')
                    .append(n(wSeries[i].weatherCode?.toDouble())).append(',')
                    .append(n(wSeries[i].windSpeedKmh)).append(',')
                    .append(n(wSeries[i].windGustKmh)).append(',')
                    .append(n(wSeries[i].apparentC)).append(',')
                    .append(n(marine?.getOrNull(i)?.waveHeightM)).append(',')
                    .append(n(marine?.getOrNull(i)?.seaLevelHeightMslM)).append(',')
                    .append(n(marine?.getOrNull(i)?.seaSurfaceTempC)).append("],")
            }
        }).append(',')
        out.append("\"results\":")
        out.append(arr {
            for (r in results) {
                append("[").append(r.timeEpochSec).append(',').append(r.total).append(',')
                    .append(s(r.confidence.name)).append(',')
                    .append(r.vetoCodes.size).append(',')
                    .append(r.factors.size).append(',')
                    .append(n(r.factors.sumOf { it.sub })).append("],")
            }
        })
        out.append('}')
    }

    out.append("\"fishScore\":{")
    dumpCase("seaShore", Presets.SEA_SHORE, mSeries)
    out.append(',')
    dumpCase("freshLure", Presets.FRESH_LURE, null)
    out.append("}")

    out.append('}')
    print(out.toString())
}