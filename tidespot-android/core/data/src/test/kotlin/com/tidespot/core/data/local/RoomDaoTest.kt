package com.tidespot.core.data.local

import kotlin.test.Ignore
import kotlin.test.Test

/**
 * Room 真实 DAO 测试（in-memory）。
 *
 * **@Ignore 原因（M2 决策）**：Room 运行时需要 Android Context（Room.inMemoryDatabaseBuilder），
 * 本项目未引入 Robolectric，纯 JVM 单测无法实例化数据库；`room-testing` 已在测试依赖中就位。
 * 以下用例的**等价逻辑覆盖**已由 JVM 侧完成：
 * - 收藏 CRUD → `TideSpotRepositoryTest.收藏CRUD与fetch_log可视化`（FakeSpotDao，同接口语义）；
 * - TTL 过期判定 → `TtlTest`（纯函数，§7.3 3h/6h 边界精确断言）；
 * - fetch_log 写入与 lastSuccess 查询 → `TideSpotRepositoryTest` 状态①⑤⑦（FakeFetchLogDao）；
 * - 实体/映射往返 → `MapperTest.实体写入与重建往返一致`。
 *
 * 本类留给 **M3 后真机/仪器测试**（androidTest 或引入 Robolectric 后启用）验证：
 * 1. Room 编译期 SQL 校验与 schema（`core/data/schemas/1.json` 应随构建生成）；
 * 2. PK 冲突 REPLACE 行为（hourly_metric 整窗覆盖）；
 * 3. 索引与查询计划（spotId / spotId+kind）。
 */
@Ignore("Room 需 Android 运行环境；M2 以 Fake DAO + 纯函数等价覆盖，真实 DAO 留 M3 真机验证")
class RoomDaoTest {

    @Test
    fun `收藏 CRUD_重启不丢`() {
        // 启用后：Room.inMemoryDatabaseBuilder(context, TideSpotDatabase::class.java)
        //   .allowMainThreadQueries().build()
        //   spotDao.insert / getById / update / delete 断言（F-2.2）
    }

    @Test
    fun `hourly_metric PK冲突REPLACE与TTL过期判定`() {
        // 启用后：同 PK(spotId,hourEpoch,field) 二次 insertAll 覆盖；
        // lastSuccess(fetchedAt) 与 Ttl.isFresh(WEATHER/MARINE) 边界断言（§7.3）
    }

    @Test
    fun `fetch_log 写入与按源查询`() {
        // 启用后：insert 多条（含 4xx/网络异常 status=null），lastSuccess 只取 httpStatus=200 最新一条
    }
}
