// File: app/src/main/java/com/insail/nmeagpsserver/SystemLogsActivity.kt
package com.insail.nmeagpsserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat

class SystemLogsActivity : ThemedActivity() {

    private lateinit var logsText: TextView
    private lateinit var overflowBtn: ImageButton

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
        // 🔒 Empêche l’overflow natif
        toolbar.menu.clear()
        toolbar.overflowIcon = null

        supportActionBar?.title = getString(R.string.logs_system_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        overflowBtn = findViewById(R.id.overflowBtn)
        overflowBtn.setOnClickListener { showOverflowMenu() }

        logsText = findViewById(R.id.logsText)

        val snapshot = LogStore.snapshot()
        if (snapshot.isNotEmpty()) {
            logsText.text = snapshot.joinToString("\n")
        }
    }

    private fun showOverflowMenu() {
        val wrapper = ContextThemeWrapper(this, R.style.App_PopupWrapper)
        val popup = PopupMenu(wrapper, overflowBtn)
        popup.menuInflater.inflate(R.menu.menu_system_logs, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_clear_logs -> { LogStore.clear(); logsText.text = ""; true }
                else -> false
            }
        }
        popup.show()
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

    private fun appendLine(line: String) {
        if (logsText.text.isNullOrEmpty()) {
            logsText.text = line
        } else {
            logsText.append("\n$line")
        }
    }
}
