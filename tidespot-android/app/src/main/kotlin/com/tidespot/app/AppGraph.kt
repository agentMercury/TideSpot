package com.tidespot.app

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.tidespot.core.data.geo.ReverseGeocoder
import com.tidespot.core.data.local.TideSpotDatabase
import com.tidespot.core.data.remote.GeocodingClient
import com.tidespot.core.data.remote.OpenMeteoClient
import com.tidespot.core.data.repo.TideSpotRepository
import com.tidespot.core.data.water.CopernicusAuthClient
import com.tidespot.core.data.water.CopernicusCredential
import com.tidespot.core.data.water.CopernicusCredentialStore
import com.tidespot.core.data.water.CopernicusCredentialValidator
import com.tidespot.core.data.water.CredentialCheck
import com.tidespot.core.data.water.FileCopernicusCredentialStore
import com.tidespot.core.data.water.InlandWaterProbe
import com.tidespot.core.data.water.OstiaLakeProbe
import com.tidespot.core.data.water.WaterBodyClassifier
import com.tidespot.feature.map.geo.DataGeocoder
import com.tidespot.feature.map.geo.DataPlaceResolver
import com.tidespot.feature.map.geo.DataWaterBodyResolver
import com.tidespot.feature.map.geo.GeocoderApi
import com.tidespot.feature.map.geo.PlaceResolver
import com.tidespot.feature.map.geo.WaterBodyResolver
import com.tidespot.feature.map.location.LocationController
import com.tidespot.feature.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * 手工 DI 组装（M6 批次 3；无 DI 框架）：单例持有 Room / 各 Client / Repository。
 *
 * - Room：databaseBuilder 挂全量迁移 MIGRATION_1_2/2_3/3_4，DAO 全部注入
 *   （hourlyScoreDao/manualObservationDao/manualTideFitDao 为 M4/M6 必注入项，null 语义仅测试路径）；
 * - OkHttp：各 Client 内部经 `HttpCommon.tideSpotHttpClient` 自建（UA=`TideSpotUserAgent`、
 *   超时、重试、节流全部内置且冻结不可动），无构造注入点——:app 不另建实例、不重复组装；
 * - Copernicus 凭据：构建期经 BuildConfig 从本机 local-secrets.properties 注入
 *   （缺文件→空串→探针禁用→UNKNOWN 降级 CHG-09；禁硬编码）；
 * - 逆地理：RG 节流 ≤1 req/2s 由 [ReverseGeocoder] 内置（RU 口径不动）。
 */
class AppGraph(context: Context) {

    private val appContext: Context = context.applicationContext

    // ────────────── Room（v4 全量迁移） ──────────────

    private val db: TideSpotDatabase = Room.databaseBuilder(
        appContext,
        TideSpotDatabase::class.java,
        TideSpotDatabase.NAME,
    )
        .addMigrations(
            TideSpotDatabase.MIGRATION_1_2,
            TideSpotDatabase.MIGRATION_2_3,
            TideSpotDatabase.MIGRATION_3_4,
            TideSpotDatabase.MIGRATION_4_5,
        )
        .build()

    // ────────────── 网络客户端（UA/节流内置不动） ──────────────

    private val openMeteoClient = OpenMeteoClient()

    val geocodingClient = GeocodingClient()

    private val reverseGeocoder = ReverseGeocoder(
        reverseCacheDao = db.reverseCacheDao(),
    )

    // ────────────── 凭据（CHG-25 P0-1：运行期外部存储，APK 内零凭据） ──────────────

    /**
     * Copernicus 凭据存储：App 私有目录 `filesDir/copernicus.properties`（0600 尽力收紧；
     * Manifest `allowBackup=false` 防云备份带出）。构建期 BuildConfig 注入已移除。
     */
    val credentialStore: CopernicusCredentialStore =
        FileCopernicusCredentialStore(File(appContext.filesDir, CREDENTIAL_FILE))

    private val credentialValidator = CopernicusCredentialValidator()

