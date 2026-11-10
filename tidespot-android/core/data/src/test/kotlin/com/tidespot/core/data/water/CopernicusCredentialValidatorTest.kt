package com.tidespot.core.data.water

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
 * CHG-25 P0-1 批次 A 锁测试：[CopernicusCredentialValidator] 四态分类。
 *
 * 覆盖契约（CONTRACT「v1.1 子契约 P0-1」）：
 * - 200 → [CredentialCheck.Valid]；
 * - 4xx（401/403/400）→ [CredentialCheck.Invalid]（凭据错误，UI 提示重填）；
 * - 429 / 5xx → [CredentialCheck.Unreachable]（网络/限流，UI 提示稍后重试，不误判凭据）；
 * - 连接失败 → [CredentialCheck.Unreachable]；
 * - null / 空白凭据 → [CredentialCheck.Missing]（不发请求）。
 */
class CopernicusCredentialValidatorTest {

    private lateinit var server: MockWebServer
    private var tokenStatus = 200
    private var requestCount = 0

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestCount++
                return MockResponse().setResponseCode(tokenStatus)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        if (tokenStatus == 200) {
                            """{"access_token":"tok-ok","expires_in":300}"""
                        } else {
                            """{"error":"invalid_grant","error_description":"Invalid user credentials"}"""
                        },
                    )
            }
        }
        server.start()
    }

    @AfterTest
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun TestScope.validator(): CopernicusCredentialValidator = CopernicusCredentialValidator {
        CopernicusAuthClient(
            username = it.username,
            password = it.password,
            tokenUrl = server.url("/token").toString(),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }

    @Test
    fun `有效凭据_返回Valid`() = runTest {
        val result = validator().check(CopernicusCredential("user-a", "pass-a"))
        assertEquals(CredentialCheck.Valid, result)
        assertEquals(1, requestCount)
    }

    @Test
    fun `凭据错误401_返回Invalid`() = runTest {
        tokenStatus = 401
        val result = validator().check(CopernicusCredential("bad", "bad"))
        assertTrue(result is CredentialCheck.Invalid, "401 → Invalid，实际 $result")
    }

    @Test
    fun `参数错误400_返回Invalid`() = runTest {
        tokenStatus = 400
        val result = validator().check(CopernicusCredential("user-a", "pass-a"))
        assertTrue(result is CredentialCheck.Invalid, "400 → Invalid，实际 $result")
    }

    @Test
    fun `服务端5xx_返回Unreachable`() = runTest {
        tokenStatus = 503
        val result = validator().check(CopernicusCredential("user-a", "pass-a"))
        assertTrue(result is CredentialCheck.Unreachable, "5xx → Unreachable，实际 $result")
    }

    @Test
    fun `限流429_返回Unreachable`() = runTest {
        tokenStatus = 429
        val result = validator().check(CopernicusCredential("user-a", "pass-a"))
        assertTrue(result is CredentialCheck.Unreachable, "429 → Unreachable，实际 $result")
    }

    @Test
    fun `连接失败_返回Unreachable`() = runTest {
        val v = validator()
        server.shutdown()
        val result = v.check(CopernicusCredential("user-a", "pass-a"))
        assertTrue(result is CredentialCheck.Unreachable, "连接失败 → Unreachable，实际 $result")
    }

    @Test
    fun `未配置或空白凭据_返回Missing且不发请求`() = runTest {
        val v = validator()
        assertEquals(CredentialCheck.Missing, v.check(null))
        assertEquals(CredentialCheck.Missing, v.check(CopernicusCredential("  ", "p")))
        assertEquals(CredentialCheck.Missing, v.check(CopernicusCredential("u", "")))
        assertEquals(0, requestCount, "Missing 态不得发网络请求")
    }
}