package com.tidespot.core.data.water

import com.tidespot.core.data.remote.getRequest
import com.tidespot.core.data.remote.httpExecute
import com.tidespot.core.data.remote.tideSpotHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl

/**
 * 内陆水体探针（SRS §6.9 第 3 级，CHG-06：GLACIO 已退役 → OSTIA L4 lake mask 替代）。
 * 实现方约定：**任何失败/超时返回 null，绝不抛异常**（图层故障不得阻塞选点，M3.0 A6-r4）。
 */
interface InlandWaterProbe {
    /** true = 掩膜内有有效分析值（内陆水体）；false = 无值；null = 查询失败（降级）。 */
    suspend fun hasLakeValue(lat: Double, lon: Double): Boolean?
}

/**
 * OSTIA WMTS GetFeatureInfo 探针（DATA_SOURCE_PROBE A6-r3：参数已核实；A6-r4：图层侧故障全走 null 降级）。
 *
 * 端点：`https://wmts.marine.copernicus.eu/teroWmts`，图层
 * `SST_GLO_SST_L4_NRT_OBSERVATIONS_010_001/METOFFICE-GLO-SST-L4-NRT-OBS-SST-V2/analysed_sst`，
 * `TILEMATRIXSET=EPSG:3857`、z=7（Web Mercator 常规公式计算瓦片号与 256px 内像素）、
 * `INFOFORMAT=application/json`；请求携带 [CopernicusAuthClient] 签发的 Bearer token。
 * 解析响应 JSON 中的数值字段判定「湖泊掩膜内是否有值」。
 * 查询低频：仅显式选点 + 30 d/点缓存（§12），不会触碰频控（M3.0 结论）。
 */
class OstiaLakeProbe(
    private val authClient: CopernicusAuthClient,
    wmtsBaseUrl: String = com.tidespot.core.data.remote.TideSpotUrls.WMTS_BASE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val zoom: Int = DEFAULT_ZOOM,
) : InlandWaterProbe {

    private val wmtsBase: String = wmtsBaseUrl
    private val http = tideSpotHttpClient(callTimeoutSec = 10)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun hasLakeValue(lat: Double, lon: Double): Boolean? = try {
        val token = authClient.token()
        val request = getRequest(featureInfoUrl(lat, lon)).newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        val (code, body) = httpExecute(http, request, ioDispatcher)
        if (code == 200) extractHasValue(body) else null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null // 任何失败/超时/鉴权失败 → null（§6.9 第 4 级降级），绝不抛异常
    }

    /**
     * z=[zoom] EPSG:3857 GetFeatureInfo URL：瓦片号与 256px 瓦片内像素换算与 URL 组装
     * 全部收敛到 [com.tidespot.core.data.remote.TideSpotUrls.ostiaFeatureInfo]（CHG-08 ②：
     * 全部外部 URL 只允许在 TideSpotUrls 生成，单文件可审计）。internal 供单测做参数防回归
     * （DATA_SOURCE_PROBE A6-r4 核实过的拼写）。
     */
    internal fun featureInfoUrl(lat: Double, lon: Double): HttpUrl =
        com.tidespot.core.data.remote.TideSpotUrls.ostiaFeatureInfo(wmtsBase, lat, lon, zoom)

    /**
     * 判定响应中是否存在有效数值字段：
     * - 优先图层变量 `analysed_sst`（有数值 → 掩膜内有值）；
     * - 其余 properties 数值字段或顶层 `values` 数组（部分响应壳）同样视为有值；
     * - 200 但无数值 = 掩膜无值 → false；JSON 解析失败 → null。
     */
    private fun extractHasValue(body: String): Boolean? = try {
        val root: JsonObject = json.parseToJsonElement(body).jsonObject
        val props: JsonObject? = (root["features"] as? JsonArray)
            ?.firstOrNull()
            ?.let { (it as? JsonObject)?.get("properties") }
            ?.let { it as? JsonObject }
        when {
            props != null && props["analysed_sst"].isNumber() -> true
            props?.values?.any { it.isNumber() } == true -> true
            (root["values"] as? JsonArray)?.any { it.isNumber() } == true -> true
            else -> false
        }
    } catch (_: Exception) {
        null
    }

    private fun JsonElement?.isNumber(): Boolean =
        this is JsonPrimitive && doubleOrNull != null


    companion object {
        /** DATA_SOURCE_PROBE A6-r4：z=6/7/8 均核实，取 7。CHG-08 ②：定义已迁至 TideSpotUrls。 */
        const val DEFAULT_ZOOM = com.tidespot.core.data.remote.TideSpotUrls.OSTIA_DEFAULT_ZOOM

        /** Web Mercator 瓦片像素尺寸。CHG-08 ②：定义已迁至 TideSpotUrls。 */
        const val TILE_SIZE = com.tidespot.core.data.remote.TideSpotUrls.WEB_MERCATOR_TILE_SIZE

        /** EPSG:3857 纬度边界（墨卡托正切函数定义域）。CHG-08 ②：定义已迁至 TideSpotUrls。 */
        const val MERCATOR_MAX_LAT = com.tidespot.core.data.remote.TideSpotUrls.WEB_MERCATOR_MAX_LAT

        /** 图层全名（CHG-06，DATA_SOURCE_PROBE A6-r3 已核实 queryable=1）。CHG-08 ②：定义已迁至 TideSpotUrls。 */
        const val LAYER = com.tidespot.core.data.remote.TideSpotUrls.OSTIA_LAYER
    }
}

