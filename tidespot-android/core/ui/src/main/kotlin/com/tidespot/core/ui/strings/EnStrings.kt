package com.tidespot.core.ui.strings

/**
 * English copy implementation (resource mirror of `res/values-en/strings.xml`).
 *
 * 用途：JVM 单测与纯函数在英文口径下的取词实现（无 Android 依赖），
 * 由 `ZhStringsParityTest` 同时锁「两套实现键集一致」与「值等于对应 res 文件」。
 *
 * 本文件是 i18n 契约中**唯一允许非中文（英文）字面量的文案提供者**之一。
 */
@Suppress("LargeClass")
object EnStrings : TideSpotStrings {

    override val tabWave = "Wind & Waves"
    override val tabWeather = "Weather"
    override val tabScore = "Fishing Score"
    override val tabWaterQuality = "Water Quality"
    override val tabTide = "Tide"
    override val tabWaveInland = "Lake Wind & Water Temp"
    override val tabTideInland = "Water Level (no tide)"
    override val tabWaveLand = "Wind & Waves (est.)"
    override val tabWaterQualityLand = "Water Quality (est.)"
    override val tabTideLand = "Tide (unavailable)"
    override val noticeLand = "Land spot: water quality and tide are estimated or unavailable. Reference only."
    override val noticeUnknown =
        "Water body undetermined; it updates automatically when the data source recovers."

    override val disclaimerTitle = "Disclaimer"
    override val disclaimerEntry = "Disclaimer"
    override val disclaimerWaveTide =
        "Tide levels and currents come from a ~8 km global model and are officially not suitable for " +
            "coastal navigation. This app does not replace nautical guides or official tide tables."
    override val disclaimerWeather =
        "Thunderstorm, gale and high-wave judgments are for reference only. Water safety follows local " +
            "warnings; never fish during lightning, typhoons or spring tides."
    override val disclaimerScore =
        "The score is an empirical weighting of weather and astronomical factors and does not guarantee catch."
    override val disclaimerWaterQuality =
        "Most water-quality values in China are model estimates, not measurements; do not use them as a " +
            "basis for drinking, potable water or safe contact."
    override val disclaimerTideCorrection =
        "\"On-site tide calibration\" is your own recorded high-tide observations used as an empirical " +
            "correction of model lag (time shift); it is for relative timing reference only. Calibration does " +
            "not change the data source or accuracy and is still not an official tide table."

    override val badgeMeasured = "Measured"
    override val badgeSatellite = "Satellite"
    override val badgeModel = "Model"
    override val badgeEstimated = "Estimated"
    override val badgeUncalibrated = "Uncalibrated"
    override val badgeLowConfidence = "Low conf."

    override fun confidenceDescription(label: String): String = "Data confidence: $label"

    override val modelAttribution =
        "Weather · Open-Meteo (ICON / ECMWF models) · Ocean · Open-Meteo Marine / Copernicus Marine"

    override val waterBodyOcean = "Sea"
    override val waterBodyNearshore = "Nearshore"
    override val waterBodyInland = "Inland water"
    override val waterBodyLand = "Land"
    override val waterBodyUnknown = "Undetermined"

    override val infoCoordinatePrefix = "Coordinates"
    override val infoElevationPrefix = "Elevation"
    override val infoLastFetchedPrefix = "Last fetched"
    override val infoGroupPrefix = "Group"
    override val infoWaterBodyPrefix = "Water body"
    override val infoAccuracyNote =
        "Coordinates are the raw WGS84 pick values kept to 6 decimals (~0.11 m) with no offset correction"

    override val factorOxygen = "Dissolved O₂"
    override val factorPressure = "Pressure"
    override val factorSolunar = "Solunar"
    override val factorTide = "Tide"
    override val factorWind = "Wind"
    override val factorWeather = "Weather"
    override val factorDaypart = "Time of day"
    override val factorTempMatch = "Temp match"
    override val factorComfort = "Comfort"
    override val factorWaterQuality = "Water quality"

    override val factorBreakdownTitle = "Factor breakdown"
    override val factorDecay = "Decay ΠV"
    override val factorRawInput = "Raw input"
    override fun factorRow(name: String, subText: String, weightText: String): String =
        "$name: sub-score $subText, weight $weightText"

    override fun factorMultiplier(multiplierText: String, reason: String): String =
        "Multiplier $multiplierText, triggered by: $reason"

    override val timePattern = "MMM d, HH:mm"
    override val weekdayMon = "Mon"
    override val weekdayTue = "Tue"
    override val weekdayWed = "Wed"
    override val weekdayThu = "Thu"
    override val weekdayFri = "Fri"
    override val weekdaySat = "Sat"
    override val weekdaySun = "Sun"
    override fun today(clock: String): String = "Today $clock"
    override fun tomorrow(clock: String): String = "Tomorrow $clock"

    override fun chartHourTooltipSuffix(hour: Int, tooltip: String): String =
        ", selected hour $hour: $tooltip"

    override fun chartHourDescription(unit: String, points: Int): String =
        "24-hour chart in $unit. $points data points"

