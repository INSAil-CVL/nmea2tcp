package com.insail.nmeagpsserver

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.Executors

class UsbNmeaReader(
    private val context: Context,
    val device: UsbDevice,
    private val usbManager: UsbManager,
    private val onNmeaLine: (String) -> Unit,
    private val onStatusUpdate: (String) -> Unit
) {
    companion object {
        private const val TAG = "UsbNmeaReader"
    }

    private var serialPort: UsbSerialPort? = null
    private var running = false
    private val executor = Executors.newSingleThreadExecutor()

    fun start() {
        Log.i(TAG, "Starting USB NMEA Reader for device: ${device.deviceName}")
        onStatusUpdate("Démarrage du lecteur USB pour: ${device.deviceName}")

        // Essayer d'abord avec le prober par défaut
        val defaultProber = UsbSerialProber.getDefaultProber()
        var availableDrivers = defaultProber.findAllDrivers(usbManager)
        Log.i(TAG, "Default drivers found: ${availableDrivers.size}")
        onStatusUpdate("Drivers par défaut trouvés: ${availableDrivers.size}")

        availableDrivers.forEach {
            Log.i(TAG, "Driver for device: ${it.device.deviceName}")
            onStatusUpdate("Driver disponible pour: ${it.device.deviceName}")
        }

        var driver = availableDrivers.find { it.device == device }

        // Si aucun driver trouvé avec le prober par défaut, essayer manuellement différents drivers
        if (driver == null) {
            onStatusUpdate("Aucun driver par défaut, essai manuel des drivers...")

            // Liste des drivers à essayer
            val driverClasses = listOf(
                com.hoho.android.usbserial.driver.CdcAcmSerialDriver::class.java,
                com.hoho.android.usbserial.driver.FtdiSerialDriver::class.java,
                com.hoho.android.usbserial.driver.Cp21xxSerialDriver::class.java,
                com.hoho.android.usbserial.driver.ProlificSerialDriver::class.java,
                com.hoho.android.usbserial.driver.Ch34xSerialDriver::class.java
            )

            for (driverClass in driverClasses) {
                try {
                    onStatusUpdate("Essai du driver: ${driverClass.simpleName}")

                    // Vérifier si ce driver supporte notre device
                    val method = driverClass.getMethod("getSupportedDevices")
                    val supportedDevices = method.invoke(null) as Map<Int, IntArray>

                    val vendorId = device.vendorId
                    val productId = device.productId

                    // Vérifier si notre device est supporté
                    val isSupported = supportedDevices[vendorId]?.contains(productId) == true

                    if (isSupported || driverClass.simpleName.contains("CdcAcm")) {
                        // Essayer de créer le driver
                        val constructor = driverClass.getConstructor(UsbDevice::class.java)
                        driver = constructor.newInstance(device) as com.hoho.android.usbserial.driver.UsbSerialDriver
                        onStatusUpdate("Driver ${driverClass.simpleName} créé avec succès!")
                        break
                    } else {
                        onStatusUpdate("Device non supporté par ${driverClass.simpleName}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create driver ${driverClass.simpleName}: ${e.message}")
                    onStatusUpdate("Échec ${driverClass.simpleName}: ${e.message}")
                }
            }
        }

        // Dernière tentative avec CDC-ACM générique (souvent compatible avec les GPS)
        if (driver == null) {
            onStatusUpdate("Dernière tentative avec CDC-ACM générique...")
            try {
                driver = com.hoho.android.usbserial.driver.CdcAcmSerialDriver(device)
                onStatusUpdate("Driver CDC-ACM générique créé")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create CDC-ACM driver: ${e.message}")
                onStatusUpdate("Échec création driver CDC-ACM: ${e.message}")
            }
        }
        if (driver == null) {
            Log.e(TAG, "No suitable USB driver found for device!")
            onStatusUpdate("ERREUR: Aucun driver USB trouvé!")
            return
        }

        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            Log.e(TAG, "Failed to open connection to device!")
            onStatusUpdate("ERREUR: Impossible d'ouvrir la connexion!")
            return
        }

        serialPort = driver.ports.firstOrNull()
        if (serialPort == null) {
            Log.e(TAG, "No serial port found on the device!")
            onStatusUpdate("ERREUR: Aucun port série trouvé!")
            connection.close()
            return
        }

        try {
            // Ouvrir le port une seule fois
            serialPort?.open(connection)
            onStatusUpdate("Port série ouvert avec succès")

            // Essayer différents bauds rates couramment utilisés pour NMEA
            val baudRates = listOf(4800, 9600, 38400, 115200)
            var connected = false

            onStatusUpdate("Test des vitesses de communication...")
            for (baudRate in baudRates) {
                try {
                    serialPort?.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    Log.i(TAG, "Trying baudrate: $baudRate")
                    onStatusUpdate("Test à $baudRate bauds...")

                    // Test de lecture rapide pour vérifier si on reçoit des données
                    val testBuffer = ByteArray(64)
                    val testLen = serialPort?.read(testBuffer, 1000) ?: 0
                    if (testLen > 0) {
                        Log.i(TAG, "Successfully connected at $baudRate baud")
                        onStatusUpdate("Connexion réussie à $baudRate bauds!")
                        connected = true
                        break
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "Failed to set baudrate $baudRate: ${e.message}")
                    onStatusUpdate("Échec à $baudRate bauds: ${e.message}")
                }
            }

            if (!connected) {
                Log.w(TAG, "No data received at any baudrate, using 4800 as default")
                onStatusUpdate("Aucune donnée reçue, utilisation de 4800 bauds par défaut")
                serialPort?.setParameters(4800, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            }

        } catch (e: IOException) {
            Log.e(TAG, "Error opening serial port: ${e.message}")
            onStatusUpdate("ERREUR d'ouverture du port série: ${e.message}")
            connection.close()
            return
        }

        running = true
        onStatusUpdate("Démarrage de la boucle de lecture...")

        executor.execute {
            val buffer = ByteArray(1024)
            var partialLine = ""

            Log.i(TAG, "Starting read loop...")

            while (running) {
                try {
                    // Timeout plus long pour les GPS lents
                    val len = serialPort?.read(buffer, 5000) ?: 0

                    if (len > 0) {
                        val rawData = buffer.copyOf(len).toString(Charset.forName("US-ASCII"))
                        Log.d(TAG, "Raw data received (${len} bytes): ${rawData.replace("\r", "\\r").replace("\n", "\\n")}")
                        //onStatusUpdate("Données reçues: $len octets")

                        partialLine += rawData

                        // Traiter ligne par ligne (NMEA se termine par \r\n ou \n)
                        while (true) {
                            var lineEndIndex = partialLine.indexOf('\n')
                            if (lineEndIndex == -1) {
                                // Essayer juste \r si pas de \n
                                lineEndIndex = partialLine.indexOf('\r')
                                if (lineEndIndex == -1) break
                            }

                            val line = partialLine.substring(0, lineEndIndex).trim()
                            partialLine = partialLine.substring(lineEndIndex + 1)

                            if (line.isNotEmpty()) {
                                Log.i(TAG, "NMEA line: $line")
                                onNmeaLine(line)
                            }
                        }
                    } else {
                        Log.d(TAG, "No data received (timeout)")
                        // Pas de log UI pour timeout pour éviter de spammer
                    }

                } catch (e: IOException) {
                    Log.e(TAG, "Error reading from serial port: ${e.message}")
                    onStatusUpdate("ERREUR de lecture: ${e.message}")
                    break
                }
            }

            try {
                serialPort?.close()
                onStatusUpdate("Port série fermé")
            } catch (e: IOException) {
                Log.w(TAG, "Error closing serial port: ${e.message}")
                onStatusUpdate("Erreur de fermeture: ${e.message}")
            }
            Log.i(TAG, "USB NMEA Reader stopped.")
            onStatusUpdate("Lecteur USB arrêté")
        }
    }

    fun stop() {
        running = false
        executor.shutdownNow()
        try {
            serialPort?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing serial port on stop: ${e.message}")
            onStatusUpdate("Erreur à l'arrêt: ${e.message}")
        }
        Log.i(TAG, "UsbNmeaReader stopped and port closed.")
        onStatusUpdate("Lecteur USB arrêté et port fermé")
    }
}