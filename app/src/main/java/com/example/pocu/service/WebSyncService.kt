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
import com.example.pocu.network.*
import kotlinx.coroutines.*
import java.util.UUID

/**
 * Servicio que mantiene comunicación con el servidor web.
 * - Envía heartbeats periódicos
 * - Recibe comandos remotos (lockdown, unlock, etc.)
 * - Reporta alertas (intentos de desinstalación, cambios de permisos)
 */
class WebSyncService : Service() {

    private lateinit var prefs: AppPreferences
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    companion object {
        private const val TAG = "WebSyncService"
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "web_sync_channel"

        // Intervalo de heartbeat (30 segundos en modo normal, 10 segundos después de alerta)
        private const val HEARTBEAT_INTERVAL_NORMAL = 30_000L
        private const val HEARTBEAT_INTERVAL_ALERT = 10_000L

        private var currentHeartbeatInterval = HEARTBEAT_INTERVAL_NORMAL

        fun start(context: Context) {
            val intent = Intent(context, WebSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WebSyncService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AppDeviceAdminReceiver::class.java)

        createNotificationChannel()
        Log.d(TAG, "WebSyncService created")
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
        Log.d(TAG, "WebSyncService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sincronización Web",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la conexión con el servidor"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
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

    private fun startHeartbeatLoop() {
        handler.post(object : Runnable {
            override fun run() {
                if (isRunning && prefs.isWebSyncEnabled()) {
                    sendHeartbeat()
                    handler.postDelayed(this, currentHeartbeatInterval)
                }
            }
        })
    }

    private fun sendHeartbeat() {
        serviceScope.launch {
            try {
                val deviceToken = prefs.getDeviceToken()
                if (deviceToken.isNullOrEmpty()) {
                    Log.w(TAG, "No device token, skipping heartbeat")
                    return@launch
                }

                val request = HeartbeatRequest(
                    deviceId = getOrCreateDeviceId(),
                    timestamp = System.currentTimeMillis(),
                    isServiceEnabled = prefs.isServiceEnabled(),
                    isLockdownMode = prefs.isLockdownMode(),
                    permissions = getCurrentPermissions(),
                    batteryLevel = getBatteryLevel()
                )

                val response = ApiClient.getApiService().sendHeartbeat(
                    token = "Bearer $deviceToken",
                    request = request
                )

                if (response.isSuccessful) {
                    val serverResponse = response.body()
                    serverResponse?.commands?.let { commands ->
                        processCommands(commands)
                    }
                    // Volver a intervalo normal después de éxito
                    currentHeartbeatInterval = HEARTBEAT_INTERVAL_NORMAL
                    Log.d(TAG, "Heartbeat sent successfully")
                } else {
                    Log.e(TAG, "Heartbeat failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending heartbeat", e)
            }
        }
    }

    private fun processCommands(commands: List<ServerCommand>) {
        for (command in commands) {
            when (command.type) {
                "lockdown" -> {
                    Log.w(TAG, "Received LOCKDOWN command from server!")
                    prefs.setLockdownMode(true)
                    prefs.setLockdownReason("Bloqueo remoto activado por administrador")
                }
                "unlock" -> {
                    Log.d(TAG, "Received UNLOCK command from server")
                    prefs.clearLockdownMode()
                }
                "enable_service" -> {
                    Log.d(TAG, "Received ENABLE_SERVICE command from server")
                    prefs.setServiceEnabled(true)
                }
                "disable_service" -> {
                    Log.d(TAG, "Received DISABLE_SERVICE command from server")
                    prefs.setServiceEnabled(false)
                }
                "update_schedule" -> {
                    Log.d(TAG, "Received UPDATE_SCHEDULE command from server")
                    command.data?.let { data ->
                        updateSchedulesFromServer(data)
                    }
                }
                "update_allowed_apps" -> {
                    Log.d(TAG, "Received UPDATE_ALLOWED_APPS command from server")
                    command.data?.let { data ->
                        @Suppress("UNCHECKED_CAST")
                        val apps = data["apps"] as? List<String>
                        apps?.let {
                            prefs.saveAllowedApps(it.toSet())
                        }
                    }
                }
            }
        }
    }

    private fun updateSchedulesFromServer(data: Map<String, Any>) {
        try {
            @Suppress("UNCHECKED_CAST")
            val schedulesData = data["schedules"] as? List<Map<String, Any>> ?: return

            val schedules = schedulesData.map { scheduleMap ->
                Schedule(
                    id = (scheduleMap["id"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    name = scheduleMap["name"] as? String ?: "",
                    startHour = (scheduleMap["start_hour"] as? Number)?.toInt() ?: 0,
                    startMinute = (scheduleMap["start_minute"] as? Number)?.toInt() ?: 0,
                    endHour = (scheduleMap["end_hour"] as? Number)?.toInt() ?: 0,
                    endMinute = (scheduleMap["end_minute"] as? Number)?.toInt() ?: 0,
                    daysOfWeek = (scheduleMap["days_of_week"] as? List<Number>)?.map { it.toInt() } ?: listOf(2,3,4,5,6),
                    enabled = scheduleMap["enabled"] as? Boolean ?: true,
                    isClassTime = scheduleMap["is_class_time"] as? Boolean ?: true
                )
            }
            prefs.saveSchedules(schedules)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing schedules from server", e)
        }
    }

    private fun getCurrentPermissions(): PermissionsStatus {
        val hasDeviceAdmin = devicePolicyManager.isAdminActive(adminComponent)
        val hasAccessibility = isAccessibilityEnabled()
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
        val hasUsageStats = hasUsageStatsPermission()

        return PermissionsStatus(
            deviceAdmin = hasDeviceAdmin,
            accessibility = hasAccessibility,
            overlay = hasOverlay,
            usageStats = hasUsageStats
        )
    }

    private fun isAccessibilityEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 0
        )
        if (accessibilityEnabled != 1) return false

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(packageName)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun getBatteryLevel(): Int {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else -1
    }

    private fun getOrCreateDeviceId(): String {
        var deviceId = prefs.getDeviceId()
        if (deviceId.isNullOrEmpty()) {
            deviceId = UUID.randomUUID().toString()
            prefs.saveDeviceId(deviceId)
        }
        return deviceId
    }

    /**
     * Enviar alerta al servidor (llamar desde otros lugares de la app)
     */
    fun sendAlert(eventType: String, details: String?) {
        serviceScope.launch {
            try {
                val deviceToken = prefs.getDeviceToken() ?: return@launch

                val alert = AlertEvent(
                    deviceId = getOrCreateDeviceId(),
                    timestamp = System.currentTimeMillis(),
                    eventType = eventType,
                    details = details
                )

                ApiClient.getApiService().sendAlert(
                    token = "Bearer $deviceToken",
                    alert = alert
                )

                // Aumentar frecuencia de heartbeat después de alerta
                currentHeartbeatInterval = HEARTBEAT_INTERVAL_ALERT

                Log.d(TAG, "Alert sent: $eventType")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending alert", e)
            }
        }
    }
}

