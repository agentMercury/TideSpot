package com.tidespot.core.data.repo

import androidx.room.withTransaction
import com.tidespot.core.data.DataError
import com.tidespot.core.data.local.TideSpotDatabase
import com.tidespot.core.data.dto.WeatherDailyDto
import com.tidespot.core.data.local.FetchLogDao
import com.tidespot.core.data.local.ManualObservationDao
import com.tidespot.core.data.local.ManualObservationEntity
import com.tidespot.core.data.local.ManualTideFitDao
import com.tidespot.core.data.local.ManualTideFitEntity
import com.tidespot.core.data.local.FetchLogEntity
import com.tidespot.core.data.local.HourlyMetricDao
import com.tidespot.core.data.local.HourlyScoreDao
import com.tidespot.core.data.local.SpotDao
import com.tidespot.core.domain.FishScoreCalculator
import com.tidespot.core.domain.model.BestWindow
import com.tidespot.core.domain.model.Confidence
import com.tidespot.core.domain.model.FactorScore
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.model.VetoCode
import com.tidespot.core.domain.tide.TideCalibrator
import com.tidespot.core.domain.window.BestWindowFinder
import com.tidespot.core.data.remote.FetchLogEntry
import com.tidespot.core.data.remote.MinRefreshGate
import com.tidespot.core.data.remote.OpenMeteoClient
import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather
import com.tidespot.core.domain.model.WaterBodyType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 详情页刷新结果（对 :app/:feature 门面输出，全部为 domain 冻结模型）。
 *
 * 降级语义（F-3.6 分维降级 / §6.9 / 附录 B8）：
 * - [weatherDegraded]：天气维度本次拉取失败（已回退旧缓存或为空）；
 * - [marineDegraded]：海洋维度失败 **或** 内陆点 200+全 null（水体降级触发条件）；
 * - [marineAllNullInland]：区分「请求失败」与「内陆无海况」两种降级成因。
 */
data class SpotDetailResult(
    val spotId: Long,
    /** 天气+海洋按时间戳 join 的并集切片（§5.0，禁止下标对齐）。 */
    val slices: List<HourSlice>,
    val weather: List<HourlyWeather>,
    val marine: List<HourlyMarine>,
    /** §5.1 daily 块（日出日落等，未建独立表，随响应透出）。 */
    val daily: WeatherDailyDto?,
    val weatherDegraded: Boolean,
    val marineDegraded: Boolean,
    val marineAllNullInland: Boolean,
    /** 分维错误（B9 结构化错误在此透出供 UI 呈现）。 */
    val errors: Map<DataSource, DataError>,
    /** 本次哪些维度的数据实际来自缓存（重试失败保留旧数据 / TTL 命中 0 请求）。 */
    val servedFromCache: Set<DataSource>,
    /** 各源最近一次成功拉取时刻（epoch 毫秒，UI「数据时间」文案）。 */
    val fetchedAtMillis: Map<DataSource, Long>,
    /**
     * M6（CHG-16⑦/OPT-M5-02）：现场校潮应用摘要；null = 未应用（无 fit / 未 adopted / 内陆点）。
     * [marine] 恒为原始未校正序列（UI 对照基准）；评分与 dh 派生管线用的校正序列只进
     * hourly_score 落库（读侧重建天然一致，无双口径）。
     */
    val tideCorrection: TideCorrectionSummary? = null,
    /**
     * CHG-35（水体判定修复）：自愈后的真实水体类型——仅当详情已确认「海洋有值」而 spot 仍是
     * 历史误判（`INLAND_WATER` / `UNKNOWN`）时非 null（= `NEARSHORE`，并已回写 spot 表）。
     * UI 取 `state.result.effectiveWaterBody ?: spot.waterBody` 作为标题/chip 的类型来源。
     */
    val effectiveWaterBody: WaterBodyType? = null,
)

/**
 * 对外 Repository 门面（CONTRACT.md：feature:* → :core:data → :core:domain）。
 *
 * 职责：TTL（§7.3 天气 3h / 海洋 6h）+ 单点最小刷新 15 min（§12）+ 分维并行拉取（≤4，F-4.1）
 * + 整窗入库（CHG-03）+ 按时间戳 join（§5.0）+ 内陆降级（§6.9/B8）+ fetch_log（F-4.6）
 * + 收藏 CRUD（F-2）。
 *
 * 水质维度（§5.3）：M0 探针 A6/A7 FAIL（无 Copernicus 凭据）→ **不发任何网络请求**，
 * 由 :core:domain 按 §6.1.2/§6.2a/§6.2j 从天气序列本地推导（徽标「估算」）。
 *
 * DAO 以接口注入：生产绑 Room 实现，JVM 单测用内存 Fake（Robolectric 未引入的 M2 策略）。
 */
