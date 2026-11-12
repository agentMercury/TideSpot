package com.tidespot.core.domain

import com.tidespot.core.domain.model.BestWindow
import com.tidespot.core.domain.model.Confidence
import com.tidespot.core.domain.model.FactorScore
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.model.VetoCode
import com.tidespot.core.domain.window.BestWindowFinder
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 最佳窗口 Top10（§10.1-8 WindowFinderTest；SRS §6.6）。
 * 合成 240h 序列断言：合并、rank 公式、同周期去重、无 ≥60 兜底、跨点聚合。
 */
class WindowFinderTest {

    private val base = LocalDateTime.of(2026, 11, 2, 0, 0).toEpochSecond(ZoneOffset.ofHours(8))

    private fun result(
        hour: Int,
        score: Int,
        veto: List<String> = emptyList(),
        // CHG-16①/OPT-M5-04：finder 只认机器码，构造否决时 codes 必须与 reasons 同步传入
        codes: List<VetoCode> = emptyList(),
    ) = FishScoreResult(
        timeEpochSec = base + hour * 3600L,
        total = score,
        factors = listOf(FactorScore("solunar", 0.8, 0.17), FactorScore("tide", 0.9, 0.24)),
        vetoReasons = veto,
        confidence = Confidence.MODEL,
        vetoCodes = codes,
    )

    @Test
    fun `相邻且间隔不超过 1h 的小时合并为一个窗口`() {
        // 高分段 10,11,12（间隔1h，峰值取首最大值 10 时）+ 20（间隔8h，独立窗）
        val scores = List(240) { h -> when (h) { 10, 11, 12, 20 -> 80; else -> 40 } }
        val out = BestWindowFinder.find(scores.mapIndexed { h, s -> result(h, s) })
        assertEquals(2, out.windows.size, "应合并为 2 个窗口")
        val w1 = out.windows.first()
        assertEquals(3, w1.durationHours)
        assertEquals(base + 10 * 3600, w1.startEpochSec)
        assertEquals(base + 10 * 3600, w1.peakEpochSec, "峰值取 argmax 首个最大值小时")
        assertEquals(80, w1.peakScore)
    }

    @Test
    fun `rank 公式 = 0_62peak + 0_23coreMean + 1_4min(coreLen,4)`() {
        // OPT-V11-25：rank 用核心段（原窗 ≤4h 时核心=原窗，恒等旧口径）
        // 窗 A：peak 90、coreLen 1 → rank = 55.8+20.7+1.4 = 77.9
        // 窗 B：peak 76、coreLen 4 → rank = 47.12+17.48+5.6 = 70.2
        val scores = List(240) { h -> when (h) { 10 -> 90; 30, 31, 32, 33 -> 76; else -> 40 } }
        val out = BestWindowFinder.find(scores.mapIndexed { h, s -> result(h, s) })
        assertEquals(2, out.windows.size)
        assertEquals(90, out.windows[0].peakScore)
        assertEquals(76, out.windows[1].peakScore)
    }

    @Test
    fun `同潮汐周期去重 - 中点差 2_5h 内同涨落段只留 rank 高者`() {
        // 两个短窗（10 与 12 时，中点差 2h <2.5h），dh 全程为正 → 同一涨潮周期 → 去重留 90
        val scores = List(240) { h -> when (h) { 10 -> 90; 12 -> 80; else -> 40 } }
        val tideRates = (0 until 240).associate { h -> (base + h * 3600L) to 0.30 }
        val out = BestWindowFinder.find(scores.mapIndexed { h, s -> result(h, s) }, tideRates)
        assertEquals(1, out.windows.size, "同周期应去重：${out.windows.map { it.peakScore }}")
        assertEquals(90, out.windows[0].peakScore)
    }

    @Test
    fun `不同潮汐周期不去重 - dh 变号后各自保留`() {
        val scores = List(240) { h -> when (h) { 10 -> 90; 16 -> 80; else -> 40 } }
        // 10 时 dh>0（涨），16 时 dh<0（落）→ 不同周期
        val tideRates = (0 until 240).associate { h -> (base + h * 3600L) to if (h < 13) 0.30 else -0.30 }
        val out = BestWindowFinder.find(scores.mapIndexed { h, s -> result(h, s) }, tideRates)
        assertEquals(2, out.windows.size, "跨周期窗口都应保留")
    }