    // ────────────── 水体判定（CHG-09：凭据缺失/探针失败 → UNKNOWN 短 TTL） ──────────────

    private val waterBodyClassifier: WaterBodyClassifier = run {
        val stored = runBlocking { credentialStore.load() }
        if (stored == null) {
            Log.i(TAG, "Copernicus 凭据未配置：OSTIA 探针禁用 → 水体判定 UNKNOWN 降级（设置页可配置）")
        }
        WaterBodyClassifier(
            waterBodyCacheDao = db.waterBodyCacheDao(),
            probe = stored?.let { OstiaLakeProbe(CopernicusAuthClient(it.username, it.password)) },
            onWarn = { Log.w(TAG, it) },
        )
    }

    /**
     * 校验并应用凭据（CHG-25 GWT）：仅 [CredentialCheck.Valid] 落库并即时切换探针（无需重启）；
     * 失败不落库、不改变现状（Invalid=凭据错误 / Unreachable=网络问题，均由 UI 提示）。
     * `credential == null` 或空凭据 = 清除。
     */
    suspend fun checkAndApply(credential: CopernicusCredential?, persist: Boolean = true): CredentialCheck {
        if (credential == null || credential.isBlank) {
            if (persist) credentialStore.clear()
            waterBodyClassifier.setProbe(null)
            return CredentialCheck.Missing
        }
        val result = credentialValidator.check(credential)
        if (result is CredentialCheck.Valid) {
            if (persist) credentialStore.save(credential)
            waterBodyClassifier.setProbe(
                OstiaLakeProbe(CopernicusAuthClient(credential.username, credential.password)),
            )
            Log.i(TAG, "Copernicus 凭据已生效（运行期切换探针，无需重启）")
        }
        return result
    }

    suspend fun hasCopernicusCredential(): Boolean = credentialStore.load() != null

    suspend fun clearCopernicusCredential() {
        credentialStore.clear()
        waterBodyClassifier.setProbe(null)
    }

    // ────────────── Repository（M4/M6 DAO 全量注入 + D13 scoringDispatcher） ──────────────

    val repository = TideSpotRepository(
        client = openMeteoClient,
        spotDao = db.spotDao(),
        hourlyDao = db.hourlyMetricDao(),
        fetchLogDao = db.fetchLogDao(),
        hourlyScoreDao = db.hourlyScoreDao(),
        manualObservationDao = db.manualObservationDao(),
        manualTideFitDao = db.manualTideFitDao(),
        scoringDispatcher = Dispatchers.Default,
    )

    // ────────────── :feature:map 装配面（DataWiring 适配） ──────────────

    val placeResolver: PlaceResolver = DataPlaceResolver(reverseGeocoder)

    val geocoder: GeocoderApi = DataGeocoder(geocodingClient)

    val waterBodyResolver: WaterBodyResolver = DataWaterBodyResolver(waterBodyClassifier)

    val locationController: LocationController = LocationController(appContext)

    // ────────────── 设置存储（N16 注入点） ──────────────

    val settingsStore: SettingsStore = SharedPreferencesSettingsStore(appContext)

    companion object {
        private const val TAG = "TideSpotApp"

        /** CHG-25：运行期凭据文件名（App 私有目录；不入库、不打包）。 */
        private const val CREDENTIAL_FILE = "copernicus.properties"
    }
}

/**
 * [SettingsStore] 的 SharedPreferences 实现（CHG-16⑱ 批次 3 落地）：仅键值读写，
 * 语义（收敛/清洗/默认值）全部在 AppSettings / SettingsLogic 纯函数层。
 */
class SharedPreferencesSettingsStore(context: Context) : SettingsStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun readString(key: String): String? = prefs.getString(key, null)

    override fun writeString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    override fun readBoolean(key: String, default: Boolean): Boolean =
        prefs.getBoolean(key, default)

    override fun writeBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    companion object {
        private const val PREFS_NAME = "tidespot.settings"
    }
}
