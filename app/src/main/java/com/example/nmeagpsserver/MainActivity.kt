package com.example.nmeagpsserver

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
        private const val ACTION_USB_PERMISSION = "com.example.nmeagpsserver.USB_PERMISSION"
        private const val TAG = "MainActivity"
    }

    private lateinit var usbManager: UsbManager
    private var usbNmeaReader: UsbNmeaReader? = null
    private val tcpServer = NmeaTcpServer(10110)

    // Handler pour la mise à jour périodique du nombre de clients
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clientCountText: TextView
    private val updateInterval = 1000L

    private val updateClientCountRunnable = object : Runnable {
        override fun run() {
            val count = tcpServer.getClientCount()
            runOnUiThread {
                clientCountText.text = "Clients connectés : $count"
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
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                Log.i(TAG, "Permission granted for device $it")
                                appendToSystemView("USB: Permission accordée pour ${it.deviceName}")
                                startReading(it)
                            }
                        } else {
                            Log.w(TAG, "Permission denied for device $device")
                            appendToSystemView("USB: Permission refusée pour ${device?.deviceName}")
                            runOnUiThread {
                                findViewById<TextView>(R.id.usbStatus).apply {
                                    text = "USB : Permission refusée"
                                    setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
                                }
                            }
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.i(TAG, "USB device attached: $it")
                        appendToSystemView("USB: Périphérique connecté - ${it.deviceName}")
                        handleDeviceAttached(it)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.i(TAG, "USB device detached: $it")
                        if (usbNmeaReader != null && it == usbNmeaReader?.device) {
                            usbNmeaReader?.stop()
                            usbNmeaReader = null
                            appendToSystemView("USB: Périphérique ${it.deviceName} déconnecté")
                            runOnUiThread {
                                findViewById<TextView>(R.id.usbStatus).apply {
                                    text = "USB : Déconnecté"
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
        localIpTextView.text = "IP locale : ${getLocalIpAddress()}"

        runOnUiThread {
            findViewById<TextView>(R.id.usbStatus).apply {
                text = "USB : Recherche..."
                setTextColor(resources.getColor(android.R.color.holo_orange_dark, theme))
            }
        }

        usbManager = getSystemService(USB_SERVICE) as UsbManager

        tcpServer.setLogCallback { message ->
            appendToSystemView("TCP: $message")
        }
        tcpServer.start()
        appendToSystemView("Serveur TCP démarré sur le port 10110")

        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)

        // Scan devices USB (reste inchangé)
        Log.i(TAG, "=== SCAN DES DEVICES USB ===")
        val deviceList = usbManager.deviceList
        Log.i(TAG, "Nombre total de devices USB: ${deviceList.size}")
        appendToSystemView("=== SCAN USB ===")
        appendToSystemView("Devices USB détectés: ${deviceList.size}")

        if (deviceList.isEmpty()) {
            Log.w(TAG, "Aucun device USB détecté!")
            appendToSystemView("Aucun périphérique USB détecté")
            runOnUiThread {
                findViewById<TextView>(R.id.usbStatus).apply {
                    text = "USB : Aucun device détecté"
                    setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
                }
            }
        } else {
            deviceList.values.forEach { device ->
                // ... (le reste du scan USB inchangé)
                handleDeviceAttached(device)
            }
        }

        // Lancer la mise à jour périodique du nombre de clients TCP
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
        val deviceAnalysis = "Analyse: vendorId=0x${device.vendorId.toString(16).uppercase()}, " +
                "productId=0x${device.productId.toString(16).uppercase()}"
        Log.i(TAG, deviceAnalysis)
        appendToSystemView(deviceAnalysis)

        if (isGpsDevice(device)) {
            Log.i(TAG, "GPS USB device detected: $device")
            appendToSystemView("GPS USB détecté: ${device.deviceName}")
            if (usbManager.hasPermission(device)) {
                Log.i(TAG, "Permission already granted, starting immediately")
                appendToSystemView("Permission déjà accordée, démarrage...")
                startReading(device)
            } else {
                val permissionIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                usbManager.requestPermission(device, permissionIntent)
                Log.i(TAG, "Permission requested for device $device")
                appendToSystemView("Demande de permission pour ${device.deviceName}")
                runOnUiThread {
                    findViewById<TextView>(R.id.usbStatus).apply {
                        text = "USB : Demande permission..."
                        setTextColor(resources.getColor(android.R.color.holo_orange_dark, theme))
                    }
                }
            }
        } else {
            // Vérifier quand même si c'est un device série générique
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            val hasDriver = availableDrivers.any { it.device == device }

            if (hasDriver) {
                Log.i(TAG, "Device série générique détecté, tentative de connexion: $device")
                appendToSystemView("Device série générique détecté: ${device.deviceName}")
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
                    appendToSystemView("Demande de permission pour ${device.deviceName}")
                }
            } else {
                // Pour les GPS, essayer de forcer la connexion même sans driver reconnu
                if (device.deviceClass == 2 || // Communication device
                    device.deviceSubclass == 2 || // CDC-ACM
                    device.vendorId == 0x1546) { // Votre GPS spécifique

                    Log.i(TAG, "Tentative de connexion forcée pour device potentiellement série: $device")
                    appendToSystemView("Tentative de connexion forcée: ${device.deviceName}")

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
                        appendToSystemView("Demande de permission pour tentative forcée: ${device.deviceName}")
                    }
                } else {
                    Log.i(TAG, "Device ignored (not serial): $device")
                    appendToSystemView("Device ignoré (pas série): ${device.deviceName}")
                }
            }
        }
    }

    private fun isGpsDevice(device: UsbDevice): Boolean {
        // Votre GPS spécifique
        val targetVendorId = 0x1546
        val targetProductId = 0x01A8

        val match = device.vendorId == targetVendorId && device.productId == targetProductId
        val checkResult = "Vérification GPS: attendu(0x${targetVendorId.toString(16).uppercase()}/0x${targetProductId.toString(16).uppercase()}) " +
                "vs réel(0x${device.vendorId.toString(16).uppercase()}/0x${device.productId.toString(16).uppercase()}) = ${if (match) "MATCH" else "NON"}"
        Log.d(TAG, checkResult)
        appendToSystemView(checkResult)

        return match
    }

    private fun startReading(device: UsbDevice) {
        Log.i(TAG, "Starting reading from device: $device")
        appendToSystemView("Démarrage de la lecture depuis: ${device.deviceName}")

        usbNmeaReader?.stop()
        usbNmeaReader = UsbNmeaReader(this, device, usbManager,
            onNmeaLine = { nmeaLine ->
                Log.d(TAG, "NMEA: $nmeaLine")
                runOnUiThread {
                    val nmeaTextView = findViewById<TextView>(R.id.nmeaText)
                    nmeaTextView.append("$nmeaLine\n")

                    // Limiter le nombre de lignes affichées pour éviter les problèmes de mémoire
                    val lines = nmeaTextView.text.lines()
                    if (lines.size > 100) {
                        val newText = lines.takeLast(50).joinToString("\n")
                        nmeaTextView.text = newText
                    }
                }
                tcpServer.sendToClients(nmeaLine)
            },
            onStatusUpdate = { message ->
                appendToSystemView("USB Reader: $message")
            }
        )

        usbNmeaReader?.start()

        runOnUiThread {
            findViewById<TextView>(R.id.usbStatus).apply {
                text = "USB : Connecté (${device.deviceName})"
                setTextColor(resources.getColor(android.R.color.holo_green_dark, theme))
            }
        }
        Log.i(TAG, "Started reading from USB GPS device.")
        appendToSystemView("Lecture USB démarrée avec succès")
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "inconnue"
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return "inconnue"
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receiver: ${e.message}")
        }
        usbNmeaReader?.stop()
        tcpServer.stop()
        handler.removeCallbacks(updateClientCountRunnable)
        Log.i(TAG, "Activity destroyed, resources cleaned up.")
    }
}
