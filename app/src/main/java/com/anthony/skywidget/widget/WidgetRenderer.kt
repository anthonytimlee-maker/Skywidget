package com.anthony.skywidget.widget

import android.content.Context
import android.widget.RemoteViews
import com.anthony.skywidget.R

/**
 * DIAGNOSTIC RENDERER — STEP 2
 *
 * Added: text values for H/L, sunrise, sunset, daylight.
 * Still no icons, no gradient bitmap, no color filters.
 *
 * If this version fails to load the widget, the problem is in text rendering
 * or LinearLayout structure, not in images. If it works, we move on to icons.
 */
object WidgetRenderer {

    fun build(context: Context, state: WidgetState): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.sky_widget)

        views.setTextViewText(R.id.temp_now, "${state.temperatureC}°")
        views.setTextViewText(R.id.temp_hl, "${state.highC}° / ${state.lowC}°")
        views.setTextViewText(R.id.sunrise, state.sunriseHhMm12)
        views.setTextViewText(R.id.sunset, state.sunsetHhMm12)
        views.setTextViewText(R.id.daylight, state.daylightText)

        return views
    }
}
