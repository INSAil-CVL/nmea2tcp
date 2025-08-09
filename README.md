# NMEA GPS Server for OpenCPN (Android)

Android app that reads GPS data via USB-C (NMEA) and retransmits NMEA sentences to **OpenCPN** through a local TCP server.  
Designed for **plug-and-play** use: displays the local IP, client count, and streams NMEA data directly to OpenCPN.

---

## 📑 Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Configuration](#configuration)
  - [Vendor / Product ID](#vendor--product-id)
  - [Change TCP Port](#change-tcp-port)
- [Usage](#usage)
- [Tested Devices](#tested-devices)
- [Manifest & Permissions](#manifest--permissions)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

---

## 🚀 Features

- Reads NMEA sentences from a USB-C GPS.
- Runs a **local TCP server** (default port `10110`).
- Displays:
  - Latest NMEA messages
  - System logs
  - Local IP address
  - Connected client count
- Supports multiple TCP clients simultaneously.

---

## 📋 Requirements

- **Android Studio** (latest version)
- Android device with **OTG / USB Host** support (USB-C)
- NMEA-compatible GPS over USB (or serial-to-USB adapter)

---

## 🔧 Installation

```bash
git clone https://github.com/insail-cvl/nmeagpsserver.git
```
Open in Android Studio, then build & run on your Android device

---

## ⚙ Configuration

### Vendor / Product ID

The code checks the GPS **Vendor ID** and **Product ID**.  
You must update these values to match your device — otherwise it may be ignored.

Example (`MainActivity.kt`):

```kotlin
private fun isGpsDevice(device: UsbDevice): Boolean {
    // <-- CHANGE HERE for your GPS
    val targetVendorId = 0x1546     // Replace with your GPS vendor ID
    val targetProductId = 0x01A8    // Replace with your GPS product ID

    return device.vendorId == targetVendorId && device.productId == targetProductId
}
```

💡 Tip:
To accept any serial device, comment/remove this check or create a list of allowed VID/PID values.


---

### Change TCP Port

Default port is set in `MainActivity`:

```kotlin
private val tcpServer = NmeaTcpServer(10110) // Change 10110 if needed
```