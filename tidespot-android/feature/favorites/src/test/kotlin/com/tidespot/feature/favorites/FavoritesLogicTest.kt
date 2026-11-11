package com.tidespot.feature.favorites

import com.tidespot.core.domain.model.BestWindow
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.feature.favorites.favorites.FavoritesSortLogic
import com.tidespot.feature.favorites.favorites.FavoritesSummaryFormatter
import com.tidespot.feature.favorites.favorites.FavoritesSpotSummary
import com.tidespot.feature.favorites.favorites.SortOrder
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.tidespot.core.ui.zone.SpotZones
import com.tidespot.feature.favorites.favorites.FavoritesSaveRules
import com.tidespot.feature.favorites.favorites.FavoritesZones

/**
 * F-2.4 排序 + F-2.5 摘要纯函数锁（JVM；UI 逻辑先抽纯函数再进 Composable——M4 D01/D02 教训）。
 */
class FavoritesLogicTest {

    private val zone: ZoneOffset = ZoneOffset.ofHours(8)

    /** 2026-11-06 17:00 +08:00 的 epoch 秒（固定基准，格式化断言可精确预期）。 */
    private fun epoch(h: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 11, 6, h, minute).toInstant(ZoneOffset.ofHours(8)).epochSecond

    private fun spot(
        id: Long,
        name: String,
        lastViewedAt: Long = 0,
    ) = FavoriteSpot(id = id, name = name, lat = 36.06, lon = 120.38, lastViewedAtEpochSec = lastViewedAt)

    private fun window(peakScore: Int, tier: String = "爆护窗口", peakEpochSec: Long = epoch(17)) = BestWindow(
        startEpochSec = peakEpochSec - 3600,
        peakEpochSec = peakEpochSec,
        durationHours = 3,
        peakScore = peakScore,
        tier = tier,
        topFactors = listOf("solunar", "tide"),
        warnings = emptyList(),
        rank = 80.0,
    )

    // ──────────────────────────── FavoritesSortLogic（F-2.4） ────────────────────────────

    @Test
    fun `名称序_按名称升序_并列保持原序稳定`() {
        val a = spot(1, "青岛外海")
        val b = spot(2, "大阪湾")
        val c = spot(3, "青岛外海")   // 与 a 同名 → 稳定排序保持输入序
        val out = FavoritesSortLogic.sort(listOf(a, b, c), SortOrder.NAME)
        assertEquals(listOf(2L, 1L, 3L), out.map { it.id })
    }

    @Test
    fun `最近查看序_降序且从未查看排最后`() {
        val never1 = spot(1, "从未1", lastViewedAt = 0)
        val never2 = spot(2, "从未2", lastViewedAt = 0)
        val old = spot(3, "较早", lastViewedAt = epoch(8))
        val recent = spot(4, "最近", lastViewedAt = epoch(20))
        val out = FavoritesSortLogic.sort(listOf(never1, recent, never2, old), SortOrder.LAST_VIEWED)
        assertEquals(listOf(4L, 3L, 1L, 2L), out.map { it.id }, "最近查看在前，从未查看（0）排最后且保持原序")
    }

    @Test
    fun `分数序_降序且未加载排最后`() {
        val unloaded1 = spot(1, "未加载1")
        val unloaded2 = spot(2, "未加载2")
        val low = spot(3, "低分")
        val high = spot(4, "高分")
        val scores = mapOf(3L to 62, 4L to 92)
        val out = FavoritesSortLogic.sort(listOf(unloaded1, high, unloaded2, low), SortOrder.SCORE, scores)
        assertEquals(listOf(4L, 3L, 1L, 2L), out.map { it.id }, "分数降序；无分数（未加载）排最后且保持原序")
    }

    @Test
    fun `标签解析_中英文分隔符切分_去空去重保序`() {
        val tags = FavoritesSortLogic.parseTags(" 路亚，标点, 深水 、路亚,")
        assertEquals(listOf("路亚", "标点", "深水"), tags)
        assertTrue(FavoritesSortLogic.parseTags("   ").isEmpty(), "空白输入 → 空列表（非空 List 契约）")
    }

    @Test
    fun `场景预设选项与_SRS_三条目一致`() {
        assertEquals(listOf("淡水路亚", "海水岸边", "淡水台钓"), FavoritesSortLogic.PRESET_OPTIONS)
    }

