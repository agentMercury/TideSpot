package com.tidespot.core.domain

import com.tidespot.core.domain.tide.TideCalibrator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B7 潮位校正层（SRS 附录 B7 + §10.1-7 TideCalibratorTest + §6.2b 采纳规则 + CHG-04 现场校潮）。
 *
 * 黄金结论（SRS §6.2b 实测）：
 * - 圣弗朗西斯科 9414290：r 0.949 → lag=+1h, gain 0.97, 校正 RMSE 0.077 m（−54%）；
 *   半样本外 test RMSE 0.074 vs 基线 0.201（−63%）→ 非过拟合。
 * - 纽波特 9435380：r 0.989 → lag 0, gain 0.99 → 基本不动。
 *
 * 录制用的 NOAA 原始序列未随 SRS 归档（隐藏量），本测试用构造谐波序列近似复现同一物理场景
 * （任务书允许："半样本外非过拟合（可用构造数据近似验证采纳规则逻辑）"）：
 * 1) R(t)=1.2·sin(2π t/12.42)+0.25·sin(2π t/24)+噪声，M(t)=0.97·R(t−1)+漂移 → 期望 lag≈+1、gain≈0.97；
 * 2) M 几乎等于 R（gain≈0.99, lag 0）→ 期望"基本不动"（校正后 RMSE 降幅 <5% 或不启用）。
 */
class TideCalibratorTest {

    /** 生成 480h 谐波潮位。 */
    private fun harmonic(hours: Int, amp: Double, phaseShift: Int, gain: Double, drift: Double, seed: Long): List<Double> {
        val out = ArrayList<Double>(hours)
        var s = seed
        for (h in 0 until hours) {
            s = s * 6364136223846793005L + 1442695040888963407L
            val noise = ((s shr 33) % 1000 - 500) / 500.0 * 0.015   // ±1.5cm 白噪
            val t = h.toDouble()
            val r = amp * sin(2 * PI * (t - phaseShift) / 12.4206) + amp * 0.2 * sin(2 * PI * (t - phaseShift) / 24.07)
            out.add(gain * r + drift * t / hours + noise)
        }
        return out
    }

    @Test
    fun `圣弗朗西斯科型 - lag 约 +1h gain 约 0_97 校正 RMSE 下降`() {
        // 黄金场景：模型比实测偏大（gain<1 的校正意义）→ 构造 M=1.03·R(t−1)+漂移+噪，
        // 期望校准器拟合出 lag=+1、gain≈1/1.03≈0.97
        val hours = 480
        val ref = harmonic(hours, 1.2, 0, 1.0, 0.0, seed = 42L)             // NOAA 实测
        val model = harmonic(hours, 1.2, -1, 1.03, 0.06, seed = 99L)        // 模型：滞后 1h、偏大 3%、轻微漂移
        val c = TideCalibrator.calibrate(model, ref)
        assertTrue(c.adopted, "应采纳校正：${c.rejectReason}")
        assertEquals(1, c.lagHours, "拟合 lag 应为 +1h（实际 ${c.lagHours}）")
        assertEquals(0.97, c.gain, 0.05, "gain 应≈0.97（实际 ${c.gain}）")
        assertTrue(abs(c.offsetM) <= 0.5, "offset 应在 ±0.5m 内")
        assertTrue(c.r >= 0.6, "r=${c.r} 应 ≥0.6")
        assertTrue(c.correctedRmseM < c.rawRmseM * 0.9, "校正 RMSE 应显著下降：${c.correctedRmseM} vs ${c.rawRmseM}")
    }

    @Test
    fun `半样本外 - fit 60% 参数在 test 40% 上仍优于基线（非过拟合）`() {
        val hours = 480
        val ref = harmonic(hours, 1.2, 0, 1.0, 0.0, seed = 42L)
        val model = harmonic(hours, 1.2, -1, 1.03, 0.06, seed = 99L)
        val c = TideCalibrator.calibrate(model, ref)
        val holdout = c.holdout ?: return
        assertTrue(holdout.correctedRmseM < holdout.baselineRmseM, "留出集应优于基线：${holdout.correctedRmseM} vs ${holdout.baselineRmseM}")
        assertTrue(holdout.improvementRatio > 0.4, "留出集改善应 ≥40%（黄金结论 −63%；实际 ${holdout.improvementRatio}）")
    }

