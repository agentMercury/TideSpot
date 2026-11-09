package com.tidespot.core.data.local

import androidx.room.migration.Migration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M6 v3→v4 迁移锁测试（CHG-16⑥）——JVM 侧。
 * RoomMigrationTest 类级 @Ignore（真机 MigrationTestHelper 先例）不适合放活动断言，
 * 故本类用 **SQL 文本常量**（[TideSpotDatabase.MIGRATION_3_4_STATEMENTS]）在纯 JVM 断言
 * 三处 schema 变更与实体/列声明对齐：
 * 1. manual_observation（id autoGenerate PK + spotId 索引 + (spotId,observedAtEpochSec) 索引）；
 * 2. manual_tide_fit（spotId PK）；
 * 3. hourly_score 尾增 veto_codes TEXT NOT NULL DEFAULT '[]'（与 HourlyScoreEntity
 *    @ColumnInfo(defaultValue = "[]") 一致——不一致真机 Room schema 校验会失败）。
 */
class RoomMigration34JvmTest {

    private val sql: String = TideSpotDatabase.MIGRATION_3_4_STATEMENTS.joinToString("\n")

    @Test
    fun `迁移对象_类型与语句非空`() {
        assertTrue(TideSpotDatabase.MIGRATION_3_4 is Migration, "MIGRATION_3_4 为 Room Migration")
        assertTrue(TideSpotDatabase.MIGRATION_3_4_STATEMENTS.size == 5, "1 建表 + 2 索引 + 1 建表 + 1 ALTER = 5 条")
        assertTrue(TideSpotDatabase.MIGRATION_3_4_STATEMENTS.none { it.isBlank() })
    }

    @Test
    fun `SQL文本_新表manual_observation_含主键与双索引`() {
        assertTrue(
            sql.contains("CREATE TABLE IF NOT EXISTS `manual_observation`"),
            "建表语句存在",
        )
        assertTrue(sql.contains("`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"), "id autoGenerate PK")
        assertTrue(sql.contains("`spotId` INTEGER NOT NULL"))
        assertTrue(sql.contains("`observedAtEpochSec` INTEGER NOT NULL"))
        assertTrue(sql.contains("`createdAtEpochMs` INTEGER NOT NULL"))
        assertTrue(
            sql.contains("CREATE INDEX IF NOT EXISTS `index_manual_observation_spotId` ON `manual_observation` (`spotId`)"),
            "Index(spotId)",
        )
        assertTrue(
            sql.contains("CREATE INDEX IF NOT EXISTS `index_manual_observation_spotId_observedAtEpochSec` " +
                "ON `manual_observation` (`spotId`, `observedAtEpochSec`)"),
            "Index(spotId, observedAtEpochSec)",
        )
    }

    @Test
    fun `SQL文本_新表manual_tide_fit_spotId主键_列与实体对齐`() {
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `manual_tide_fit`"))
        assertTrue(sql.contains("`spotId` INTEGER NOT NULL"))
        assertTrue(sql.contains("`lagHours` INTEGER NOT NULL"))
        assertTrue(sql.contains("`offsetM` REAL NOT NULL"))
        assertTrue(sql.contains("`gain` REAL NOT NULL"), "gain 为 Double → REAL（与实体一致）")
        assertTrue(sql.contains("`adopted` INTEGER NOT NULL"), "Boolean → INTEGER（Room 惯例）")
        assertTrue(sql.contains("`updatedAtEpochMs` INTEGER NOT NULL"))
        assertTrue(sql.contains("PRIMARY KEY(`spotId`))"), "spotId 单列 PK")
    }

    @Test
    fun `SQL文本_hourly_score尾增veto_codes默认空数组`() {
        assertTrue(
            sql.contains("ALTER TABLE `hourly_score` ADD COLUMN `veto_codes` TEXT NOT NULL DEFAULT '[]'"),
            "v3→v4 尾增列：TEXT NOT NULL DEFAULT '[]'（与 @ColumnInfo(defaultValue = \"[]\") 一致）",
        )
        // 实体侧声明对齐（列名/defaultValue 的真机校验由 KSP schema 4.json + MigrationTestHelper 兜底；
        // JVM 侧以编译期实体字段存在性 + SQL 文本守护）
        val field = HourlyScoreEntity::class.java.getDeclaredField("vetoCodes")
        assertEquals(String::class.java, field.type, "vetoCodes 列类型 String（JSON 数组串）")
    }
}