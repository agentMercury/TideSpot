package com.tidespot.core.data.remote

import com.tidespot.core.data.ApiErrorClassifier
import com.tidespot.core.data.DataError
import com.tidespot.core.data.dto.MarineResponseDto
import com.tidespot.core.data.dto.WeatherResponseDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 单次 HTTP 尝试的访问日志条目（落 fetch_log 表，§7.3 / F-4.6 频控可视化）。 */
data class FetchLogEntry(
    val source: String,
    val urlHash: Int,
    val httpStatus: Int?,
    val bytes: Long?,
    val costMs: Long,
    val fetchedAt: Long,
)

/**
 * Open-Meteo HTTP 客户端（SRS §5.1 天气 / §5.2 海洋）。
 *
 * 硬性约束落点：
 * - UA 常量拦截器统一注入（§5.0 / CONTRACT 冻结点 5，F-4.6）；
 * - `followRedirects(true)`（§5.8 实测 307 场景的通用防御）；
 * - 令牌桶 **6 req/min**（§12）；
 * - 详情页并行请求上限 **≤4**（F-4.1，Semaphore；水质走本地推导（M0 结论 A6/A7 FAIL），当前仅天气/海洋两路）；
 * - 重试 **1s/4s/10s 三次退避**，区分 429 / 5xx / 网络错误 / B9 参数错误（F-4.5 / B9）；
 * - 整窗 240h 一次拉取（past_days + forecast_days=10，CHG-03 无省流量开关）；
 * - URL 全部经 [TideSpotUrls] 单点拼接（CHG-08 ② / §7.1：core:data 唯一 URL 构造器，单文件可审计）。
 *
 * 时钟注入：生产默认系统毫秒钟；测试可注入虚拟时钟对齐 TTL 判定。
 */
