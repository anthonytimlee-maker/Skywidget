package com.anthony.skywidget.data

/**
 * Maps WMO weather codes (used by Open-Meteo) to a small enum of condition
 * categories we actually draw icons for. We intentionally collapse the full
 * WMO range (~30 codes) to 7 visual categories — a widget is too small to
 * distinguish "drizzle" from "light rain" graphically, and users don't care.
 *
 * WMO reference: https://open-meteo.com/en/docs#weathervariables
 */
object WeatherCode {

    enum class Condition {
        CLEAR,          // 0, 1
        PARTLY_CLOUDY,  // 2
        OVERCAST,       // 3
        FOG,            // 45, 48
        RAIN,           // 51, 53, 55, 61, 63, 65, 80, 81, 82
        SNOW,           // 71, 73, 75, 77, 85, 86
        THUNDERSTORM    // 95, 96, 99
    }

    fun categorize(code: Int): Condition = when (code) {
        0, 1 -> Condition.CLEAR
        2 -> Condition.PARTLY_CLOUDY
        3 -> Condition.OVERCAST
        45, 48 -> Condition.FOG
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> Condition.RAIN
        71, 73, 75, 77, 85, 86 -> Condition.SNOW
        95, 96, 99 -> Condition.THUNDERSTORM
        else -> Condition.PARTLY_CLOUDY // safe default; obscure codes fall here
    }

    /** Human-readable label for debug / accessibility. */
    fun label(condition: Condition): String = when (condition) {
        Condition.CLEAR -> "Clear"
        Condition.PARTLY_CLOUDY -> "Partly cloudy"
        Condition.OVERCAST -> "Overcast"
        Condition.FOG -> "Fog"
        Condition.RAIN -> "Rain"
        Condition.SNOW -> "Snow"
        Condition.THUNDERSTORM -> "Thunderstorm"
    }
}
