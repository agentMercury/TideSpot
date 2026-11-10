package com.tidespot.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * TideSpot 本地库（§7.3）。M2：spot 收藏 / hourly_metric 逐小时缓存 / fetch_log 访问日志。
 * M3（CONTRACT M3 子契约）：+ spot_reverse_cache 逆地理缓存（§5.8）/ spot_water_body_cache
 * 水体判定缓存（§6.9）。
 * schema 经 KSP 导出到 `core/data/schemas`（build.gradle.kts room.schemaLocation）。
 */
@Database(
    entities = [
        SpotEntity::class,
        HourlyMetricEntity::class,
        FetchLogEntity::class,
        ReverseCacheEntity::class,
        WaterBodyCacheEntity::class,
        HourlyScoreEntity::class,
        ManualObservationEntity::class,
        ManualTideFitEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class TideSpotDatabase : RoomDatabase() {

    abstract fun spotDao(): SpotDao

    abstract fun hourlyMetricDao(): HourlyMetricDao

    abstract fun fetchLogDao(): FetchLogDao

    abstract fun reverseCacheDao(): ReverseCacheDao

    abstract fun waterBodyCacheDao(): WaterBodyCacheDao

    /** M4 必修 8（§7.3）：逐小时评分缓存。 */
    abstract fun hourlyScoreDao(): HourlyScoreDao

    /** M6（CHG-16⑥/OPT-M5-02）：现场校潮观测。 */
    abstract fun manualObservationDao(): ManualObservationDao

    /** M6（CHG-16⑥/OPT-M5-02）：现场校潮拟合参数。 */
    abstract fun manualTideFitDao(): ManualTideFitDao

    companion object {
        const val NAME = "tidespot.db"

        /**
         * v1→v2（M3）：新增 spot_reverse_cache 与 spot_water_body_cache 两表（§7.3 / §5.8 / §6.9），
         * v1 既有表结构不变。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `spot_reverse_cache` (" +
                        "`spotKey` TEXT NOT NULL, `displayName` TEXT NOT NULL, `subLabel` TEXT, " +
                        "`plusCode` TEXT, `locality` TEXT, `city` TEXT, `admin1` TEXT, " +
                        "`source` TEXT NOT NULL, `resolvedAtEpochMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`spotKey`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `spot_water_body_cache` (" +
                        "`spotKey` TEXT NOT NULL, `waterBodyType` TEXT NOT NULL, " +
                        "`judgedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`spotKey`))"
                )
            }
        }

        /**
         * v2→v3（M4 必修 8）：新增 hourly_score 逐小时评分表（§7.3），
         * PK(spotId, hourEpoch, algoVersion)（N-10）；vetoReason 可空列 = SQL 惯例，
         * 落库空列表 → NULL（CHG-08 ④）。v2 既有表结构不变。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hourly_score` (" +
                        "`spotId` INTEGER NOT NULL, `hourEpoch` INTEGER NOT NULL, `hourLocal` TEXT, " +
                        "`score` REAL NOT NULL, `band` TEXT NOT NULL, `vetoReason` TEXT, " +
                        "`factorsJson` TEXT NOT NULL, `algoVersion` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`spotId`, `hourEpoch`, `algoVersion`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_hourly_score_spotId` ON `hourly_score` (`spotId`)"
                )
            }
        }

    /**
     * M6 v3→v4（CHG-16⑥）三处 schema 变更，SQL 文本承载于语句列表常量
     * （RoomMigration34JvmTest 在 JVM 侧断言文本与实体/列声明对齐；真机
     * MigrationTestHelper 验证沿用 RoomMigrationTest @Ignore 先例）：
     * 1. 新表 manual_observation（观测时刻 + spotId 两个索引）；
     * 2. 新表 manual_tide_fit（spotId PK，REPLACE 覆盖式）；
     * 3. hourly_score 尾增列 vetoCodes TEXT NOT NULL DEFAULT '[]'
     *    （列名必须与 HourlyScoreEntity 属性名一致——Room 默认列名=属性名（无 @ColumnInfo name），
     *    真机迁移校验锁（A-1/RoomMigrationInstrumentedTest）实证：列名写成 veto_codes 会导致
     *    v3→v4 升级路径 schema 校验崩溃（P0），新装路径不受影响）。
     *    （与 HourlyScoreEntity @ColumnInfo(defaultValue = "[]") 一致，CHG-16①）。
     */
    val MIGRATION_3_4_STATEMENTS: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `manual_observation` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`spotId` INTEGER NOT NULL, " +
            "`observedAtEpochSec` INTEGER NOT NULL, " +
            "`createdAtEpochMs` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_manual_observation_spotId` ON `manual_observation` (`spotId`)",
        "CREATE INDEX IF NOT EXISTS `index_manual_observation_spotId_observedAtEpochSec` ON `manual_observation` (`spotId`, `observedAtEpochSec`)",
        "CREATE TABLE IF NOT EXISTS `manual_tide_fit` (" +
            "`spotId` INTEGER NOT NULL, `lagHours` INTEGER NOT NULL, `offsetM` REAL NOT NULL, " +
            "`gain` REAL NOT NULL, `adopted` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, " +
            "PRIMARY KEY(`spotId`))",
        "ALTER TABLE `hourly_score` ADD COLUMN `vetoCodes` TEXT NOT NULL DEFAULT '[]'",
    )

    /**
     * v3→v4（M6/CHG-16⑥）：新增 manual_observation / manual_tide_fit 两表 + hourly_score
     * 尾增 vetoCodes 列。v3 既有表数据不变（ALTER 只加列，旧行取 DEFAULT '[]' =
     * 无否决码语义，与 vetoReason NULL 对称）。
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_3_4_STATEMENTS.forEach { db.execSQL(it) }
        }
    }
    /**
     * v4→v5（OPT-V11-02 / CHG-27）：`manual_observation` 尾增两列（只加列，旧行取默认值）：
     * 1. `kind TEXT NOT NULL DEFAULT 'TIDE_OBSERVATION'`（观测类型；旧行=校潮观测）；
     * 2. `value REAL`（实测数值，可空；校潮观测无值，禁 0 填充）。
     * 列名/类型/defaultValue 必须与 [ManualObservationEntity] 声明逐字一致。
     */
    val MIGRATION_4_5_STATEMENTS: List<String> = listOf(
        "ALTER TABLE `manual_observation` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'TIDE_OBSERVATION'",
        "ALTER TABLE `manual_observation` ADD COLUMN `value` REAL",
    )

    /** v4→v5（OPT-V11-02）：水质实测录入支撑列（kind/value）；v4 既有数据不变。 */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_4_5_STATEMENTS.forEach { db.execSQL(it) }
        }
    }

    /**
     * v5→v6（OPT-V11-16 / CHG-37）：`spot` 尾增 `groupName TEXT`（**可空、无 DEFAULT**）。
     * - 只加列，旧行 = NULL = 未分组（UI 归入默认组「未分组」，不丢项）；
     * - 列声明必须与 [SpotEntity.groupName] 逐字一致（可空无默认），否则真机
     *   RoomMigrationInstrumentedTest 的 schema 校验失败（CHG-18② 教训）。
     */
    val MIGRATION_5_6_STATEMENTS: List<String> = listOf(
        "ALTER TABLE `spot` ADD COLUMN `groupName` TEXT",
    )

    /** v5→v6（OPT-V11-16）：收藏分组列（旧收藏全部落入「未分组」，零数据丢失）。 */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_5_6_STATEMENTS.forEach { db.execSQL(it) }
        }
    }
    }
}
