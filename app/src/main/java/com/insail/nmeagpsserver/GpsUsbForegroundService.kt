package com.insail.nmeagpsserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.net.Inet4Address
import java.net.NetworkInterface
import androidx.core.content.ContextCompat

class GpsUsbForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "gps_usb_foreground"
        private const val NOTIF_ID = 42

        const val ACTION_START = "com.insail.nmeagpsserver.action.START"
        const val ACTION_STOP = "com.insail.nmeagpsserver.action.STOP"
        const val ACTION_USB_PERMISSION = "com.insail.nmeagpsserver.USB_PERMISSION"

        // Diffusions UI
        const val ACTION_UI_LOG = "com.insail.nmeagpsserver.ui.LOG"
        const val ACTION_UI_NMEA = "com.insail.nmeagpsserver.ui.NMEA"
        const val ACTION_UI_CLIENTS = "com.insail.nmeagpsserver.ui.CLIENTS"
        const val ACTION_UI_STATUS = "com.insail.nmeagpsserver.ui.STATUS"
        const val EXTRA_TEXT = "text"
        const val EXTRA_COUNT = "count"
    }

    private lateinit var usbManager: UsbManager
    private var usbNmeaReader: UsbNmeaReader? = null
    private lateinit var tcpServer: NmeaTcpServer

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        usbManager = getSystemService(USB_SERVICE) as UsbManager

        createChannelIfNeeded()
        startForeground(NOTIF_ID, buildNotification(getString(R.string.usb_searching)))

        tcpServer = NmeaTcpServer(10110, this).apply {
            setLogCallback { msg -> logToUi(getString(R.string.tcp_message, msg)) }
        }
        tcpServer.start()
        logToUi(getString(R.string.tcp_server_started, 10110))

        registerUsbReceiver()
        scanAndAttachIfPresent()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(usbReceiver) } catch (_: Exception) {}
        usbNmeaReader?.stop()
        tcpServer.stop()
    }

    // --- USB flow (identique à ta méthodo existante) ---
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        @Suppress("DEPRECATION")
                        val device: UsbDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                            } else {
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                            }
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                logToUi(getString(R.string.usb_permission_granted, it.deviceName))
                                startReading(it)
                            }
                        } else {
                            logToUi(getString(R.string.usb_permission_refused, device?.deviceName ?: "inconnue"))
                            updateForeground(getString(R.string.usb_permission_refusee))
                            sendStatusToUi(getString(R.string.usb_permission_refusee))
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    @Suppress("DEPRECATION")
                    val device: UsbDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                    device?.let {
                        logToUi(getString(R.string.usb_device_connected, it.deviceName))
                        handleDeviceAttached(it)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    @Suppress("DEPRECATION")
                    val device: UsbDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                    device?.let {
                        if (usbNmeaReader != null && it == usbNmeaReader?.device) {
                            usbNmeaReader?.stop()
                            usbNmeaReader = null
                            logToUi(getString(R.string.usb_device_disconnected, it.deviceName))
                            updateForeground(getString(R.string.usb_disconnected))
                            sendStatusToUi(getString(R.string.usb_disconnected))
                        }
                    }
                }
            }
        }
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        // AVANT : if (SDK_INT >= 33) … else …
        // APRÈS :
        ContextCompat.registerReceiver(
            this,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }


    private fun scanAndAttachIfPresent() {
        val deviceList = usbManager.deviceList
        logToUi(getString(R.string.usb_scan_start))
        logToUi(getString(R.string.usb_devices_detected, deviceList.size))
        if (deviceList.isEmpty()) {
            updateForeground(getString(R.string.usb_aucun_device))
            return
        }
        deviceList.values.forEach { device -> handleDeviceAttached(device) }
    }

    private fun handleDeviceAttached(device: UsbDevice) {
        logToUi(getString(R.string.usb_analysis,
            device.vendorId.toString(16).uppercase(),
            device.productId.toString(16).uppercase()))

        if (isGpsDevice(device) || hasSerialDriver(device)) {
            if (usbManager.hasPermission(device)) {
                logToUi(getString(R.string.usb_permission_already))
                startReading(device)
            } else {
                val permissionIntent = PendingIntent.getBroadcast(
                    this, 0,
                    Intent(ACTION_USB_PERMISSION).setPackage(packageName), // ✅ explicite
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                usbManager.requestPermission(device, permissionIntent)
                logToUi(getString(R.string.usb_permission_request, device.deviceName))
                updateForeground(getString(R.string.usb_permission_demande))
            }
        } else {
            logToUi(getString(R.string.usb_device_ignored, device.deviceName))
        }
    }

    private fun hasSerialDriver(device: UsbDevice): Boolean {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        return availableDrivers.any { it.device == device } ||
                device.deviceClass == 2 || device.deviceSubclass == 2 || device.vendorId == 0x1546
    }

    private fun isGpsDevice(device: UsbDevice): Boolean {
        val targetVendorId = 0x1546
        val targetProductId = 0x01A8
        val match = device.vendorId == targetVendorId && device.productId == targetProductId
        logToUi(getString(R.string.usb_gps_check,
            targetVendorId.toString(16).uppercase(),
            targetProductId.toString(16).uppercase(),
            device.vendorId.toString(16).uppercase(),
            device.productId.toString(16).uppercase(),
            if (match) "MATCH" else "NON"))
        return match
    }

    private fun startReading(device: UsbDevice) {
        logToUi(getString(R.string.usb_start_reading, device.deviceName))
        usbNmeaReader?.stop()
        usbNmeaReader = UsbNmeaReader(
            context = this,
            device = device,
            usbManager = usbManager,
            onNmeaLine = { nmea ->
                sendToUi(ACTION_UI_NMEA, nmea)
                tcpServer.sendToClients(nmea)
                sendClientsCount()
            },
            onStatusUpdate = { msg ->
                logToUi(getString(R.string.usb_reader_status, msg))

                val m = msg.lowercase()
                val isNoisy = listOf("driver", "baud", "test", "trying", "loop", "starting")
                    .any { m.contains(it) }

                if (!isNoisy) {
                    // seulement les messages “lisibles” alimentent la notif
                    updateForeground(msg.take(60))
                    // et, si c'est un vrai statut, on le pousse aussi à l’UI
                    sendStatusToUi(msg.take(120))
                }
            }
        )
        usbNmeaReader?.start()
        updateForeground(getString(R.string.usb_connecte, device.deviceName))
        sendStatusToUi(getString(R.string.usb_connecte, device.deviceName))
        logToUi(getString(R.string.usb_reading_started))
    }

    // --- Notifs / UI ---
    private fun createChannelIfNeeded() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
            }
        )

    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val ip = getLocalIpAddress()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("$status • ${getString(R.string.ip_locale, ip)} • TCP 10110")
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateForeground(status: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(status))
    }

    private fun logToUi(text: String) = sendToUi(ACTION_UI_LOG, text)

    private fun sendClientsCount() {
        val intent = Intent(ACTION_UI_CLIENTS).apply {
            setPackage(packageName)                 // ✅ explicite
            putExtra(EXTRA_COUNT, tcpServer.getClientCount())
        }
        sendBroadcast(intent)
    }

    private fun sendToUi(action: String, text: String) {
        val intent = Intent(action).apply {
            setPackage(packageName)                 // ✅ explicite
            putExtra(EXTRA_TEXT, text)
        }
        sendBroadcast(intent)
    }

    private fun sendStatusToUi(text: String) {
        val intent = Intent(ACTION_UI_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_TEXT, text)
        }
        sendBroadcast(intent)
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
}
