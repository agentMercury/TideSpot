package com.tidespot.core.domain.algorithm

import com.tidespot.core.domain.model.WaterBodyType

/**
 * 场景预设权重与参数（SRS §6.4 表；JSON 资源化属 :app 职责，此处为内置缺省）。
 * 规则：因子为 null 时从分母剔除（不是给 0 分，SRS §6.4）。
 */
data class ScenePreset(
    val id: String,
    val label: String,
    val salinityPpt: Double,        // S：淡水 0 / 海水 30
    val nightTarget: Boolean,       // 夜钓型物种（影响 daypart）
    val tempOptC: Double,           // T_opt
    val tempSigmaC: Double,         // σ_T
    val doBase: Double,             // DO_base（DO_ref 的基准）
    val weights: Map<FactorId, Double>,
) {
    /** 权重总和（用于低置信判定与归一）。 */
    val weightSum: Double get() = weights.values.sum()
}

/** 因子标识（与 SRS §6.4 表行名一致）。 */
enum class FactorId { OXYGEN, PRESSURE, SOLUNAR, TIDE, WIND, WEATHER, DAYPART, TEMP_MATCH, COMFORT, WATER_QUALITY }

object Presets {

    /** 淡水·路亚（鲈/鳜/翘嘴），SRS §6.4 列 1。 */
    val FRESH_LURE = ScenePreset(
        id = "fresh_lure", label = "淡水·路亚",
        salinityPpt = 0.0, nightTarget = false, tempOptC = 22.0, tempSigmaC = 7.0, doBase = 8.0,
        weights = mapOf(
            FactorId.OXYGEN to 0.21, FactorId.PRESSURE to 0.14, FactorId.SOLUNAR to 0.17,
            FactorId.WIND to 0.10, FactorId.WEATHER to 0.10, FactorId.DAYPART to 0.08,
            FactorId.TEMP_MATCH to 0.14, FactorId.COMFORT to 0.06,
        ),
    )

    /** 海水·岸边/矶钓（黑鲷/海鲈），SRS §6.4 列 2（tide 权重最高 0.24）。 */
    val SEA_SHORE = ScenePreset(
        id = "sea_shore", label = "海水·岸边/矶钓",
        salinityPpt = 30.0, nightTarget = true, tempOptC = 20.0, tempSigmaC = 6.0, doBase = 7.4,
        weights = mapOf(
            FactorId.OXYGEN to 0.14, FactorId.PRESSURE to 0.11, FactorId.SOLUNAR to 0.13,
            FactorId.TIDE to 0.24, FactorId.WIND to 0.11, FactorId.WEATHER to 0.09,
            FactorId.DAYPART to 0.06, FactorId.TEMP_MATCH to 0.06, FactorId.COMFORT to 0.06,
        ),
    )

    /** 淡水·台钓（鲫鱼），SRS §6.4 列 3。 */
    val FRESH_BAIT = ScenePreset(
        id = "fresh_bait", label = "淡水·台钓",
        salinityPpt = 0.0, nightTarget = false, tempOptC = 18.0, tempSigmaC = 9.0, doBase = 7.6,
        weights = mapOf(
            FactorId.OXYGEN to 0.22, FactorId.PRESSURE to 0.13, FactorId.SOLUNAR to 0.16,
            FactorId.WIND to 0.10, FactorId.WEATHER to 0.11, FactorId.DAYPART to 0.10,
            FactorId.TEMP_MATCH to 0.12, FactorId.COMFORT to 0.06,
        ),
    )

    fun byId(id: String): ScenePreset? = listOf(FRESH_LURE, SEA_SHORE, FRESH_BAIT).firstOrNull { it.id == id }
}

// ───────────────────────── OPT-19/M6（CHG-16）：8 物种预设库 ─────────────────────────

/**
 * 物种预设（OPT-19/M6）：物种维度对场景预设权重的**覆盖层**。
 * 规则：[weightOverrides] 仅覆盖出现的因子，未出现的因子沿用场景预设（[Presets]）权重；
 * [waterBodies] 为适用水体（UI 侧过滤可选物种）。场景预设解析唯一入口仍是
 * `HourlyScoreStore.resolvePreset`（硬约束 6，本类不参与场景解析，零行为漂移）。
 */
data class SpeciesPreset(
    val speciesId: String,
    val name: String,
    /** 适用水体类型（WaterBodyType.name）。 */
    val waterBodies: Set<WaterBodyType>,
    /** 权重覆盖（仅覆盖出现的因子；FactorId.name → 权重）。 */
    val weightOverrides: Map<FactorId, Double>,
)

object SpeciesPresets {

    private const val RESOURCE = "/species_presets.json"

