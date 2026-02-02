 package com.example.pocu.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.pocu.data.AppPreferences
import com.example.pocu.databinding.ActivityLockdownOverlayBinding

class LockdownOverlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockdownOverlayBinding
    private lateinit var prefs: AppPreferences

    private var blockedPackage: String? = null

    // Handler para actualizar el contador de tiempo restante
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateTimeRemaining()
            handler.postDelayed(this, 1000) // Actualizar cada segundo
        }
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLockdownOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)
        blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)

        setupUI()
        setupBackPressHandler()
        setupButtons()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Redirect to home instead of allowing back
                goHome()
            }
        })
    }

    private fun setupUI() {


        val isTemporaryLockdown = prefs.getLockdownUntil() > 0

        if (isTemporaryLockdown) {
            // Bloqueo temporal por intento de modificar permisos o desinstalar
            binding.tvEmoji.text = "🚫"
            binding.tvBlockedTitle.text = "🚫 Dispositivo Bloqueado"

            val penaltyCount = prefs.getPenaltyCount()
            val remainingSeconds = prefs.getRemainingLockdownSeconds()
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60

            binding.tvBlockedMessage.text = "No puedes modificar los permisos de la aplicación por reglamento escolar.\n\nIntento #$penaltyCount"

            binding.tvUnblockInfo.text = "Tiempo restante: ${String.format("%02d:%02d", minutes, seconds)}"

            // Ocultar botones de Play Store y Ajustes - solo mostrar "Voy a esperar"
            binding.btnOpenPlayStore.visibility = android.view.View.GONE
            binding.btnOpenSettings.visibility = android.view.View.GONE
            binding.btnGoHome.text = "Voy a esperar"

            // Iniciar actualización del contador
            handler.post(updateRunnable)
        } else {
            // Bloqueo permanente (permisos revocados - no debería pasar normalmente)
            binding.tvEmoji.text = "🚫"
            binding.tvBlockedTitle.text = "🚫 Dispositivo Bloqueado"

            binding.tvBlockedMessage.text = "Los permisos de Pocu fueron modificados.\n\nContacta a tu establecimiento para resolver este problema."

            binding.tvUnblockInfo.text = ""

            // Ocultar todos los botones excepto ir al inicio
            binding.btnOpenPlayStore.visibility = android.view.View.GONE
            binding.btnOpenSettings.visibility = android.view.View.GONE
            binding.btnGoHome.text = "Ir al inicio"
        }
    }

    private fun updateTimeRemaining() {
        if (prefs.isTemporaryLockdownExpired()) {
            // El tiempo expiró, cerrar overlay
            prefs.clearTemporaryLockdown()
            handler.removeCallbacks(updateRunnable)
            finish()
            return
        }

        val remainingSeconds = prefs.getRemainingLockdownSeconds()
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        binding.tvUnblockInfo.text = "Tiempo restante: ${String.format("%02d:%02d", minutes, seconds)}"
    }

    private fun setupButtons() {
        binding.btnOpenPlayStore.setOnClickListener {
            openPlayStore()
        }

        binding.btnOpenSettings.setOnClickListener {
            openSettings()
        }

        binding.btnGoHome.setOnClickListener {
            goHome()
        }
    }

    private fun openPlayStore() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.android.vending")
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
        } catch (e: Exception) {
            // If can't open Play Store, just go home
            goHome()
        }
    }

    private fun openSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            // If can't open Settings, just go home
            goHome()
        }
    }

    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            blockedPackage = it.getStringExtra(EXTRA_BLOCKED_PACKAGE)
            setupUI()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if lockdown mode was disabled or expired
        if (!prefs.isLockdownMode() || prefs.isTemporaryLockdownExpired()) {
            prefs.clearTemporaryLockdown()
            finish()
            return
        }

        // Reiniciar el handler si es bloqueo temporal
        if (prefs.getLockdownUntil() > 0) {
            handler.post(updateRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }
}

