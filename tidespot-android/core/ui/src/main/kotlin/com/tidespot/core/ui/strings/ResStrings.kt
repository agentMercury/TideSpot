package com.tidespot.core.ui.strings

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.tidespot.core.ui.R

/**
 * Android 运行期文案实现：**所有值来自资源文件**（`values/strings.xml` / `values-en/strings.xml`），
 * 系统语言切换后 `Resources` 配置更新，重组即取到新语言文案（N-8）。
 *
 * 非 Composable 纯逻辑请显式传入本类实例（`ResStrings(context.resources)`），
 * 不要在逻辑层读取全局 Locale——保证单测可注入 [ZhStrings] / [EnStrings]。
 */
@Suppress("LargeClass")
class ResStrings(private val res: Resources) : TideSpotStrings {

    private fun s(id: Int): String = res.getString(id)

    override val tabWave get() = s(R.string.ts_tab_wave)
    override val tabWeather get() = s(R.string.ts_tab_weather)
    override val tabScore get() = s(R.string.ts_tab_score)
    override val tabWaterQuality get() = s(R.string.ts_tab_water_quality)
    override val tabTide get() = s(R.string.ts_tab_tide)
    override val tabWaveInland get() = s(R.string.ts_tab_wave_inland)
    override val tabTideInland get() = s(R.string.ts_tab_tide_inland)
    override val tabWaveLand get() = s(R.string.ts_tab_wave_land)
    override val tabWaterQualityLand get() = s(R.string.ts_tab_water_quality_land)
    override val tabTideLand get() = s(R.string.ts_tab_tide_land)
    override val noticeLand get() = s(R.string.ts_notice_land)
    override val noticeUnknown get() = s(R.string.ts_notice_unknown)

    override val disclaimerTitle get() = s(R.string.ts_disclaimer_title)
    override val disclaimerEntry get() = s(R.string.ts_disclaimer_entry)
    override val disclaimerWaveTide get() = s(R.string.ts_disclaimer_wave_tide)
    override val disclaimerWeather get() = s(R.string.ts_disclaimer_weather)
    override val disclaimerScore get() = s(R.string.ts_disclaimer_score)
    override val disclaimerWaterQuality get() = s(R.string.ts_disclaimer_water_quality)
    override val disclaimerTideCorrection get() = s(R.string.ts_disclaimer_tide_correction)

    override val badgeMeasured get() = s(R.string.ts_badge_measured)
    override val badgeSatellite get() = s(R.string.ts_badge_satellite)
    override val badgeModel get() = s(R.string.ts_badge_model)
    override val badgeEstimated get() = s(R.string.ts_badge_estimated)
    override val badgeUncalibrated get() = s(R.string.ts_badge_uncalibrated)
    override val badgeLowConfidence get() = s(R.string.ts_badge_low_confidence)

    override fun confidenceDescription(label: String): String =
        res.getString(R.string.ts_confidence_description, label)

    override val modelAttribution get() = s(R.string.ts_model_attribution)
    override val waterBodyOcean get() = s(R.string.ts_water_body_ocean)
    override val waterBodyNearshore get() = s(R.string.ts_water_body_nearshore)
    override val waterBodyInland get() = s(R.string.ts_water_body_inland)
    override val waterBodyLand get() = s(R.string.ts_water_body_land)
    override val waterBodyUnknown get() = s(R.string.ts_water_body_unknown)

    override val infoCoordinatePrefix get() = s(R.string.ts_info_coordinate_prefix)
    override val infoElevationPrefix get() = s(R.string.ts_info_elevation_prefix)
    override val infoLastFetchedPrefix get() = s(R.string.ts_info_last_fetched_prefix)
    override val infoGroupPrefix get() = s(R.string.ts_info_group_prefix)
    override val infoWaterBodyPrefix get() = s(R.string.ts_info_water_body_prefix)
    override val infoAccuracyNote get() = s(R.string.ts_info_accuracy_note)

    override val factorOxygen get() = s(R.string.ts_factor_oxygen)
    override val factorPressure get() = s(R.string.ts_factor_pressure)
    override val factorSolunar get() = s(R.string.ts_factor_solunar)
    override val factorTide get() = s(R.string.ts_factor_tide)
    override val factorWind get() = s(R.string.ts_factor_wind)
    override val factorWeather get() = s(R.string.ts_factor_weather)
    override val factorDaypart get() = s(R.string.ts_factor_daypart)
    override val factorTempMatch get() = s(R.string.ts_factor_temp_match)
    override val factorComfort get() = s(R.string.ts_factor_comfort)
    override val factorWaterQuality get() = s(R.string.ts_factor_water_quality)

    override val factorBreakdownTitle get() = s(R.string.ts_factor_breakdown_title)
    override val factorDecay get() = s(R.string.ts_factor_decay)
    override val factorRawInput get() = s(R.string.ts_factor_raw_input)
    override fun factorRow(name: String, subText: String, weightText: String): String =
        res.getString(R.string.ts_factor_row, name, subText, weightText)