    @Test
    fun `内陆点跳过同周期去重`() {
        val scores = List(240) { h -> when (h) { 10 -> 90; 12 -> 80; else -> 40 } }
        val out = BestWindowFinder.find(scores.mapIndexed { h, s -> result(h, s) }, tideRates = null)
        assertEquals(2, out.windows.size, "无潮汐数据（内陆）不得去重")
    }

    @Test
    fun `全部小于 75 - 兜底文案给出最高分与时刻`() {
        val scores = List(240) { h -> if (h == 100) 58 else 30 }
        val out = BestWindowFinder.find(scores.mapIndexed { h, s -> result(h, s) })
        assertEquals(0, out.windows.size)
        val msg = assertNotNull(out.fallbackMessage)
        assertTrue(msg.contains("≥75"), "兜底文案应说明无 ≥75 分窗口（OPT-V11-26 阈值 75）：$msg")
        assertTrue(msg.contains("58"), "兜底文案应给出最高分：$msg")
    }

    @Test
    fun `有效数据不足 24h - 禁用并提示`() {
        val out = BestWindowFinder.find((0 until 12).map { h -> result(h, 90) }, validHoursPresent = 12)
        assertEquals(0, out.windows.size)
        assertNotNull(out.fallbackMessage)
    }

    @Test
    fun `雷暴否决小时不得入选候选 - 但普通衰减小时仍可入选且注意事项必显`() {
        val scores = List(240) { h -> if (h in 10..14) 85 else 40 }
        // 10 时雷暴（排除，按 VetoCode.THUNDERSTORM 机器码），11-14 普通衰减（可入选但带警告）
        val rows = scores.mapIndexed { h, s ->
            if (h == 10) result(h, s, listOf("雷暴（安全否决）"), listOf(VetoCode.THUNDERSTORM))
            else if (h in 11..14) result(h, s, listOf("可作钓性衰减 V=0.60 风32/46km/h"), listOf(VetoCode.FISHABILITY_DECAY))
            else result(h, s)
        }
        val out = BestWindowFinder.find(rows)
        assertEquals(1, out.windows.size)
        val w = out.windows.first()
        assertEquals(base + 11 * 3600, w.startEpochSec)
        assertTrue(w.warnings.isNotEmpty(), "含否决小时入选时注意事项必须显示")
    }

    @Test
    fun `档位文案与主因输出`() {
        val out = BestWindowFinder.find((0 until 240).map { h -> result(h, if (h in 10..12) 85 else 40) })
        assertEquals(1, out.windows.size)
        assertEquals("爆护窗口", out.windows[0].tier)
        assertEquals(2, out.windows[0].topFactors.size, "主因输出 2 项")
        // solunar 贡献 0.8*0.17=0.136 < tide 0.9*0.24=0.216 → tide 排第一
        assertEquals("tide", out.windows[0].topFactors.first())
        assertNull(out.fallbackMessage)
    }

    @Test
    fun `跨点聚合 - 各点独立跑后按 rank 全局取 10`() {
        val mk: (Int) -> List<BestWindow> = { peak ->
            BestWindowFinder.find((0 until 240).map { h -> result(h, if (h in 10..12) peak else 40) }).windows
        }
        val merged = BestWindowFinder.rankAcrossSpots(listOf("千岛湖" to mk(85), "青岛" to mk(92), "舟山" to mk(70)))
        assertTrue(merged.size <= 10)
        assertEquals("青岛", merged.first().first, "峰值最高者全局第一")
        assertEquals(92, merged.first().second.peakScore)
    }

    // ─────────────── M5 FO-13：合并间隔 = MERGE_GAP_HOURS * 3600（≤1h，SRS §6.6 步骤 2） ───────────────

    @Test
    fun `FO13_相邻间隔3600s合并为一窗`() {
        // 间隔恰为 3600s（≤1h 上界）必须合并
        val scores = List(240) { h -> when (h) { 10, 11, 12 -> 80; else -> 40 } }
        val out = BestWindowFinder.find(scores.mapIndexed { h, s -> result(h, s) })
        assertEquals(1, out.windows.size, "间隔恰为 1h（3600s）必须合并（FO-13）")
        assertEquals(3, out.windows.first().durationHours)
    }

    @Test
    fun `FO13_间隔5400s不再合并_两独立窗`() {
        // 10:00 与 11:30（差 5400s > 3600s）：旧实现 (+1800L → 5400s) 会违规合并为一窗
        val scores = List(240) { h -> when (h) { 10, 11 -> 80; else -> 40 } }
        val rows = scores.mapIndexed { h, s ->
            result(h, s).copy(timeEpochSec = base + h * 3600L + if (h == 11) 1800L else 0)
        }
        val out = BestWindowFinder.find(rows)
        assertEquals(2, out.windows.size, "5400s 间隔不得合并（SRS §6.6 步骤 2 间隔 ≤1h）")
    }

