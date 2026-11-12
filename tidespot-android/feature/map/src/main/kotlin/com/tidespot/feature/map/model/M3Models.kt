package com.tidespot.feature.map.model

import com.tidespot.core.domain.model.NamedPlace
import com.tidespot.core.domain.model.WaterBodyType

/**
 * 选点真源：准星指向的经纬度（WGS84 度）。
 * （NamedPlace / GeocodeCandidate / ReverseGeoSource 已由域模型
 * com.tidespot.core.domain.model 提供，集成时占位类型已删除。）
 */
data class GeoPoint(val lat: Double, val lon: Double)

/**
 * 确认选点结果（CHG-09① / NEW-01/02）：确认动作流水线的下传载体。
 *
 * 流水线（MapScreen 确认按钮触发）：
 * 1. `marineHasValue = null`（M3 尚无海洋数据 → classify 内部走探针路径，CHG-09 接线口径）；
 * 2. `elevation`（暂无来源时为 null，搜索候选可透出）+ 探针 → WaterBodyClassifier.classify；
 * 3. 逆地理解析（§5.8，仅确认后发起）；
 * 4. 结果包装成本类随 `onSpotConfirmed` 下传；spot 表写入（waterBodyType/elevation/tzOffsetSec）
 *    由消费方（M6 组装）经 `TideSpotRepository.updateSpotWaterBody` 落库（写入口已就绪）。
 *
 * 放置说明：放 feature:map 而非 domain——它只服务地图确认回调这一处 UI 事件，
 * 不参与 :core:* 任何契约；NamedPlace 引用是纯读透传，不构成反向耦合（CHG-09 报告项）。
 */
data class SpotConfirmed(
    val point: GeoPoint,
    /** 逆地理解析结果；逆地理总开关关闭（N-5）或解析失败时为 null（信息条另有兜底，不空白）。 */
    val place: NamedPlace?,
    /** §6.9 判定输出（探针失败期 = UNKNOWN「待判定」，CHG-09②）。 */
    val waterBody: WaterBodyType,
)
