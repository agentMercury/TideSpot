package com.tidespot.feature.map.geo

import com.tidespot.core.data.water.WaterBodyClassifier
import com.tidespot.core.domain.model.WaterBodyType

/**
 * 水体判定接口（CHG-09① / NEW-01/02 接线）：MapScreen 确认动作触发 classify() 的注入点，
 * 与 [PlaceResolver] 同风格（构造注入 + Fake 单测）。
 *
 * M3 接线口径（CHG-09 任务书）：confirm 时**尚无海洋数据** → marineHasValue 按「未知」
 * 处理（传 null 语义 → classify 内部走探针路径；探针失败判 UNKNOWN「待判定」）。
 * M4 接入真实 marine 序列后，由数据层提供携带真实 marineHasValue 的实现并补全此处 KDoc。
 */
fun interface WaterBodyResolver {
    /**
     * @param elevation 判定辅助输入（§6.9）；来源：搜索候选 GeocodeCandidate.elevation；
     *   长按/定位选点暂无来源 → null（classify 按探针路径处理）。
     */
    suspend fun classify(lat: Double, lon: Double, elevation: Double?): WaterBodyType
}

/**
 * 数据层实现：委托 :core:data WaterBodyClassifier（判定缓存仍在 classifier 内部 WaterBodyCacheDao，无第二缓存）。
 *
 * CHG-35（水体判定修复）：尾增可选 [marineHasValue] 探测注入——
 * 确认选点时先探「该坐标是否有海况」作 §6.9 **主判据**（true → NEARSHORE，不再问 OSTIA；
 * false → 交 OSTIA 分湖/陆；null/未注入 → 维持旧行为走探针路径）。
 * 默认 null 保持既有调用与测试零改动。
 */
class DataWaterBodyResolver(
    private val classifier: WaterBodyClassifier,
    private val marineHasValue: (suspend (Double, Double) -> Boolean?)? = null,
) : WaterBodyResolver {
    override suspend fun classify(lat: Double, lon: Double, elevation: Double?): WaterBodyType =
        classifier.classify(
            lat,
            lon,
            marineHasValue = marineHasValue?.invoke(lat, lon),
            elevation = elevation,
        )
}

/** Fake 实现：仅用于 Compose 预览与 JVM 单测，无网络/缓存行为。 */
class FakeWaterBodyResolver(
    private val fixed: WaterBodyType = WaterBodyType.UNKNOWN,
) : WaterBodyResolver {
    override suspend fun classify(lat: Double, lon: Double, elevation: Double?): WaterBodyType = fixed
}