    // ──────────────────────────── FavoritesSummaryFormatter（F-2.5） ────────────────────────────

    @Test
    fun `有窗口摘要行_时刻分数档位`() {
        val line = FavoritesSummaryFormatter.windowLine(window(92), zone)
        assertEquals("11月6日 17:00 · 92 分 · 爆护窗口", line)
    }

    @Test
    fun `未加载_validHours为0`() {
        val line = FavoritesSummaryFormatter.line(FavoritesSpotSummary(validHoursPresent = 0), zone)
        assertEquals("未加载", line)
    }

    @Test
    fun `有窗口的完整摘要`() {
        val line = FavoritesSummaryFormatter.line(
            FavoritesSpotSummary(validHoursPresent = 240, window = window(88)), zone,
        )
        assertEquals("11月6日 17:00 · 88 分 · 爆护窗口", line)
    }

    @Test
    fun `无窗口有兜底_文案附加最高分时刻`() {
        val line = FavoritesSummaryFormatter.line(
            FavoritesSpotSummary(
                validHoursPresent = 240,
                fallbackMessage = "未来 7 天无 ≥60 分窗口，最高 55 分",
                fallbackBestEpochSec = epoch(13, 30),
            ),
            zone,
        )
        assertEquals("未来 7 天无 ≥60 分窗口，最高 55 分（11月6日 13:30）", line, "兜底文案必须含时刻（SRS §6.6 边界）")
    }

    @Test
    fun `无窗口无兜底_回退未加载`() {
        val line = FavoritesSummaryFormatter.line(
            FavoritesSpotSummary(validHoursPresent = 10, fallbackMessage = null, fallbackBestEpochSec = null), zone,
        )
        assertEquals("未加载", line)
    }

    @Test
    fun `全过期禁用文案_validHours为0仍显示文案而非未加载`() {
        // CHG-15 偏差①：库内数据全过期 → topWindows 返回禁用文案（validHours=0）→ 列表须显示文案而非「未加载」
        val line = FavoritesSummaryFormatter.line(
            FavoritesSpotSummary(
                validHoursPresent = 0,
                fallbackMessage = "有效数据不足 24 小时，最佳窗口功能暂不可用",
                fallbackBestEpochSec = null,
            ),
            zone,
        )
        assertEquals("有效数据不足 24 小时，最佳窗口功能暂不可用", line)
    }

    @Test
    fun `跨日时刻_日期部分正确`() {
        val nextDay = FavoritesSummaryFormatter.time(epoch(0, 30) + 24 * 3600, zone)
        assertEquals("11月7日 00:30", nextDay)
    }

    // ── OPT-V11-06（P1）：相对时间摘要 ──

    @Test
    fun `相对时间_同日为今天`() {
        val line = FavoritesSummaryFormatter.line(
            FavoritesSpotSummary(validHoursPresent = 240, window = window(92)),
            zone,
            nowEpochSec = epoch(12),
        )
        assertEquals("今天 17:00 · 92 分 · 爆护窗口", line)
    }

    @Test
    fun `相对时间_次日为明天`() {
        val line = FavoritesSummaryFormatter.windowLine(window(92), zone, nowEpochSec = epoch(17) - 24 * 3600)
        assertEquals("明天 17:00 · 92 分 · 爆护窗口", line)
    }

    @Test
    fun `相对时间_未注入now回落绝对格式_零行为漂移`() {
        val line = FavoritesSummaryFormatter.line(
            FavoritesSpotSummary(validHoursPresent = 240, window = window(92)),
            zone,
        )
        assertEquals("11月6日 17:00 · 92 分 · 爆护窗口", line)
    }
}

// ──────────────────── OPT-M5-03 / OPT-M5-07（CHG-16⑯⑰） ────────────────────

/** 跨点时区回贴 + 新建坐标防御纯函数锁。 */
class FavoritesZonesTest {

    private fun spot(id: Long, name: String, tz: Int?) =
        FavoriteSpot(id = id, name = name, lat = 34.6, lon = 135.4, tzOffsetSec = tz)

    private fun window(rank: Double, peakEpochSec: Long) = BestWindow(
        startEpochSec = peakEpochSec - 3600, peakEpochSec = peakEpochSec, durationHours = 3,
        peakScore = 90, tier = "爆护窗口", topFactors = listOf("tide"), warnings = emptyList(), rank = rank,
    )