class OpenMeteoClient(
    weatherBaseUrl: String = TideSpotUrls.WEATHER_BASE,
    marineBaseUrl: String = TideSpotUrls.MARINE_BASE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    retryDelaysMillis: List<Long> = listOf(1_000L, 4_000L, 10_000L),
    /** §10.2 超时态测试可注入；生产默认 30s（§5.0 callTimeout）。 */
    callTimeoutMillis: Long = 30_000L,
) {

    private val retry = RetryExecutor(retryDelaysMillis)
    private val json = Json { ignoreUnknownKeys = true }

    /** §12：令牌桶 6 req/min。 */
    private val bucket = TokenBucket(capacity = 6, periodMillis = 60_000L, clock = clock)

    /** F-4.1：详情页并行请求 ≤4。 */
    private val parallelLimit = Semaphore(4)

    private val http: OkHttpClient = OkHttpClient.Builder()
        // §5.8 坑 1：RG-A 实测 307 → 备用域；HTTP 客户端必须开启 followRedirects
        .followRedirects(true)
        .callTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", TideSpotUserAgent.VALUE)
                    .build()
            )
        }
        .build()

    private val weatherBase: String = weatherBaseUrl
    private val marineBase: String = marineBaseUrl

    /** §5.1 天气 240h 整窗（past_days=7 非可选：Δ3h/Δ24h/7 日基线必需）。 */
    suspend fun fetchWeather(
        latitude: Double,
        longitude: Double,
        onLog: (FetchLogEntry) -> Unit = {},
    ): WeatherResponseDto = parallelLimit.withPermit {
        val url = weatherUrl(latitude, longitude)
        val body = executeGet(OpenMeteoSource.WEATHER, url, onLog)
        decodeBody("weather") { json.decodeFromString<WeatherResponseDto>(body) }
    }

    /** §5.2 海洋 240h 整窗（past_days=1）。注意内陆点 200 + 全 null（附录 B8）。 */
    suspend fun fetchMarine(
        latitude: Double,
        longitude: Double,
        onLog: (FetchLogEntry) -> Unit = {},
    ): MarineResponseDto = parallelLimit.withPermit {
        val url = marineUrl(latitude, longitude)
        val body = executeGet(OpenMeteoSource.MARINE, url, onLog)
        decodeBody("marine") { json.decodeFromString<MarineResponseDto>(body) }
    }

    /**
     * 响应体解析兜底（BLK-06/N-4，CHG-07 修复 4）：脏 JSON（截断、`"time":null`、类型不符等）
     * 不得裸抛 SerializationException，统一包成 [DataError.NetworkError]（保结构化语义，
     * 由 Repository 映射为分维降级）。解析发生在重试循环之外，故不会对脏响应做无谓重试。
     */
    private inline fun <T> decodeBody(source: String, block: () -> T): T =
        try {
            block()
        } catch (e: kotlinx.serialization.SerializationException) {
            throw DataError.NetworkError(IllegalStateException("$source 响应解析失败（脏 JSON）", e))
        }

    // ──────────────────── URL 构造（CHG-08 ②：统一走 TideSpotUrls 单点） ────────────────────

    private fun weatherUrl(lat: Double, lon: Double): HttpUrl = TideSpotUrls.weather(weatherBase, lat, lon)

    private fun marineUrl(lat: Double, lon: Double): HttpUrl = TideSpotUrls.marine(marineBase, lat, lon)

    // ────────────────────────────── 执行 + 重试 + 分类 ──────────────────────────────

    private suspend fun executeGet(
        source: OpenMeteoSource,
        url: HttpUrl,
        onLog: (FetchLogEntry) -> Unit,
    ): String = retry.execute {
        // 每个真实 HTTP 尝试都消耗令牌（§12 频控按请求计）
        bucket.acquire()
        val startedAt = clock()
        val response: Response = try {
            withContext(ioDispatcher) {
                http.newCall(Request.Builder().url(url).get().build()).execute()
            }
        } catch (e: IOException) {
            onLog(
                FetchLogEntry(
                    source = source.wire, urlHash = url.hashCode(), httpStatus = null,
                    bytes = null, costMs = clock() - startedAt, fetchedAt = clock(),
                )
            )
            throw DataError.NetworkError(e)
        }
        response.use { resp ->
            val bodyBytes = resp.body?.bytes()
            onLog(
                FetchLogEntry(
                    source = source.wire, urlHash = url.hashCode(), httpStatus = resp.code,
                    bytes = bodyBytes?.size?.toLong(), costMs = clock() - startedAt, fetchedAt = clock(),
                )
            )
            val bodyText = bodyBytes?.let { String(it, Charsets.UTF_8) }.orEmpty()
            if (resp.code == 200) {
                bodyText
            } else {
                // 429 / 5xx / 4xx（含 B9 参数拼写识别）→ 结构化错误；重试策略由 [RetryExecutor] 决定
                throw ApiErrorClassifier.classify(resp.code, bodyText)
            }
        }
    }

    /** 数据源标识（与 fetch_log.source / §7.3 TTL 表对应）。 */
    enum class OpenMeteoSource(val wire: String) { WEATHER("weather"), MARINE("marine") }

    companion object {
        /** §5.1 hourly 参数（顺序与契约一致，21 项）。CHG-08 ②：定义已迁至 [TideSpotUrls]。 */
        val WEATHER_HOURLY: List<String> get() = TideSpotUrls.WEATHER_HOURLY

        /** §5.1 daily 参数。CHG-08 ②：定义已迁至 [TideSpotUrls]。 */
        val WEATHER_DAILY: List<String> get() = TideSpotUrls.WEATHER_DAILY

        /**
         * §5.2 hourly 参数（16 项）。陷阱字段：潮位 = `sea_level_height_msl`
         * （写成 mssh → 400 B9，§5.2 / 附录 B9 防回归点）。CHG-08 ②：定义已迁至 [TideSpotUrls]。
         */
        val MARINE_HOURLY: List<String> get() = TideSpotUrls.MARINE_HOURLY
    }
}
