package com.example.pocu.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.pocu.data.AppPreferences
import com.example.pocu.ui.BlockerOverlayActivity
import com.example.pocu.ui.LockdownOverlayActivity

class AppBlockerAccessibilityService : AccessibilityService() {

    private lateinit var prefs: AppPreferences
    private var lastBlockedPackage: String? = null
    private var lastBlockTime: Long = 0
    private var currentForegroundPackage: String? = null
    private var wasInOurApp: Boolean = false
    private var ourAppExitTime: Long = 0

    companion object {
        private const val TAG = "AppBlockerService"
        private const val BLOCK_COOLDOWN_MS = 500L
        private const val OUR_APP_GRACE_PERIOD_MS = 2000L // 2 seconds grace period after leaving our app
        var isRunning = false
            private set

        // ONLY launchers, keyboards and our app - everything else gets blocked (including Settings!)
        private val IGNORED_PACKAGES = setOf(
            // Launchers (needed to go home)
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher",
            "com.oneplus.launcher",
            "com.motorola.launcher3",
            "com.teslacoilsw.launcher",
            "com.microsoft.launcher",
            "com.nova.launcher",
            // System UI (notifications, status bar)
            "com.android.systemui",
            // Keyboards
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.swiftkey.swiftkey",
            "com.touchtype.swiftkey",
            "com.android.inputmethod.latin",
            "com.sec.android.inputmethod",
            "com.lge.ime",
            // Our app
            "com.example.pocu"
        )

        // Apps allowed during lockdown mode (Play Store and Settings to reinstall the app)
        private val LOCKDOWN_ALLOWED_PACKAGES = setOf(
            "com.android.vending", // Google Play Store
            "com.example.pocu", // Our app
            // Settings app (needed to reinstall or restore permissions)
            "com.android.settings",
            "com.samsung.android.settings",
            "com.miui.settings",
            "com.oppo.settings",
            "com.vivo.settings",
            "com.huawei.settings",
            "com.oneplus.settings",
            // Launchers
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher",
            "com.oneplus.launcher",
            "com.motorola.launcher3",
            "com.teslacoilsw.launcher",
            "com.microsoft.launcher",
            "com.nova.launcher",
            // System UI
            "com.android.systemui",
            // Keyboards
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.swiftkey.swiftkey",
            "com.touchtype.swiftkey",
            "com.android.inputmethod.latin",
            "com.sec.android.inputmethod",
            "com.lge.ime"
        )

        // Apps that should ALWAYS be blocked (permissions, accessibility, app managers)
        // NOTE: We allow package installers so user can uninstall OTHER apps (not Pocu)
        private val ALWAYS_BLOCKED_PACKAGES = setOf(
            // Permission controllers
            "com.android.permissioncontroller",
            "com.samsung.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            // Security centers (used to manage apps/permissions)
            "com.miui.securitycenter",
            "com.huawei.systemmanager",
            "com.coloros.safecenter",
            "com.iqoo.secure",
            "com.oppo.safe",
            "com.vivo.permissionmanager",
            "com.samsung.android.sm",
            "com.samsung.android.lool",
            // Accessibility settings shortcuts
            "com.android.accessibility",
            "com.samsung.accessibility"
        )
    }

    // Track when service was just enabled (to give grace period for returning from settings)
    private var serviceConnectedTime: Long = 0
    private val SERVICE_STARTUP_GRACE_PERIOD_MS = 5000L // 5 seconds after enabling accessibility

