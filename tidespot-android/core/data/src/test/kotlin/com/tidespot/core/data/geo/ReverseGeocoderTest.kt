package com.tidespot.core.data.geo

import com.tidespot.core.data.mock.FakeReverseCacheDao
import com.tidespot.core.data.remote.TideSpotUserAgent
import com.tidespot.core.domain.model.ReverseGeoSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import kotlin.test.assertTrue

/**
 * 逆地理链单测（SRS §5.8 / 附录 B7b 黄金值 / CONTRACT M3 频控红线）。
 * 网络侧 MockWebServer；时钟 = runTest 虚拟时钟（TTL 30d 与 2s 令牌桶共用同一注入 clock）。
 */
class ReverseGeocoderTest {

    private companion object {
        /** B7b：RG-A zh 湖心(29.55,118.95) 实测字段。 */
        val B7B_LAKE_CENTER_A = """
            {"countryName":"中华人民共和国","countryCode":"CN","principalSubdivision":"浙江省",
             "city":"杭州市","locality":"淳安县","plusCode":"7PXWHX22+22"}
        """.trimIndent()

        /** B7b：RG-A zh 青岛外海(36.20,120.70) —— 繁简混排实测（省繁市简区繁）。 */
        val B7B_QINGDAO_A = """
            {"countryName":"中华人民共和国","countryCode":"CN","principalSubdivision":"山東省",
             "city":"青島市","locality":"嶗山區","plusCode":"7PXXJ2XX+XX"}
        """.trimIndent()

        /** B7b：RG-B 岸边(29.605,119.038) layer=locality 实测。 */
        val B7B_QILONG_B = """
            {"features":[{"type":"Feature","properties":{"osm_id":9827162,"osm_type":"N",
             "name":"骑龙巷街区","type":"locality","city":"杭州市","state":"浙江省",
             "country":"中华人民共和国"}}]}
        """.trimIndent()

        /** B7b：RG-B 湖心/远海 —— 200 + features:[]（无聚落正常态，禁止当错误重试）。 */
        val PHOTON_EMPTY = """{"features":[]}"""
    }

    private lateinit var server: MockWebServer
    private lateinit var dao: FakeReverseCacheDao
    private val recorded = mutableListOf<RecordedRequest>()

    private var aResponder: (RecordedRequest) -> MockResponse = { ok(B7B_LAKE_CENTER_A) }
    private var bResponder: (RecordedRequest) -> MockResponse = { ok(PHOTON_EMPTY) }

