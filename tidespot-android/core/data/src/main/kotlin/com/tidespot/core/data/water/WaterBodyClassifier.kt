package com.tidespot.core.data.water

import com.tidespot.core.data.local.CacheKeys
import com.tidespot.core.data.local.WaterBodyCacheDao
import com.tidespot.core.data.local.WaterBodyCacheEntity
import com.tidespot.core.domain.model.WaterBodyType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 水体类型判定（SRS §6.9，CHG-06 版：第 3 级 = OSTIA lake mask 探针；CHG-09② 判定树修正）。
 *
 * 红线（§5.8 坑 2）：判定**只看海洋字段是否非 null 与探针结果**，
 * 禁止用逆地理行政区结果反推水体类型（RG-A 在珠江口水面返回「深圳市/南山区」）。
 *
 * CHG-09② 变更（M3 改进意见 02 / NEW-03）：
 * - **删除 `elevation ≤ 3m → INLAND_WATER 且跳过探针` 捷径**（SRS §6.9 无此规则；低海拔陆地
 *   不得误判为湖）——低海拔同样走探针；elevation 仅作为透出/参考量保留；
 * - 探针失败（null）/探针未注入 → 判 [WaterBodyType.UNKNOWN]（§6.9 第 4 级「未知+徽标」），
 *   **不再落 LAND**（CHG-09 产品口径 C1：探针失败期判 UNKNOWN + 「待判定」徽标，
 *   OSTIA 复测通过后自动恢复）；
 * - UNKNOWN 判定入库用**短 TTL 24 h**，成功判定仍 30 d。实现采用**免迁移方案**：
 *   读取时按 `waterBodyType == UNKNOWN` 判 24 h，其余 30 d（零 schema 变更，不升 Room v3、
 *   不加 `uncertain` 列——如未来需要区分更多失败形态再评估加列）。
 */
