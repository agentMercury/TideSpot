package com.tidespot.core.data.mock

import com.tidespot.core.data.local.FetchLogDao
import com.tidespot.core.data.local.ReverseCacheDao
import com.tidespot.core.data.local.ReverseCacheEntity
import com.tidespot.core.data.local.WaterBodyCacheDao
import com.tidespot.core.data.local.WaterBodyCacheEntity
import com.tidespot.core.data.local.FetchLogEntity
import com.tidespot.core.data.local.HourlyMetricDao
import com.tidespot.core.data.local.HourlyMetricEntity
import com.tidespot.core.data.local.HourlyScoreDao
import com.tidespot.core.data.local.ManualObservationDao
import com.tidespot.core.data.local.ManualObservationEntity
import com.tidespot.core.data.local.ManualTideFitDao
import com.tidespot.core.data.local.ManualTideFitEntity
import com.tidespot.core.data.local.HourlyScoreEntity
import com.tidespot.core.data.local.SpotDao
import com.tidespot.core.data.local.SpotEntity

/**
 * 内存 Fake DAO（JVM 单测专用）：Room 真实实现需 Android 运行环境（Robolectric 未引入），
 * 接口化 DAO 使仓储 6 态测试可在纯 JVM 跑通；真实 Room DAO 验证留 M3（见 RoomDaoTest @Ignore）。
 */
class FakeSpotDao : SpotDao {
    val rows = LinkedHashMap<Long, SpotEntity>()

    override suspend fun insert(e: SpotEntity): Long {
        val id = if (e.id == 0L) (rows.size + 1).toLong() else e.id
        rows[id] = e.copy(id = id)
        return id
    }

    override suspend fun update(e: SpotEntity) {
        rows[e.id] = e
    }

    override suspend fun delete(e: SpotEntity) {
        rows.remove(e.id)
    }

    override suspend fun getById(id: Long): SpotEntity? = rows[id]

    override suspend fun getAll(): List<SpotEntity> = rows.values.toList()

    override suspend fun updateWaterBody(id: Long, waterBodyType: String, elevation: Double?, tzOffsetSec: Int?) {
        val old = rows[id] ?: return
        rows[id] = old.copy(
            waterBodyType = waterBodyType,
            elevation = elevation ?: old.elevation,     // D08（M5 客户裁决）：null = 保持原值，与 Room COALESCE 同语义
            tzOffsetSec = tzOffsetSec ?: old.tzOffsetSec,
        )
    }

    override suspend fun updateTzOffset(id: Long, tzOffsetSec: Int?) {
        val old = rows[id] ?: return
        rows[id] = old.copy(tzOffsetSec = tzOffsetSec)
    }
}

class FakeHourlyDao : HourlyMetricDao {
    val rows = mutableListOf<HourlyMetricEntity>()

    override suspend fun insertAll(list: List<HourlyMetricEntity>) {
        list.forEach { new ->
            rows.removeAll { old ->
                old.spotId == new.spotId && old.hourEpoch == new.hourEpoch && old.field == new.field
            }
            rows += new
        }
    }

    override suspend fun forSpot(spotId: Long): List<HourlyMetricEntity> =
        rows.filter { it.spotId == spotId }

    override suspend fun deleteKind(spotId: Long, kind: String) {
        rows.removeAll { it.spotId == spotId && it.kind == kind }
    }

    override suspend fun deleteForSpot(spotId: Long) {
        rows.removeAll { it.spotId == spotId }
    }
}

class FakeFetchLogDao : FetchLogDao {
    val rows = mutableListOf<FetchLogEntity>()
    private var seq = 0L

    override suspend fun insert(e: FetchLogEntity): Long {
        rows += e.copy(id = ++seq)
        return seq
    }

    override suspend fun insertAll(entries: List<FetchLogEntity>) {
        entries.forEach { insert(it) }
    }

    override suspend fun lastSuccess(spotId: Long, source: String): FetchLogEntity? =
        rows.filter { it.spotId == spotId && it.source == source && it.httpStatus == 200 }
            .maxByOrNull { it.fetchedAt }

    override suspend fun forSpot(spotId: Long): List<FetchLogEntity> =
        rows.filter { it.spotId == spotId }.sortedByDescending { it.fetchedAt }
}

// ────────────────────────────── M3 新增（CONTRACT M3 子契约） ──────────────────────────────

class FakeReverseCacheDao : ReverseCacheDao {
    val rows = LinkedHashMap<String, ReverseCacheEntity>()
    var upserts = 0

    override suspend fun upsert(e: ReverseCacheEntity) {
        upserts++
        rows[e.spotKey] = e
    }

    override suspend fun getByKey(spotKey: String): ReverseCacheEntity? = rows[spotKey]
}

class FakeWaterBodyCacheDao : WaterBodyCacheDao {
    val rows = LinkedHashMap<String, WaterBodyCacheEntity>()
    var upserts = 0

    override suspend fun upsert(e: WaterBodyCacheEntity) {
        upserts++
        rows[e.spotKey] = e
    }

    override suspend fun getByKey(spotKey: String): WaterBodyCacheEntity? = rows[spotKey]
}


/** M4 必修 8：hourly_score 内存 Fake（PK REPLACE 语义对齐 Room OnConflictStrategy.REPLACE）。 */
class FakeHourlyScoreDao : HourlyScoreDao {
    val rows = mutableListOf<HourlyScoreEntity>()

    override suspend fun insertAll(list: List<HourlyScoreEntity>) {
        list.forEach { new ->
            rows.removeAll { old ->
                old.spotId == new.spotId && old.hourEpoch == new.hourEpoch && old.algoVersion == new.algoVersion
            }
            rows += new
        }
    }

    override suspend fun forSpot(spotId: Long, algoVersion: Int): List<HourlyScoreEntity> =
        rows.filter { it.spotId == spotId && it.algoVersion == algoVersion }

    override suspend fun deleteForSpot(spotId: Long) {
        rows.removeAll { it.spotId == spotId }
    }
}

// ────────────────────────────── M6 新增（CONTRACT M6 子契约 / OPT-M5-02） ──────────────────────────────

/** 现场校潮观测内存 Fake（读回按观测时刻升序，对齐 Room @Query 口径）。 */
class FakeManualObservationDao : ManualObservationDao {
    val rows = mutableListOf<ManualObservationEntity>()
    private var seq = 0L

    override suspend fun insert(e: ManualObservationEntity): Long {
        val id = ++seq
        rows += e.copy(id = id)
        return id
    }

    override suspend fun forSpot(spotId: Long): List<ManualObservationEntity> =
        rows.filter { it.spotId == spotId }
            .sortedWith(compareBy({ it.observedAtEpochSec }, { it.id }))
}

/** 现场校潮拟合内存 Fake（spotId PK REPLACE 覆盖，对齐 Room 口径）。 */
class FakeManualTideFitDao : ManualTideFitDao {
    val rows = LinkedHashMap<Long, ManualTideFitEntity>()

    override suspend fun upsert(e: ManualTideFitEntity) {
        rows[e.spotId] = e
    }

    override suspend fun getBySpot(spotId: Long): ManualTideFitEntity? = rows[spotId]
}