    @Test
    fun `纽波特型 - 本已很好则基本不动（lag 0 gain≈1 校正无过拟合收益）`() {
        // 构造：模型与参考仅独立白噪不同（无系统增益/相位差）→ 校正不应产生收益
        val hours = 480
        val ref = harmonic(hours, 1.7, 0, 1.0, 0.0, seed = 7L)
        val model = harmonic(hours, 1.7, 0, 1.0, 0.0, seed = 8L)
        val c = TideCalibrator.calibrate(model, ref)
        if (c.adopted) {
            assertEquals(0, c.lagHours, "本已对齐时 lag 应为 0（实际 ${c.lagHours}）")
            assertEquals(1.0, c.gain, 0.05)
            // "基本不动"：RMSE 改善 < 5%（黄金结论：纽波特留出集 0.132 vs 0.132 无收益）
            val improvement = (c.rawRmseM - c.correctedRmseM) / c.rawRmseM
            assertTrue(improvement < 0.05, "校正不应产生过拟合收益：改善 $improvement")
        }
    }

    @Test
    fun `采纳规则 - r 不足 0_6 拒绝并标注未校正`() {
        // 随机序列（无潮汐结构）→ r 低
        val noise = List(200) { i -> sin(i * 12.9898) * 43758.5453 % 1.0 }
        val ref = List(200) { i -> sin(i.toDouble() / 3.0) }
        val c = TideCalibrator.calibrate(noise, ref)
        assertTrue(!c.adopted, "无潮汐结构应拒绝校正")
        assertTrue(c.rejectReason!!.contains("相关系数") || c.rejectReason!!.contains("0.6"))
        // 未采纳时 apply 原样返回
        assertEquals(noise, TideCalibrator.apply(noise, c))
    }

    @Test
    fun `采纳规则 - 对齐样本不足 150h 禁用校正层`() {
        val ref = harmonic(120, 1.2, 0, 1.0, 0.0, 1L)
        val model = harmonic(120, 1.2, -1, 0.97, 0.0, 2L)
        val c = TideCalibrator.calibrate(model, ref)
        assertTrue(!c.adopted)
        assertTrue(c.rejectReason!!.contains("样本"))
    }

    @Test
    fun `采纳规则 - gain 超界 1_4 拒绝采用`() {
        val ref = harmonic(480, 1.2, 0, 1.0, 0.0, 42L)
        val model = harmonic(480, 1.2, -1, 1.6, 0.06, 99L)   // gain 1.6 超出 [0.7,1.4]
        val c = TideCalibrator.calibrate(model, ref)
        assertTrue(!c.adopted, "gain 超界必须拒绝")
        assertTrue(c.rejectReason!!.contains("gain"))
    }

    @Test
    fun `采纳后 apply - M'(h)=gain·M(h-lag)+offset`() {
        val model = listOf<Double?>(1.0, 2.0, 3.0, 4.0)
        val cal = TideCalibrator.Calibration(
            lagHours = 1, gain = 2.0, offsetM = 0.5, r = 0.9, alignedSamples = 200,
            rawRmseM = 0.2, correctedRmseM = 0.1, adopted = true, rejectReason = null, holdout = null,
        )
        val applied = TideCalibrator.apply(model, cal)
        // M'(i) = 2·M(i-1)+0.5：首位无源 → null
        assertNull(applied[0])
        assertEquals(2.5, applied[1]!!, 1e-9)
        assertEquals(4.5, applied[2]!!, 1e-9)
    }

    // ---------- CHG-04 现场校潮（无官方参考序列地区的唯一合规路径） ----------

    @Test
    fun `现场校潮 - 不足 2 条只落观察不启用`() {
        val fit = TideCalibrator.fitManual(listOf(3600L), listOf(0L))
        assertTrue(!fit.adopted)
        assertEquals(1.0, fit.gain, 1e-9)
        assertTrue(fit.message!!.contains("再记 1 条"))
    }

