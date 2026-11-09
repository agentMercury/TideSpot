package com.tidespot.core.domain

import com.tidespot.core.domain.algorithm.ScenePreset
import com.tidespot.core.domain.algorithm.Solunar
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 测试辅助：解析 src/test/resources/golden 下的自洽对拍资源 + B3–B6 题面构造合成器。
 *
 * 资源由 fetch-golden.mjs 生成：同一响应既作逐小时输入又作参照分数（refScore），
 * 保证"输入 ↔ 期望"严格同源（规避 Open-Meteo 预报漂移导致的错位）。
 *
 * BLK-03 / M4 必修 2：原测试内复制品 composeScore() 已删除——场景黄金值一律经
 * [toProductionInputs] 构造序列后走 FishScoreCalculator 生产管线，编排改坏即测试变红。
 */
object GoldenData {
    data class GoldenRow(
        val time: String, val p: Double?, val ta: Double?, val wind: Double?, val gust: Double?,
        val rain: Double?, val cloud: Double?, val code: Int?, val apparent: Double?,
        val wave: Double?, val sl: Double?, val sst: Double?, val age: Double?, val refScore: Int?,
    )

    data class GoldenSpot(
        val name: String, val lon: Double, val presetId: String, val amp: Double,
        val sunrise: Map<String, Double>, val sunset: Map<String, Double>, val rows: List<GoldenRow>,
    ) {
        val preset: ScenePreset
            get() = if (presetId == "fresh") com.tidespot.core.domain.algorithm.Presets.FRESH_LURE
            else com.tidespot.core.domain.algorithm.Presets.SEA_SHORE
    }

    /** 加载 golden_$name.json（单行 JSON，字段定长格式，正则提取足够）。 */
    fun load(name: String): GoldenSpot {
        val text = GoldenData::class.java.getResourceAsStream("/golden/golden_$name.json")!!
            .readBytes().decodeToString()
        val lon = num(text, "lon")!!
        val amp = num(text, "amp")!!
        val presetId = Regex("\"preset\":\"(\\w+)\"").find(text)!!.groupValues[1]
        val sunrise = parseDayMap(text, "sr")
        val sunset = parseDayMap(text, "ss")
        val rows = ArrayList<GoldenRow>()
        // 按 {"t":"2026-..","P":...} 切分每个行对象
        val rowRegex = Regex("\\{\"t\":\"([^\"]+)\".*?\\}")
        for (m in rowRegex.findAll(text)) {
            val body = m.value
            rows.add(
                GoldenRow(
                    time = m.groupValues[1],
                    p = num(body, "P"), ta = num(body, "Ta"),
                    wind = num(body, "wind"), gust = num(body, "gust"),
                    rain = num(body, "rain"), cloud = num(body, "cloud"),
                    code = num(body, "code")?.toInt(), apparent = num(body, "apparent"),
                    wave = num(body, "wave"), sl = num(body, "sl"), sst = num(body, "sst"), age = num(body, "age"),
                    refScore = num(body, "score")?.toInt(),
                ),
            )
        }
        return GoldenSpot(name, lon, presetId, amp, sunrise, sunset, rows)
    }