    @Test
    fun `非整点时区边界_带30min偏移的hourEpoch按epoch合并_起点与时长保真`() {
        // :30 偏移网格：10:30→11:30（差 3600s）合并；12:00（差 1800s）并入；20:00 独立
        fun at(minShift: Long, h: Int, score: Int) = FishScoreResult(
            timeEpochSec = base + h * 3600L + minShift,
            total = score,
            factors = listOf(FactorScore("solunar", 0.8, 0.17), FactorScore("tide", 0.9, 0.24)),
            vetoReasons = emptyList(),
            confidence = Confidence.MODEL,
        )
        val rows = listOf(
            at(1800, 10, 80), at(1800, 11, 76), at(0, 12, 75),
            at(0, 20, 76),
        )
        // 稀疏样本仅 4 行，显式声明有效窗口 240h（默认取 results.size 会误入 <24h 禁用分支）
        val out = BestWindowFinder.find(rows, validHoursPresent = 240)
        assertEquals(2, out.windows.size, "合并/独立判定一律按 epoch 秒差，与整点与否无关")
        val w1 = out.windows[0]
        assertEquals(base + 10 * 3600L + 1800, w1.startEpochSec, "窗起点 = 首个候选原始 epoch（:30 偏移保真，禁整点取整）")
        assertEquals(3, w1.durationHours)
        assertEquals(80, w1.peakScore)
        assertEquals(base + 20 * 3600L, out.windows[1].startEpochSec)
    }

    @Test
    fun `非整点时区边界_同周期去重按epoch比较_偏移网格下仍生效`() {
        // 两窗中点差 2h（<2.5h）且 dh 同号 → 按 epoch 去重留 rank 高者；tideRates 键与候选同网格（±30min）
        fun at(minShift: Long, h: Int, score: Int) = FishScoreResult(
            timeEpochSec = base + h * 3600L + minShift,
            total = score,
            factors = listOf(FactorScore("solunar", 0.8, 0.17), FactorScore("tide", 0.9, 0.24)),
            vetoReasons = emptyList(),
            confidence = Confidence.MODEL,
        )
        val rows = listOf(at(1800, 10, 90), at(1800, 12, 80))
        val tideRates = mapOf(
            base + 10 * 3600L + 1800 to 0.30,
            base + 11 * 3600L + 1800 to 0.30,
            base + 12 * 3600L + 1800 to 0.30,
        )
        // 稀疏样本仅 2 行，显式声明有效窗口 240h（默认取 results.size 会误入 <24h 禁用分支）
        val out = BestWindowFinder.find(rows, tideRates, validHoursPresent = 240)
        assertEquals(1, out.windows.size, "偏移网格下中点差/同周期判定仍按 epoch 正确去重")
        assertEquals(90, out.windows[0].peakScore)
    }

    // ─────────────── M5 FO-14：rankAcrossSpots 按窗口 rank 全局降序 ───────────────

    @Test
    fun `FO14_跨点聚合按rank全局排序_长窗加权反超高peak短窗`() {
        // OPT-V11-25 后 rank 用核心段：A 窗 peak 在窗首（10 时）→ core 被窗界截断为 10..12（3h）：
        //   rank = 0.62·88 + 0.23·88 + 1.4·3 = 54.56+20.24+4.2 = 79.0
        // B 点：peak 90、coreLen 1 → rank = 0.62·90 + 0.23·90 + 1.4·1 = 77.9（peak 更高但 rank 更低）
        val scoresA = List(240) { h -> if (h in 10..13) 88 else 40 }
        val scoresB = List(240) { h -> if (h == 10) 90 else 40 }
        val winA = BestWindowFinder.find(scoresA.mapIndexed { h, s -> result(h, s) }).windows
        val winB = BestWindowFinder.find(scoresB.mapIndexed { h, s -> result(h, s) }).windows
        assertEquals(79.0, winA.first().rank, 1e-9, "核心段 rank（窗首峰值 → core 截断为 3h）")
        assertEquals(77.9, winB.first().rank, 1e-9)

        val merged = BestWindowFinder.rankAcrossSpots(listOf("B短窗" to winB, "A长窗" to winA))
        assertEquals("A长窗", merged.first().first, "全局第一 = rank 最高者（旧实现按 peakScore 会误选 B 短窗）")
        assertEquals(88, merged.first().second.peakScore)
        assertEquals("B短窗", merged[1].first)
    }

