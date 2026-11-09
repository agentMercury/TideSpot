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
 */
class OstiaLakeProbeTest {

    private lateinit var server: MockWebServer
    private val recorded = mutableListOf<RecordedRequest>()
    private var wmtsResponder: (RecordedRequest) -> MockResponse = {
        ok("""{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"analysed_sst":291.5,"time":"2026-11-05"}}]}""")
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
    fun `掩膜内有有效数值_true`() = runTest {
        assertEquals(true, newProbe().hasLakeValue(29.55, 118.95))
    }

    @Test
    fun `200但无数值字段_false_掩膜外无值是正常态`() = runTest {
        wmtsResponder = { ok("""{"type":"FeatureCollection","features":[{"properties":{"label":"no-data"}}]}""") }
        assertEquals(false, newProbe().hasLakeValue(29.55, 118.95))
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
}
