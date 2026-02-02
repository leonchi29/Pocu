package com.example.pocu.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.pocu.data.AppPreferences

class AppDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "AppDeviceAdminReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device Admin enabled")

        try {
            val prefs = AppPreferences(context)
            prefs.setPermissionsGranted(true)
            prefs.setDeviceAdminGranted(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving prefs: ${e.message}", e)
        }
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
        return "⚠️ Si desactivas esto, la app podrá ser desinstalada."
    }

    @Suppress("UNUSED_PARAMETER")
    private fun checkAndDisableLockdown(context: Context, prefs: AppPreferences) {
        // Verificar si todos los permisos están restaurados
        // Por ahora solo verificamos Device Admin aquí
        // El PermissionMonitorService verificará los demás
        Log.d(TAG, "Device Admin restored, checking other permissions...")
    }
}

