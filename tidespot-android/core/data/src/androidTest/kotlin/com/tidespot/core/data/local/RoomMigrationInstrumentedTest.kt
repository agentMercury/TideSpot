package com.tidespot.core.data.local

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room 迁移真机测试（BLK-04 / HANDOVER §6 A-1，CHG-18 解锁）。
 *
 * **迁移路径**：原 JVM 侧 [RoomMigrationTest]（类级 @Ignore）三个空壳用例按注释语义
 * 在真机以 MigrationTestHelper 落地；原类随本类落地删除。JVM 侧 SQL 文本对齐断言
 * （[RoomMigration34JvmTest]）保留，与本类互补：JVM 锁「迁移 SQL 文本 ↔ 实体声明」，
 * 本类在真机执行迁移并强校验 schema（runMigrationsAndValidate 的表/列/索引结构校验 +
 * 旧行数据保留断言）。
 *
 * 插入列集逐版本对齐 `core/data/schemas/com.tidespot.core.data.local.TideSpotDatabase/{1,2,3,4}.json`。
 */
@RunWith(AndroidJUnit4::class)
class RoomMigrationInstrumentedTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TideSpotDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private fun spotRow(id: Long = 1): ContentValues = ContentValues().apply {
        put("id", id)
        put("lat", 29.55)
        put("lon", 118.95)
        put("name", "千岛湖")
        put("elevation", 108.0 as Double?)
        put("waterBodyType", "LAKE")
        put("tzOffsetSec", 28800 as Int?)
        put("favorite", 1)
        put("note", "路亚" as String?)
        put("groupPreset", "淡水路亚" as String?)
        put("speciesId", "bass" as String?)
        put("tagsJson", "[]")
        put("lastViewedAt", 0L)
        put("updatedAt", 0L)
        put("createdAtEpochSec", 0L)
    }

    private fun metricRow(spotId: Long, fetchedAt: Long): ContentValues = ContentValues().apply {
        put("spotId", spotId)
        put("hourEpoch", 1_700_000_000L)
        put("hourLocal", "2026-11-09T17:00" as String?)
        put("kind", "weather")
        put("field", "temperature_2m")
        put("value", 18.5 as Double?)
        put("source", "open-meteo")
        put("fetchedAt", fetchedAt)
    }

    private fun fetchRow(id: Long, status: Int?): ContentValues = ContentValues().apply {
        put("id", id)
        put("spotId", 1L)
        put("source", "weather")
        put("urlHash", 123)
        put("httpStatus", status)
        put("bytes", 100L as Long?)
        put("costMs", 30L)
        put("fetchedAt", 1_000L)
    }

    private fun reverseRow(key: String): ContentValues = ContentValues().apply {
        put("spotKey", key)
        put("displayName", "千岛湖")
        put("subLabel", "杭州市淳安县" as String?)
        put("plusCode", null as String?)
        put("locality", null as String?)
        put("city", null as String?)
        put("admin1", null as String?)
        put("source", "RG_B")
        put("resolvedAtEpochMs", 1_000L)
    }

    private fun waterRow(key: String): ContentValues = ContentValues().apply {
        put("spotKey", key)
        put("waterBodyType", "LAKE")
        put("judgedAtEpochMs", 1_000L)
    }

    private fun scoreRow(spotId: Long, algoVersion: Int, vetoReason: String?): ContentValues =
        ContentValues().apply {
            put("spotId", spotId)
            put("hourEpoch", 1_700_000_000L)
            put("hourLocal", "2026-11-09T17:00" as String?)
            put("score", 82.5)
            put("band", "很适合")
            put("vetoReason", vetoReason)
            put("factorsJson", "[]")
            put("algoVersion", algoVersion)
        }

    private fun queryScalar(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): String? =
        db.query(sql).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun queryCount(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { c -> c.moveToFirst(); c.getInt(0) }

    /** v1→v2（M3）：新增逆地理/水体缓存两表；v1 三表旧数据保留 + 两新表可插入读取。 */
    @Test
    fun `v1到v2_新增逆地理与水体缓存表_旧表数据保留`() {
        val db = helper.createDatabase(TEST_DB_1, 1).also {
            it.insert("spot", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, spotRow())
            it.insert("hourly_metric", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, metricRow(1, 100L))
            it.insert("fetch_log", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, fetchRow(1, 200))
            it.close()
        }
        val migrated = helper.runMigrationsAndValidate(TEST_DB_1, 2, true, TideSpotDatabase.MIGRATION_1_2)
        try {
            assertEquals("千岛湖", queryScalar(migrated, "SELECT name FROM spot WHERE id = 1"))
            assertEquals(1, queryCount(migrated, "SELECT COUNT(*) FROM hourly_metric"))
            assertEquals(1, queryCount(migrated, "SELECT COUNT(*) FROM fetch_log"))
            migrated.insert("spot_reverse_cache", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, reverseRow("29.55,118.95"))
            migrated.insert("spot_water_body_cache", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, waterRow("29.55,118.95"))
            assertEquals("RG_B", queryScalar(migrated, "SELECT source FROM spot_reverse_cache WHERE spotKey = '29.55,118.95'"))
            assertEquals("LAKE", queryScalar(migrated, "SELECT waterBodyType FROM spot_water_body_cache WHERE spotKey = '29.55,118.95'"))
        } finally {
            migrated.close()
        }
    }

    /** v2→v3（M4 必修 8）：新增 hourly_score；旧数据保留 + 新表读写 + PK REPLACE + vetoReason NULL 语义。 */
    @Test
    fun `v2到v3_新增hourly_score评分表_旧表数据保留`() {
        helper.createDatabase(TEST_DB_2, 2).also {
            it.insert("spot", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, spotRow(2))
            it.insert("spot_reverse_cache", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, reverseRow("k1"))
            it.insert("spot_water_body_cache", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, waterRow("k1"))
            it.insert("hourly_metric", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, metricRow(2, 200L))
            it.close()
        }
        val migrated = helper.runMigrationsAndValidate(TEST_DB_2, 3, true, TideSpotDatabase.MIGRATION_2_3)
        try {
            assertEquals("千岛湖", queryScalar(migrated, "SELECT name FROM spot WHERE id = 2"))
            assertEquals(1, queryCount(migrated, "SELECT COUNT(*) FROM spot_reverse_cache"))
            assertEquals(1, queryCount(migrated, "SELECT COUNT(*) FROM spot_water_body_cache"))
            assertEquals(1, queryCount(migrated, "SELECT COUNT(*) FROM hourly_metric"))

            // 新表可写：同 PK 两行 → REPLACE 覆盖为 1 行；vetoReason NULL 列语义（CHG-08④）
            migrated.insert("hourly_score", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, scoreRow(2, 1, null))
            migrated.insert("hourly_score", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, scoreRow(2, 1, null))
            assertEquals(1, queryCount(migrated, "SELECT COUNT(*) FROM hourly_score"))
            val band = queryScalar(migrated, "SELECT band FROM hourly_score WHERE spotId = 2")
            assertEquals("很适合", band)
            migrated.query("SELECT vetoReason FROM hourly_score WHERE spotId = 2").use { c ->
                assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
            }
        } finally {
            migrated.close()
        }
    }

    /**
     * v3→v4（CHG-16⑥）：新增校潮两表 + hourly_score 尾增 vetoCodes。
     * 旧数据保留 + manual_observation 双索引（结构由 runMigrationsAndValidate 强校验）
     * + manual_tide_fit PK REPLACE + 旧行 vetoCodes 取 DEFAULT '[]' 回填。
     */
    @Test
    fun `v3到v4_新增校潮两表与veto_codes列_旧表数据保留`() {
        helper.createDatabase(TEST_DB_3, 3).also {
            it.insert("spot", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, spotRow(3))
            it.insert("hourly_metric", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, metricRow(3, 300L))
            it.insert("fetch_log", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, fetchRow(3, 200))
            it.insert("spot_reverse_cache", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, reverseRow("k3"))
            it.insert("spot_water_body_cache", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, waterRow("k3"))
            it.insert("hourly_score", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, scoreRow(3, 1, null))
            it.close()
        }
        val migrated = helper.runMigrationsAndValidate(TEST_DB_3, 4, true, TideSpotDatabase.MIGRATION_3_4)
        try {
            // v3 旧表数据全部保留
            assertEquals("千岛湖", queryScalar(migrated, "SELECT name FROM spot WHERE id = 3"))
            assertEquals(1, queryCount(migrated, "SELECT COUNT(*) FROM hourly_metric"))
            assertEquals(1, queryCount(migrated, "SELECT COUNT(*) FROM fetch_log"))
            assertEquals(1, queryCount(migrated, "SELECT COUNT(*) FROM spot_reverse_cache"))
            assertEquals(1, queryCount(migrated, "SELECT COUNT(*) FROM spot_water_body_cache"))

            // 旧行 vetoCodes = DEFAULT '[]' 回填（空数组 = 无否决码，与 vetoReason NULL 对称）
            assertEquals("[]", queryScalar(migrated, "SELECT vetoCodes FROM hourly_score WHERE spotId = 3"))

            // manual_observation：只增不覆盖（autoGenerate PK），双索引结构由 schema 校验保证
            val obs = ContentValues().apply {
                put("spotId", 3L); put("observedAtEpochSec", 1_700_000_100L); put("createdAtEpochMs", 1_000L)
            }
            migrated.insert("manual_observation", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, obs)
            migrated.insert("manual_observation", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, ContentValues(obs))
            assertEquals(2, queryCount(migrated, "SELECT COUNT(*) FROM manual_observation"))

            // manual_tide_fit：spotId PK，REPLACE 覆盖式登记
            val fit = ContentValues().apply {
                put("spotId", 3L); put("lagHours", 1); put("offsetM", 0.0)
                put("gain", 1.0); put("adopted", 0); put("updatedAtEpochMs", 1_000L)
            }
            migrated.insert("manual_tide_fit", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, fit)
            migrated.insert("manual_tide_fit", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, ContentValues(fit).apply { put("lagHours", 2) })
            assertEquals(1, queryCount(migrated, "SELECT COUNT(*) FROM manual_tide_fit"))
            assertEquals(2, queryScalar(migrated, "SELECT lagHours FROM manual_tide_fit WHERE spotId = 3")!!.toInt())
        } finally {
            migrated.close()
        }
    }

    /**
     * v4→v5（CHG-27）：manual_observation 尾增 kind/value。
     * 旧行 kind = DEFAULT 'TIDE_OBSERVATION'（= 校潮观测）、value = NULL（禁 0 填充）；
     * 新写入可指定水质实测类型与数值（OPT-V11-02）。
     */
    @Test
    fun `v4到v5_观测表增kind与value_旧行默认校潮`() {
        helper.createDatabase(TEST_DB_4, 4).also {
            it.insert("spot", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, spotRow(4))
            it.insert("hourly_score", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, scoreRow(4, 1, null))
            val oldObs = ContentValues().apply {
                put("spotId", 4L); put("observedAtEpochSec", 1_700_000_200L); put("createdAtEpochMs", 1_000L)
            }
            it.insert("manual_observation", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, oldObs)
            it.close()
        }
        val migrated = helper.runMigrationsAndValidate(TEST_DB_4, 5, true, TideSpotDatabase.MIGRATION_4_5)
        try {
            assertEquals("千岛湖", queryScalar(migrated, "SELECT name FROM spot WHERE id = 4"))
            // 旧行 kind 回填校潮默认值、value 保持 NULL
            assertEquals(
                ManualObservationEntity.KIND_TIDE_OBSERVATION,
                queryScalar(migrated, "SELECT kind FROM manual_observation WHERE spotId = 4"),
            )
            migrated.query("SELECT value FROM manual_observation WHERE spotId = 4").use { c ->
                assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
            }
            // 新行可写水质实测（kind=WATER_TEMP + value）
            val waterObs = ContentValues().apply {
                put("spotId", 4L); put("observedAtEpochSec", 1_700_000_300L); put("createdAtEpochMs", 2_000L)
                put("kind", "WATER_TEMP"); put("value", 18.5 as Double?)
            }
            migrated.insert("manual_observation", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, waterObs)
            assertEquals(2, queryCount(migrated, "SELECT COUNT(*) FROM manual_observation"))
            assertEquals(
                "18.5",
                queryScalar(migrated, "SELECT value FROM manual_observation WHERE observedAtEpochSec = 1700000300"),
            )
        } finally {
            migrated.close()
        }
    }

    /**
     * v5→v6（CHG-37 / OPT-V11-16）：spot 尾增 groupName（可空无 DEFAULT）。
     * 旧收藏全部 groupName = NULL（= 未分组，UI 归入默认组不丢项）；新写入可指定分组，
     * 且定向 UPDATE 语义可区分「未分组（NULL）」与「有分组」。
     */
    @Test
    fun `v5到v6_收藏点增分组列_旧行归未分组`() {
        helper.createDatabase(TEST_DB_5, 5).also {
            it.insert("spot", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, spotRow(5))
            it.insert("hourly_metric", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, metricRow(5, 500L))
            it.close()
        }
        val migrated = helper.runMigrationsAndValidate(TEST_DB_5, 6, true, TideSpotDatabase.MIGRATION_5_6)
        try {
            // 旧数据保留 + 新列 NULL = 未分组
            assertEquals("千岛湖", queryScalar(migrated, "SELECT name FROM spot WHERE id = 5"))
            assertEquals(1, queryCount(migrated, "SELECT COUNT(*) FROM hourly_metric"))
            migrated.query("SELECT groupName FROM spot WHERE id = 5").use { c ->
                assertTrue(c.moveToFirst()); assertTrue(c.isNull(0), "旧收藏 groupName 必须为 NULL（未分组）")
            }
            // 新列可写入并读回
            migrated.execSQL("UPDATE spot SET groupName = '常去' WHERE id = 5")
            assertEquals("常去", queryScalar(migrated, "SELECT groupName FROM spot WHERE id = 5"))
            // 可回到未分组（NULL，禁空串语义）
            migrated.execSQL("UPDATE spot SET groupName = NULL WHERE id = 5")
            migrated.query("SELECT groupName FROM spot WHERE id = 5").use { c ->
                assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
            }
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val TEST_DB_1 = "migration-test-1to2.db"
        const val TEST_DB_2 = "migration-test-2to3.db"
        const val TEST_DB_3 = "migration-test-3to4.db"
        const val TEST_DB_4 = "migration-test-4to5.db"
        const val TEST_DB_5 = "migration-test-5to6.db"
    }
}
