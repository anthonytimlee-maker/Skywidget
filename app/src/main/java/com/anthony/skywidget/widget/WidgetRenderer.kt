package com.anthony.skywidget.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.widget.RemoteViews
import com.anthony.skywidget.R
import com.anthony.skywidget.data.BarometerTrend
import com.anthony.skywidget.data.WeatherCode

/**
 * Builds the RemoteViews for the widget.
 *
 * Responsibilities:
 *  - Paint the sky gradient as a dynamic bitmap on the background ImageView.
 *  - Set all text content from WidgetState.
 *  - Dynamically tint text + tintable icons to stay legible on any sky.
 *  - Swap weather and barometer icons based on current conditions.
 *  - Wire a tap-to-refresh PendingIntent so touching the widget triggers
 *    an immediate one-shot refresh via SkyWidgetProvider.
 */
object WidgetRenderer {

    private const val GRADIENT_BITMAP_W = 540
    private const val GRADIENT_BITMAP_H = 220

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

        // Background gradient
        views.setImageViewBitmap(
            R.id.background,
            buildGradientBitmap(state.topGradientArgb, state.botGradientArgb)
        )

        // Text content
        views.setTextViewText(R.id.temp_now, "${state.temperatureC}°")
        views.setTextViewText(R.id.temp_hl, "${state.highC}° / ${state.lowC}°")
        views.setTextViewText(R.id.precip_pct, "${state.precipPct}%")
        views.setTextViewText(R.id.sunrise, state.sunriseHhMm12)
        views.setTextViewText(R.id.sunset, state.sunsetHhMm12)
        views.setTextViewText(R.id.daylight, state.daylightText)

        // Dynamic text colors
        for (id in TEXT_IDS) {
            views.setTextColor(id, state.textArgb)
        }

        // Tint small icons to match (not the hero weather icon — it keeps its colors)
        for (id in TINTABLE_ICON_IDS) {
            views.setInt(id, "setColorFilter", state.textArgb)
        }

        // Dynamic icon swaps
        views.setImageViewResource(R.id.ico_weather, iconForCondition(state.condition, state.isDaytime))
        views.setImageViewResource(R.id.ico_baro, barometerIcon(state.barometer))

        // Tap-to-refresh. The PendingIntent points at SkyWidgetProvider with a
        // custom ACTION_REFRESH; the provider's onReceive enqueues a one-shot
        // RefreshWorker. FLAG_IMMUTABLE is required for Android 12+.
        val tapIntent = Intent(context, SkyWidgetProvider::class.java).apply {
            action = SkyWidgetProvider.ACTION_REFRESH
        }
        val pending = PendingIntent.getBroadcast(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Attach the click to the background ImageView so the entire widget
        // surface is tappable. The press-state overlay on that view gives
        // subtle dim feedback when pressed.
        views.setOnClickPendingIntent(R.id.background, pending)

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