class WaterBodyClassifier(
    private val waterBodyCacheDao: WaterBodyCacheDao,
    probe: InlandWaterProbe? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** 探针未注入等异常状态的告警回调（:core:domain 纯 Kotlin 无日志；默认空实现，M6 接日志系统）。 */
    private val onWarn: (String) -> Unit = {},
) {

    /** CHG-25 批次 A：运行期可切换探针（构造签名不变；@Volatile 保证跨线程可见）。 */
    @Volatile
    private var probeRef: InlandWaterProbe? = probe

    /**
     * CHG-25（P0-1 批次 A，只增）：设置页保存/清除 Copernicus 凭据后运行期切换探针。
     *
     * `null` = 探针禁用 → 判定走 UNKNOWN 降级（CHG-09② 语义不变，不落 LAND）；
     * 线程安全（@Volatile 写，[classify] 读单次快照）。
     */
    fun setProbe(probe: InlandWaterProbe?) {
        probeRef = probe
    }

    /**
     * 判定（成功结果缓存 30 d/点、UNKNOWN 缓存 24 h/点，仅显式选点时查询，§12）。
     *
     * @param marineHasValue 海洋字段是否非 null（§6.9 主判据）：
     *   - `true` = 有值 → 近岸水域（不触探针）；
     *   - `false` = 全 null（内陆/无海况）；
     *   - `null` = **未知**（M3 接线口径：确认选点时尚无海洋数据 → 走探针路径）。
     *     M4 接入真实 marine 序列后，调用方改为透传真实布尔值并补全本 KDoc。
     * @param elevation Open-Meteo 海拔 m（§6.9 辅助判据；B5：近岸可能取到陆地格点，仅作参考。
     *   CHG-09② 后不再参与决策，仅为调用方透出/未来判据保留）。
     */
    suspend fun classify(
        lat: Double,
        lon: Double,
        marineHasValue: Boolean?,
        elevation: Double?,
    ): WaterBodyType {
        val key = "${CacheKeys.spotKey(lat, lon)}#v$JUDGE_VERSION"
        waterBodyCacheDao.getByKey(key)?.let { e ->
            if (clock() - e.judgedAtEpochMs <= ttlMillisFor(e.waterBodyType)) {
                return WaterBodyType.valueOf(e.waterBodyType)
            }
        }
        // 探针发起条件：海洋「未知或全 null」时查询 OSTIA（海洋有值即主判据，无需探针）。
        // CHG-09②：低海拔不再跳过探针（原 ≤3m 捷径已删除，SRS §6.9 无此规则）。
        // CHG-25：探针读单次快照（设置页运行期切换与判定并发安全）。
        val currentProbe = probeRef
        val lakeProbe: Boolean? = when {
            marineHasValue == true -> null
            currentProbe == null -> {
                onWarn("WaterBodyClassifier: probe not injected, lat=$lat lon=$lon -> UNKNOWN")
                null
            }
            else -> currentProbe.hasLakeValue(lat, lon)
        }
        val type = decide(marineHasValue = marineHasValue, elevation = elevation, lakeProbe = lakeProbe)
        waterBodyCacheDao.upsert(WaterBodyCacheEntity(spotKey = key, waterBodyType = type.name, judgedAtEpochMs = clock()))
        return type
    }

    companion object {
        /**
         * CHG-35（水体判定修复）：判定**版本号**——参与缓存键（`spotKey#vN`）。
         *
         * 历史缺陷：v1 判定依赖「OSTIA 全球 SST 是否有值」当湖泊掩膜，**海洋必然有 SST**
         * → 海域被误判 `INLAND_WATER`（浪潮 Tab 显示「水位（无潮汐）」）并按 30 d 缓存。
         * v2 起主判据改为调用方透传的真实 `marineHasValue`（`probeMarineHasValue`），
         * 键升级使旧错误缓存自然失效（不删表、不迁移）。
         */
        const val JUDGE_VERSION = 2

        /** §6.9：成功判定结果缓存 30 d/点。 */
        const val TTL_MILLIS = 30L * 24 * 3600 * 1000

        /** CHG-09②：UNKNOWN（探针失败/未注入）判定结果短 TTL 24 h，避免坏判定钉死 30 d。 */
        const val UNCERTAIN_TTL_MILLIS = 24L * 3600 * 1000

        /**
         * 免迁移方案（CHG-09②）：不升级 Room v3、不加 uncertain 列，
         * 按缓存行类型名判定 TTL：UNKNOWN = 24 h，其余 = 30 d。
         */
        fun ttlMillisFor(waterBodyType: String): Long =
            if (waterBodyType == WaterBodyType.UNKNOWN.name) UNCERTAIN_TTL_MILLIS else TTL_MILLIS

        /**
         * 纯决策函数（§6.9 判定树，CHG-09② 修正版，抽出便于单测）：
         * - 海洋字段非 null（marineHasValue=true）→ 近岸水域（OCEAN/NEARSHORE 细分 v1 无离岸距离
         *   判据，统一按 NEARSHORE，与 UI「（近岸水域）」后缀一致；OCEAN 枚举保留给后续增强）；
         * - 探针明确有值 → 内陆水体（湖/库）；探针明确无值 → 陆地（低海拔亦然，捷径已删）；
         * - 探针失败/未注入（null）→ [WaterBodyType.UNKNOWN]（§6.9 第 4 级「未知」，不再落 LAND）。
         *
         * @param elevation 仅透出保留（CHG-09② 后不参与决策，见 [classify] KDoc）。
         */
        fun decide(marineHasValue: Boolean?, elevation: Double?, lakeProbe: Boolean?): WaterBodyType = when {
            marineHasValue == true -> WaterBodyType.NEARSHORE
            lakeProbe == true -> WaterBodyType.INLAND_WATER
            lakeProbe == false -> WaterBodyType.LAND
            else -> WaterBodyType.UNKNOWN
        }
    }
}
