package com.tidespot.core.data.water

import com.tidespot.core.data.DataError
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Copernicus 鉴权（DATA_SOURCE_PROBE M3.0 A6-r1 实测契约）：
 * form 参数 / token 内存缓存 / 提前 30 s 刷新 / 凭据只经构造器注入（无硬编码）。
 */
class CopernicusAuthClientTest {

    private lateinit var server: MockWebServer
    private val recorded = mutableListOf<RecordedRequest>()
    private var tokenCount = 0
    private var tokenStatus = 200

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recorded += request
                return if (request.requestUrl!!.encodedPath == "/token") {
                    tokenCount++
                    MockResponse().setResponseCode(tokenStatus)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"access_token":"tok-$tokenCount","expires_in":300}""")
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

    /** clock 捕获测试内 var vNow（按引用），TTL 推进才生效。 */
    private fun TestScope.newAuth(clock: () -> Long): CopernicusAuthClient = CopernicusAuthClient(
        username = "user-a",
        password = "pass-a",
        tokenUrl = server.url("/token").toString(),
        clock = clock,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    @Test
    fun `token获取_缓存_提前30s刷新`() = runTest {
        var vNow = 0L
        val auth = newAuth { vNow }

        assertEquals("tok-1", auth.token())
        assertEquals(1, tokenCount)

        assertEquals("tok-1", auth.token(), "缓存内不再发请求")
        assertEquals(1, tokenCount)

        vNow += (300 - 30) * 1000 - 1 // expires_in - 30s 之内
        assertEquals("tok-1", auth.token())
        assertEquals(1, tokenCount)

        vNow += 1 // 到达提前刷新点
        assertEquals("tok-2", auth.token(), "提前 30 s 刷新")
        assertEquals(2, tokenCount)

        // form 参数与 UA（M3.0 A6-r1 实测契约 / §12）
        val req = recorded.first()
        val form = req.body!!.readUtf8()
        assertTrue(form.contains("client_id=toolbox"), form)
        assertTrue(form.contains("grant_type=password"), form)
        assertTrue(form.contains("username=user-a"), form)
        assertTrue(form.contains("password=pass-a"), form)
        assertTrue(form.contains("scope=openid%20profile%20email"), form)
        assertEquals(TideSpotUserAgent.VALUE, req.getHeader("User-Agent"))
    }

    @Test
    fun `非200抛结构化错误_凭据不落日志`() = runTest {
        tokenStatus = 401
        val e = assertFailsWith<DataError.BadRequest> { newAuth { 0L }.token() }
        assertEquals(401, e.httpStatus)
        // 凭据只在请求体里（构造器注入），不进错误信息
        assertTrue(!e.message!!.contains("pass-a"))
    }
}

