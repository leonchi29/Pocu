package com.example.pocu.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.pocu.R
import com.example.pocu.data.AppPreferences
import com.example.pocu.ui.BlockerOverlayActivity
import com.example.pocu.ui.MainActivity

class AppBlockerService : Service() {

    private lateinit var prefs: AppPreferences
    private lateinit var handler: Handler
    private lateinit var usageStatsManager: UsageStatsManager

    private var isMonitoring = false
    private var lastBlockedPackage: String? = null
    private var lastBlockTime: Long = 0

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "app_blocker_channel"
        private const val CHECK_INTERVAL_MS = 500L
        private const val BLOCK_COOLDOWN_MS = 1000L

        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, AppBlockerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AppBlockerService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        handler = Handler(Looper.getMainLooper())
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        isRunning = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return

            checkForegroundApp()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    private fun startMonitoring() {
        isMonitoring = true
        handler.post(monitorRunnable)
    }

    private fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacks(monitorRunnable)
    }

    private fun checkForegroundApp() {
        // Skip if accessibility service is running (it handles blocking)
        if (AppBlockerAccessibilityService.isRunning) return

        // Check if blocking is enabled and we're in a blocked time period
        if (!prefs.isServiceEnabled()) return
        if (!prefs.isCurrentlyBlocked()) return

        val foregroundApp = getForegroundApp() ?: return

        // Skip system UI and our own app
        if (foregroundApp == "com.android.systemui" ||
            foregroundApp == "com.example.pocu") {
            return
        }

        // Check if app is allowed
        if (prefs.isAppAllowed(foregroundApp)) return

        // Prevent rapid re-blocking
        val now = System.currentTimeMillis()
        if (foregroundApp == lastBlockedPackage && (now - lastBlockTime) < BLOCK_COOLDOWN_MS) {
            return
        }

        // Block the app
        lastBlockedPackage = foregroundApp
        lastBlockTime = now

        showBlockerOverlay(foregroundApp)
    }

    private fun getForegroundApp(): String? {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 // Last minute

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (usageStats.isNullOrEmpty()) return null

        return usageStats
            .filter { it.lastTimeUsed > 0 }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    private fun showBlockerOverlay(blockedPackage: String) {
        val intent = Intent(this, BlockerOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(BlockerOverlayActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
        }
        startActivity(intent)
    }
}

