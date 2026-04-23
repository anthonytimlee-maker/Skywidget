package com.anthony.skywidget.data

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * Determines pressure trend by comparing the current surface pressure with
 * the closest hourly reading from ~1.5 hours ago.
 *
 * A ±0.5 hPa dead-zone avoids flapping between "rising" and "falling" on
 * tiny fluctuations. Tuning this higher gives calmer behavior; lower gives
 * more sensitivity. 0.5 hPa was chosen to match typical barometer dials.
 */
object BarometerTrend {

    enum class Trend { RISING, STEADY, FALLING }

    private const val THRESHOLD_HPA = 0.5
    private const val WINDOW_HOURS = 1.5

    fun compute(snapshot: WeatherSnapshot, now: ZonedDateTime): Result {
        val zone = runCatching { ZoneId.of(snapshot.timezoneId) }.getOrDefault(ZoneId.systemDefault())
        val nowInApiZone = now.withZoneSameInstant(zone)
        val target = nowInApiZone.minusMinutes((WINDOW_HOURS * 60).toLong())

        var bestMatch: PressurePoint? = null
        var bestDiffMs = Long.MAX_VALUE
        for (point in snapshot.pressureHistory) {
            val pointTime = try {
                LocalDateTime.parse(point.localIsoTime).atZone(zone)
            } catch (e: Exception) { continue }
            val diff = abs(Duration.between(target, pointTime).toMillis())
            if (diff < bestDiffMs) {
                bestDiffMs = diff
                bestMatch = point
            }
        }

        if (bestMatch == null) return Result(Trend.STEADY, 0.0, null)

        val delta = snapshot.surfacePressureHpa - bestMatch.hpa
        val trend = when {
            delta > THRESHOLD_HPA -> Trend.RISING
            delta < -THRESHOLD_HPA -> Trend.FALLING
            else -> Trend.STEADY
        }
        return Result(trend, delta, bestMatch.hpa)
    }

    data class Result(val trend: Trend, val deltaHpa: Double, val pastHpa: Double?)
}
