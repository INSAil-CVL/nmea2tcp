// File: app/src/main/java/com/insail/nmeagpsserver/SystemLogsActivity.kt
package com.insail.nmeagpsserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat

class SystemLogsActivity : AppCompatActivity() {

    private lateinit var logsText: TextView

    private val uiLogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == GpsUsbForegroundService.ACTION_UI_LOG) {
                val line = intent.getStringExtra(GpsUsbForegroundService.EXTRA_TEXT) ?: return
                appendLine(line)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_logs)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.logs_system_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        logsText = findViewById(R.id.logsText)

        // Charger l’historique au démarrage
        val snapshot = LogStore.snapshot()
        if (snapshot.isNotEmpty()) {
            logsText.text = snapshot.joinToString("\n")
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(GpsUsbForegroundService.ACTION_UI_LOG)
        }
        ContextCompat.registerReceiver(
            this, uiLogReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(uiLogReceiver) } catch (_: Exception) {}
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_system_logs, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_clear_logs -> {
                LogStore.clear()
                logsText.text = ""
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun appendLine(line: String) {
        if (logsText.text.isNullOrEmpty()) {
            logsText.text = line
        } else {
            logsText.append("\n$line")
        }
    }
}
