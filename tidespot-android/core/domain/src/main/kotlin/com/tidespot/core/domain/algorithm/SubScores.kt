package com.tidespot.core.domain.algorithm

/**
 * 单因子子分函数集（SRS §6.2 全部小节；与 fishscore.mjs 第 3 节逐行等价）。
 * 全部返回 [0,1]；输入的缺失语义由调用方（FishScoreCalculator）按 §6.7 处理。
 */
object SubScores {

    /** (a) 溶氧潜力子分 → 见 water.DoPhysics.oxygenScore，此处不重复实现。 */

    /** (b) 潮汐子分（海水专用）：涨落率 + 潮差，平潮"停流"×0.72（SRS §6.2b）。 */
    fun tide(dhPerHour: Double, tidalRangeM: Double): Double {
        val moving = MathKit.clip01(kotlin.math.abs(dhPerHour) / 0.16)   // m/h
        var v = 0.22 + 0.58 * moving + 0.20 * MathKit.clip01(tidalRangeM / 1.8)
        if (kotlin.math.abs(dhPerHour) < 0.02) v *= 0.72                 // 平潮/满潮"停流"
        return MathKit.clip01(v)
    }

    /**
     * (d) 气压子分：趋势(dP3) + 绝对值高斯 + 24h 相对项（不得只用绝对气压跨地区判定，§6.2d）。
     * [dP3] 缺 past（无法计算 ΔP3，§6.7）时传 null：内部按 §6.5 修正 2 的平台值 0.0 处理
     * （trend 项落到中性点 0.55），与旧实现"dP3 置 0"的子分数值对拍一致——信息缺失由调用方的
     * 权重降级（0.14→0.08）与"趋势数据不足"标注承担，子分函数本身不引入新行为。
     */
    fun pressure(pressureHpa: Double, dP3: Double?, dP24: Double): Double {
        val d3 = dP3 ?: 0.0   // 缺 past → 平台值（§6.5 修正 2 / §6.7），非"缺测当 0"的新语义
        val trend = if (d3 >= 0) 0.55 + MathKit.clip01(d3 / 5) * 0.45
        else 0.55 - MathKit.clip01(-d3 / 6) * 0.55
        return MathKit.clip01(
            0.45 * trend + 0.32 * MathKit.gauss(pressureHpa, 1020.0, 10.0) +
                0.23 * MathKit.clip01(0.5 + dP24 / 12.0),
        )
    }

    /** (c) Solunar 子分 → 见 algorithm.Solunar.score，此处不重复实现。 */

    /** (e) 风子分：溶氧扰动 + 可抛投，阵风乘性惩罚（35→×0.8，45→×0.45）。 */
    fun wind(windSpeedKmh: Double, gustKmh: Double?): Double {
        val ideal = MathKit.gauss(windSpeedKmh, 15.0, 13.0)
        val castable = if (windSpeedKmh <= 28) 1.0 else MathKit.clip01(1 - (windSpeedKmh - 28) / 20)
        val gustPen = when {
            gustKmh == null -> 1.0          // 缺阵风 → 不惩罚（§6.7：缺值不重罚）
            gustKmh > 45 -> 0.45
            gustKmh > 35 -> 0.8
            else -> 1.0
        }
        return MathKit.clip01((0.55 * ideal + 0.45 * castable) * gustPen)
    }

