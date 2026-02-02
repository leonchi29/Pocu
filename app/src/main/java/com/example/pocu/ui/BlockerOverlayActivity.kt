package com.example.pocu.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.pocu.R
import com.example.pocu.data.AppPreferences
import com.example.pocu.databinding.ActivityBlockerOverlayBinding

class BlockerOverlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockerOverlayBinding
    private lateinit var prefs: AppPreferences

    private var blockedPackage: String? = null
    private var isPermissionBlock: Boolean = false

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
        const val EXTRA_IS_PERMISSION_BLOCK = "is_permission_block"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockerOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)
        blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)
        isPermissionBlock = intent.getBooleanExtra(EXTRA_IS_PERMISSION_BLOCK, false)

        setupUI()
        setupBackPressHandler()
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
        if (isPermissionBlock) {
            // Bloqueo por intento de modificar permisos o desinstalar la app
            binding.tvEmoji.text = "🚫"
            binding.tvBlockedTitle.text = "🚫 Acceso Restringido"
            binding.tvBlockedMessage.text = "No puedes modificar los permisos de la aplicación por reglamento escolar."
            binding.tvUnblockTime.text = ""
        } else {
            // Bloqueo por horario de clase
            binding.tvEmoji.text = "🚫"
            binding.tvBlockedTitle.text = "🚫 App Bloqueada"
            binding.tvBlockedMessage.text = "No puedes utilizar esta app.\n\nEstás en horario de clases."

            val unblockTime = prefs.getNextUnblockTime()
            if (unblockTime.isNotEmpty()) {
                binding.tvUnblockTime.text = "Se desbloqueará a las $unblockTime"
            } else {
                binding.tvUnblockTime.text = ""
            }
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
            isPermissionBlock = it.getBooleanExtra(EXTRA_IS_PERMISSION_BLOCK, false)
            setupUI()
        }
    }
}
