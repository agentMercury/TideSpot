package com.tidespot.core.domain

import com.tidespot.core.domain.model.Confidence
import com.tidespot.core.domain.model.FactorScore
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.window.BestWindowFinder
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CHG-27（P0-2）锁测试：OPT-V11-26 候选阈值 75 + OPT-V11-25 核心段派生/持续标注 + OPT-V11-03 时段筛选。
 *
 * 口径（OPT_REQUIREMENTS_V11 §3 / 台账 CHG-23/27）：
 * - 候选 `Score ≥ 75`（单点常量 CANDIDATE_MIN，读侧共用）；
 * - 核心段 = 峰值 −2h..+2h 且夹在原窗内（**跨度 ≤4h**，含端点最多 5 个整点，rank 内 min(len,4)）；
 *   原窗跨度 ≤4h 时 core=原窗（恒等旧口径）；
 * - `truncatedUntilEpochSec` = 原窗尾（仅当 core 短于原窗）；`durationHours` = 核心段小时数；
 * - 时段筛选为**输出过滤**（原窗相交），不改候选/rank/去重；空结果兜底取时段内最高分。
 */
class WindowCoreAndFilterTest {

    private val base = LocalDateTime.of(2026, 11, 2, 0, 0).toEpochSecond(ZoneOffset.ofHours(8))

    private fun result(hour: Int, score: Int) = FishScoreResult(
        timeEpochSec = base + hour * 3600L,
        total = score,
        factors = listOf(FactorScore("solunar", 0.8, 0.17), FactorScore("tide", 0.9, 0.24)),
        vetoReasons = emptyList(),
        confidence = Confidence.MODEL,
    )

    @Test
    fun `阈值75_74分不入选_75分入选`() {
        val below = List(240) { h -> if (h == 100) 74 else 30 }
        val outBelow = BestWindowFinder.find(below.mapIndexed { h, s -> result(h, s) })
        assertEquals(0, outBelow.windows.size, "74 分 < 75 不得生成窗口（D12）")
        assertTrue(outBelow.fallbackMessage!!.contains("≥75"), "兜底文案按 75 口径：${outBelow.fallbackMessage}")

        val atThreshold = List(240) { h -> if (h in 100..102) 75 else 30 }
        val outAt = BestWindowFinder.find(atThreshold.mapIndexed { h, s -> result(h, s) })
        assertEquals(1, outAt.windows.size, "75 分恰达阈值应入选")
        assertEquals(75, outAt.windows[0].peakScore)
    }

    @Test
    fun `核心段_峰值在窗中时取峰值正负2小时_并标注延续到原窗尾`() {
        // 10..39 共 30h ≥75（80），仅 25 时峰值 90 → core=23..27（跨度 4h），延续到 39 时
        val scores = List(240) { h ->
            when {
                h == 25 -> 90
                h in 10..39 -> 80
                else -> 30
            }
        }
        val w = BestWindowFinder.find(scores.mapIndexed { h, s -> result(h, s) }).windows.single()
        assertEquals(90, w.peakScore)
        assertEquals(base + 23 * 3600L, w.coreStartEpochSec, "coreStart = peak −2h")
        assertEquals(base + 27 * 3600L, w.coreEndEpochSec, "coreEnd = peak +2h")
        assertEquals(4 * 3600L, w.coreEndEpochSec - w.coreStartEpochSec, "核心段跨度恒 ≤4h")
        assertEquals(base + 39 * 3600L, w.truncatedUntilEpochSec, "延续标注 = 原窗尾")
        assertEquals(base + 10 * 3600L, w.startEpochSec, "原窗起点保留（信息不丢）")
        assertEquals(5, w.durationHours, "durationHours = 核心段小时数（23..27 含端点 5 个整点；rank 内 min(len,4)）")
    }

    @Test
    fun `原窗不超过4小时_核心即原窗_持续标注为空且rank恒等旧公式`() {
        val scores = List(240) { h -> if (h in 10..12) 80 else 30 }
        val w = BestWindowFinder.find(scores.mapIndexed { h, s -> result(h, s) }).windows.single()
        assertEquals(w.startEpochSec, w.coreStartEpochSec, "原窗 ≤4h → core=原窗")
        assertEquals(base + 12 * 3600L, w.coreEndEpochSec)
        assertNull(w.truncatedUntilEpochSec, "核心即原窗 → 持续标注 null（禁空串）")
        assertEquals(3, w.durationHours)
        // 恒等旧公式：0.62·80 + 0.23·80（全窗均值） + 1.4·3
        assertEquals(0.62 * 80 + 0.23 * 80 + 1.4 * 3, w.rank, 1e-9)
    }

    @Test
    fun `时段筛选_与原窗相交但只与核心相交的窗口仍入选`() {
        // 单长窗 10..39（core 23..27）；筛选 36:00-38:00 只与原窗后半段相交（不与 core 相交）
        val scores = List(240) { h -> if (h in 10..39) 80 else 30 }
        val rows = scores.mapIndexed { h, s -> result(h, s) }
        val out = BestWindowFinder.find(
            rows,
            windowStartEpochSec = base + 36 * 3600L,
            windowEndEpochSec = base + 38 * 3600L,
        )
        assertEquals(1, out.windows.size, "利好延续期内（原窗相交）仍应入选（OPT-V11-25 需求 4）")
        assertNotNull(out.windows[0].truncatedUntilEpochSec)
    }

    @Test
    fun `时段筛选_无相交窗口_兜底取时段内最高分`() {
        val scores = List(240) { h -> when (h) { 100 -> 84; 101 -> 70; 200 -> 60; else -> 30 } }
        val rows = scores.mapIndexed { h, s -> result(h, s) }
        val out = BestWindowFinder.find(
            rows,
            windowStartEpochSec = base + 200 * 3600L,
            windowEndEpochSec = base + 202 * 3600L,
        )
        assertEquals(0, out.windows.size)
        val msg = assertNotNull(out.fallbackMessage)
        assertTrue(msg.contains("所选时段"), "兜底文案应标明所选时段：$msg")
        assertTrue(msg.contains("60"), "兜底最高分应取**时段内**最高（60，非全窗 84）：$msg")
        assertEquals(base + 200 * 3600L, out.fallbackBestEpochSec)
    }

    @Test
    fun `不筛选_与全窗输出逐字段一致`() {
        val scores = List(240) { h -> when (h) { 10 -> 90; 30, 31, 32 -> 76; 100 -> 80; else -> 30 } }
        val rows = scores.mapIndexed { h, s -> result(h, s) }
        val plain = BestWindowFinder.find(rows)
        val explicitZero = BestWindowFinder.find(rows, windowStartEpochSec = 0, windowEndEpochSec = 0)
        assertEquals(plain.windows, explicitZero.windows, "0/0 = 不筛选，与全窗输出恒等（零行为漂移）")
        assertNull(plain.fallbackMessage)
    }
}