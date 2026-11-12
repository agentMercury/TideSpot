package com.tidespot.core.data.water

import com.tidespot.core.data.ApiErrorClassifier
import com.tidespot.core.data.remote.httpExecute
import com.tidespot.core.data.remote.postFormRequest
import com.tidespot.core.data.remote.tideSpotHttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** openid-connect 标准 token 响应（DATA_SOURCE_PROBE M3.0 A6-r1 实测 expires_in=300 s）。 */
@Serializable
internal data class CopernicusTokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresInSec: Long? = null,
)

/**
 * Copernicus Marine 鉴权客户端（DATA_SOURCE_PROBE M3.0 A6-r1 [已验证 200]）。
 *
 * - `POST {tokenUrl}`，form：client_id=toolbox / grant_type=password / username / password /
 *   scope=openid profile email；tokenUrl 默认经 [com.tidespot.core.data.remote.TideSpotUrls.copernicusToken]
 *   单点拼接（CHG-08 ②：全部外部 URL 只允许在 TideSpotUrls 生成，单文件可审计）；
 * - token **内存缓存**：expires_in 实测 300 s，**提前 30 s 刷新**（避免边界时刻携带将过期 token）；
 * - **用户名/密码一律构造器注入，禁止任何硬编码**（§12；凭据存 local-secrets.properties，不入库）。
 *
 * 失败抛结构化 [com.tidespot.core.data.DataError]（由探针调用方降级为 null）。
 */
class CopernicusAuthClient(
    private val username: String,
    private val password: String,
    tokenUrl: String = com.tidespot.core.data.remote.TideSpotUrls
        .copernicusToken(com.tidespot.core.data.remote.TideSpotUrls.COPERNICUS_AUTH_BASE).toString(),
    private val clientId: String = "toolbox",
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val url: HttpUrl = tokenUrl.toHttpUrl()
    private val http = tideSpotHttpClient(callTimeoutSec = 15)
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var cachedExpiresAt: Long = 0L

    /** 获取有效 Bearer token（带内存缓存 + 提前刷新；加锁保证并发下单飞刷新）。 */
    suspend fun token(): String = mutex.withLock {
        val current = cachedToken
        if (current != null && clock() < cachedExpiresAt) {
            current
        } else {
            fetchToken()
        }
    }

    private suspend fun fetchToken(): String {
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "password")
            .add("username", username)
            .add("password", password)
            .add("scope", SCOPE)
            .build()
        val (code, body) = httpExecute(http, postFormRequest(url, form), ioDispatcher)
        if (code != 200) throw ApiErrorClassifier.classify(code, body)
        val dto = json.decodeFromString<CopernicusTokenDto>(body)
        cachedToken = dto.accessToken
        val expiresIn = dto.expiresInSec ?: DEFAULT_EXPIRES_IN_SEC
        cachedExpiresAt = clock() + ((expiresIn - REFRESH_AHEAD_SEC).coerceAtLeast(1L)) * 1000L
        return dto.accessToken
    }

    companion object {
        /** M3.0 实测 expires_in=300 s；源缺省时按此值。 */
        const val DEFAULT_EXPIRES_IN_SEC = 300L

        /** 提前 30 s 刷新（M3.0 实现注意）。 */
        const val REFRESH_AHEAD_SEC = 30L

        const val SCOPE = "openid profile email"
    }
}

