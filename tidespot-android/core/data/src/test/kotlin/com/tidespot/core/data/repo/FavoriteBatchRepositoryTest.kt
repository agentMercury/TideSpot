package com.tidespot.core.data.repo

import com.tidespot.core.data.local.FetchLogEntity
import com.tidespot.core.data.local.HourlyMetricEntity
import com.tidespot.core.data.local.HourlyScoreEntity
import com.tidespot.core.data.local.ManualObservationEntity
import com.tidespot.core.data.local.ManualTideFitEntity
import com.tidespot.core.data.mock.FakeFetchLogDao
import com.tidespot.core.data.mock.FakeHourlyDao
import com.tidespot.core.data.mock.FakeHourlyScoreDao
import com.tidespot.core.data.mock.FakeManualObservationDao
import com.tidespot.core.data.mock.FakeManualTideFitDao
import com.tidespot.core.data.mock.FakeSpotDao
import com.tidespot.core.data.remote.OpenMeteoClient
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.model.WaterBodyType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OPT-V11-12 / OPT-V11-16（P2/CHG-37）仓储批量操作锁（JVM，Fake DAO）：
 *
 * 1. **级联删除**：`deleteFavorites` / `deleteFavorite` 必须清 `spot` + `hourly_score` + `hourly_metric`
 *    + `manual_observation` + `manual_tide_fit` + `fetch_log` 六张表（客户裁定 D19 修正既有孤儿行缺陷）；
 * 2. **批量改分组**：空白/空串 → NULL（未分组），定向 UPDATE 不动其它列；
 * 3. **批量改标签**：只改 `tagsJson`（D07 patch 语义），派生列（waterBodyType/elevation/tzOffsetSec）不变。
 *
 * 生产路径事务由 Room `withTransaction` 包裹（JVM 路径 db=null → 顺序执行，行为等价）。
 */
class FavoriteBatchRepositoryTest {

    private lateinit var spotDao: FakeSpotDao
    private lateinit var hourlyDao: FakeHourlyDao
    private lateinit var logDao: FakeFetchLogDao
    private lateinit var scoreDao: FakeHourlyScoreDao
    private lateinit var obsDao: FakeManualObservationDao
    private lateinit var fitDao: FakeManualTideFitDao

    private fun repo(): TideSpotRepository {
        // 本测试零网络：client 指向不可达地址，任何意外请求都会失败暴露（禁静默补数据）
        val client = OpenMeteoClient(
            weatherBaseUrl = "http://127.0.0.1:1/",
            marineBaseUrl = "http://127.0.0.1:1/",
        )
        return TideSpotRepository(
            client = client,
            spotDao = spotDao,
            hourlyDao = hourlyDao,
            fetchLogDao = logDao,
            hourlyScoreDao = scoreDao,
            manualObservationDao = obsDao,
            manualTideFitDao = fitDao,
        )
    }

    private fun setUpDaos() {
        spotDao = FakeSpotDao()
        hourlyDao = FakeHourlyDao()
        logDao = FakeFetchLogDao()
        scoreDao = FakeHourlyScoreDao()
        obsDao = FakeManualObservationDao()
        fitDao = FakeManualTideFitDao()
    }

    private suspend fun seedSpot(repository: TideSpotRepository, name: String): Long =
        repository.upsertFavorite(
            FavoriteSpot(id = 0, name = name, lat = 29.55, lon = 118.95),
        )

    private suspend fun seedChildRows(spotId: Long) {
        hourlyDao.insertAll(
            listOf(
                HourlyMetricEntity(
                    spotId = spotId,
                    hourEpoch = 1_700_000_000L,
                    hourLocal = null,
                    kind = "weather",
                    field = "temperature_2m",
                    value = 18.0,
                    source = "open-meteo",
                    fetchedAt = 1_700_000_000_000L,
                ),
            ),
        )
        scoreDao.insertAll(
            listOf(
                HourlyScoreEntity(
                    spotId = spotId,
                    hourEpoch = 1_700_000_000L,
                    hourLocal = null,
                    score = 80.0,
                    band = "爆护窗口",
                    vetoReason = null,
                    factorsJson = "[]",
                    algoVersion = 1,
                ),
            ),
        )
        logDao.insertAll(
            listOf(
                FetchLogEntity(
                    spotId = spotId,
                    source = "weather",
                    urlHash = 1,
                    httpStatus = 200,
                    bytes = 100,
                    costMs = 10,
                    fetchedAt = 1_700_000_000_000L,
                ),
            ),
        )
        obsDao.insert(
            ManualObservationEntity(
                spotId = spotId,
                observedAtEpochSec = 1_700_000_000L,
                createdAtEpochMs = 1_700_000_000_000L,
            ),
        )
        fitDao.upsert(
            ManualTideFitEntity(
                spotId = spotId,
                lagHours = 1,
                offsetM = 0.0,
                gain = 1.0,
                adopted = true,
                updatedAtEpochMs = 1_700_000_000_000L,
            ),
        )
    }

