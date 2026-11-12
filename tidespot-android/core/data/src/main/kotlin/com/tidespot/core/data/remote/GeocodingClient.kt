package com.tidespot.core.data.remote

import com.tidespot.core.data.ApiErrorClassifier
import com.tidespot.core.data.dto.GeocodingSearchResponseDto
import com.tidespot.core.domain.model.GeocodeCandidate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

/**
 * 正地理搜索客户端（SRS §5.9：搜索框 = Open-Meteo Geocoding `/v1/search`，中文 `language=zh`）。
 *
 * 与逆地理的分工（§5.9 硬约束）：正地理不接受坐标反查，§5.8 各源不做关键词检索，两者不互相顶替。
 * - 频控：Open-Meteo 免费档 → 令牌桶 6 req/min（§12，与天气/海洋同源策略）；
 * - 搜索由用户输入触发：令牌不足时挂起等待（[TokenBucket.acquire]），不做退避重试（陈旧查询重试无意义）；
 * - 错误经 [ApiErrorClassifier] 结构化抛出（B9 文案识别复用 M2 分类器）。
 */
class GeocodingClient(
    baseUrl: String = TideSpotUrls.GEOCODING_BASE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl: String = baseUrl
    private val http = tideSpotHttpClient(callTimeoutSec = 15)

    /** §12：Open-Meteo 令牌桶 6 req/min。 */
    private val bucket = TokenBucket(capacity = 6, periodMillis = 60_000L, clock = clock)

    /** @param count 返回条数（默认 5，§5.9）。 */
    suspend fun search(query: String, count: Int = 5): List<GeocodeCandidate> {
        // CHG-08 ②：URL 拼接收敛到 TideSpotUrls（§5.9 参数单点可审计）
        val url = TideSpotUrls.geocoding(baseUrl, query, count)
        bucket.acquire()
        val (code, body) = httpExecute(http, getRequest(url), ioDispatcher)
        if (code != 200) throw ApiErrorClassifier.classify(code, body)
        val dto = json.decodeFromString<GeocodingSearchResponseDto>(body)
        return dto.results.orEmpty().mapNotNull { r ->
            val name = r.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val lat = r.latitude ?: return@mapNotNull null
            val lon = r.longitude ?: return@mapNotNull null
            GeocodeCandidate(
                name = name,
                admin1 = r.admin1,
                admin2 = r.admin2,
                lat = lat,
                lon = lon,
                elevation = r.elevation,
                timezone = r.timezone,
            )
        }
    }
}
