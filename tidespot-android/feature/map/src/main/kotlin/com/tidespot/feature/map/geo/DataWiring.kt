package com.tidespot.feature.map.geo

import com.tidespot.core.data.geo.ReverseGeocoder
import com.tidespot.core.data.remote.GeocodingClient

/**
 * M3 集成接线（CONTRACT「M3 子契约」）：用 `:core:data` 的实现类
 * 适配本模块的 [PlaceResolver] / [GeocoderApi] 接口。
 *
 * 构造 ReverseGeocoder / GeocodingClient 所需的 Room DAO 与凭据
 * 由 `:app`（M6 组装）提供，本模块不持有 Android Context。
 */
class DataPlaceResolver(private val delegate: ReverseGeocoder) : PlaceResolver {
    override suspend fun resolve(lat: Double, lon: Double) = delegate.resolve(lat, lon)
}

class DataGeocoder(private val delegate: GeocodingClient) : GeocoderApi {
    override suspend fun search(query: String, count: Int) = delegate.search(query, count)
}
