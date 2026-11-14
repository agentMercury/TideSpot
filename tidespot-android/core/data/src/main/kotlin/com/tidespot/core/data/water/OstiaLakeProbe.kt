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
    /** true = 掩膜内有有效分析值（内陆水体）；false = 确认无值；null = 查询失败 **或无结论**（降级 UNKNOWN）。 */
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
 *
 * CHG-92（2026-11-14）：实测该图层是**全球 SST**，`value` 在湖 / 陆 / 海三点均为 null，
 * 不具备湖/陆区分能力；旧解析会把 numeric `lat`/`lon` 当成掩膜值而恒判有湖。
 * 现只止错：无有效数值 → null（UNKNOWN 待判定），**不落 LAND**；湖/陆仍判不出，
 * 待换用经验证的湖泊掩膜图层（届时按 §0.2 升 `JUDGE_VERSION`）。
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
     * CHG-92（2026-11-14 止错）：判定响应中是否存在有效数值字段。
     *
     * **真实响应形状**（本轮实测，湖 / 陆 / 海三点一致）：
     * ```
     * {"features":[{"properties":{"lat":29.52,"lon":118.97,"variableId":"analysed_sst",
     *   "value":null,"units":"kelvin"}}]}
     * ```
     * 即：数值字段叫 `value`（旧称 `analysed_sst`），**没有** `analysed_sst` 键；`lat`/`lon` 也是数值。
     *
     * 旧实现的兜底「properties 任一数值即 true」会被 `lat`/`lon` 命中 → **只要 HTTP 200 就恒判 true**，
     * 导致所有非海点（含陆地、乃至海况探测失败的海点）被误判 `INLAND_WATER` 并缓存 30 d。
     * 本轮删除该兜底，只认 `value` / `analysed_sst` 两个真值字段。
     *
     * **无值一律返回 null（降级 UNKNOWN）而不是 false**：该图层是**全球 SST 不是湖泊掩膜**
     * （CHG-35），实测三点 `value` 全为 null；「查不到掩膜值」不等于「确认是陆地」。
     * 200 但无有效数值 = 探针无结论，交上层按 §6.9 第 4 级「待判定」处理。
     */
    private fun extractHasValue(body: String): Boolean? = try {
        val root: JsonObject = json.parseToJsonElement(body).jsonObject
        val props: JsonObject? = (root["features"] as? JsonArray)
            ?.firstOrNull()
            ?.let { (it as? JsonObject)?.get("properties") }
            ?.let { it as? JsonObject }
        val realValue = props?.get("value") ?: props?.get("analysed_sst")
        when {
            realValue.isNumber() -> true
            (root["values"] as? JsonArray)?.any { it.isNumber() } == true -> true
            else -> null
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

