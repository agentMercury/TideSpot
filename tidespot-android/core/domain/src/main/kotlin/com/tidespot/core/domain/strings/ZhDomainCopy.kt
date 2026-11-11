package com.tidespot.core.domain.strings

import com.tidespot.core.domain.tide.TideObservationType
import com.tidespot.core.domain.tide.TidePhase

/**
 * 中文默认取词实现（**迁移前原文**，逐字一致；零行为漂移锁的真源）。
 *
 * 红线豁免：本文件是 `:core:domain` 文案提供者，允许中文字面量（i18n 契约白名单）。
 */
object ZhDomainCopy : DomainCopy {

    override val windowNotEnoughData = "有效数据不足 24 小时，最佳窗口功能暂不可用"

    override fun windowNoCandidate(horizonDays: Int, minScore: Int, best: Int?): String =
        if (best != null) {
            "未来 $horizonDays 天无 ≥$minScore 分窗口，最高 $best 分"
        } else {
            "未来 $horizonDays 天无 ≥$minScore 分窗口"
        }

    override fun windowNoCandidateInRange(minScore: Int, best: Int?): String =
        if (best != null) {
            "所选时段无 ≥$minScore 分窗口，时段内最高 $best 分"
        } else {
            "所选时段无 ≥$minScore 分窗口"
        }

    override val warningSlackTide = "临近平潮/停流，潮流交换弱"
    override val warningGustOver40 = "阵风超过 40 km/h，注意抛投安全"

    override fun manualCalibrationDelay(lagHours: Int): String = "已按现场校潮校正：时刻延后 ${lagHours}h"
    override fun manualCalibrationAhead(lagHours: Int): String = "已按现场校潮校正：时刻提前 ${-lagHours}h"
    override val manualCalibrationNoShift = "已按现场校潮校正：时刻无偏移"

    override fun manualNeedMore(count: Int): String = "再记 $count 条即可校潮"
    override fun manualNoValidObservations(count: Int): String =
        "有效观测不足 2 条（现有 $count 条 ±6h 内有匹配极值）"

    override fun manualLagExceeds(lag: Int, maxAbsLag: Int): String = "现场校潮 lag $lag h 超出 ±$maxAbsLag h，不启用"

    override fun tier(score: Int): String = when {
        score >= 80 -> "爆护窗口"
        score >= 65 -> "很适合"
        score >= 50 -> "可以"
        score >= 35 -> "较差"
        else -> "别去"
    }

    override val vetoThunderstorm = "雷暴（安全否决）"

    override fun vetoDecay(decay: String, details: String): String = "可作钓性衰减 V=$decay$details"
    override fun vetoWind(wind: String, gust: String): String = " 风$wind/$gust" + "km/h"
    override fun vetoPressure(d3: String): String = " 气压3h降${d3}hPa"
    override fun vetoRain(rain: String): String = " 降水${rain}mm/h"
    override fun vetoWave(wave: String): String = " 浪${wave}m"
    override fun vetoLowOxygen(doPotentialMgL: String): String = " 低溶氧$doPotentialMgL"

    override fun tideSizeLabel(size: TidePhase.TideSize): String? = when (size) {
        TidePhase.TideSize.SPRING -> "大潮"
        TidePhase.TideSize.NEAP -> "小潮"
        TidePhase.TideSize.NONE -> null
    }

    override fun tideCurrentLabel(current: TidePhase.TideCurrent): String = when (current) {
        TidePhase.TideCurrent.ACTIVE -> "活汛"
        TidePhase.TideCurrent.DEAD -> "死汛"
    }

    override fun observationTypeLabel(type: TideObservationType): String = when (type) {
        TideObservationType.HIGH -> "高潮"
        TideObservationType.LOW -> "低潮"
        TideObservationType.RISING_START -> "我开始涨潮"
        TideObservationType.FALLING_START -> "我开始退潮"
    }

    override fun presetLabel(presetId: String): String = when (presetId) {
        "fresh_lure" -> "淡水·路亚"
        "sea_shore" -> "海水·岸边/矶钓"
        "fresh_bait" -> "淡水·台钓"
        else -> presetId
    }

    override fun solunarDetail(age: String, illumination: String, transit: String): String =
        "月龄${age}天 照明$illumination 中天${transit}时"

    override val trendInsufficient = "趋势数据不足"
    override val hourlyGranularity = "逐小时"
    override val credentialInvalid = "凭据无效"
    override val networkError = "网络异常"
    override val waterQualityProxyNote = "水质 Tab 专用"
}