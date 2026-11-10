package com.tidespot.core.data.repo

import com.tidespot.core.data.DataError
import com.tidespot.core.data.dto.MarineResponseDto
import com.tidespot.core.data.dto.OpenMeteoTime
import com.tidespot.core.data.dto.WeatherResponseDto
import com.tidespot.core.data.mock.FakeFetchLogDao
import com.tidespot.core.data.mock.FakeHourlyDao
import com.tidespot.core.data.mock.FakeHourlyScoreDao
import com.tidespot.core.data.mock.FakeManualObservationDao
import com.tidespot.core.data.mock.FakeManualTideFitDao
import com.tidespot.core.data.mock.FakeSpotDao
import com.tidespot.core.data.mock.MockFixtures
import com.tidespot.core.data.remote.MinRefreshGate
import com.tidespot.core.data.remote.OpenMeteoClient
import com.tidespot.core.data.remote.TideSpotUserAgent
import com.tidespot.core.domain.FishScoreCalculator
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.model.WaterBodyType
import com.tidespot.core.domain.model.VetoCode
import com.tidespot.core.domain.tide.TideCalibrator
import com.tidespot.core.domain.window.BestWindowFinder
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import java.util.concurrent.Executors
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M2 退出标准：MockWebServer 6 状态 + TTL/UA/fetch_log/join 断言。
 *
 * ① 200 正常（解析 + join）② 200 全 null（内陆）③ 400（B9 文案识别）
 * ④ 429（限流重试路径）⑤ 500（重试耗尽 → 保留旧数据）⑥ 网络异常（1s/4s/10s 虚拟时钟断言）
 * 附加：⑦ TTL 命中 0 请求；⑧ UA 与 §5.1/§5.2 URL 参数防回归。
 */
class TideSpotRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var spotDao: FakeSpotDao
    private lateinit var hourlyDao: FakeHourlyDao
    private lateinit var logDao: FakeFetchLogDao
    private lateinit var scoreDao: FakeHourlyScoreDao

    /** 虚拟墙钟：与 client 时钟同源，TTL/fetchedAt 断言可精确控制。 */
    private var vNow = 1_700_000_000_000L

    private var weatherResponder: (RecordedRequest) -> MockResponse = {
        MockResponse().setResponseCode(200).setBody(MockFixtures.weatherJson())
    }
    private var marineResponder: (RecordedRequest) -> MockResponse = {
        MockResponse().setResponseCode(200).setBody(MockFixtures.marineJson())
    }
    private var weatherRequests = 0
    private var marineRequests = 0
    private val recordedRequests = mutableListOf<RecordedRequest>()

    private lateinit var repo: TideSpotRepository
    private lateinit var spot: FavoriteSpot

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recordedRequests += request
                // 注意：request.path 含查询串，必须用 encodedPath 匹配
                val path = request.requestUrl!!.encodedPath
                return when {
                    path.endsWith("/v1/forecast") -> {
                        weatherRequests++
                        weatherResponder(request)
                    }
                    path.endsWith("/v1/marine") -> {
                        marineRequests++
                        marineResponder(request)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()

        spotDao = FakeSpotDao()
        hourlyDao = FakeHourlyDao()
        logDao = FakeFetchLogDao()
        scoreDao = FakeHourlyScoreDao()
        spot = FavoriteSpot(id = 0, name = "青岛外海", lat = 36.06, lon = 120.38)

        buildRepo().also { repo = it }
        // 先落收藏拿 spot.id（runTest 外用 runBlocking 不便，改在各测试内首行 ensureSpot）
    }

    /** @param scheduler 测试虚拟调度器：client 时钟与重试 delay 都走它（可断言 1s/4s/10s）。 */
    private fun buildRepo(
        scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
        weatherBaseUrl: String = server.url("/").toString(),
        callTimeoutMillis: Long = 30_000L,
        manualObservationDao: com.tidespot.core.data.local.ManualObservationDao? = null,
        manualTideFitDao: com.tidespot.core.data.local.ManualTideFitDao? = null,
        scoringDispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
    ): TideSpotRepository =
        TideSpotRepository(
            client = OpenMeteoClient(
                weatherBaseUrl = weatherBaseUrl,
                marineBaseUrl = server.url("/").toString(),
                clock = { vNow },
                ioDispatcher = UnconfinedTestDispatcher(scheduler),
                callTimeoutMillis = callTimeoutMillis,
            ),
            spotDao = spotDao,
            hourlyDao = hourlyDao,
            fetchLogDao = logDao,
            hourlyScoreDao = scoreDao,
            refreshGate = MinRefreshGate(),
            nowMillis = { vNow },
            manualObservationDao = manualObservationDao,
            manualTideFitDao = manualTideFitDao,
            scoringDispatcher = scoringDispatcher,
        )

    private suspend fun TideSpotRepository.ensureSpot(): FavoriteSpot =
        if (spot.id > 0) spot else {
            val id = upsertFavorite(spot)
            spot = spot.copy(id = id)
            spot
        }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun ok200(body: String) =
        MockResponse().setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    // ────────────────────────────── ① 200 正常 ──────────────────────────────

    @Test
    fun `状态1_200正常_解析与按时间戳join正确`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        val result = repo.refreshSpotDetail(s)

        // join：天气 8h [T0..T0+7h]，海洋 5h [T0+3h..T0+7h]
        assertEquals(8, result.slices.size, "join 为时间戳并集")
        assertEquals(MockFixtures.BASE_EPOCH_SEC, result.slices[0].timeEpochSec)
        assertNull(result.slices[0].marine, "前 3 小时无海洋数据（下标对齐会误配对）")
        assertEquals(10.0, result.slices[0].weather?.temperatureC)
        // 第 4 小时：weather[3]=13.0 与 marine[0]（wave=0.3）按时间戳配对
        assertEquals(13.0, result.slices[3].weather?.temperatureC)
        assertEquals(0.3, result.slices[3].marine?.waveHeightM)
        assertEquals(19.5, result.slices[3].marine?.seaSurfaceTempC)
        assertEquals(1.2, result.slices[3].marine?.oceanCurrentVelocityKmh)
        assertEquals(0.7, result.slices[7].marine?.waveHeightM)
        assertEquals(17.0, result.slices[7].weather?.temperatureC)

        assertTrue(result.weather.isNotEmpty() && result.marine.isNotEmpty())
        assertTrue(!result.weatherDegraded && !result.marineDegraded)
        assertTrue(result.errors.isEmpty())

        // 整窗入库：8h×21（天气含湿度） + 5h×16（海洋）
        assertEquals(8 * 21 + 5 * 16, hourlyDao.rows.size)
        // fetch_log 各源一条 200（F-4.6）
        assertEquals(2, logDao.rows.count { it.httpStatus == 200 })

        // daily 块透出
        assertEquals(1, result.daily?.time?.size)
    }

    @Test
    fun `状态1b_UA与URL参数防回归`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        repo.refreshSpotDetail(s)

        val paths = recordedRequests.map { it.requestUrl!!.encodedPath }
        val weatherReq = recordedRequests.first { it.requestUrl!!.encodedPath.endsWith("/v1/forecast") }
        val marineReq = recordedRequests.first { it.requestUrl!!.encodedPath.endsWith("/v1/marine") }

        assertEquals(2, paths.size, "详情页一次刷新 = 天气+海洋 2 请求（≤4 并行，F-4.1）")
        // §5.0/CONTRACT 冻结点 5：统一 UA
        assertEquals(
            TideSpotUserAgent.VALUE,
            weatherReq.getHeader("User-Agent"),
            "UA 常量必须逐请求注入（§12 防回归）",
        )
        assertEquals(TideSpotUserAgent.VALUE, marineReq.getHeader("User-Agent"))
        // §5.1：past_days=7 非可选；整窗 forecast_days=10（CHG-03）；timezone=auto
        val wq = weatherReq.requestUrl!!
        assertEquals("7", wq.queryParameter("past_days"))
        assertEquals("10", wq.queryParameter("forecast_days"))
        assertEquals("auto", wq.queryParameter("timezone"))
        assertTrue(wq.queryParameter("hourly")!!.contains("temperature_2m"))
        assertTrue(wq.queryParameter("hourly")!!.contains("cape"))
        // §5.2：past_days=1；陷阱字段 sea_level_height_msl（B9 防回归）
        val mq = marineReq.requestUrl!!
        assertEquals("1", mq.queryParameter("past_days"))
        assertEquals("10", mq.queryParameter("forecast_days"))
        val marineHourly = mq.queryParameter("hourly")!!
        assertTrue(marineHourly.contains("sea_level_height_msl"))
        assertTrue(!marineHourly.contains("sea_level_height_mssh"), "禁止拼写错误的潮位字段")
        assertTrue(marineHourly.contains("ocean_current_velocity"))
    }

    // ────────────────────────────── ② 200 全 null（内陆） ──────────────────────────────

    @Test
    fun `状态2_内陆全null_触发水体降级且禁止0填充`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        marineResponder = { ok200(MockFixtures.marineJson(allNull = true)) }

        val result = repo.refreshSpotDetail(s)

        assertTrue(result.marineAllNullInland, "附录 B8：200+全 null → isAllNull 全真")
        assertTrue(result.marineDegraded, "§6.9 降级触发（海洋侧）")
        assertTrue(!result.weatherDegraded, "分维降级：天气不受影响（F-3.6）")
        assertTrue(result.errors.isEmpty(), "全 null 是正常态，不是错误")
        // null 禁止当 0：切片海洋侧为 null，而非 0
        val marineSlice = result.slices.last { it.marine != null || it.weather != null }.marine
        assertNull(marineSlice?.waveHeightM, "内陆点禁止出现 wave=0 假海况")
        // 显式 null 行入库（保真 §5.0 null 语义）
        val waveRows = hourlyDao.rows.filter { it.field == "wave_height" && it.kind == "marine" }
        assertEquals(5, waveRows.size)
        assertTrue(waveRows.all { it.value == null })
    }

    // ────────────────────────────── ③ 400（B9 文案识别） ──────────────────────────────

    @Test
    fun `状态3_400_B9文案识别且不重试_天气照常`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        marineResponder = {
            MockResponse().setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody(MockFixtures.OPEN_METEO_400_B9_MODELS)
        }

        val result = repo.refreshSpotDetail(s)

        assertEquals(1, marineRequests, "B9 参数错误不可重试（重试会打爆频控）")
        val err = result.errors[DataSource.MARINE]
        assertIs<DataError.SourceParamError>(err)
        assertEquals(400, err.httpStatus)
        assertEquals("Cannot initialize MultiDomains", err.reason, "B9 文案必须原样透出")
        assertTrue(result.marineDegraded && !result.weatherDegraded)
        assertTrue(result.weather.isNotEmpty(), "海洋失败不影响天气（F-3.6 分维降级）")
        assertTrue(result.slices.all { it.marine == null })
    }

    // ────────────────────────────── ④ 429（限流重试路径） ──────────────────────────────

    @Test
    fun `状态4_429限流_退避重试后成功`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        weatherResponder = {
            if (weatherRequests <= 2) {
                MockResponse().setResponseCode(429).setBody("""{"error":true,"reason":"Rate limit exceeded"}""")
            } else {
                ok200(MockFixtures.weatherJson())
            }
        }
        val t0 = testScheduler.currentTime
        val result = repo.refreshSpotDetail(s)

        assertEquals(3, weatherRequests, "429 两次 + 第三次成功（1s/4s 退避）")
        assertTrue(result.errors.isEmpty(), "重试成功后无错误")
        assertTrue(result.weather.isNotEmpty())
        assertEquals(5_000L, testScheduler.currentTime - t0, "429 退避 1s+4s = 5s（虚拟时钟）")
    }

    // ────────────────────────────── ⑤ 500（重试耗尽 → 保留旧数据） ──────────────────────────────

    @Test
    fun `状态5_500重试耗尽_保留旧缓存数据`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        val first = repo.refreshSpotDetail(s)
        assertTrue(!first.weatherDegraded)
        val oldWeatherCount = hourlyDao.rows.count { it.kind == "weather" }

        // 天气 TTL 3h 过期、海洋 6h 仍新鲜 → 只会再请求天气
        vNow += 4 * 3600_000L
        weatherResponder = {
            MockResponse().setResponseCode(500).setBody("""{"error":true,"reason":"internal"}""")
        }
        val t0 = testScheduler.currentTime
        val result = repo.refreshSpotDetail(s)

        // 本轮天气重试耗尽：4 次（首轮已用 1 次，累计 1+4）；海洋 TTL 未到 → 0 请求
        assertEquals(1 + 4, weatherRequests)
        assertEquals(1, marineRequests)
        assertEquals(15_000L, testScheduler.currentTime - t0, "1s+4s+10s 退避（虚拟时钟）")

        val err = result.errors[DataSource.WEATHER]
        assertIs<DataError.ServerError>(err)
        assertEquals(500, err.httpStatus)
        assertTrue(result.weatherDegraded, "重试耗尽 → 天气降级标记")
        assertTrue(DataSource.WEATHER in result.servedFromCache, "重试期保留旧数据（F-4.5/F-2.3）")
        // 旧数据保真：值与首轮一致，未被空列表/0 覆盖
        assertEquals(first.weather.map { it.temperatureC }, result.weather.map { it.temperatureC })
        assertEquals(oldWeatherCount, hourlyDao.rows.count { it.kind == "weather" }, "失败不写库")
    }

    // ────────────────────────────── ⑥ 网络异常 ──────────────────────────────

    @Test
    fun `状态6_网络异常_3次退避1s4s10s后降级_海洋不受影响`() = runTest {
        // 天气端点指向已关闭端口 → 每次尝试连接层失败
        val deadServer = MockWebServer()
        deadServer.start()
        val deadUrl = deadServer.url("/").toString()
        deadServer.shutdown()

        repo = buildRepo(testScheduler, weatherBaseUrl = deadUrl)
        val s = repo.ensureSpot()
        val t0 = testScheduler.currentTime
        val result = repo.refreshSpotDetail(s)

        assertEquals(15_000L, testScheduler.currentTime - t0, "网络异常退避 1s+4s+10s = 15s（F-4.5）")
        val err = result.errors[DataSource.WEATHER]
        assertIs<DataError.NetworkError>(err)
        assertTrue(result.weatherDegraded)
        assertTrue(result.weather.isEmpty(), "无历史缓存 → 空序列（不造假数据）")
        assertTrue(!result.marineDegraded, "分维降级：海洋照常（F-3.6）")
        assertTrue(result.marine.isNotEmpty())
        assertTrue(result.slices.all { it.weather == null }, "join 后天气侧为 null，非 0")
    }

    // ────────────────────────────── ⑦ TTL 命中 0 请求 ──────────────────────────────

    @Test
    fun `状态7_TTL内重复进入_0网络请求直接回缓存`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        repo.refreshSpotDetail(s)
        val fetchedAtFirst = vNow
        assertEquals(1, weatherRequests)
        assertEquals(1, marineRequests)

        vNow += 2 * 3600_000L // 2h < 天气 3h TTL
        val second = repo.refreshSpotDetail(s)

        assertEquals(1, weatherRequests, "F-4.2：缓存未过期 → 0 网络请求")
        assertEquals(1, marineRequests)
        assertEquals(setOf(DataSource.WEATHER, DataSource.MARINE), second.servedFromCache)
        assertTrue(second.errors.isEmpty())
        assertEquals(fetchedAtFirst, second.fetchedAtMillis[DataSource.WEATHER], "数据时间 = 首轮拉取时刻")
        // 数据与首轮一致
        assertEquals(10.0, second.slices[0].weather?.temperatureC)
    }

    // ────────────────────────────── ⑧ 收藏 CRUD + fetch_log 可查 ──────────────────────────────

    @Test
    fun `收藏CRUD与fetch_log可视化`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        repo.refreshSpotDetail(s)

        // fetch_log：天气 1 条 200 + 海洋 1 条 200（F-4.6/N-6）
        val logs = repo.fetchLogs(s.id)
        assertEquals(2, logs.size)
        assertTrue(logs.all { it.httpStatus == 200 && it.bytes != null && it.costMs >= 0 })

        // 编辑 / 删除
        repo.upsertFavorite(s.copy(name = "青岛外海(改)"))
        assertEquals("青岛外海(改)", spotDao.rows[s.id]!!.name)
        repo.deleteFavorite(s.id)
        assertTrue(spotDao.rows.isEmpty())
        assertTrue(hourlyDao.rows.isEmpty(), "删除收藏应连带清理小时缓存")
    }

    // ─────────────── BLK-06/N-4（CHG-07 修复 4）：强制异常用例 ───────────────

    @Test
    fun `状态9_脏JSON的200响应_不抛异常_结构化NetworkError降级`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        // 截断 + time:null 的脏响应（SerializationException 源），HTTP 200 走解析路径
        weatherResponder = {
            ok200("""{"latitude":36.06,"utc_offset_seconds":28800,"hourly":{"time":null""")
        }
        val result = repo.refreshSpotDetail(s)   // 不得抛 SerializationException（N-4）

        val err = result.errors[DataSource.WEATHER]
        assertIs<DataError.NetworkError>(err, "脏 JSON → 结构化 NetworkError（保 B9 语义）")
        assertTrue(result.weatherDegraded, "解析失败 → 天气降级标记")
        assertTrue(result.weather.isEmpty(), "无历史缓存 → 空序列（不造假数据）")
        assertEquals(1, weatherRequests, "解析在重试循环外：脏 JSON 不做无谓重试")
        assertTrue(result.slices.all { it.weather == null }, "join 后天气侧为 null，非 0")
        assertTrue(!result.marineDegraded, "分维降级：海洋不受影响（F-3.6）")
    }

    @Test
    fun `状态10_非数字时间串_200响应_跳过脏小时不抛异常`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        // 8 小时中第 2 条 time 为非数字串：该小时整行跳过，其余 7 小时保真
        // 时间串必须从夹具常量推导（禁止硬编码日期：BASE_EPOCH_SEC 的绝对日期无意义，硬编码会与海洋轴错开）
        val times = (0 until MockFixtures.WEATHER_HOURS).map { i ->
            if (i == 2) "not-a-time"
            else OpenMeteoTime.localIso(MockFixtures.BASE_EPOCH_SEC + i * 3600L, MockFixtures.TZ_OFFSET_SEC)
        }
        val dirty = MockFixtures.weatherJson().replaceFirst(
            Regex(""""time":\[[^\]]*\]"""),
            """"time":[""" + times.joinToString(",") { "\"$it\"" } + """]""",
        )
        weatherResponder = { ok200(dirty) }
        val result = repo.refreshSpotDetail(s)   // 不得抛 DateTimeParseException（N-4）

        assertTrue(result.errors.isEmpty(), "脏时间串是数据质量降级，不是请求错误（正常 200）")
        assertTrue(!result.weatherDegraded)
        assertEquals(MockFixtures.WEATHER_HOURS - 1, result.weather.size, "仅跳过 1 个脏小时")
        assertEquals(7 * 21, hourlyDao.rows.count { it.kind == "weather" }, "整窗入库同步跳过脏小时")
        // 时间戳 join 仍正确：weather 剩 {T0,T1,T3..T7} 7 小时，marine {T3..T7} 5 小时全在其内
        assertEquals(7, result.slices.size)
    }
    // ────────────────────────────── 内陆水体类型辅助断言 ──────────────────────────────

    @Test
    fun `elevation透出供水体判定_收藏水体类型持久化`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        repo.refreshSpotDetail(s)
        // 海拔写入收藏实体（§6.9 判定输入；M0 结论：仅辅助判据）
        assertTrue(hourlyDao.rows.isNotEmpty())

        // CHG-09①：updateSpotWaterBody 真断言——waterBodyType/elevation/tzOffsetSec 三字段写入值
        repo.updateSpotWaterBody(s.id, WaterBodyType.INLAND_WATER, elevation = 108.0, tzOffsetSec = 28800)
        val row = spotDao.rows[s.id]!!
        assertEquals("INLAND_WATER", row.waterBodyType)
        assertEquals(108.0, row.elevation)
        assertEquals(28800, row.tzOffsetSec)
        // 其余字段不受影响（name/tags 等不被覆盖）
        assertEquals("青岛外海", row.name)
        // UNKNOWN 同样可写（CHG-09② 待判定）；D08（M5 客户裁决 COALESCE）：null = 保持原值
        repo.updateSpotWaterBody(s.id, WaterBodyType.UNKNOWN, elevation = null, tzOffsetSec = null)
        val unknown = spotDao.rows[s.id]!!
        assertEquals("UNKNOWN", unknown.waterBodyType)
        assertEquals(108.0, unknown.elevation, "D08：传 null 保持原值，不得置 NULL")
        assertEquals(28800, unknown.tzOffsetSec, "D08：传 null 保持原值，不得置 NULL")
    }

    // ────────────────────────────── M5 A-15 锁测试（D07/D08/D03） ──────────────────────────────

    @Test
    fun `D07_编辑收藏不冲掉派生列_patch合并语义`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        // 派生列经确认选点流水线写入（CHG-09①）
        repo.updateSpotWaterBody(s.id, WaterBodyType.INLAND_WATER, elevation = 108.0, tzOffsetSec = 28800)
        // 编辑名称+备注（走 upsertFavorite → @Update 全列覆盖路径）
        repo.upsertFavorite(s.copy(name = "青岛外海(改)", memo = "新备注"))
        val row = spotDao.rows[s.id]!!
        assertEquals("青岛外海(改)", row.name, "编辑内容正常落库")
        assertEquals("新备注", row.note)
        assertEquals(108.0, row.elevation, "D07：toEntity 不携带 elevation，@Update 前必须 patch 合并旧值")
        assertEquals(28800, row.tzOffsetSec, "D07：toEntity 不携带 tzOffsetSec，@Update 前必须 patch 合并旧值")
        assertEquals("INLAND_WATER", row.waterBodyType)
        // domain 往返：toDomain 映射 lastViewedAt（M5 尾增字段）
        val domain = repo.favorites().first { it.id == s.id }
        assertEquals("青岛外海(改)", domain.name)
    }

    @Test
    fun `D08_updateWaterBody_null保持原值与Room_COALESCE同语义`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        repo.updateSpotWaterBody(s.id, WaterBodyType.INLAND_WATER, elevation = 108.0, tzOffsetSec = 28800)
        // null 写入 → 派生列保持原值（Fake 与 Room SQL COALESCE 双侧同语义，Room 侧由 RoomDaoTest 真机项覆盖）
        repo.updateSpotWaterBody(s.id, WaterBodyType.NEARSHORE, elevation = null, tzOffsetSec = null)
        val row = spotDao.rows[s.id]!!
        assertEquals("NEARSHORE", row.waterBodyType, "waterBodyType 无 COALESCE，总是覆盖")
        assertEquals(108.0, row.elevation, "D08 客户裁决：elevation null = 保持原值")
        assertEquals(28800, row.tzOffsetSec, "D08 客户裁决：tzOffsetSec null = 保持原值")
    }

    @Test
    fun `D03_恒等对拍_读侧重建find与现算find输出逐字段一致`() = runTest {
        // 落库走生产路径（48h 天气夹具 ≥ 24h，可进真实窗口计算而非禁用分支）
        weatherResponder = { ok200(MockFixtures.weatherJson(hourCount = 48)) }
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        repo.refreshSpotDetail(s)
        assertTrue(scoreDao.rows.isNotEmpty(), "refreshSpotDetail 必须落库 hourly_score（D03 读侧前提）")

        // 现算侧：与生产同参（同 JSON → DTO → domain → buildScoreInputs → calculate）直接 find
        val json = Json { ignoreUnknownKeys = true }
        val wDto = json.decodeFromString(WeatherResponseDto.serializer(), MockFixtures.weatherJson(hourCount = 48))
        val mDto = json.decodeFromString(MarineResponseDto.serializer(), MockFixtures.marineJson())
        val tz = HourlyScoreStore.resolveTzOffsetSec(wDto, mDto)!!
        val inputs = HourlyScoreStore.buildScoreInputs(
            DtoMappers.toHourlyWeatherList(wDto), DtoMappers.toHourlyMarineList(mDto), wDto.daily, tz,
        )
        val results = FishScoreCalculator().calculate(inputs, HourlyScoreStore.presetFor(s), s.lon)
        assertTrue(results.size >= 24, "对拍前提：有效小时 ≥24（实际 ${results.size}）")
        // 与 topWindows 同参（CHG-15 偏差①：夹具数据全部晚于 vNow → nextHour 过滤不剔除任何行）
        val nowSec = vNow / 1000
        val nextHour = nowSec - nowSec % 3600 + 3600
        val expect = BestWindowFinder.find(results, tideRates = null, validHoursPresent = results.size, nowEpochSec = nextHour)

        // 读侧：hourly_score 行重建 → find（topWindows 生产路径）
        val actual = repo.topWindows(s)
        assertEquals(expect.windows, actual.windows, "读侧重建与现算 find 的 Top10 必须逐字段恒等")
        assertEquals(expect.fallbackMessage, actual.fallbackMessage)
        assertEquals(expect.fallbackBestEpochSec, actual.fallbackBestEpochSec)
        assertEquals(results.size, actual.validHoursPresent, "夹具数据全部在未来 → 未来有效行数 = 全部行数")
    }

    @Test
    fun `D03_topWindows_库内全过期_禁用文案且未来有效行数为0`() = runTest {
        // 库内 48h 全部早于 now（base = now - 600h）→ 时间域过滤后未来 0 行 → 禁用（不得用历史窗口冒充未来窗口）
        val pastBase = vNow / 1000 - 600 * 3600
        weatherResponder = { ok200(MockFixtures.weatherJson(hourCount = 48, baseEpochSec = pastBase)) }
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        repo.refreshSpotDetail(s)
        assertTrue(scoreDao.rows.isNotEmpty(), "落库前提")
        val r = repo.topWindows(s)
        assertEquals(0, r.windows.size, "全过期 → 无任何未来窗口")
        assertEquals(0, r.validHoursPresent, "未来有效行数 = 0")
        assertTrue(r.fallbackMessage?.contains("不足 24") == true, "禁用文案：${r.fallbackMessage}")
        assertNull(r.fallbackBestEpochSec, "禁用分支无兜底时刻")
    }

    // ────────── §10.2 六态补齐（BLK-02 / M4 必修 1）：超时 / 部分字段缺失 / 陈旧缓存无网 ──────────

    @Test
    fun `状态11_超时_callTimeout触发_结构化NetworkError且海洋不受影响`() = runTest {
        repo = buildRepo(testScheduler, callTimeoutMillis = 600)
        val s = repo.ensureSpot()
        // SocketPolicy.NO_RESPONSE：连接建立后永不响应 → callTimeout 到期（§10.2「超时」态）
        weatherResponder = { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
        val t0 = testScheduler.currentTime
        val result = repo.refreshSpotDetail(s)   // 不得抛异常（N-4）

        assertEquals(4, weatherRequests, "callTimeout 4 次尝试（1+3 重试，退避 1s/4s/10s 虚拟时钟）")
        val err = result.errors[DataSource.WEATHER]
        assertIs<DataError.NetworkError>(err)
        assertIs<java.io.InterruptedIOException>(err.cause, "callTimeout 到期 → OkHttp 超时（InterruptedIOException）")
        assertTrue(result.weatherDegraded)
        assertTrue(!result.marineDegraded, "分维降级：海洋照常（F-3.6）")
        assertTrue(result.weather.isEmpty(), "无历史缓存 → 空序列（不造假数据）")
        assertEquals(15_000L, testScheduler.currentTime - t0, "退避 1s+4s+10s（虚拟时钟）")
    }

    @Test
    fun `状态12_部分字段缺失_缺数组字段null_其余保真`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        // §10.2「部分字段缺失」态：200 正常响应但 hourly 缺 wind_speed_10m / weather_code 两个键
        weatherResponder = {
            ok200(MockFixtures.weatherJson(omitFields = setOf("wind_speed_10m", "weather_code")))
        }
        val result = repo.refreshSpotDetail(s)

        assertTrue(result.errors.isEmpty(), "部分字段缺失是正常 200 降维，不是请求错误")
        assertTrue(!result.weatherDegraded)
        val w0 = result.weather[0]
        assertNull(w0.windSpeedKmh, "缺数组字段 = null（§5.0 禁 0 填充）")
        assertNull(w0.weatherCode)
        assertEquals(10.0, w0.temperatureC, "其余字段保真")
        assertEquals(15.0, w0.windGustKmh, "未缺失字段不受影响")
        // 入库同步：缺失字段落显式 null 行（不落假 0、不缺行）
        assertTrue(hourlyDao.rows.none { it.field == "wind_speed_10m" && it.value != null })
        assertEquals(
            MockFixtures.WEATHER_HOURS * WeatherFieldNames.ALL.size,
            hourlyDao.rows.count { it.kind == "weather" },
            "缺失字段仍落显式 null 行（21 字段 × 8h）",
        )
    }

    @Test
    fun `状态13_陈旧缓存无网_超TTL回缓存_过期标记0请求`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        repo.refreshSpotDetail(s)
        vNow += 4 * 3600_000L   // 天气 TTL 3h 过期（海洋 6h 仍新鲜）

        // 无网：cachedDetail 纯本地读取（C7 离线可读 / §10.2「陈旧缓存无网」态）
        val result = repo.cachedDetail(s)

        assertEquals(1, weatherRequests, "cachedDetail 0 网络请求")
        assertTrue(result.weatherDegraded, "超 TTL → 「数据时间已过期」标记（§6.7）")
        assertTrue(!result.marineDegraded)
        assertEquals(setOf(DataSource.WEATHER, DataSource.MARINE), result.servedFromCache)
        assertEquals(10.0, result.slices[0].weather?.temperatureC, "陈旧数据保真显示（禁清空/补 0）")
        assertTrue(result.weather.isNotEmpty())
        assertTrue(result.errors.isEmpty(), "无网回缓存不是请求错误")
    }

    // ────────── FO-17 刷新状态机三修（M4 必修 3） ──────────

    @Test
    fun `FO17a_force绕过15min gate_TTL内强制刷新重新拉取`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        repo.refreshSpotDetail(s)
        assertEquals(1, weatherRequests)

        vNow += 60_000L   // 1min：TTL 未到 + 15min gate 内
        repo.refreshSpotDetail(s)
        assertEquals(1, weatherRequests, "非 force：15min gate 拦截 → 0 请求")

        vNow += 60_000L
        val forced = repo.refreshSpotDetail(s, force = true)
        assertEquals(2, weatherRequests, "force 绕过 TTL + 15min gate → 真实拉取")
        assertEquals(2, marineRequests)
        assertTrue(forced.errors.isEmpty())
        assertEquals(vNow, forced.fetchedAtMillis[DataSource.WEATHER], "强制刷新后数据时间 = 本次成功时刻")
    }

    @Test
    fun `FO17b_首刷数据时间等于本次成功时刻_不回填1970`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        val result = repo.refreshSpotDetail(s)

        assertEquals(vNow, result.fetchedAtMillis[DataSource.WEATHER], "首刷成功 → 数据时间 = 本次成功时刻（禁 0/1970）")
        assertEquals(vNow, result.fetchedAtMillis[DataSource.MARINE])
        assertTrue(result.fetchedAtMillis.values.all { it > 0 })
    }

    @Test
    fun `FO17b_重试失败回退旧缓存_数据时间保留上一轮成功时刻`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        repo.refreshSpotDetail(s)
        val firstFetchAt = vNow

        vNow += 4 * 3600_000L   // 天气 TTL 过期
        weatherResponder = { MockResponse().setResponseCode(500) }
        val result = repo.refreshSpotDetail(s)

        assertEquals(firstFetchAt, result.fetchedAtMillis[DataSource.WEATHER], "失败 → 保留上一轮成功时刻（禁 1970）")
        assertTrue(result.weatherDegraded)
    }

    @Test
    fun `FO17c_同点重复刷新_gate去重_仅一次网络请求`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        coroutineScope {
            val a = async { repo.refreshSpotDetail(s) }
            val b = async { repo.refreshSpotDetail(s) }
            a.await()
            b.await()
        }
        assertEquals(1, weatherRequests, "gate 在途占用/成功窗口去重 → 同点无重复请求")
        assertEquals(1, marineRequests)
    }

    @Test
    fun `FO17c_刷新失败不占15min窗口_可立即重试`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        marineResponder = { MockResponse().setResponseCode(500) }
        repo.refreshSpotDetail(s)   // 海洋 500 重试耗尽（天气成功）→ roundSucceeded=false → 不 record
        val afterFirst = marineRequests
        assertEquals(4, afterFirst, "500 重试耗尽 4 次尝试")

        vNow += 60_000L
        marineResponder = { ok200(MockFixtures.marineJson()) }
        val second = repo.refreshSpotDetail(s, force = true)   // 立即重试：失败未占 gate
        assertTrue(marineRequests > afterFirst, "失败不占 15min 窗口 → 可立即重试")
        assertTrue(second.errors.isEmpty())
    }

    // ────────── FO-18 时区 ZoneId 化（M4 必修 4）：响应时区 → spot.tzOffsetSec ──────────

    @Test
    fun `FO18_响应时区换算写入spot_tzOffsetSec_JST加9`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        weatherResponder = { ok200(MockFixtures.weatherJson(tzOffsetSec = 9 * 3600, ianaTz = "Asia/Tokyo")) }
        marineResponder = { ok200(MockFixtures.marineJson(tzOffsetSec = 9 * 3600, ianaTz = "Asia/Tokyo")) }
        repo.refreshSpotDetail(s)

        assertEquals(9 * 3600, spotDao.rows[s.id]!!.tzOffsetSec, "JST +9 → tzOffsetSec=32400（F-3.8）")
    }

    @Test
    fun `FO18_半点时区加5_30写入spot_tzOffsetSec`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        weatherResponder = { ok200(MockFixtures.weatherJson(tzOffsetSec = 19800, ianaTz = "Asia/Kolkata")) }
        marineResponder = { ok200(MockFixtures.marineJson(tzOffsetSec = 19800, ianaTz = "Asia/Kolkata")) }
        repo.refreshSpotDetail(s)

        assertEquals(19800, spotDao.rows[s.id]!!.tzOffsetSec, "+5:30 → 19800（N-9/F-3.8）")
    }

    // ────────── M4 必修 8：评分 + hourly_score 整窗一次性落库（§7.3） ──────────

    @Test
    fun `必修8_refreshSpotDetail评分后hourly_score整窗落库_空vetoReason映射NULL`() = runTest {
        repo = buildRepo(testScheduler)
        val s = repo.ensureSpot()
        repo.refreshSpotDetail(s)

        val rows = scoreDao.rows
        assertTrue(rows.isNotEmpty(), "评分完成后整窗一次性落库（§7.3/CHG-03）")
        assertEquals(MockFixtures.WEATHER_HOURS, rows.map { it.hourEpoch }.distinct().size, "逐小时一行（8h 夹具）")
        assertTrue(rows.all { it.algoVersion == FishScoreCalculator.ALGO_VERSION }, "algoVersion 入键（N-10）")
        assertTrue(rows.all { it.spotId == s.id })
        assertTrue(rows.all { it.score in 0.0..100.0 })
        assertTrue(rows.all { it.band.isNotEmpty() }, "档位文案（§6.3）")
        assertTrue(rows.all { it.vetoReason == null }, "CHG-08 ④：夹具良态小时无否决 → 领域空列表 → SQL NULL")
        assertTrue(rows.all { it.factorsJson.startsWith("[") }, "factorsJson = FactorScore JSON 数组")
        assertEquals(MockFixtures.TZ_OFFSET_SEC, spotDao.rows[s.id]!!.tzOffsetSec, "tzOffsetSec 同步回写（+8 夹具）")
        // hourLocal = 当地墙串（F-3.8）：首行 == 夹具基时刻的 +8 墙串
        assertEquals(
            OpenMeteoTime.localIso(MockFixtures.BASE_EPOCH_SEC, MockFixtures.TZ_OFFSET_SEC),
            rows.minByOrNull { it.hourEpoch }!!.hourLocal,
        )
        // 再次刷新（TTL 过期）→ 整窗替换（delete-then-insert），无残留旧行
        vNow += 4 * 3600_000L
        repo.refreshSpotDetail(s)
        assertEquals(
            MockFixtures.WEATHER_HOURS,
            scoreDao.rows.map { it.hourEpoch }.distinct().size,
            "整窗替换语义（CHG-03），无重复/残留行",
        )
    }

    // ────────── M6（CHG-16⑥⑦⑧⑨）：校潮持久化 / 应用闭环 / D13 / D07 扩展 ──────────

    @Test
    fun `M6_观测登记与读回_升序_未注入DAO写路径抛IllegalState`() = runTest {
        // 未注入（既有调用点形态）：写路径抛 IllegalStateException（防静默丢观测），读路径=未登记（空列表）
        val s = repo.ensureSpot()
        assertFailsWith<IllegalStateException> { repo.addTideObservation(s.id, 1_700_000_000L) }
        assertTrue(repo.tideObservations(s.id).isEmpty(), "DAO 未注入 → 空列表（未登记语义，KDoc 口径）")
        assertNull(repo.manualTideFit(s.id))

        // 注入 Fake：登记 3 条乱序 → 读回升序 + 字段保真
        val obsDao = FakeManualObservationDao()
        val fitDao = FakeManualTideFitDao()
        repo = buildRepo(testScheduler, manualObservationDao = obsDao, manualTideFitDao = fitDao)
        repo.addTideObservation(s.id, 1_700_000_300L)
        repo.addTideObservation(s.id, 1_700_000_100L)
        repo.addTideObservation(s.id, 1_700_000_200L)
        val obs = repo.tideObservations(s.id)
        assertEquals(
            listOf(1_700_000_100L, 1_700_000_200L, 1_700_000_300L),
            obs.map { it.observedAtEpochSec },
            "按观测时刻升序（对齐 DAO @Query 口径）",
        )
        assertEquals(s.id, obs.first().spotId)
        assertEquals(vNow, obs.first().createdAtEpochMs, "createdAtEpochMs = 记录时刻（虚拟墙钟）")
    }

    @Test
    fun `M6_拟合登记_adopted_false也登记_读回字段保真`() = runTest {
        val s = repo.ensureSpot()
        val obsDao = FakeManualObservationDao()
        val fitDao = FakeManualTideFitDao()
        repo = buildRepo(testScheduler, manualObservationDao = obsDao, manualTideFitDao = fitDao)

        repo.saveManualTideFit(s.id, TideCalibrator.ManualTideFit(0, 0.0, 1.0, false, "再记 2 条即可校潮"))
        val notAdopted = assertNotNull(repo.manualTideFit(s.id), "adopted=false 也登记（D12 对照需要）")
        assertEquals(false, notAdopted.adopted)
        assertEquals(0, notAdopted.lagHours)

        // REPLACE 覆盖式（spotId PK）：再次 save → 覆盖
        repo.saveManualTideFit(s.id, TideCalibrator.ManualTideFit(1, 0.0, 1.0, true, null))
        val adopted = assertNotNull(repo.manualTideFit(s.id))
        assertEquals(1, adopted.lagHours)
        assertEquals(true, adopted.adopted)
        assertNull(adopted.message, "message 不持久化（读回恒 null）")
    }

    @Test
    fun `M6_校潮应用闭环_评分序列用校正_marine保持原始`() = runTest {
        // preset 解析走 domain 对象 resolvePreset(spot)：spot.waterBody 必须 OCEAN（仅写 DB 不改对象会落 FRESH_LURE 剔除 tide 因子）
        val s = repo.ensureSpot().copy(waterBody = WaterBodyType.OCEAN)
        repo.updateSpotWaterBody(s.id, WaterBodyType.OCEAN, null, null) // 海洋点 → SEA_SHORE（tide 权重生效）
        // 基线（无 fit）：正常刷新落库
        repo.refreshSpotDetail(s)
        val baseline = scoreDao.rows.filter { it.spotId == s.id }.map { it.hourEpoch to it.score }.toMap()

        // 现场校潮：观测 → fitManual（±6h 匹配窗，lag 中位数）→ saveManualTideFit → force 刷新
        val obsTimes = listOf(MockFixtures.BASE_EPOCH_SEC + 3 * 3600L, MockFixtures.BASE_EPOCH_SEC + 5 * 3600L)
        val modelExtremes = listOf(MockFixtures.BASE_EPOCH_SEC + 2 * 3600L, MockFixtures.BASE_EPOCH_SEC + 4 * 3600L)
        val fit = TideCalibrator.fitManual(obsTimes, modelExtremes)
        assertEquals(true, fit.adopted, "夹具构造前提：两条 ±6h 内匹配观测 → adopted")
        // 注入 manual DAO 的 repo（防御性 IllegalStateException 是正确生产语义，测试必须真注入）
        val obsDao = FakeManualObservationDao()
        val fitDao = FakeManualTideFitDao()
        repo = buildRepo(testScheduler, manualObservationDao = obsDao, manualTideFitDao = fitDao)
        repo.saveManualTideFit(s.id, fit)
        val refreshed = repo.refreshSpotDetail(s, force = true)

        // tideCorrection 摘要透出 + marine 原始未校正（UI 对照基准）
        val tc = assertNotNull(refreshed.tideCorrection, "海洋点命中 adopted fit → 摘要非 null")
        assertEquals(fit.lagHours, tc.lagHours)
        assertEquals(fit.offsetM, tc.offsetM, 1e-12)
        assertEquals(fit.gain, tc.gain, 1e-12)
        assertEquals(true, tc.adopted)
        assertEquals(
            0.17,
            refreshed.marine.first { it.timeEpochSec == MockFixtures.BASE_EPOCH_SEC + 3 * 3600L }.seaLevelHeightMslM,
            "SpotDetailResult.marine = 原始未校正序列（fixture 首时值 0.17，禁校正泄漏）",
        )

        // 评分序列为校正后：落库分数与基线不同（校正改变 dh/amp → tide 因子路径）
        val after = scoreDao.rows.filter { it.spotId == s.id }.map { it.hourEpoch to it.score }.toMap()
        assertTrue(after != baseline, "adopted fit 后评分序列必须变化（校正进评分与 dh 派生管线）")

        // 纯函数恒等：applyManualTideCorrection ≡ 手工 TideCalibrator.apply（等价 Calibration）
        val marine = refreshed.marine.sortedBy { it.timeEpochSec }
        val corrected = applyManualTideCorrection(marine, fit)
        val manual = TideCalibrator.apply(
            marine.map { it.seaLevelHeightMslM },
            TideCalibrator.Calibration(
                fit.lagHours, fit.gain, fit.offsetM, r = 1.0, alignedSamples = 0,
                rawRmseM = 0.0, correctedRmseM = 0.0, adopted = true, rejectReason = null, holdout = null,
            ),
        )
        assertEquals(manual, corrected.map { it.seaLevelHeightMslM }, "校正序列与手工 apply 恒等")
        // 公式抽查：M'(i)=gain·M(i−lag)+offset（fixture M(i)=0.17+i·0.05；lag=1, gain=1, offset=0）
        if (fit.lagHours == 1) {
            assertEquals(0.17, corrected[1].seaLevelHeightMslM!!, 1e-9, "M'(1)=M(0)+offset")
            assertNull(corrected[0].seaLevelHeightMslM, "窗外（i−lag 越界）→ null（公式语义）")
        }
    }

    @Test
    fun `M6_无fit_tideCorrection为null_行为向后兼容`() = runTest {
        val s = repo.ensureSpot()
        repo.updateSpotWaterBody(s.id, WaterBodyType.OCEAN, null, null)
        val refreshed = repo.refreshSpotDetail(s)
        assertNull(refreshed.tideCorrection, "无 fit → null（未应用）")
        assertTrue(scoreDao.rows.filter { it.spotId == s.id }.isNotEmpty(), "评分管线照常落库")
        assertEquals(0.17, refreshed.marine.first().seaLevelHeightMslM, "无 fit → marine 原样（改造前行为）")
        // adopted=false 的 fit 同样不应用、摘要 null
        val fitDao = FakeManualTideFitDao()
        val obsDao = FakeManualObservationDao()
        repo = buildRepo(testScheduler, manualObservationDao = obsDao, manualTideFitDao = fitDao)
        repo.saveManualTideFit(s.id, TideCalibrator.ManualTideFit(2, 0.3, 1.0, false, "未采纳"))
        val again = repo.refreshSpotDetail(s, force = true)
        assertNull(again.tideCorrection, "未 adopted → null=未应用")
    }

    @Test
    fun `M6_D07_patch合并_编辑名称不冲掉tzOffsetSec`() = runTest {
        val s = repo.ensureSpot()
        repo.updateSpotWaterBody(s.id, WaterBodyType.NEARSHORE, 12.0, 7 * 3600)
        repo.upsertFavorite(s.copy(name = "改名不冲派生列"))
        val back = assertNotNull(spotDao.rows[s.id], "编辑后行仍在")
        assertEquals(7 * 3600, back.tzOffsetSec, "D07 patch 合并清单尾增 tzOffsetSec：编辑不冲掉")
        assertEquals("NEARSHORE", back.waterBodyType)
        assertEquals(12.0, back.elevation)
        assertEquals(7 * 3600, repo.favorites().first { it.id == s.id }.tzOffsetSec, "toDomain 映射回读一致")
    }

    @Test
    fun `M6_D13_评分切注入scoringDispatcher_专用线程可观测`() = runTest {
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "tidespot-scoring") }
        var scoringThread: Thread? = null
        val dispatcher = object : CoroutineDispatcher() {
            override fun isDispatchNeeded(context: CoroutineContext): Boolean = true
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                executor.execute {
                    scoringThread = Thread.currentThread()
                    block.run()
                }
            }
        }
        repo = buildRepo(testScheduler, scoringDispatcher = dispatcher)
        val s = repo.ensureSpot()
        try {
            repo.refreshSpotDetail(s)
            assertEquals("tidespot-scoring", scoringThread?.name, "评分计算在注入的 scoringDispatcher 线程执行（D13）")
            assertTrue(scoreDao.rows.isNotEmpty(), "评分管线完成且落库")
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `M6_D13_评分期取消_CancellationException重抛不被吞`() = runTest {
        val dispatcher = object : CoroutineDispatcher() {
            override fun isDispatchNeeded(context: CoroutineContext): Boolean = true
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                // 评分块执行前取消宿主协程：withContext（ATOMIC）仍会跑完纯 CPU 块，
                // 但评分后的 ensureActive 检查点必须以 CancellationException 逃逸——
                // 任何 catch 不得吞为降级/空结果（D13 锁）
                context[Job]?.cancel()
                block.run()
            }
        }
        repo = buildRepo(testScheduler, scoringDispatcher = dispatcher)
        val s = repo.ensureSpot()
        val result = runCatching { repo.refreshSpotDetail(s) }
        val err = result.exceptionOrNull()
        assertTrue(
            err is CancellationException,
            "评分期取消必须以 CancellationException 逃逸（实际：$err；成功返回=${result.isSuccess}）",
        )
        assertTrue(scoreDao.rows.isEmpty(), "已取消协程的评分不得静默落库")
    }

    @Test
    fun `M6_topWindows读侧_veto_codes解析_雷暴行按机器码排除`() = runTest {
        val s = repo.ensureSpot()
        // 直接预置 hourly_score 行（读侧路径，不经网络）：240h 未来窗，第 100 小时雷暴
        val nextHour = vNow / 1000 - (vNow / 1000) % 3600 + 3600
        val rows = (0 until 240).map { h ->
            com.tidespot.core.data.local.HourlyScoreEntity(
                spotId = s.id,
                hourEpoch = nextHour + h * 3600L,
                hourLocal = null,
                score = if (h == 100) 90.0 else 80.0,
                band = "很适合",
                vetoReason = if (h == 100) "[\"雷暴（安全否决）\"]" else null,
                factorsJson = "[]",
                algoVersion = FishScoreCalculator.ALGO_VERSION,
                vetoCodes = if (h == 100) "[\"THUNDERSTORM\"]" else "[]",
            )
        }
        scoreDao.insertAll(rows)
        val out = repo.topWindows(s)
        assertTrue(out.windows.isNotEmpty(), "240 行全未来 → 正常出窗")
        assertTrue(
            out.windows.all { it.peakEpochSec != nextHour + 100 * 3600L },
            "读侧重建：雷暴行按 veto_codes 机器码排除（finder 只认 codes）",
        )
        // 对照：同 reason 文案但 codes='[]'（v3 旧行 DEFAULT 语义）→ 不排除
        scoreDao.deleteForSpot(s.id)
        scoreDao.insertAll(rows.map { it.copy(vetoCodes = "[]") })
        val outOld = repo.topWindows(s)
        assertTrue(
            outOld.windows.any { it.peakEpochSec == nextHour + 100 * 3600L },
            "旧行无机器码 → 不排除（空数组语义与 vetoReason NULL 对称，文案不再参与筛选）",
        )
    }
}
