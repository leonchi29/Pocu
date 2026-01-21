package com.example.pocu.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.pocu.data.AppPreferences
import com.example.pocu.ui.BlockerOverlayActivity

class AppDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "AppDeviceAdminReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        val prefs = AppPreferences(context)
        prefs.setPermissionsGranted(true)
        prefs.setDeviceAdminGranted(true)
        // Si estábamos en lockdown y ahora tenemos admin, verificar si podemos salir
        if (prefs.isLockdownMode()) {
            checkAndDisableLockdown(context, prefs)
        }
        Toast.makeText(context, "✅ Protección activada - La app no puede ser desinstalada", Toast.LENGTH_LONG).show()
        Log.d(TAG, "Device Admin enabled - protection active")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "!!! Device Admin DISABLED - activating lockdown mode !!!")

        val prefs = AppPreferences(context)
        if (prefs.isServiceEnabled() && prefs.werePermissionsGranted()) {
            // Activar modo lockdown - bloquear todas las apps excepto Play Store
            prefs.setLockdownMode(true)
            prefs.setLockdownReason("Device Admin desactivado")
            Toast.makeText(context, "🔒 MODO BLOQUEO ACTIVADO - Reinstala Pocu para desbloquear", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "⚠️ Protección desactivada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "!!! Disable requested - warning user !!!")

        val prefs = AppPreferences(context)

        // Si el servicio está habilitado, activar lockdown
        if (prefs.isServiceEnabled() && prefs.werePermissionsGranted()) {
            Log.w(TAG, "Service enabled - will activate lockdown if disabled")
            prefs.setLockdownMode(true)
            prefs.setLockdownReason("Intento de desactivar Device Admin")
        }

        // Launch blocker overlay
        val blockerIntent = Intent(context, BlockerOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(BlockerOverlayActivity.EXTRA_BLOCKED_PACKAGE, "com.android.settings")
        }
        context.startActivity(blockerIntent)

        return "🚨 ADVERTENCIA 🚨\n\n" +
               "Si desactivas el administrador, TODAS las apps serán bloqueadas.\n\n" +
               "Solo podrás usar Play Store para reinstalar Pocu.\n\n" +
               "¿Continuar de todos modos?"
    }

    @Suppress("UNUSED_PARAMETER")
    private fun checkAndDisableLockdown(context: Context, prefs: AppPreferences) {
        // Verificar si todos los permisos están restaurados
        // Por ahora solo verificamos Device Admin aquí
        // El PermissionMonitorService verificará los demás
        Log.d(TAG, "Device Admin restored, checking other permissions...")
    }
}

