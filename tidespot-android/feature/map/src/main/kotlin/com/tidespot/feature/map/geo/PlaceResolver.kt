package com.tidespot.feature.map.geo

import com.tidespot.core.domain.model.NamedPlace
import com.tidespot.core.domain.model.ReverseGeoSource

/**
 * 逆地理解析接口（M3 子契约占位，签名 = :core:data ReverseGeocoder.resolve）。
 * 集成时由数据层的 ReverseGeocoder 提供同签名实现接入本模块。
 */
interface PlaceResolver {
    suspend fun resolve(lat: Double, lon: Double): NamedPlace
}

/** Fake 实现：仅用于 Compose 预览与手工调试，不含任何网络行为。 */
class FakePlaceResolver(
    private val fixed: NamedPlace? = null,
) : PlaceResolver {
    override suspend fun resolve(lat: Double, lon: Double): NamedPlace =
        fixed ?: NamedPlace(
            lat = lat,
            lon = lon,
            displayName = "测试水域",
            subLabel = "测试市, 测试省",
            plusCode = "7PXXJ24Q+26",
            waterName = null,
            locality = null,
            city = "测试市",
            admin1 = "测试省",
            source = ReverseGeoSource.RG_A,
        )
}