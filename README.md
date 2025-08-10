# NMEA GPS Server for OpenCPN (Android)

Android app that reads NMEA GPS data from **any compatible USB device** (via a user picker) and re-transmits it over a **local TCP server** for OpenCPN or other clients.

> **New (Aug 2025):**
>
> - **USB device picker**: lists *all* connected USB devices and lets you pick which one to use.
> - Service accepts a \*\*specific \*\*\`\` via intent extra (no more blind scanning when you chose a device).
> - Cleaner startup: background scan is optional; VID/PID hard-coding is no longer required for manual selection.
>
> Files updated: `MainActivity.kt`, `GpsUsbForegroundService.kt`, `strings.xml`, and drawable placeholders.

---

## 📑 Table of Contents

- [Features](#features)
- [Quick Start](#quick-start)
- [How it works](#how-it-works)
- [Configuration (optional)](#configuration-optional)
- [Project Structure](#project-structure)
- [Build & Dependencies](#build--dependencies)
- [Android Manifest & Permissions](#android-manifest--permissions)
- [Resources (drawables/strings)](#resources-drawablesstrings)
- [Troubleshooting](#troubleshooting)
- [FAQ](#faq)
- [License](#license)

---

## 🚀 Features

- Reads NMEA sentences from a USB GPS (or any serial-over-USB device with a supported driver).
- Runs a **local TCP server** (default port `10110`) and streams NMEA to all connected clients.
- Clean UI: local IP, client count, last NMEA lines, system log.
- **Picker-first UX**: you explicitly choose the USB device to use.
- Still supports **auto attach** (when a device is plugged), if you want that.

---

## ⚡ Quick Start

1. Download the latest APK from the **[GitHub Releases](https://github.com/INSAil-CVL/nmeagpsserver/releases)** page  
   *(Latest: `v0.0.001-beta`)*.
2. Copy it to your Android device.
3. Enable **installation from unknown sources** in your Android settings.
4. Plug your USB GPS (or serial adapter) into the device.
5. Open the app → tap the big button → **select your device** from the picker.
6. In **OpenCPN**, add a TCP connection to `YOUR_DEVICE_IP:10110` or `127.0.0.1:10110` if it's on the same device.
7. You should now see NMEA data flowing in real time.

> Tip: the app shows your local IP on the main screen and in the foreground notification.

---

## 🧠 How it works

- **Activity** (`MainActivity.kt`)
  - Shows UI and **opens a device picker** using `UsbManager.deviceList`.
  - Starts the foreground service with action `ACTION_START` and passes the chosen device in extra `EXTRA_DEVICE`.
- **Service** (`GpsUsbForegroundService.kt`)
  - Starts in the foreground immediately (stable notification, category `SERVICE`).
  - If started **with a device extra** → calls `handleDeviceAttached(device, explicit = true)` → skips VID/PID filtering and **does not scan** others.
  - If started **without a device** → optional `scanAndAttachIfPresent()` (kept for auto-attach scenarios).
  - Spins up `NmeaTcpServer` on port **10110** and forwards every NMEA line to connected clients; also broadcasts UI updates back to the activity.
- **USB permission**
  - If permission is missing, requests it with a `PendingIntent` (broadcast `ACTION_USB_PERMISSION`).
  - On grant, opens the serial port and starts reading.

### Intents & Extras (public contract)

- `ACTION_START` / `ACTION_STOP`
- `EXTRA_DEVICE` → `UsbDevice` chosen by the user
- UI broadcasts from service to activity:
  - `ACTION_UI_LOG` (text)
  - `ACTION_UI_STATUS` (text)
  - `ACTION_UI_NMEA` (text)
  - `ACTION_UI_CLIENTS` (int)

---

## ⚙ Configuration (optional)

VID/PID filtering is **no longer required** when using the picker. The service path with `explicit = true` accepts any device that has a serial driver (`hasSerialDriver(...)`).

You can still keep/adjust `isGpsDevice(...)` for **auto-scan** / security:

- Auto mode (no explicit device) uses: `isGpsDevice(device) || hasSerialDriver(device)`.
- Picker mode (explicit device) uses: `hasSerialDriver(device)` only.

**TCP port**: default is `10110` (change in `NmeaTcpServer` initialization if needed).

---

## 🗂 Project Structure

- `MainActivity.kt` – UI, device picker, broadcast receiver for UI updates.
- `GpsUsbForegroundService.kt` – USB handling, permissions, reader lifecycle, TCP server, foreground notification.
- `UsbNmeaReader.kt` – Serial open/read loop and callbacks.
- `NmeaTcpServer.kt` – Lightweight TCP server, multi-clients broadcast.
- `StopServiceConfirmActivity.kt` – Simple confirmation dialog to stop the service.
- `UsbAttachReceiver.kt` – (optional) Listen to device attach if you enable it.
- `res/layout/activity_main.xml` – Main UI.
- `res/drawable/*` – Icons and shapes (see below).
- `res/values/strings.xml` – User-visible texts.

---

## 🏗 Build & Dependencies

**Gradle (module):**

```gradle
dependencies {
    implementation "androidx.core:core-ktx:1.13.1"
    implementation "androidx.appcompat:appcompat:1.7.0"

    // USB Serial for Android (mik3y)
    implementation "com.github.mik3y:usb-serial-for-android:3.8.6"

    // (UI) if you use Material, add material-components
    // implementation "com.google.android.material:material:1.12.0"
}
```

**Compile/target**: tested with `compileSdk 36`, `targetSdk 36`, `minSdk 29`.

---

## 📝 Android Manifest & Permissions

```xml
<uses-feature android:name="android.hardware.usb.host" />
<uses-permission android:name="android.permission.INTERNET" />

<!-- Foreground service (Android 10+) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<!-- Android 14+: choose types you actually use -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<!-- Android 13+: notifications runtime permission -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Notes:

- The service promotes itself to **foreground immediately** and creates a Notification Channel before notifying.
- The notification is ongoing (non-dismissable by design). Some OEMs may still allow swiping; we re-post it via a deleteIntent guard.

---

## 🖼 Resources (drawables/strings)

**Required drawables** (you can replace with your own assets):

- `@drawable/ic_stat_name` – small icon for the foreground notification (vector recommended).
- `@drawable/bg_big_toggle_pill` – background of the big toggle button (selector with `state_activated`).
- `@drawable/ic_power` – vector icon for the toggle.

**Strings** (add if missing):

```xml
<string name="select_usb_device_title">Select a USB device</string>
<string name="usb_aucun_device">No USB device detected</string>
<string name="usb_permission_demande">Requesting USB permission…</string>
```

(Plus the existing strings used across the app: `usb_searching`, `usb_connecte`, `usb_disconnected`, etc.)

---

## 🧰 Troubleshooting

| Symptom                                       | Likely Cause                                         | Fix                                                                                        |
| --------------------------------------------- | ---------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| Crash at app start                            | Missing/invalid notification small icon              | Ensure `@drawable/ic_stat_name` exists and is a valid vector.                              |
| Layout inflation crash                        | Missing drawables referenced by layout               | Ensure `bg_big_toggle_pill.xml` & `ic_power.xml` exist.                                    |
| Picker never shows                            | Two conflicting click listeners                      | Keep only the listener that calls `showUsbDevicePicker()`.                                 |
| Still logs “VID/PID check” after using picker | `explicit=true` not passed to `handleDeviceAttached` | In `onStartCommand`, call `handleDeviceAttached(device, explicit = true)`.                 |
| No data to OpenCPN                            | Wrong IP/port or permission denied                   | Accept USB permission prompt; verify TCP `10110` and device IP on the same LAN.            |
| USB denied repeatedly                         | Permission not persisted                             | Re-plug and accept prompt; ensure `PendingIntent` broadcast is set on `requestPermission`. |

---

## ❓ FAQ

**Q: Do I still need to hardcode Vendor/Product IDs?**\
A: **No** when starting via the picker. The service checks for a compatible serial driver instead. You may keep VID/PID filtering for auto-scan mode.

**Q: Can I auto-start on device attach?**\
A: Yes. Keep `scanAndAttachIfPresent()` path and/or a `BroadcastReceiver` for `UsbManager.ACTION_USB_DEVICE_ATTACHED`. For stability during tests, picker-first behavior is the default.

**Q: Which devices are supported?**\
A: Any device supported by **mik3y/usb-serial-for-android** or exposing a CDC/ACM class should work.

---

## 📄 License

Distributed under **CC BY-NC-SA 4.0** (Non‑commercial, credit required, share alike).

