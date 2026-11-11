package com.tidespot.core.data.water

import com.tidespot.core.data.DataError
import com.tidespot.core.domain.strings.DomainCopy
import com.tidespot.core.domain.strings.ZhDomainCopy
import kotlinx.coroutines.CancellationException

/**
 * 凭据校验结果（CHG-25 P0-1 批次 A）：**四态封闭**，UI 禁自行解析异常。
 */
sealed interface CredentialCheck {

    /** 本地未配置（或凭据任一项空白）。 */
    data object Missing : CredentialCheck

    /** token 获取成功（凭据有效）。 */
    data object Valid : CredentialCheck

    /** 凭据错误（4xx：401/403 等，含 B9 参数错误响应）。 */
    data class Invalid(val reason: String) : CredentialCheck

    /** 网络/超时/限流/5xx：凭据本身未必有错，可稍后重试。 */
    data class Unreachable(val reason: String) : CredentialCheck
}

/**
 * Copernicus 凭据校验器（CHG-25 P0-1 批次 A）。
 *
 * 复用既有 [CopernicusAuthClient.token]（URL 单点收敛于 `TideSpotUrls.copernicusToken`、
 * 频控/UA/超时内置），把 [DataError] 结构化分类映射为 UI 可用的四态；**异常不出本类**。
 *
 * 分类口径：
 * - [DataError.BadRequest] / [DataError.SourceParamError]（4xx）→ [CredentialCheck.Invalid]；
 * - [DataError.RateLimited] / [DataError.ServerError] / [DataError.NetworkError] → [CredentialCheck.Unreachable]；
 * - 未知异常一律 [CredentialCheck.Unreachable]（不误伤"凭据有效但网络抖动"的用户）；
 * - [CancellationException] 必须重抛（D13 先例）。
 */
class CopernicusCredentialValidator(
    private val authFactory: (CopernicusCredential) -> CopernicusAuthClient =
        { CopernicusAuthClient(it.username, it.password) },
) {

    suspend fun check(credential: CopernicusCredential?, copy: DomainCopy = ZhDomainCopy): CredentialCheck {
        val c = credential ?: return CredentialCheck.Missing
        if (c.isBlank) return CredentialCheck.Missing
        return try {
            authFactory(c).token()
            CredentialCheck.Valid
        } catch (e: CancellationException) {
            throw e
        } catch (e: DataError) {
            when (e) {
                is DataError.BadRequest, is DataError.SourceParamError ->
                    CredentialCheck.Invalid(e.message ?: copy.credentialInvalid)
                is DataError.RateLimited, is DataError.ServerError, is DataError.NetworkError ->
                    CredentialCheck.Unreachable(e.message ?: copy.networkError)
            }
        } catch (e: Exception) {
            CredentialCheck.Unreachable(e.message ?: copy.networkError)
        }
    }
}