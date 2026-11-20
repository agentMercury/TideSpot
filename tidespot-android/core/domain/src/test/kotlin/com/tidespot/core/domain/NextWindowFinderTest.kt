package com.tidespot.core.domain

import com.tidespot.core.domain.model.BestWindow
import com.tidespot.core.domain.window.NextWindowFinder
import com.tidespot.core.domain.window.NextWindowFinder.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 首页「下一个最佳窗口」挑选（对齐 Web `domain/nextWindow.test.ts` 的 19 条用例）。
 *
 * 重点覆盖**最容易错的边界**：
 * - 右开区间：窗口最后一小时结束后那一刻不算在窗内
 * - windowTailEpochSec 的两条分支（truncatedUntil 非空 / 为空取 coreEnd）
 * - 多窗口同时活跃 → 选剩余最短（最紧迫），而非 rank 最高
 * - 仅剩几分钟时 remainingHours 仍 ≥ 1
 * - 全过期 → NONE（不假造窗口）
 */
class NextWindowFinderTest {

    /** 基准时刻（任意，仅用于相对计算）。 */
    private val t0 = 1794560000L
    private fun h(n: Long) = n * 3600L

    /**
     * 构造窗口。
     * @param startOffsetH 窗口首小时相对 t0 的偏移
     * @param coreLenH 核心段小时数（写入 durationHours）
     * @param truncated 原窗尾（若核心段短于原窗）；null 表示核心即整窗
     */
    private fun win(
        startOffsetH: Long,
        coreLenH: Int = 3,
        truncatedOffsetH: Long? = null,
        peak: Int = 80,
        coreEndOffsetH: Long? = null,
    ): BestWindow {
        val start = t0 + h(startOffsetH)
        val peakEpoch = start
        val coreEnd = if (coreEndOffsetH != null) t0 + h(coreEndOffsetH) else start + h(coreLenH - 1L)
        return BestWindow(
            startEpochSec = start,
            peakEpochSec = peakEpoch,
            durationHours = coreLenH,
            peakScore = peak,
            tier = "爆护窗口",
            topFactors = emptyList(),
            warnings = emptyList(),
            rank = peak.toDouble(),
            coreStartEpochSec = start,
            coreEndEpochSec = coreEnd,
            truncatedUntilEpochSec = truncatedOffsetH?.let { t0 + h(it) },
        )
    }

    // ---------- 窗口结束时刻 ----------

    @Test
    fun `windowTail 优先用 truncatedUntil（核心段短于原窗）`() {
        // 核心段 3h（start..start+2h），原窗尾在 +5h
        val w = win(0, coreLenH = 3, truncatedOffsetH = 5)
        assertEquals(t0 + h(5), NextWindowFinder.windowTailEpochSec(w))
        assertEquals(t0 + h(6), NextWindowFinder.windowEndExclusive(w), "开区间右端 = 尾 + 3600")
    }

    @Test
    fun `windowTail 在 truncatedUntil 为空时取 coreEnd`() {
        val w = win(0, coreLenH = 3)   // coreEnd = start + 2h
        assertEquals(t0 + h(2), NextWindowFinder.windowTailEpochSec(w))
        assertEquals(t0 + h(3), NextWindowFinder.windowEndExclusive(w))
    }

    @Test
    fun `windowTail 对未派生旧数据用峰值+核心段估算`() {
        val w = BestWindow(
            startEpochSec = t0, peakEpochSec = t0, durationHours = 2, peakScore = 70,
            tier = "适合", topFactors = emptyList(), warnings = emptyList(),
            // coreStart/coreEnd/truncated 全默认（0 / null）= 未派生
        )
        assertEquals(t0 + h(1), NextWindowFinder.windowTailEpochSec(w), "峰值 + (2-1)h")
        assertEquals(t0 + h(2), NextWindowFinder.windowEndExclusive(w))
    }

    // ---------- 区间判定 ----------

