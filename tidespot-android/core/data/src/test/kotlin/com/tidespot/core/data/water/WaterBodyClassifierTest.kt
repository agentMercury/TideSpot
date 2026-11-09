package com.tidespot.core.data.water

import com.tidespot.core.data.local.CacheKeys
import com.tidespot.core.data.mock.FakeWaterBodyCacheDao
import com.tidespot.core.domain.model.WaterBodyType
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 水体类型判定（SRS §6.9 / CHG-06 / CHG-09②）：decide 判定表全覆盖 + classify 缓存/降级路径。
 * CHG-09②：删除低海拔捷径；探针失败/未注入 → UNKNOWN（不落 LAND）+ 24h 短 TTL。
 */
class WaterBodyClassifierTest {

    private class FakeProbe(var result: Boolean?) : InlandWaterProbe {
        var calls = 0
        override suspend fun hasLakeValue(lat: Double, lon: Double): Boolean? {
            calls++
            return result
        }
    }

    private fun TestScope.newClassifier(
        probe: InlandWaterProbe?,
        vNow: Long,
        dao: FakeWaterBodyCacheDao,
        onWarn: (String) -> Unit = {},
    ): WaterBodyClassifier = WaterBodyClassifier(
        waterBodyCacheDao = dao,
        probe = probe,
        clock = { vNow },
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        onWarn = onWarn,
    )

    // ────────────────────────────── decide 纯决策表 ──────────────────────────────

    @Test
    fun `decide判定表_全覆盖`() {
        val D = WaterBodyClassifier.Companion::decide
        // 海洋有值 → 近岸（v1 无离岸距离判据，统一 NEARSHORE）
        assertEquals(WaterBodyType.NEARSHORE, D(true, 285.0, null))
        assertEquals(WaterBodyType.NEARSHORE, D(true, null, false))
        assertEquals(WaterBodyType.NEARSHORE, D(true, null, true))
        // 探针有值 → 内陆水体；探针无值 → 陆地（CHG-09②：低海拔不再有捷径）
        assertEquals(WaterBodyType.INLAND_WATER, D(false, 285.0, true))
        assertEquals(WaterBodyType.LAND, D(false, 285.0, false))
        // 低海拔（含 3m 边界）：同样走探针结果判定（捷径已删，SRS §6.9 无此规则）
        assertEquals(WaterBodyType.LAND, D(false, 1.0, false), "低海拔陆地不得误判为湖")
        assertEquals(WaterBodyType.LAND, D(false, 3.0, false))
        assertEquals(WaterBodyType.INLAND_WATER, D(false, 1.0, true))
        assertEquals(WaterBodyType.LAND, D(false, 3.1, false))
        // 探针失败（null）→ §6.9 第 4 级「未知」= UNKNOWN（CHG-09②：不再落 LAND）
        assertEquals(WaterBodyType.UNKNOWN, D(false, 285.0, null))
        assertEquals(WaterBodyType.UNKNOWN, D(false, null, null))
        assertEquals(WaterBodyType.UNKNOWN, D(false, 99.0, null), "千岛湖回归：marine 全 null + 高海拔 + 探针失败 → UNKNOWN")
        assertEquals(WaterBodyType.UNKNOWN, D(null, null, null), "marine 未知（M3 接线口径）+ 探针失败 → UNKNOWN")
        // marine 未知但探针有值 → 仍可判内陆水体
        assertEquals(WaterBodyType.INLAND_WATER, D(null, 99.0, true))
    }

    // ────────────────────────────── classify 缓存与降级路径 ──────────────────────────────

    @Test
    fun `classify_成功判定缓存30d内命中不重探_TTL过期重新探针`() = runTest {
        val probe = FakeProbe(result = true)
        val dao = FakeWaterBodyCacheDao()
        val classifier = newClassifier(probe, vNow = 0L, dao = dao)

        assertEquals(
            WaterBodyType.INLAND_WATER,
            classifier.classify(29.55, 118.95, marineHasValue = false, elevation = 285.0),
        )
        assertEquals(1, probe.calls)
        assertEquals(1, dao.upserts, "判定结果入库（30 d/点）")

        val cached = classifier.classify(29.55, 118.95, marineHasValue = false, elevation = 285.0)
        assertEquals(WaterBodyType.INLAND_WATER, cached)
        assertEquals(1, probe.calls, "缓存命中 → 不再探针")
        assertEquals(1, dao.upserts)
    }

    @Test
    fun `classify_千岛湖回归_marine全null高海拔探针失败_UNKNOWN不返回LAND`() = runTest {
        // M3改进意见02 回归用例（NEW-03）：千岛湖 (marineAllNull=true, elevation=99, probe=null)
        val probe = FakeProbe(result = null)
        val dao = FakeWaterBodyCacheDao()
        val classifier = newClassifier(probe, vNow = 0L, dao = dao)

        assertEquals(
            WaterBodyType.UNKNOWN,
            classifier.classify(29.55, 118.95, marineHasValue = false, elevation = 99.0),
            "探针失败 → §6.9 第 4 级 UNKNOWN，不得误判 LAND（修正前千岛湖被判陆地）",
        )
        assertEquals(1, probe.calls)
        assertEquals(
            "UNKNOWN",
            dao.rows[CacheKeys.spotKey(29.55, 118.95)]!!.waterBodyType,
            "UNKNOWN 判定同样入库（供短 TTL 判定）",
        )
    }

