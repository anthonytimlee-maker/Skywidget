package com.anthony.skywidget.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Minimal client for the Open-Meteo forecast endpoint.
 *
 * We pull:
 *  - current: temperature, weather code, surface pressure, precip probability
 *  - hourly (past + now): surface pressure (for 1.5h barometer trend)
 *  - daily: today's high/low, weather code
 *
 * Kept framework-only (no Retrofit/OkHttp/Moshi) to minimize APK size and
 * avoid dependency churn. org.json is part of the Android framework.
 */
object OpenMeteoClient {

    private const val TAG = "OpenMeteoClient"
    private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"

    /**
     * Fetches the current snapshot. Runs on IO dispatcher.
     *
     * Throws [java.io.IOException] on network trouble or non-2xx HTTP status.
     */
    suspend fun fetch(latDeg: Double, lonDeg: Double): WeatherSnapshot = withContext(Dispatchers.IO) {
        val url = buildUrl(latDeg, lonDeg)
        Log.d(TAG, "GET $url")

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }

        try {
            if (conn.responseCode !in 200..299) {
                throw java.io.IOException("HTTP ${conn.responseCode}: ${conn.responseMessage}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun buildUrl(lat: Double, lon: Double): String {
        return "$BASE_URL" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,surface_pressure,precipitation_probability" +
            "&hourly=surface_pressure,precipitation_probability" +
            "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
            "&timezone=auto&past_hours=3&forecast_days=1"
    }

    private fun parse(json: String): WeatherSnapshot {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val hourly = root.getJSONObject("hourly")

        // Hourly pressure history (ISO strings + parallel pressure array).
        val hourlyTimes = hourly.getJSONArray("time")
        val hourlyPressures = hourly.getJSONArray("surface_pressure")
        val pressureHistory = buildList {
            for (i in 0 until hourlyTimes.length()) {
                val timeStr = hourlyTimes.getString(i)
                val pressureObj = if (hourlyPressures.isNull(i)) null else hourlyPressures.getDouble(i)
                if (pressureObj != null) {
                    add(PressurePoint(parseLocalIso(timeStr), pressureObj))
                }
            }
        }

        return WeatherSnapshot(
            temperatureC = current.getDouble("temperature_2m"),
            weatherCode = current.getInt("weather_code"),
            surfacePressureHpa = current.getDouble("surface_pressure"),
            precipProbabilityPct = if (current.isNull("precipitation_probability")) null
                                   else current.getInt("precipitation_probability"),
            dailyHighC = daily.getJSONArray("temperature_2m_max").getDouble(0),
            dailyLowC = daily.getJSONArray("temperature_2m_min").getDouble(0),
            dailyWeatherCode = daily.getJSONArray("weather_code").getInt(0),
            pressureHistory = pressureHistory,
            timezoneId = root.optString("timezone", "UTC")
        )
    }

    /**
     * Open-Meteo returns ISO-8601 strings *without* a zone suffix (the zone is
     * set separately by the `timezone` field). We parse as local and treat it
     * as naive — callers that need absolute time should pair with [timezoneId].
     */
    private fun parseLocalIso(s: String): String = s
}

/** Single hourly pressure reading. Time is the local ISO string Open-Meteo returned. */
data class PressurePoint(val localIsoTime: String, val hpa: Double)

data class WeatherSnapshot(
    val temperatureC: Double,
    val weatherCode: Int,
    val surfacePressureHpa: Double,
    val precipProbabilityPct: Int?,
    val dailyHighC: Double,
    val dailyLowC: Double,
    val dailyWeatherCode: Int,
    val pressureHistory: List<PressurePoint>,
    val timezoneId: String
)
