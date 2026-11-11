package com.tidespot.core.ui.band

import androidx.annotation.StringRes
import com.tidespot.core.domain.window.BestWindowFinder
import com.tidespot.core.ui.R
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.strings.ZhStrings

/**
 * 钓鱼指数 5 档色带（SRS §6.3 档位与色值，F-3.4 / §8.2-2）。
 * 色值为规格冻结值，禁止改动；OK 档 #FDD835 亮黄 → onColor 用深灰字（§8.2-6 对比度 ≥ 4.5:1）。
 * 档位文字由 [labelRes] 承载（N-7：档位同时有文字，不靠颜色单独编码）；
 * OPT-V11-13：文字走资源（[ResStrings] 取词），本枚举不存中文字面量。
 */
enum class ScoreBand(
    val minScore: Int,
    @StringRes val labelRes: Int,
    val argb: Long,
    val onArgb: Long,
) {
    /** ≥80 爆护窗口 #1B5E20 */
    EXPLOSIVE(80, R.string.ts_band_explosive, 0xFF1B5E20, 0xFFFFFFFF),

    /** 65–79 很适合 #43A047 */
    GREAT(65, R.string.ts_band_great, 0xFF43A047, 0xFFFFFFFF),

    /** 50–64 可以 #FDD835（深灰字） */
    OK(50, R.string.ts_band_ok, 0xFFFDD835, 0xFF263238),

    /** 35–49 较差 #FB8C00 */
    POOR(35, R.string.ts_band_poor, 0xFFFB8C00, 0xFF000000),

    /** <35 别去 #E53935 */
    BAD(0, R.string.ts_band_bad, 0xFFE53935, 0xFFFFFFFF),
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

        /** ≥80 分「窗口」横条阈值（§8.2-2：指数 Tab ≥80 段叠窗口横条）。 */
        const val WINDOW_THRESHOLD = 80
    }
}

/**
 * 档位文案（§6.3；[ScoreBand.labelRes] 与 domain 兜底档位同源）。
 * OPT-V11-13：统一从 [TideSpotStrings] 取词（默认 [ZhStrings]，零漂移；Android 侧传 [ResStrings]）。
 */
object BandTitles {

    /** 分数 → 档位文案（UI 入口；与 domain `BestWindowFinder.tierOf` 同档位边界）。 */
    fun fromDomainTier(score: Int, strings: TideSpotStrings = ZhStrings): String =
        when {
            score >= ScoreBand.WINDOW_THRESHOLD -> strings.bandExplosive
            score >= 65 -> strings.bandGreat
            score >= 50 -> strings.bandOk
            score >= 35 -> strings.bandPoor
            else -> strings.bandBad
        }

    /**
     * domain 档位字符串 → 本地化档位文案（跨语言映射）。
     * `:core:domain` 默认产出中文档位（`ZhDomainCopy`），英文语境下需要在 UI 侧按**分数**重取文案，
     * 不得直接透传中文串（否则切英文后档位仍为中文）。
     */
    fun localize(domainTier: String, peakScore: Int, strings: TideSpotStrings = ZhStrings): String =
        if (domainTier == BestWindowFinder.tierOf(peakScore)) fromDomainTier(peakScore, strings) else domainTier
}