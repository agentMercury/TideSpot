package com.tidespot.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * DAO 全 suspend（§7.1）。
 * 注意：接口化声明使仓储可在纯 JVM 单测中以内存 Fake 实现替换（Robolectric 未引入，M2 策略）。
 */
@Dao
interface SpotDao {

    @Insert
    suspend fun insert(e: SpotEntity): Long

    @Update
    suspend fun update(e: SpotEntity)

    @Delete
    suspend fun delete(e: SpotEntity)

    @Query("SELECT * FROM spot WHERE id = :id")
    suspend fun getById(id: Long): SpotEntity?

    @Query("SELECT * FROM spot ORDER BY lastViewedAt DESC, id DESC")
    suspend fun getAll(): List<SpotEntity>

    /**
     * CHG-09①：确认选点流水线的最小写入口（§6.9 判定结果 + elevation/tzOffsetSec 回写）。
     * 不动既有 CRUD；@Query 定向 UPDATE，不触发言义上的整行覆盖（不更新 lastViewedAt/updatedAt）。
     *
     * D08（M5，客户裁决 COALESCE）：elevation/tzOffsetSec 传 null = **保持原值**（COALESCE 回退列现值），
     * 非「置 NULL」——缺值 ≠ 清值（§5.0 null 语义）。
     */
    @Query(
        "UPDATE spot SET waterBodyType = :waterBodyType, " +
            "elevation = COALESCE(:elevation, elevation), " +
            "tzOffsetSec = COALESCE(:tzOffsetSec, tzOffsetSec) WHERE id = :id"
    )
    suspend fun updateWaterBody(id: Long, waterBodyType: String, elevation: Double?, tzOffsetSec: Int?)

    /**
     * FO-18（M4 必修 4）：刷新响应 IANA timezone / utc_offset_seconds → spot.tzOffsetSec 回写
     * （F-3.8：小时轴 = 选点当地墙上时间）。与 [updateWaterBody] 同风格：定向 UPDATE，不动其余列。
     */
    @Query("UPDATE spot SET tzOffsetSec = :tzOffsetSec WHERE id = :id")
    suspend fun updateTzOffset(id: Long, tzOffsetSec: Int?)

    // ── OPT-V11-12 / OPT-V11-16（P2/CHG-37）：批量操作支撑（单条删除/批量删除共用级联清理） ──

    /** 按 id 批量读（批量标签合并、分组校验输入）。 */
    @Query("SELECT * FROM spot WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SpotEntity>

    /** 批量删除收藏点行（**调用方负责事务**，见 `TideSpotRepository.deleteFavorites`）。 */
    @Query("DELETE FROM spot WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * OPT-V11-16：批量改分组。`:groupName` 传 null = 移回「未分组」（**禁空串**，写入侧归一）。
     * 定向 UPDATE，不触派生列（D07 patch 语义保持）。
     */
    @Query("UPDATE spot SET groupName = :groupName WHERE id IN (:ids)")
    suspend fun updateGroupName(ids: List<Long>, groupName: String?)

    /** OPT-V11-12：批量改标签（定向 UPDATE tagsJson，不冲 waterBodyType/elevation/时区等派生列）。 */
    @Query("UPDATE spot SET tagsJson = :tagsJson WHERE id = :id")
    suspend fun updateTagsJson(id: Long, tagsJson: String)
}

@Dao
interface HourlyMetricDao {

    /** 整窗替换写入（CHG-03：240h 一次入库；PK 冲突即覆盖旧小时值）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<HourlyMetricEntity>)

    @Query("SELECT * FROM hourly_metric WHERE spotId = :spotId")
    suspend fun forSpot(spotId: Long): List<HourlyMetricEntity>

    @Query("DELETE FROM hourly_metric WHERE spotId = :spotId AND kind = :kind")
    suspend fun deleteKind(spotId: Long, kind: String)

    @Query("DELETE FROM hourly_metric WHERE spotId = :spotId")
    suspend fun deleteForSpot(spotId: Long)

    /** OPT-V11-12（P2/CHG-37）：批量删除时级联清理逐小时指标缓存（事务由仓储包裹）。 */
    @Query("DELETE FROM hourly_metric WHERE spotId IN (:spotIds)")
    suspend fun deleteForSpots(spotIds: List<Long>)
}

@Dao
interface FetchLogDao {

    @Insert
    suspend fun insert(e: FetchLogEntity): Long

    @Insert
    suspend fun insertAll(entries: List<FetchLogEntity>)

    /** 最近一次成功拉取（httpStatus=200），TTL 判定基准（F-4.2 / §7.3）。 */
    @Query(
        "SELECT * FROM fetch_log WHERE spotId = :spotId AND source = :source " +
            "AND httpStatus = 200 ORDER BY fetchedAt DESC LIMIT 1"
    )
    suspend fun lastSuccess(spotId: Long, source: String): FetchLogEntity?

    @Query("SELECT * FROM fetch_log WHERE spotId = :spotId ORDER BY fetchedAt DESC")
    suspend fun forSpot(spotId: Long): List<FetchLogEntity>

    /** OPT-V11-12（P2/CHG-37）：删除收藏点时级联清理访问日志（事务由仓储包裹）。 */
    @Query("DELETE FROM fetch_log WHERE spotId IN (:spotIds)")
    suspend fun deleteForSpots(spotIds: List<Long>)
}

// ────────────────────────────── M3 新增（CONTRACT M3 子契约） ──────────────────────────────

@Dao
interface ReverseCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: ReverseCacheEntity)

