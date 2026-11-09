package com.tidespot.core.data.local

import kotlin.test.Ignore
import kotlin.test.Test

/**
 * Room v1→v2 迁移真实测试（in-memory + MigrationTestHelper）。
 *
 * **@Ignore 原因（对齐 [RoomDaoTest] M2 先例）**：Room 运行时需 Android Context，
 * 本项目未引入 Robolectric；`MIGRATION_1_2` 的 SQL 已与 @Database entities 声明逐列对齐，
 * 编译期由 KSP schema 导出（`core/data/schemas/2.json`）校验。等价覆盖：
 * - 两表列结构 → 实体 `ReverseCacheEntity` / `WaterBodyCacheEntity`（KSP 生成校验）；
 * - 读写语义 → `FakeReverseCacheDao` / `FakeWaterBodyCacheDao`（ReverseGeocoderTest / WaterBodyClassifierTest）。
 * 本类留给 M3 真机/仪器测试验证：v1 库升级后旧表数据保留 + 新表可读写。
 */
@Ignore("MIGRATION_1_2 需 Android Room 运行环境；对齐 RoomDaoTest 先例，留真机验证")
class RoomMigrationTest {

    @Test
    fun `v1到v2_新增逆地理与水体缓存表_旧表数据保留`() {
        // 启用后：MigrationTestHelper.createDatabase(TestContext, 1) 写入 v1 数据
        //   → runMigrationsAndValidate(2, MIGRATION_1_2)
        //   → 断言 spot/hourly_metric/fetch_log 数据仍在，两新表可插入读取
    }

    /**
     * M4 必修 8（§7.3）：v2→v3 新增 hourly_score 表。
     * SQL 列结构与 [HourlyScoreEntity] / KSP schema（`core/data/schemas/3.json`）逐列对齐，
     * `MIGRATION_2_3` 落在 TideSpotDatabase.companion（风格对齐 MIGRATION_1_2）。
     * 等价 JVM 覆盖：[com.tidespot.core.data.repo.HourlyScoreStoreTest]（vetoReason NULL 映射 +
     * FakeHourlyScoreDao PK REPLACE）。本用例留真机验证 v2 库升级后旧数据保留 + 新表读写。
     */
    @Test
    fun `v2到v3_新增hourly_score评分表_旧表数据保留`() {
        // 启用后：MigrationTestHelper.createDatabase(TestContext, 2) 写入 v2 数据
        //   → runMigrationsAndValidate(3, MIGRATION_2_3)
        //   → 断言 spot/hourly_metric/fetch_log/两缓存表数据仍在；hourly_score 可插入读取，
        //     PK(spotId,hourEpoch,algoVersion) 冲突覆盖；vetoReason NULL 列语义（CHG-08 ④）
    }

    /**
     * M6（CHG-16⑥）：v3→v4 新增 manual_observation / manual_tide_fit 两表 + hourly_score
     * 尾增 veto_codes 列。SQL 文本与实体/列声明的 JVM 侧对齐断言见
     * [RoomMigration34JvmTest]（MIGRATION_3_4_STATEMENTS 常量逐条校验）。
     * 本用例对齐类级 @Ignore 真机先例：留真机验证 v3 库升级后旧数据保留 + 新表读写 +
     * hourly_score 旧行 veto_codes 取 DEFAULT '[]'（空数组 = 无否决码，与 vetoReason NULL 对称）。
     */
    @Test
    fun `v3到v4_新增校潮两表与veto_codes列_旧表数据保留`() {
        // 启用后：MigrationTestHelper.createDatabase(TestContext, 3) 写入 v3 数据
        //   → runMigrationsAndValidate(4, MIGRATION_3_4)
        //   → 断言 spot/hourly_metric/fetch_log/两缓存表/hourly_score 数据仍在；
        //     manual_observation 可插入读取（双索引）；manual_tide_fit spotId PK REPLACE 覆盖；
        //     旧行 hourly_score.vetoCodes == "[]"（DEFAULT 回填）
    }
}
