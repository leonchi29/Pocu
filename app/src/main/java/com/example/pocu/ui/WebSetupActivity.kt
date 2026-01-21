package com.example.pocu.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pocu.R
import com.example.pocu.data.AppPreferences
import com.example.pocu.databinding.ActivityWebSetupBinding
import com.example.pocu.network.*
import com.example.pocu.service.WebSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Activity para configurar la conexión con el servidor web
 */
class WebSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebSetupBinding
    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)

        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        // Mostrar estado actual
        val serverUrl = prefs.getServerUrl()
        val isRegistered = prefs.isDeviceRegistered()
        val isEnabled = prefs.isWebSyncEnabled()

        if (!serverUrl.isNullOrEmpty()) {
            binding.etServerUrl.setText(serverUrl)
        }

        if (isRegistered) {
            binding.layoutRegistered.visibility = View.VISIBLE
            binding.layoutRegister.visibility = View.GONE
            binding.tvDeviceId.text = "ID: ${prefs.getDeviceId()}"
            binding.switchSync.isChecked = isEnabled
        } else {
            binding.layoutRegistered.visibility = View.GONE
            binding.layoutRegister.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        binding.btnRegister.setOnClickListener {
            registerDevice()
        }

        binding.btnUnregister.setOnClickListener {
            unregisterDevice()
        }

        binding.switchSync.setOnCheckedChangeListener { _, isChecked ->
            prefs.setWebSyncEnabled(isChecked)
            if (isChecked) {
                WebSyncService.start(this)
                Toast.makeText(this, "Sincronización activada", Toast.LENGTH_SHORT).show()
            } else {
                WebSyncService.stop(this)
                Toast.makeText(this, "Sincronización desactivada", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun testConnection() {
        val serverUrl = binding.etServerUrl.text.toString().trim()
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "Ingresa la URL del servidor", Toast.LENGTH_SHORT).show()
            return
        }

        // Asegurar que termine con /
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
                    Toast.makeText(this@WebSetupActivity, "¡Conexión exitosa!", Toast.LENGTH_SHORT).show()
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
        val parentCode = binding.etParentCode.text.toString().trim()
        if (parentCode.isEmpty()) {
            Toast.makeText(this, "Ingresa el código de vinculación", Toast.LENGTH_SHORT).show()
            return
        }

        val serverUrl = prefs.getServerUrl()
        if (serverUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Primero configura y prueba la conexión al servidor", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnRegister.isEnabled = false

        lifecycleScope.launch {
            try {
                val deviceId = prefs.getDeviceId() ?: UUID.randomUUID().toString().also {
                    prefs.saveDeviceId(it)
                }

                val deviceInfo = DeviceInfo(
                    deviceId = deviceId,
                    deviceName = Build.DEVICE,
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    androidVersion = Build.VERSION.RELEASE,
                    appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
                )

                val request = RegisterDeviceRequest(
                    deviceId = deviceId,
                    deviceInfo = deviceInfo,
                    parentCode = parentCode
                )

                val response = withContext(Dispatchers.IO) {
                    ApiClient.getApiService().registerDevice(request)
                }

                binding.progressBar.visibility = View.GONE
                binding.btnRegister.isEnabled = true

                if (response.isSuccessful && response.body()?.success == true) {
                    val deviceToken = response.body()?.deviceToken
                    if (!deviceToken.isNullOrEmpty()) {
                        prefs.saveDeviceToken(deviceToken)
                        prefs.saveParentCode(parentCode)
                        prefs.setWebSyncEnabled(true)

                        // Iniciar servicio de sincronización
                        WebSyncService.start(this@WebSetupActivity)

                        Toast.makeText(this@WebSetupActivity, "¡Dispositivo registrado!", Toast.LENGTH_LONG).show()
                        setupUI() // Actualizar UI
                    }
                } else {
                    val errorMsg = response.body()?.message ?: "Error desconocido"
                    Toast.makeText(this@WebSetupActivity, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnRegister.isEnabled = true
                Toast.makeText(this@WebSetupActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun unregisterDevice() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Desvincular dispositivo")
            .setMessage("¿Estás seguro? El dispositivo dejará de sincronizarse con el servidor.")
            .setPositiveButton("Desvincular") { _, _ ->
                WebSyncService.stop(this)
                prefs.clearWebSyncData()
                Toast.makeText(this, "Dispositivo desvinculado", Toast.LENGTH_SHORT).show()
                setupUI()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

