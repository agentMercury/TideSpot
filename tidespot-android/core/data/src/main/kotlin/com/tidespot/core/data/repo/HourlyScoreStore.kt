package com.tidespot.core.data.repo

import com.tidespot.core.data.dto.MarineResponseDto
import com.tidespot.core.data.dto.OpenMeteoTime
import com.tidespot.core.data.dto.WeatherDailyDto
import com.tidespot.core.data.dto.WeatherResponseDto
import com.tidespot.core.data.local.HourlyScoreEntity
import com.tidespot.core.domain.FishScoreCalculator
import com.tidespot.core.domain.algorithm.Presets
import com.tidespot.core.domain.algorithm.ScenePreset
import com.tidespot.core.domain.model.FactorScore
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.model.VetoCode
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather
import com.tidespot.core.domain.model.WaterBodyType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * M4 必修 8 / 必修 4：评分编排支撑（§7.3 hourly_score / F-3.8 时区）。
 * 纯函数为主，可脱离网络与 Room 单测。
 */
object HourlyScoreStore {

    private val json = Json { ignoreUnknownKeys = true }
    private val reasonsSerializer = ListSerializer(String.serializer())

    // ──────────────────────────── FO-18：时区解析 ────────────────────────────

    /**
     * FO-18（M4 必修 4）：按响应换算选点时区偏移（秒）。天气优先（epoch 解析以天气 utcOffset
     * 为基准，评分必须与其一致）；`utc_offset_seconds` 优先（整窗权威），缺失回退 IANA `timezone`
     * 推算（CHG-09⑥ 同型）；两者都缺 → null（评分跳过，禁默认 +8）。
     */
    fun resolveTzOffsetSec(weather: WeatherResponseDto?, marine: MarineResponseDto?): Int? {
        weather?.let { w -> return w.utcOffsetSeconds ?: w.timezoneId.ianaOffsetSec() }
        marine?.let { m -> return m.utcOffsetSeconds ?: m.timezoneId.ianaOffsetSec() }
        return null
    }

    private fun String?.ianaOffsetSec(): Int? = this?.let { id ->
        runCatching { ZoneId.of(id).rules.getOffset(Instant.now()).totalSeconds }.getOrNull()
    }

    // ──────────────────────────── 评分输入构建 ────────────────────────────

    /**
     * 天气+海洋序列 → [FishScoreCalculator.HourlyScoreInput]（按时间戳 join，§5.0 禁下标对齐）。
     * sr/ss 由 daily 块按当地日期映射；tzOffsetHours **显式传入**（FO-18：禁依赖默认 +8）。
     */
    fun buildScoreInputs(
        weather: List<HourlyWeather>,
        marine: List<HourlyMarine>,
        daily: WeatherDailyDto?,
        tzOffsetSec: Int,
    ): List<FishScoreCalculator.HourlyScoreInput> {
        val zone = ZoneOffset.ofTotalSeconds(tzOffsetSec)
        val sunriseByDate = HashMap<String, Double>()
        val sunsetByDate = HashMap<String, Double>()
        daily?.time?.forEachIndexed { i, date ->
            daily.sunrise?.getOrNull(i)?.let { localIsoToHour(it)?.let { h -> sunriseByDate[date] = h } }
            daily.sunset?.getOrNull(i)?.let { localIsoToHour(it)?.let { h -> sunsetByDate[date] = h } }
        }
        val marineByEpoch = marine.associateBy { it.timeEpochSec }
        return weather.map { w ->
            val local = LocalDateTime.ofInstant(Instant.ofEpochSecond(w.timeEpochSec), zone)
            val date = local.toLocalDate().toString()
            FishScoreCalculator.HourlyScoreInput(
                timeEpochSec = w.timeEpochSec,
                weather = w,
                marine = marineByEpoch[w.timeEpochSec],
                sunriseLocalHour = sunriseByDate[date],
                sunsetLocalHour = sunsetByDate[date],
                tzOffsetHours = tzOffsetSec / 3600.0,
            )
        }
    }

    /** `"2026-11-02T06:30"` → 6.5（当地钟表小时）；脏串 → null（N-4 不抛异常）。 */
    private fun localIsoToHour(iso: String?): Double? {
        if (iso.isNullOrEmpty()) return null
        return runCatching {
            val t = LocalDateTime.parse(iso)
            t.hour + t.minute / 60.0
        }.getOrNull()
    }

