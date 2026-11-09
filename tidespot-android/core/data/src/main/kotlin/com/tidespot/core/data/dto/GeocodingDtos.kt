package com.tidespot.core.data.dto

import kotlinx.serialization.Serializable

/** 正地理搜索（SRS §5.9：Open-Meteo `GET /v1/search?name=&count=&language=zh&format=json`）。 */
@Serializable
data class GeocodingSearchResponseDto(
    /** 无命中时源返回空数组或缺省 → 上层映射为空列表。 */
    val results: List<GeocodingResultDto>? = null,
)

@Serializable
data class GeocodingResultDto(
    val name: String? = null,
    /** 一级行政区（如 `浙江省`）。 */
    val admin1: String? = null,
    /** 二级行政区（如 `杭州市`）；§5.9：与 admin1 作副标题、字段保持分开。 */
    val admin2: String? = null,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** 海拔 m（§6.9 水体判定辅助判据透传）。 */
    val elevation: Double? = null,
    val timezone: String? = null,
)
