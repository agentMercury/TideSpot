package com.tidespot.core.domain

import com.tidespot.core.domain.tide.TideCalibrator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * M5 OPT-08：附录 B7 潮位校正层回归台账（序列归档 test 资源 noaa_b7.csv，可重跑）。
 *
 * 口径说明（偏离登记）：SRS 附录 B7 仅给出统计结论（原始 RMSE/r/lag/gain），原始 480h NOAA
 * 序列未随 SRS 归档（隐藏量，[TideCalibratorTest] KDoc 已自认），沙箱无网络不可重录。
 * 本测试资源为与既有 B7 场景测试同源的确定性谐波构造序列（LCG 种子固定、逐位可复现）：
 * - 9414290 圣弗朗西斯科型：M = 1.03·R(t−1) + 漂移 → 期望 lag=+1、gain≈0.97、校正显著优于原始；
 * - 9435380 纽波特型：M 与 R 仅独立白噪差异 → 期望 lag 0、gain≈1、校正无过拟合收益。
 * 断言锁定 B7 的可复核结论（方向性 + 参数带），不锁绝对 RMSE 黄金值。
 */
class TideCalibratorB7RegressionTest {

    private data class Series(val model: List<Double?>, val ref: List<Double?>)

    private fun load(station: String): Series {
        val stream = TideCalibratorB7RegressionTest::class.java.getResourceAsStream("/noaa_b7.csv")
            ?: error("测试资源 noaa_b7.csv 缺失（OPT-08：B7 序列必须归档 core/domain/src/test/resources）")
        val model = ArrayList<Double?>(480)
        val ref = ArrayList<Double?>(480)
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.forEach { line ->
                val parts = line.split(',')
                if (parts[0] == station) {
                    model.add(parts[2].toDouble())
                    ref.add(parts[3].toDouble())
                }
            }
        }
        assertTrue(model.size >= 150 && ref.size >= 150, "B7 序列须 ≥150 对齐小时（实际 ${model.size}）")
        return Series(model, ref)
    }

    @Test
    fun `B7回归_圣弗朗西斯科9414290_校正RMSE优于原始且lag与gain复现`() {
        val s = load("9414290")
        val c = TideCalibrator.calibrate(s.model, s.ref)
        assertTrue(c.adopted, "B7 场景应采纳校正：${c.rejectReason}")
        assertEquals(1, c.lagHours, "黄金结论 lag=+1h（实际 ${c.lagHours}）")
        assertTrue(c.gain in 0.9..1.05, "gain∈[0.9,1.05]（黄金 0.97；实际 ${c.gain}）")
        assertTrue(
            c.correctedRmseM < c.rawRmseM,
            "校正后 RMSE 必须优于校正前：${c.correctedRmseM} vs ${c.rawRmseM}",
        )
        assertTrue(
            c.correctedRmseM < c.rawRmseM * 0.5,
            "该场景校正应大幅改善（黄金结论 −54%）：改善率 ${(1 - c.correctedRmseM / c.rawRmseM) * 100}%",
        )
    }

    @Test
    fun `B7回归_圣弗朗西斯科_留出集同向_test段校正优于基线`() {
        val s = load("9414290")
        val c = TideCalibrator.calibrate(s.model, s.ref)
        val holdout = assertNotNull(c.holdout, "480h 样本足够做半样本外验证")
        assertTrue(
            holdout.correctedRmseM < holdout.baselineRmseM,
            "留出集同向：${holdout.correctedRmseM} vs 基线 ${holdout.baselineRmseM}",
        )
        assertTrue(
            holdout.improvementRatio > 0.4,
            "留出集改善 ≥40%（黄金结论 −63%；实际 ${(holdout.improvementRatio * 100)}%）→ 非过拟合",
        )
    }

    @Test
    fun `B7回归_纽波特9435380_本已对齐则不被错误改动`() {
        val s = load("9435380")
        val c = TideCalibrator.calibrate(s.model, s.ref)
        if (c.adopted) {
            assertEquals(0, c.lagHours, "本已对齐 → lag 0（实际 ${c.lagHours}）")
            assertEquals(1.0, c.gain, 0.05, "gain≈1（实际 ${c.gain}）")
            // 「基本不动」双向断言（FO-12 深化）：校正不得产生过拟合收益，也不得变差超过 5%
            val improvement = (c.rawRmseM - c.correctedRmseM) / c.rawRmseM
            assertTrue(
                improvement in -0.05..0.05,
                "纽波特型 RMSE 变化须 <5%（双向；实际 ${(improvement * 100)}%）",
            )
        }
    }
}