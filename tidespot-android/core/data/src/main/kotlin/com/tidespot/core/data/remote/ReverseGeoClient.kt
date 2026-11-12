package com.tidespot.core.data.remote

import com.tidespot.core.data.dto.BigDataCloudReverseDto
import com.tidespot.core.data.dto.PhotonReverseDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

/**
 * 逆地理 HTTP 客户端（SRS §5.8：RG-A = BigDataCloud 主 / RG-B = Photon 次）。
 *
 * 实测坑落点（§5.8 / 附录 B7b / §12 红线）：
 * - RG-A 实测会 **307 跳转备用域**：[tideSpotHttpClient] 已开启 followRedirects；
 *   超时 6 s，**任何失败最多重试 1 次**（§12：失败不重试超过 1 次）；
 * - RG-B 超时 4 s，**单次尝试、绝不重试**；`features:[]` 是「此处无聚落」正常态而非错误
 *   （按失败重试会打爆频控）；**禁止传 lang 参数**（B7b：lang=zh → 400）。
 * - 任何失败一律返回 null（上层跳过该级继续降级，绝不阻塞选点）。
 *
 * 频控不在本类：RG-A/RG-B 各自独立令牌桶由 [com.tidespot.core.data.geo.ReverseGeocoder] 持有。
 */
class ReverseGeoClient(
    bigDataCloudBaseUrl: String = TideSpotUrls.BIG_DATA_CLOUD_BASE,
    photonBaseUrl: String = TideSpotUrls.PHOTON_BASE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    bigDataCloudTimeoutSec: Long = 6,
    photonTimeoutSec: Long = 4,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val bigDataCloudBase: String = bigDataCloudBaseUrl
    private val photonBase: String = photonBaseUrl
    private val httpA = tideSpotHttpClient(bigDataCloudTimeoutSec)
    private val httpB = tideSpotHttpClient(photonTimeoutSec)

    /** RG-A 逆地理：失败（非 200 / 网络异常 / 解析失败）最多重试 1 次，仍失败返回 null。 */
    suspend fun fetchBigDataCloud(lat: Double, lon: Double): BigDataCloudReverseDto? {
        // CHG-08 ②：URL 拼接收敛到 TideSpotUrls（RG-A §5.8 参数单点可审计）
        val url = TideSpotUrls.bigDataCloudReverse(bigDataCloudBase, lat, lon)
        repeat(MAX_ATTEMPTS) {
            try {
                val (code, body) = httpExecute(httpA, getRequest(url), ioDispatcher)
                if (code == 200) return json.decodeFromString<BigDataCloudReverseDto>(body)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 网络异常 / 超时 / 非 200 / 解析失败 → 最多重试 1 次后放弃
            }
        }
        return null
    }

    /**
     * RG-B 逆地理：单次尝试（含 `features:[]` 正常态，由调用方判空降级）。
     * 注意 URL 不含 lang 参数（防回归：B7b lang=zh → 400）。
     */
    suspend fun fetchPhoton(lat: Double, lon: Double): PhotonReverseDto? = try {
        // CHG-08 ②：URL 拼接收敛到 TideSpotUrls（RG-B §5.8 参数单点可审计；
        // 禁止 lang 参数的防回归见 TideSpotUrls.photonReverse KDoc）
        val url = TideSpotUrls.photonReverse(photonBase, lat, lon)
        val (code, body) = httpExecute(httpB, getRequest(url), ioDispatcher)
        if (code == 200) json.decodeFromString<PhotonReverseDto>(body) else null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        /** §12 RG-A 红线：失败不重试超过 1 次（即共 ≤2 次尝试）。 */
        const val MAX_ATTEMPTS = 2
    }
}
