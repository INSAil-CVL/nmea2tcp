package com.insail.nmeagpsserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager

class UsbAttachReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action) {
            val svc = Intent(context, GpsUsbForegroundService::class.java)
                .setAction(GpsUsbForegroundService.ACTION_START)

            // ✅ Plus besoin de test sur la version Android, minSdk >= 29 → startForegroundService OK
            context.startForegroundService(svc)
        }
    }
}
