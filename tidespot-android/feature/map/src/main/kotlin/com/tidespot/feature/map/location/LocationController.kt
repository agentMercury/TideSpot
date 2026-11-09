package com.tidespot.feature.map.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/** 一次定位结果；accuracyMeters 为精度半径（米），用于 UI 展示精度（F-1.2）。 */
data class LocationFix(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float?,
) {
    companion object {
        fun from(location: Location): LocationFix =
            LocationFix(
                lat = location.latitude,
                lon = location.longitude,
                accuracyMeters = location.accuracy.takeIf { it > 0f },
            )
    }
}

/** GMS 可用性探测（F-1.2 双通道判据）。 */
object GmsDetector {
    fun isGooglePlayServicesAvailable(context: Context): Boolean =
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}

/**
 * 定位控制器（Android 侧薄封装，逻辑判定在状态机/纯函数内）：
 * - 有 GMS：FusedLocationProviderClient.getCurrentLocation（BALANCED 精度）；
 * - 无 GMS：回退 LocationManager.requestSingleUpdate(GPS_PROVIDER)；
 *   GPS 关闭时退最后已知位置（GPS → NETWORK）。
 * 回调统一在主线程发出，且带超时兜底（只回调一次）。
 */
class LocationController(
    private val context: Context,
    private val preferFused: Boolean = GmsDetector.isGooglePlayServicesAvailable(context),
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 当前生效的定位通道，供 UI/日志标注（F-1.2 验收观察点）。 */
    val usesFusedChannel: Boolean get() = preferFused

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun requestCurrentLocation(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onResult: (LocationFix?) -> Unit,
    ) {
        if (!hasLocationPermission()) {
            onResult(null)
            return
        }
        var delivered = false
        fun deliver(fix: LocationFix?) {
            if (delivered) return
            delivered = true
            mainHandler.post { onResult(fix) }
        }
        val timeout = Runnable { deliver(null) }
        mainHandler.postDelayed(timeout, timeoutMs)
        fun onFirst(fix: LocationFix?) {
            mainHandler.removeCallbacks(timeout)
            deliver(fix)
        }

        if (preferFused) {
            try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token,
                )
                    .addOnSuccessListener { location -> onFirst(location?.let { LocationFix.from(it) }) }
                    .addOnFailureListener { onFirst(lastKnownViaLocationManager()) }
                    .addOnCanceledListener { onFirst(null) }
            } catch (e: Exception) {
                onFirst(lastKnownViaLocationManager())
            }
        } else {
            requestSingleViaLocationManager { location -> onFirst(location?.let { LocationFix.from(it) }) }
        }
    }

    /** F-1.2 无 GMS 回退：LocationManager(GPS_PROVIDER) 单次更新。 */
    @SuppressLint("MissingPermission")
    private fun requestSingleViaLocationManager(onLocation: (Location?) -> Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                runCatching { lm.removeUpdates(this) }
                onLocation(location)
            }
        }
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
            } else {
                onLocation(
                    lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER),
                )
            }
        } catch (e: SecurityException) {
            onLocation(null)
        } catch (e: Exception) {
            onLocation(null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownViaLocationManager(): LocationFix? = try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        (lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))
            ?.let { LocationFix.from(it) }
    } catch (e: Exception) {
        null
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}