package com.tidespot.core.domain.strings

import com.tidespot.core.domain.tide.TideObservationType
import com.tidespot.core.domain.tide.TidePhase

/**
 * `:core:domain` 纯 Kotlin 层的取词契约（OPT-V11-13 全量 i18n）。
 *
 * 存在理由：domain 模块无 Android 依赖，无法读 `res/`；但其产出（最佳窗口兜底提示、否决原因、
 * 档位名、潮汛/观测标签、因子明细）**会直接上屏**。故按「唯一抽象层」取词：
 * - 默认实现 [ZhDomainCopy]（中文，与迁移前逐字一致 → 既有单测/黄金对拍零行为漂移）；
 * - Android 运行期由 `:core:ui` 的 `ResDomainCopy` 注入（读 `values-en/strings.xml`）；
 * - 调用方全部以**默认参数**接入（`copy: DomainCopy = ZhDomainCopy`），未显式注入时行为不变。
 *
 * 本包（`strings/`）是 i18n 契约中**唯一允许中文字面量的代码位置之一**（红线扫描白名单）。
 */
interface DomainCopy {

    // ────────────── 最佳窗口兜底 / 注意事项（SRS §6.6） ──────────────
    val windowNotEnoughData: String

    /** 未来 N 天无窗口；[best] = 区间最高分（null = 无任何数据）。 */
    fun windowNoCandidate(horizonDays: Int, minScore: Int, best: Int?): String

    /** 所选时段无窗口；[best] = 时段内最高分。 */
    fun windowNoCandidateInRange(minScore: Int, best: Int?): String

    /** 临近平潮/停流，潮流交换弱。 */
    val warningSlackTide: String

    /** 阵风超过 40 km/h，注意抛投安全。 */
    val warningGustOver40: String

    // ────────────── 现场校潮（SRS §6.2b 规则 5） ──────────────
    fun manualCalibrationDelay(lagHours: Int): String
    fun manualCalibrationAhead(lagHours: Int): String
    val manualCalibrationNoShift: String
    fun manualNeedMore(count: Int): String
    fun manualNoValidObservations(count: Int): String
    fun manualLagExceeds(lag: Int, maxAbsLag: Int): String

    // ────────────── 档位（SRS §6.3） ──────────────
    fun tier(score: Int): String

    // ────────────── ΠV 否决/衰减原因（SRS §6.5；存库并上屏） ──────────────
    val vetoThunderstorm: String

    /** 衰减主句：`可作钓性衰减 V=0.42`（[details] 为各触发项拼接）。 */
    fun vetoDecay(decay: String, details: String): String

    fun vetoWind(wind: String, gust: String): String
    fun vetoPressure(d3: String): String
    fun vetoRain(rain: String): String
    fun vetoWave(wave: String): String
    fun vetoLowOxygen(doPotentialMgL: String): String

    // ────────────── 潮汛 / 现场观测标签（仅展示，不落库） ──────────────
    fun tideSizeLabel(size: TidePhase.TideSize): String?
    fun tideCurrentLabel(current: TidePhase.TideCurrent): String
    fun observationTypeLabel(type: TideObservationType): String

    // ────────────── 场景预设展示名（协议值=稳定 id；展示层映射，DB 仍存 id/中文兼容） ──────────────
    fun presetLabel(presetId: String): String

    // ────────────── 因子明细（存库并上屏，仅新增行走当前语言） ──────────────
    fun solunarDetail(age: String, illumination: String, transit: String): String
    val trendInsufficient: String

    /** 凭据校验失败原因（:core:data，透出 UI）。 */
    /** 数据源粒度（UI 来源行）。 */
    val hourlyGranularity: String

    val credentialInvalid: String
    val networkError: String
    val waterQualityProxyNote: String
}