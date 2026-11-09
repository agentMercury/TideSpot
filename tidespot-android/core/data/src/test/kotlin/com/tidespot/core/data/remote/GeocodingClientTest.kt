package com.tidespot.core.data.remote

import com.tidespot.core.data.DataError
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
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 正地理搜索（SRS §5.9）：Open-Meteo /v1/search，中文，admin2+admin1 副标题字段分开。 */
class GeocodingClientTest {

    private companion object {
        val SEARCH_JSON = """
            {"results":[
              {"id":1,"name":"千岛湖","admin1":"浙江省","admin2":"杭州市","country":"中国",
               "latitude":29.6,"longitude":119.03,"elevation":108.0,"timezone":"Asia/Shanghai"},
              {"id":2,"name":"千岛湖镇","admin1":"浙江省","admin2":"杭州市","country":"中国",
               "latitude":29.611,"longitude":119.041,"timezone":"Asia/Shanghai"}
            ]}
        """.trimIndent()
    }

    private lateinit var server: MockWebServer
    private val recorded = mutableListOf<RecordedRequest>()
    private var searchResponder: (RecordedRequest) -> MockResponse = { ok(SEARCH_JSON) }

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recorded += request
                return if (request.requestUrl!!.encodedPath == "/v1/search") {
                    searchResponder(request)
                } else {
                    MockResponse().setResponseCode(404)
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

    private fun TestScope.newClient(): GeocodingClient = GeocodingClient(
        baseUrl = server.url("/").toString(),
        clock = { 0L },
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    @Test
    fun `中文搜索映射正确_副标题字段保持分开`() = runTest {
        val list = newClient().search("千岛湖")

        assertEquals(2, list.size)
        val first = list[0]
        assertEquals("千岛湖", first.name)
        assertEquals("浙江省", first.admin1)
        assertEquals("杭州市", first.admin2, "admin2+admin1 副标题字段保持分开（§5.9）")
        assertEquals(29.6, first.lat)
        assertEquals(119.03, first.lon)
        assertEquals(108.0, first.elevation)
        assertEquals("Asia/Shanghai", first.timezone)
        assertEquals("千岛湖镇", list[1].name)
        assertNull(list[1].elevation, "源缺值 = null，禁止 0 填充")

        val req = recorded.first()
        val url = req.requestUrl!!
        assertEquals("千岛湖", url.queryParameter("name"), "name 参数中文原样透传（URL 编码由 HTTP 层处理）")
        assertEquals("5", url.queryParameter("count"), "默认 count=5（§5.9）")
        assertEquals("zh", url.queryParameter("language"))
        assertEquals("json", url.queryParameter("format"))
        assertEquals(TideSpotUserAgent.VALUE, req.getHeader("User-Agent"))
    }

    @Test
    fun `count参数覆盖_空结果返回空列表`() = runTest {
        val client = newClient()
        client.search("西湖", count = 3)
        assertEquals("3", recorded.last().requestUrl!!.queryParameter("count"))

        searchResponder = { ok("""{"results":[]}""") }
        assertTrue(client.search("nosuchplace").isEmpty(), "无命中 → 空列表")
    }

    @Test
    fun `400抛结构化BadRequest`() = runTest {
        searchResponder = {
            MockResponse().setResponseCode(400).setBody("""{"error":true,"reason":"bad name"}""")
        }
        val e = assertFailsWith<DataError.BadRequest> { newClient().search("x") }
        assertEquals(400, e.httpStatus)
    }

}

