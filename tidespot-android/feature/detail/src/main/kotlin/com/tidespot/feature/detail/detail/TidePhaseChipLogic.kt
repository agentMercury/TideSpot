package com.tidespot.feature.detail.detail

import com.tidespot.core.domain.algorithm.Solunar
import com.tidespot.core.domain.tide.TidePhase
import java.time.LocalDate
import java.time.ZoneId

/**
 * UX-23（OPT-V11-23）潮汛 chip 纯函数层：选中当地日 → 当日正午月龄 → [TidePhase] 文案。
 *
 * - 月龄取**当日 12:00 当地**（一天内月龄变化 <0.05 天，不影响大潮/小潮判定）；
 * - 零网络请求（§6.2c 已有本地天文计算）；
 * - **仅展示层**：不参与评分（禁入 FishScoreCalculator，与水体判定隔离同等语义）。
 */
object TidePhaseChipLogic {

    /** 选中日 → chip 文案（「大潮 · 活汛」/「小潮 · 死汛」/ 过渡带仅「活汛」或「死汛」）。 */
    fun chipText(localDate: LocalDate, zone: ZoneId): String {
        val noonMillis = localDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        return TidePhase.labelFor(Solunar.lunarAge(noonMillis))
    }
}