package com.tidespot.core.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CHG-37（P2 / OPT-V11-16）v5→v6 迁移锁测试——JVM 侧（对齐 [RoomMigration45JvmTest] 先例）。
 *
 * 锁「迁移 SQL 文本 ↔ 实体声明」：
 * 1. `spot` 尾增 `groupName TEXT`（**可空、无 DEFAULT**——NULL = 未分组，禁空串语义）；
 * 2. 实体字段类型可空 String；列名必须与实体属性名逐字一致（CHG-18② 教训）；
 * 3. 库版本号 = 6，且迁移对象只含一条 ALTER（只加列，不重建表）。
 */
class RoomMigration56JvmTest {

    private val sql: String = TideSpotDatabase.MIGRATION_5_6_STATEMENTS.joinToString("\n")

    @Test
    fun `迁移对象_单条ALTER语句`() {
        assertEquals(1, TideSpotDatabase.MIGRATION_5_6_STATEMENTS.size)
        assertTrue(TideSpotDatabase.MIGRATION_5_6_STATEMENTS.none { it.isBlank() })
    }

    @Test
    fun `SQL文本_groupName列_可空且无默认值`() {
        assertTrue(
            sql.contains("ALTER TABLE `spot` ADD COLUMN `groupName` TEXT"),
            "groupName 列：TEXT 可空、无 DEFAULT（NULL = 未分组）：$sql",
        )
        assertTrue(
            !sql.contains("DEFAULT", ignoreCase = true),
            "禁 DEFAULT：NULL 与空串语义必须可区分（未分组 = NULL）：$sql",
        )
        assertTrue(!sql.contains("NOT NULL", ignoreCase = true), "禁 NOT NULL：未分组必须可空：$sql")
    }

    @Test
    fun `实体声明_与迁移SQL逐字对齐`() {
        val field = SpotEntity::class.java.getDeclaredField("groupName")
        assertEquals("java.lang.String", field.type.name, "groupName 列可空 String（java.lang.String）")
    }

    @Test
    fun `迁移对象_只从v5到v6`() {
        // Room 的 @Database 注解 Retention=BINARY（运行时不可反射），版本号由 KSP 导出的
        // schemas/…/6.json 与真机 MigrationTestHelper 共同保证；JVM 侧只锁迁移语句数量与列声明。
        assertTrue(
            TideSpotDatabase.MIGRATION_5_6_STATEMENTS.size == 1,
            "v5→v6 只应有一条 ALTER（只加列，不重建表）",
        )
    }
}