class TideSpotRepository(
    private val client: OpenMeteoClient,
    private val spotDao: SpotDao,
    private val hourlyDao: HourlyMetricDao,
    private val fetchLogDao: FetchLogDao,
    private val refreshGate: MinRefreshGate = MinRefreshGate(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    /**
     * M4 必修 8：hourly_score DAO。尾部默认参数追加（既有调用点零改动）；
     * null = 不落评分（既有 JVM 测试路径），生产由 :app 注入 Room 实现。
     */
    private val hourlyScoreDao: HourlyScoreDao? = null,
    /**
     * M6（CHG-16⑦/OPT-M5-02）：现场校潮观测 DAO；null = 未注入（既有 JVM 测试路径——
     * 写方法抛 IllegalStateException 防静默丢观测，读方法返回空/未登记；生产由 :app 批次 3 注入）。
     */
    private val manualObservationDao: ManualObservationDao? = null,
    /** M6：现场校潮拟合 DAO；null 语义同上（刷新/缓存路径视为「无 fit」，行为与未应用校正一致）。 */
    private val manualTideFitDao: ManualTideFitDao? = null,
    /**
     * D13（M6/CHG-16⑧）：评分计算调度器。默认 Dispatchers.Default（CPU 密集型评分不再占用
     * 调用方上下文）；TTL 命中路径与刷新路径对同一输入只算一次全量评分（单点最小刷新：
     * 仅 persistHourlyScores 一处计算入口，cached 路径零计算）。
     */
    private val scoringDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * OPT-V11-12（P2/CHG-37）：Room 实例（批量删除/批量更新的**单事务**入口）。
     * null = 未注入（JVM 测试路径 → 顺序执行无事务，行为等价）；生产由 `:app` 注入。
     */
    private val db: TideSpotDatabase? = null,
) {

    private val scoreCalculator = FishScoreCalculator()

    /** factorsJson 解析器（与 HourlyScoreStore 组装侧同配置；ignoreUnknownKeys 容错）。 */
    private val factorJsonParser = Json { ignoreUnknownKeys = true }

    // ────────────────────────────── 详情页数据流 ──────────────────────────────

    /**
     * 刷新详情页（F-2.3：先渲染缓存，后台刷新；F-4.2：TTL 内 0 网络请求）。
     *
     * @param force 跳过 TTL 判定强制刷新（UI 下拉刷新）。
     */
    suspend fun refreshSpotDetail(spot: FavoriteSpot, force: Boolean = false): SpotDetailResult {
        val now = nowMillis()
        val lastWeather = fetchLogDao.lastSuccess(spot.id, DataSource.WEATHER.wire)
        val lastMarine = fetchLogDao.lastSuccess(spot.id, DataSource.MARINE.wire)
        val weatherFresh = !force && Ttl.isFresh(DataSource.WEATHER, lastWeather?.fetchedAt ?: 0L, now)
        val marineFresh = !force && Ttl.isFresh(DataSource.MARINE, lastMarine?.fetchedAt ?: 0L, now)
        val hasAnyData = lastWeather != null || lastMarine != null

        // §12 单点最小刷新 15 min：TTL 命中 → 直接回缓存，0 请求（不触碰 gate，不占并发名额）
        if (weatherFresh && marineFresh) {
            return cachedDetailInternal(spot, now)
        }
        //
        // FO-17 三修（M4 必修 3）：
        // (a) force 绕过 15min gate（下拉刷新立即生效；旧实现只跳 TTL，gate 仍拦截）；
        // (c) gate 线程安全 + tryBegin 原子「校验+占用」：同点并发刷新仅放行一个（无重复请求）；
        //     且仅本轮全部发起维度成功后 finishSuccess 记入 15min 窗口——失败 finishFailure
        //     不占窗，可立即重试（旧实现在网络请求前就 record）。
        val gateGranted = force || refreshGate.tryBegin(gateKey(spot.id), now)
        if (!gateGranted) {
            return cachedDetailInternal(spot, now)
        }

        // F-4.1：分维并行；单维失败不影响另一维（F-3.6）；失败方回退旧缓存（F-4.5 重试期保留旧数据）
        val weatherPart: WeatherPart?
        val marinePart: MarinePart?
        coroutineScope {
            val wJob = if (!weatherFresh) async { fetchWeatherPart(spot, now) } else null
            val mJob = if (!marineFresh) async { fetchMarinePart(spot, now) } else null
            weatherPart = wJob?.await()
            marinePart = mJob?.await()
        }

        var weather = weatherPart?.data
        var marine = marinePart?.data
        val servedFromCache = mutableSetOf<DataSource>()

        if (weather != null) {
            hourlyDao.deleteKind(spot.id, DataSource.WEATHER.wire)
            hourlyDao.insertAll(DtoMappers.weatherEntities(spot.id, weatherPart!!.dto!!, now))
        } else {
            weather = EntityMappers.toHourlyWeather(hourlyDao.forSpot(spot.id))
            if (weatherPart != null) servedFromCache += DataSource.WEATHER // 拉取失败→旧缓存
        }
        if (marine != null) {
            hourlyDao.deleteKind(spot.id, DataSource.MARINE.wire)
            hourlyDao.insertAll(DtoMappers.marineEntities(spot.id, marinePart!!.dto!!, now))
        } else {
            marine = EntityMappers.toHourlyMarine(hourlyDao.forSpot(spot.id))
            if (marinePart != null) servedFromCache += DataSource.MARINE
        }

        // §5.2/附录 B8：海洋 200+全 null → 内陆无海况，上层触发水体降级（禁止显示"浪高 0 m"）
        val marineAllNull = marine!!.isNotEmpty() && marine.all { it.isAllNull }

        // FO-17 (c)：仅本轮所有发起维度成功才记录 gate；任一失败 → 不占 15min 窗口，可立即重试
        val roundSucceeded = weatherPart?.error == null && marinePart?.error == null
        if (roundSucceeded) {
            refreshGate.finishSuccess(gateKey(spot.id), nowMillis())
        } else {
            refreshGate.finishFailure(gateKey(spot.id))
        }

        // FO-18（M4 必修 4）：响应 IANA timezone / utc_offset_seconds → spot.tzOffsetSec 回写
        // （F-3.8：小时轴 = 选点当地墙上时间）。响应缺 tz 时保留已存值；两者皆无 → 不写。
        val spotRow = spotDao.getById(spot.id)
        val tzOffsetSec = HourlyScoreStore.resolveTzOffsetSec(weatherPart?.dto, marinePart?.dto)
            ?: spotRow?.tzOffsetSec
        if (tzOffsetSec != null && spotRow != null && spotRow.tzOffsetSec != tzOffsetSec) {
            spotDao.updateTzOffset(spot.id, tzOffsetSec)
        }

        // OPT-M5-02 应用闭环（CHG-16⑦）：海洋点且命中 adopted fit → 评分与 dh 派生管线用校正序列
        // M'(h)=gain·M(h−lag)+offset（复用 TideCalibrator.apply，现场校潮 gain=1.0）；
        // SpotDetailResult.marine 保持原始未校正序列（UI 对照基准），校正只进 hourly_score 落库。
        val fitEntity = manualTideFitDao?.getBySpot(spot.id)
        val marineAllNullCheck = marine!!.isNotEmpty() && marine.all { it.isAllNull }
        val isMarineSpot = marine.isNotEmpty() && !marineAllNullCheck
        val tideCorrection = fitEntity
            ?.takeIf { it.adopted && isMarineSpot }
            ?.let { TideCorrectionSummary(it.lagHours, it.offsetM, it.gain, it.adopted) }
        // CHG-35 存量自愈：海洋已确认有值，但 spot 仍是历史误判（OSTIA SST 当湖泊掩膜 → INLAND/UNKNOWN）
        // → 回写 NEARSHORE；本结果透出 effectiveWaterBody 供首屏立即纠正标题与潮汛 chip。
        val currentWaterBody = spotRow?.waterBodyType
            ?.let { runCatching { WaterBodyType.valueOf(it) }.getOrNull() }
        val healedType = if (
            isMarineSpot &&
            (currentWaterBody == WaterBodyType.INLAND_WATER || currentWaterBody == WaterBodyType.UNKNOWN)
        ) {
            WaterBodyType.NEARSHORE
        } else {
            null
        }
        if (healedType != null) {
            spotDao.updateWaterBody(spot.id, healedType.name, null, tzOffsetSec)
        }
        val marineForScoring = fitEntity
            ?.takeIf { it.adopted }
            ?.let { fit -> applyManualTideCorrection(marine!!, TideCalibrator.ManualTideFit(fit.lagHours, fit.offsetM, fit.gain, fit.adopted, null)) }
            ?: marine

        // M4 必修 8（§7.3）：评分完成后 hourly_score 整窗一次性落库（评分输入含 past 7d 前置小时
        // 供 Tw 滞后窗口/ΔP24；delete-then-insert 一次性替换，CHG-03/N-2）。
        persistHourlyScores(spot, weather!!, marineForScoring, weatherPart?.daily, tzOffsetSec)

        return SpotDetailResult(
            spotId = spot.id,
            slices = SeriesJoiner.join(weather!!, marine!!),
            weather = weather,
            marine = marine,
            daily = weatherPart?.daily,
            weatherDegraded = weatherPart?.error != null,
            marineDegraded = marinePart?.error != null || marineAllNull,
            marineAllNullInland = marineAllNull,
            errors = buildMap {
                weatherPart?.error?.let { put(DataSource.WEATHER, it) }
                marinePart?.error?.let { put(DataSource.MARINE, it) }
            },
            servedFromCache = servedFromCache,
            fetchedAtMillis = mapOf(
                // FO-17 (b)：重查 lastSuccess——本轮成功 → 取本次成功时刻（禁回填 1970）；
                // 本轮失败 → 保留上一轮成功时刻；从未成功 → 0（无数据时间语义，非回填）
                DataSource.WEATHER to (fetchLogDao.lastSuccess(spot.id, DataSource.WEATHER.wire)?.fetchedAt ?: 0L),
                DataSource.MARINE to (fetchLogDao.lastSuccess(spot.id, DataSource.MARINE.wire)?.fetchedAt ?: 0L),
            ),
            tideCorrection = tideCorrection,
            effectiveWaterBody = healedType,
        )
    }

    /** 纯缓存读取（C7 离线可读 / F-2.3 先渲染缓存），不发起任何网络请求。 */
    suspend fun cachedDetail(spot: FavoriteSpot): SpotDetailResult =
        cachedDetailInternal(spot, nowMillis())

    // ────────────────────────────── 收藏 CRUD（F-2） ──────────────────────────────

    suspend fun upsertFavorite(favorite: FavoriteSpot): Long {
        val entity = favorite.toEntity(nowEpochSec = nowMillis() / 1000)
        return if (favorite.id > 0) {
            // D07 patch 语义（M5）：toEntity 不携带派生列（elevation/tzOffsetSec/speciesId/lastViewedAt）
            // 与 DB 管理的 createdAtEpochSec——@Update 全列覆盖前先读旧实体 copy 合并，防「编辑名称」冲掉。
            // waterBodyType 同为派生列（§6.9 判定结果经 updateSpotWaterBody 回写；domain 侧 waterBody
            // 是过期默认值 LAND），编辑时必须保持库内判定结果，不得回退。
            val old = spotDao.getById(favorite.id)
            val merged = if (old != null) entity.copy(
                waterBodyType = old.waterBodyType,
                elevation = old.elevation,
                tzOffsetSec = old.tzOffsetSec,
                speciesId = old.speciesId,
                lastViewedAt = old.lastViewedAt,
                createdAtEpochSec = if (old.createdAtEpochSec > 0) old.createdAtEpochSec else entity.createdAtEpochSec,
            ) else entity
            spotDao.update(merged)
            favorite.id
        } else {
            spotDao.insert(entity)
        }
    }

    /**
     * CHG-09①：确认选点流水线写入口——水体判定结果 + elevation/tzOffsetSec 一并回写 spot 表
     * （§6.9 / F-3.8；M6 消费 SpotConfirmed 后调用）。elevation/tzOffsetSec 传 null = 保持原值
     * （D08/M5 客户裁决 COALESCE；SQL 只覆盖三列，其余字段不受影响）。
     */
    suspend fun updateSpotWaterBody(
        spotId: Long,
        waterBodyType: WaterBodyType,
        elevation: Double?,
        tzOffsetSec: Int?,
    ) {
        spotDao.updateWaterBody(spotId, waterBodyType.name, elevation, tzOffsetSec)
    }

    suspend fun deleteFavorite(id: Long) {
        deleteFavorites(listOf(id))
    }

    /**
     * OPT-V11-12（P2/CHG-37）：批量删除收藏点（**单事务**；任一表失败整体回滚，验收 GWT
     * 「删除中杀进程 → 重启后无半删状态」由此保证）。
     *
     * 级联清理口径（客户裁定 D19）：`spot` + `hourly_score` + `hourly_metric` + `manual_observation`
     * + `manual_tide_fit` 5 张表 + `fetch_log` 访问日志——旧版 [deleteFavorite] 只删前两张，
     * 遗留孤儿行（既有缺陷同批修正，单条删除复用本方法）。
     *
     * [db] 未注入（JVM 测试路径）时退化为顺序执行（无事务），生产路径由 `:app` 注入 Room 实例。
     */
    suspend fun deleteFavorites(ids: List<Long>) {
        if (ids.isEmpty()) return
        runAtomic {
            hourlyDao.deleteForSpots(ids)
            hourlyScoreDao?.deleteForSpots(ids)
            fetchLogDao.deleteForSpots(ids)
            manualObservationDao?.deleteForSpots(ids)
            manualTideFitDao?.deleteForSpots(ids)
            spotDao.deleteByIds(ids)
        }
    }

    /**
     * OPT-V11-16（P2/CHG-37）：批量改分组。空白/空串归一为 null = 移回「未分组」（禁空串语义）。
     * 定向 UPDATE 仅触 `groupName` 列（D07 patch 语义：派生列与备注/标签不动）。
     */
    suspend fun updateFavoritesGroup(ids: List<Long>, groupName: String?) {
        if (ids.isEmpty()) return
        val normalized = groupName?.trim()?.ifEmpty { null }
        runAtomic { spotDao.updateGroupName(ids, normalized) }
    }

    /**
     * OPT-V11-12（P2/CHG-37）：批量写标签（**按点给出最终标签**——追加/替换的合并语义由
     * `:feature:favorites` 纯函数 `FavoritesBatchLogic.mergeTags` 决定，数据层只负责落库）。
     * 定向 UPDATE `tagsJson`，不触派生列（D07 patch 语义）。
     */
    suspend fun updateFavoritesTags(tagsBySpotId: Map<Long, List<String>>) {
        if (tagsBySpotId.isEmpty()) return
        runAtomic {
            tagsBySpotId.forEach { (spotId, tags) ->
                spotDao.updateTagsJson(spotId, tagsToJson(tags))
            }
        }
    }

    /** 单事务包装：生产路径（注入 [db]）走 Room `withTransaction`；JVM 测试路径顺序执行。 */
    private suspend fun runAtomic(block: suspend () -> Unit) {
        val database = db
        if (database != null) database.withTransaction { block() } else block()
    }

    suspend fun favorites(): List<FavoriteSpot> = spotDao.getAll().map { it.toDomain() }

    suspend fun markViewed(spotId: Long) {
        spotDao.getById(spotId)?.let {
            spotDao.update(it.copy(lastViewedAt = nowMillis() / 1000))
        }
    }

    /** fetch_log 可查（F-4.6 / N-6 设置页频控可视化）。 */
    suspend fun fetchLogs(spotId: Long): List<FetchLogEntity> = fetchLogDao.forSpot(spotId)

    /** OPT-A-06：设置页「请求日志」——全局最近 N 条（跨点位倒序；只读，含 429 频控命中）。 */
    suspend fun recentFetchLogs(limit: Int = 50): List<FetchLogEntity> = fetchLogDao.recent(limit)

    /** OPT-A-06：清空本地访问日志（仅删日志行；业务缓存/TTL 不再有日志基准，下次进入按需重拉）。 */
    suspend fun clearFetchLogs() = fetchLogDao.deleteAll()

    /**
     * OPT-V11-11（P1）：逐源最近一次成功拉取时刻（epoch 毫秒；0 = 无记录）。
     *
     * 收藏列表「数据时间 + 陈旧/已过期」判定输入；TTL 判定仍只走 [Ttl]（禁另造阈值）。
     */
    suspend fun lastFetchedMillis(spotId: Long): Map<DataSource, Long> =
        DataSource.entries.associateWith { src ->
            fetchLogDao.lastSuccess(spotId, src.wire)?.fetchedAt ?: 0L
        }

    /**
     * CHG-35（水体判定修复，SRS §6.9 主判据「海洋字段是否非 null」）：
     * 轻量探测某坐标**是否有海况**，供确认选点时的水体判定使用。
     *
     * - 200 且任一小时 `sea_level_height_msl` / `wave_height` 非 null → `true`（海洋/近岸）；
     * - 200 但全 null → `false`（内陆/陆地）；
     * - 4xx（源对该坐标无数据，常见于内陆/陆地）→ `false`（同样表示「无海况」，不是失败）；
     * - 429 / 5xx / 网络异常 / 其它异常 → `null`（未知，调用方走 UNKNOWN 降级，禁编造）。
     *
     * 频控：复用 [OpenMeteoClient] 全局 6 req/min 令牌桶与 fetch_log；每点判定结果另由
     * [WaterBodyClassifier] 缓存 30 d（本轮升级 v2 键，旧错误判定失效）。
     */
    suspend fun probeMarineHasValue(lat: Double, lon: Double): Boolean? = try {
        val dto = client.fetchMarine(lat, lon)
        val series = DtoMappers.toHourlyMarineList(dto)
        series.any { it.seaLevelHeightMslM != null || it.waveHeightM != null }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: com.tidespot.core.data.DataError) {
        when (e) {
            // 源明确「该坐标无海况」：4xx（BadRequest / SourceParamError）→ false；其余（429/5xx/网络）→ 未知
            is com.tidespot.core.data.DataError.BadRequest,
            is com.tidespot.core.data.DataError.SourceParamError -> false
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    // ────────────────────── 现场校潮（M6/OPT-M5-02，§6.2b 规则 5） ──────────────────────

    /**
     * 登记一条现场校潮观测（高低潮/涨落时刻）。
     * OPT-V11-07（P1，尾部只增）：[type] = 四类观测；null = 历史无类型（`TIDE_OBSERVATION`）。
     * [manualObservationDao] 未注入 → 抛 [IllegalStateException]（防静默丢观测）。
     */
    suspend fun addTideObservation(
        spotId: Long,
        observedAtEpochSec: Long,
        type: com.tidespot.core.domain.tide.TideObservationType? = null,
    ) {
        val dao = manualObservationDao ?: throw IllegalStateException(
            "manualObservationDao 未注入（生产由 :app 提供 Room 实现；观测不可静默丢弃）",
        )
        dao.insert(
            ManualObservationEntity(
                spotId = spotId,
                observedAtEpochSec = observedAtEpochSec,
                createdAtEpochMs = nowMillis(),
                kind = type?.name ?: ManualObservationEntity.KIND_TIDE_OBSERVATION,
            ),
        )
    }

    /**
     * OPT-V11-02（CHG-27；尾部只增）：水质实测值录入（水温/溶氧/浊度）。
     * 离线可录入（纯本地落库）；[kind] 区分类型，校潮拟合只读 `TIDE_OBSERVATION`（不混算）。
     *
     * @param value 实测值（水温 ℃ / 溶氧 mg/L / 浊度 NTU；范围校验由 UI 层 [WaterQualityEntryLogic] 负责）
     */
    suspend fun addWaterQualityObservation(
        spotId: Long,
        kind: WaterQualityObservationKind,
        value: Double,
        observedAtEpochSec: Long,
    ) {
        val dao = manualObservationDao ?: throw IllegalStateException(
            "manualObservationDao 未注入（生产由 :app 提供 Room 实现；观测不可静默丢弃）",
        )
        dao.insert(
            ManualObservationEntity(
                spotId = spotId,
                observedAtEpochSec = observedAtEpochSec,
                createdAtEpochMs = nowMillis(),
                kind = kind.name,
                value = value,
            ),
        )
    }

    /**
     * OPT-V11-02：水质实测读回（按观测时刻升序；校潮观测不混入）。
     * [manualObservationDao] 未注入 → 空列表（未登记语义）。
     */
    suspend fun waterQualityObservations(spotId: Long): List<WaterQualityObservation> =
        manualObservationDao?.forSpot(spotId)
            ?.filter { !isTideObservationKind(it.kind) }
            ?.map {
                WaterQualityObservation(
                    id = it.id,
                    spotId = it.spotId,
                    kind = it.kind,
                    value = it.value,
                    observedAtEpochSec = it.observedAtEpochSec,
                    createdAtEpochMs = it.createdAtEpochMs,
                )
            }
            ?: emptyList()

    /**
     * 观测记录读回（按观测时刻升序）；[manualObservationDao] 未注入 → 空列表（未登记语义）。
     * OPT-V11-07：含历史无类型行与四类观测（水质实测不混入）。
     */
    suspend fun tideObservations(spotId: Long): List<TideObservation> =
        manualObservationDao?.forSpot(spotId)
            ?.filter { isTideObservationKind(it.kind) }
            ?.map { TideObservation(it.id, it.spotId, it.observedAtEpochSec, it.createdAtEpochMs, it.kind) }
            ?: emptyList()

    /** 校潮类观测 kind 判定（历史 `TIDE_OBSERVATION` + 四类枚举名）。 */
    private fun isTideObservationKind(kind: String): Boolean =
        kind == ManualObservationEntity.KIND_TIDE_OBSERVATION ||
            com.tidespot.core.domain.tide.TideObservationType.entries.any { it.name == kind }

    /**
     * 登记现场校潮拟合参数（**adopted=false 也登记**，D12 校正前后对照与「再记 N 条」
     * 回放需要）；每点一行 REPLACE 覆盖式。DAO 未注入 → 抛 [IllegalStateException]（同上）。
     */
    suspend fun saveManualTideFit(spotId: Long, fit: TideCalibrator.ManualTideFit) {
        val dao = manualTideFitDao ?: throw IllegalStateException(
            "manualTideFitDao 未注入（生产由 :app 提供 Room 实现；拟合参数不可静默丢弃）",
        )
        dao.upsert(
            ManualTideFitEntity(
                spotId = spotId,
                lagHours = fit.lagHours,
                offsetM = fit.offsetM,
                gain = fit.gain,
                adopted = fit.adopted,
                updatedAtEpochMs = nowMillis(),
            ),
        )
    }

    /** 拟合参数读回（message 不持久化，恒 null）；未登记/未注入 → null = 未应用校正。 */
    suspend fun manualTideFit(spotId: Long): TideCalibrator.ManualTideFit? =
        manualTideFitDao?.getBySpot(spotId)?.let {
            TideCalibrator.ManualTideFit(it.lagHours, it.offsetM, it.gain, it.adopted, message = null)
        }

    // ────────────────────────────── 内部实现 ──────────────────────────────

    private data class WeatherPart(
        val dto: com.tidespot.core.data.dto.WeatherResponseDto?,
        val data: List<HourlyWeather>?,
        val daily: WeatherDailyDto?,
        val error: DataError?,
    )

    private data class MarinePart(
        val dto: com.tidespot.core.data.dto.MarineResponseDto?,
        val data: List<HourlyMarine>?,
        val error: DataError?,
    )

    private suspend fun fetchWeatherPart(spot: FavoriteSpot, now: Long): WeatherPart {
        val logs = mutableListOf<FetchLogEntry>()
        return try {
            val dto = client.fetchWeather(spot.lat, spot.lon) { logs += it }
            persistLogs(spot.id, logs)
            WeatherPart(dto, DtoMappers.toHourlyWeatherList(dto), dto.daily, null)
        } catch (e: DataError) {
            persistLogs(spot.id, logs)
            WeatherPart(null, null, null, e)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e   // 协作式取消必须透传（不得吞为降级）
        } catch (e: Exception) {
            // BLK-06/N-4（CHG-07 修复 4）：脏 JSON/脏时间串/反序列化等未分类异常不得逃逸，
            // 统一映射为结构化 DataError.NetworkError（分维降级 + 可 UI 呈现）
            persistLogs(spot.id, logs)
            WeatherPart(null, null, null, DataError.NetworkError(e))
        }
    }

    private suspend fun fetchMarinePart(spot: FavoriteSpot, now: Long): MarinePart {
        val logs = mutableListOf<FetchLogEntry>()
        return try {
            val dto = client.fetchMarine(spot.lat, spot.lon) { logs += it }
            persistLogs(spot.id, logs)
            MarinePart(dto, DtoMappers.toHourlyMarineList(dto), null)
        } catch (e: DataError) {
            persistLogs(spot.id, logs)
            MarinePart(null, null, e)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e   // 协作式取消必须透传（不得吞为降级）
        } catch (e: Exception) {
            // BLK-06/N-4（CHG-07 修复 4）：同天气侧——未分类异常映射为 DataError.NetworkError
            persistLogs(spot.id, logs)
            MarinePart(null, null, DataError.NetworkError(e))
        }
    }

    private suspend fun persistLogs(spotId: Long, entries: List<FetchLogEntry>) {
        if (entries.isEmpty()) return
        fetchLogDao.insertAll(
            entries.map {
                FetchLogEntity(
                    spotId = spotId,
                    source = it.source,
                    urlHash = it.urlHash,
                    httpStatus = it.httpStatus,
                    bytes = it.bytes,
                    costMs = it.costMs,
                    fetchedAt = it.fetchedAt,
                )
            }
        )
    }

    private suspend fun cachedDetailInternal(spot: FavoriteSpot, now: Long): SpotDetailResult {
        val rows = hourlyDao.forSpot(spot.id)
        val weather = EntityMappers.toHourlyWeather(rows)
        val marine = EntityMappers.toHourlyMarine(rows)
        val lastWeather = fetchLogDao.lastSuccess(spot.id, DataSource.WEATHER.wire)
        val lastMarine = fetchLogDao.lastSuccess(spot.id, DataSource.MARINE.wire)
        val marineAllNull = marine.isNotEmpty() && marine.all { it.isAllNull }
        // CHG-35：缓存路径同样透出自愈类型（只读不改库；真正回写发生在 refreshSpotDetail）
        val cachedEffectiveWaterBody = if (
            marine.isNotEmpty() && !marineAllNull &&
            (spot.waterBody == WaterBodyType.INLAND_WATER || spot.waterBody == WaterBodyType.UNKNOWN)
        ) {
            WaterBodyType.NEARSHORE
        } else {
            null
        }
        // OPT-M5-02：缓存路径同口径透出校潮摘要（评分序列已在刷新时按校正落库，读侧天然一致）
        val tideCorrection = manualTideFitDao?.getBySpot(spot.id)
            ?.takeIf { it.adopted && marine.isNotEmpty() && !marineAllNull }
            ?.let { TideCorrectionSummary(it.lagHours, it.offsetM, it.gain, it.adopted) }
        return SpotDetailResult(
            spotId = spot.id,
            slices = SeriesJoiner.join(weather, marine),
            weather = weather,
            marine = marine,
            daily = null,
            weatherDegraded = !Ttl.isFresh(DataSource.WEATHER, lastWeather?.fetchedAt ?: 0L, now),
            marineDegraded = !Ttl.isFresh(DataSource.MARINE, lastMarine?.fetchedAt ?: 0L, now) || marineAllNull,
            marineAllNullInland = marineAllNull,
            errors = emptyMap(),
            servedFromCache = setOf(DataSource.WEATHER, DataSource.MARINE),
            fetchedAtMillis = mapOf(
                DataSource.WEATHER to (lastWeather?.fetchedAt ?: 0L),
                DataSource.MARINE to (lastMarine?.fetchedAt ?: 0L),
            ),
            tideCorrection = tideCorrection,
            effectiveWaterBody = cachedEffectiveWaterBody,
        )
    }

    /**
     * M4 必修 8（§7.3）：评分 + hourly_score 整窗一次性落库。
     * - 无 DAO 注入 / 无天气序列 / 无时区（响应与库存皆缺，FO-18 禁默认 +8）→ 跳过（不造假数据）；
     * - delete-then-insert：旧窗残留行一次性清除（CHG-03 整窗替换语义）。
     */
    private suspend fun persistHourlyScores(
        spot: FavoriteSpot,
        weather: List<HourlyWeather>,
        marine: List<HourlyMarine>,
        daily: WeatherDailyDto?,
        tzOffsetSec: Int?,
    ) {
        val dao = hourlyScoreDao ?: return
        if (weather.isEmpty() || tzOffsetSec == null) return
        val inputs = HourlyScoreStore.buildScoreInputs(weather, marine, daily, tzOffsetSec)
        val preset = HourlyScoreStore.presetFor(spot)
        // D13（CHG-16⑧）：评分计算切 [scoringDispatcher]（默认 Dispatchers.Default）；
        // 协作式取消必须重抛（catch 后 throw，禁吞为降级/空结果）。
        // 单点最小刷新路径唯一计算入口：TTL 命中走 cachedDetailInternal（零计算），
        // 刷新路径仅此处一次全量计算——refresh/cached 不存在双次全量重算。
        val results = try {
            withContext(scoringDispatcher) { scoreCalculator.calculate(inputs, preset, spot.lon) }
        } catch (e: CancellationException) {
            throw e   // D13：CancellationException 捕获后必须重抛
        }
        // D13：withContext 块为 ATOMIC 启动（纯 CPU 无挂起点，块内不响应取消）——
        // 评分完成在此检查点主动响应取消，禁把已取消协程的评分静默落库
        coroutineContext.ensureActive()
        if (results.isEmpty()) return
        dao.deleteForSpot(spot.id)
        dao.insertAll(HourlyScoreStore.toEntities(spot.id, inputs, results, tzOffsetSec))
    }

    private fun gateKey(spotId: Long) = "spot:$spotId"

    /**
     * D03 读侧收口（M5）：跨收藏点 Top10（F-2.6）/ 列表摘要（F-2.5）的无网读取——
     * 读 hourly_score 库内行（ALGO_VERSION 入键）重建 [FishScoreResult] 序列 → [BestWindowFinder.find]。
     * 与详情页派生层现算（cachedDetail slices 同源）恒等：同输入同参 find 输出逐字段一致（锁测试 TopWindowsTest）。
     *
     * 口径（CONTRACT M5 登记）：读侧无 dh 序列 → tideRates=null = 内陆语义跳过同周期去重（§6.6 步骤 4）。
     *
     * @param horizonDays 用户设置 N=7 或 10（与现算口径一致）。
     * @return [TopWindowsResult.validHoursPresent] = 0 表示未算过/无库内行 → UI 显示「未加载」。
     */
    suspend fun topWindows(spot: FavoriteSpot, horizonDays: Int = 7): TopWindowsResult {
        val dao = hourlyScoreDao ?: return TopWindowsResult(emptyList(), null, null, 0)
        val rows = dao.forSpot(spot.id, FishScoreCalculator.ALGO_VERSION)
        if (rows.isEmpty()) return TopWindowsResult(emptyList(), null, null, 0)
        // forSpot 无 ORDER BY 保证，find 的合并逻辑假定升序 → 按 hourEpoch 升序重建（N-9：排序一律 epoch）
        val rebuilt = rows.sortedBy { it.hourEpoch }.map { row ->
            FishScoreResult(
                timeEpochSec = row.hourEpoch,
                total = row.score.toInt(),
                factors = parseFactorsJson(row.factorsJson),
                vetoReasons = HourlyScoreStore.columnToVetoReasons(row.vetoReason),
                // CHG-16①⑥：读侧重建解析 veto_codes 列——finder 只认机器码，缺此列雷暴小时会在
                // 读侧被误入选；空数组/旧行 DEFAULT '[]' 语义与 vetoReason NULL 对称
                vetoCodes = HourlyScoreStore.columnToVetoCodes(row.vetoCodes),
                // CHG-08 ④：非空 List；仅窗口计算内部使用（find 不读 confidence），禁止透出展示（D10 不变）
                confidence = Confidence.ESTIMATED,
            )
        }
        // CHG-15 偏差①：时间域 = 当前小时的下一整点起（SRS §6.6），库内历史小时（past 7d 前置）不入选
        val nowSec = nowMillis() / 1000
        val nextHourEpochSec = nowSec - nowSec % 3600 + 3600
        val futureCount = rebuilt.count { it.timeEpochSec >= nextHourEpochSec }
        // OPT-V11-09（P1）：读侧与现算同口径——有效校潮 lag 透传给 find（只影响注意事项文案）
        val manualTideLagHours = manualTideFitDao?.getBySpot(spot.id)?.takeIf { it.adopted }?.lagHours
        val out = BestWindowFinder.find(
            results = rebuilt,
            tideRates = null,
            validHoursPresent = rebuilt.size,
            horizonDays = horizonDays,
            nowEpochSec = nextHourEpochSec,
            manualTideLagHours = manualTideLagHours,
        )
        // validHoursPresent = 未来窗口内有效行数（历史行不计；全过期 = 0 → UI「未加载」或禁用文案）
        return TopWindowsResult(out.windows, out.fallbackMessage, out.fallbackBestEpochSec, futureCount)
    }

    /**
     * factorsJson → [FactorScore]：与 [HourlyScoreStore.toEntities]/factorsJson 组装完全对称的
     * 手工 JSON 解析（domain 无 @Serializable）；脏串/缺键 → 空列表（N-4 不抛异常）。
     */
    private fun parseFactorsJson(json: String): List<FactorScore> = runCatching {
        factorJsonParser.parseToJsonElement(json).jsonArray.map { el ->
            val obj = el.jsonObject
            FactorScore(
                id = obj.getValue("id").jsonPrimitive.content,
                sub = obj.getValue("sub").jsonPrimitive.double,
                weight = obj.getValue("weight").jsonPrimitive.double,
                detail = obj["detail"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }.getOrDefault(emptyList())
}

/**
 * D03 读侧收口（M5）输出（data 层类型，不放 Models.kt——CONTRACT M5 子契约）。
 *
 * @param windows Top10 窗口（与详情页现算同参 find 恒等）
 * @param fallbackMessage [com.tidespot.core.domain.window.BestWindowFinder.Output.fallbackMessage] 透传
 * @param fallbackBestEpochSec 兜底最高分时刻（epoch 秒，UI 渲染「（时刻…）」）；有窗口/禁用时为 null
 * @param validHoursPresent 库内有效小时行数；=0 表示未算过/无库内行 → UI 显示「未加载」
 */
data class TopWindowsResult(
    val windows: List<BestWindow>,
    val fallbackMessage: String?,
    val fallbackBestEpochSec: Long?,
    val validHoursPresent: Int,
)

/**
 * 现场校潮观测（M6/OPT-M5-02 读侧门面类型；data 层类型，不放 Models.kt——CHG-16⑦）。
 *
 * OPT-V11-07（P1）：尾增 [kind]（`TIDE_OBSERVATION` = 历史无类型观测；其余 = 四类观测枚举名）。
 */
data class TideObservation(
    val id: Long,
    val spotId: Long,
    /** 用户记录的高低潮/涨落时刻（epoch 秒）。 */
    val observedAtEpochSec: Long,
    /** 记录时刻（epoch 毫秒）。 */
    val createdAtEpochMs: Long,
    val kind: String = KIND_TIDE_OBSERVATION,
) {
    /** 观测类型；历史无类型行 → null（拟合按「任意极值最近匹配」处理）。 */
    val type: com.tidespot.core.domain.tide.TideObservationType?
        get() = com.tidespot.core.domain.tide.TideObservationType.entries
            .firstOrNull { it.name == kind }

    companion object {
        const val KIND_TIDE_OBSERVATION = ManualObservationEntity.KIND_TIDE_OBSERVATION
    }
}

/** OPT-V11-02（CHG-27）：水质实测类型（落 `manual_observation.kind`；与校潮 `TIDE_OBSERVATION` 互斥）。 */
enum class WaterQualityObservationKind { WATER_TEMP, DO, TURBIDITY }

/** OPT-V11-02：水质实测记录（读回用；[value] 单位随 [kind]：WATER_TEMP=℃ / DO=mg/L / TURBIDITY=NTU）。 */
data class WaterQualityObservation(
    val id: Long,
    val spotId: Long,
    val kind: String,
    val value: Double?,
    val observedAtEpochSec: Long,
    val createdAtEpochMs: Long,
)

/**
 * 现场校潮应用摘要（M6/CHG-16⑦；[SpotDetailResult.tideCorrection] 透出，D12 对照用）。
 * null = 未应用（无 fit / 未 adopted / 内陆点）。
 */
data class TideCorrectionSummary(
    val lagHours: Int,
    val offsetM: Double,
    val gain: Double,
    val adopted: Boolean,
)

/**
 * OPT-M5-02 纯函数：现场校潮序列校正 M'(h)=gain·M(h−lag)+offset。
 * 复用 [TideCalibrator.apply]（ManualTideFit 构造等价 Calibration，gain=1.0 现场口径）；
 * [fit.adopted]=false 时原样返回（仅登记不应用）；窗外（i−lag 越界/源缺测）→ null（公式语义）。
 * 与手工 apply 结果恒等由锁测试守护（TideSpotRepositoryTest.M6 校潮应用闭环）。
 */
internal fun applyManualTideCorrection(
    marine: List<HourlyMarine>,
    fit: TideCalibrator.ManualTideFit,
): List<HourlyMarine> {
    if (!fit.adopted) return marine
    val calibration = TideCalibrator.Calibration(
        lagHours = fit.lagHours,
        gain = fit.gain,
        offsetM = fit.offsetM,
        r = 1.0,
        alignedSamples = 0,
        rawRmseM = 0.0,
        correctedRmseM = 0.0,
        adopted = true,
        rejectReason = null,
        holdout = null,
    )
    val corrected = TideCalibrator.apply(marine.map { it.seaLevelHeightMslM }, calibration)
    return marine.mapIndexed { i, m -> m.copy(seaLevelHeightMslM = corrected[i]) }
}