    override fun chartMissingSuffix(missing: Int): String =
        ", $missing hours missing (shown as gaps; table shows —)"

    override val chartScoreDescription = "24-hour fishing score bars, 0 to 100, 5 color bands"
    override fun chartWindowSuffix(hours: Int): String = ", $hours hours reach the prime window (≥80)"
    override fun chartHint(base: String, selected: String): String =
        "$base. Tap to inspect hourly values; see the hourly table below. $selected"

    override fun dayToday(weekday: String, date: String): String = "Today $weekday, $date"
    override fun dayFuture(offsetDays: Int, weekday: String, date: String): String =
        "Day $offsetDays $weekday, $date"

    override fun dayShortToday(weekday: String): String = "Today $weekday"
    override val dayTodayLabel = "Today"
    override val noDataHour = "No data for this hour"
    override val deviceZoneNote = "device time zone"
    override val unnamedWater = "Unnamed water"
    override val previewHint = "Moving; tap Confirm to switch"
    override val waterTemp = "Water temp"
    override val dissolvedOxygen = "Dissolved oxygen"
    override val turbidity = "Turbidity"

    override fun durationHours(hours: Int): String = "$hours h"
    override fun windowLine(start: String, peak: String, duration: String): String =
        "Start $start · Peak $peak · Duration $duration"
    override fun peakScoreLine(score: Int, tier: String): String = "$score pts · $tier"
    override val burstBadge = "Burst"
    override fun sustainedUntil(time: String): String = "Favorable until $time"
    override fun calibrationEnabled(lagHours: Int): String = "Enabled: lag ${lagHours}h"
    override val calibrationDisabled = "Not enabled"
    override val rawWind = "Wind"
    override val rawGust = "Gust"
    override val rawRain = "Rain"
    override val rawCloud = "Cloud"
    override val rawWave = "Wave"
    override val rawTideLevel = "Tide level"
    override val rawCurrent = "Current"
    override val rawTurbidity = "Turbidity"
    override val rawChlorophyll = "Chlorophyll"
    override fun hourWave(hour: Int, wave: String, period: String, direction: String): String =
        "Hour $hour: wave $wave, period $period, direction $direction"
    override fun hourWeather(hour: Int, temp: String, wind: String, pressure: String): String =
        "Hour $hour: temp $temp, wind $wind, MSL pressure $pressure"
    override fun hourScore(hour: Int, score: Int): String = "Hour $hour: fishing score $score"
    override fun hourScoreMissing(hour: Int): String = "Hour $hour: not scored"
    override fun hourWater(hour: Int, temp: String, doPotential: String): String =
        "Hour $hour: water temp $temp, DO potential $doPotential (estimated)"
    override fun hourTide(hour: Int, level: String, rate: String, current: String): String =
        "Hour $hour: tide level $level, rate $rate, current $current"
    override val hourMissing = "missing"
    override val sourceUnavailable = "Source unavailable"
    override fun dataUnusable(ageHours: Int): String = "Data unavailable (expired ${ageHours} h); not used for scoring"
    override val manualSame = "Matches"
    override fun manualLater(delta: String): String = "${delta}h later"
    override fun manualEarlier(delta: String): String = "${delta}h earlier"
    override fun manualCompare(type: String, model: String, delta: String): String =
        "Model $type $model · you recorded $delta"
    override val manualAnyType = "high/low tide"
    override fun manualLine(type: String, time: String): String = "$type · $time"
    override val manualTimeFormatError = "Time must be yyyy-MM-dd HH:mm"
    override fun manualOutOfRange(maxHours: Int): String = "Time outside the back-fill range (±${maxHours}h)"
    override fun tideBefore(before: String): String = "Before correction: $before"
    override fun tideAfter(lagHours: Int, after: String): String = "After correction (lag ${lagHours}h): $after"
    override fun tideHigh(times: String): String = "High tide $times"
    override fun tideLow(times: String): String = "Low tide $times"
    override val noSourceData = "No data from this source for this spot"
    override val unitScore = "pts"
    override val ungrouped = "Ungrouped"
    override val summaryNotLoaded = "Not loaded"
    override val staleLabel = "Stale"
    override val expiredLabel = "Expired"
    override fun dataPrefix(date: String, time: String): String = "Data $date $time"
    override val presetFreshLure = "Freshwater lure"
    override val presetSeaShore = "Saltwater shore"
    override val presetFreshBait = "Freshwater bait"
    override val errBatchDelete = "Batch delete failed; no changes were made"
    override val errBatchTag = "Batch tag edit failed; no changes were made"
    override val errBatchGroup = "Batch group edit failed; no changes were made"
    override val errNoCoordinate = "A new favorite needs coordinates (pick a spot on the map first)"
    override fun summaryLine(time: String, score: Int, tier: String): String = "$time · $score pts · $tier"
    override fun batchDeleteConfirm(count: Int): String =
        "Delete the $count selected favorites? This cannot be undone."

    override val bandExplosive = "Prime window"
    override val bandGreat = "Very good"
    override val bandOk = "Fair"
    override val bandPoor = "Poor"
    override val bandBad = "Avoid"
}