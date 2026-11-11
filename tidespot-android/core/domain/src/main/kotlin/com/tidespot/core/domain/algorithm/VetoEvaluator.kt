package com.tidespot.core.domain.algorithm

import com.tidespot.core.domain.model.VetoCode
import com.tidespot.core.domain.strings.DomainCopy
import com.tidespot.core.domain.strings.ZhDomainCopy

/**
 * 安全/可作钓性衰减 ΠV（SRS §6.5）——连续 sigmoid，**禁止硬阈值**。
 * 三条修正经验（§6.5 必须遵守）：
 * 1. 不得用硬阈值乘子（54.7→55.0 km/h 曾出现 50→12 分的荒谬跳变）；
 * 2. ΔP3 惩罚起点 ≥ −2.5 hPa/3h（正常午后日变化 −3.0 不得误杀）；
 * 3. "无否决原因"必须存**空列表**，判定用 `isEmpty()`（§6.5 修正 3 / CHG-08：非空 `List<String>` + `isEmpty()` 是 Kotlin 明确安全的惯用法，禁止可空与 == null 判定；如需区分「无否决 vs 未计算」走新增字段表达）。
 *
 * 缺测输入的安全侧处理（FO-11/OPT-02，CHG-07 P1 修复 1）——所有否决类输入可空，缺测
 * **禁止按"良态 0 值"处理**（旧实现 `?: 0.0` 会让缺测小时漏掉安全衰减、误加降雨 bonus）。
 * 保守档位取值与理由（写入实现处注释）：
 * - wind 缺测 → 34.0 km/h（衰减 sigmoid 拐点，V 项 ×0.50）；
 * - gust 缺测 → 45.0 km/h（衰减 sigmoid 拐点，V 项 ×0.50）；
 * - dP3 缺测 → −2.5 hPa/3h（惩罚起点，V 项 ×1.00——信息缺失已由 §6.7 权重降级 0.14→0.08 承担，
 *   再叠加衰减会违反修正 2"日变化量级不得误杀"对未知数据的保护；−2.5 是"不视为无变化"的
 *   最保守且不误杀的点）；
 * - rain 缺测 → 6.0 mm/h（衰减坡 [2,10] 中点，V 项 ×0.65——缺雨量计读数时不得假设无雨）；
 * - wave/DO/wmoCode 缺测维持原语义（内陆无浪项 / DO 缺测跳过 / 无雷暴信息不硬否决，§6.5 原文）。
 */
object VetoEvaluator {

    /**
     * 单小时否决结果：[value] ∈ (0,1] 为 ΠV；[reasons] 为可显示的否决/衰减原因，
     * **无理由时为空列表**（§6.5 修正 3 / CHG-08：非空 `List<String>`，判定用 `isEmpty()`，
     * 禁止 null/空串占位语义）。
     *
     * M6（CHG-16①/OPT-M5-04/FO-01）：尾增 [codes] 机器可读否决码——与 [reasons] **同步产生/
     * 同步为空**（雷暴原因出现 ⇔ THUNDERSTORM 出现；V<0.75 衰减明细出现 ⇔ FISHABILITY_DECAY
     * 出现）。消费侧（BestWindowFinder/组装层）筛选与判定**只认 codes**，禁再 contains 文案
     * （文案随 i18n 可变，码恒定）。
     */
    data class Outcome(
        val value: Double,
        val reasons: List<String>,
        /** M6 尾增（CHG-16①）：机器可读否决码，与 [reasons] 同步产生/同步为空。 */
        val codes: List<VetoCode> = emptyList(),
    )

    // —— 缺测输入的保守档位（取值理由见类 KDoc；CHG-07 P1 修复 1）——
    /** wind 缺测代入值：持续风衰减 sigmoid 拐点（V 项 ×0.50，中性偏保守）。 */
    private const val MISSING_WIND_KMH = 34.0
    /** gust 缺测代入值：阵风衰减 sigmoid 拐点（V 项 ×0.50，中性偏保守）。 */
    private const val MISSING_GUST_KMH = 45.0
    /** dP3 缺测代入值：ΔP3 惩罚起点（V 项 ×1.00，不误杀；信息缺失由权重降级承担）。 */
    private const val MISSING_DP3_HPA = -2.5
    /** rain 缺测代入值：雨强衰减坡 [2,10] 中点（V 项 ×0.65，不得假设无雨）。 */
    private const val MISSING_RAIN_MM = 6.0

