package com.tidespot.feature.detail.detail

import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.tide.TideCalibrator
import com.tidespot.core.domain.window.BestWindowFinder

/**
 * 本点 Top10 / 现场校潮纯派生层（F-3.5 / §6.6 / §6.2b 规则 5；与 [Derivations] 同层，全纯 Kotlin 可 JVM 单测）。
 *
 * 红线自我约束：
 * - 缺测小时跳过、不插值（§5.0 缺值=null 禁 0 填充）；不足 2 个连续整点不算差分；
 * - 内陆全 null → 空 map / 空列表（语义 = [BestWindowFinder.find] 跳过同周期去重，§6.6 步骤 4）；
 * - 与详情页分数同源（cachedDetail slices → state.scores）→ 恒等天然成立（CONTRACT M5）。
 */
object TopWindowDerivations {

    /**
     * dh 一阶差分（m/h；潮汐同周期去重输入）：
     * 相邻整点均非 null 且间隔恰为 3600s 才产出；缺测小时跳过（不插值）、断开处不跨算。
     * 返回非空值 map（仅含可算条目）；空 map（内陆全 null / 不足 2 连续点）→ find 跳过去重。
     */
    fun tideRatesFromMarine(marine: List<HourlyMarine>): Map<Long, Double> {
        val sorted = marine.filter { it.seaLevelHeightMslM != null }.sortedBy { it.timeEpochSec }
        val out = LinkedHashMap<Long, Double>(sorted.size)
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val cur = sorted[i]
            if (cur.timeEpochSec - prev.timeEpochSec == 3600L) {
                out[cur.timeEpochSec] = cur.seaLevelHeightMslM!! - prev.seaLevelHeightMslM!!
            }
        }
        return out
    }

    /**
     * 模型极值时刻（§6.2b 高低潮）：一阶差分符号翻转点（+→− = 高潮、−→+ = 低潮）。
     * 翻转仅在两段差分共享同一小时（间隔恰 3600s）时判定，共享点时刻 = 极值时刻；
     * 缺测断开处不判定（不插值）；恒 0 差分（平潮）不产生极值。全 null（内陆）→ 空列表。
     */
    fun extremeTimes(marine: List<HourlyMarine>): List<Long> {
        val rates = tideRatesFromMarine(marine)
        val times = rates.keys.sorted()
        val out = mutableListOf<Long>()
        for (i in 1 until times.size) {
            val t0 = times[i - 1]
            val t1 = times[i]
            if (t1 - t0 != 3600L) continue
            val r0 = rates.getValue(t0)
            val r1 = rates.getValue(t1)
            if ((r0 > 0 && r1 < 0) || (r0 < 0 && r1 > 0)) out.add(t0)
        }
        return out
    }

    /**
     * 本点 Top10 现算（F-3.5）：find(scores（与详情页同源）, tideRates = dh 差分,
     * validHoursPresent = scores.size, horizonDays, nowEpochSec)。
     * scores 为空（天气不可用未评分）→ find 内置「有效数据不足 24 小时」禁用文案。
     *
     * @param nowEpochSec 时间域下限（CHG-15 偏差①）：调用方传「当前小时的下一整点」epoch 秒，
     *   历史小时不入选（SRS §6.6）；0 = 不过滤（既有单测口径）。
     */
    fun top10(
        scores: List<FishScoreResult>,
        marine: List<HourlyMarine>,
        horizonDays: Int = 7,
        nowEpochSec: Long = 0,
    ): BestWindowFinder.Output = BestWindowFinder.find(
        results = scores,
        tideRates = tideRatesFromMarine(marine),
        validHoursPresent = scores.size,
        horizonDays = horizonDays,
        nowEpochSec = nowEpochSec,
    )

    /** 现场校潮拟合（§6.2b 规则 5）：模型极值时刻由 [extremeTimes] 提取；≥2 条有效才启用（fitManual 内置，UI 只透传）。 */
    fun manualFit(
        observations: List<Long>,
        marine: List<HourlyMarine>,
    ): TideCalibrator.ManualTideFit = TideCalibrator.fitManual(observations, extremeTimes(marine))
}