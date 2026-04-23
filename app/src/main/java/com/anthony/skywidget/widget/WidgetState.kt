package com.anthony.skywidget.widget

import com.anthony.skywidget.data.BarometerTrend
import com.anthony.skywidget.data.WeatherCode

/**
 * Everything the widget's RemoteViews layer needs to paint itself. Kept as
 * a single immutable data class so the worker can assemble it off the main
 * thread and hand it to the renderer in one go — avoiding partial-update
 * flicker.
 */
data class WidgetState(
    val temperatureC: Int,          // rounded
    val highC: Int,                 // rounded
    val lowC: Int,                  // rounded
    val precipPct: Int,              // 0 if API returned null
    val condition: WeatherCode.Condition,
    val isDaytime: Boolean,         // sun altitude > -6° (picks day vs night icon variant)
    val barometer: BarometerTrend.Trend,
    val sunriseHhMm12: String,      // "6:08 AM"
    val sunsetHhMm12: String,       // "8:13 PM"
    val daylightText: String,       // "14h 5m" — or error text for polar regions
    val topGradientArgb: Int,
    val botGradientArgb: Int,
    val textArgb: Int
) {
    companion object {
        /**
         * Pre-populated placeholder shown before the first fetch completes, so the
         * widget never displays blank cells. Uses neutral "noon" colors.
         */
        val Loading = WidgetState(
            temperatureC = 0,
            highC = 0,
            lowC = 0,
            precipPct = 0,
            condition = WeatherCode.Condition.PARTLY_CLOUDY,
            isDaytime = true,
            barometer = BarometerTrend.Trend.STEADY,
            sunriseHhMm12 = "—",
            sunsetHhMm12 = "—",
            daylightText = "—",
            topGradientArgb = 0xFF4A9BCF.toInt(),
            botGradientArgb = 0xFF8DC8E6.toInt(),
            textArgb = 0xFF2E1608.toInt()
        )
    }
}
