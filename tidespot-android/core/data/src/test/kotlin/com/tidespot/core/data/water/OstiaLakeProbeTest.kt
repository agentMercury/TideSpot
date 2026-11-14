package com.tidespot.core.data.water

import com.tidespot.core.data.remote.TideSpotUserAgent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OSTIA WMTS GetFeatureInfo 探针（CHG-06）：有值/无值/失败三分支 + URL 参数防回归
 * （DATA_SOURCE_PROBE A6-r3/A6-r4 核实过的拼写）。
 *
 * CHG-92（2026-11-14）：fixture 改为**真实响应形状**——数值字段是 `value`，
 * 且 `lat`/`lon` 同为数值。旧 fixture 用虚构的 `analysed_sst` / `{"label":"no-data"}`，
 * 从未覆盖「properties 里有数值但不是掩膜值」的情形，导致恒 true 的解析 bug 长期假绿。
 */
class OstiaLakeProbeTest {

    private lateinit var server: MockWebServer
    private val recorded = mutableListOf<RecordedRequest>()
    /** 真实响应壳：value 有数值（掩膜有值）。 */
    private var wmtsResponder: (RecordedRequest) -> MockResponse = {
        ok(REAL_RESPONSE.replace("\"value\":null", "\"value\":291.5"))
    }
    private var tokenCount = 0

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recorded += request
                return when (request.requestUrl!!.encodedPath) {
                    "/token" -> {
                        tokenCount++
                        ok("""{"access_token":"tok-$tokenCount","expires_in":300}""")
                    }
                    "/teroWmts" -> wmtsResponder(request)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun TestScope.newProbe(): OstiaLakeProbe = OstiaLakeProbe(
        authClient = CopernicusAuthClient(
            username = "u",
            password = "p",
            tokenUrl = server.url("/token").toString(),
            clock = { 0L },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        ),
        wmtsBaseUrl = server.url("/").toString(),
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    @Test
    fun `真实响应_value有数值_true`() = runTest {
        assertEquals(true, newProbe().hasLakeValue(29.55, 118.95))
    }

    @Test
    fun `真实响应_value为null但latLon是数值_降级null不得判有湖`() = runTest {
        // CHG-92 回归：2026-11-14 实测湖/陆/海三点均返回 value:null，
        // 旧实现「properties 任一数值即 true」被 lat/lon 命中 → 恒 true → 全判 INLAND_WATER。
        wmtsResponder = { ok(REAL_RESPONSE) }
        assertEquals(null, newProbe().hasLakeValue(29.55, 118.95))
    }

    @Test
    fun `200但无有效数值字段_降级null而非false`() = runTest {
        wmtsResponder = { ok("""{"type":"FeatureCollection","features":[{"properties":{"label":"no-data"}}]}""") }
        assertEquals(null, newProbe().hasLakeValue(29.55, 118.95), "无掩膜结论 ≠ 确认是陆地（不落 LAND）")
    }

    @Test
    fun `旧字段analysed_sst仍兼容_true`() = runTest {
        wmtsResponder = { ok("""{"features":[{"properties":{"analysed_sst":291.5,"time":"2026-11-05"}}]}""") }
        assertEquals(true, newProbe().hasLakeValue(29.55, 118.95))
    }

    @Test
    fun `500返回null_绝不抛异常`() = runTest {
        wmtsResponder = { MockResponse().setResponseCode(500).setBody("empty iterator") }
        assertEquals(null, newProbe().hasLakeValue(29.55, 118.95))
    }

    @Test
    fun `鉴权失败也返回null_绝不抛异常`() = runTest {
        val dead = MockWebServer()
        dead.start()
        val deadUrl = dead.url("/token").toString()
        dead.shutdown()

        val probe = OstiaLakeProbe(
            authClient = CopernicusAuthClient(
                username = "u",
                password = "p",
                tokenUrl = deadUrl,
                clock = { 0L },
                ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            ),
            wmtsBaseUrl = server.url("/").toString(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        assertEquals(null, probe.hasLakeValue(29.55, 118.95))
    }

    @Test
    fun `URL参数防回归_WebMercator瓦片与像素_Bearer与UA`() = runTest {
        newProbe().hasLakeValue(29.55, 118.95)

        val req = recorded.first { it.requestUrl!!.encodedPath == "/teroWmts" }
        val url = req.requestUrl!!
        assertEquals("WMTS", url.queryParameter("SERVICE"))
        assertEquals("GetFeatureInfo", url.queryParameter("REQUEST"))
        assertEquals("1.0.0", url.queryParameter("VERSION"))
        assertEquals(OstiaLakeProbe.LAYER, url.queryParameter("LAYER"))
        assertEquals("default", url.queryParameter("STYLE"))
        assertEquals("image/png", url.queryParameter("FORMAT"))
        assertEquals("EPSG:3857", url.queryParameter("TILEMATRIXSET"))
        assertEquals("7", url.queryParameter("TILEMATRIX"), "z=7（DATA_SOURCE_PROBE A6-r4）")
        assertEquals("106", url.queryParameter("TILECOL"), "(29.55,118.95) → x=106.29 → 瓦片 106")
        assertEquals("application/json", url.queryParameter("INFOFORMAT"))
        assertTrue(url.queryParameter("TILEROW")!!.toInt() in 0..127)
        assertTrue(url.queryParameter("I")!!.toInt() in 0..255, "像素列在瓦片内")
        assertTrue(url.queryParameter("J")!!.toInt() in 0..255, "像素行在瓦片内")
        assertTrue(req.getHeader("Authorization")!!.startsWith("Bearer tok-"))
        assertEquals(TideSpotUserAgent.VALUE, req.getHeader("User-Agent"))
    }

    private companion object {
        /** 2026-11-14 实测响应（千岛湖；北京陆地、青岛近海除坐标外完全一致，value 均 null）。 */
        const val REAL_RESPONSE = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[29.52499949928786,118.97500403481467]},"properties":{"lat":29.52499949928786,"lon":118.97500403481467,"variableId":"analysed_sst","datasetId":"SST_GLO_SST_L4_NRT_OBSERVATIONS_010_001/METOFFICE-GLO-SST-L4-NRT-OBS-SST-V2","value":null,"units":"kelvin"}}]}"""
    }
}
