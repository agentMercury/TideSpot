package com.tidespot.core.data.local

import androidx.room.ColumnInfo
import com.tidespot.core.data.repo.WaterQualityObservationKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * CHG-27（P0-2 / OPT-V11-02）v4→v5 迁移锁测试——JVM 侧（对齐 [RoomMigration34JvmTest] 先例）。
 *
 * 锁「迁移 SQL 文本 ↔ 实体声明」：
 * 1. `manual_observation` 尾增 `kind TEXT NOT NULL DEFAULT 'TIDE_OBSERVATION'`；
 * 2. `manual_observation` 尾增 `value REAL`（可空，禁 0 填充）；
 * 3. 实体侧字段与 defaultValue 逐字一致（真机 Room schema 校验依据，列名错位即升级崩——CHG-18② 教训）。
 */
class RoomMigration45JvmTest {

    private val sql: String = TideSpotDatabase.MIGRATION_4_5_STATEMENTS.joinToString("\n")

    @Test
    fun `迁移对象_两条ALTER语句非空`() {
        assertEquals(2, TideSpotDatabase.MIGRATION_4_5_STATEMENTS.size, "kind + value 两条 ALTER")
        assertTrue(TideSpotDatabase.MIGRATION_4_5_STATEMENTS.none { it.isBlank() })
    }

    @Test
    fun `SQL文本_kind列_默认TIDE_OBSERVATION`() {
        assertTrue(
            sql.contains("ALTER TABLE `manual_observation` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'TIDE_OBSERVATION'"),
            "kind 列：TEXT NOT NULL DEFAULT 'TIDE_OBSERVATION'（旧行=校潮观测）：$sql",
        )
    }

    @Test
    fun `SQL文本_value列_可空REAL`() {
        assertTrue(
            sql.contains("ALTER TABLE `manual_observation` ADD COLUMN `value` REAL"),
            "value 列：REAL 可空（校潮观测无值，禁 0 填充）：$sql",
        )
    }

    @Test
    fun `实体声明_与迁移SQL逐字对齐`() {
        val kindField = ManualObservationEntity::class.java.getDeclaredField("kind")
        assertEquals(String::class.java, kindField.type, "kind 列类型 String")
        // defaultValue 逐字对齐由真机迁移锁（MigrationTestHelper schema 校验）承担（CHG-18② 教训：
        // SQL 文本锁不等价于迁移行为锁）；JVM 侧锁常量与 SQL 文本的一致性
        assertTrue(
            sql.contains("DEFAULT '${ManualObservationEntity.KIND_TIDE_OBSERVATION}'"),
            "SQL DEFAULT 必须取实体常量值（禁硬编码双份）：$sql",
        )

        val valueField = ManualObservationEntity::class.java.getDeclaredField("value")
        assertEquals("java.lang.Double", valueField.type.name, "value 列可空 Double（java.lang.Double）")

        assertEquals("TIDE_OBSERVATION", ManualObservationEntity.KIND_TIDE_OBSERVATION)
        assertEquals("WATER_TEMP", WaterQualityObservationKind.WATER_TEMP.name)
        assertEquals("DO", WaterQualityObservationKind.DO.name)
        assertEquals("TURBIDITY", WaterQualityObservationKind.TURBIDITY.name)
    }
}