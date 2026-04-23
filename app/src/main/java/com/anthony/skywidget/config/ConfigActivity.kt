package com.anthony.skywidget.config

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.anthony.skywidget.R
import com.anthony.skywidget.location.LocationResolver
import com.anthony.skywidget.worker.RefreshWorker

/**
 * One-time configuration screen shown when the widget is placed on the home
 * screen. Two paths:
 *
 *  - "Use my location" — requests the runtime location permission, saves the
 *    returned fix so subsequent worker runs can use it.
 *  - "Enter manually" — inputs for latitude/longitude, persisted to the same
 *    SharedPreferences key the worker reads.
 *
 * When the user completes setup we set RESULT_OK with the widget id in the
 * intent; without this, Android immediately removes the widget we were about
 * to create. That's the one gotcha to remember.
 */
class ConfigActivity : AppCompatActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Default to "cancelled" — if the user backs out we want the widget
        // to not be placed.
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        setContentView(R.layout.activity_config)

        val latInput = findViewById<EditText>(R.id.input_lat)
        val lonInput = findViewById<EditText>(R.id.input_lon)
        val status = findViewById<TextView>(R.id.status)

        findViewById<Button>(R.id.btn_use_device).setOnClickListener {
            requestLocationPermissionOrFinish(status)
        }

        findViewById<Button>(R.id.btn_save_manual).setOnClickListener {
            val lat = latInput.text.toString().toDoubleOrNull()
            val lon = lonInput.text.toString().toDoubleOrNull()
            if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                Toast.makeText(this, "Please enter valid coordinates.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            LocationResolver(this).saveManual(lat, lon)
            finishOk()
        }
    }

    private fun requestLocationPermissionOrFinish(status: TextView) {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            status.text = getString(R.string.config_status_using_device)
            finishOk()
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQ_CODE_LOCATION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CODE_LOCATION) {
            val anyGranted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            if (anyGranted) {
                finishOk()
            } else {
                // User denied — stay on the config screen so they can enter manually.
                Toast.makeText(
                    this,
                    "Permission denied. Please enter coordinates manually.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun finishOk() {
        // Kick off an immediate refresh so the widget paints real data right away
        // rather than waiting up to 15 minutes for the periodic worker.
        RefreshWorker.enqueueOneShot(this)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    companion object {
        private const val REQ_CODE_LOCATION = 42
    }
}
