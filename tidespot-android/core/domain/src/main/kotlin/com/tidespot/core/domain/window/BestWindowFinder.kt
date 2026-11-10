package com.tidespot.core.domain.window

import com.tidespot.core.domain.model.BestWindow
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.model.VetoCode
import kotlin.math.abs
import kotlin.math.min

/**
 * 未来 7 天最佳窗口 Top10（SRS §6.6）。
 *
 * 步骤：候选（Score≥60 且非雷暴否决）→ 相邻间隔 ≤1h 合并 → rank=0.62·peak+0.23·mean+1.4·min(len,4)
 * → 同潮汐周期去重（dh 符号连续段；内陆跳过）→ rank 降序取 10。
 * 边界：全部 <60 → fallbackMessage 给出"未来 7 天无 ≥60 分窗口，最高 X 分（时刻…）"；
 * 有效数据 <24 h → 禁用并提示；含否决的小时仍可入选但注意事项必须显示。
 */
object BestWindowFinder {

    /** 输出：Top10 窗口 + 兜底提示（null = 有窗口）+ 兜底最高分时刻（M5：全部 <60 时 = 最高分小时 timeEpochSec，时刻渲染在 UI 层；有窗口/禁用时为 null）。 */
    data class Output(
        val windows: List<BestWindow>,
        val fallbackMessage: String?,
        val fallbackBestEpochSec: Long? = null,
    )

    // OPT-V11-26（D12/CHG-23/27）：候选阈值 60 → 75（"很适合"档中部，Top10 名实相符）。
    // **单点常量**：现算侧（本 find）与 CHG-14② 读侧重建共用同一入口（读侧走 find，禁另写字面量）。
    private const val CANDIDATE_MIN = 75
    private const val MERGE_GAP_HOURS = 1
    private const val DEDUP_MIDPOINT_HOURS = 2.5
    private const val TOP_N = 10

    /** OPT-V11-25（D11）：核心段半宽（峰值 ±2h，跨度 ≤4h）。 */
    private const val CORE_HALF_HOURS = 2

