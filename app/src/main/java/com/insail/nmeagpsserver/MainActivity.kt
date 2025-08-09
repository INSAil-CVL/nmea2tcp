package com.insail.nmeagpsserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var clientCountText: TextView
    private lateinit var usbStatusText: TextView
    private lateinit var logText: TextView
    private lateinit var nmeaText: TextView
    private lateinit var localIpText: TextView

    private val uiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                GpsUsbForegroundService.ACTION_UI_STATUS -> {
                    val status = intent.getStringExtra(GpsUsbForegroundService.EXTRA_TEXT) ?: return
                    usbStatusText.text = status
                    // option : colorer en fonction du contenu
                    val lower = status.lowercase()
                    val color = when {
                        lower.contains("connect") -> android.R.color.holo_green_dark
                        lower.contains("refus") || lower.contains("déconnect") || lower.contains("error") -> android.R.color.holo_red_dark
                        else -> android.R.color.darker_gray
                    }
                    usbStatusText.setTextColor(getColor(color))
                }

                GpsUsbForegroundService.ACTION_UI_LOG -> {
                    val msg = intent.getStringExtra(GpsUsbForegroundService.EXTRA_TEXT) ?: return
                    appendToSystemView(msg)
                    // Petites heuristiques pour colorer le statut si le service envoie des messages connus
                    when {
                        msg.contains("connecté", ignoreCase = true) ||
                                msg.contains("serial port opened", ignoreCase = true) -> {
                            usbStatusText.text = getString(R.string.usb_connecte, "")
                            usbStatusText.setTextColor(getColor(android.R.color.holo_green_dark))
                        }
                        msg.contains("déconnecté", ignoreCase = true) ||
                                msg.contains("refus", ignoreCase = true) ||
                                msg.contains("failed", ignoreCase = true) -> {
                            usbStatusText.text = getString(R.string.usb_disconnected)
                            usbStatusText.setTextColor(getColor(android.R.color.holo_red_dark))
                        }
                    }
                }
                GpsUsbForegroundService.ACTION_UI_NMEA -> {
                    val nmea = intent.getStringExtra(GpsUsbForegroundService.EXTRA_TEXT) ?: return
                    appendToNmeaView(nmea)
                }
                GpsUsbForegroundService.ACTION_UI_CLIENTS -> {
                    val count = intent.getIntExtra(GpsUsbForegroundService.EXTRA_COUNT, 0)
                    clientCountText.text = getString(R.string.clients_connected, count)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clientCountText = findViewById(R.id.clientCountText)
        usbStatusText = findViewById(R.id.usbStatus)
        logText = findViewById(R.id.logText)
        nmeaText = findViewById(R.id.nmeaText)
        localIpText = findViewById(R.id.localIpText)

        localIpText.text = getString(R.string.ip_locale, getLocalIpAddress())
        usbStatusText.text = getString(R.string.usb_searching)
        usbStatusText.setTextColor(getColor(android.R.color.holo_orange_dark))

        // ✅ Démarre le Foreground Service (il gère USB + TCP + notif)
        try {
            val svc = Intent(this, GpsUsbForegroundService::class.java)
                .setAction(GpsUsbForegroundService.ACTION_START)
            ContextCompat.startForegroundService(this, svc)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot start foreground service: ${e.message}")
            appendToSystemView("Erreur de démarrage du service: ${e.message}")
        }

        // ✅ Écoute uniquement les diffusions UI du service
        val filter = IntentFilter().apply {
            addAction(GpsUsbForegroundService.ACTION_UI_LOG)
            addAction(GpsUsbForegroundService.ACTION_UI_NMEA)
            addAction(GpsUsbForegroundService.ACTION_UI_CLIENTS)
            addAction(GpsUsbForegroundService.ACTION_UI_STATUS)
        }
        ContextCompat.registerReceiver(
            this,
            uiReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun appendToSystemView(message: String) {
        val maxLines = 100
        logText.append("$message\n")
        val lines = logText.text.lines()
        if (lines.size > maxLines) {
            logText.text = lines.takeLast(maxLines / 2).joinToString("\n")
        }
    }

    private fun appendToNmeaView(nmea: String) {
        val maxLines = 100
        nmeaText.append("$nmea\n")
        val lines = nmeaText.text.lines()
        if (lines.size > maxLines) {
            nmeaText.text = lines.takeLast(maxLines / 2).joinToString("\n")
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: getString(R.string.unknown)
                    }
                }
            }
        } catch (_: Exception) { }
        return getString(R.string.unknown)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(uiReceiver) } catch (_: Exception) { }
    }
}
