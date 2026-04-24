package com.anthony.skywidget.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.widget.RemoteViews
import com.anthony.skywidget.R
import com.anthony.skywidget.data.BarometerTrend
import com.anthony.skywidget.data.WeatherCode

/**
 * Final renderer.
 *
 * Everything from Step 6 plus:
 *  - Dynamic text color driven by state.textArgb (dark on bright skies,
 *    light on dark skies; transitions around civil twilight).
 *  - ColorFilter applied to all tintable icons via RemoteViews.setInt so
 *    the vector strokes stay legible against any sky gradient.
 */
object WidgetRenderer {

    private const val GRADIENT_BITMAP_W = 540
    private const val GRADIENT_BITMAP_H = 220

    // IDs we need to re-color at paint time.
    private val TEXT_IDS = intArrayOf(
        R.id.temp_now, R.id.temp_hl, R.id.precip_pct,
        R.id.sunrise, R.id.sunset, R.id.daylight
    )
    private val TINTABLE_ICON_IDS = intArrayOf(
        R.id.ico_precip, R.id.ico_baro,
        R.id.ico_sunrise, R.id.ico_sunset, R.id.ico_daylight
    )

    fun build(context: Context, state: WidgetState): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.sky_widget)

        // Background gradient.
        views.setImageViewBitmap(
            R.id.background,
            buildGradientBitmap(state.topGradientArgb, state.botGradientArgb)
        )

        // Text content.
        views.setTextViewText(R.id.temp_now, "${state.temperatureC}°")
        views.setTextViewText(R.id.temp_hl, "${state.highC}° / ${state.lowC}°")
        views.setTextViewText(R.id.precip_pct, "${state.precipPct}%")
        views.setTextViewText(R.id.sunrise, state.sunriseHhMm12)
        views.setTextViewText(R.id.sunset, state.sunsetHhMm12)
        views.setTextViewText(R.id.daylight, state.daylightText)

        // Dynamic text colors — dark for bright skies, light for dark skies.
        for (id in TEXT_IDS) {
            views.setTextColor(id, state.textArgb)
        }

        // Tint the small icons to match. The hero weather icon keeps its
        // authored colors (yellow sun, white cloud, etc.) — tinting it would
        // flatten it into a silhouette.
        for (id in TINTABLE_ICON_IDS) {
            views.setInt(id, "setColorFilter", state.textArgb)
        }

        // Dynamic icon swaps.
        views.setImageViewResource(R.id.ico_weather, iconForCondition(state.condition, state.isDaytime))
        views.setImageViewResource(R.id.ico_baro, barometerIcon(state.barometer))

        return views
    }

    private fun iconForCondition(c: WeatherCode.Condition, daytime: Boolean): Int = when (c) {
        WeatherCode.Condition.CLEAR         -> if (daytime) R.drawable.ic_clear_day else R.drawable.ic_clear_night
        WeatherCode.Condition.PARTLY_CLOUDY -> if (daytime) R.drawable.ic_partly_cloudy_day else R.drawable.ic_partly_cloudy_night
        WeatherCode.Condition.OVERCAST      -> R.drawable.ic_overcast
        WeatherCode.Condition.FOG           -> R.drawable.ic_fog
        WeatherCode.Condition.RAIN          -> R.drawable.ic_rain
        WeatherCode.Condition.SNOW          -> R.drawable.ic_snow
        WeatherCode.Condition.THUNDERSTORM  -> R.drawable.ic_thunderstorm
    }

    private fun barometerIcon(t: BarometerTrend.Trend): Int = when (t) {
        BarometerTrend.Trend.RISING -> R.drawable.ic_baro_up
        BarometerTrend.Trend.FALLING -> R.drawable.ic_baro_down
        BarometerTrend.Trend.STEADY -> R.drawable.ic_baro_flat
    }

    private fun buildGradientBitmap(topArgb: Int, botArgb: Int): Bitmap {
        val bmp = Bitmap.createBitmap(GRADIENT_BITMAP_W, GRADIENT_BITMAP_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val drawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topArgb, botArgb)
        ).apply {
            cornerRadius = 40f
            setBounds(0, 0, GRADIENT_BITMAP_W, GRADIENT_BITMAP_H)
        }
        drawable.draw(canvas)
        return bmp
    }
}
