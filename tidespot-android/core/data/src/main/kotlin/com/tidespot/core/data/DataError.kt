package com.tidespot.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * 数据层结构化错误（SRS F-4.5 / 附录 B9）。
 *
 * 分类依据 F-4.5「重试：1s/4s/10s 退避 3 次；区分 429 / 5xx / 网络错误」：
 * - [RateLimited]（429）、[ServerError]（5xx）、[NetworkError] 可重试；
 * - [SourceParamError]（B9 参数拼写错误）与 [BadRequest]（其余 4xx）不可重试，立即抛出，
 *   重试只会打爆免费源频控（§12）。
 */
sealed class DataError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** 429 限流：可重试（退避 1s/4s/10s，§12 Open-Meteo 频控路径）。 */
    class RateLimited(val retryAfterSec: Long? = null) :
        DataError("数据源限流(429)" + (retryAfterSec?.let { "，Retry-After=${it}s" } ?: ""))

    /** 5xx 服务端错误：可重试。 */
    class ServerError(val httpStatus: Int, val reason: String?) :
        DataError("数据源服务端错误($httpStatus): ${reason ?: "无详情"}")

    /** 网络异常（连接失败 / 断开 / IO）：可重试。 */
    class NetworkError(cause: Throwable) : DataError("网络异常: ${cause.message}", cause)

    /**
     * B9 参数拼写错误响应（400 且文案可识别）：不可重试。
     * 例：`Cannot initialize MultiDomains`（models 拼错）、
     * `Cannot initialize SurfacePressureAndHeightVariable…`（sea_level_height_mssh 拼错）。
     */
    class SourceParamError(val httpStatus: Int, val reason: String) :
        DataError("数据源参数错误(B9): $reason")

    /** 其余 4xx：不可重试。 */
    class BadRequest(val httpStatus: Int, val reason: String?) :
        DataError("请求被拒绝($httpStatus): ${reason ?: "无详情"}")

    /** 是否参与 1s/4s/10s 退避重试（F-4.5）。 */
    val isRetryable: Boolean
        get() = this is RateLimited || this is ServerError || this is NetworkError
}

/**
 * HTTP 错误响应分类器（纯函数，可单测）。
 * 负责识别附录 B9 各参数错误响应文案，并按 429 / 5xx / 4xx / 网络错误归类为结构化 [DataError]。
 */
object ApiErrorClassifier {

    /**
     * 附录 B9 已实测的参数错误文案片段（命中即判定为参数拼写 / 参数非法）。
     * - Open-Meteo：`Cannot initialize MultiDomains`（models=ncep_gfsocean016）；
     *   `Cannot initialize SurfacePressureAndHeightVariable…`（sea_level_height_mssh）
     * - NOAA：`Wrong Time Zone`（time_zone=UTC，只接受 gmt|lst|lst_ldt）、
     *   `No data was found…`（hourly_height 部分站点，应改 water_level+interval=6）、
     *   `The station is not a valid station…`（无效站点 ID）
     */
    val PARAM_REASON_PATTERNS: List<String> = listOf(
        "Cannot initialize",
        "Wrong Time Zone",
        "No data was found",
        "not a valid station",
    )

    /**
     * 按 HTTP 状态码 + 响应体归类为结构化错误。
     */
    fun classify(status: Int, body: String?): DataError = when {
        status == 429 -> DataError.RateLimited()
        status in 500..599 -> DataError.ServerError(status, extractReason(body))
        status in 400..499 -> {
            val reason = extractReason(body)
            if (reason != null && PARAM_REASON_PATTERNS.any { reason.contains(it, ignoreCase = true) }) {
                DataError.SourceParamError(status, reason)
            } else {
                DataError.BadRequest(status, reason)
            }
        }
        else -> DataError.BadRequest(status, extractReason(body))
    }

    /**
     * 兼容两种错误壳（B9）：
     * - Open-Meteo：`{"error":true,"reason":"Cannot initialize MultiDomains"}`
     * - NOAA：`{"error":{"message":"Wrong Time Zone"}}`
     * 解析失败时原样返回响应体（保留给日志 / fetch_log 排查）。
     */
    fun extractReason(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val obj: JsonObject = Json.parseToJsonElement(body).jsonObject
            val err = obj["error"]
            when (err) {
                is JsonObject -> (err["message"] as? JsonPrimitive)?.content
                else -> (obj["reason"] as? JsonPrimitive)?.content
            } ?: body
        } catch (_: Exception) {
            body
        }
    }
}
