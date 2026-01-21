package com.example.pocu.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.pocu.data.AppPreferences
import com.example.pocu.service.AppBlockerService
import com.example.pocu.service.PermissionMonitorService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            val prefs = AppPreferences(context)
            if (prefs.isServiceEnabled()) {
                AppBlockerService.start(context)
                // Start permission monitor to detect permission revocation
                if (prefs.werePermissionsGranted()) {
                    PermissionMonitorService.start(context)
                }
            }
        }
    }
}

