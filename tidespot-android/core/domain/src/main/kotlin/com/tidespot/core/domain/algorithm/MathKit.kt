package com.tidespot.core.domain.algorithm

/**
 * 数学工具集（SRS §6.2 工具函数）。
 * 与参照实现 fishscore.mjs 的同名函数逐行等价（M1 对拍基线，禁止改名改义）。
 */
object MathKit {

    /** 截断到 [0,1]（SRS §6.2 clip01）。 */
    fun clip01(x: Double): Double = x.coerceIn(0.0, 1.0)

    /** 高斯核 gauss(x,μ,σ)=exp(−0.5·((x−μ)/σ)²)（SRS §6.2）。 */
    fun gauss(x: Double, mu: Double, sigma: Double): Double =
        kotlin.math.exp(-0.5 * ((x - mu) / sigma) * ((x - mu) / sigma))

    /** 梯形隶属度 trap(x,a,b,c,d)：x<=a||x>=d→0；x<b→线性升；x<=c→1；否则线性降（SRS §6.2）。 */
    fun trap(x: Double, a: Double, b: Double, c: Double, d: Double): Double = when {
        x <= a || x >= d -> 0.0
        x < b -> (x - a) / (b - a)
        x <= c -> 1.0
        else -> (d - x) / (d - c)
    }

    /** 连续衰减 sigmoid sig(x,mid,k)=1/(1+exp(k·(x−mid)))：x 越大越不利（SRS §6.5，禁止硬阈值）。 */
    fun sig(x: Double, mid: Double, k: Double): Double =
        1.0 / (1.0 + kotlin.math.exp(k * (x - mid)))

    /** JS 风格取余（保留被除数符号），供天文周期取模用。 */
    fun jsMod(x: Double, m: Double): Double {
        val r = x % m
        return if (r < 0) r + m else r
    }
}