    override fun factorMultiplier(multiplierText: String, reason: String): String =
        res.getString(R.string.ts_factor_multiplier, multiplierText, reason)

    override val timePattern get() = s(R.string.ts_time_pattern)
    override val weekdayMon get() = s(R.string.ts_weekday_mon)
    override val weekdayTue get() = s(R.string.ts_weekday_tue)
    override val weekdayWed get() = s(R.string.ts_weekday_wed)
    override val weekdayThu get() = s(R.string.ts_weekday_thu)
    override val weekdayFri get() = s(R.string.ts_weekday_fri)
    override val weekdaySat get() = s(R.string.ts_weekday_sat)
    override val weekdaySun get() = s(R.string.ts_weekday_sun)
    override fun today(clock: String): String = res.getString(R.string.ts_today, clock)
    override fun tomorrow(clock: String): String = res.getString(R.string.ts_tomorrow, clock)

    override fun chartHourTooltipSuffix(hour: Int, tooltip: String): String =
        res.getString(R.string.ts_chart_hour_tooltip_suffix, hour, tooltip)

    override fun chartHourDescription(unit: String, points: Int): String =
        res.getString(R.string.ts_chart_hour_desc, unit, points)

    override fun chartMissingSuffix(missing: Int): String =
        res.getString(R.string.ts_chart_missing_suffix, missing)

    override val chartScoreDescription get() = s(R.string.ts_chart_score_desc)
    override fun chartWindowSuffix(hours: Int): String = res.getString(R.string.ts_chart_window_suffix, hours)
    override fun chartHint(base: String, selected: String): String =
        res.getString(R.string.ts_chart_hint, base, selected)

    override fun dayToday(weekday: String, date: String): String =
        res.getString(R.string.ts_day_today, weekday, date)

    override fun dayFuture(offsetDays: Int, weekday: String, date: String): String =
        res.getString(R.string.ts_day_future, offsetDays, weekday, date)

    override fun dayShortToday(weekday: String): String = res.getString(R.string.ts_day_short_today, weekday)
    override val noDataHour get() = s(R.string.ts_no_data_hour)
    override val deviceZoneNote get() = s(R.string.ts_device_zone_note)
    override val unnamedWater get() = s(R.string.ts_info_unnamed_water)
    override val previewHint get() = s(R.string.ts_info_preview_hint)
    override val waterTemp get() = s(R.string.ts_water_temp)
    override val dissolvedOxygen get() = s(R.string.ts_dissolved_oxygen)
    override val turbidity get() = s(R.string.ts_turbidity)

    override fun durationHours(hours: Int): String = res.getString(R.string.ts_d2_duration_hours, hours)
    override fun windowLine(start: String, peak: String, duration: String): String =
        res.getString(R.string.ts_d2_window_line, start, peak, duration)

    override fun peakScoreLine(score: Int, tier: String): String =
        res.getString(R.string.ts_d2_peak_score_line, score, tier)

    override val burstBadge get() = s(R.string.ts_d2_burst)
    override fun sustainedUntil(time: String): String = res.getString(R.string.ts_d2_sustained, time)
    override fun calibrationEnabled(lagHours: Int): String = res.getString(R.string.ts_d2_calib_on, lagHours)
    override val calibrationDisabled get() = s(R.string.ts_d2_calib_off)
    override val rawWind get() = s(R.string.ts_d2_raw_wind)
    override val rawGust get() = s(R.string.ts_d2_raw_gust)
    override val rawRain get() = s(R.string.ts_d2_raw_rain)
    override val rawCloud get() = s(R.string.ts_d2_raw_cloud)
    override val rawWave get() = s(R.string.ts_d2_raw_wave)
    override val rawTideLevel get() = s(R.string.ts_d2_raw_tide_level)
    override val rawCurrent get() = s(R.string.ts_d2_raw_current)
    override val rawTurbidity get() = s(R.string.ts_d2_raw_turbidity)
    override val rawChlorophyll get() = s(R.string.ts_d2_raw_chlorophyll)

    override fun hourWave(hour: Int, wave: String, period: String, direction: String): String =
        res.getString(R.string.ts_d2_hour_wave, hour, wave, period, direction)

    override fun hourWeather(hour: Int, temp: String, wind: String, pressure: String): String =
        res.getString(R.string.ts_d2_hour_weather, hour, temp, wind, pressure)

    override fun hourScore(hour: Int, score: Int): String = res.getString(R.string.ts_d2_hour_score, hour, score)
    override fun hourScoreMissing(hour: Int): String = res.getString(R.string.ts_d2_hour_score_missing, hour)

    override fun hourWater(hour: Int, temp: String, doPotential: String): String =
        res.getString(R.string.ts_d2_hour_water, hour, temp, doPotential)