    /**
     * @param results 逐小时分数（升序，≤240 h）
     * @param tideRates 可选的逐小时 dh（m/h）；null/缺键视为内陆，跳过同周期去重
     * @param validHoursPresent 有效数据小时数（<24 时禁用）
     * @param horizonDays 用户设置 N=7 或 10
     * @param nowEpochSec 时间域下限（CHG-15 偏差①，SRS §6.6「从当前小时的下一整点起」）：
     *   >0 时剔除 timeEpochSec < nowEpochSec 的历史小时（调用方传入下一整点 epoch 秒）；
     *   0 = 不过滤（向后兼容，既有单测口径）。过滤后有效小时数同步收缩（不足 24 → 禁用），
     *   兜底「最高分」也只在未来序列内取——历史窗口不得出现在「未来最佳窗口」中。
     */
    fun find(
        results: List<FishScoreResult>,
        tideRates: Map<Long, Double>? = null,
        validHoursPresent: Int = results.size,
        horizonDays: Int = 7,
        nowEpochSec: Long = 0,
        // OPT-V11-03（CHG-27）：输出时间域筛选（只过滤结果，不改候选/rank/去重公式）；
        // 0/0 = 不筛选（现有全窗行为，恒等锁）。相交判定用**原窗**（利好延续期内仍值得入选）。
        windowStartEpochSec: Long = 0,
        windowEndEpochSec: Long = 0,
    ): Output {
        // 0) 时间域过滤（CHG-15 偏差①）：results 升序，filter 保序；nowEpochSec=0 时零开销直通
        val inHorizon = if (nowEpochSec > 0) results.filter { it.timeEpochSec >= nowEpochSec } else results
        val effectiveHours = if (nowEpochSec > 0) minOf(validHoursPresent, inHorizon.size) else validHoursPresent
        if (effectiveHours < 24) {
            return Output(emptyList(), "有效数据不足 24 小时，最佳窗口功能暂不可用")
        }

        // 1) 候选集：Score≥60 且未触发雷暴否决（含否决小时由 warnings 提示）
        val candidates = inHorizon.filter { it.total >= CANDIDATE_MIN && !hasThunderstorm(it) }

        // 2) 合并窗口：按时间升序，相邻且间隔 ≤1h 并为一段
        val merged = ArrayList<CandidateWindow>()
        for (r in candidates) {
            val last = merged.lastOrNull()
            if (last != null && (r.timeEpochSec - last.endEpoch) <= MERGE_GAP_HOURS * 3600L) {   // FO-13（M5）：间隔 ≤1h（SRS §6.6 步骤 2；旧实现 5400s 违规）
                last.endEpoch = r.timeEpochSec
                last.hours.add(r)
            } else {
                merged.add(CandidateWindow(r.timeEpochSec, r.timeEpochSec, mutableListOf(r)))
            }
        }
        if (merged.isEmpty()) {
            // 全部 <60 → 兜底文案（最高分只在未来序列内取，CHG-15 偏差①）
            val best = inHorizon.maxByOrNull { it.total }
            val msg = if (best != null) {
                "未来 $horizonDays 天无 ≥$CANDIDATE_MIN 分窗口，最高 ${best.total} 分"
            } else {
                "未来 $horizonDays 天无 ≥$CANDIDATE_MIN 分窗口"
            }
            return Output(emptyList(), msg, best?.timeEpochSec)
        }

        // 3) 核心段派生 + rank（OPT-V11-25/D11）：rank = 0.62·peak + 0.23·coreMean + 1.4·min(coreLen,4)
        //    —— **系数不变**（附录 C-4 禁顺手美化）；core = 峰值 −2h..+2h 且夹在原窗内；原窗 ≤4h 时 core=原窗（恒等）。
        val ranked = merged.map { w ->
            val peakHour = w.hours.maxBy { it.total }
            val peak = peakHour.total
            val peakEpoch = peakHour.timeEpochSec
            val coreStart = maxOf(w.startEpoch, peakEpoch - CORE_HALF_HOURS * 3600L)
            val coreEnd = minOf(w.endEpoch, peakEpoch + CORE_HALF_HOURS * 3600L)
            val coreHours = w.hours.filter { it.timeEpochSec in coreStart..coreEnd }
            val coreMean = if (coreHours.isEmpty()) peak.toDouble() else coreHours.map { it.total }.average()
            val coreLen = coreHours.size.coerceAtLeast(1)
            val rank = 0.62 * peak + 0.23 * coreMean + 1.4 * min(coreLen, 4)
            RankedWindow(
                startEpoch = w.startEpoch,
                endEpoch = w.endEpoch,
                peak = peak,
                peakHour = peakHour,
                mean = coreMean,
                len = coreLen,
                rank = rank,
                coreStartEpoch = coreStart,
                coreEndEpoch = coreEnd,
            )
        }

        // 4) 同周期去重：两窗中点差 <2.5h 且同属一个潮汐周期（dh 符号连续段定义）→ 仅保留 rank 高者
        //    （OPT-V11-25：中点仍用**原窗**，语义不变）
        val deduped = if (tideRates.isNullOrEmpty()) ranked else dedupByTideCycle(ranked, tideRates)

        // 5) rank 降序 → OPT-V11-03 时段筛选（原窗相交）→ 前 10
        val sorted = deduped.sortedByDescending { it.rank }
        val filteredOut = if (windowStartEpochSec > 0 && windowEndEpochSec > windowStartEpochSec) {
            sorted.filter { it.startEpoch <= windowEndEpochSec && it.endEpoch >= windowStartEpochSec }
        } else {
            sorted
        }
        val top = filteredOut.take(TOP_N).map { w ->
            val coreIsWholeWindow = w.coreStartEpoch == w.startEpoch && w.coreEndEpoch == w.endEpoch
            BestWindow(
                startEpochSec = w.startEpoch,
                peakEpochSec = w.peakHour.timeEpochSec,
                durationHours = w.len,
                peakScore = w.peak,
                rank = w.rank,
                tier = tierOf(w.peak),
                topFactors = topFactors(w.peakHour),
                warnings = warningsOf(w.peakHour, tideRates),
                coreStartEpochSec = w.coreStartEpoch,
                coreEndEpochSec = w.coreEndEpoch,
                // 利好延续：仅当核心段短于原窗（UI 附注「利好持续至 HH:mm」；null = 核心即原窗）
                truncatedUntilEpochSec = if (coreIsWholeWindow) null else w.endEpoch,
            )
        }
        if (top.isNotEmpty()) return Output(top, null)

        // 5b) 时段筛选后为空（或全窗本就为空）：兜底文案只在**筛选时段内**取最高分参考
        val best = if (windowStartEpochSec > 0 && windowEndEpochSec > windowStartEpochSec) {
            inHorizon.filter { it.timeEpochSec in windowStartEpochSec..windowEndEpochSec }.maxByOrNull { it.total }
        } else {
            inHorizon.maxByOrNull { it.total }
        }
        val msg = when {
            windowStartEpochSec > 0 && windowEndEpochSec > windowStartEpochSec ->
                if (best != null) {
                    "所选时段无 ≥$CANDIDATE_MIN 分窗口，时段内最高 ${best.total} 分"
                } else {
                    "所选时段无 ≥$CANDIDATE_MIN 分窗口"
                }
            best != null -> "未来 $horizonDays 天无 ≥$CANDIDATE_MIN 分窗口，最高 ${best.total} 分"
            else -> "未来 $horizonDays 天无 ≥$CANDIDATE_MIN 分窗口"
        }
        return Output(emptyList(), msg, best?.timeEpochSec)
    }