    // Settings packages to allow during setup
    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.miui.settings",
        "com.oppo.settings",
        "com.vivo.settings",
        "com.huawei.settings",
        "com.oneplus.settings"
    )

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        Log.d(TAG, "=== Service CREATED ===")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        serviceConnectedTime = System.currentTimeMillis()

        // Configure the service
        val info = serviceInfo
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                          AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        info.notificationTimeout = 50
        serviceInfo = info

        Log.d(TAG, "=== Service CONNECTED and CONFIGURED - Grace period started ===")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Grace period after service just connected (user returning from accessibility settings)
        val timeSinceConnected = System.currentTimeMillis() - serviceConnectedTime
        if (timeSinceConnected < SERVICE_STARTUP_GRACE_PERIOD_MS) {
            Log.d(TAG, "In startup grace period (${timeSinceConnected}ms), not blocking anything")
            return
        }

        // Only process window state changes
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // If service is disabled in preferences, don't block anything
        if (!prefs.isServiceEnabled()) {
            Log.d(TAG, "Service is disabled in preferences, not blocking")
            return
        }

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        // Allow Settings app for an extended period (10 seconds) after service starts
        // This allows the user to navigate back to the app after enabling accessibility
        val extendedGracePeriod = 10000L // 10 seconds
        if (timeSinceConnected < extendedGracePeriod && SETTINGS_PACKAGES.contains(packageName)) {
            Log.d(TAG, "Allowing Settings during extended grace period: $packageName")
            return
        }

        // FIRST: Always ignore our own app - this is critical!
        if (packageName == "com.example.pocu") {
            Log.d(TAG, "In our own app - setting flag")
            wasInOurApp = true
            currentForegroundPackage = packageName
            return
        }

        // If we just left our app, set the exit time
        if (wasInOurApp) {
            ourAppExitTime = System.currentTimeMillis()
            wasInOurApp = false
            Log.d(TAG, "Just left our app, starting grace period")
        }

        // Grace period: don't block right after leaving our app (for keyboard, etc.)
        val timeSinceOurApp = System.currentTimeMillis() - ourAppExitTime
        if (timeSinceOurApp < OUR_APP_GRACE_PERIOD_MS) {
            Log.d(TAG, "In grace period (${timeSinceOurApp}ms), not blocking: $packageName")
            return
        }

        // Skip keyboards
        if (packageName.contains("inputmethod") || packageName.contains("keyboard")) {
            Log.d(TAG, "Skipping keyboard: $packageName")
            return
        }

        // Skip if same package as before
        if (packageName == currentForegroundPackage) return
        currentForegroundPackage = packageName

        Log.d(TAG, ">>> Window changed to: $packageName")

        // ==================== LOCKDOWN MODE ====================
        // If in lockdown mode, block EVERYTHING except Play Store, Settings and essentials
        // But still block dangerous sections within Settings!
        if (prefs.isLockdownMode()) {
            // Block dangerous settings sections even in lockdown
            // Only allow main Settings screen, not app management/permissions
            if (packageName.contains("settings") && isDangerousSettingsSection(className)) {
                Log.d(TAG, "!!! LOCKDOWN - BLOCKING DANGEROUS SETTINGS SECTION: $className !!!")
                lastBlockedPackage = packageName
                lastBlockTime = System.currentTimeMillis()
                showLockdownOverlay(packageName)
                return
            }

            // Block dangerous packages even in lockdown
            if (isSettingsOrPackageManager(packageName)) {
                Log.d(TAG, "!!! LOCKDOWN - BLOCKING DANGEROUS PACKAGE: $packageName !!!")
                lastBlockedPackage = packageName
                lastBlockTime = System.currentTimeMillis()
                showLockdownOverlay(packageName)
                return
            }

            if (isAllowedInLockdown(packageName)) {
                Log.d(TAG, "Lockdown mode - allowing essential app: $packageName")
                return
            }

            Log.d(TAG, "!!! LOCKDOWN MODE - BLOCKING APP: $packageName !!!")
            lastBlockedPackage = packageName
            lastBlockTime = System.currentTimeMillis()
            showLockdownOverlay(packageName)
            return
        }

        // ==================== NORMAL MODE ====================

        // Skip launchers and system UI
        if (isSystemPackage(packageName)) {
            Log.d(TAG, "Skipping launcher/system UI: $packageName")
            return
        }

        // ALWAYS block dangerous sections - EVEN IF SERVICE IS DISABLED!
        // This prevents students from modifying permissions or uninstalling the app
        if (isSettingsOrPackageManager(packageName)) {
            Log.d(TAG, "!!! ALWAYS BLOCKING DANGEROUS APP: $packageName !!!")
            // Activate lockdown mode if trying to access dangerous settings
            if (prefs.isServiceEnabled() && prefs.werePermissionsGranted()) {
                Log.w(TAG, "!!! ATTEMPTING TO ACCESS DANGEROUS APP - ACTIVATING LOCKDOWN !!!")
                prefs.setLockdownMode(true)
                prefs.setLockdownReason("Intento de acceso a configuración peligrosa")
            }
            lastBlockedPackage = packageName
            lastBlockTime = System.currentTimeMillis()
            showBlockerOverlay(packageName)
            return
        }

        // Block dangerous sections WITHIN Settings app
        if (packageName.contains("settings") && isDangerousSettingsSection(className)) {
            Log.d(TAG, "!!! BLOCKING DANGEROUS SETTINGS SECTION: $className !!!")
            // Activate lockdown mode if trying to access dangerous settings
            if (prefs.isServiceEnabled() && prefs.werePermissionsGranted()) {
                Log.w(TAG, "!!! ATTEMPTING TO ACCESS DANGEROUS SETTINGS - ACTIVATING LOCKDOWN !!!")
                prefs.setLockdownMode(true)
                prefs.setLockdownReason("Intento de acceso a configuración peligrosa")
            }
            lastBlockedPackage = packageName
            lastBlockTime = System.currentTimeMillis()
            showBlockerOverlay(packageName)
            return
        }

        // Check if service/blocking is enabled (only for regular apps, not settings)
        if (!prefs.isServiceEnabled()) {
            Log.d(TAG, "Service is DISABLED in settings")
            return
        }

        // Check if we're in a blocked time period (class time)
        val isBlocked = prefs.isCurrentlyBlocked()
        Log.d(TAG, "Is currently blocked? $isBlocked")

        if (!isBlocked) {
            Log.d(TAG, "Not in blocked time period")
            return
        }

        // Check if app is in allowed list
        if (prefs.isAppAllowed(packageName)) {
            Log.d(TAG, "App is in ALLOWED list: $packageName")
            return
        }

        // Prevent rapid re-blocking of same app
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && (now - lastBlockTime) < BLOCK_COOLDOWN_MS) {
            return
        }

        // BLOCK THE APP!
        Log.d(TAG, "!!! BLOCKING APP: $packageName !!!")
        lastBlockedPackage = packageName
        lastBlockTime = now

        showBlockerOverlay(packageName)
    }

    private fun isSystemPackage(packageName: String): Boolean {
        // Only ignore launchers and system UI
        return IGNORED_PACKAGES.contains(packageName) ||
               packageName.contains("launcher") ||
               packageName.contains("home")
    }

    private fun isSettingsOrPackageManager(packageName: String): Boolean {
        // Only block permission controllers and security centers
        // Allow package installers so user can uninstall OTHER apps
        return ALWAYS_BLOCKED_PACKAGES.contains(packageName) ||
               packageName.contains("permissioncontroller") ||
               packageName.contains("securitycenter") ||
               packageName.contains("systemmanager") ||
               packageName.contains("safecenter") ||
               packageName.contains("accessibility") ||
               packageName.contains("permissionmanager")
    }

    private fun isDangerousSettingsSection(className: String): Boolean {
        val lowerClassName = className.lowercase()
        // Block these specific sections within Settings:
        // - Accessibility settings (to prevent disabling our service)
        // - Device admin settings (to prevent removing admin)
        // - Permission manager (to prevent changing our permissions)
        // - Special access (overlay, usage, etc.)
        // - App info/details (to prevent uninstalling Pocu)
        return lowerClassName.contains("accessibilitysettings") ||
               lowerClassName.contains("accessibility") && !lowerClassName.contains("fragment") ||
               lowerClassName.contains("deviceadmin") ||
               lowerClassName.contains("permissionmanager") ||
               lowerClassName.contains("appops") ||
               lowerClassName.contains("usageaccess") ||
               lowerClassName.contains("specialaccess") ||
               lowerClassName.contains("displayoverotherapp") ||
               lowerClassName.contains("drawoverlay") ||
               lowerClassName.contains("managedefaultapps") ||
               lowerClassName.contains("installedappdetails") ||
               lowerClassName.contains("appinfo") ||
               lowerClassName.contains("applicationinfo") ||
               lowerClassName.contains("manageapplications") ||
               lowerClassName.contains("allapps")
    }

    private fun showBlockerOverlay(blockedPackage: String) {
        try {
            val intent = Intent(this, BlockerOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                putExtra(BlockerOverlayActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            }
            startActivity(intent)
            Log.d(TAG, "Blocker overlay launched for: $blockedPackage")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching blocker overlay", e)
        }
    }

    private fun isAllowedInLockdown(packageName: String): Boolean {
        return LOCKDOWN_ALLOWED_PACKAGES.contains(packageName) ||
               packageName.contains("launcher") ||
               packageName.contains("home") ||
               packageName.contains("inputmethod") ||
               packageName.contains("keyboard") ||
               packageName.contains("settings") // Allow settings to restore permissions
    }

    private fun showLockdownOverlay(blockedPackage: String) {
        try {
            val intent = Intent(this, LockdownOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                putExtra(LockdownOverlayActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            }
            startActivity(intent)
            Log.d(TAG, "Lockdown overlay launched for: $blockedPackage")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching lockdown overlay", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "=== Service DESTROYED ===")
    }
}

