package com.tidespot.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tidespot.core.data.repo.DataSource

/**
 * Room 实体（§7.3 数据模型）。
 * null 语义（CONTRACT 冻结点 4）：`value` 为 NULL 表示源该小时缺值，**禁止 0 填充**（§5.0）。
 */

/** 收藏点（F-2 / §7.3 spot 表）。tags 以 JSON 数组串存储。 */
@Entity(tableName = "spot")
data class SpotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lat: Double,
    val lon: Double,
    val name: String,
    /** 海拔 m（§6.9 辅助判据；Open-Meteo elevation）。 */
    val elevation: Double? = null,
    /** [com.tidespot.core.domain.model.WaterBodyType].name（§6.9 判定输出，M3 写入）。 */
    val waterBodyType: String = "LAND",
    /** 时区偏移秒（timezone=auto 返回，F-3.8）。 */
    val tzOffsetSec: Int? = null,
    val favorite: Boolean = true,
    /** F-2.1 可选备注（domain `memo`）。 */
    val note: String? = null,
    /** 场景预设：淡水路亚/海水岸边/淡水台钓（domain `preset`，§7.3 groupPreset）。 */
    val groupPreset: String? = null,
    val speciesId: String? = null,
    /** JSON 数组串，如 `["礁区","夜钓"]`。 */
    val tagsJson: String = "[]",
    val lastViewedAt: Long = 0,
    val updatedAt: Long = 0,
    /** 创建时刻（epoch 秒，domain `createdAtEpochSec`）。 */
    val createdAtEpochSec: Long = 0,
)

/**
 * 逐小时指标缓存（§7.3 hourly_metric，PK(spotId, hourEpoch, field)）。
 * 整窗 240h 一次入库（CHG-03）；`kind` = [DataSource.WEATHER]/[DataSource.MARINE].wire。
 */
@Entity(
    tableName = "hourly_metric",
    primaryKeys = ["spotId", "hourEpoch", "field"],
    indices = [Index("spotId"), Index("spotId", "kind")],
)
data class HourlyMetricEntity(
    val spotId: Long,
    /** epoch 秒（N-9：比较与排序一律 epoch）。 */
    val hourEpoch: Long,
    /** 当地墙上时间串（F-3.8，如 `2026-11-02T17:00`）。 */
    val hourLocal: String?,
    val kind: String,
    /** 源字段名（如 `temperature_2m` / `wave_height`）。 */
    val field: String,
    /** NULL = 源缺值，禁止 0 填充（§5.0）。 */
    val value: Double?,
    /** 数据来源标识（如 `open-meteo`）。 */
    val source: String,
    /** 拉取时刻（epoch 毫秒，TTL 判定 §7.3）。 */
    val fetchedAt: Long,
)

/** 网络访问日志（§7.3 fetch_log / F-4.6 频控可视化 / N-6 设置页可查）。 */
@Entity(tableName = "fetch_log", indices = [Index("spotId"), Index("spotId", "source")])
data class FetchLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spotId: Long,
    /** weather | marine（§7.3 TTL 分源）。 */
    val source: String,
    val urlHash: Int,
    /** HTTP 状态码；网络异常（未达 HTTP 层）为 NULL。 */
    val httpStatus: Int?,
    val bytes: Long?,
    val costMs: Long,
    /** epoch 毫秒；同时是 TTL 判定基准。 */
    val fetchedAt: Long,
)

// ────────────────────────────── M3 新增（CONTRACT M3 子契约） ──────────────────────────────

/**
 * 逆地理缓存（§7.3 spot_reverse_cache；键 = 0.005° 格点，TTL 30 d，§5.8）。
 */
@Entity(tableName = "spot_reverse_cache")
data class ReverseCacheEntity(
    /** 形如 `"29.55,118.95"`（[CacheKeys.spotKey]）。 */
    @PrimaryKey val spotKey: String,
    /** 信息条主标题（已过 T2S 归一）。 */
    val displayName: String,
    /** 市/省 或海区名（domain `NamedPlace.subLabel`）。 */
    val subLabel: String?,
    val plusCode: String?,
    val locality: String?,
    val city: String?,
    val admin1: String?,
    /** [com.tidespot.core.domain.model.ReverseGeoSource].name。 */
    val source: String,
    /** epoch 毫秒（TTL 判定基准）。 */
    val resolvedAtEpochMs: Long,
)

