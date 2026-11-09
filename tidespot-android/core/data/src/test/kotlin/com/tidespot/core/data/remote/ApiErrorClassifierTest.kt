package com.tidespot.core.data.remote

import com.tidespot.core.data.ApiErrorClassifier
import com.tidespot.core.data.DataError
import com.tidespot.core.data.mock.MockFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * B9 各参数错误响应识别（附录 B9）——状态码 + 错误壳（Open-Meteo / NOAA）全覆盖。
 */
class ApiErrorClassifierTest {

    @Test
    fun `OpenMeteo models 拼错 400 识别为 B9`() {
        val e = ApiErrorClassifier.classify(400, MockFixtures.OPEN_METEO_400_B9_MODELS)
        assertIs<DataError.SourceParamError>(e)
        assertEquals(400, e.httpStatus)
        assertEquals("Cannot initialize MultiDomains", e.reason)
        assertEquals(false, e.isRetryable)
    }

    @Test
    fun `潮位字段拼写 mssh 400 识别为 B9`() {
        val e = ApiErrorClassifier.classify(400, MockFixtures.OPEN_METEO_400_B9_FIELD)
        assertIs<DataError.SourceParamError>(e)
        assertTrue(e.reason.startsWith("Cannot initialize SurfacePressureAndHeightVariable"))
    }

    @Test
    fun `NOAA Wrong Time Zone 识别为 B9`() {
        val e = ApiErrorClassifier.classify(400, MockFixtures.NOAA_400_WRONG_TZ)
        assertIs<DataError.SourceParamError>(e)
        assertEquals("Wrong Time Zone", e.reason)
    }

    @Test
    fun `NOAA 无效站点识别为 B9`() {
        val e = ApiErrorClassifier.classify(
            400,
            """{"error":{"message":" The station is not a valid station or there is system error."}}""",
        )
        assertIs<DataError.SourceParamError>(e)
    }

    @Test
    fun `普通 400 不误判为 B9`() {
        val e = ApiErrorClassifier.classify(400, MockFixtures.OPEN_METEO_400_GENERIC)
        assertIs<DataError.BadRequest>(e)
        assertEquals("Latitude must be in range of [-90,90]", e.reason)
    }

    @Test
    fun `429 归类限流_5xx 归类服务端`() {
        assertIs<DataError.RateLimited>(ApiErrorClassifier.classify(429, ""))
        val e = ApiErrorClassifier.classify(502, "<html>Bad Gateway</html>")
        assertIs<DataError.ServerError>(e)
        assertEquals(502, e.httpStatus)
    }
}
