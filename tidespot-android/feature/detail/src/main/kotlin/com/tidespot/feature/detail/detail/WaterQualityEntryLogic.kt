package com.tidespot.feature.detail.detail

import com.tidespot.core.data.repo.WaterQualityObservationKind

/**
 * OPT-V11-02（CHG-27）：水质实测录表单纯逻辑（JVM 锁；UI 只做状态与渲染）。
 *
 * 口径（OPT_REQUIREMENTS_V11 §3 / SRS §5.3）：
 * - 三项均可选填（空文本 = 不录入该指标）；
 * - 范围：水温 −5~45 ℃、溶解氧 0~20 mg/L、浊度 0~1000 NTU（超界拒绝并提示）；
 * - 录入时刻默认当前、可改（±24h 内，事后补记）。
 */
object WaterQualityEntryLogic {

    /** 校验错误码（UI 映射资源文案）。 */
    enum class Error { NOT_A_NUMBER, OUT_OF_RANGE }

    /** 单指标解析结果：null = 空（不录入）。 */
    sealed interface ValueResult {
        data class Ok(val value: Double) : ValueResult
        data class Err(val error: Error) : ValueResult
    }

    /** 指标范围（闭区间；与 §5.3 校验口径一致）。 */
    fun range(kind: WaterQualityObservationKind): ClosedFloatingPointRange<Double> = when (kind) {
        WaterQualityObservationKind.WATER_TEMP -> -5.0..45.0
        WaterQualityObservationKind.DO -> 0.0..20.0
        WaterQualityObservationKind.TURBIDITY -> 0.0..1000.0
    }

    /** 指标单位（展示用）。 */
    fun unit(kind: WaterQualityObservationKind): String = when (kind) {
        WaterQualityObservationKind.WATER_TEMP -> "℃"
        WaterQualityObservationKind.DO -> "mg/L"
        WaterQualityObservationKind.TURBIDITY -> "NTU"
    }

    /** 指标中文名。 */
    fun label(kind: WaterQualityObservationKind): String = when (kind) {
        WaterQualityObservationKind.WATER_TEMP -> "水温"
        WaterQualityObservationKind.DO -> "溶解氧"
        WaterQualityObservationKind.TURBIDITY -> "浊度"
    }

    /**
     * 解析并校验单指标输入：
     * 空/空白 → null（不录入）；非数字 → [Error.NOT_A_NUMBER]；超范围 → [Error.OUT_OF_RANGE]。
     */
    fun parse(kind: WaterQualityObservationKind, text: String): ValueResult? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val value = trimmed.toDoubleOrNull() ?: return ValueResult.Err(Error.NOT_A_NUMBER)
        return if (value in range(kind)) ValueResult.Ok(value) else ValueResult.Err(Error.OUT_OF_RANGE)
    }

    /** 录入时刻必须落在 now ±24h 内（事后补记边界，禁未来超出）。 */
    fun isTimeWithinWindow(observedAtEpochSec: Long, nowEpochSec: Long): Boolean =
        kotlin.math.abs(observedAtEpochSec - nowEpochSec) <= 24 * 3600L
}