    @Test
    fun `classify_低海拔陆地走探针_明确无值判LAND_低海拔水体判内陆`() = runTest {
        // 低海拔陆地：探针明确无值 → LAND（捷径删除后低海拔也走探针）
        val probeFalse = FakeProbe(result = false)
        val daoFalse = FakeWaterBodyCacheDao()
        val c1 = newClassifier(probeFalse, vNow = 0L, dao = daoFalse)
        assertEquals(
            WaterBodyType.LAND,
            c1.classify(30.0, 120.0, marineHasValue = false, elevation = 2.0),
            "低海拔陆地（elevation=2, probe=false）→ LAND",
        )
        assertEquals(1, probeFalse.calls, "低海拔也发起探针（≤3m 跳过捷径已删除）")

        // 低海拔水体：探针明确有值 → INLAND_WATER
        val probeTrue = FakeProbe(result = true)
        val daoTrue = FakeWaterBodyCacheDao()
        val c2 = newClassifier(probeTrue, vNow = 0L, dao = daoTrue)
        assertEquals(
            WaterBodyType.INLAND_WATER,
            c2.classify(29.6, 119.0, marineHasValue = false, elevation = 2.0),
            "低海拔水体（elevation=2, probe=true）→ INLAND_WATER",
        )
        assertEquals(1, probeTrue.calls)
    }

    @Test
    fun `classify_UNKNOWN短TTL_24h过期重判_成功判定仍30d`() = runTest {
        var vNow = 0L
        val probe = FakeProbe(result = null)
        val dao = FakeWaterBodyCacheDao()
        val classifier = WaterBodyClassifier(
            dao, probe,
            clock = { vNow },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        assertEquals(WaterBodyType.UNKNOWN, classifier.classify(29.55, 118.95, false, 285.0))
        assertEquals(1, probe.calls)

        // 24h 内 → 短 TTL 缓存命中
        vNow += WaterBodyClassifier.UNCERTAIN_TTL_MILLIS - 1
        classifier.classify(29.55, 118.95, false, 285.0)
        assertEquals(1, probe.calls, "UNKNOWN 缓存 24h 内 → 不重判")
        // 24h 过界 → 重判（30d TTL 不会命中）
        vNow += 2
        classifier.classify(29.55, 118.95, false, 285.0)
        assertEquals(2, probe.calls, "UNKNOWN 短 TTL 24h 过期 → 重新探针（坏判定不钉死 30d）")

        // 对照：先让 UNKNOWN 缓存过期并重判为成功，再验证成功判定 30d 内仍命中
        probe.result = true
        vNow += WaterBodyClassifier.UNCERTAIN_TTL_MILLIS + 10_000
        assertEquals(WaterBodyType.INLAND_WATER, classifier.classify(29.55, 118.95, false, 285.0))
        assertEquals(3, probe.calls, "UNKNOWN 过期 → 重判成功（probe=true → INLAND_WATER）")
        vNow += WaterBodyClassifier.TTL_MILLIS - 20_000
        classifier.classify(29.55, 118.95, false, 285.0)
        assertEquals(3, probe.calls, "成功判定（INLAND_WATER）30d 内不重判（30d TTL）")
    }

    @Test
    fun `classify_探针未注入_UNKNOWN并告警`() = runTest {
        val dao = FakeWaterBodyCacheDao()
        val warnings = mutableListOf<String>()
        val classifier = newClassifier(null, vNow = 0L, dao = dao, onWarn = { warnings += it })

        assertEquals(
            WaterBodyType.UNKNOWN,
            classifier.classify(29.55, 118.95, marineHasValue = null, elevation = null),
            "probe 未注入（null）→ UNKNOWN（M3 层暂无探针/OBSTIA 图层故障期的接线口径）",
        )
        assertEquals(1, warnings.size, "探针未注入 → onWarn 告警回调（:core:data 层，M6 接日志系统）")
        assertTrue(warnings[0].contains("probe"), "告警内容含 probe 标识")
    }

    @Test
    fun `classify_marine未知走探针路径_海洋有值不触探针`() = runTest {
        val probe = FakeProbe(result = true)
        val dao = FakeWaterBodyCacheDao()
        val classifier = newClassifier(probe, vNow = 0L, dao = dao)

        // M3 接线口径：marineHasValue = null（未知）→ 走探针路径
        assertEquals(
            WaterBodyType.INLAND_WATER,
            classifier.classify(29.6, 119.0, marineHasValue = null, elevation = 99.0),
        )
        assertEquals(1, probe.calls)

        // 海洋有值 → 主判据成立，不查 OSTIA
        assertEquals(
            WaterBodyType.NEARSHORE,
            classifier.classify(36.06, 120.38, marineHasValue = true, elevation = 285.0),
        )
        assertEquals(1, probe.calls, "海洋有值即主判据，不查 OSTIA")
    }
}