    /** 6) 跨收藏点模式：各点独立跑 1–5，再按 rank 全局排序取 10（由调用方拼接后调 [rankAcrossSpots]）。 */
    fun rankAcrossSpots(perSpot: List<Pair<String, List<BestWindow>>>): List<Pair<String, BestWindow>> =
        perSpot.flatMap { (name, ws) -> ws.map { name to it } }
            .sortedByDescending { it.second.rank }   // FO-14（M5）：全局序按窗口 rank（长窗加权可反超高 peak 短窗），非 peakScore
            .take(TOP_N)

    // ---------- 内部 ----------

    private class CandidateWindow(val startEpoch: Long, var endEpoch: Long, val hours: MutableList<FishScoreResult>)

    private class RankedWindow(
        val startEpoch: Long,
        val endEpoch: Long,
        val peak: Int,
        val peakHour: FishScoreResult,
        /** OPT-V11-25：核心段均值（rank 用；原窗 ≤4h 时 = 原窗均值）。 */
        val mean: Double,
        /** OPT-V11-25：核心段小时数（≤4；rank 用）。 */
        val len: Int,
        val rank: Double,
        val coreStartEpoch: Long,
        val coreEndEpoch: Long,
    )

    private fun hasThunderstorm(r: FishScoreResult): Boolean =
        // CHG-16①/OPT-M5-04/FO-01：筛选只认机器码 VetoCode.THUNDERSTORM，禁再 contains("雷暴")——
        // 文案随 i18n 可变，子串判定会漂移；codes 与 reasons 同步产生/同步为空（对空列表自然为 false）
        r.vetoCodes.contains(VetoCode.THUNDERSTORM)

    /** 同周期去重：中点差 <2.5h 且两窗峰值小时同属一个 dh 符号连续段。 */
    private fun dedupByTideCycle(windows: List<RankedWindow>, tideRates: Map<Long, Double>): List<RankedWindow> {
        val result = ArrayList<RankedWindow>()
        for (w in windows.sortedByDescending { it.rank }) {
            val midW = (w.startEpoch + w.endEpoch) / 2
            val dup = result.any { kept ->
                val midK = (kept.startEpoch + kept.endEpoch) / 2
                abs(midW - midK) < DEDUP_MIDPOINT_HOURS * 3600 &&
                    sameTideCycle(w.peakHour.timeEpochSec, kept.peakHour.timeEpochSec, tideRates)
            }
            if (!dup) result.add(w)
        }
        return result
    }

    /** dh 符号连续段：从 a 时刻到 b 时刻之间 dh 未变号 → 同一涨/落潮周期。 */
    private fun sameTideCycle(a: Long, b: Long, tideRates: Map<Long, Double>): Boolean {
        val from = minOf(a, b)
        val to = maxOf(a, b)
        val signs = (from..to step 3600).mapNotNull { t ->
            tideRates[t]?.let { if (it >= 0) 1 else -1 }
        }
        if (signs.isEmpty()) return false
        return signs.distinct().size == 1
    }

    /** 主因：Σw_k·s_k 贡献最高的 2 项因子 id。 */
    private fun topFactors(peakHour: FishScoreResult): List<String> =
        peakHour.factors.sortedByDescending { it.sub * it.weight }.take(2).map { it.id }

    private fun warningsOf(peakHour: FishScoreResult, tideRates: Map<Long, Double>?): List<String> {
        val warnings = mutableListOf<String>()
        peakHour.vetoReasons.firstOrNull()?.let { warnings.add(it) }
        if (tideRates != null) {
            val dh = tideRates[peakHour.timeEpochSec]
            if (dh != null && abs(dh) < 0.02) warnings.add("临近平潮/停流，潮流交换弱")
        }
        val gust = peakHour.factors.firstOrNull { it.id == "wind" }?.detail
        if (gust != null && gust.toDoubleOrNull()?.let { it > 40 } == true) warnings.add("阵风超过 40 km/h，注意抛投安全")
        return warnings
    }

    /** 档位文案（SRS §6.3）。 */
    fun tierOf(score: Int): String = when {
        score >= 80 -> "爆护窗口"
        score >= 65 -> "很适合"
        score >= 50 -> "可以"
        score >= 35 -> "较差"
        else -> "别去"
    }


}