    /**
     * 逐项乘性衰减（SRS §6.5 公式，与 fishscore.mjs 第 6 节逐行等价）：
     * 雷暴 ×0.05（唯一硬否决）→ 持续风 → 阵风 → ΔP3（起点 −2.5）→ 雨强 → 浪高（内陆跳过）→ 低 DO。
     *
     * **有值输入的行为与旧实现完全一致**（对拍保证）；仅缺测输入改走保守档位。
     *
     * @param wmoCode 天气码；null = 无雷暴信息（不硬否决，§6.5 原文）
     * @param windSpeedKmh 持续风；缺测 → [MISSING_WIND_KMH] 保守档
     * @param gustKmh 阵风；缺测 → [MISSING_GUST_KMH] 保守档
     * @param dP3 3h 气压差；缺测（趋势数据不足）→ [MISSING_DP3_HPA]，不得视为"无变化"（§6.7）
     * @param rainMmPerH 雨强；缺测 → [MISSING_RAIN_MM]，不得假设无雨
     * @param waveHeightM 浪高；null = 内陆点跳过浪项（非缺测语义，§6.5 原文）
     * @param doPotentialMgL 溶氧潜力；null 跳过该项（§6.5 原文）
     * @param copy 文案源（OPT-V11-13；默认中文 [ZhDomainCopy]，零行为漂移）
     */
    fun evaluate(
        wmoCode: Int?,
        windSpeedKmh: Double?,
        gustKmh: Double?,
        dP3: Double?,
        rainMmPerH: Double?,
        waveHeightM: Double?,
        doPotentialMgL: Double?,
        copy: DomainCopy = ZhDomainCopy,
    ): Outcome {
        var veto = 1.0
        val reasons = mutableListOf<String>()
        val codes = mutableListOf<VetoCode>()
        // 缺测 → 安全侧保守档位（有值时与旧实现逐位一致）
        val wind = windSpeedKmh ?: MISSING_WIND_KMH
        val gust = gustKmh ?: MISSING_GUST_KMH
        val d3 = dP3 ?: MISSING_DP3_HPA
        val rain = rainMmPerH ?: MISSING_RAIN_MM

        if (wmoCode != null && wmoCode >= 95) {
            veto *= 0.05                                   // 雷暴：唯一硬否决（安全）
            reasons.add(copy.vetoThunderstorm)
            codes.add(VetoCode.THUNDERSTORM)               // CHG-16①：机器码与文案同步产生
        }
        veto *= maxOf(0.12, MathKit.sig(wind, 34.0, 0.30))             // 持续风 34 km/h 起快速衰减
        veto *= maxOf(0.10, MathKit.sig(gust, 45.0, 0.18))             // 阵风 45 km/h 起衰减（抛投）
        veto *= maxOf(0.40, 1 - MathKit.clip01((-d3 - 2.5) / 3) * 0.55) // ΔP3 起点 −2.5（日变化量级不罚）
        veto *= maxOf(0.20, 1 - MathKit.clip01((rain - 2) / 8) * 0.70)  // 2 mm/h 以上开始扣分
        if (waveHeightM != null) {
            veto *= maxOf(0.08, MathKit.sig(waveHeightM, 1.6, 1.8))     // 浪高 1.6 m 起衰减（岸边安全）
        }
        if (doPotentialMgL != null) {
            veto *= maxOf(0.18, 1 - MathKit.clip01((4.6 - doPotentialMgL) / 1.6) * 0.75)  // 低溶氧
        }

        if (veto < 0.75) {
            // 原因拼接触发项（SRS §6.5：存库并显示）；OPT-V11-13：标签由 DomainCopy 提供，数值格式不变
            val details = buildString {
                if (wind > 30 || gust > 45) append(copy.vetoWind(wind.toString(), gust.toString()))
                if (d3 < -2.5) append(copy.vetoPressure(String.format(java.util.Locale.US, "%.1f", d3)))
                if (rain > 2) append(copy.vetoRain(String.format(java.util.Locale.US, "%.1f", rain)))
                if (waveHeightM != null && waveHeightM > 1.6) append(copy.vetoWave(waveHeightM.toString()))
                if (doPotentialMgL != null && doPotentialMgL < 4.6) {
                    append(copy.vetoLowOxygen(String.format(java.util.Locale.US, "%.1f", doPotentialMgL)))
                }
            }
            val detail = copy.vetoDecay(String.format(java.util.Locale.US, "%.2f", veto), details)
            reasons.add(detail)
            codes.add(VetoCode.FISHABILITY_DECAY)          // CHG-16①：衰减明细项同样入机器码
        }
        // §6.5 修正 3（CHG-08）：无理由 = 空列表（UI 判 isEmpty()；禁止 null/空串占位）
        return Outcome(veto, reasons, codes)
    }
}
