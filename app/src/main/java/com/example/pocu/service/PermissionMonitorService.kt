package com.example.pocu.service

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.pocu.R
import com.example.pocu.admin.AppDeviceAdminReceiver
import com.example.pocu.data.AppPreferences

/**
 * Service that monitors permissions and activates lockdown mode if any critical
 * permission is revoked. Also checks if permissions are restored to disable lockdown.
 */
class PermissionMonitorService : Service() {

    private lateinit var prefs: AppPreferences
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false

    companion object {
        private const val TAG = "PermissionMonitorSvc"
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "permission_monitor_channel"
        private const val CHECK_INTERVAL_MS = 3000L // Check every 3 seconds

        fun start(context: Context) {
            val intent = Intent(context, PermissionMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PermissionMonitorService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AppDeviceAdminReceiver::class.java)
        Log.d(TAG, "PermissionMonitorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        if (!isMonitoring) {
            isMonitoring = true
            startPermissionMonitoring()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitor de Protección",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitorea la protección del dispositivo"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val text = if (prefs.isLockdownMode()) {
            "🔒 MODO BLOQUEO - Restaura permisos para desbloquear"
        } else {
            "Monitoreando permisos del sistema"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (prefs.isLockdownMode()) "⚠️ Dispositivo bloqueado" else "Protección activa")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(if (prefs.isLockdownMode()) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun startPermissionMonitoring() {
        handler.post(permissionCheckRunnable)
    }

    private val permissionCheckRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return

            checkPermissions()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    private fun checkPermissions() {
        val currentDeviceAdmin = devicePolicyManager.isAdminActive(adminComponent)
        val currentAccessibility = isAccessibilityServiceEnabled()
        val currentOverlay = Settings.canDrawOverlays(this)
        val currentUsageStats = hasUsageStatsPermission()

        // If in lockdown mode, check if all permissions are restored
        if (prefs.isLockdownMode()) {
            checkIfCanDisableLockdown(currentDeviceAdmin, currentAccessibility, currentOverlay, currentUsageStats)
            return
        }

        // Only check for revocations if service was enabled and permissions were previously granted
        if (!prefs.isServiceEnabled() || !prefs.werePermissionsGranted()) {
            return
        }

        val wasDeviceAdmin = prefs.wasDeviceAdminGranted()
        val wasAccessibility = prefs.wasAccessibilityGranted()
        val wasOverlay = prefs.wasOverlayGranted()
        val wasUsageStats = prefs.wasUsageStatsGranted()

        val revokedPermissions = mutableListOf<String>()

        // Check if any permission was revoked
        if (wasDeviceAdmin && !currentDeviceAdmin) {
            revokedPermissions.add("Device Admin")
            Log.w(TAG, "!!! Device Admin permission REVOKED !!!")
        }

        if (wasAccessibility && !currentAccessibility) {
            revokedPermissions.add("Accesibilidad")
            Log.w(TAG, "!!! Accessibility permission REVOKED !!!")
        }

        if (wasOverlay && !currentOverlay) {
            revokedPermissions.add("Superposición")
            Log.w(TAG, "!!! Overlay permission REVOKED !!!")
        }

        if (wasUsageStats && !currentUsageStats) {
            revokedPermissions.add("Uso de apps")
            Log.w(TAG, "!!! Usage Stats permission REVOKED !!!")
        }

        if (revokedPermissions.isNotEmpty()) {
            Log.w(TAG, "Permissions revoked: $revokedPermissions - Activating lockdown mode!")
            activateLockdown(revokedPermissions.joinToString(", "))
        }
    }

    private fun checkIfCanDisableLockdown(
        currentDeviceAdmin: Boolean,
        currentAccessibility: Boolean,
        currentOverlay: Boolean,
        currentUsageStats: Boolean
    ) {
        // Si es un bloqueo temporal, no hacer nada (dejar que expire)
        if (prefs.getLockdownUntil() > 0) {
            if (prefs.isTemporaryLockdownExpired()) {
                prefs.clearTemporaryLockdown()
            }
            return
        }

        val wasDeviceAdmin = prefs.wasDeviceAdminGranted()
        val wasAccessibility = prefs.wasAccessibilityGranted()
        val wasOverlay = prefs.wasOverlayGranted()
        val wasUsageStats = prefs.wasUsageStatsGranted()

        // Check if all previously granted permissions are restored
        val adminOk = !wasDeviceAdmin || currentDeviceAdmin
        val accessibilityOk = !wasAccessibility || currentAccessibility
        val overlayOk = !wasOverlay || currentOverlay
        val usageOk = !wasUsageStats || currentUsageStats

        if (adminOk && accessibilityOk && overlayOk && usageOk) {
            Log.d(TAG, "All permissions restored - disabling lockdown mode")
            disableLockdown()
        } else {
            Log.d(TAG, "Still missing permissions - Admin: $adminOk, Accessibility: $accessibilityOk, Overlay: $overlayOk, Usage: $usageOk")
        }
    }

    private fun activateLockdown(reason: String) {
        prefs.setLockdownMode(true)
        prefs.setLockdownReason(reason)
        updateNotification()

        handler.post {
            Toast.makeText(
                this,
                "🔒 MODO BLOQUEO ACTIVADO\n$reason\nRestaura los permisos o reinstala Pocu",
                Toast.LENGTH_LONG
            ).show()
        }

        Log.w(TAG, "LOCKDOWN MODE ACTIVATED - Reason: $reason")
    }

    private fun disableLockdown() {
        prefs.clearLockdownMode()
        updateNotification()

        // Update saved permissions state
        prefs.saveAllPermissionsState(
            deviceAdmin = devicePolicyManager.isAdminActive(adminComponent),
            accessibility = isAccessibilityServiceEnabled(),
            overlay = Settings.canDrawOverlays(this),
            usageStats = hasUsageStatsPermission()
        )

        handler.post {
            Toast.makeText(
                this,
                "✅ Permisos restaurados - Dispositivo desbloqueado",
                Toast.LENGTH_LONG
            ).show()
        }

        Log.d(TAG, "LOCKDOWN MODE DISABLED - Permissions restored")
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = enabledServices.split(":")
        val myService = ComponentName(this, AppBlockerAccessibilityService::class.java).flattenToString()

        return colonSplitter.any { it.equals(myService, ignoreCase = true) }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }


    override fun onDestroy() {
        super.onDestroy()
        isMonitoring = false
        handler.removeCallbacks(permissionCheckRunnable)
        Log.d(TAG, "PermissionMonitorService destroyed")
    }
}

