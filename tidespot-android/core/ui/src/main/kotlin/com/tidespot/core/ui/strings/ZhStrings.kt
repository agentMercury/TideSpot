package com.tidespot.core.ui.strings

/**
 * 中文默认文案实现（**资源镜像**，与 `res/values/strings.xml` 逐字一致）。
 *
 * 存在理由：`:core:ui` 的纯函数（[com.tidespot.core.ui.titles.WaterBodyTabTitles]、
 * [com.tidespot.core.ui.format.RelativeTimeFormatter]）与 JVM 单测无法访问 Android 资源，
 * 需要一个无 Android 依赖的取词实现。Android 运行期一律走 [ResStrings]（读 res），
 * 两份值由 `ZhStringsParityTest` 逐键锁死（禁止双源漂移）。
 *
 * 本文件是 i18n 契约中**唯一允许中文字面量的代码位置之一**（红线扫描白名单）。
 */
object ZhStrings : TideSpotStrings {

    override val tabWave = "风浪"
    override val tabWeather = "天气"
    override val tabScore = "钓鱼指数"
    override val tabWaterQuality = "水质"
    override val tabTide = "潮流"
    override val tabWaveInland = "湖面风与水温"
    override val tabTideInland = "水位（无潮汐）"
    override val tabWaveLand = "风浪（估算）"
    override val tabWaterQualityLand = "水质（估算）"
    override val tabTideLand = "潮流（不可用）"
    override val noticeLand = "陆地点：水质与潮流为估算或不可用，仅供参考"
    override val noticeUnknown = "水体类型待判定：数据源恢复后自动更新"

    override val disclaimerTitle = "免责声明"
    override val disclaimerEntry = "免责声明"
    override val disclaimerWaveTide =
        "潮位与潮流来自 ~8 km 全球模型，官方声明不适用于近岸航行；本 App 不替代航路指南与官方潮汐表。"
    override val disclaimerWeather =
        "雷暴、大风、大浪判定仅供参考。水上安全以当地预警为准，切勿在雷电、台风、大潮汛期间出钓。"
    override val disclaimerScore = "指数基于气象与天文因子的经验加权估算，不构成渔获保证。"
    override val disclaimerWaterQuality = "中国境内多数点位水质为模型估算值，非实测，不作为用水、饮用或接触安全依据。"
    override val disclaimerTideCorrection =
        "「现场校潮」为用户自记高潮观测对模型滞后的经验修正（lag 平移），仅用于相对时刻参考；" +
            "校正不改变数据来源与精度，仍非官方潮汐表。"

    override val badgeMeasured = "实测"
    override val badgeSatellite = "卫星"
    override val badgeModel = "模型"
    override val badgeEstimated = "估算"
    override val badgeUncalibrated = "未校正"
    override val badgeLowConfidence = "低置信"

    override fun confidenceDescription(label: String): String = "数据置信：$label"

    override val modelAttribution =
        "天气 · Open-Meteo（ICON / ECMWF 模型） · 海洋 · Open-Meteo Marine / Copernicus Marine"

    override val waterBodyOcean = "海域"
    override val waterBodyNearshore = "近岸"
    override val waterBodyInland = "内陆水域"
    override val waterBodyLand = "陆地"
    override val waterBodyUnknown = "待判定"

    override val infoCoordinatePrefix = "坐标"
    override val infoElevationPrefix = "海拔"
    override val infoLastFetchedPrefix = "最近拉取"
    override val infoGroupPrefix = "分组"
    override val infoWaterBodyPrefix = "水体"
    override val infoAccuracyNote = "坐标为选点原始 WGS84 值，保留 6 位小数（≈0.11 m），未做偏移纠偏"

    override val factorOxygen = "溶氧"
    override val factorPressure = "气压"
    override val factorSolunar = "Solunar"
    override val factorTide = "潮流"
    override val factorWind = "风"
    override val factorWeather = "天气"
    override val factorDaypart = "时段"
    override val factorTempMatch = "适温"
    override val factorComfort = "舒适"
    override val factorWaterQuality = "水质"

    override val factorBreakdownTitle = "因子分解"
    override val factorDecay = "衰减 ΠV"
    override val factorRawInput = "原始输入"
    override fun factorRow(name: String, subText: String, weightText: String): String =
        "$name：子分 $subText，权重 $weightText"

    override fun factorMultiplier(multiplierText: String, reason: String): String =
        "乘子 $multiplierText，触发：$reason"

    override val timePattern = "M月d日 HH:mm"
    override val weekdayMon = "周一"
    override val weekdayTue = "周二"
    override val weekdayWed = "周三"
    override val weekdayThu = "周四"
    override val weekdayFri = "周五"
    override val weekdaySat = "周六"
    override val weekdaySun = "周日"
    override fun today(clock: String): String = "今天 $clock"
    override fun tomorrow(clock: String): String = "明天 $clock"

    override fun chartHourTooltipSuffix(hour: Int, tooltip: String): String =
        "，当前选中第 $hour 小时：$tooltip"

    override fun chartHourDescription(unit: String, points: Int): String =
        "24 小时逐小时图表，单位 $unit。$points 个数据点"

