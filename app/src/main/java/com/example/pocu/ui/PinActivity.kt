package com.example.pocu.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.pocu.R
import com.example.pocu.data.AppPreferences
import com.example.pocu.databinding.ActivityPinBinding
import com.example.pocu.service.AppBlockerService

class PinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinBinding
    private lateinit var prefs: AppPreferences

    private var mode: Int = MODE_CREATE
    private var targetActivity: String? = null
    private var action: String? = null
    private var isVerified = false

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_TARGET_ACTIVITY = "target_activity"
        const val EXTRA_ACTION = "action"

        const val MODE_CREATE = 0
        const val MODE_VERIFY = 1
        const val MODE_CHANGE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)

        mode = intent.getIntExtra(EXTRA_MODE, MODE_CREATE)
        targetActivity = intent.getStringExtra(EXTRA_TARGET_ACTIVITY)
        action = intent.getStringExtra(EXTRA_ACTION)

        setupUI()
        setupClickListeners()
        setupBackPressHandler()
    }

    private fun setupUI() {
        when (mode) {
            MODE_CREATE -> {
                binding.tvPinTitle.visibility = View.VISIBLE
                binding.tvPinTitle.text = getString(R.string.create_pin)
                binding.tilConfirmPin.visibility = View.VISIBLE
                binding.btnCancel.visibility = View.GONE
            }
            MODE_VERIFY -> {
                binding.tvPinTitle.visibility = View.VISIBLE
                binding.tvPinTitle.text = getString(R.string.enter_pin)
                binding.tilConfirmPin.visibility = View.GONE
            }
            MODE_CHANGE -> {
                if (!isVerified) {
                    binding.tvPinTitle.visibility = View.VISIBLE
                    binding.tvPinTitle.text = getString(R.string.enter_pin)
                    binding.tilConfirmPin.visibility = View.GONE
                } else {
                    binding.tvPinTitle.visibility = View.VISIBLE
                    binding.tvPinTitle.text = getString(R.string.create_pin)
                    binding.tilConfirmPin.visibility = View.VISIBLE
                    binding.etPin.text?.clear()
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSubmit.setOnClickListener {
            handleSubmit()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun handleSubmit() {
        val pin = binding.etPin.text.toString()

        if (pin.length < 4) {
            showError(getString(R.string.pin_too_short))
            return
        }

        when (mode) {
            MODE_CREATE -> {
                val confirmPin = binding.etConfirmPin.text.toString()
                if (pin != confirmPin) {
                    showError(getString(R.string.pin_mismatch))
                    return
                }
                prefs.savePin(pin)
                prefs.setFirstRunComplete()
                Toast.makeText(this, getString(R.string.pin_created), Toast.LENGTH_SHORT).show()
                finish()
            }
            MODE_VERIFY -> {
                if (prefs.verifyPin(pin)) {
                    handleVerificationSuccess()
                } else {
                    showError(getString(R.string.pin_incorrect))
                }
            }
            MODE_CHANGE -> {
                if (!isVerified) {
                    if (prefs.verifyPin(pin)) {
                        isVerified = true
                        hideError()
                        setupUI()
                    } else {
                        showError(getString(R.string.pin_incorrect))
                    }
                } else {
                    val confirmPin = binding.etConfirmPin.text.toString()
                    if (pin != confirmPin) {
                        showError(getString(R.string.pin_mismatch))
                        return
                    }
                    prefs.savePin(pin)
                    Toast.makeText(this, getString(R.string.pin_changed), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun handleVerificationSuccess() {
        when {
            action == MainActivity.ACTION_DISABLE_SERVICE -> {
                prefs.setServiceEnabled(false)
                AppBlockerService.stop(this)
                finish()
            }
            action == MainActivity.ACTION_LOGOUT -> {
                // Limpiar datos de estudiante y cerrar sesión
                prefs.clearStudentData()
                prefs.clearWebSyncData()

                // Ir a pantalla de login
                startActivity(Intent(this, StudentLoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            targetActivity != null -> {
                try {
                    val clazz = Class.forName(targetActivity!!)
                    startActivity(Intent(this, clazz))
                    finish()
                } catch (e: ClassNotFoundException) {
                    finish()
                }
            }
            else -> {
                finish()
            }
        }
    }

    private fun showError(message: String) {
        binding.tvError.visibility = View.VISIBLE
        binding.tvError.text = message
    }

    private fun hideError() {
        binding.tvError.visibility = View.GONE
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Prevent going back without PIN in create mode
                if (mode == MODE_CREATE && !prefs.hasPin()) {
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }
}