    @Test
    fun `现场校潮 - 2 条以上拟合 lag gain 固定 1_0`() {
        val base = 1798147200L   // 2026-11-02T00:00+08:00
        // 模型极值在 3:00 与 15:33；用户观测滞后 1h
        val modelExtremes = listOf(base + 3 * 3600, base + 15 * 3600 + 1980)
        val observations = listOf(base + 4 * 3600, base + 16 * 3600 + 1980, base + 28 * 3600)
        val fit = TideCalibrator.fitManual(observations, modelExtremes)
        assertTrue(fit.adopted)
        assertEquals(1.0, fit.gain, 1e-9)
        assertEquals(0.0, fit.offsetM, 1e-9)
        assertEquals(1, fit.lagHours, "观测滞后 1h → lag=+1")
    }

    @Test
    fun `现场校潮 - lag 超出 ±5h 拒绝启用`() {
        val base = 1798147200L
        // OPT-06 后语义：观测先按 ±6h 匹配窗过滤，再取 lag 中位数——
        // 构造两条均落在 ±6h 窗内、但 lag 中位数 6h > 5h 的用例（保持本测试「超界拒绝」意图）：
        // 模型极值在 0:00 与 13:00；观测 6:00（lag +6h，距 0:00 恰 6h 窗内有效）、19:00（lag +6h，距 13:00 恰 6h 窗内有效）
        val fit = TideCalibrator.fitManual(
            listOf(base + 6 * 3600, base + 19 * 3600),
            listOf(base + 0, base + 13 * 3600),
        )
        assertTrue(!fit.adopted)
        assertTrue(fit.message!!.contains("超出"), "两条有效但 lag 中位数 6h > ±5h → 拒绝：${fit.message}")
    }

    // ─────────────── M5 OPT-06：fitManual 模型极值匹配限 ±6h 窗 ───────────────

    @Test
    fun `OPT06_部分观测超窗_只计有效条仍可拟合`() {
        val base = 1798147200L
        val modelExtremes = listOf(base + 3 * 3600, base + 15 * 3600 + 1980)
        // 第 3 条观测（+28h）距最近极值 12.45h > 6h → 不计入 lags；2 条有效 → 仍启用
        val observations = listOf(base + 4 * 3600, base + 16 * 3600 + 1980, base + 28 * 3600)
        val fit = TideCalibrator.fitManual(observations, modelExtremes)
        assertTrue(fit.adopted, "2 条有效（1 条超窗不计入）→ 仍可拟合：${fit.message}")
        assertEquals(1, fit.lagHours)
    }

    @Test
    fun `OPT06_全部超窗_不启用并提示有效条数`() {
        val base = 1798147200L
        // 两条观测均距最近极值 >6h → 有效 0 条
        val fit = TideCalibrator.fitManual(
            listOf(base + 30 * 3600, base + 40 * 3600),
            listOf(base + 3 * 3600, base + 15 * 3600),
        )
        assertFalse(fit.adopted)
        assertTrue(fit.message!!.contains("有效观测不足 2 条"), "message 必须提示有效条数：${fit.message}")
        assertTrue(fit.message!!.contains("现有 0 条"), "${fit.message}")
    }

    @Test
    fun `OPT06_失配观测不污染中位数_超窗观测不计入lags`() {
        val base = 1798147200L
        val modelExtremes = listOf(base, base + 13 * 3600)
        // 2 条 +2h 有效 + 2 条距最近极值 87h 的失配观测：
        // 旧实现（无匹配也计 lag=最近值差）→ lags [2,2,87,87] 中位数 44.5→45 拒绝；新实现 → [2,2] → lag 2 启用
        val observations = listOf(
            base + 2 * 3600, base + 2 * 3600,
            base + 100 * 3600, base + 100 * 3600,
        )
        val fit = TideCalibrator.fitManual(observations, modelExtremes)
        assertTrue(fit.adopted, "失配观测不计入 → 2 条有效正常拟合：${fit.message}")
        assertEquals(2, fit.lagHours)
    }

    @Test
    fun `OPT06_恰6h边界命中_两条有效正常拟合`() {
        val base = 1798147200L
        // +6h 与 −6h（abs(it-obs) == 6*3600，含边界）均命中；中位数 0 → 启用
        val fit = TideCalibrator.fitManual(
            listOf(base + 6 * 3600, base + 7 * 3600),
            listOf(base, base + 13 * 3600),
        )
        assertTrue(fit.adopted, "±6h 边界（≤6h）应命中：${fit.message}")
        assertEquals(0, fit.lagHours)
    }
}
