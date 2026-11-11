package com.tidespot.core.ui.strings

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.tidespot.core.domain.strings.DomainCopy
import com.tidespot.core.domain.strings.ZhDomainCopy
import com.tidespot.core.domain.tide.TideObservationType
import com.tidespot.core.domain.tide.TidePhase
import com.tidespot.core.ui.R

/**
 * `:core:domain` 文案的 Android 实现（OPT-V11-13）：全部值来自资源，系统语言切换即生效。
 *
 * 注入点：`:app` 组装层把本实例传给 domain 调用
 * （`FishScoreCalculator.calculate(..., copy=)` / `BestWindowFinder.find(..., copy=)` /
 * `VetoEvaluator.evaluate(..., copy=)` / `TideCalibrator.fitManual(..., copy=)` / `TidePhase.labelFor(area, copy)`）。
 * 未注入时 domain 使用 [ZhDomainCopy]，行为与迁移前逐字一致。
 */
@Suppress("LargeClass")
class ResDomainCopy(private val res: Resources) : DomainCopy {

    private fun s(id: Int): String = res.getString(id)

    override val windowNotEnoughData get() = s(R.string.ts_domain_window_not_enough)

    override fun windowNoCandidate(horizonDays: Int, minScore: Int, best: Int?): String =
        if (best != null) {
            res.getString(R.string.ts_domain_window_no_candidate_best, horizonDays, minScore, best)
        } else {
            res.getString(R.string.ts_domain_window_no_candidate, horizonDays, minScore)
        }

    override fun windowNoCandidateInRange(minScore: Int, best: Int?): String =
        if (best != null) {
            res.getString(R.string.ts_domain_window_no_candidate_range_best, minScore, best)
        } else {
            res.getString(R.string.ts_domain_window_no_candidate_range, minScore)
        }

    override val warningSlackTide get() = s(R.string.ts_domain_warning_slack)
    override val warningGustOver40 get() = s(R.string.ts_domain_warning_gust)

    override fun manualCalibrationDelay(lagHours: Int): String =
        res.getString(R.string.ts_domain_manual_delay, lagHours)

    override fun manualCalibrationAhead(lagHours: Int): String =
        res.getString(R.string.ts_domain_manual_ahead, lagHours)

    override val manualCalibrationNoShift get() = s(R.string.ts_domain_manual_no_shift)
    override fun manualNeedMore(count: Int): String = res.getString(R.string.ts_domain_manual_need_more, count)
    override fun manualNoValidObservations(count: Int): String =
        res.getString(R.string.ts_domain_manual_no_valid, count)

    override fun manualLagExceeds(lag: Int, maxAbsLag: Int): String =
        res.getString(R.string.ts_domain_manual_lag_exceeds, lag, maxAbsLag)

    override fun tier(score: Int): String = when {
        score >= 80 -> s(R.string.ts_domain_tier_explosive)
        score >= 65 -> s(R.string.ts_domain_tier_great)
        score >= 50 -> s(R.string.ts_domain_tier_ok)
        score >= 35 -> s(R.string.ts_domain_tier_poor)
        else -> s(R.string.ts_domain_tier_bad)
    }

    override val vetoThunderstorm get() = s(R.string.ts_domain_veto_thunderstorm)
    override fun vetoDecay(decay: String, details: String): String =
        res.getString(R.string.ts_domain_veto_decay, decay, details)

    override fun vetoWind(wind: String, gust: String): String =
        res.getString(R.string.ts_domain_veto_wind, wind, gust)

    override fun vetoPressure(d3: String): String = res.getString(R.string.ts_domain_veto_pressure, d3)
    override fun vetoRain(rain: String): String = res.getString(R.string.ts_domain_veto_rain, rain)
    override fun vetoWave(wave: String): String = res.getString(R.string.ts_domain_veto_wave, wave)
    override fun vetoLowOxygen(doPotentialMgL: String): String =
        res.getString(R.string.ts_domain_veto_low_oxygen, doPotentialMgL)

    @StringRes
    private fun tideSizeRes(size: TidePhase.TideSize): Int? = when (size) {
        TidePhase.TideSize.SPRING -> R.string.ts_domain_tide_size_spring
        TidePhase.TideSize.NEAP -> R.string.ts_domain_tide_size_neap
        TidePhase.TideSize.NONE -> null
    }

    override fun tideSizeLabel(size: TidePhase.TideSize): String? = tideSizeRes(size)?.let(::s)

    override fun tideCurrentLabel(current: TidePhase.TideCurrent): String = when (current) {
        TidePhase.TideCurrent.ACTIVE -> s(R.string.ts_domain_tide_current_active)
        TidePhase.TideCurrent.DEAD -> s(R.string.ts_domain_tide_current_dead)
    }

    override fun observationTypeLabel(type: TideObservationType): String = when (type) {
        TideObservationType.HIGH -> s(R.string.ts_domain_obs_high)
        TideObservationType.LOW -> s(R.string.ts_domain_obs_low)
        TideObservationType.RISING_START -> s(R.string.ts_domain_obs_rising_start)
        TideObservationType.FALLING_START -> s(R.string.ts_domain_obs_falling_start)
    }

    override fun presetLabel(presetId: String): String = when (presetId) {
        "fresh_lure" -> s(R.string.ts_domain_preset_fresh_lure)
        "sea_shore" -> s(R.string.ts_domain_preset_sea_shore)
        "fresh_bait" -> s(R.string.ts_domain_preset_fresh_bait)
        else -> presetId
    }

    override fun solunarDetail(age: String, illumination: String, transit: String): String =
        res.getString(R.string.ts_domain_solunar_detail, age, illumination, transit)

    override val trendInsufficient get() = s(R.string.ts_domain_trend_insufficient)
    override val hourlyGranularity get() = s(R.string.ts_domain_hourly)
    override val credentialInvalid get() = s(R.string.ts_domain_credential_invalid)
    override val networkError get() = s(R.string.ts_domain_network_error)
    override val waterQualityProxyNote get() = s(R.string.ts_domain_water_quality_note)

    companion object {
        fun of(res: Resources): DomainCopy = ResDomainCopy(res)
    }
}

/** Composable 侧取 domain 文案（随语言变更重建）。 */
@Composable
fun localDomainCopy(): DomainCopy {
    val res = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    return remember(res, configuration) { ResDomainCopy(res) }
}