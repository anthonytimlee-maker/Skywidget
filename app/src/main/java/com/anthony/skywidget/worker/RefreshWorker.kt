package com.anthony.skywidget.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anthony.skywidget.astro.SolarCalc
import com.anthony.skywidget.data.BarometerTrend
import com.anthony.skywidget.data.OpenMeteoClient
import com.anthony.skywidget.data.WeatherCode
import com.anthony.skywidget.location.LocationResolver
import com.anthony.skywidget.sky.SkyPalette
import com.anthony.skywidget.widget.SkyWidgetProvider
import com.anthony.skywidget.widget.WidgetRenderer
import com.anthony.skywidget.widget.WidgetState
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Assembles widget state from live data and repaints every mounted widget.
 *
 * Lives in a [CoroutineWorker] so the network call runs off the main thread.
 * WorkManager persists the schedule across reboots and handles backoff on
 * network failures automatically.
 *
 * Failure handling: on any failure we return [Result.retry] rather than
 * [Result.failure] — this tells WorkManager to try again with exponential
 * backoff instead of giving up. The widget keeps showing its last successful
 * render in the meantime; it only goes blank on first launch before any
 * successful fetch.
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "RefreshWorker starting")
        return try {
            val loc = LocationResolver(applicationContext).resolve()
            if (loc == null) {
                Log.w(TAG, "No location available (permission denied and no cache); skipping.")
                // Paint a distinct sentinel so we can tell "no location" apart
                // from a real 0° reading. During diagnostic phase this makes
                // failures visible; Stage 4c will replace with a proper error UI.
                paintAll(WidgetState.Loading.copy(temperatureC = -99))
                return Result.success()
            }
            Log.d(TAG, "Location resolved: ${loc.lat}, ${loc.lon} (source=${loc.source})")

            val snapshot = OpenMeteoClient.fetch(loc.lat, loc.lon)
            val now = ZonedDateTime.now()

            val altDeg = SolarCalc.altitudeDeg(now, loc.lat, loc.lon)
            val events = SolarCalc.riseSet(now, loc.lat, loc.lon)
            val setting = events.solarNoon?.let { now.isAfter(it) } ?: false
            val gradient = SkyPalette.resolve(altDeg, setting)

            val baro = BarometerTrend.compute(snapshot, now)

            val state = WidgetState(
                temperatureC = snapshot.temperatureC.roundToInt(),
                highC = snapshot.dailyHighC.roundToInt(),
                lowC = snapshot.dailyLowC.roundToInt(),
                precipPct = snapshot.precipProbabilityPct ?: 0,
                condition = WeatherCode.categorize(snapshot.weatherCode),
                isDaytime = altDeg > -6.0,   // civil twilight threshold
                barometer = baro.trend,
                sunriseHhMm12 = events.sunrise?.format(TIME_FMT) ?: "—",
                sunsetHhMm12 = events.sunset?.format(TIME_FMT) ?: "—",
                daylightText = daylightText(events),
                topGradientArgb = gradient.topArgb,
                botGradientArgb = gradient.botArgb,
                textArgb = gradient.textArgb
            )

            paintAll(state)
            Log.d(TAG, "RefreshWorker success — alt=${"%.1f".format(altDeg)}°, temp=${state.temperatureC}°C")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "RefreshWorker failed: ${e.message}", e)
            Result.retry()
        }
    }

    private fun paintAll(state: WidgetState) {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val component = ComponentName(applicationContext, SkyWidgetProvider::class.java)
        val widgetIds = manager.getAppWidgetIds(component)
        val views = WidgetRenderer.build(applicationContext, state)
        for (id in widgetIds) {
            manager.updateAppWidget(id, views)
        }
    }

    private fun daylightText(events: SolarCalc.Events): String = when (events.reason) {
        SolarCalc.Reason.POLAR_DAY -> "24h 0m"
        SolarCalc.Reason.POLAR_NIGHT -> "0h 0m"
        SolarCalc.Reason.NORMAL -> SolarCalc.daylight(events)?.let { dur ->
            val total = dur.toMinutes()
            "${total / 60}h ${total % 60}m"
        } ?: "—"
    }

    companion object {
        private const val TAG = "RefreshWorker"
        private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a")

        /** Run once right away — used when the widget is first added. */
        fun enqueueOneShot(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