    /**
     * OPT-19 加载入口：读 classpath `species_presets.json`（8 物种）。
     * 资源缺失/脏 JSON → 空列表（N-4 不抛异常，禁让预设问题炸评分管线）。
     */
    fun load(): List<SpeciesPreset> = runCatching {
        val text = SpeciesPresets::class.java.getResourceAsStream(RESOURCE)
            ?.readBytes()?.toString(Charsets.UTF_8) ?: return emptyList()
        parse(text)
    }.getOrDefault(emptyList())

    /** 按 speciesId 查找（供 UI 设置页/收藏编辑按物种选预设；未命中 = null）。 */
    fun byId(id: String): SpeciesPreset? = load().firstOrNull { it.speciesId == id }

    /** 文本 → [SpeciesPreset] 列表（公开供锁测试直接喂串；脏行跳过，不抛异常）。 */
    fun parse(text: String): List<SpeciesPreset> {
        val root = MiniJson.parse(text) as? MiniJson.Obj ?: return emptyList()
        val arr = root.map["species"] as? MiniJson.Arr ?: return emptyList()
        return arr.items.mapNotNull { el ->
            val o = el as? MiniJson.Obj ?: return@mapNotNull null
            val id = (o.map["speciesId"] as? MiniJson.Str)?.v ?: return@mapNotNull null
            val name = (o.map["name"] as? MiniJson.Str)?.v ?: return@mapNotNull null
            val bodies = ((o.map["waterBodies"] as? MiniJson.Arr)?.items ?: emptyList())
                .mapNotNull { s -> (s as? MiniJson.Str)?.v?.let { runCatching { WaterBodyType.valueOf(it) }.getOrNull() } }
                .toSet()
            val overrides = ((o.map["weightOverrides"] as? MiniJson.Obj)?.map ?: emptyMap())
                .mapNotNull { (k, v) ->
                    val f = runCatching { FactorId.valueOf(k) }.getOrNull() ?: return@mapNotNull null
                    val w = (v as? MiniJson.Num)?.v ?: return@mapNotNull null
                    f to w
                }.toMap()
            SpeciesPreset(id, name, bodies, overrides)
        }
    }
}

/**
 * 内部极小 JSON 解析（OPT-19 专用；domain 纯 Kotlin 无 kotlinx-serialization 依赖，
 * 沙箱禁新增依赖——按 M6 子契约手写）。仅支持本资源所需子集：
 * 对象/数组/字符串（含 \" \\ \/ \b \f \n \r \t \uXXXX 转义）/数字/true/false/null。
 */
internal object MiniJson {

    sealed class Node
    class Obj(val map: Map<String, Node>) : Node()
    class Arr(val items: List<Node>) : Node()
    class Str(val v: String) : Node()
    class Num(val v: Double) : Node()
    class Bool(val v: Boolean) : Node()
    object Null : Node()

    fun parse(text: String): Node {
        val p = P(text)
        p.skipWs()
        val n = p.value()
        p.skipWs()
        return n
    }

    private class P(private val s: String) {
        private var i = 0

        fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun value(): Node {
            when (s[i]) {
                '{' -> {
                    i++
                    val m = LinkedHashMap<String, Node>()
                    skipWs()
                    if (s[i] == '}') { i++; return Obj(m) }
                    while (true) {
                        skipWs()
                        val k = str()
                        skipWs()
                        expect(':')
                        skipWs()
                        m[k] = value()
                        skipWs()
                        when (s[i]) {
                            ',' -> i++
                            '}' -> { i++; return Obj(m) }
                            else -> throw IllegalArgumentException("JSON 语法错误@${'$'}i")
                        }
                    }
                }
                '[' -> {
                    i++
                    val items = ArrayList<Node>()
                    skipWs()
                    if (s[i] == ']') { i++; return Arr(items) }
                    while (true) {
                        skipWs()
                        items.add(value())
                        skipWs()
                        when (s[i]) {
                            ',' -> i++
                            ']' -> { i++; return Arr(items) }
                            else -> throw IllegalArgumentException("JSON 语法错误@${'$'}i")
                        }
                    }
                }
                '"' -> return Str(str())
                't' -> { i += 4; return Bool(true) }
                'f' -> { i += 5; return Bool(false) }
                'n' -> { i += 4; return Null }
                else -> return Num(number())
            }
        }

        private fun expect(c: Char) {
            if (i >= s.length || s[i] != c) throw IllegalArgumentException("JSON 语法错误：期望 $c@${'$'}i")
            i++
        }

        fun str(): String {
            if (s[i] != '"') throw IllegalArgumentException("JSON 语法错误：期望字符串@${'$'}i")
            i++
            val sb = StringBuilder()
            while (true) {
                val c = s[i]
                i++
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                            else -> throw IllegalArgumentException("非法转义 @$e")
                        }
                        i++
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }
    }
}