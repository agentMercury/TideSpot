package com.tidespot.core.data.geo

import com.tidespot.core.data.dto.BigDataCloudReverseDto
import com.tidespot.core.data.dto.PhotonReverseDto
import com.tidespot.core.data.local.CacheKeys
import com.tidespot.core.data.local.ReverseCacheDao
import com.tidespot.core.data.local.ReverseCacheEntity
import com.tidespot.core.data.remote.ReverseGeoClient
import com.tidespot.core.data.remote.TideSpotUrls
import com.tidespot.core.data.remote.TokenBucket
import com.tidespot.core.domain.model.NamedPlace
import com.tidespot.core.domain.model.ReverseGeoSource
import com.tidespot.core.domain.util.OpenLocationCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * 逆地理解析门面（SRS §5.8 降级链；CONTRACT M3 子契约签名）。
 * 仅在用户显式确认选点时由调用方触发（拖动预览不发请求，F-1.4）。
 *
 * 流程：
 * 1. 缓存 `spot_reverse_cache`（键 = 0.005° 格点，TTL 30 d）命中直接返回，不打网络；
 * 2. 并行发 RG-A(6s) ∥ RG-B(4s)；RG-A/RG-B 各自独立令牌桶 `TokenBucket(1, 2000)`（§12 红线）。
 *    CHG-09④（NEW-04/GAP-M3-11）：令牌被占时**挂起等待且可取消**（[TokenBucket.acquire]，
 *    与正地理 GeocodingClient 的 acquire 语义一致；协程取消自然传播），不再跳级丢弃——
 *    连续选点等待令牌恢复（≤2s/级）而非立即落 RG-D；等待有 **3s 预算上限**（[WAIT_BUDGET_MILLIS]，
 *    超时该级按失败跳过走降级链，预算防极端阻塞），KDoc 与单测均固化此语义；
 * 3. 显示名优先级：RG-B 聚落名 > RG-A(locality|city) > RG-A(principalSubdivision) > RG-D plusCode；
 *    RG-A 的 市/省 由 [NamedPlace.subLabel] 承载（有则附）；全部名称先过 T2S 归一（§5.8 坑 1）；
 * 4. 全链失败落 RG-D（本地 Plus Code + 格式化坐标，永不失败，信息条绝不空白）。
 *
 * CHG-09④ 缓存语义（NEW-04/GAP-M3-06）：
 * - **仅成功结果（RG-A/RG-B 任一有名称）才 upsert 30d**；
 * - RG-D 兜底结果**不落缓存**（选「不入库」而非「5min 负缓存」——实现简单、无 schema 变更、
 *   网络恢复后立即可重试；代价是全链失败期间重复 resolve 会重打网络，受令牌桶节流保护）。
 */
