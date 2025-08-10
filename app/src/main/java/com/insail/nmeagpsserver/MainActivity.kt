package com.insail.nmeagpsserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ThemedActivity() {
    private lateinit var clientCountText: TextView
    private lateinit var usbStatusText: TextView
    private lateinit var nmeaText: TextView
    private lateinit var localIpText: TextView
    private lateinit var btnMainToggle: ImageButton
    private lateinit var tcpStatusText: TextView
    private lateinit var usbManager: UsbManager

    // Lance Settings et recrée Main au retour (sécurité supplémentaire)
    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            recreate()
        }

    private val uiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                GpsUsbForegroundService.ACTION_UI_STATUS -> {
                    val status = intent.getStringExtra(GpsUsbForegroundService.EXTRA_TEXT) ?: return
                    usbStatusText.text = status
                    val lower = status.lowercase()
                    val color = when {
                        lower.contains("connect") -> android.R.color.holo_green_dark
                        lower.contains("refus") || lower.contains("déconnect") || lower.contains("error") -> android.R.color.holo_red_dark
                        else -> android.R.color.darker_gray
                    }
                    usbStatusText.setTextColor(getColor(color))
                    btnMainToggle.isActivated = lower.contains("connect")
                }
                GpsUsbForegroundService.ACTION_UI_LOG -> {
                    val msg = intent.getStringExtra(GpsUsbForegroundService.EXTRA_TEXT) ?: return
                    when {
                        msg.contains("connecté", ignoreCase = true) ||
                                msg.contains("serial port opened", ignoreCase = true) -> {
                            usbStatusText.setTextColor(getColor(android.R.color.holo_green_dark))
                            btnMainToggle.isActivated = true
                        }
                        msg.contains("déconnecté", ignoreCase = true) ||
                                msg.contains("refus", ignoreCase = true) ||
                                msg.contains("failed", ignoreCase = true) -> {
                            usbStatusText.text = getString(R.string.usb_disconnected)
                            usbStatusText.setTextColor(getColor(android.R.color.holo_red_dark))
                            btnMainToggle.isActivated = false
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

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        btnMainToggle = findViewById(R.id.btnMainToggle)
        clientCountText = findViewById(R.id.clientCountText)
        usbStatusText = findViewById(R.id.usbStatus)
        nmeaText = findViewById(R.id.nmeaText)
        localIpText = findViewById(R.id.localIpText)
        tcpStatusText = findViewById(R.id.tcpStatus)

        localIpText.text = getString(R.string.ip_locale, getLocalIpAddress())
        usbStatusText.text = getString(R.string.usb_searching)
        usbStatusText.setTextColor(getColor(android.R.color.holo_orange_dark))
        tcpStatusText.text = getString(R.string.tcp_server_port_label, 10110)

        btnMainToggle.isActivated = GpsUsbForegroundService.isRunning

        usbManager = getSystemService(USB_SERVICE) as UsbManager
        btnMainToggle.setOnClickListener {
            if (GpsUsbForegroundService.isRunning) {
                startActivity(Intent(this, StopServiceConfirmActivity::class.java))
            } else {
                showUsbDevicePicker()
            }
        }

        val filter = IntentFilter().apply {
            addAction(GpsUsbForegroundService.ACTION_UI_LOG)
            addAction(GpsUsbForegroundService.ACTION_UI_NMEA)
            addAction(GpsUsbForegroundService.ACTION_UI_CLIENTS)
            addAction(GpsUsbForegroundService.ACTION_UI_STATUS)
        }
        ContextCompat.registerReceiver(
            this, uiReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
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

    private fun showUsbDevicePicker() {
        val devices = usbManager.deviceList.values.toList()
        if (devices.isEmpty()) {
            Toast.makeText(this, getString(R.string.usb_aucun_device), Toast.LENGTH_SHORT).show()
            return
        }
        val labels = devices.map { d ->
            val vid = "0x" + d.vendorId.toString(16).uppercase()
            val pid = "0x" + d.productId.toString(16).uppercase()
            "${d.deviceName} ($vid:$pid)"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_usb_device_title))
            .setItems(labels) { _, which ->
                val selected = devices[which]
                val svc = Intent(this, GpsUsbForegroundService::class.java)
                    .setAction(GpsUsbForegroundService.ACTION_START)
                    .putExtra(GpsUsbForegroundService.EXTRA_DEVICE, selected)

                ContextCompat.startForegroundService(this, svc)
                usbStatusText.text = getString(R.string.usb_permission_demande)
                usbStatusText.setTextColor(getColor(android.R.color.holo_orange_dark))
                btnMainToggle.isActivated = true
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(uiReceiver) } catch (_: Exception) { }
        if (GpsUsbForegroundService.isRunning) {
            try { androidx.core.app.NotificationManagerCompat.from(this).cancelAll() } catch (_: Exception) { }
            try { stopService(Intent(this, GpsUsbForegroundService::class.java)) } catch (_: Exception) { }
        }
    }
}
