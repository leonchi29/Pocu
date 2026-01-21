package com.example.pocu.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pocu.data.AppPreferences
import com.example.pocu.databinding.ActivityStudentRegisterDeviceBinding
import com.example.pocu.network.ApiClient
import com.example.pocu.network.DeviceRegistrationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Pantalla para registrar el dispositivo del alumno.
 * Después de encontrar al alumno, se le pide el serial y correo.
 */
class StudentRegisterDeviceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentRegisterDeviceBinding
    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentRegisterDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)

        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        val studentName = prefs.getStudentName() ?: "Estudiante"
        val schoolName = prefs.getSchoolName() ?: ""

        binding.tvWelcome.text = "¡Hola, $studentName! 👋"

        if (schoolName.isNotEmpty()) {
            binding.tvSchool.text = "Colegio: $schoolName"
            binding.tvSchool.visibility = View.VISIBLE
        } else {
            binding.tvSchool.visibility = View.GONE
        }

        // Pre-llenar el serial del dispositivo si es posible
        val deviceSerial = getDeviceSerial()
        if (deviceSerial.isNotEmpty()) {
            binding.etDeviceSerial.setText(deviceSerial)
        }

        // Pre-llenar modelo del dispositivo
        binding.tvDeviceModel.text = "Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}"
    }

    private fun setupClickListeners() {
        binding.btnRegisterDevice.setOnClickListener {
            registerDevice()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun registerDevice() {
        val email = binding.etEmail.text.toString().trim()
        val deviceSerial = binding.etDeviceSerial.text.toString().trim()

        // Validaciones
        if (email.isEmpty()) {
            binding.tilEmail.error = "Ingresa tu correo"
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Correo inválido"
            return
        }

        if (deviceSerial.isEmpty()) {
            binding.tilDeviceSerial.error = "Ingresa el serial del dispositivo"
            return
        }

        binding.tilEmail.error = null
        binding.tilDeviceSerial.error = null

        binding.progressBar.visibility = View.VISIBLE
        binding.btnRegisterDevice.isEnabled = false

        lifecycleScope.launch {
            try {
                val deviceId = prefs.getDeviceId() ?: UUID.randomUUID().toString().also {
                    prefs.saveDeviceId(it)
                }

                val request = DeviceRegistrationRequest(
                    studentId = prefs.getStudentId() ?: "",
                    studentRut = prefs.getStudentRut() ?: "",
                    deviceId = deviceId,
                    deviceSerial = deviceSerial,
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    deviceName = Build.DEVICE,
                    androidVersion = Build.VERSION.RELEASE,
                    email = email,
                    appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
                )

                val response = withContext(Dispatchers.IO) {
                    ApiClient.getApiService().registerStudentDevice(request)
                }

                binding.progressBar.visibility = View.GONE
                binding.btnRegisterDevice.isEnabled = true

                if (response.isSuccessful && response.body()?.success == true) {
                    val result = response.body()!!

                    // Guardar token y datos
                    result.deviceToken?.let { prefs.saveDeviceToken(it) }
                    prefs.saveStudentEmail(email)
                    prefs.saveDeviceSerial(deviceSerial)
                    prefs.setStudentRegistered(true)

                    // Guardar horarios si vienen en la respuesta
                    result.schedules?.let { schedules ->
                        prefs.saveSchedulesFromServer(schedules)
                    }

                    // Guardar apps permitidas si vienen
                    result.allowedApps?.let { apps ->
                        prefs.saveAllowedApps(apps.toSet())
                    }

                    Toast.makeText(
                        this@StudentRegisterDeviceActivity,
                        "¡Dispositivo registrado correctamente!",
                        Toast.LENGTH_LONG
                    ).show()

                    // Ir a la pantalla principal
                    startActivity(Intent(this@StudentRegisterDeviceActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()

                } else {
                    val errorMsg = response.body()?.message ?: "Error al registrar el dispositivo"
                    Toast.makeText(this@StudentRegisterDeviceActivity, errorMsg, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnRegisterDevice.isEnabled = true
                Toast.makeText(
                    this@StudentRegisterDeviceActivity,
                    "Error de conexión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    @Suppress("DEPRECATION", "HardwareIds")
    private fun getDeviceSerial(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // En Android 8+, necesita permiso READ_PHONE_STATE
                // Usamos Android ID como alternativa
                Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
            } else {
                Build.SERIAL ?: ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}

