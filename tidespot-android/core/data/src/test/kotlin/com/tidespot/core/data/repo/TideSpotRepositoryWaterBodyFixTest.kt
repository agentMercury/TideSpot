package com.tidespot.core.data.repo

import com.tidespot.core.data.local.SpotEntity
import com.tidespot.core.data.mock.FakeFetchLogDao
import com.tidespot.core.data.mock.FakeHourlyDao
import com.tidespot.core.data.mock.FakeSpotDao
import com.tidespot.core.data.mock.MockFixtures
import com.tidespot.core.data.remote.OpenMeteoClient
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.model.WaterBodyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * CHG-35（水体判定修复）：
 * - [TideSpotRepository.probeMarineHasValue]：海况有无 → true / false / null（失败不编造）；
 * - 详情刷新自愈：海洋有值而 spot 被历史误判 INLAND/UNKNOWN → 回写 NEARSHORE 并透出 effectiveWaterBody。
 */
class TideSpotRepositoryWaterBodyFixTest {

    private lateinit var server: MockWebServer
    private lateinit var spotDao: FakeSpotDao
    private lateinit var hourlyDao: FakeHourlyDao
    private lateinit var logDao: FakeFetchLogDao
    private val vNow = 1_700_000_000_000L

    private var marineResponder: () -> MockResponse = {
        MockResponse().setResponseCode(200).setBody(MockFixtures.marineJson())
    }

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl!!.encodedPath
                return when {
                    path.endsWith("/v1/forecast") ->
                        MockResponse().setResponseCode(200).setBody(MockFixtures.weatherJson())
                    path.endsWith("/v1/marine") -> marineResponder()
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        spotDao = FakeSpotDao()
        hourlyDao = FakeHourlyDao()
        logDao = FakeFetchLogDao()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun newRepo(): TideSpotRepository = TideSpotRepository(
        client = OpenMeteoClient(
            weatherBaseUrl = server.url("/").toString(),
            marineBaseUrl = server.url("/").toString(),
            clock = { vNow },
            ioDispatcher = Dispatchers.Unconfined,
            // 测试不需要 1s/4s/10s 退避
            retryDelaysMillis = emptyList(),
        ),
        spotDao = spotDao,
        hourlyDao = hourlyDao,
        fetchLogDao = logDao,
        nowMillis = { vNow },
        scoringDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun probeMarine_hasValues_true() = runBlocking {
        assertEquals(true, newRepo().probeMarineHasValue(36.06, 120.38))
    }

    @Test
    fun probeMarine_allNull_false() = runBlocking {
        marineResponder = { MockResponse().setResponseCode(200).setBody(MockFixtures.marineJson(allNull = true)) }
        assertEquals(false, newRepo().probeMarineHasValue(29.55, 118.95))
    }

    @Test
    fun probeMarine_4xx_treatedAsNoMarine_false() = runBlocking {
        marineResponder = { MockResponse().setResponseCode(400).setBody("""{"error":true,"reason":"No data available"}""") }
        assertEquals(false, newRepo().probeMarineHasValue(29.55, 118.95))
    }

    @Test
    fun probeMarine_5xx_returnsNull_unknown() = runBlocking {
        marineResponder = { MockResponse().setResponseCode(500).setBody("""{"error":true}""") }
        assertNull(newRepo().probeMarineHasValue(36.06, 120.38), "失败 → null（未知），禁编造 false")
    }

    @Test
    fun refresh_healsMisclassifiedMarineSpotToNearshore() = runBlocking {
        val id = spotDao.insert(
            SpotEntity(lat = 36.06, lon = 120.38, name = "南海点", waterBodyType = "INLAND_WATER"),
        )
        val repo = newRepo()
        val spot = FavoriteSpot(id = id, name = "南海点", lat = 36.06, lon = 120.38, waterBody = WaterBodyType.INLAND_WATER)

        val result = repo.refreshSpotDetail(spot, force = true)

        assertEquals(WaterBodyType.NEARSHORE, result.effectiveWaterBody, "海洋有值 → 自愈为 NEARSHORE")
        assertEquals(
            "NEARSHORE",
            spotDao.rows[id]!!.waterBodyType,
            "自愈结果必须回写 spot 表（下次打开即正确）",
        )
    }

    @Test
    fun refresh_doesNotTouchInlandPointWithoutMarine() = runBlocking {
        marineResponder = { MockResponse().setResponseCode(200).setBody(MockFixtures.marineJson(allNull = true)) }
        val id = spotDao.insert(
            SpotEntity(lat = 29.55, lon = 118.95, name = "千岛湖", waterBodyType = "INLAND_WATER"),
        )
        val repo = newRepo()
        val spot = FavoriteSpot(id = id, name = "千岛湖", lat = 29.55, lon = 118.95, waterBody = WaterBodyType.INLAND_WATER)

        val result = repo.refreshSpotDetail(spot, force = true)

        assertNull(result.effectiveWaterBody, "内陆点（海洋全 null）不得被改成近岸")
        assertEquals("INLAND_WATER", spotDao.rows[id]!!.waterBodyType)
    }
}