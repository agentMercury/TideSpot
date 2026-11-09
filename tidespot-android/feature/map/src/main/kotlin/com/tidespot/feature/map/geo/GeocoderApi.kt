package com.tidespot.feature.map.geo

import com.tidespot.core.domain.model.GeocodeCandidate

/**
 * 正地理搜索接口（M3 子契约占位，签名 = :core:data GeocodingClient.search）。
 * 集成时由数据层的 GeocodingClient（Open-Meteo /v1/search, language=zh）实现接入。
 */
interface GeocoderApi {
    suspend fun search(query: String, count: Int = 5): List<GeocodeCandidate>
}

/** Fake 实现：内存候选集，仅用于预览与手工调试。 */
class FakeGeocoder(
    private val candidates: List<GeocodeCandidate> = defaultCandidates,
) : GeocoderApi {
    override suspend fun search(query: String, count: Int): List<GeocodeCandidate> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return candidates.filter { it.name.contains(q) }.take(count)
    }

    companion object {
        val defaultCandidates = listOf(
            GeocodeCandidate("万绿湖", "广东", "河源", 23.766, 114.701, 100.0, "Asia/Shanghai"),
            GeocodeCandidate("千岛湖", "浙江", "杭州", 29.600, 119.038, 108.0, "Asia/Shanghai"),
            GeocodeCandidate("青岛防波堤", "山东", "青岛", 36.061, 120.350, 0.0, "Asia/Shanghai"),
        )
    }
}