    /** 固定基准 epoch 秒（+08:00 的 2026-11-06 h:00），与 FavoritesLogicTest 同基准。 */
    private fun epoch(h: Int): Long =
        java.time.LocalDateTime.of(2026, 11, 6, h, 0).toInstant(java.time.ZoneOffset.ofHours(8)).epochSecond

    @Test
    fun `OPT-M5-03_JST与UTC同列表_时刻各归其区`() {
        val jst = spot(1, "大阪湾", 32400)
        val utc = spot(2, "雷克雅未克", 0)
        // 同一 epoch（+08:00 的 17:00）：JST 显示 18:00、UTC 显示 09:00
        assertEquals("11月6日 18:00", FavoritesSummaryFormatter.time(epoch(17), SpotZones.zoneForSpot(jst)))
        assertEquals("11月6日 09:00", FavoritesSummaryFormatter.time(epoch(17), SpotZones.zoneForSpot(utc)))
    }

    @Test
    fun `attachZones_不同名点_各归其点`() {
        val a = spot(1, "大阪湾", 32400)
        val b = spot(2, "雷克雅未克", 0)
        val w1 = window(80.0, epoch(17))
        val w2 = window(70.0, epoch(17))
        val ranked = listOf("大阪湾" to w1, "雷克雅未克" to w2)
        val entries = FavoritesZones.attachZones(ranked, listOf(a to listOf(w1), b to listOf(w2)))
        assertEquals(32400, entries[0].spot?.tzOffsetSec)
        assertEquals(0, entries[1].spot?.tzOffsetSec)
        assertEquals(false, entries[0].usesDeviceZone)
        assertEquals(false, entries[1].usesDeviceZone)
    }

    @Test
    fun `attachZones_同名多点_按输入序与窗口数精确回贴`() {
        val a1 = spot(1, "同名湾", 32400)
        val a2 = spot(2, "同名湾", 0)
        val w1 = window(80.0, epoch(17))
        val w2 = window(70.0, epoch(16))
        val w3 = window(60.0, epoch(15))
        val ranked = listOf("同名湾" to w1, "同名湾" to w2, "同名湾" to w3)
        val entries = FavoritesZones.attachZones(ranked, listOf(a1 to listOf(w1, w2), a2 to listOf(w3)))
        assertEquals(32400, entries[0].spot?.tzOffsetSec)
        assertEquals(32400, entries[1].spot?.tzOffsetSec)
        assertEquals(0, entries[2].spot?.tzOffsetSec)
    }

    @Test
    fun `attachZones_时区未回写_usesDeviceZone为真`() {
        val legacy = spot(1, "旧点", null)
        val w = window(80.0, epoch(17))
        val entries = FavoritesZones.attachZones(listOf("旧点" to w), listOf(legacy to listOf(w)))
        assertTrue(entries[0].usesDeviceZone)
        assertEquals(java.time.ZoneId.systemDefault(), entries[0].zone)
    }

    @Test
    fun `attachZones_防御分支_spot缺失_不虚构时区`() {
        val w = window(80.0, epoch(17))
        val entries = FavoritesZones.attachZones(listOf("未知点" to w), emptyList())
        assertNull(entries[0].spot)
        assertTrue(entries[0].usesDeviceZone)
    }
}

/** OPT-M5-07 新建收藏坐标防御锁。 */
class FavoritesSaveRulesTest {

    @Test
    fun `新建_携带合法坐标_放行`() {
        assertTrue(FavoritesSaveRules.canCreate(29.6, 119.0))
        assertTrue(FavoritesSaveRules.canCreate(0.0, 119.0))  // 纬度 0 合法
        assertTrue(FavoritesSaveRules.canCreate(29.6, 0.0))   // 经度 0 合法
    }

    @Test
    fun `新建_坐标缺失或全零_拒绝`() {
        assertTrue(!FavoritesSaveRules.canCreate(null, 119.0))
        assertTrue(!FavoritesSaveRules.canCreate(29.6, null))
        assertTrue(!FavoritesSaveRules.canCreate(null, null))
        assertTrue(!FavoritesSaveRules.canCreate(0.0, 0.0))   // 占位 (0,0) 拒绝
    }

    @Test
    fun `拒绝提示_非空文案`() {
        assertTrue(FavoritesSaveRules.rejectMessage().isNotBlank())
    }
}