class ReverseGeocoder(
    private val reverseCacheDao: ReverseCacheDao,
    // M6（CHG-16⑩/URL 单点收敛）：生产默认值改引用 TideSpotUrls 常量（CHG-08 ②硬约束 3），
    // 字符串与原硬编码逐字符一致（防回归断言不改）
    bigDataCloudBaseUrl: String = TideSpotUrls.BIG_DATA_CLOUD_BASE,
    photonBaseUrl: String = TideSpotUrls.PHOTON_BASE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** 令牌等待预算 ms（A/B 并行、各自限时；超时该级跳过走降级）。测试可注入小值验证预算路径。 */
    private val waitBudgetMillis: Long = WAIT_BUDGET_MILLIS,
) {

    private val client = ReverseGeoClient(bigDataCloudBaseUrl, photonBaseUrl, ioDispatcher)

    /** §12：RG-A / RG-B 各自独立令牌桶 ≤1 req/2s。 */
    private val bucketA = TokenBucket(capacity = 1, periodMillis = 2_000L, clock = clock)
    private val bucketB = TokenBucket(capacity = 1, periodMillis = 2_000L, clock = clock)

    suspend fun resolve(lat: Double, lon: Double): NamedPlace {
        val key = CacheKeys.spotKey(lat, lon)
        // 1) 缓存（0.005°，TTL 30 d）
        reverseCacheDao.getByKey(key)?.let { e ->
            if (clock() - e.resolvedAtEpochMs <= TTL_MILLIS) return e.toNamedPlace(lat, lon)
        }
        // 2) 并行 RG-A ∥ RG-B；令牌不足 → acquire 挂起等待（可取消，CHG-09④），
        //    超过等待预算 → 该级按失败跳过走降级链
        val (a, b) = coroutineScope {
            val da = async { safe { if (withTimeoutOrNull(waitBudgetMillis) { bucketA.acquire() } == null) null else client.fetchBigDataCloud(lat, lon) } }
            val db = async { safe { if (withTimeoutOrNull(waitBudgetMillis) { bucketB.acquire() } == null) null else client.fetchPhoton(lat, lon) } }
            da.await() to db.await()
        }
        val place = buildPlace(lat, lon, a, b)
        // 3) 仅成功结果（RG-A/RG-B 任一有名称）才缓存 30d；RG-D 兜底不入库（CHG-09④）
        if (place.source != ReverseGeoSource.RG_D) {
            reverseCacheDao.upsert(place.toEntity(key))
        }
        return place
    }

    // ────────────────────────────── 显示名组装（§5.8 第 4/5 步） ──────────────────────────────

    private fun buildPlace(
        lat: Double,
        lon: Double,
        a: BigDataCloudReverseDto?,
        b: PhotonReverseDto?,
    ): NamedPlace {
        val aLocality = a?.locality.normalize()
        val aCity = a?.city.normalize()
        val aAdmin1 = a?.principalSubdivision.normalize()
        val bProps = b?.features?.firstOrNull()?.properties
        val bName = bProps?.name.normalize()
        val bCity = bProps?.city.normalize()
        val bState = bProps?.state.normalize()

        // 优先级：RG-B 聚落名 > RG-A(locality|city) > RG-A(省)；全空 → RG-D 兜底（永不失败）
        val base = bName ?: aLocality ?: aCity ?: aAdmin1 ?: return rgdFallback(lat, lon)
        val source = if (bName != null) ReverseGeoSource.RG_B else ReverseGeoSource.RG_A

        val city = aCity ?: bCity
        val admin1 = aAdmin1 ?: bState
        val subLabel = listOfNotNull(city, admin1)
            .filter { it != base } // 主标题即市/省时避免重复
            .joinToString(SUB_SEPARATOR)
            .takeIf { it.isNotEmpty() }

        return NamedPlace(
            lat = lat,
            lon = lon,
            displayName = base,
            subLabel = subLabel,
            plusCode = a?.plusCode,
            waterName = null, // RG-C 不接入（CHG-06），恒为 null
            locality = aLocality,
            city = city,
            admin1 = admin1,
            source = source,
        )
    }

    /** RG-D 兜底：Plus Code + 格式化坐标（纯本地计算，永不失败 → 信息条绝不空白）。不落缓存。 */
    private fun rgdFallback(lat: Double, lon: Double): NamedPlace {
        val code = OpenLocationCode.encode(lat, lon)
        return NamedPlace(
            lat = lat,
            lon = lon,
            displayName = code,
            subLabel = String.format(Locale.US, "%.4f, %.4f", lat, lon),
            plusCode = code,
            waterName = null,
            locality = null,
            city = null,
            admin1 = null,
            source = ReverseGeoSource.RG_D,
        )
    }

    private fun String?.normalize(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }?.let { T2S.toSimplified(it) }

    // ────────────────────────────── 缓存映射 ──────────────────────────────

    private fun NamedPlace.toEntity(key: String) = ReverseCacheEntity(
        spotKey = key,
        displayName = displayName,
        subLabel = subLabel,
        plusCode = plusCode,
        locality = locality,
        city = city,
        admin1 = admin1,
        source = source.name,
        resolvedAtEpochMs = clock(),
    )

    private fun ReverseCacheEntity.toNamedPlace(lat: Double, lon: Double) = NamedPlace(
        lat = lat,
        lon = lon,
        displayName = displayName,
        subLabel = subLabel,
        plusCode = plusCode,
        waterName = null,
        locality = locality,
        city = city,
        admin1 = admin1,
        source = ReverseGeoSource.CACHE,
    )

    companion object {
        /** §5.8 / §12：同格点缓存 30 d。 */
        const val TTL_MILLIS = 30L * 24 * 3600 * 1000

        /**
         * 令牌等待预算（CHG-09④）：RG-A/RG-B 并行、各自 [withTimeoutOrNull] 限时，
         * 超时该级按失败跳过走降级链。桶周期 2s → 常态等待 ≤2s 不会触顶；
         * 预算是防极端阻塞的安全上限（如未来桶参数调整/多调用方排队）。
         */
        const val WAIT_BUDGET_MILLIS = 3_000L

        private const val SUB_SEPARATOR = " · "

        /** 任何网络失败只跳过该级不阻塞（§5.8 第 2 步）；取消除外。 */
        private suspend fun <T> safe(block: suspend () -> T?): T? =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
    }
}
