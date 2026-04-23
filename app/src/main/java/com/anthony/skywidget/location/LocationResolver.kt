package com.anthony.skywidget.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Location resolution with three-tier fallback:
 *
 *  1. Live fused location, if permission is granted and a recent fix is available.
 *  2. Last known cached location (persisted in SharedPreferences from the last
 *     successful fix or manual entry).
 *  3. Null — caller is expected to show the config activity so the user can
 *     enter coordinates manually.
 *
 * This matches the Stage 4a HTML behavior exactly.
 */
class LocationResolver(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    /** Entry point. Returns best available location, or null if we have nothing. */
    suspend fun resolve(): Resolved? {
        val live = tryLiveLocation()
        if (live != null) {
            saveCache(live.lat, live.lon)
            return Resolved(live.lat, live.lon, Source.LIVE)
        }
        val cached = readCache()
        if (cached != null) return Resolved(cached.first, cached.second, Source.CACHED)
        return null
    }

    /** Persists manual coordinates entered via the config activity. */
    fun saveManual(lat: Double, lon: Double) {
        saveCache(lat, lon)
    }

    private fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryLiveLocation(): LatLon? {
        if (!hasPermission()) {
            Log.d(TAG, "Location permission not granted; skipping live fix.")
            return null
        }

        // Try getLastLocation() first — it returns instantly if Google Play
        // Services has any cached location from this or any other app. Much
        // more reliable in WorkManager background contexts where
        // getCurrentLocation() often returns null without a recent fix.
        val last = tryLastLocation()
        if (last != null) {
            Log.d(TAG, "Resolved from getLastLocation(): ${last.lat}, ${last.lon}")
            return last
        }
        Log.d(TAG, "getLastLocation returned null, falling back to getCurrentLocation")

        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            // PRIORITY_BALANCED_POWER_ACCURACY: city-block-level accuracy without
            // waking the GPS chip. More than enough for sunrise/sunset math —
            // 100 m of error shifts sunrise by well under a second.
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc == null) Log.w(TAG, "getCurrentLocation returned null (no fix available)")
                    else Log.d(TAG, "Resolved from getCurrentLocation(): ${loc.latitude}, ${loc.longitude}")
                    cont.resume(loc?.let { LatLon(it.latitude, it.longitude) })
                }
                .addOnFailureListener {
                    Log.w(TAG, "getCurrentLocation failed: ${it.message}")
                    cont.resume(null)
                }
            cont.invokeOnCancellation { cts.cancel() }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryLastLocation(): LatLon? {
        return suspendCancellableCoroutine { cont ->
            fused.lastLocation
                .addOnSuccessListener { loc -> cont.resume(loc?.let { LatLon(it.latitude, it.longitude) }) }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    private fun saveCache(lat: Double, lon: Double) {
        prefs.edit()
            .putString(KEY_LAT, lat.toString())
            .putString(KEY_LON, lon.toString())
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun readCache(): Pair<Double, Double>? {
        val latStr = prefs.getString(KEY_LAT, null) ?: return null
        val lonStr = prefs.getString(KEY_LON, null) ?: return null
        return try { latStr.toDouble() to lonStr.toDouble() } catch (e: Exception) { null }
    }

    private data class LatLon(val lat: Double, val lon: Double)

    enum class Source { LIVE, CACHED }
    data class Resolved(val lat: Double, val lon: Double, val source: Source)

    companion object {
        private const val TAG = "LocationResolver"
        private const val PREFS_NAME = "sky_widget_prefs"
        const val KEY_LAT = "loc_lat"
        const val KEY_LON = "loc_lon"
        const val KEY_SAVED_AT = "loc_saved_at"
    }
}
