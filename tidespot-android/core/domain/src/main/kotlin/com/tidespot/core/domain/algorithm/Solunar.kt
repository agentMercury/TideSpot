package com.tidespot.core.domain.algorithm

/**
 * 本地天文：月龄 / 月上中天 / 照明度 / Solunar 子分（SRS §6.2c）。
 * 公式为 Meeus 简化（与 fishscore.mjs 第 2、3 节逐行等价），零外部依赖。
 * 黄金值：附录 B2（2026-11-09 新月 → 月龄 ≈ 0.4 天，容差 ±0.6 天）。
 */
object Solunar {

    /** 朔望月长度（天）。 */
    const val SYNODIC_MONTH_DAYS: Double = 29.530588861

    /**
     * 月龄（天）。[utcMillis] 为该时刻的真实 UTC 毫秒
     * （参照实现将本地墙钟 +08:00 解析为 UTC 时刻后计算，此处语义一致）。
     */
    fun lunarAge(utcMillis: Long): Double {
        val d = utcMillis / 86400000.0 + 2440587.5 - 2451545.0
        val rad = Math.PI / 180.0
        val Ls = 280.460 + 0.9856474 * d                       // 太阳平黄经
        val Ms = (357.529 + 0.9856003 * d) * rad               // 太阳平近点角
        val lamS = Ls + 1.915 * Math.sin(Ms) + 0.020 * Math.sin(2 * Ms)
        val Lm = 218.316 + 13.176396 * d                       // 月球平黄经
        val Mm = (134.963 + 13.064993 * d) * rad               // 月球平近点角
        val Dm = (Lm - lamS) * rad                             // 月日平距差
        val lamM = Lm + 6.289 * Math.sin(Mm) + 2.100 * Math.sin(Dm) -
            1.274 * Math.sin((2 * (Lm - lamS) - Mm) * rad) + 0.658 * Math.sin(2 * Dm) -
            0.186 * Math.sin(Ms) - 0.114 * Math.sin(2 * (Lm - 13.915) * rad)
        var e = (lamM - lamS) % 360.0
        if (e < 0) e += 360.0
        return e / 360.0 * SYNODIC_MONTH_DAYS
    }

    /** 月相照明度 illum=(1−cos(2π·age/29.5306))/2，新月 0、满月 1，关于 age 单调分段。 */
    fun illumination(ageDays: Double): Double =
        (1.0 - Math.cos(2 * Math.PI * ageDays / SYNODIC_MONTH_DAYS)) / 2.0

    /**
     * 月上中天的"钟表时"（小时）：LMT 正午 + 50.47 min × 月龄，再换算到点所在时区；
     * 对 24.8412（太阴日）取模。FO-18（M4 必修 4）：tzOffsetHours **无默认值**，
     * 调用侧按选点时区显式传值（参照实现基线 +8 由调用侧给出）。
     */
    fun lunarTransitClock(ageDays: Double, lonDeg: Double, tzOffsetHours: Double): Double {
        val lmt = 12.0 + (50.47 / 60.0) * ageDays
        val clock = lmt + (tzOffsetHours - lonDeg / 15.0)
        val P = 24.8412
        return ((clock % P) + P) % P
    }

    /**
     * Solunar 子分（[0,1]，SRS §6.2c）。
     * [hourLocal] 当地钟表小时；[transitHour] 月上中天钟表时；[ageDays] 月龄。
     */
    fun score(hourLocal: Double, transitHour: Double, ageDays: Double): Double {
        val P = 12.4206
        val dl = MathKit.jsMod(hourLocal + 0.5 - transitHour, P)
        val majDist = minOf(dl, P - dl)                        // 距主窗口（中天前后）的弧长
        val minDist = kotlin.math.abs(dl - P / 2)              // 距次窗口（中天相位对面）的弧长
        val base = maxOf(
            MathKit.trap(majDist, -0.2, 0.5, 1.2, 2.1),
            MathKit.trap(minDist, 0.15, 0.7, 1.1, 1.7) * 0.75,
        )
        return MathKit.clip01(base * (0.55 + 0.45 * illumination(ageDays)) + 0.05)
    }
}
