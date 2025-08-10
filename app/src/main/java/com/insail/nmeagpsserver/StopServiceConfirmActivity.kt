// File: app/src/main/java/com/insail/nmeagpsserver/StopServiceConfirmActivity.kt
package com.insail.nmeagpsserver

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import android.content.Intent

class StopServiceConfirmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.stop_service_title))
            .setMessage(getString(R.string.stop_service_confirm))
            .setPositiveButton(R.string.stop_service_yes) { _, _ ->
                val stopIntent = Intent(this, GpsUsbForegroundService::class.java)
                    .setAction(GpsUsbForegroundService.ACTION_STOP)
                try { startService(stopIntent) } catch (_: Exception) {
                    stopService(Intent(this, GpsUsbForegroundService::class.java))
                }
                androidx.core.app.NotificationManagerCompat.from(this).cancelAll()
                finish()
            }
            .setNegativeButton(R.string.stop_service_no) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
