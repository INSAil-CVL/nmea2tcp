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
                // Demande d'arrêt SANS relancer en foreground
                val stopIntent = Intent(this, GpsUsbForegroundService::class.java)
                    .setAction(GpsUsbForegroundService.ACTION_STOP)

                try {
                    // Si le service tourne, on lui envoie l'intent STOP
                    startService(stopIntent)
                } catch (_: Exception) {
                    // Fallback: tente un stop direct
                    stopService(Intent(this, GpsUsbForegroundService::class.java))
                }

                // Ceinture et bretelles : on purge les notifs de l'app
                androidx.core.app.NotificationManagerCompat.from(this).cancelAll()
                finish()
            }

            .setNegativeButton(R.string.stop_service_no) { _, _ ->
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}