    override fun hourTide(hour: Int, level: String, rate: String, current: String): String =
        res.getString(R.string.ts_d2_hour_tide, hour, level, rate, current)

    override val hourMissing get() = s(R.string.ts_d2_hour_missing)
    override val sourceUnavailable get() = s(R.string.ts_d2_source_unavailable)
    override fun dataUnusable(ageHours: Int): String = res.getString(R.string.ts_d2_unusable, ageHours)
    override val manualSame get() = s(R.string.ts_d2_manual_same)
    override fun manualLater(delta: String): String = res.getString(R.string.ts_d2_manual_later, delta)
    override fun manualEarlier(delta: String): String = res.getString(R.string.ts_d2_manual_earlier, delta)

    override fun manualCompare(type: String, model: String, delta: String): String =
        res.getString(R.string.ts_d2_manual_compare, type, model, delta)

    override val manualAnyType get() = s(R.string.ts_d2_manual_any)
    override fun manualLine(type: String, time: String): String = res.getString(R.string.ts_d2_manual_line, type, time)
    override val manualTimeFormatError get() = s(R.string.ts_d2_manual_time_err)
    override fun manualOutOfRange(maxHours: Int): String = res.getString(R.string.ts_d2_manual_range_err, maxHours)
    override fun tideBefore(before: String): String = res.getString(R.string.ts_d2_tide_before, before)
    override fun tideAfter(lagHours: Int, after: String): String =
        res.getString(R.string.ts_d2_tide_after, lagHours, after)

    override fun tideHigh(times: String): String = res.getString(R.string.ts_d2_tide_high, times)
    override fun tideLow(times: String): String = res.getString(R.string.ts_d2_tide_low, times)
    override val noSourceData get() = s(R.string.ts_d2_no_source)
    override val unitScore get() = s(R.string.ts_d2_unit_score)
    override val ungrouped get() = s(R.string.ts_fx_ungrouped)
    override val summaryNotLoaded get() = s(R.string.ts_fx_not_loaded)
    override val staleLabel get() = s(R.string.ts_fx_stale)
    override val expiredLabel get() = s(R.string.ts_fx_expired)
    override fun dataPrefix(date: String, time: String): String = res.getString(R.string.ts_fx_data_prefix, date, time)
    override val presetFreshLure get() = s(R.string.ts_fx_preset_fresh_lure)
    override val presetSeaShore get() = s(R.string.ts_fx_preset_sea_shore)
    override val presetFreshBait get() = s(R.string.ts_fx_preset_fresh_bait)
    override val errBatchDelete get() = s(R.string.ts_fx_err_batch_delete)
    override val errBatchTag get() = s(R.string.ts_fx_err_batch_tag)
    override val errBatchGroup get() = s(R.string.ts_fx_err_batch_group)
    override val errNoCoordinate get() = s(R.string.ts_fx_err_no_coord)
    override fun summaryLine(time: String, score: Int, tier: String): String =
        res.getString(R.string.ts_fx_summary_line, time, score, tier)

    override fun batchDeleteConfirm(count: Int): String = res.getString(R.string.ts_fx_batch_delete_confirm, count)

    override val bandExplosive get() = s(R.string.ts_band_explosive)
    override val bandGreat get() = s(R.string.ts_band_great)
    override val bandOk get() = s(R.string.ts_band_ok)
    override val bandPoor get() = s(R.string.ts_band_poor)
    override val bandBad get() = s(R.string.ts_band_bad)

    companion object {
        /** 便捷：任意 Context / Resources 取文案实现。 */
        fun of(res: Resources): TideSpotStrings = ResStrings(res)
    }
}

/** 默认 null = 未注入（由 [localTideSpotStrings] 回落本机资源）；注入值优先（测试可注入 [ZhStrings]）。 */
private val LocalTideSpotStrings: ProvidableCompositionLocal<TideSpotStrings?> =
    staticCompositionLocalOf { null }

/** Composable 取词入口：随系统语言（Configuration）变化重建，无需手动重启。 */
@Composable
fun tideSpotStrings(): TideSpotStrings {
    val res = LocalContext.current.resources
    // 读取 Configuration 以登记「语言变更 → 重组」依赖（values-en 切换后立即生效）
    val configuration = LocalConfiguration.current
    return remember(res, configuration) { ResStrings(res) }
}

/**
 * 组装层注入（`:app` `MainActivity`）：把 `TideSpotStrings` 下发到整棵 Compose 树，
 * 供 `stringResource` 无法覆盖的纯函数取词（如 `WaterBodyTabTitles`）。
 */
@Composable
fun ProvideTideSpotStrings(
    strings: TideSpotStrings = tideSpotStrings(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTideSpotStrings provides strings, content = content)
}

/** 纯函数侧取词（在 Composable 作用域内使用）：优先取注入实现，未注入则读本机资源。 */
@Composable
fun localTideSpotStrings(): TideSpotStrings = LocalTideSpotStrings.current ?: tideSpotStrings()