    /**
     * 场景预设唯一解析（CHG-13/D03 最小止血，OPT-51 完整解留 v1.1）：
     * 固定三级顺序 **英文 id → 中文标签子串 → 水体类型兜底**。
     * `:core:data` 落库与 `:feature:detail` UI 派生**必须共用本函数**，
     * 禁止任何一侧另行实现解析（否则同一收藏点出现两套分数）。
     */
    fun resolvePreset(spot: FavoriteSpot): ScenePreset {
        val byId = spot.preset?.let { Presets.byId(it) }
        if (byId != null) return byId
        val byLabel = when {
            spot.preset?.contains("海水") == true -> Presets.SEA_SHORE
            spot.preset?.contains("台钓") == true -> Presets.FRESH_BAIT
            spot.preset?.contains("路亚") == true -> Presets.FRESH_LURE
            else -> null
        }
        if (byLabel != null) return byLabel
        return if (spot.waterBody == WaterBodyType.OCEAN || spot.waterBody == WaterBodyType.NEARSHORE) {
            Presets.SEA_SHORE
        } else {
            Presets.FRESH_LURE
        }
    }

    /**
     * 场景预设（§6.4）：spot.preset 字符串（淡水路亚/海水岸边/淡水台钓）优先，
     * 缺省按水体类型兜底（OCEAN/NEARSHORE → 海水，其余 → 淡水路亚）。
     * CHG-13/D03：内部委托 [resolvePreset]（唯一解析口径）。
     */
    fun presetFor(spot: FavoriteSpot): ScenePreset = resolvePreset(spot)

    // ──────────────────────────── 落库映射（CHG-08 ④） ────────────────────────────

    /**
     * 领域 vetoReasons（非空 List，无理由 = 空列表）→ SQL 可空列：
     * **空列表 → NULL**；非空 → JSON 数组串（含中文/数字，结构化无损往返）。
     */
    fun vetoReasonsToColumn(reasons: List<String>): String? =
        if (reasons.isEmpty()) null else json.encodeToString(reasonsSerializer, reasons)

    /** SQL 列 → 领域语义：NULL/脏串 → **空列表**（CHG-08 ④：领域层永不出现 null 列表）。 */
    fun columnToVetoReasons(column: String?): List<String> = column?.let { col ->
        runCatching { json.decodeFromString(reasonsSerializer, col) }.getOrDefault(emptyList())
    } ?: emptyList()

    // ──────────────────── M6 CHG-16①：机器可读否决码列（veto_codes NOT NULL DEFAULT '[]'） ────────────────────

    /**
     * 领域 vetoCodes → SQL 列（**NOT NULL**，空列表 → `'[]'`，与 vetoReason NULL 语义对称；
     * 元素 = VetoCode.name 的 JSON 数组串）。
     */
    fun vetoCodesToColumn(codes: List<VetoCode>): String =
        if (codes.isEmpty()) "[]" else json.encodeToString(reasonsSerializer, codes.map { it.name })

    /**
     * SQL 列 → 领域语义：NULL/脏串/未知名 → **空列表**（对空数组 '[]' 与 v3 旧行 DEFAULT '[]'
     * 同语义；未知枚举名跳过而非抛异常，向前兼容新增码）。
     */
    fun columnToVetoCodes(column: String?): List<VetoCode> = column?.let { col ->
        runCatching {
            json.decodeFromString(reasonsSerializer, col).mapNotNull { name ->
                runCatching { VetoCode.valueOf(name) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    } ?: emptyList()

    /** §6.3 档位（与 UI 色带一致；hourly_score.band）。 */
    fun band(score: Int): String = when {
        score >= 80 -> "爆护窗口"
        score >= 65 -> "很适合"
        score >= 50 -> "可以"
        score >= 35 -> "较差"
        else -> "别去"
    }

    /** 评分结果 → hourly_score 行（hourLocal = 当地墙串；algoVersion 入键，N-10）。 */
    fun toEntities(
        spotId: Long,
        inputs: List<FishScoreCalculator.HourlyScoreInput>,
        results: List<FishScoreResult>,
        tzOffsetSec: Int,
    ): List<HourlyScoreEntity> = results.mapIndexedNotNull { i, r ->
        val input = inputs.getOrNull(i) ?: return@mapIndexedNotNull null
        HourlyScoreEntity(
            spotId = spotId,
            hourEpoch = r.timeEpochSec,
            hourLocal = OpenMeteoTime.localIso(r.timeEpochSec, tzOffsetSec),
            score = r.total.toDouble(),
            band = band(r.total),
            vetoReason = vetoReasonsToColumn(r.vetoReasons),
            // CHG-16①：机器码随行落库（读侧 topWindows 重建同参对称解析）
            vetoCodes = vetoCodesToColumn(r.vetoCodes),
            factorsJson = factorsToJson(r.factors),
            algoVersion = FishScoreCalculator.ALGO_VERSION,
        )
    }

    /** FactorScore → JSON 数组串（F-3.4 因子明细；domain 无 @Serializable，此处手工组装）。 */
    private fun factorsToJson(factors: List<FactorScore>): String = buildJsonArray {
        factors.forEach { f ->
            add(
                kotlinx.serialization.json.buildJsonObject {
                    put("id", f.id)
                    put("sub", f.sub)
                    put("weight", f.weight)
                    f.detail?.let { put("detail", it) }
                }
            )
        }
    }.toString()
}
