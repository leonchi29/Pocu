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

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockerOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)
        blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)

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
        binding.tvEmoji.text = "🚫"
        binding.tvBlockedTitle.text = getString(R.string.app_blocked_title)
        binding.tvBlockedMessage.text = getString(R.string.app_blocked_simple)

        val unblockTime = prefs.getNextUnblockTime()
        if (unblockTime.isNotEmpty()) {
            binding.tvUnblockTime.text = getString(R.string.unblock_time_message, unblockTime)
        } else {
            binding.tvUnblockTime.text = ""
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
}