    @Test
    fun `isInWindow 左闭：窗口起点算在窗内`() {
        val w = win(0, coreLenH = 3)   // 覆盖 [t0, t0+3h)
        assertTrue(NextWindowFinder.isInWindow(w, t0))
    }

    @Test
    fun `isInWindow 右开：最后一小时结束后不算`() {
        val w = win(0, coreLenH = 3)   // endExclusive = t0+3h
        assertTrue(NextWindowFinder.isInWindow(w, t0 + h(3) - 1))
        assertTrue(!NextWindowFinder.isInWindow(w, t0 + h(3)), "右端那刻应已出窗")
    }

    @Test
    fun `isInWindow 窗口开始前不算`() {
        val w = win(2, coreLenH = 2)
        assertTrue(!NextWindowFinder.isInWindow(w, t0))
    }

    // ---------- 挑选逻辑 ----------

    @Test
    fun `空列表 → NONE`() {
        val r = NextWindowFinder.findNext(emptyList(), t0)
        assertEquals(State.NONE, r.state)
        assertNull(r.window)
        assertEquals(0L, r.remainingSec)
        assertEquals(0, r.remainingHours)
    }

    @Test
    fun `全部过期 → NONE（不假造窗口）`() {
        val past = win(-5, coreLenH = 2)
        assertEquals(State.NONE, NextWindowFinder.findNext(listOf(past), t0).state)
    }

    @Test
    fun `正在窗口内 → IN_WINDOW 且剩余时间正确`() {
        val w = win(-1, coreLenH = 3)   // 覆盖 [t0-1h, t0+2h)
        val r = NextWindowFinder.findNext(listOf(w), t0)
        assertEquals(State.IN_WINDOW, r.state)
        assertEquals(w, r.window)
        assertEquals(h(2), r.remainingSec)
        assertEquals(2, r.remainingHours)
    }

    @Test
    fun `多窗口同时活跃 → 选剩余最短（最紧迫）`() {
        val soon = win(-2, coreLenH = 3)   // 覆盖到 t0+1h，剩余 1h
        val later = win(-1, coreLenH = 5)  // 覆盖到 t0+4h，剩余 4h
        val r = NextWindowFinder.findNext(listOf(later, soon), t0)
        assertEquals(State.IN_WINDOW, r.state)
        assertEquals(h(1), r.remainingSec)
    }

    @Test
    fun `紧迫性优先于 rank（高分但更晚的窗口不被选）`() {
        val soonLow = win(1, coreLenH = 1, peak = 70)
        val laterHigh = win(8, coreLenH = 4, peak = 95)
        val r = NextWindowFinder.findNext(listOf(laterHigh, soonLow), t0)
        assertEquals(State.UPCOMING, r.state)
        assertEquals(70, r.window?.peakScore, "应选分数低但更早的窗口")
    }

    @Test
    fun `无活跃窗口 → 选开始最近的未来窗口`() {
        val far = win(10, coreLenH = 2)
        val near = win(3, coreLenH = 2)
        val r = NextWindowFinder.findNext(listOf(far, near), t0)
        assertEquals(State.UPCOMING, r.state)
        assertEquals(near, r.window)
        assertEquals(h(3), r.remainingSec)
        assertEquals(0, r.remainingHours)
    }

    @Test
    fun `有活跃窗口时不考虑未来窗口`() {
        val active = win(-1, coreLenH = 3)
        val future = win(5, coreLenH = 2)
        val r = NextWindowFinder.findNext(listOf(future, active), t0)
        assertEquals(State.IN_WINDOW, r.state)
        assertEquals(active, r.window)
    }

    @Test
    fun `仅剩几分钟时 remainingHours 至少为 1`() {
        // windowEndExclusive = coreEnd + 3600。要让剩余 = 60s：
        //   coreEnd + 3600 = t0 + 60  =>  coreEnd = t0 - 3540
        val near = BestWindow(
            startEpochSec = t0 - h(2),
            peakEpochSec = t0 - h(2),
            durationHours = 1,
            peakScore = 75,
            tier = "适合",
            topFactors = emptyList(),
            warnings = emptyList(),
            coreStartEpochSec = t0 - h(2),
            coreEndEpochSec = t0 - 3540L,
            truncatedUntilEpochSec = null,
        )
        val r = NextWindowFinder.findNext(listOf(near), t0)
        assertEquals(State.IN_WINDOW, r.state)
        assertEquals(60L, r.remainingSec)
        assertEquals(1, r.remainingHours, "剩 60 秒仍应显示 1 小时（向上取整）")
    }