/** 水体判定缓存（§6.9：30 d/点；键 = 0.005° 格点）。 */
@Entity(tableName = "spot_water_body_cache")
data class WaterBodyCacheEntity(
    /** 形如 `"29.55,118.95"`（[CacheKeys.spotKey]）。 */
    @PrimaryKey val spotKey: String,
    /** [com.tidespot.core.domain.model.WaterBodyType].name。 */
    val waterBodyType: String,
    /** epoch 毫秒（TTL 判定基准）。 */
    val judgedAtEpochMs: Long,
)

// ────────────────────────────── M4 新增（CONTRACT M4 子契约 / §7.3） ──────────────────────────────

/**
 * 逐小时钓鱼指数（§7.3 hourly_score；M4 必修 8）。
 * PK(spotId, hourEpoch, algoVersion)——N-10：算法版本升级后新旧并存、可整体重算。
 * CHG-08 ④：领域 vetoReasons 为非空 List（无理由 = 空列表，isEmpty() 判定）；SQL 列可空
 * 为惯例——落库 **空列表 → NULL**，读回映射为空列表（映射见 [com.tidespot.core.data.repo.HourlyScoreStore]）。
 */
@Entity(
    tableName = "hourly_score",
    primaryKeys = ["spotId", "hourEpoch", "algoVersion"],
    indices = [Index("spotId")],
)
data class HourlyScoreEntity(
    val spotId: Long,
    /** epoch 秒（N-9：比较与排序一律 epoch）。 */
    val hourEpoch: Long,
    /** 当地墙上时间串（F-3.8，如 `2026-11-02T17:00`）。 */
    val hourLocal: String?,
    /** 钓鱼指数总分 0–100（REAL，§7.3；内部浮点排序、显示取整由 UI 承担）。 */
    val score: Double,
    /** §6.3 档位文案：爆护窗口/很适合/可以/较差/别去。 */
    val band: String,
    /** CHG-08 ④：无否决 = NULL（领域空列表映射）；非空 = JSON 数组串。 */
    val vetoReason: String?,
    /** F-3.4 FactorScore JSON 数组串。 */
    val factorsJson: String,
    /** N-10 / FO-09：算法版本（[com.tidespot.core.domain.FishScoreCalculator.ALGO_VERSION]）。 */
    val algoVersion: Int,
    /**
     * M6（CHG-16①/OPT-M5-04）：机器可读否决码列（VetoCode.name 的 JSON 数组串，如
     * `["THUNDERSTORM"]`）。v3→v4 尾增列，**NOT NULL DEFAULT '[]'**——空数组语义与
     * [vetoReason] NULL 对称（读回空列表；CHG-08 ④ 空列表口径）。
     * defaultValue 声明必须与 MIGRATION_3_4 的 ALTER TABLE ... DEFAULT '[]' 一致，
     * 否则真机 Room schema 校验失败。
     */
    @ColumnInfo(defaultValue = "[]") val vetoCodes: String = "[]",
)

// ────────────────────────────── M6 新增（CONTRACT M6 子契约 / §6.2b 规则 5） ──────────────────────────────

/**
 * 现场校潮观测记录（OPT-M5-02 应用闭环 / §6.2b 规则 5）。
 * 用户在潮 Tab 记录高低潮时刻；≥2 条且 [com.tidespot.core.domain.tide.TideCalibrator.fitManual]
 * 拟合通过才启用校正（adopted=false 的 fit 也登记，D12 校正前后对照需要）。
 */
@Entity(
    tableName = "manual_observation",
    indices = [Index("spotId"), Index("spotId", "observedAtEpochSec")],
)
data class ManualObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spotId: Long,
    /** 用户记录的高低潮时刻（epoch 秒，N-9 排序口径）。 */
    val observedAtEpochSec: Long,
    /** 记录时刻（epoch 毫秒）。 */
    val createdAtEpochMs: Long,
)

/**
 * 现场校潮拟合参数（每点一行，PK=spotId 覆盖式更新）。
 * [adopted]=false 也登记（D12 对照与「再记 N 条」文案回放需要，CHG-16⑦）。
 */
@Entity(tableName = "manual_tide_fit")
data class ManualTideFitEntity(
    @PrimaryKey val spotId: Long,
    /** lag（小时）：M'(h)=gain·M(h−lag)+offset。 */
    val lagHours: Int,
    /** 垂直偏移（m）；现场校潮无水位观测 → 固定 0。 */
    val offsetM: Double,
    /** 增益；现场校潮固定 1.0（SRS §6.2b 规则 5）。 */
    val gain: Double,
    /** 是否启用校正（false = 仅登记，评分不应用）。 */
    val adopted: Boolean,
    /** 写入时刻（epoch 毫秒）。 */
    val updatedAtEpochMs: Long,
)