    /** 加载 B3–B6 题面构造场景（scenarios.json，由 calc-ref.mjs 参照函数预验证）。 */
    data class Scenario(val key: String, val input: Map<String, Double?>, val refScore: Int, val golden: Int?)
    fun scenarios(): Map<String, Scenario> {
        val text = GoldenData::class.java.getResourceAsStream("/golden/scenarios.json")!!
            .readBytes().decodeToString()
        val out = LinkedHashMap<String, Scenario>()
        // 仅匹配场景键（B3_0600 / B3_gust_10 等），避免把 "input" 内嵌对象误当作块起点
        val blockRegex = Regex("\"(B\\d+_[A-Za-z0-9_]+)\"\\s*:\\s*\\{")
        val blocks = blockRegex.findAll(text).toList()
        for ((idx, m) in blocks.withIndex()) {
            val start = m.range.last + 1
            val end = if (idx + 1 < blocks.size) blocks[idx + 1].range.first else text.length
            val body = text.substring(start, end)
            val input = mapOf(
                "hour" to num(body, "hour"), "P" to num(body, "P"), "dP3" to num(body, "dP3"),
                "dP24" to num(body, "dP24"), "Tw" to num(body, "Tw"), "wind" to num(body, "wind"),
                "gust" to num(body, "gust"), "rain" to num(body, "rain"), "cloud" to num(body, "cloud"),
                "code" to num(body, "code"), "apparent" to num(body, "apparent"), "sr" to num(body, "sr"),
                "ss" to num(body, "ss"), "age" to num(body, "age"), "lon" to num(body, "lon"),
                "sl" to num(body, "sl"), "dh" to num(body, "dh"), "amp" to num(body, "amp"),
                "wave" to num(body, "wave"),
            )
            out[m.groupValues[1]] = Scenario(
                m.groupValues[1], input,
                num(body, "ref")!!.toInt(), num(body, "golden")?.toInt(),
            )
        }
        return out
    }

    private fun num(src: String, key: String): Double? =
        Regex("\"$key\":\\s*(-?\\d+(?:\\.\\d+)?|null)").find(src)?.groupValues?.get(1)?.toDoubleOrNull()

    private fun parseDayMap(text: String, key: String): Map<String, Double> {
        val m = Regex("\"$key\":\\{([^}]*)\\}").find(text) ?: return emptyMap()
        return Regex("\"(\\d{4}-\\d{2}-\\d{2})\":(-?\\d+(?:\\.\\d+)?)").findAll(m.groupValues[1])
            .associate { it.groupValues[1] to it.groupValues[2].toDouble() }
    }

    /** GoldenRow → 管线输入（tz 固定 +8，与参照实现一致）。 */
    fun toInput(row: GoldenRow, spot: GoldenSpot): FishScoreCalculator.HourlyScoreInput {
        val date = row.time.substring(0, 10)
        return FishScoreCalculator.HourlyScoreInput(
            timeEpochSec = LocalDateTime.parse(row.time).toEpochSecond(ZoneOffset.ofHours(8)),
            weather = HourlyWeather(
                timeEpochSec = 0,
                temperatureC = row.ta,
                pressureMslHpa = row.p,
                apparentC = row.apparent,
                windSpeedKmh = row.wind,
                windGustKmh = row.gust,
                cloudCoverPct = row.cloud,
                rainMm = row.rain,
                showersMm = 0.0,
                weatherCode = row.code,
            ),
            marine = if (spot.presetId == "fresh") null else HourlyMarine(
                timeEpochSec = 0,
                waveHeightM = row.wave,
                seaLevelHeightMslM = row.sl,
                seaSurfaceTempC = row.sst,   // §6.1.1 SST 优先；行内缺失时走 6.1.2 内陆模型
            ),
            sunriseLocalHour = spot.sunrise[date],
            sunsetLocalHour = spot.sunset[date],
            tzOffsetHours = 8.0,
        )
    }