    override fun chartMissingSuffix(missing: Int): String =
        "，$missing 个小时缺测（显示为断线，数值表为 —）"

    override val chartScoreDescription = "24 小时钓鱼指数柱状图，0 到 100 分，5 档色带"
    override fun chartWindowSuffix(hours: Int): String = "，其中 $hours 个小时达到爆护窗口（≥80 分）"
    override fun chartHint(base: String, selected: String): String =
        "$base。点选可查看小时数值，等价数值见下方逐小时表。$selected"

    override fun dayToday(weekday: String, date: String): String = "今天 $weekday，$date"
    override fun dayFuture(offsetDays: Int, weekday: String, date: String): String =
        "未来第 $offsetDays 天 $weekday，$date"

    override fun dayShortToday(weekday: String): String = "今天 $weekday"
    override val dayTodayLabel = "今天"
    override val noDataHour = "该小时无数据"
    override val deviceZoneNote = "本机时区"
    override val unnamedWater = "未命名水域"
    override val previewHint = "移动中，点确定切换"
    override val waterTemp = "水温"
    override val dissolvedOxygen = "溶解氧"
    override val turbidity = "浊度"

    override fun durationHours(hours: Int): String = "$hours 小时"
    override fun windowLine(start: String, peak: String, duration: String): String =
        "起始 $start · 峰值 $peak · 时长 $duration"
    override fun peakScoreLine(score: Int, tier: String): String = "$score 分 · $tier"
    override val burstBadge = "爆护"
    override fun sustainedUntil(time: String): String = "利好持续至 $time"
    override fun calibrationEnabled(lagHours: Int): String = "已启用：滞后 ${lagHours}h"
    override val calibrationDisabled = "未启用"
    override val rawWind = "风"
    override val rawGust = "阵风"
    override val rawRain = "雨"
    override val rawCloud = "云"
    override val rawWave = "浪"
    override val rawTideLevel = "潮位"
    override val rawCurrent = "流速"
    override val rawTurbidity = "浊度"
    override val rawChlorophyll = "叶绿素"
    override fun hourWave(hour: Int, wave: String, period: String, direction: String): String =
        "第 $hour 小时：浪高 $wave，周期 $period，方向 $direction"
    override fun hourWeather(hour: Int, temp: String, wind: String, pressure: String): String =
        "第 $hour 小时：气温 $temp，风速 $wind，海平面气压 $pressure"
    override fun hourScore(hour: Int, score: Int): String = "第 $hour 小时：钓鱼指数 $score 分"
    override fun hourScoreMissing(hour: Int): String = "第 $hour 小时：该小时未评分"
    override fun hourWater(hour: Int, temp: String, doPotential: String): String =
        "第 $hour 小时：水温 $temp，溶氧潜力 $doPotential（估算）"
    override fun hourTide(hour: Int, level: String, rate: String, current: String): String =
        "第 $hour 小时：潮位 $level，涨落率 $rate，流速 $current"
    override val hourMissing = "缺测"
    override val sourceUnavailable = "来源不可用"
    override fun dataUnusable(ageHours: Int): String = "数据不可用（已过期 ${ageHours} h），不作为评分依据"
    override val manualSame = "基本一致"
    override fun manualLater(delta: String): String = "晚 ${delta}h"
    override fun manualEarlier(delta: String): String = "早 ${delta}h"
    override fun manualCompare(type: String, model: String, delta: String): String =
        "模型$type $model · 你记录$delta"
    override val manualAnyType = "高低潮"
    override fun manualLine(type: String, time: String): String = "$type · $time"
    override val manualTimeFormatError = "时刻格式应为 yyyy-MM-dd HH:mm"
    override fun manualOutOfRange(maxHours: Int): String = "时刻超出可补记范围（当前 ±${maxHours}h）"
    override fun tideBefore(before: String): String = "校正前：$before"
    override fun tideAfter(lagHours: Int, after: String): String = "校正后（滞后 ${lagHours}h）：$after"
    override fun tideHigh(times: String): String = "高潮 $times"
    override fun tideLow(times: String): String = "低潮 $times"
    override val noSourceData = "本点无该来源数据"
    override val unitScore = "分"
    override val ungrouped = "未分组"
    override val summaryNotLoaded = "未加载"
    override val staleLabel = "陈旧"
    override val expiredLabel = "已过期"
    override fun dataPrefix(date: String, time: String): String = "数据 $date $time"
    override val presetFreshLure = "淡水路亚"
    override val presetSeaShore = "海水岸边"
    override val presetFreshBait = "淡水台钓"
    override val errBatchDelete = "批量删除失败，未做任何改动"
    override val errBatchTag = "批量改标签失败，未做任何改动"
    override val errBatchGroup = "批量改分组失败，未做任何改动"
    override val errNoCoordinate = "新建收藏点需携带坐标（请在地图上选点后保存）"
    override fun summaryLine(time: String, score: Int, tier: String): String = "$time · $score 分 · $tier"
    override fun batchDeleteConfirm(count: Int): String = "确定删除选中的 $count 个收藏点吗？该操作不可撤销。"

    override val bandExplosive = "爆护窗口"
    override val bandGreat = "很适合"
    override val bandOk = "可以"
    override val bandPoor = "较差"
    override val bandBad = "别去"
}