    // ─────────────── M5 Output 尾增 fallbackBestEpochSec ───────────────

    @Test
    fun `M5_fallbackBestEpochSec_全部低于75时给出最高分时刻`() {
        val scores = List(240) { h -> if (h == 100) 58 else 30 }
        val out = BestWindowFinder.find(scores.mapIndexed { h, s -> result(h, s) })
        assertEquals(0, out.windows.size)
        assertNotNull(out.fallbackMessage)
        assertEquals(base + 100 * 3600L, out.fallbackBestEpochSec, "兜底 = 最高分小时的 timeEpochSec（时刻渲染在 UI 层）")
    }

    @Test
    fun `M5_fallbackBestEpochSec_有窗口与禁用时为null`() {
        val scores = List(240) { h -> if (h in 10..12) 80 else 40 }
        val ok = BestWindowFinder.find(scores.mapIndexed { h, s -> result(h, s) })
        assertNull(ok.fallbackBestEpochSec, "有窗口 → null")
        assertNull(ok.fallbackMessage)
        val disabled = BestWindowFinder.find((0 until 12).map { h -> result(h, 90) }, validHoursPresent = 12)
        assertNull(disabled.fallbackBestEpochSec, "有效数据不足 24h 禁用分支 → null")
    }

    // ─────────────── M6 CHG-15 偏差①：时间域过滤（SRS §6.6「从当前小时的下一整点起」） ───────────────

    @Test
    fun `M6_历史窗口不入选_now之后的候选才参与排名`() {
        // h5=92（历史，now=第 100 小时）与 h200=85（未来）：
        // 旧实现会取历史 92 分窗为第一；修复后历史候选剔除，只输出未来窗
        val scores = List(240) { h -> when (h) { 5 -> 92; 200, 201 -> 85; else -> 40 } }
        val out = BestWindowFinder.find(
            scores.mapIndexed { h, s -> result(h, s) },
            nowEpochSec = base + 100 * 3600L,
        )
        assertEquals(1, out.windows.size, "历史高分窗（h5=92）不得入选：${out.windows.map { it.peakScore }}")
        assertEquals(85, out.windows[0].peakScore)
        assertEquals(base + 200 * 3600L, out.windows[0].startEpochSec)
    }

    @Test
    fun `M6_兜底最高分只在未来序列内取`() {
        // 全部 <60：历史 h10=58、未来 h200=50 → 兜底取未来 50（历史 58 不得出现）
        val scores = List(240) { h -> when (h) { 10 -> 58; 200 -> 50; else -> 20 } }
        val out = BestWindowFinder.find(
            scores.mapIndexed { h, s -> result(h, s) },
            nowEpochSec = base + 100 * 3600L,
        )
        assertEquals(0, out.windows.size)
        assertNotNull(out.fallbackMessage)
        assertEquals(base + 200 * 3600L, out.fallbackBestEpochSec, "兜底最高分时刻必须来自未来序列")
        assertTrue(out.fallbackMessage!!.contains("50 分"), "兜底分数 = 未来最高 50：${out.fallbackMessage}")
    }

    @Test
    fun `M6_未来有效小时不足24h_禁用`() {
        // now=第 230 小时 → 未来仅 10 行（含高分）→ 过滤后 <24 → 禁用（不得用历史行凑数）
        val scores = List(240) { h -> when (h) { 5 -> 90; 232, 233 -> 90; else -> 40 } }
        val out = BestWindowFinder.find(
            scores.mapIndexed { h, s -> result(h, s) },
            nowEpochSec = base + 230 * 3600L,
        )
        assertEquals(0, out.windows.size)
        assertTrue(out.fallbackMessage!!.contains("不足 24"), "未来有效小时不足 24 → 禁用：${out.fallbackMessage}")
    }

    @Test
    fun `M6_nowEpochSec为0_不过滤_向后兼容`() {
        // 默认参数 nowEpochSec=0：历史语义（全序列参与）与既有单测口径完全一致
        val scores = List(240) { h -> when (h) { 5 -> 92; 200, 201 -> 85; else -> 40 } }
        val out = BestWindowFinder.find(scores.mapIndexed { h, s -> result(h, s) })
        assertEquals(2, out.windows.size, "nowEpochSec=0 → 不过滤（既有口径）")
        assertEquals(92, out.windows[0].peakScore)
    }
}