    @Query("SELECT * FROM spot_reverse_cache WHERE spotKey = :spotKey")
    suspend fun getByKey(spotKey: String): ReverseCacheEntity?
}

@Dao
interface WaterBodyCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: WaterBodyCacheEntity)

    @Query("SELECT * FROM spot_water_body_cache WHERE spotKey = :spotKey")
    suspend fun getByKey(spotKey: String): WaterBodyCacheEntity?
}

// ────────────────────────────── M4 新增（CONTRACT M4 子契约 / §7.3） ──────────────────────────────

/**
 * 逐小时评分 DAO（§7.3 hourly_score；M4 必修 8）。整窗替换写入（CHG-03/N-2）。
 */
@Dao
interface HourlyScoreDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<HourlyScoreEntity>)

    @Query("SELECT * FROM hourly_score WHERE spotId = :spotId AND algoVersion = :algoVersion")
    suspend fun forSpot(spotId: Long, algoVersion: Int): List<HourlyScoreEntity>

    @Query("DELETE FROM hourly_score WHERE spotId = :spotId")
    suspend fun deleteForSpot(spotId: Long)

    /** OPT-V11-12（P2/CHG-37）：批量删除时级联清理评分缓存（事务由仓储包裹）。 */
    @Query("DELETE FROM hourly_score WHERE spotId IN (:spotIds)")
    suspend fun deleteForSpots(spotIds: List<Long>)
}

// ────────────────────────────── M6 新增（CONTRACT M6 子契约 / OPT-M5-02） ──────────────────────────────

/** 现场校潮观测 DAO（§6.2b 规则 5；观测只增不改，无删除入口 v1）。 */
@Dao
interface ManualObservationDao {

    @Insert
    suspend fun insert(e: ManualObservationEntity): Long

    /** 按观测时刻升序（fitManual 与 D12 对照的确定性口径）。 */
    @Query("SELECT * FROM manual_observation WHERE spotId = :spotId ORDER BY observedAtEpochSec ASC, id ASC")
    suspend fun forSpot(spotId: Long): List<ManualObservationEntity>

    /**
     * OPT-V11-02（CHG-27）：按类型查询（校潮拟合只取 TIDE_OBSERVATION，水质实测录入取 WATER_TEMP/DO/TURBIDITY——
     * 两类观测不得混入彼此计算）。
     */
    @Query(
        "SELECT * FROM manual_observation WHERE spotId = :spotId AND kind = :kind " +
            "ORDER BY observedAtEpochSec ASC, id ASC",
    )
    suspend fun forSpotKind(spotId: Long, kind: String): List<ManualObservationEntity>

    /** OPT-V11-12（P2/CHG-37）：删除收藏点时级联清理观测记录（含校潮与水质实测）。 */
    @Query("DELETE FROM manual_observation WHERE spotId IN (:spotIds)")
    suspend fun deleteForSpots(spotIds: List<Long>)
}

/** 现场校潮拟合参数 DAO（每点一行，REPLACE 覆盖式登记；adopted=false 也登记）。 */
@Dao
interface ManualTideFitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: ManualTideFitEntity)

    @Query("SELECT * FROM manual_tide_fit WHERE spotId = :spotId")
    suspend fun getBySpot(spotId: Long): ManualTideFitEntity?

    /** OPT-V11-12（P2/CHG-37）：删除收藏点时级联清理拟合参数。 */
    @Query("DELETE FROM manual_tide_fit WHERE spotId IN (:spotIds)")
    suspend fun deleteForSpots(spotIds: List<Long>)
}