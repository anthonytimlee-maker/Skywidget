package com.anthony.skywidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.anthony.skywidget.worker.RefreshWorker
import java.util.concurrent.TimeUnit

/**
 * Widget lifecycle:
 *
 *  - onEnabled: first widget placed. Kick off the periodic refresh so data
 *    starts flowing. Safe to call with KEEP policy even on reboots.
 *  - onDisabled: last widget removed. Cancel the periodic work so we don't
 *    keep refreshing data nobody's looking at.
 *  - onUpdate: called by Android when the home screen wants a repaint. We
 *    enqueue a one-shot refresh to freshen the data.
 *  - onReceive with ACTION_REFRESH: our custom intent, fired by the tap-to-
 *    refresh PendingIntent. Behaves like a manual onUpdate.
 */
class SkyWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "onEnabled — scheduling periodic refresh")
        schedulePeriodicRefresh(context)
        RefreshWorker.enqueueOneShot(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "onDisabled — cancelling periodic refresh")
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d(TAG, "onUpdate for ids ${appWidgetIds.toList()}")
        RefreshWorker.enqueueOneShot(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            Log.d(TAG, "Tap-to-refresh received")
            RefreshWorker.enqueueOneShot(context)
        }
    }

    private fun schedulePeriodicRefresh(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(
            15, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        private const val TAG = "SkyWidgetProvider"
        private const val PERIODIC_WORK_NAME = "sky_widget_periodic_refresh"

        /** Custom intent action for tap-to-refresh. */
        const val ACTION_REFRESH = "com.anthony.skywidget.ACTION_REFRESH"
    }
}
