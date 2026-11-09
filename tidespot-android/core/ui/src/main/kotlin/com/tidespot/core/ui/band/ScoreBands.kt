package com.tidespot.core.ui.band

import com.tidespot.core.domain.window.BestWindowFinder

/**
 * 钓鱼指数 5 档色带（SRS §6.3 档位与色值，F-3.4 / §8.2-2）。
 * 色值为规格冻结值，禁止改动；OK 档 #FDD835 亮黄 → onColor 用深灰字（§8.2-6 对比度 ≥ 4.5:1）。
 * 档位文字由 [label] 承载（N-7：档位同时有文字，不靠颜色单独编码）。
 */
enum class ScoreBand(
    val minScore: Int,
    val label: String,
    val argb: Long,
    val onArgb: Long,
) {
    /** ≥80 爆护窗口 #1B5E20 */
    EXPLOSIVE(80, "爆护窗口", 0xFF1B5E20, 0xFFFFFFFF),

    /** 65–79 很适合 #43A047 */
    GREAT(65, "很适合", 0xFF43A047, 0xFFFFFFFF),

    /** 50–64 可以 #FDD835（深灰字） */
    OK(50, "可以", 0xFFFDD835, 0xFF263238),

    /** 35–49 较差 #FB8C00 */
    POOR(35, "较差", 0xFFFB8C00, 0xFF000000),

    /** <35 别去 #E53935 */
    BAD(0, "别去", 0xFFE53935, 0xFFFFFFFF),
    ;

    companion object {

        /** 分数 → 档位（闭区间下界从高到低匹配；域内分数 0–100）。 */
        fun of(score: Int): ScoreBand {
            for (band in entries) {
                if (score >= band.minScore) return band
            }
            return BAD
        }

        /** 分数 → 档位色值（Long ARGB，供 Canvas/Compose 使用）。 */
        fun colorOf(score: Int): Long = of(score).argb

        /** 档位文字（§6.3/BestWindowFinder.tierOf 对齐口径：爆护窗口/很适合/可以/较差/别去）。 */
        fun labelOf(score: Int): String = of(score).label

        /** ≥80 分「窗口」横条阈值（§8.2-2：指数 Tab ≥80 段叠窗口横条）。 */
        const val WINDOW_THRESHOLD = 80
    }
}

/**
 * 与 domain 口径一致性：BestWindowFinder.tierOf 的档位字符串应与 [ScoreBand.labelOf] 一致
 * （入口/横条共用同一文案源，避免双源漂移）。
 */
object BandTitles {
    fun fromDomainTier(score: Int): String =
        if (score >= ScoreBand.WINDOW_THRESHOLD) ScoreBand.labelOf(score)
        else BestWindowFinder.tierOf(score)
}
