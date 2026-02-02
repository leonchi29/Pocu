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
            "com.lge.launcher2",
            "com.lge.launcher3",
            "com.asus.launcher",
            "com.hihonor.android.launcher",
            "com.realme.launcher",
            "com.nothing.launcher",
            "com.transsion.hilauncher",
            "com.bbk.launcher2",
            "com.android.quicksearchbox",
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

        // Lista de launchers dinámicamente detectados
        private var detectedLaunchers: Set<String> = emptySet()

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
        detectLaunchers()
        Log.d(TAG, "=== Service CREATED ===")
    }

    private fun detectLaunchers() {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            intent.addCategory(android.content.Intent.CATEGORY_HOME)
            val launchers = packageManager.queryIntentActivities(intent, 0)
            detectedLaunchers = launchers.map { it.activityInfo.packageName }.toSet()
            Log.d(TAG, "Detected launchers: $detectedLaunchers")
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting launchers", e)
            detectedLaunchers = emptySet()
        }
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

        // Skip keyboards first - always allowed
        if (packageName.contains("inputmethod") || packageName.contains("keyboard")) {
            Log.d(TAG, "Skipping keyboard: $packageName")
            return
        }

        // Skip if same package as before
        if (packageName == currentForegroundPackage) return
        currentForegroundPackage = packageName

        Log.d(TAG, ">>> Window changed to: $packageName")

        // ==================== LOCKDOWN MODE ====================
        // Durante el bloqueo temporal, bloquear TODAS las apps excepto launcher y nuestra app
        if (prefs.isLockdownMode()) {
            // Check if temporary lockdown has expired
            if (prefs.isTemporaryLockdownExpired()) {
                Log.d(TAG, "Temporary lockdown expired, clearing...")
                prefs.clearTemporaryLockdown()
                // Continue with normal flow after lockdown expires
            } else {
                // LOCKDOWN ACTIVO - Solo permitir launcher, teclados y nuestra app

                // Permitir nuestra app durante el lockdown
                if (packageName == "com.example.pocu") {
                    Log.d(TAG, "Lockdown - allowing our app")
                    return
                }

                // Permitir launcher/system UI durante lockdown
                if (isSystemPackage(packageName)) {
                    Log.d(TAG, "Lockdown - allowing system package: $packageName")
                    return
                }

                // Bloquear TODO lo demás durante lockdown
                Log.d(TAG, "!!! LOCKDOWN MODE - BLOCKING APP: $packageName !!!")
                lastBlockedPackage = packageName
                lastBlockTime = System.currentTimeMillis()
                showLockdownOverlay(packageName)
                return
            }
        }

        // ==================== NORMAL MODE (no lockdown) ====================

        // Nuestra app siempre permitida
        if (packageName == "com.example.pocu") {
            Log.d(TAG, "In our own app - allowed")
            wasInOurApp = true
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

        // Skip launchers and system UI
        if (isSystemPackage(packageName)) {
            Log.d(TAG, "Skipping launcher/system UI: $packageName")
            return
        }

        // ==================== UNINSTALL PROTECTION ====================
        // Detectar intento de desinstalar POCU específicamente
        // Solo bloquear si el estudiante tiene sesión activa
        if (isAttemptingToUninstallPocu(event, packageName, className)) {
            if (prefs.isStudentLoggedIn()) {
                Log.d(TAG, "!!! BLOCKING POCU UNINSTALL ATTEMPT - Student is logged in !!!")
                prefs.setPermissionLockdown() // Bloqueo acumulativo de 20 segundos
                lastBlockedPackage = packageName
                lastBlockTime = System.currentTimeMillis()
                showBlockerOverlay(packageName, true)
                return
            } else {
                Log.d(TAG, "Allowing POCU uninstall - Student is NOT logged in")
                // Permitir desinstalar si no hay sesión
            }
        }

        // ==================== PERMISSION PROTECTION ====================
        // Solo bloquear permisos y configuraciones peligrosas SI hay sesión activa
        // Sin sesión activa, el usuario puede tocar los permisos libremente
        if (!prefs.isStudentLoggedIn()) {
            Log.d(TAG, "No active session - allowing access to permissions and settings")
            // Continuar con el flujo normal (no bloquear permisos)
        } else {
            // CON SESIÓN ACTIVA: Bloquear acceso a permisos y configuraciones peligrosas
            if (isSettingsOrPackageManager(packageName)) {
                Log.d(TAG, "!!! BLOCKING DANGEROUS APP (student logged in): $packageName !!!")
                Log.w(TAG, "!!! ATTEMPTING TO ACCESS PERMISSIONS - ACTIVATING LOCKDOWN !!!")
                prefs.setPermissionLockdown()
                lastBlockedPackage = packageName
                lastBlockTime = System.currentTimeMillis()
                showBlockerOverlay(packageName, true)
                return
            }

            // Block dangerous sections WITHIN Settings app
            if (packageName.contains("settings") && isDangerousSettingsSection(className)) {
                Log.d(TAG, "!!! BLOCKING DANGEROUS SETTINGS SECTION (student logged in): $className !!!")
                Log.w(TAG, "!!! ATTEMPTING TO ACCESS DANGEROUS SETTINGS - ACTIVATING LOCKDOWN !!!")
                prefs.setPermissionLockdown()
                lastBlockedPackage = packageName
                lastBlockTime = System.currentTimeMillis()
                showBlockerOverlay(packageName, true)
                return
            }
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
               detectedLaunchers.contains(packageName) ||
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

        // Si el estudiante NO tiene sesión activa, permitir acceso a app info/uninstall
        if (!prefs.isStudentLoggedIn()) {
            Log.d(TAG, "Student not logged in - allowing access to app management sections")
            // Solo bloquear secciones de permisos críticas, pero permitir desinstalar
            return lowerClassName.contains("accessibilitysettings") ||
                   lowerClassName.contains("accessibility") && !lowerClassName.contains("fragment") ||
                   lowerClassName.contains("deviceadmin") ||
                   lowerClassName.contains("permissionmanager") ||
                   lowerClassName.contains("appops") ||
                   lowerClassName.contains("usageaccess") ||
                   lowerClassName.contains("specialaccess") ||
                   lowerClassName.contains("displayoverotherapp") ||
                   lowerClassName.contains("drawoverlay")
        }

        // Si el estudiante TIENE sesión activa, bloquear todo incluyendo desinstalación
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
               lowerClassName.contains("allapps") ||
               lowerClassName.contains("uninstall")
    }

    /**
     * Detecta si el usuario está intentando desinstalar POCU específicamente
     * Esto incluye:
     * - Diálogo de desinstalación del sistema
     * - Pantalla de información de la app POCU
     * - Cualquier actividad relacionada con desinstalar "com.example.pocu"
     */
    private fun isAttemptingToUninstallPocu(event: AccessibilityEvent, packageName: String, className: String): Boolean {
        val lowerClassName = className.lowercase()
        val lowerPackageName = packageName.lowercase()

        // Detectar Package Installer con diálogo de desinstalación
        if (lowerPackageName.contains("packageinstaller") ||
            lowerPackageName.contains("packageuninstaller")) {
            // Intentar obtener texto del evento para ver si menciona POCU
            val eventText = getEventTextContent(event)
            if (eventText.contains("pocu", ignoreCase = true) ||
                eventText.contains("com.example.pocu", ignoreCase = true)) {
                Log.d(TAG, "Detected POCU uninstall dialog in package installer")
                return true
            }
            // Si no podemos verificar el texto, asumimos que es intento de desinstalar POCU
            // porque el estudiante ya está en el instalador de paquetes
            Log.d(TAG, "Package installer detected - assuming POCU uninstall attempt")
            return true
        }

        // Detectar pantalla de información de app para POCU
        if (lowerClassName.contains("appinfo") ||
            lowerClassName.contains("installedappdetails") ||
            lowerClassName.contains("applicationinfo")) {
            // Intentar detectar si es la info de POCU
            val eventText = getEventTextContent(event)
            if (eventText.contains("pocu", ignoreCase = true) ||
                eventText.contains("com.example.pocu", ignoreCase = true)) {
                Log.d(TAG, "Detected POCU app info screen")
                return true
            }
        }

        // Detectar diálogo de confirmación de desinstalación
        if (lowerClassName.contains("uninstallactivity") ||
            lowerClassName.contains("uninstalldialog") ||
            lowerClassName.contains("deleteconfirmation")) {
            val eventText = getEventTextContent(event)
            if (eventText.contains("pocu", ignoreCase = true) ||
                eventText.contains("com.example.pocu", ignoreCase = true)) {
                Log.d(TAG, "Detected POCU uninstall confirmation dialog")
                return true
            }
        }

        return false
    }

    /**
     * Obtiene el texto contenido en el evento de accesibilidad
     */
    private fun getEventTextContent(event: AccessibilityEvent): String {
        val textBuilder = StringBuilder()

        // Obtener texto del evento
        event.text.forEach { charSeq ->
            textBuilder.append(charSeq.toString()).append(" ")
        }

        // Obtener descripción del contenido
        event.contentDescription?.let {
            textBuilder.append(it.toString()).append(" ")
        }

        // Intentar obtener texto de la fuente
        try {
            event.source?.let { source ->
                source.text?.let {
                    textBuilder.append(it.toString()).append(" ")
                }
                source.contentDescription?.let {
                    textBuilder.append(it.toString()).append(" ")
                }
            }
        } catch (e: Exception) {
            // Ignorar errores al acceder a la fuente
        }

        return textBuilder.toString()
    }

    private fun showBlockerOverlay(blockedPackage: String, isPermissionBlock: Boolean = false) {
        try {
            val intent = Intent(this, BlockerOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                putExtra(BlockerOverlayActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
                putExtra(BlockerOverlayActivity.EXTRA_IS_PERMISSION_BLOCK, isPermissionBlock)
            }
            startActivity(intent)
            Log.d(TAG, "Blocker overlay launched for: $blockedPackage (permissionBlock: $isPermissionBlock)")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching blocker overlay", e)
        }
    }

    private fun isAllowedInLockdown(packageName: String): Boolean {
        return LOCKDOWN_ALLOWED_PACKAGES.contains(packageName) ||
               detectedLaunchers.contains(packageName) ||
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

