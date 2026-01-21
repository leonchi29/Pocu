package com.example.pocu.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pocu.data.AppPreferences
import com.example.pocu.databinding.ActivityServerSetupBinding
import com.example.pocu.network.ApiClient
import com.example.pocu.network.DeviceInfo
import com.example.pocu.network.RegisterDeviceRequest
import com.example.pocu.service.SqlSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ServerSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerSetupBinding
    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPreferences(this)
        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        val serverUrl = prefs.getServerUrl()
        val isRegistered = prefs.isDeviceRegistered()
        val deviceId = prefs.getDeviceId()

        if (!serverUrl.isNullOrEmpty()) {
            binding.etServerUrl.setText(serverUrl)
        }

        if (isRegistered && !deviceId.isNullOrEmpty()) {
            binding.layoutRegistered.visibility = View.VISIBLE
            binding.layoutRegister.visibility = View.GONE
            binding.tvDeviceId.text = "ID: $deviceId"
            binding.tvParentCode.text = "Código: ${prefs.getParentCode() ?: "---"}"
            binding.tvServerUrl.text = "Servidor: ${prefs.getServerUrl() ?: "---"}"
            binding.switchSync.isChecked = prefs.isWebSyncEnabled()
        } else {
            binding.layoutRegistered.visibility = View.GONE
            binding.layoutRegister.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        binding.btnTestConnection.setOnClickListener { testConnection() }
        binding.btnRegister.setOnClickListener { registerDevice() }
        binding.btnUnregister.setOnClickListener { unregisterDevice() }

        binding.switchSync.setOnCheckedChangeListener { _, isChecked ->
            prefs.setWebSyncEnabled(isChecked)
            if (isChecked) {
                SqlSyncService.start(this)
                Toast.makeText(this, "Sincronización activada", Toast.LENGTH_SHORT).show()
            } else {
                SqlSyncService.stop(this)
                Toast.makeText(this, "Sincronización desactivada", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun testConnection() {
        val serverUrl = binding.etServerUrl.text.toString().trim()
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "Ingresa la URL del servidor", Toast.LENGTH_SHORT).show()
            return
        }

        val normalizedUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        binding.progressBar.visibility = View.VISIBLE
        binding.btnTestConnection.isEnabled = false

        lifecycleScope.launch {
            try {
                ApiClient.setBaseUrl(normalizedUrl)
                val response = withContext(Dispatchers.IO) {
                    ApiClient.getApiService().healthCheck()
                }

                binding.progressBar.visibility = View.GONE
                binding.btnTestConnection.isEnabled = true

                if (response.isSuccessful) {
                    prefs.setServerUrl(normalizedUrl)
                    binding.tvConnectionStatus.text = "✅ Conexión exitosa"
                    binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                } else {
                    binding.tvConnectionStatus.text = "❌ Error: ${response.code()}"
                    binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnTestConnection.isEnabled = true
                binding.tvConnectionStatus.text = "❌ Error: ${e.message}"
                binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            }
        }
    }

    private fun registerDevice() {
        val parentCode = binding.etParentCode.text.toString().trim().uppercase()
        if (parentCode.isEmpty()) {
            Toast.makeText(this, "Ingresa el código de vinculación", Toast.LENGTH_SHORT).show()
            return
        }

        val serverUrl = prefs.getServerUrl()
        if (serverUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Primero prueba la conexión al servidor", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnRegister.isEnabled = false

        lifecycleScope.launch {
            try {
                val deviceId = prefs.getDeviceId() ?: UUID.randomUUID().toString().also { prefs.saveDeviceId(it) }

                val request = RegisterDeviceRequest(
                    deviceId = deviceId,
                    deviceInfo = DeviceInfo(
                        deviceId = deviceId,
                        deviceName = Build.DEVICE,
                        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                        androidVersion = Build.VERSION.RELEASE,
                        appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
                    ),
                    parentCode = parentCode
                )

                val response = withContext(Dispatchers.IO) {
                    ApiClient.getApiService().registerDevice(request)
                }

                binding.progressBar.visibility = View.GONE
                binding.btnRegister.isEnabled = true

                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.deviceToken?.let { token ->
                        prefs.saveDeviceToken(token)
                        prefs.saveParentCode(parentCode)
                        prefs.setWebSyncEnabled(true)
                        SqlSyncService.start(this@ServerSetupActivity)
                        Toast.makeText(this@ServerSetupActivity, "¡Dispositivo registrado!", Toast.LENGTH_LONG).show()
                        setupUI()
                    }
                } else {
                    Toast.makeText(this@ServerSetupActivity, "Error: ${response.body()?.message ?: "Desconocido"}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnRegister.isEnabled = true
                Toast.makeText(this@ServerSetupActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun unregisterDevice() {
        AlertDialog.Builder(this)
            .setTitle("Desvincular dispositivo")
            .setMessage("¿Estás seguro?")
            .setPositiveButton("Desvincular") { _, _ ->
                SqlSyncService.stop(this)
                prefs.clearWebSyncData()
                Toast.makeText(this, "Dispositivo desvinculado", Toast.LENGTH_SHORT).show()
                setupUI()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

