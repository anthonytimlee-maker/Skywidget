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
 * DIAGNOSTIC RENDERER — STEP 6
 *
 * Added: gradient bitmap painted onto the background ImageView via
 * setImageViewBitmap. This is the final mechanism to verify — if it works,
 * we've proven the full layout approach and can move to Step 7 (polish
 * and original-layout restoration).
 *
 * Still no color filters on icons/text (those come in Step 7).
 */
object WidgetRenderer {

    private const val GRADIENT_BITMAP_W = 540
    private const val GRADIENT_BITMAP_H = 220

    fun build(context: Context, state: WidgetState): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.sky_widget)

        // Background gradient — dynamic bitmap painted with the state's sky colors.
        views.setImageViewBitmap(
            R.id.background,
            buildGradientBitmap(state.topGradientArgb, state.botGradientArgb)
        )

        // Text values
        views.setTextViewText(R.id.temp_now, "${state.temperatureC}°")
        views.setTextViewText(R.id.temp_hl, "${state.highC}° / ${state.lowC}°")
        views.setTextViewText(R.id.precip_pct, "${state.precipPct}%")
        views.setTextViewText(R.id.sunrise, state.sunriseHhMm12)
        views.setTextViewText(R.id.sunset, state.sunsetHhMm12)
        views.setTextViewText(R.id.daylight, state.daylightText)

        // Dynamic icon swaps
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