    /**
     * B3–B6 题面场景 → 生产管线输入（M4 必修 2 / BLK-03：替代已删除的 composeScore）。
     *
     * 题面给的是"中间量"（hour/age/P/dP3/dP24/Tw/wind/gust/rain/cloud/code/apparent/sr/ss/
     * sl/dh/amp/wave），生产编排（FishScoreCalculator）只接受原始序列并自行推导中间量。
     * 本构造器保证推导结果与题面逐项一致：
     * - hour → epoch 取当地（tz +8）钟表时锚点，hourLocal 精确相等；
     * - age  → 月龄是 epoch 的确定函数，在整数天网格上搜索 lunarAge ≈ 题面 age 的 epoch
     *   （24h 推进 ≈1 天且带摄动，序列 mod 29.53 天稠密覆盖；4000 天网格残差 <0.01 天，
     *   transit 漂移 <0.5 min，对总分影响 <<1，处于 ±2 黄金容差内）；
     * - Tw   → 海水点走 SST 优先（§6.1.1）；内陆点恒温气温序列经谐波滞后+α=0.70 融合后
     *   精确还原题面 Tw（§6.1.2）；
     * - dP3/dP24 → 在 index−3 / index−24 处放置 P−dP3 / P−dP24；
     * - tide → index−1 = sl−dh（dh 精确），index−2/−3 放置中心±amp/2（amp 精确）。
     * 返回 (输入序列, 目标小时下标=48)。
     */
    fun toProductionInputs(
        sc: Scenario,
        preset: ScenePreset,
    ): Pair<List<FishScoreCalculator.HourlyScoreInput>, Int> {
        val s = sc.input
        val tw = s["Tw"]!!
        val p = s["P"]!!
        val dP3 = s["dP3"] ?: 0.0
        val dP24 = s["dP24"] ?: 0.0

        // —— hour → 当地钟表时锚点 epoch（tz +8，与参照实现一致）——
        val h = s["hour"]!!
        val hourInt = h.toInt()
        val minute = Math.round((h - hourInt) * 60.0).toInt()
        val anchorEpoch = LocalDateTime.of(2026, 1, 1, hourInt, minute).toEpochSecond(ZoneOffset.ofHours(8))

        // —— age → 整数天网格反解 epoch ——
        val targetAge = s["age"]!!
        val synodic = Solunar.SYNODIC_MONTH_DAYS
        fun circDist(d: Double): Double {
            val m = ((d % synodic) + synodic) % synodic
            return minOf(m, synodic - m)
        }
        var bestEpoch = anchorEpoch
        var bestErr = Double.MAX_VALUE
        for (k in 0..4000L) {
            val t = anchorEpoch + k * 86400L
            val err = circDist(Solunar.lunarAge(t * 1000) - targetAge)
            if (err < bestErr) {
                bestErr = err
                bestEpoch = t
            }
        }

        val targetIndex = 48   // ≥24 → dP24 可计算；≥3 → dP3 可计算；Tw 窗口 30h 齐备
        val sl = s["sl"]
        val dh = s["dh"] ?: 0.0
        val amp = s["amp"] ?: 0.0
        val wave = s["wave"]
        val center = if (sl != null) sl - dh / 2.0 else 0.0

        val inputs = (0..targetIndex).map { i ->
            val epoch = bestEpoch - (targetIndex - i) * 3600L
            val pressure = when (i) {
                targetIndex - 24 -> p - dP24
                targetIndex - 3 -> p - dP3
                else -> p
            }
            // sl != null = 海水场景（题面 B3/B6）；淡水（B4）整维 marine null
            val marine = if (sl == null) null else when (i) {
                targetIndex -> HourlyMarine(
                    timeEpochSec = epoch, waveHeightM = wave,
                    seaLevelHeightMslM = sl, seaSurfaceTempC = tw,   // §6.1.1 SST 优先 → Tw 精确
                )
                targetIndex - 1 -> HourlyMarine(timeEpochSec = epoch, seaLevelHeightMslM = sl - dh)
                targetIndex - 2 -> HourlyMarine(timeEpochSec = epoch, seaLevelHeightMslM = center + amp / 2.0)
                else -> HourlyMarine(timeEpochSec = epoch, seaLevelHeightMslM = center - amp / 2.0)
            }
            FishScoreCalculator.HourlyScoreInput(
                timeEpochSec = epoch,
                weather = HourlyWeather(
                    timeEpochSec = epoch,
                    temperatureC = tw,               // 恒温序列 → 谐波滞后融合精确还原题面 Tw
                    pressureMslHpa = pressure,
                    apparentC = s["apparent"],
                    windSpeedKmh = s["wind"],
                    windGustKmh = s["gust"],
                    cloudCoverPct = s["cloud"],
                    rainMm = s["rain"] ?: 0.0,       // 题面 rain 缺省 = 无雨（参照语义），非缺测
                    weatherCode = s["code"]?.toInt(),
                ),
                marine = marine,
                sunriseLocalHour = s["sr"],
                sunsetLocalHour = s["ss"],
                tzOffsetHours = 8.0,
            )
        }
        return inputs to targetIndex
    }
}
