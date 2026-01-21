package com.example.pocu.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.pocu.R
import com.example.pocu.admin.AppDeviceAdminReceiver
import com.example.pocu.data.AppPreferences
import com.example.pocu.data.Schedule
import com.example.pocu.network.ApiClient
import com.example.pocu.network.AlertEvent
import com.example.pocu.network.HeartbeatRequest
import com.example.pocu.network.PermissionsStatus
import kotlinx.coroutines.*
import java.util.UUID

class SqlSyncService : Service() {

    private lateinit var prefs: AppPreferences
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    companion object {
        private const val TAG = "SqlSyncService"
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "sql_sync_channel"
        private const val HEARTBEAT_INTERVAL = 30_000L

        fun start(context: Context) {
            val intent = Intent(context, SqlSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SqlSyncService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AppDeviceAdminReceiver::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        if (!isRunning) {
            isRunning = true
            startHeartbeatLoop()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Sincronización SQL", NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pocu")
            .setContentText("Sincronizando...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun getOrCreateDeviceId(): String {
        var deviceId = prefs.getDeviceId()
        if (deviceId.isNullOrEmpty()) {
            deviceId = UUID.randomUUID().toString()
            prefs.saveDeviceId(deviceId)
        }
        return deviceId
    }

    private fun startHeartbeatLoop() {
        handler.post(object : Runnable {
            override fun run() {
                if (isRunning && prefs.isWebSyncEnabled()) {
                    sendHeartbeat()
                    handler.postDelayed(this, HEARTBEAT_INTERVAL)
                }
            }
        })
    }

    private fun sendHeartbeat() {
        serviceScope.launch {
            try {
                val deviceToken = prefs.getDeviceToken() ?: return@launch

                val request = HeartbeatRequest(
                    deviceId = getOrCreateDeviceId(),
                    timestamp = System.currentTimeMillis(),
                    isServiceEnabled = prefs.isServiceEnabled(),
                    isLockdownMode = prefs.isLockdownMode(),
                    permissions = getCurrentPermissions(),
                    batteryLevel = getBatteryLevel()
                )

                val response = ApiClient.getApiService().sendHeartbeat("Bearer $deviceToken", request)

                if (response.isSuccessful) {
                    response.body()?.commands?.forEach { cmd ->
                        processCommand(cmd.type, cmd.data)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending heartbeat", e)
            }
        }
    }

    private fun processCommand(command: String, data: Map<String, Any>?) {
        when (command) {
            "lockdown" -> {
                prefs.setLockdownMode(true)
                prefs.setLockdownReason(data?.get("reason") as? String ?: "Bloqueo remoto")
            }
            "unlock" -> prefs.clearLockdownMode()
            "enable_service" -> prefs.setServiceEnabled(true)
            "disable_service" -> prefs.setServiceEnabled(false)
            "update_schedules" -> {
                @Suppress("UNCHECKED_CAST")
                (data?.get("schedules") as? List<Map<String, Any>>)?.let { updateSchedules(it) }
            }
            "update_allowed_apps" -> {
                @Suppress("UNCHECKED_CAST")
                (data?.get("apps") as? List<String>)?.let { prefs.saveAllowedApps(it.toSet()) }
            }
        }
    }

    private fun updateSchedules(schedulesData: List<Map<String, Any>>) {
        val schedules = schedulesData.mapNotNull { s ->
            try {
                Schedule(
                    id = (s["id"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    name = s["name"] as? String ?: "",
                    startHour = (s["startHour"] as? Number)?.toInt() ?: 0,
                    startMinute = (s["startMinute"] as? Number)?.toInt() ?: 0,
                    endHour = (s["endHour"] as? Number)?.toInt() ?: 0,
                    endMinute = (s["endMinute"] as? Number)?.toInt() ?: 0,
                    daysOfWeek = (s["daysOfWeek"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: listOf(2,3,4,5,6),
                    enabled = s["enabled"] as? Boolean ?: true,
                    isClassTime = s["isClassTime"] as? Boolean ?: true
                )
            } catch (e: Exception) { null }
        }
        prefs.saveSchedules(schedules)
    }

    private fun getCurrentPermissions(): PermissionsStatus {
        return PermissionsStatus(
            deviceAdmin = devicePolicyManager.isAdminActive(adminComponent),
            accessibility = isAccessibilityEnabled(),
            overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true,
            usageStats = hasUsageStatsPermission()
        )
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        if (enabled != 1) return false
        val services = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return services.contains(packageName)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun getBatteryLevel(): Int {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    fun sendAlert(eventType: String, details: String?) {
        serviceScope.launch {
            try {
                val deviceToken = prefs.getDeviceToken() ?: return@launch
                val alert = AlertEvent(getOrCreateDeviceId(), System.currentTimeMillis(), eventType, details)
                ApiClient.getApiService().sendAlert("Bearer $deviceToken", alert)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending alert", e)
            }
        }
    }
}