    @Test
    fun `批量删除_六张表级联清理`() = runTest {
        setUpDaos()
        val repository = repo()
        val a = seedSpot(repository, "A")
        val b = seedSpot(repository, "B")
        val c = seedSpot(repository, "C")
        listOf(a, b, c).forEach { seedChildRows(it) }

        repository.deleteFavorites(listOf(a, b))

        assertEquals(listOf(c), repository.favorites().map { it.id }, "只删选中项")
        assertTrue(hourlyDao.rows.all { it.spotId == c }, "hourly_metric 级联清理")
        assertTrue(scoreDao.rows.all { it.spotId == c }, "hourly_score 级联清理")
        assertTrue(logDao.rows.all { it.spotId == c }, "fetch_log 级联清理")
        assertTrue(obsDao.rows.all { it.spotId == c }, "manual_observation 级联清理")
        assertEquals(setOf(c), fitDao.rows.keys, "manual_tide_fit 级联清理")
    }

    @Test
    fun `单条删除_复用级联路径`() = runTest {
        setUpDaos()
        val repository = repo()
        val a = seedSpot(repository, "A")
        seedChildRows(a)

        repository.deleteFavorite(a)

        assertTrue(repository.favorites().isEmpty())
        assertTrue(hourlyDao.rows.isEmpty())
        assertTrue(scoreDao.rows.isEmpty())
        assertTrue(logDao.rows.isEmpty())
        assertTrue(obsDao.rows.isEmpty())
        assertTrue(fitDao.rows.isEmpty())
    }

    @Test
    fun `空id列表_不触发任何删除`() = runTest {
        setUpDaos()
        val repository = repo()
        seedSpot(repository, "A")
        repository.deleteFavorites(emptyList())
        assertEquals(1, repository.favorites().size)
    }

    @Test
    fun `批量改分组_空白归一为null且不动其它列`() = runTest {
        setUpDaos()
        val repository = repo()
        val a = seedSpot(repository, "A")
        val b = seedSpot(repository, "B")
        repository.updateSpotWaterBody(a, WaterBodyType.NEARSHORE, 12.5, 28800)

        repository.updateFavoritesGroup(listOf(a, b), "常去")
        assertEquals(listOf("常去"), repository.favorites().mapNotNull { it.groupName }.distinct())

        repository.updateFavoritesGroup(listOf(a), "   ")
        val reloaded = repository.favorites().first { it.id == a }
        assertNull(reloaded.groupName, "空白 → NULL = 未分组（禁空串）")
        assertEquals(WaterBodyType.NEARSHORE, reloaded.waterBody, "定向 UPDATE 不动派生列")
        assertEquals(12.5, reloaded.elevation)
        assertEquals(28800, reloaded.tzOffsetSec)
    }

    @Test
    fun `批量改标签_只改tagsJson不动派生列`() = runTest {
        setUpDaos()
        val repository = repo()
        val a = seedSpot(repository, "A")
        repository.updateSpotWaterBody(a, WaterBodyType.NEARSHORE, 8.0, 32400)

        repository.updateFavoritesTags(mapOf(a to listOf("夜钓", "礁区")))

        val reloaded = repository.favorites().first { it.id == a }
        assertEquals(listOf("夜钓", "礁区"), reloaded.tags)
        assertEquals(WaterBodyType.NEARSHORE, reloaded.waterBody)
        assertEquals(8.0, reloaded.elevation)
        assertEquals(32400, reloaded.tzOffsetSec)
    }
}