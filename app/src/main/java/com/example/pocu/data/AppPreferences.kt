package com.example.pocu.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

data class Schedule(
    val id: Long = System.currentTimeMillis(),
    val name: String = "",
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val daysOfWeek: List<Int> = listOf(2, 3, 4, 5, 6), // Lunes a Viernes por defecto
    val enabled: Boolean = true,
    val isClassTime: Boolean = true // true = Horario de Clases (bloqueado), false = Horario de Recreo (permitido)
)

class AppPreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_PIN = "pin"
        private const val KEY_SCHEDULES = "schedules"
        private const val KEY_ALLOWED_APPS = "allowed_apps"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_FIRST_RUN = "first_run"
        private const val KEY_INTERFACE_MODE = "interface_mode"
        private const val KEY_PERMISSIONS_GRANTED = "permissions_granted"
        private const val KEY_DEVICE_ADMIN_GRANTED = "device_admin_granted"
        private const val KEY_ACCESSIBILITY_GRANTED = "accessibility_granted"
        private const val KEY_OVERLAY_GRANTED = "overlay_granted"
        private const val KEY_USAGE_STATS_GRANTED = "usage_stats_granted"
        private const val KEY_LOCKDOWN_MODE = "lockdown_mode"
        private const val KEY_LOCKDOWN_REASON = "lockdown_reason"
        private const val KEY_LOCKDOWN_UNTIL = "lockdown_until"
        private const val KEY_LOCKDOWN_PENALTY_COUNT = "lockdown_penalty_count"
        private const val KEY_LAST_PENALTY_TIME = "last_penalty_time"

        // Web sync keys
        private const val KEY_WEB_SYNC_ENABLED = "web_sync_enabled"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_PARENT_CODE = "parent_code"

        // Student keys
        private const val KEY_STUDENT_ID = "student_id"
        private const val KEY_STUDENT_NUMERIC_ID = "student_numeric_id"
        private const val KEY_STUDENT_NAME = "student_name"
        private const val KEY_STUDENT_RUT = "student_rut"
        private const val KEY_STUDENT_EMAIL = "student_email"
        private const val KEY_SCHOOL_NAME = "school_name"
        private const val KEY_STUDENT_COURSE = "student_course"
        private const val KEY_DEVICE_SERIAL = "device_serial"
        private const val KEY_STUDENT_REGISTERED = "student_registered"

        const val MODE_BRAINROT = 0
        const val MODE_CLASSIC = 1

        // Default calculator package names
        val DEFAULT_ALLOWED_APPS = setOf(
            "com.android.calculator2",
            "com.google.android.calculator",
            "com.sec.android.app.popupcalculator",
            "com.miui.calculator",
            "com.huawei.calculator",
            "com.oppo.calculator",
            "com.vivo.calculator",
            "com.oneplus.calculator",
            "com.example.pocu" // Our own app
        )
    }

    // PIN Management
    fun savePin(pin: String) {
        securePrefs.edit().putString(KEY_PIN, pin).apply()
    }

    fun getPin(): String? {
        return securePrefs.getString(KEY_PIN, null)
    }

    fun hasPin(): Boolean {
        // El PIN siempre existe (es fijo: 2536)
        return true
    }

    fun verifyPin(pin: String): Boolean {
        // PIN fijo: 2536
        return pin == "2536"
    }

    // Schedule Management
    fun saveSchedules(schedules: List<Schedule>) {
        val json = gson.toJson(schedules)
        prefs.edit().putString(KEY_SCHEDULES, json).apply()
    }

    fun getSchedules(): List<Schedule> {
        val json = prefs.getString(KEY_SCHEDULES, null) ?: return emptyList()
        val type = object : TypeToken<List<Schedule>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addSchedule(schedule: Schedule) {
        val schedules = getSchedules().toMutableList()
        schedules.add(schedule)
        saveSchedules(schedules)
    }

    fun removeSchedule(scheduleId: Long) {
        val schedules = getSchedules().filter { it.id != scheduleId }
        saveSchedules(schedules)
    }

    fun updateSchedule(schedule: Schedule) {
        val schedules = getSchedules().map {
            if (it.id == schedule.id) schedule else it
        }
        saveSchedules(schedules)
    }

    // Allowed Apps Management
    fun saveAllowedApps(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_ALLOWED_APPS, apps).apply()
    }

    fun getAllowedApps(): Set<String> {
        return prefs.getStringSet(KEY_ALLOWED_APPS, DEFAULT_ALLOWED_APPS) ?: DEFAULT_ALLOWED_APPS
    }

    fun isAppAllowed(packageName: String): Boolean {
        return getAllowedApps().contains(packageName)
    }

    // Service State
    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    fun isServiceEnabled(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }

    // First Run
    fun isFirstRun(): Boolean {
        return prefs.getBoolean(KEY_FIRST_RUN, true)
    }

    fun setFirstRunComplete() {
        prefs.edit().putBoolean(KEY_FIRST_RUN, false).apply()
    }

    // Interface Mode
    fun setInterfaceMode(mode: Int) {
        prefs.edit().putInt(KEY_INTERFACE_MODE, mode).apply()
    }

    fun getInterfaceMode(): Int {
        return prefs.getInt(KEY_INTERFACE_MODE, MODE_BRAINROT) // Brainrot por defecto
    }

    fun isBrainrotMode(): Boolean {
        return getInterfaceMode() == MODE_BRAINROT
    }

    // Check if current time is within blocking schedule
    // Returns true if we're in a CLASS TIME (blocked) and NOT in a RECESS TIME (allowed)
    fun isCurrentlyBlocked(): Boolean {
        val schedules = getSchedules().filter { it.enabled }
        if (schedules.isEmpty()) {
            Log.d("AppPreferences", "No schedules found")
            return false
        }

        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute

        Log.d("AppPreferences", "Current time: $currentHour:$currentMinute ($currentTimeInMinutes mins), Day: $currentDayOfWeek")

        var inClassTime = false
        var inRecessTime = false

        for (schedule in schedules) {
            if (!schedule.daysOfWeek.contains(currentDayOfWeek)) {
                Log.d("AppPreferences", "Schedule '${schedule.name}' not for today (daysOfWeek: ${schedule.daysOfWeek})")
                continue
            }

            val startTimeInMinutes = schedule.startHour * 60 + schedule.startMinute
            val endTimeInMinutes = schedule.endHour * 60 + schedule.endMinute

            val isInThisSchedule = if (startTimeInMinutes <= endTimeInMinutes) {
                currentTimeInMinutes in startTimeInMinutes..endTimeInMinutes  // Usar .. para inclusive
            } else {
                // Overnight: from start to midnight OR from midnight to end
                currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes < endTimeInMinutes
            }

            Log.d("AppPreferences", "Schedule '${schedule.name}': ${schedule.startHour}:${schedule.startMinute}-${schedule.endHour}:${schedule.endMinute} (${startTimeInMinutes}-${endTimeInMinutes}), isInThisSchedule: $isInThisSchedule, isClassTime: ${schedule.isClassTime}")

            if (isInThisSchedule) {
                if (schedule.isClassTime) {
                    inClassTime = true
                    Log.d("AppPreferences", "Currently IN CLASS TIME")
                } else {
                    inRecessTime = true
                    Log.d("AppPreferences", "Currently IN RECESS TIME")
                }
            }
        }

        // Block if in class time AND not in recess time
        // Recess time takes priority (allows using apps during breaks within class hours)
        val shouldBlock = inClassTime && !inRecessTime
        Log.d("AppPreferences", "isCurrentlyBlocked: $shouldBlock (inClassTime: $inClassTime, inRecessTime: $inRecessTime)")
        return shouldBlock
    }

    // Get next unblock time (end of current class time or start of next recess)
    fun getNextUnblockTime(): String {
        val schedules = getSchedules().filter { it.enabled }
        if (schedules.isEmpty()) return ""

        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute

        // Find current class time schedule
        for (schedule in schedules.filter { it.isClassTime }) {
            if (!schedule.daysOfWeek.contains(currentDayOfWeek)) continue

            val startTimeInMinutes = schedule.startHour * 60 + schedule.startMinute
            val endTimeInMinutes = schedule.endHour * 60 + schedule.endMinute

            val isInSchedule = if (startTimeInMinutes <= endTimeInMinutes) {
                currentTimeInMinutes in startTimeInMinutes until endTimeInMinutes
            } else {
                currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes < endTimeInMinutes
            }

            if (isInSchedule) {
                // Check if there's a recess coming up before the class ends
                for (recess in schedules.filter { !it.isClassTime }) {
                    if (!recess.daysOfWeek.contains(currentDayOfWeek)) continue
                    val recessStart = recess.startHour * 60 + recess.startMinute
                    if (recessStart > currentTimeInMinutes && recessStart < endTimeInMinutes) {
                        return String.format("%02d:%02d", recess.startHour, recess.startMinute)
                    }
                }
                return String.format("%02d:%02d", schedule.endHour, schedule.endMinute)
            }
        }
        return ""
    }

    // Permission tracking (to detect if permissions are revoked)
    fun setPermissionsGranted(granted: Boolean) {
        prefs.edit().putBoolean(KEY_PERMISSIONS_GRANTED, granted).apply()
    }

    fun werePermissionsGranted(): Boolean {
        return prefs.getBoolean(KEY_PERMISSIONS_GRANTED, false)
    }

    fun setDeviceAdminGranted(granted: Boolean) {
        prefs.edit().putBoolean(KEY_DEVICE_ADMIN_GRANTED, granted).apply()
    }

    fun wasDeviceAdminGranted(): Boolean {
        return prefs.getBoolean(KEY_DEVICE_ADMIN_GRANTED, false)
    }

    fun setAccessibilityGranted(granted: Boolean) {
        prefs.edit().putBoolean(KEY_ACCESSIBILITY_GRANTED, granted).apply()
    }

    fun wasAccessibilityGranted(): Boolean {
        return prefs.getBoolean(KEY_ACCESSIBILITY_GRANTED, false)
    }

    fun setOverlayGranted(granted: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_GRANTED, granted).apply()
    }

    fun wasOverlayGranted(): Boolean {
        return prefs.getBoolean(KEY_OVERLAY_GRANTED, false)
    }

    fun setUsageStatsGranted(granted: Boolean) {
        prefs.edit().putBoolean(KEY_USAGE_STATS_GRANTED, granted).apply()
    }

    fun wasUsageStatsGranted(): Boolean {
        return prefs.getBoolean(KEY_USAGE_STATS_GRANTED, false)
    }

    // Save all permissions state
    fun saveAllPermissionsState(deviceAdmin: Boolean, accessibility: Boolean, overlay: Boolean, usageStats: Boolean) {
        prefs.edit()
            .putBoolean(KEY_DEVICE_ADMIN_GRANTED, deviceAdmin)
            .putBoolean(KEY_ACCESSIBILITY_GRANTED, accessibility)
            .putBoolean(KEY_OVERLAY_GRANTED, overlay)
            .putBoolean(KEY_USAGE_STATS_GRANTED, usageStats)
            .putBoolean(KEY_PERMISSIONS_GRANTED, deviceAdmin && accessibility && overlay && usageStats)
            .apply()
    }

    // Lockdown mode - blocks all apps except Play Store when permissions are revoked
    fun setLockdownMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCKDOWN_MODE, enabled).apply()
    }

    fun isLockdownMode(): Boolean {
        return prefs.getBoolean(KEY_LOCKDOWN_MODE, false)
    }

    fun setLockdownReason(reason: String) {
        prefs.edit().putString(KEY_LOCKDOWN_REASON, reason).apply()
    }

    fun getLockdownReason(): String {
        return prefs.getString(KEY_LOCKDOWN_REASON, "Permisos modificados") ?: "Permisos modificados"
    }

    fun clearLockdownMode() {
        prefs.edit()
            .putBoolean(KEY_LOCKDOWN_MODE, false)
            .putString(KEY_LOCKDOWN_REASON, "")
            .apply()
    }

    // Bloqueo temporal por intento de configuración peligrosa
    // El tiempo se acumula: 20 seg, 40 seg, 60 seg, etc.
    // Razón específica: "No puedes modificar los permisos de la app por reglamento escolar"
    fun setTemporaryLockdown(reason: String) {
        val now = System.currentTimeMillis()
        val lastPenaltyTime = prefs.getLong(KEY_LAST_PENALTY_TIME, 0)

        // Si han pasado más de 30 minutos desde la última penalización, reiniciar contador
        val thirtyMinutes = 30 * 60 * 1000L
        var penaltyCount = if (now - lastPenaltyTime > thirtyMinutes) {
            1
        } else {
            prefs.getInt(KEY_LOCKDOWN_PENALTY_COUNT, 0) + 1
        }

        // Calcular tiempo de bloqueo: 20 segundos * penaltyCount (máximo 5 minutos = 300 segundos)
        val lockdownSeconds = (20 * penaltyCount).coerceAtMost(300)
        val lockdownUntil = now + (lockdownSeconds * 1000L)

        prefs.edit()
            .putBoolean(KEY_LOCKDOWN_MODE, true)
            .putString(KEY_LOCKDOWN_REASON, reason)
            .putLong(KEY_LOCKDOWN_UNTIL, lockdownUntil)
            .putInt(KEY_LOCKDOWN_PENALTY_COUNT, penaltyCount)
            .putLong(KEY_LAST_PENALTY_TIME, now)
            .apply()

        Log.d("AppPreferences", "Temporary lockdown set for $lockdownSeconds seconds (penalty #$penaltyCount)")
    }

    /**
     * Bloqueo específico por intento de modificar permisos de la app
     * 20 segundos base + 20 segundos adicionales por cada intento
     */
    fun setPermissionLockdown() {
        val reason = "No puedes modificar los permisos de la app por reglamento escolar"
        setTemporaryLockdown(reason)
    }

    fun getLockdownUntil(): Long {
        return prefs.getLong(KEY_LOCKDOWN_UNTIL, 0)
    }

    fun getRemainingLockdownMinutes(): Int {
        val lockdownUntil = getLockdownUntil()
        if (lockdownUntil == 0L) return 0

        val remaining = lockdownUntil - System.currentTimeMillis()
        return if (remaining > 0) {
            (remaining / 60000).toInt() + 1 // Redondear hacia arriba
        } else {
            0
        }
    }

    fun getRemainingLockdownSeconds(): Int {
        val lockdownUntil = getLockdownUntil()
        if (lockdownUntil == 0L) return 0

        val remaining = lockdownUntil - System.currentTimeMillis()
        return if (remaining > 0) {
            (remaining / 1000).toInt()
        } else {
            0
        }
    }

    fun isTemporaryLockdownExpired(): Boolean {
        val lockdownUntil = getLockdownUntil()
        if (lockdownUntil == 0L) return true
        return System.currentTimeMillis() >= lockdownUntil
    }

    fun clearTemporaryLockdown() {
        prefs.edit()
            .putBoolean(KEY_LOCKDOWN_MODE, false)
            .putString(KEY_LOCKDOWN_REASON, "")
            .putLong(KEY_LOCKDOWN_UNTIL, 0)
            .apply()
    }

    fun getPenaltyCount(): Int {
        return prefs.getInt(KEY_LOCKDOWN_PENALTY_COUNT, 0)
    }

    // ==================== WEB SYNC ====================

    fun setWebSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEB_SYNC_ENABLED, enabled).apply()
    }

    fun isWebSyncEnabled(): Boolean {
        return prefs.getBoolean(KEY_WEB_SYNC_ENABLED, false)
    }

    fun setServerUrl(url: String) {
        securePrefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun getServerUrl(): String? {
        return securePrefs.getString(KEY_SERVER_URL, null)
    }

    fun saveDeviceId(deviceId: String) {
        securePrefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    fun getDeviceId(): String? {
        return securePrefs.getString(KEY_DEVICE_ID, null)
    }

    fun saveDeviceToken(token: String) {
        securePrefs.edit().putString(KEY_DEVICE_TOKEN, token).apply()
    }

    fun getDeviceToken(): String? {
        return securePrefs.getString(KEY_DEVICE_TOKEN, null)
    }

    fun saveParentCode(code: String) {
        securePrefs.edit().putString(KEY_PARENT_CODE, code).apply()
    }

    fun getParentCode(): String? {
        return securePrefs.getString(KEY_PARENT_CODE, null)
    }

    fun isDeviceRegistered(): Boolean {
        return !getDeviceToken().isNullOrEmpty()
    }

    fun clearWebSyncData() {
        securePrefs.edit()
            .remove(KEY_DEVICE_TOKEN)
            .remove(KEY_PARENT_CODE)
            .apply()
        prefs.edit()
            .putBoolean(KEY_WEB_SYNC_ENABLED, false)
            .apply()
    }

    // ============ Student Management ============

    fun saveStudentData(studentId: String, studentName: String, studentRut: String, schoolName: String, studentCourse: String = "") {
        securePrefs.edit()
            .putString(KEY_STUDENT_ID, studentId)
            .putString(KEY_STUDENT_NAME, studentName)
            .putString(KEY_STUDENT_RUT, studentRut)
            .putString(KEY_SCHOOL_NAME, schoolName)
            .putString(KEY_STUDENT_COURSE, studentCourse)
            .apply()
    }

    fun getStudentId(): String? = securePrefs.getString(KEY_STUDENT_ID, null)
    fun getStudentName(): String? = securePrefs.getString(KEY_STUDENT_NAME, null)
    fun getStudentRut(): String? = securePrefs.getString(KEY_STUDENT_RUT, null)
    fun getSchoolName(): String? = securePrefs.getString(KEY_SCHOOL_NAME, null)
    fun getStudentCourse(): String? = securePrefs.getString(KEY_STUDENT_COURSE, null)

    // ID numérico del alumno (para usar con la API)
    fun saveStudentNumericId(id: Int) {
        securePrefs.edit().putInt(KEY_STUDENT_NUMERIC_ID, id).apply()
    }

    fun getStudentNumericId(): Int = securePrefs.getInt(KEY_STUDENT_NUMERIC_ID, -1)

    fun saveStudentEmail(email: String) {
        securePrefs.edit().putString(KEY_STUDENT_EMAIL, email).apply()
    }

    fun getStudentEmail(): String? = securePrefs.getString(KEY_STUDENT_EMAIL, null)

    fun saveDeviceSerial(serial: String) {
        prefs.edit().putString(KEY_DEVICE_SERIAL, serial).apply()
    }

    fun getDeviceSerial(): String? = prefs.getString(KEY_DEVICE_SERIAL, null)

    fun setStudentRegistered(registered: Boolean) {
        prefs.edit().putBoolean(KEY_STUDENT_REGISTERED, registered).apply()
    }

    fun isStudentRegistered(): Boolean {
        return prefs.getBoolean(KEY_STUDENT_REGISTERED, false)
    }

    /**
     * Verifica si hay un estudiante con sesión activa
     * @return true si el estudiante tiene sesión activa (RUT guardado)
     */
    fun isStudentLoggedIn(): Boolean {
        val studentRut = getStudentRut()
        return !studentRut.isNullOrEmpty()
    }

    fun saveSchedulesFromServer(schedules: List<Map<String, Any>>) {
        val parsedSchedules = schedules.mapNotNull { s ->
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
        saveSchedules(parsedSchedules)
    }

    fun clearStudentData() {
        securePrefs.edit()
            .remove(KEY_STUDENT_ID)
            .remove(KEY_STUDENT_NAME)
            .remove(KEY_STUDENT_RUT)
            .remove(KEY_STUDENT_EMAIL)
            .remove(KEY_SCHOOL_NAME)
            .apply()
        prefs.edit()
            .remove(KEY_DEVICE_SERIAL)
            .putBoolean(KEY_STUDENT_REGISTERED, false)
            .apply()
    }
}