    /**
     * (f) 天气现象子分：WMO 基值 + 小雨 bonus + 云量修正（SRS §6.2f 含 85/86 雪码）。
     * [rainMmPerH] 缺测（雨量各分量全 null）时传 null：**不加小雨 bonus 也不罚**（§6.5/CHG-07
     * 修复 1：缺测小时不得误加降雨 bonus）；有值时行为与旧实现完全一致。
     */
    fun weather(wmoCode: Int, rainMmPerH: Double?, cloudCoverPct: Double?): Double {
        val base = when (wmoCode) {
            0 -> 1.00; 1 -> 0.97; 2 -> 0.92; 3 -> 0.86; 45 -> 0.55; 48 -> 0.45
            51 -> 0.82; 53 -> 0.70; 55 -> 0.50
            61 -> 0.62; 63 -> 0.44; 65 -> 0.22
            80 -> 0.64; 81 -> 0.46; 82 -> 0.28
            85 -> 0.25; 86 -> 0.20
            95, 96, 99 -> 0.0
            else -> 0.70
        }
        val lightRain = wmoCode in 51..65 || wmoCode in 80..82
        val bonus = if (lightRain && rainMmPerH != null) {
            when {
                rainMmPerH < 0.6 -> 0.12
                rainMmPerH < 2.0 -> 0.04
                else -> -0.12
            }
        } else 0.0
        val cloudTerm = cloudCoverPct?.let { 0.12 * MathKit.gauss(it, 55.0, 45.0) } ?: 0.0
        return MathKit.clip01(base + bonus + cloudTerm)
    }

    /**
     * (g) 时段子分：晨昏高、正午低、夜钓型夜间抬升（SRS §6.2g）。
     * [sunriseH]/[sunsetH] 当地日出日落小时；null 时用参照实现兜底 6.3/17.4。
     */
    fun daypart(hourLocal: Double, sunriseH: Double?, sunsetH: Double?, nightTarget: Boolean): Double {
        val sr = sunriseH ?: 6.3
        val ss = sunsetH ?: 17.4
        val cp = maxOf(MathKit.gauss(hourLocal, sr + 0.4, 1.1), MathKit.gauss(hourLocal, ss - 0.3, 1.1))
        val daytime = hourLocal > sr + 1.5 && hourLocal < ss - 1.5
        val night = hourLocal < sr - 1 || hourLocal > ss + 1.2
        var v = 0.30 + 0.60 * cp
        if (daytime) v = 0.30 + 0.20 * (1 - cp)
        if (night) v = if (nightTarget) 0.55 + 0.25 * cp else 0.28
        return MathKit.clip01(v + 0.20 * cp)
    }

    /** (h) 适温子分 = clip01(gauss(Tw,T_opt,σ_T)+0.04)（物种预设 §6.8）。 */
    fun tempMatch(waterTempC: Double, optC: Double, sigmaC: Double): Double =
        MathKit.clip01(MathKit.gauss(waterTempC, optC, sigmaC) + 0.04)

    /** (i) 人体感子分 = clip01(gauss(apparent,22,11)·0.9+0.10)。 */
    fun comfort(apparentC: Double): Double =
        MathKit.clip01(MathKit.gauss(apparentC, 22.0, 11.0) * 0.9 + 0.10)

    /** (j) 水质代理 turbidityIdx（强降雨→浑水；SRS §6.2j）。[rain24h] 为前 24 h 降水累计 mm。 */
    fun turbidityIndex(cloudCoverPct: Double?, rain24hMm: Double): Double =
        MathKit.clip01(0.5 + (cloudCoverPct?.let { 0.25 * MathKit.gauss(it, 60.0, 40.0) } ?: 0.0) - 0.05 * rain24hMm)

    /** (j) 叶绿素代理：有卫星 CHL 用 (CHL−0.05)/0.35；无则由水温+光照推导（标"估算"）。 */
    fun chlorophyllIndex(chlMgm3: Double?, waterTempC: Double?, cloudCoverPct: Double?): Double =
        chlMgm3?.let { MathKit.clip01((it - 0.05) / 0.35) }
            ?: MathKit.clip01(
                0.35 * (waterTempC?.let { MathKit.gauss(it, 22.0, 12.0) } ?: 0.5) +
                    (cloudCoverPct?.let { 0.3 * MathKit.gauss(it, 55.0, 45.0) } ?: 0.15),
            )

    /** (j) 水质综合 s_water = 0.55·s_oxygen + 0.25·turbidityIdx + 0.20·chlIdx。 */
    fun waterQuality(sOxygen: Double, turbidityIdx: Double, chlIdx: Double): Double =
        0.55 * sOxygen + 0.25 * turbidityIdx + 0.20 * chlIdx
}