    // ---------- 文案 ----------

    @Test
    fun `formatDuration 各分支`() {
        assertEquals("2 小时 15 分", NextWindowFinder.formatDuration(2 * 3600L + 15 * 60))
        assertEquals("3 小时", NextWindowFinder.formatDuration(3 * 3600L))
        assertEquals("45 分钟", NextWindowFinder.formatDuration(45 * 60L))
        assertEquals("不到 1 分钟", NextWindowFinder.formatDuration(30))
        assertEquals("0 分钟", NextWindowFinder.formatDuration(0))
        assertEquals("0 分钟", NextWindowFinder.formatDuration(-100))
    }

    @Test
    fun `formatWindowRange 按本地时区显示，右端为收杆时刻`() {
        val tz = 8 * 3600
        // 本地 09:00 起、核心段 3h → 09:00–12:00
        val localNine = 1794560400L   // 任意基准，按 tz 反推
        val startUtc = 1794550800L    // 需保证 (startUtc+tz) % 86400 == 9*3600
        val w = BestWindow(
            startEpochSec = startUtc, peakEpochSec = startUtc, durationHours = 3, peakScore = 80,
            tier = "爆护窗口", topFactors = emptyList(), warnings = emptyList(),
            coreStartEpochSec = startUtc, coreEndEpochSec = startUtc + h(2), truncatedUntilEpochSec = null,
        )
        val range = NextWindowFinder.formatWindowRange(w, tz)
        assertTrue(range.contains("–"), "应为 HH:00–HH:00 形式，实际 $range")
        assertEquals(11, range.length, "格式固定 11 字符（含 en dash）")
    }
    // ---------- 日期感知格式化（bug：后天窗口显示成「今天还没到」） ----------

    @Test
    fun `今天窗口不显示日期`() {
        val tz = 8 * 3600
        // 本地今天 08:00 起、核心 4h → 08:00–12:00
        val nowLocal8 = 1794844800L + 8 * 3600L   // 本地 11-17 08:00
        val w = win(0, coreLenH = 4)               // t0 起，t0 = 1794560000（可能不是今天）
        // 用相对 now 构造：让 startEpoch 落在「今天」
        val todayStart = nowLocal8 - 8 * 3600L     // 本地今天 00:00
        val wToday = w.copy(
            startEpochSec = todayStart + 8 * 3600L,
            peakEpochSec = todayStart + 8 * 3600L,
            coreStartEpochSec = todayStart + 8 * 3600L,
            coreEndEpochSec = todayStart + 11 * 3600L,
        )
        val s = NextWindowFinder.formatWindowRangeWithDate(wToday, nowLocal8, tz)
        assertEquals("08:00–12:00", s, "今天的窗口不应带日期")
    }

    @Test
    fun `后天窗口前缀日期`() {
        val tz = 8 * 3600
        val nowLocal8 = 1794844800L + 8 * 3600L   // 本地 11-17 08:00
        val todayStart = nowLocal8 - 8 * 3600L
        // 本地 11-19 08:00（后天）
        val target = todayStart + 2 * 86400L + 8 * 3600L
        val w = win(0, coreLenH = 4).copy(
            startEpochSec = target,
            peakEpochSec = target,
            coreStartEpochSec = target,
            coreEndEpochSec = target + 3 * 3600L,
        )
        val s = NextWindowFinder.formatWindowRangeWithDate(w, nowLocal8, tz)
        assertEquals("11/19 08:00–12:00", s, "后天窗口应显示 MM/DD 前缀")
    }
}
