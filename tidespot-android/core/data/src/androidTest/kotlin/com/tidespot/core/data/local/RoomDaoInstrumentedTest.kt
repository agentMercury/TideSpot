package com.tidespot.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room 真实 DAO 测试（BLK-04 / HANDOVER §6 A-1，CHG-18 解锁）。
 *
 * **迁移路径**：原 JVM 侧 [RoomDaoTest]（类级 @Ignore，M2 决策）空壳用例按注释语义
 * 在真机 instrumented 落地（androidTest source set，in-memory + 文件库双形态），
 * 原类随本类落地删除（跳过数归零；等价 JVM 覆盖说明见台账 CHG-18）。
 *
 * 覆盖目标（对齐原 @Ignore 注释）：
 * 1. Room 编译期 SQL 校验与 schema（KSP schema 已导出，迁移侧由 RoomMigrationInstrumentedTest 强校验）；
 * 2. PK 冲突 REPLACE 行为（hourly_metric 整窗覆盖，CHG-03）；
 * 3. fetch_log 写入与 lastSuccess 查询（F-4.2 TTL 判定基准；TTL 3h/6h 边界为纯函数，
 *    已由 JVM 侧 TtlTest 精确锁定，不在真机重复）；
 * 4. F-2.2 语义的真机形态：文件库 close → 重开（等效「重启」）后收藏不丢。
 */
@RunWith(AndroidJUnit4::class)
class RoomDaoInstrumentedTest {

    private lateinit var db: TideSpotDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TideSpotDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 收藏 CRUD + 文件库「重启不丢」（F-2.2 真机形态）。 */
    @Test
    fun `收藏CRUD_文件库重启不丢`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "test_dao_restart_${System.currentTimeMillis()}.db"
        val fileDb = Room.databaseBuilder(ctx, TideSpotDatabase::class.java, name)
            .allowMainThreadQueries().build()

        val spot = SpotEntity(lat = 29.55, lon = 118.95, name = "千岛湖", note = "路亚")
        val id = fileDb.spotDao().insert(spot)
        assertTrue(id > 0)
        fileDb.close() // 等效进程重启

        val reopened = Room.databaseBuilder(ctx, TideSpotDatabase::class.java, name)
            .allowMainThreadQueries().build()
        try {
            val loaded = reopened.spotDao().getById(id) ?: error("重启后收藏丢失（F-2.2 失败）")
            assertEquals("千岛湖", loaded.name)
            assertEquals("路亚", loaded.note)
            assertEquals("LAND", loaded.waterBodyType) // 默认派生列

            reopened.spotDao().update(loaded.copy(name = "千岛湖·东南湖区"))
            assertEquals("千岛湖·东南湖区", reopened.spotDao().getById(id)?.name)

            reopened.spotDao().delete(reopened.spotDao().getById(id)!!)
            assertNull(reopened.spotDao().getById(id))
        } finally {
            reopened.close()
            ctx.deleteDatabase(name)
        }
    }

    /** hourly_metric PK(spotId,hourEpoch,field) 冲突 REPLACE（CHG-03 整窗覆盖语义）。 */
    @Test
    fun `hourly_metric_PK冲突REPLACE`() = runBlocking {
        val base = HourlyMetricEntity(
            spotId = 1, hourEpoch = 1_700_000_000, hourLocal = "2026-11-09T17:00",
            kind = "weather", field = "temperature_2m", value = 18.5, source = "open-meteo", fetchedAt = 100L,
        )
        db.hourlyMetricDao().insertAll(listOf(base))
        db.hourlyMetricDao().insertAll(listOf(base.copy(value = 19.0, fetchedAt = 200L)))
        val rows = db.hourlyMetricDao().forSpot(1)
        assertEquals(1, rows.size) // 覆盖而非追加
        assertEquals(19.0, rows.single().value)
        assertEquals(200L, rows.single().fetchedAt)
    }

    /** fetch_log 写入与 lastSuccess：只取 httpStatus=200 最新一条（4xx/网络异常 null 不命中）。 */
    @Test
    fun `fetch_log_写入与lastSuccess查询`() = runBlocking {
        db.fetchLogDao().insertAll(
            listOf(
                FetchLogEntity(spotId = 7, source = "weather", urlHash = 1, httpStatus = 200, bytes = 100L, costMs = 30L, fetchedAt = 1_000L),
                FetchLogEntity(spotId = 7, source = "weather", urlHash = 1, httpStatus = 500, bytes = null, costMs = 40L, fetchedAt = 2_000L),
                FetchLogEntity(spotId = 7, source = "weather", urlHash = 1, httpStatus = null, bytes = null, costMs = 50L, fetchedAt = 3_000L),
                FetchLogEntity(spotId = 7, source = "weather", urlHash = 1, httpStatus = 200, bytes = 120L, costMs = 60L, fetchedAt = 4_000L),
            ),
        )
        val last = db.fetchLogDao().lastSuccess(7, "weather")!!
        assertEquals(4_000L, last.fetchedAt) // 最新一次成功（跳过 500 / null）
        assertEquals(200, last.httpStatus)
        assertNull(db.fetchLogDao().lastSuccess(8, "weather")) // 无记录点 → null
    }
}
