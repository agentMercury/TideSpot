package com.tidespot.core.domain.water

/**
 * 溶氧物理（SRS §6.2a，附录 B1 黄金值，容差 ±0.05 mg/L）。
 * 黄金值：(10, 1013.25, 0)=11.25 | (25, 1013.25, 0)=8.27 | (25, 990, 0)=8.07 | (25, 1013.25, 30)=6.41
 */
object DoPhysics {

    /** 饱和溶解度 Cs(Tw,S)=(468/(31.6+Tw))·(1−0.0075·S) mg/L；S=盐度‰（淡水 0，海水 30）。 */
    fun doSaturation(waterTempC: Double, salinityPpt: Double): Double =
        (468.0 / (31.6 + waterTempC)) * (1.0 - 0.0075 * salinityPpt)

    /** 饱和水汽压 pw(Tw)=6.1078·exp(17.27·Tw/(Tw+237.3)) hPa。 */
    fun vaporPressure(waterTempC: Double): Double =
        6.1078 * kotlin.math.exp(17.27 * waterTempC / (waterTempC + 237.3))

    /**
     * 水体溶氧"潜力"（平衡态估计，未计生物耗氧）：
     * DO_p = Cs · (P − pw) / (1013.25 − pw)，P = 海平面气压 hPa。
     */
    fun doPotential(waterTempC: Double, pressureMslHpa: Double, salinityPpt: Double): Double {
        val pw = vaporPressure(waterTempC)
        val f = (pressureMslHpa - pw) / (1013.25 - pw)
        return doSaturation(waterTempC, salinityPpt) * f
    }

    /**
     * 自适应满分门槛 DO_ref = clamp(5.2, 8.5, base − 0.05·(Tw−15))：
     * 暖水鱼代谢更高但饱和值更低，故随水温下调"好"的门槛（[doBase] 来自物种预设）。
     */
    fun doRef(waterTempC: Double, doBase: Double): Double =
        (doBase - 0.05 * (waterTempC - 15.0)).coerceIn(5.2, 8.5)

    /** s_oxygen = clip01((DO_p − 4.0)/(DO_ref − 4.0))；<4 mg/L → 0（4 mg/L 以下基本停口）。 */
    fun oxygenScore(doPotentialMgL: Double, doRefMgL: Double): Double =
        MathKitClip01.clip01((doPotentialMgL - 4.0) / (doRefMgL - 4.0))
}

/** water 包内部对 clip01 的转发引用（避免 water→algorithm 反向依赖噪音，仅此一处）。 */
internal object MathKitClip01 {
    fun clip01(x: Double): Double = x.coerceIn(0.0, 1.0)
}
