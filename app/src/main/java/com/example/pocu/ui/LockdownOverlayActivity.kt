package com.example.pocu.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.pocu.data.AppPreferences
import com.example.pocu.databinding.ActivityLockdownOverlayBinding

class LockdownOverlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockdownOverlayBinding
    private lateinit var prefs: AppPreferences

    private var blockedPackage: String? = null

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
        binding.tvEmoji.text = "🔒"
        binding.tvBlockedTitle.text = "DISPOSITIVO BLOQUEADO"

        val reason = prefs.getLockdownReason()
        binding.tvBlockedMessage.text = "Los permisos de Pocu fueron modificados o la app fue desinstalada.\n\n" +
                "Razón: $reason\n\n" +
                "Para desbloquear tu dispositivo:\n" +
                "1. Abre Play Store y reinstala Pocu\n" +
                "2. O abre Ajustes y restaura los permisos\n" +
                "3. Asegúrate de conceder TODOS los permisos"

        binding.tvUnblockInfo.text = "Solo puedes usar Play Store y Ajustes hasta que restaures los permisos"
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
        // Check if lockdown mode was disabled
        if (!prefs.isLockdownMode()) {
            finish()
        }
    }
}