    /** §5.8 坑：RG-A 实测 307 → 备用域，开关置 true 后 A 端点回 307。 */
    private var aRedirect = false
    private var aCount = 0
    private var bCount = 0

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recorded += request
                return when (request.requestUrl!!.encodedPath) {
                    "/data/reverse-geocode-client" -> {
                        aCount++
                        if (aRedirect) {
                            MockResponse().setResponseCode(307)
                                .setHeader("Location", server.url("/bdc-backup").toString())
                        } else {
                            aResponder(request)
                        }
                    }
                    // 307 目标（备用域），成功即证明 followRedirects 生效且未被计为重试
                    "/bdc-backup" -> ok(B7B_LAKE_CENTER_A)
                    "/reverse" -> {
                        bCount++
                        bResponder(request)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        dao = FakeReverseCacheDao()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun TestScope.newGeocoder(): ReverseGeocoder = ReverseGeocoder(
        reverseCacheDao = dao,
        bigDataCloudBaseUrl = server.url("/").toString(),
        photonBaseUrl = server.url("/").toString(),
        clock = { testScheduler.currentTime },
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    // ────────────────────────────── B7b 黄金值与参数/UA 防回归 ──────────────────────────────

    @Test
    fun `B7b黄金_湖心_RG_A_locality优先_plusCode透出_简繁不混排`() = runTest {
        val place = newGeocoder().resolve(29.55, 118.95)

        assertEquals("淳安县", place.displayName, "显示名 = RG-A locality（§5.8 优先级）")
        assertEquals(ReverseGeoSource.RG_A, place.source)
        assertEquals("7PXWHX22+22", place.plusCode)
        assertEquals("杭州市 · 浙江省", place.subLabel, "后缀附 市/省")
        assertEquals("淳安县", place.locality)
        assertEquals("杭州市", place.city)
        assertEquals("浙江省", place.admin1)
        assertNull(place.waterName, "RG-C 不接入，waterName 恒为 null（CHG-06）")

        // RG-A 参数防回归（§5.8）
        val aReq = recorded.first { it.requestUrl!!.encodedPath == "/data/reverse-geocode-client" }
        assertEquals("zh", aReq.requestUrl!!.queryParameter("localityLanguage"))
        assertEquals("29.55", aReq.requestUrl!!.queryParameter("latitude"))
        assertEquals("118.95", aReq.requestUrl!!.queryParameter("longitude"))
        // RG-B 参数防回归（B7b）：limit=1、layer=locality、禁止 lang
        val bReq = recorded.first { it.requestUrl!!.encodedPath == "/reverse" }
        assertEquals("1", bReq.requestUrl!!.queryParameter("limit"))
        assertEquals("locality", bReq.requestUrl!!.queryParameter("layer"))
        assertNull(bReq.requestUrl!!.queryParameter("lang"), "禁止 lang 参数（B7b：lang=zh → 400）")
        // UA（§12 / CONTRACT 冻结点 5）
        assertEquals(TideSpotUserAgent.VALUE, aReq.getHeader("User-Agent"))
        assertEquals(TideSpotUserAgent.VALUE, bReq.getHeader("User-Agent"))
    }

    @Test
    fun `B7b黄金_青岛繁简混排_T2S归一后显示`() = runTest {
        aResponder = { ok(B7B_QINGDAO_A) }
        val place = newGeocoder().resolve(36.20, 120.70)

        assertEquals("崂山区", place.displayName, "嶗山區 → 崂山区")
        assertEquals("青岛市 · 山东省", place.subLabel, "青島市/山東省 → 归一")
        assertEquals(ReverseGeoSource.RG_A, place.source)
    }

    @Test
    fun `RG_B聚落名优先_后缀附RG_A市省`() = runTest {
        bResponder = { ok(B7B_QILONG_B) }
        val place = newGeocoder().resolve(29.605, 119.038)

        assertEquals("骑龙巷街区", place.displayName, "RG-B 聚落名 > RG-A locality")
        assertEquals(ReverseGeoSource.RG_B, place.source)
        assertEquals("杭州市 · 浙江省", place.subLabel)
        assertEquals("7PXWHX22+22", place.plusCode, "plusCode 仍取 RG-A 实测值")
    }

    // ────────────────────────────── 实测坑防御 ──────────────────────────────

    @Test
    fun `RG_B空features是正常态_不重试不报错_降级用RG_A`() = runTest {
        val place = newGeocoder().resolve(29.55, 118.95)

        assertEquals(1, bCount, "features:[] 不得重试（§5.8 坑 3，重试会打爆频控）")
        assertEquals(1, aCount)
        assertEquals("淳安县", place.displayName, "RG-B 无聚落 → 正常降级用 RG-A")
        assertEquals(ReverseGeoSource.RG_A, place.source)
    }

    @Test
    fun `RG_A_307重定向跟随到备用域_不计为重试`() = runTest {
        aRedirect = true
        val place = newGeocoder().resolve(29.55, 118.95)

        assertEquals("淳安县", place.displayName, "307 → 备用域 200，解析成功")
        assertEquals(ReverseGeoSource.RG_A, place.source)
        assertEquals(1, aCount, "跳转由 HTTP 客户端完成，A 端点只命中 1 次")
        // resolve 并行发 A ∥ B：A 原始 + 307 备用域 + RG-B 各 1 次（B 正常态也算请求）
        assertEquals(3, recorded.size, "A 原始 + 备用域 + RG-B 各 1；307 跳转不计为重试")
        assertEquals(1, recorded.count { it.requestUrl!!.encodedPath == "/bdc-backup" }, "备用域恰好命中 1 次")
    }

    // ────────────────────────────── 缓存与频控 ──────────────────────────────

    @Test
    fun `缓存命中0网络请求_TTL过期后重新拉取`() = runTest {
        val geocoder = newGeocoder()

        val first = geocoder.resolve(29.55, 118.95)
        assertEquals(1, aCount)
        assertEquals(1, bCount)

        val second = geocoder.resolve(29.55, 118.95)
        assertEquals(ReverseGeoSource.CACHE, second.source, "30 d TTL 内 → 直接回缓存")
        assertEquals(1, aCount, "缓存命中 → 0 网络请求")
        assertEquals(1, bCount)
        assertEquals(first.displayName, second.displayName)

        advanceTimeBy(ReverseGeocoder.TTL_MILLIS + 1)
        val third = geocoder.resolve(29.55, 118.95)
        assertEquals(ReverseGeoSource.RG_A, third.source, "TTL 过期 → 重新拉取")
        assertEquals(2, aCount)
        assertEquals(2, bCount)
    }

    @Test
    fun `2s节流_连续resolve等待令牌而非丢弃_最终都有名称`() = runTest { // CHG-09④：tryAcquire → acquire
        val geocoder = newGeocoder()

        val p1 = geocoder.resolve(29.55, 118.95) // 令牌充足：RG-A + RG-B 各 1（t≈0）
        val p2 = geocoder.resolve(30.0, 119.0)   // 令牌耗尽 → acquire 挂起等待 ≈2s（不再跳级 RG-D）
        val p3 = geocoder.resolve(31.0, 120.0)   // 再等待一个补充周期 ≈2s

        assertEquals(ReverseGeoSource.RG_A, p1.source)
        assertEquals(ReverseGeoSource.RG_A, p2.source, "节流期等待令牌 → 照常请求（不再丢弃）")
        assertEquals(ReverseGeoSource.RG_A, p3.source)
        assertTrue(p2.displayName.isNotBlank() && p3.displayName.isNotBlank(), "信息条永不空白")
        assertEquals(3, aCount, "连续 3 次 resolve = RG-A 各 1 次（桶速 ≤1 req/2s 不变）")
        assertEquals(3, bCount)
        assertTrue(testScheduler.currentTime >= 4_000, "两次等待各 ≈2s（虚拟时钟）")
        // 等待语义下同样不超预算：每次 resolve 的令牌等待 ≤2s < 3s 预算
    }

    @Test
    fun `等待预算超时_该级跳过走降级_不发该级请求`() = runTest { // CHG-09④：3s 预算上限
        // 注入 1ms 预算：令牌需等待 ≈2s > 预算 → 两级均按超时跳过 → RG-D 兜底，且不打网络
        val geocoder = ReverseGeocoder(
            reverseCacheDao = dao,
            bigDataCloudBaseUrl = server.url("/").toString(),
            photonBaseUrl = server.url("/").toString(),
            clock = { testScheduler.currentTime },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            waitBudgetMillis = 1L,
        )
        geocoder.resolve(29.55, 118.95) // 消耗首轮令牌
        val aBefore = aCount; val bBefore = bCount; val upsertsBefore = dao.upserts

        val p2 = geocoder.resolve(30.0, 119.0) // 等待 > 预算 → 跳级

        assertEquals(ReverseGeoSource.RG_D, p2.source, "等待超预算 → 该级跳过，落 RG-D 兜底")
        assertTrue(p2.displayName.isNotBlank())
        assertEquals(aBefore, aCount, "超时该级不打网络")
        assertEquals(bBefore, bCount)
        assertEquals(upsertsBefore, dao.upserts, "RG-D 不落缓存")
    }

    @Test
    fun `节流等待可取消_取消传播不吞异常`() = runTest { // CHG-09④：协程取消自然传播
        val geocoder = newGeocoder()
        geocoder.resolve(29.55, 118.95) // 用掉令牌 → 下次 resolve 会等待
        val aBefore = aCount
        val job = launch { geocoder.resolve(30.0, 119.0) }
        advanceTimeBy(500)
        job.cancel() // 等待中取消 → CancellationException 传播，resolve 不吞不降级
        advanceTimeBy(5_000)
        assertEquals(aBefore, aCount, "取消后未发出网络请求")
    }

    // ────────────────────────────── 全链失败 → RG-D ──────────────────────────────

    @Test
    fun `全链失败落RG_D_displayName非空白_RG_A最多重试1次`() = runTest {
        aResponder = { MockResponse().setResponseCode(500).setBody("boom") }
        bResponder = { MockResponse().setResponseCode(500).setBody("boom") }

        val place = newGeocoder().resolve(29.605, 119.038)

        assertEquals(ReverseGeoSource.RG_D, place.source)
        assertEquals("7PXXJ24Q+26", place.displayName, "RG-D = 本地 Plus Code（永不失败）")
        assertEquals("7PXXJ24Q+26", place.plusCode)
        assertEquals("29.6050, 119.0380", place.subLabel, "格式化坐标兜底")
        assertTrue(place.displayName.isNotBlank(), "信息条绝不空白（§5.8 第 5 步）")
        assertEquals(2, aCount, "RG-A 失败最多重试 1 次（共 2 次尝试，§12 红线）")
        assertEquals(1, bCount, "RG-B 单次尝试，绝不重试")
        assertEquals(0, dao.upserts, "CHG-09④：RG-D 兜底结果不落 30d 缓存（信息条照常非空白）")

        // 全链失败后再次 resolve 会重打网络（不再被坏缓存钉 30d；受令牌桶节流保护）
        val again = newGeocoder().resolve(29.605, 119.038)
        assertEquals(ReverseGeoSource.RG_D, again.source, "无缓存命中 → 再次走全链，仍 RG-D 兜底")
        assertEquals(4, aCount, "再次 resolve → RG-A 重打（每次 resolve 2 次尝试）")
        assertEquals(2, bCount)
        assertEquals(0, dao.upserts, "重复失败仍不入库")
    }

    @Test
    fun `RG_A返回但名称字段全空_仍落RG_D`() = runTest {
        aResponder = { ok("""{"countryName":"中华人民共和国","countryCode":"CN"}""") }
        val place = newGeocoder().resolve(29.55, 118.95)

        assertEquals(ReverseGeoSource.RG_D, place.source)
        assertEquals("7PXWHX22+22", place.displayName)
        assertTrue(place.subLabel!!.isNotBlank())
    }
}
