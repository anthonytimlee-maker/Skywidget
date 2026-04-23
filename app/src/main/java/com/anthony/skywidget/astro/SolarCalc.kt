package com.anthony.skywidget.astro

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Astronomical calculations for sunrise, sunset, and sun altitude.
 *
 * Based on NOAA Solar Calculator algorithms (public domain). All math here is
 * DST-safe because input is a [ZonedDateTime] which carries its own time zone
 * rules — we never manipulate raw clock offsets ourselves.
 *
 * Accuracy: ~±1 minute for sunrise/sunset at temperate latitudes; sun altitude
 * accurate to better than 0.1°. This is more than enough for a widget that
 * drives sky color.
 */
object SolarCalc {

    private const val DEG_PER_RAD = 180.0 / PI

    /** Computes sun altitude above the horizon, in degrees. */
    fun altitudeDeg(at: ZonedDateTime, latDeg: Double, lonDeg: Double): Double {
        val utc = at.withZoneSameInstant(ZoneId.of("UTC"))
        val jd = julianDay(utc)
        val declDeg = solarDeclinationDeg(jd)
        val eotMin = equationOfTimeMin(jd)

        // Minutes past UTC midnight.
        val utcMin = utc.hour * 60 + utc.minute + utc.second / 60.0
        // Solar time in minutes past local solar midnight.
        val solarTimeMin = utcMin + eotMin + 4.0 * lonDeg
        val hourAngleRad = Math.toRadians(solarTimeMin / 4.0 - 180.0)

        val latRad = Math.toRadians(latDeg)
        val declRad = Math.toRadians(declDeg)
        val altRad = asin(
            sin(latRad) * sin(declRad) +
                cos(latRad) * cos(declRad) * cos(hourAngleRad)
        )
        return altRad * DEG_PER_RAD
    }

    /**
     * Sunrise, sunset, and solar noon for the given date at the given location.
     *
     * All returned times are in the zone of [date]. When the location is in
     * perpetual day or night on the given date, the corresponding fields are null.
     */
    fun riseSet(date: ZonedDateTime, latDeg: Double, lonDeg: Double): Events {
        val utcNoon = date.withZoneSameInstant(ZoneId.of("UTC"))
            .withHour(12).withMinute(0).withSecond(0).withNano(0)
        val jd = julianDay(utcNoon)
        val declRad = Math.toRadians(solarDeclinationDeg(jd))
        val eotMin = equationOfTimeMin(jd)
        val latRad = Math.toRadians(latDeg)

        // -0.833° accounts for solar disc radius + standard atmospheric refraction.
        val zenithRad = Math.toRadians(90.833)
        val cosH = (cos(zenithRad) - sin(latRad) * sin(declRad)) /
            (cos(latRad) * cos(declRad))

        return when {
            cosH > 1.0 -> Events(null, null, null, Reason.POLAR_NIGHT)
            cosH < -1.0 -> Events(null, null, null, Reason.POLAR_DAY)
            else -> {
                val hDeg = acos(cosH) * DEG_PER_RAD
                val solarNoonUtcMin = 720.0 - 4.0 * lonDeg - eotMin
                val sunriseUtcMin = solarNoonUtcMin - hDeg * 4.0
                val sunsetUtcMin = solarNoonUtcMin + hDeg * 4.0
                val zone = date.zone
                Events(
                    sunrise = utcMinutesToZoned(date, sunriseUtcMin, zone),
                    sunset = utcMinutesToZoned(date, sunsetUtcMin, zone),
                    solarNoon = utcMinutesToZoned(date, solarNoonUtcMin, zone),
                    reason = Reason.NORMAL
                )
            }
        }
    }

    /** Convenience: daylight duration for a given [Events]. Null if no sunrise/sunset. */
    fun daylight(events: Events): Duration? {
        val rise = events.sunrise ?: return null
        val set = events.sunset ?: return null
        return Duration.between(rise, set)
    }

    // --- Internals ---

    /** Julian Day number for a UTC moment. */
    private fun julianDay(utc: ZonedDateTime): Double {
        val y0 = utc.year
        val m0 = utc.monthValue
        val d = utc.dayOfMonth + (utc.hour + utc.minute / 60.0 + utc.second / 3600.0) / 24.0
        val (y, m) = if (m0 <= 2) Pair(y0 - 1, m0 + 12) else Pair(y0, m0)
        val a = floor(y / 100.0)
        val b = 2.0 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) +
            floor(30.6001 * (m + 1)) +
            d + b - 1524.5
    }

    /** Solar declination in degrees. */
    private fun solarDeclinationDeg(jd: Double): Double {
        val n = jd - 2451545.0
        val meanLongDeg = (280.460 + 0.9856474 * n).mod360()
        val meanAnomRad = Math.toRadians((357.528 + 0.9856003 * n).mod360())
        val eclLongRad = Math.toRadians(
            meanLongDeg + 1.915 * sin(meanAnomRad) + 0.020 * sin(2 * meanAnomRad)
        )
        val obliquityRad = Math.toRadians(23.439 - 0.0000004 * n)
        return asin(sin(obliquityRad) * sin(eclLongRad)) * DEG_PER_RAD
    }

    /** Equation of time in minutes. Clamped to ±20 min envelope as a safety net. */
    private fun equationOfTimeMin(jd: Double): Double {
        val n = jd - 2451545.0
        val meanLongDeg = (280.460 + 0.9856474 * n).mod360()
        val meanAnomRad = Math.toRadians((357.528 + 0.9856003 * n).mod360())
        val eclLongDeg = meanLongDeg + 1.915 * sin(meanAnomRad) + 0.020 * sin(2 * meanAnomRad)
        val alphaDeg = Math.toDegrees(
            Math.atan2(
                cos(Math.toRadians(23.439)) * sin(Math.toRadians(eclLongDeg)),
                cos(Math.toRadians(eclLongDeg))
            )
        )
        var eot = 4.0 * (meanLongDeg - alphaDeg)
        if (eot > 20) eot -= 1440
        if (eot < -20) eot += 1440
        return eot
    }

    private fun utcMinutesToZoned(
        referenceDate: ZonedDateTime,
        utcMinutesPastMidnight: Double,
        targetZone: ZoneId
    ): ZonedDateTime {
        val refUtc = referenceDate.withZoneSameInstant(ZoneId.of("UTC"))
            .withHour(0).withMinute(0).withSecond(0).withNano(0)
        val seconds = (utcMinutesPastMidnight * 60).toLong()
        val nanos = ((utcMinutesPastMidnight * 60 - seconds) * 1_000_000_000).toLong()
        return refUtc.plusSeconds(seconds).plusNanos(nanos)
            .withZoneSameInstant(targetZone)
    }

    private fun Double.mod360(): Double {
        val r = this % 360.0
        return if (r < 0) r + 360.0 else r
    }

    // --- Types ---

    enum class Reason { NORMAL, POLAR_DAY, POLAR_NIGHT }

    data class Events(
        val sunrise: ZonedDateTime?,
        val sunset: ZonedDateTime?,
        val solarNoon: ZonedDateTime?,
        val reason: Reason
    )
}
