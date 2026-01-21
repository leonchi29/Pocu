package com.example.pocu.ui

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.example.pocu.R
import com.example.pocu.admin.AppDeviceAdminReceiver
import com.example.pocu.data.AppPreferences
import com.example.pocu.data.Schedule
import com.example.pocu.databinding.ActivityMainBinding
import com.example.pocu.service.AppBlockerAccessibilityService
import com.example.pocu.service.AppBlockerService
import com.example.pocu.service.PermissionMonitorService
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    // Handler para actualizar el reloj
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClockAndStatus()
            clockHandler.postDelayed(this, 1000) // Actualizar cada segundo
        }
    }

    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AppDeviceAdminReceiver::class.java)

        // Configurar toolbar con menú
        setupToolbar()

        // Check if first run - require PIN setup
        if (prefs.isFirstRun() || !prefs.hasPin()) {
            startActivity(Intent(this, PinActivity::class.java).apply {
                putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_CREATE)
            })
        }

        setupClickListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_theme -> {
                    showThemeDialog()
                    true
                }
                R.id.action_about -> {
                    showAboutDialog()
                    true
                }
                R.id.action_logout -> {
                    showLogoutDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun showThemeDialog() {
        val themes = arrayOf("🌞 Claro", "🌙 Oscuro", "📱 Sistema")
        val currentTheme = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> 0
            AppCompatDelegate.MODE_NIGHT_YES -> 1
            else -> 2
        }

        AlertDialog.Builder(this)
            .setTitle("Seleccionar tema")
            .setSingleChoiceItems(themes, currentTheme) { dialog, which ->
                when (which) {
                    0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAboutDialog() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.0"
        }

        AlertDialog.Builder(this)
            .setTitle("Acerca de Pocu")
            .setMessage("Versión: $version\n\nControl parental para gestionar el uso del dispositivo durante horarios no autorizados.\n\n© 2026 Pocu")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLogoutDialog() {
        // Pedir PIN antes de cerrar sesión
        startActivity(Intent(this, PinActivity::class.java).apply {
            putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_VERIFY)
            putExtra(PinActivity.EXTRA_ACTION, ACTION_LOGOUT)
        })
    }

    override fun onResume() {
        super.onResume()
        // Check if we can disable lockdown mode (all permissions restored)
        checkAndDisableLockdown()
        updateUI()
        setupStudentCard()

        // Iniciar reloj
        clockHandler.post(clockRunnable)
    }

    override fun onPause() {
        super.onPause()
        // Detener reloj para ahorrar batería
        clockHandler.removeCallbacks(clockRunnable)
    }

    private fun setupStudentCard() {
        val studentName = prefs.getStudentName()
        val schoolName = prefs.getSchoolName()
        val studentCourse = prefs.getStudentCourse()

        Log.d("MainActivity", "setupStudentCard - Nombre: '$studentName', Colegio: '$schoolName', Curso: '$studentCourse'")

        if (!studentName.isNullOrEmpty()) {
            binding.tvStudentName.text = "Hola, $studentName"
            binding.cardStudent.visibility = android.view.View.VISIBLE
        } else {
            binding.tvStudentName.text = "Hola, Estudiante"
        }

        if (!schoolName.isNullOrEmpty()) {
            binding.tvSchoolName.text = schoolName
            binding.tvSchoolName.visibility = android.view.View.VISIBLE
        } else {
            binding.tvSchoolName.visibility = android.view.View.GONE
        }

        // Mostrar curso del alumno
        if (!studentCourse.isNullOrEmpty()) {
            Log.d("MainActivity", "Mostrando curso: $studentCourse")
            binding.tvStudentCourse.text = "Curso: $studentCourse"
        } else {
            Log.d("MainActivity", "Curso vacío o null")
            binding.tvStudentCourse.text = "Curso: -"
        }

        // Botón para ver horarios
        binding.btnViewSchedules.setOnClickListener {
            showMySchedulesDialog()
        }
    }

    private fun showMySchedulesDialog() {
        val schedules = prefs.getSchedules()

        if (schedules.isEmpty()) {
            Toast.makeText(this, "No hay horarios configurados", Toast.LENGTH_SHORT).show()
            return
        }

        // Ordenar por hora de inicio
        val sortedSchedules = schedules.sortedBy { it.startHour * 60 + it.startMinute }

        val message = StringBuilder()
        message.append("Horarios de Lunes a Viernes:\n\n")

        sortedSchedules.forEach { schedule ->
            val startTime = String.format("%02d:%02d", schedule.startHour, schedule.startMinute)
            val endTime = String.format("%02d:%02d", schedule.endHour, schedule.endMinute)
            val icon = if (schedule.isClassTime) "🔒" else "✅"
            val status = if (schedule.isClassTime) "(Bloqueado)" else "(Permitido)"
            message.append("$icon ${schedule.name}\n")
            message.append("    $startTime - $endTime $status\n\n")
        }

        AlertDialog.Builder(this)
            .setTitle("📅 Mis Horarios")
            .setMessage(message.toString())
            .setPositiveButton("OK", null)
            .setNeutralButton("✏️ Editar nombres") { _, _ ->
                showEditScheduleNamesDialog()
            }
            .show()
    }

    private fun showEditScheduleNamesDialog() {
        val schedules = prefs.getSchedules()
        val sortedSchedules = schedules.sortedBy { it.startHour * 60 + it.startMinute }

        // Solo mostrar horarios de clase (bloqueados) para editar
        val classSchedules = sortedSchedules.filter { it.isClassTime }

        if (classSchedules.isEmpty()) {
            Toast.makeText(this, "No hay horarios de clase para editar", Toast.LENGTH_SHORT).show()
            return
        }

        val scheduleNames = classSchedules.map { schedule ->
            val startTime = String.format("%02d:%02d", schedule.startHour, schedule.startMinute)
            "${schedule.name} ($startTime)"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Selecciona un horario para editar")
            .setItems(scheduleNames) { _, which ->
                showRenameScheduleDialog(classSchedules[which])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showRenameScheduleDialog(schedule: Schedule) {
        val input = android.widget.EditText(this)
        input.setText(schedule.name)
        input.setSelection(input.text.length)
        input.hint = "Ej: Matemáticas, Lenguaje, etc."

        val container = android.widget.FrameLayout(this)
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(50, 20, 50, 20)
        input.layoutParams = params
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Renombrar horario")
            .setMessage("Ingresa el nuevo nombre para este horario:")
            .setView(container)
            .setPositiveButton("Guardar") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    renameSchedule(schedule.id, newName)
                    Toast.makeText(this, "Horario renombrado a: $newName", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun renameSchedule(scheduleId: Long, newName: String) {
        val schedules = prefs.getSchedules().toMutableList()
        val index = schedules.indexOfFirst { it.id == scheduleId }

        if (index != -1) {
            val oldSchedule = schedules[index]
            val updatedSchedule = Schedule(
                id = oldSchedule.id,
                name = newName,
                startHour = oldSchedule.startHour,
                startMinute = oldSchedule.startMinute,
                endHour = oldSchedule.endHour,
                endMinute = oldSchedule.endMinute,
                daysOfWeek = oldSchedule.daysOfWeek,
                enabled = oldSchedule.enabled,
                isClassTime = oldSchedule.isClassTime
            )
            schedules[index] = updatedSchedule
            prefs.saveSchedules(schedules)
        }
    }

    private fun checkAndDisableLockdown() {
        if (prefs.isLockdownMode()) {
            val hasDeviceAdmin = devicePolicyManager.isAdminActive(adminComponent)
            val hasAccessibility = isAccessibilityServiceEnabled()
            val hasOverlay = hasOverlayPermission()
            val hasUsageStats = hasUsageStatsPermission()

            val wasDeviceAdmin = prefs.wasDeviceAdminGranted()
            val wasAccessibility = prefs.wasAccessibilityGranted()
            val wasOverlay = prefs.wasOverlayGranted()
            val wasUsageStats = prefs.wasUsageStatsGranted()

            // Check if all previously granted permissions are restored
            val adminOk = !wasDeviceAdmin || hasDeviceAdmin
            val accessibilityOk = !wasAccessibility || hasAccessibility
            val overlayOk = !wasOverlay || hasOverlay
            val usageOk = !wasUsageStats || hasUsageStats

            if (adminOk && accessibilityOk && overlayOk && usageOk) {
                prefs.clearLockdownMode()
                // Update saved permissions state
                prefs.saveAllPermissionsState(hasDeviceAdmin, hasAccessibility, hasOverlay, hasUsageStats)
                Toast.makeText(this, "✅ Permisos restaurados - Dispositivo desbloqueado", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupClickListeners() {
        // Service toggle removed - service is always active

        // Permission buttons
        binding.btnUsageAccess.setOnClickListener {
            showPermissionDialog(
                title = getString(R.string.permission_usage_title),
                message = getString(R.string.permission_usage_message),
                onConfirm = {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            )
        }

        binding.btnOverlayPermission.setOnClickListener {
            showPermissionDialog(
                title = getString(R.string.permission_overlay_title),
                message = getString(R.string.permission_overlay_message),
                onConfirm = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        startActivity(Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        ))
                    }
                }
            )
        }

        binding.btnAccessibility.setOnClickListener {
            showPermissionDialog(
                title = getString(R.string.permission_accessibility_title),
                message = getString(R.string.permission_accessibility_message),
                onConfirm = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
        }

        binding.btnDeviceAdmin.setOnClickListener {
            if (!devicePolicyManager.isAdminActive(adminComponent)) {
                showPermissionDialog(
                    title = getString(R.string.permission_admin_title),
                    message = getString(R.string.permission_admin_message),
                    onConfirm = {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                            putExtra(
                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                getString(R.string.device_admin_description)
                            )
                        }
                        deviceAdminLauncher.launch(intent)
                    }
                )
            }
        }

        // Settings buttons removed - student mode doesn't need configuration
    }

    private fun updateUI() {
        updatePermissionStatus()
    }

    // ...existing code...

    private fun updatePermissionStatus() {
        val hasUsageAccess = hasUsageStatsPermission()
        val hasOverlay = hasOverlayPermission()
        val hasAccessibility = isAccessibilityServiceEnabled()
        val hasDeviceAdmin = devicePolicyManager.isAdminActive(adminComponent)

        updatePermissionButton(binding.btnUsageAccess, hasUsageAccess)
        updatePermissionButton(binding.btnOverlayPermission, hasOverlay)
        updatePermissionButton(binding.btnAccessibility, hasAccessibility)
        updatePermissionButton(binding.btnDeviceAdmin, hasDeviceAdmin)

        // Hide permissions card if all granted
        val allGranted = hasUsageAccess && hasOverlay && hasAccessibility && hasDeviceAdmin
        binding.cardPermissions.visibility = if (allGranted) View.GONE else View.VISIBLE

        // Save permission state when all permissions are granted
        // This is used to detect if permissions are revoked later
        if (allGranted) {
            prefs.saveAllPermissionsState(
                deviceAdmin = hasDeviceAdmin,
                accessibility = hasAccessibility,
                overlay = hasOverlay,
                usageStats = hasUsageAccess
            )

            // Start permission monitor service if service is enabled
            if (prefs.isServiceEnabled()) {
                PermissionMonitorService.start(this)
            }
        }
    }

    private fun updatePermissionButton(button: View, granted: Boolean) {
        button.alpha = if (granted) 0.5f else 1f
        button.isEnabled = !granted
    }

    private fun hasAllPermissions(): Boolean {
        return hasUsageStatsPermission() &&
               hasOverlayPermission() &&
               isAccessibilityServiceEnabled()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
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

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/${AppBlockerAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(service)
    }

    private fun showPermissionDialog(title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.btn_grant_permission)) { dialog, _ ->
                dialog.dismiss()
                onConfirm()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setIcon(R.drawable.ic_permission)
            .show()
    }

    /**
     * 🕐 Actualizar reloj y estado (clase o recreo)
     * Se ejecuta cada segundo para mostrar hora en tiempo real
     */
    private fun updateClockAndStatus() {
        val schedules = prefs.getSchedules()
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentSecond = calendar.get(Calendar.SECOND)

        // Formato: HH:MM:SS
        val timeFormat = String.format("%02d:%02d:%02d", currentHour, currentMinute, currentSecond)

        // Buscar si estamos en clase o recreo
        val isInClass = schedules.any { schedule ->
            val currentTimeInMinutes = currentHour * 60 + currentMinute
            val startTimeInMinutes = schedule.startHour * 60 + schedule.startMinute
            val endTimeInMinutes = schedule.endHour * 60 + schedule.endMinute

            schedule.isClassTime &&
            currentTimeInMinutes >= startTimeInMinutes &&
            currentTimeInMinutes < endTimeInMinutes
        }

        val isInRecess = schedules.any { schedule ->
            val currentTimeInMinutes = currentHour * 60 + currentMinute
            val startTimeInMinutes = schedule.startHour * 60 + schedule.startMinute
            val endTimeInMinutes = schedule.endHour * 60 + schedule.endMinute

            !schedule.isClassTime &&
            currentTimeInMinutes >= startTimeInMinutes &&
            currentTimeInMinutes < endTimeInMinutes
        }

        // Actualizar UI - Reloj SIEMPRE VISIBLE
        binding.tvClock.visibility = View.VISIBLE
        binding.tvClock.text = "🕐 $timeFormat"

        // Actualizar estado según si está en clase o recreo
        if (isInRecess) {
            // En recreo
            binding.tvCurrentStatus.text = "En recreo - ¡Puedes descansar! ✅"
        } else if (isInClass) {
            // En clase
            binding.tvCurrentStatus.text = "En clase - Aplicación bloqueada 🔒"
        } else {
            // Fuera de horario
            binding.tvCurrentStatus.text = "Fuera de horario - Aplicación permitida ✅"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Detener el reloj
        clockHandler.removeCallbacks(clockRunnable)
    }

    companion object {
        const val ACTION_DISABLE_SERVICE = "disable_service"
        const val ACTION_LOGOUT = "logout"
    }
}

