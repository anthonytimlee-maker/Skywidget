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
 * Location resolution with two modes:
 *
 *  - AUTO: use device fused location, fall back to cached last-known,
 *          fall back to null.
 *  - MANUAL: use the explicitly-saved coordinates, never override with
 *           live device location.
 *
 * The mode is set by which method the user calls:
 *   - saveManual(lat, lon) → MANUAL
 *   - useDeviceLocation()  → AUTO
 *
 * This prevents the previous bug where entering a manual Sydney location
 * was silently overwritten by the live Vancouver fix on next refresh.
 */
class LocationResolver(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }

    /** Entry point. Returns best available location, or null if we have nothing. */
    suspend fun resolve(): Resolved? {
        val mode = prefs.getString(KEY_MODE, MODE_AUTO) ?: MODE_AUTO
        Log.d(TAG, "resolve() mode=$mode")

        if (mode == MODE_MANUAL) {
            // User explicitly set coordinates; honor them and do not overwrite.
            val manual = readCache() ?: return null
            return Resolved(manual.first, manual.second, Source.MANUAL)
        }

        // AUTO mode: try live, fall back to cache.
        val live = tryLiveLocation()
        if (live != null) {
            saveCache(live.lat, live.lon)
            return Resolved(live.lat, live.lon, Source.LIVE)
        }
        val cached = readCache()
        if (cached != null) return Resolved(cached.first, cached.second, Source.CACHED)
        return null
    }

    /** Persist manual coordinates and switch to MANUAL mode. */
    fun saveManual(lat: Double, lon: Double) {
        prefs.edit()
            .putString(KEY_MODE, MODE_MANUAL)
            .putString(KEY_LAT, lat.toString())
            .putString(KEY_LON, lon.toString())
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "saveManual: $lat, $lon (mode=MANUAL)")
    }

    /** Switch to AUTO mode, causing next resolve() to use live device location. */
    fun useDeviceLocation() {
        prefs.edit()
            .putString(KEY_MODE, MODE_AUTO)
            .apply()
        Log.d(TAG, "useDeviceLocation: mode=AUTO")
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
        val last = tryLastLocation()
        if (last != null) {
            Log.d(TAG, "Resolved from getLastLocation(): ${last.lat}, ${last.lon}")
            return last
        }
        Log.d(TAG, "getLastLocation returned null, falling back to getCurrentLocation")

        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
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

    enum class Source { LIVE, CACHED, MANUAL }
    data class Resolved(val lat: Double, val lon: Double, val source: Source)

    companion object {
        private const val TAG = "LocationResolver"
        private const val PREFS_NAME = "sky_widget_prefs"
        const val KEY_MODE = "loc_mode"
        const val KEY_LAT = "loc_lat"
        const val KEY_LON = "loc_lon"
        const val KEY_SAVED_AT = "loc_saved_at"
        const val MODE_AUTO = "auto"
        const val MODE_MANUAL = "manual"
    }
}
