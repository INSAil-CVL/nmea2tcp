package com.insail.nmeagpsserver

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.net.NetworkInterface
import java.net.Inet4Address

class MainActivity : AppCompatActivity() {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.insail.nmeagpsserver.USB_PERMISSION"
        private const val TAG = "MainActivity"
    }

    private lateinit var usbManager: UsbManager
    private var usbNmeaReader: UsbNmeaReader? = null
    private val tcpServer = NmeaTcpServer(10110)

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clientCountText: TextView
    private val updateInterval = 1000L

    private val updateClientCountRunnable = object : Runnable {
        override fun run() {
            val count = tcpServer.getClientCount()
            runOnUiThread {
                clientCountText.text = getString(R.string.clients_connected, count)
            }
            handler.postDelayed(this, updateInterval)
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        @Suppress("DEPRECATION") // pour éviter le warning sur l'ancienne méthode
                        val device: UsbDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                Log.i(TAG, getString(R.string.log_usb_permission_granted, it))
                                appendToSystemView(getString(R.string.usb_permission_granted, it.deviceName))
                                startReading(it)
                            }
                        } else {
                            Log.w(TAG, getString(R.string.log_usb_permission_denied, device))
                            appendToSystemView(getString(R.string.usb_permission_refused, device?.deviceName ?: "inconnue"))
                            runOnUiThread {
                                findViewById<TextView>(R.id.usbStatus).apply {
                                    text = getString(R.string.usb_permission_refusee)
                                    setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
                                }
                            }
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    @Suppress("DEPRECATION") // pour éviter le warning sur l'ancienne méthode
                    val device: UsbDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    device?.let {
                        Log.i(TAG, getString(R.string.log_usb_attached, it))
                        appendToSystemView(getString(R.string.usb_device_connected, it.deviceName))
                        handleDeviceAttached(it)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    @Suppress("DEPRECATION") // pour éviter le warning sur l'ancienne méthode
                    val device: UsbDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    device?.let {
                        Log.i(TAG, getString(R.string.log_usb_detached, it))
                        if (usbNmeaReader != null && it == usbNmeaReader?.device) {
                            usbNmeaReader?.stop()
                            usbNmeaReader = null
                            appendToSystemView(getString(R.string.usb_device_disconnected, it.deviceName))
                            runOnUiThread {
                                findViewById<TextView>(R.id.usbStatus).apply {
                                    text = getString(R.string.usb_disconnected)
                                    setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clientCountText = findViewById(R.id.clientCountText)

        val localIpTextView = findViewById<TextView>(R.id.localIpText)
        localIpTextView.text = getString(R.string.ip_locale, getLocalIpAddress())

        runOnUiThread {
            findViewById<TextView>(R.id.usbStatus).apply {
                text = getString(R.string.usb_searching)
                setTextColor(resources.getColor(android.R.color.holo_orange_dark, theme))
            }
        }

        usbManager = getSystemService(USB_SERVICE) as UsbManager

        tcpServer.setLogCallback { message ->
            appendToSystemView(getString(R.string.tcp_message, message))
        }
        tcpServer.start()
        appendToSystemView(getString(R.string.tcp_server_started, 10110))

        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)

        Log.i(TAG, getString(R.string.log_usb_scan))
        val deviceList = usbManager.deviceList
        Log.i(TAG, getString(R.string.log_usb_device_count, deviceList.size))
        appendToSystemView(getString(R.string.usb_scan_start))
        appendToSystemView(getString(R.string.usb_devices_detected, deviceList.size))

        if (deviceList.isEmpty()) {
            Log.w(TAG, getString(R.string.log_usb_none))
            appendToSystemView(getString(R.string.usb_no_device))
            runOnUiThread {
                findViewById<TextView>(R.id.usbStatus).apply {
                    text = getString(R.string.usb_aucun_device)
                    setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
                }
            }
        } else {
            deviceList.values.forEach { device ->
                handleDeviceAttached(device)
            }
        }

        handler.post(updateClientCountRunnable)
    }

    private fun appendToSystemView(message: String) {
        runOnUiThread {
            val systemTextView = findViewById<TextView>(R.id.logText)
            systemTextView.append("$message\n")

            val lines = systemTextView.text.lines()
            if (lines.size > 100) {
                systemTextView.text = lines.takeLast(50).joinToString("\n")
            }
        }
    }

    private fun appendToNmeaView(nmea: String) {
        runOnUiThread {
            val nmeaTextView = findViewById<TextView>(R.id.nmeaText)
            nmeaTextView.append("$nmea\n")

            val lines = nmeaTextView.text.lines()
            if (lines.size > 100) {
                nmeaTextView.text = lines.takeLast(50).joinToString("\n")
            }
        }
    }

    private fun handleDeviceAttached(device: UsbDevice) {
        appendToSystemView(getString(R.string.usb_analysis, device.vendorId.toString(16).uppercase(), device.productId.toString(16).uppercase()))

        if (isGpsDevice(device)) {
            appendToSystemView(getString(R.string.usb_gps_detected, device.deviceName))
            if (usbManager.hasPermission(device)) {
                appendToSystemView(getString(R.string.usb_permission_already))
                startReading(device)
            } else {
                val permissionIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                usbManager.requestPermission(device, permissionIntent)
                appendToSystemView(getString(R.string.usb_permission_request, device.deviceName))
                runOnUiThread {
                    findViewById<TextView>(R.id.usbStatus).apply {
                        text = getString(R.string.usb_permission_demande)
                        setTextColor(resources.getColor(android.R.color.holo_orange_dark, theme))
                    }
                }
            }
        } else {
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            val hasDriver = availableDrivers.any { it.device == device }

            if (hasDriver) {
                appendToSystemView(getString(R.string.usb_generic_serial_detected, device.deviceName))
                if (usbManager.hasPermission(device)) {
                    startReading(device)
                } else {
                    val permissionIntent = PendingIntent.getBroadcast(
                        this,
                        0,
                        Intent(ACTION_USB_PERMISSION),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    usbManager.requestPermission(device, permissionIntent)
                    appendToSystemView(getString(R.string.usb_permission_request, device.deviceName))
                }
            } else {
                if (device.deviceClass == 2 || device.deviceSubclass == 2 || device.vendorId == 0x1546) {
                    appendToSystemView(getString(R.string.usb_forced_connection_attempt, device.deviceName))
                    if (usbManager.hasPermission(device)) {
                        startReading(device)
                    } else {
                        val permissionIntent = PendingIntent.getBroadcast(
                            this,
                            0,
                            Intent(ACTION_USB_PERMISSION),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        usbManager.requestPermission(device, permissionIntent)
                        appendToSystemView(getString(R.string.usb_permission_request_forced, device.deviceName))
                    }
                } else {
                    appendToSystemView(getString(R.string.usb_device_ignored, device.deviceName))
                }
            }
        }
    }

    private fun isGpsDevice(device: UsbDevice): Boolean {
        val targetVendorId = 0x1546
        val targetProductId = 0x01A8

        val match = device.vendorId == targetVendorId && device.productId == targetProductId
        appendToSystemView(getString(R.string.usb_gps_check,
            targetVendorId.toString(16).uppercase(),
            targetProductId.toString(16).uppercase(),
            device.vendorId.toString(16).uppercase(),
            device.productId.toString(16).uppercase(),
            if (match) "MATCH" else "NON"
        ))
        return match
    }

    private fun startReading(device: UsbDevice) {
        appendToSystemView(getString(R.string.usb_start_reading, device.deviceName))

        usbNmeaReader?.stop()
        usbNmeaReader = UsbNmeaReader(this, device, usbManager,
            onNmeaLine = { nmeaLine ->
                appendToNmeaView(nmeaLine)
                tcpServer.sendToClients(nmeaLine)
            },
            onStatusUpdate = { message ->
                appendToSystemView(getString(R.string.usb_reader_status, message))
            }
        )

        usbNmeaReader?.start()

        runOnUiThread {
            findViewById<TextView>(R.id.usbStatus).apply {
                text = getString(R.string.usb_connecte, device.deviceName)
                setTextColor(resources.getColor(android.R.color.holo_green_dark, theme))
            }
        }
        appendToSystemView(getString(R.string.usb_reading_started))
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
        } catch (_: Exception) {}
        return getString(R.string.unknown)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
        usbNmeaReader?.stop()
        tcpServer.stop()
        handler.removeCallbacks(updateClientCountRunnable)
    }
}