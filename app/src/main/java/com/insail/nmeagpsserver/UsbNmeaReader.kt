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
        onStatusUpdate(context.getString(R.string.usb_starting_reader, device.deviceName))

        val defaultProber = UsbSerialProber.getDefaultProber()
        val availableDrivers = defaultProber.findAllDrivers(usbManager)
        Log.i(TAG, "Default drivers found: ${availableDrivers.size}")
        onStatusUpdate(context.getString(R.string.usb_default_drivers_found, availableDrivers.size))

        availableDrivers.forEach {
            Log.i(TAG, "Driver for device: ${it.device.deviceName}")
            onStatusUpdate(context.getString(R.string.usb_driver_available_for, it.device.deviceName))
        }

        var driver = availableDrivers.find { it.device == device }

        if (driver == null) {
            onStatusUpdate(context.getString(R.string.usb_no_default_driver_try_manual))

            val driverClasses = listOf(
                com.hoho.android.usbserial.driver.CdcAcmSerialDriver::class.java,
                com.hoho.android.usbserial.driver.FtdiSerialDriver::class.java,
                com.hoho.android.usbserial.driver.Cp21xxSerialDriver::class.java,
                com.hoho.android.usbserial.driver.ProlificSerialDriver::class.java,
                com.hoho.android.usbserial.driver.Ch34xSerialDriver::class.java
            )

            for (driverClass in driverClasses) {
                try {
                    onStatusUpdate(context.getString(R.string.usb_trying_driver, driverClass.simpleName))

                    val method = driverClass.getMethod("getSupportedDevices")
                    @Suppress("UNCHECKED_CAST")
                    val supportedDevices = method.invoke(null) as Map<Int, IntArray>

                    val vendorId = device.vendorId
                    val productId = device.productId

                    val isSupported = supportedDevices[vendorId]?.contains(productId) == true

                    if (isSupported || driverClass.simpleName.contains("CdcAcm")) {
                        val constructor = driverClass.getConstructor(UsbDevice::class.java)
                        driver = constructor.newInstance(device) as com.hoho.android.usbserial.driver.UsbSerialDriver
                        onStatusUpdate(context.getString(R.string.usb_driver_created_success, driverClass.simpleName))
                        break
                    } else {
                        onStatusUpdate(context.getString(R.string.usb_driver_not_supported, driverClass.simpleName))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create driver ${driverClass.simpleName}: ${e.message}")
                    onStatusUpdate(context.getString(R.string.usb_driver_creation_failed, driverClass.simpleName, e.message ?: ""))
                }
            }
        }

        if (driver == null) {
            onStatusUpdate(context.getString(R.string.usb_try_generic_cdc_acm))
            try {
                driver = com.hoho.android.usbserial.driver.CdcAcmSerialDriver(device)
                onStatusUpdate(context.getString(R.string.usb_generic_cdc_acm_created))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create CDC-ACM driver: ${e.message}")
                onStatusUpdate(context.getString(R.string.usb_generic_cdc_acm_failed, e.message ?: ""))
            }
        }

        if (driver == null) {
            Log.e(TAG, "No suitable USB driver found for device!")
            onStatusUpdate(context.getString(R.string.usb_no_driver_found))
            return
        }

        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            Log.e(TAG, "Failed to open connection to device!")
            onStatusUpdate(context.getString(R.string.usb_connection_failed))
            return
        }

        serialPort = driver.ports.firstOrNull()
        if (serialPort == null) {
            Log.e(TAG, "No serial port found on the device!")
            onStatusUpdate(context.getString(R.string.usb_no_serial_port))
            connection.close()
            return
        }

        try {
            serialPort?.open(connection)
            onStatusUpdate(context.getString(R.string.usb_serial_port_opened))

            val baudRates = listOf(4800, 9600, 38400, 115200)
            var connected = false

            onStatusUpdate(context.getString(R.string.usb_testing_baud_rates))
            for (baudRate in baudRates) {
                try {
                    serialPort?.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    Log.i(TAG, "Trying baudrate: $baudRate")
                    onStatusUpdate(context.getString(R.string.usb_testing_baud_rate, baudRate))

                    val testBuffer = ByteArray(64)
                    val testLen = serialPort?.read(testBuffer, 1000) ?: 0
                    if (testLen > 0) {
                        Log.i(TAG, "Successfully connected at $baudRate baud")
                        onStatusUpdate(context.getString(R.string.usb_connected_baud_rate, baudRate))
                        connected = true
                        break
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "Failed to set baudrate $baudRate: ${e.message}")
                    onStatusUpdate(context.getString(R.string.usb_failed_baud_rate, baudRate, e.message ?: ""))
                }
            }

            if (!connected) {
                Log.w(TAG, "No data received at any baudrate, using 4800 as default")
                onStatusUpdate(context.getString(R.string.usb_no_data_default_baud))
                serialPort?.setParameters(4800, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            }

        } catch (e: IOException) {
            Log.e(TAG, "Error opening serial port: ${e.message}")
            onStatusUpdate(context.getString(R.string.usb_serial_port_error_opening, e.message ?: ""))
            connection.close()
            return
        }

        running = true
        onStatusUpdate(context.getString(R.string.usb_start_read_loop))

        executor.execute {
            val buffer = ByteArray(1024)
            var partialLine = ""

            Log.i(TAG, "Starting read loop...")

            while (running) {
                try {
                    val len = serialPort?.read(buffer, 5000) ?: 0

                    if (len > 0) {
                        val rawData = buffer.copyOf(len).toString(Charset.forName("US-ASCII"))
                        Log.d(TAG, "Raw data received (${len} bytes): ${rawData.replace("\r", "\\r").replace("\n", "\\n")}")

                        partialLine += rawData

                        while (true) {
                            var lineEndIndex = partialLine.indexOf('\n')
                            if (lineEndIndex == -1) {
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
                    }

                } catch (e: IOException) {
                    Log.e(TAG, "Error reading from serial port: ${e.message}")
                    onStatusUpdate(context.getString(R.string.usb_read_error, e.message ?: ""))
                    break
                }
            }

            try {
                serialPort?.close()
                onStatusUpdate(context.getString(R.string.usb_serial_port_closed))
            } catch (e: IOException) {
                Log.w(TAG, "Error closing serial port: ${e.message}")
                onStatusUpdate(context.getString(R.string.usb_serial_port_close_error, e.message ?: ""))
            }
            Log.i(TAG, "USB NMEA Reader stopped.")
            onStatusUpdate(context.getString(R.string.usb_reader_stopped))
        }
    }

    fun stop() {
        running = false
        executor.shutdownNow()
        try {
            serialPort?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing serial port on stop: ${e.message}")
            onStatusUpdate(context.getString(R.string.usb_stop_error, e.message ?: ""))
        }
        Log.i(TAG, "UsbNmeaReader stopped and port closed.")
        onStatusUpdate(context.getString(R.string.usb_reader_stopped_and_closed))
    }
}