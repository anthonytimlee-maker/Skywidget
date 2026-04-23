package com.anthony.skywidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.anthony.skywidget.worker.RefreshWorker
import java.util.concurrent.TimeUnit

/**
 * Entry point for the widget lifecycle. Android calls methods here when the
 * widget is added, updated, or removed.
 *
 * We don't do any real work here beyond scheduling and cancelling the
 * periodic refresh. Actual data fetch + widget repaint happens in
 * [RefreshWorker].
 */
class SkyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate for ${appWidgetIds.size} widget(s)")

        // Paint an immediate placeholder so the cell isn't blank while we fetch.
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, WidgetRenderer.build(context, WidgetState.Loading))
        }

        // Kick off (or refresh) the periodic worker.
        schedulePeriodicRefresh(context)

        // Also run once right now so the first render isn't 15 minutes away.
        RefreshWorker.enqueueOneShot(context)
    }

    override fun onEnabled(context: Context) {
        // First widget added to home screen.
        schedulePeriodicRefresh(context)
    }

    override fun onDisabled(context: Context) {
        // Last widget removed — stop doing work.
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    private fun schedulePeriodicRefresh(context: Context) {
        // 15 min is the floor for PeriodicWorkRequest. Anything lower gets
        // silently rounded up by the framework.
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        private const val TAG = "SkyWidgetProvider"
        const val PERIODIC_WORK_NAME = "sky_widget_refresh"
    }
}
