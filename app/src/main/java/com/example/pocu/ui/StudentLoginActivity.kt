package com.example.pocu.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pocu.data.AppPreferences
import com.example.pocu.databinding.ActivityStudentLoginBinding
import com.example.pocu.network.AddDeviceRequest
import com.example.pocu.network.ApiClient
import com.example.pocu.network.StudentSearchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

/**
 * Pantalla de login para el alumno.
 * El alumno ingresa su RUT y la app lo busca en la base de datos.
 */
class StudentLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentLoginBinding
    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)

        Log.d("StudentLogin", "=== onCreate ===")
        Log.d("StudentLogin", "isStudentRegistered: ${prefs.isStudentRegistered()}")
        Log.d("StudentLogin", "StudentRut guardado: ${prefs.getStudentRut()}")

        // Si ya hay datos del estudiante, registrar dispositivo automáticamente y continuar
        val savedRut = prefs.getStudentRut()
        if (!savedRut.isNullOrEmpty()) {
            Log.d("StudentLogin", "RUT encontrado, registrando dispositivo automáticamente...")
            registerDeviceAutomatically(savedRut)
        } else {
            Log.d("StudentLogin", "No hay RUT guardado, mostrando pantalla de login")
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnSearchStudent.setOnClickListener {
            searchStudent()
        }

        // Agregar formateador de RUT en tiempo real
        binding.etRut.addTextChangedListener(RutTextWatcher(binding.etRut))
    }

    /**
     * TextWatcher para formatear el RUT automáticamente mientras se escribe
     * Formato final: XXXXXXXX-X (sin puntos, solo guión final)
     * Ejemplo: 24609128-4
     */
    private inner class RutTextWatcher(private val editText: android.widget.EditText) : TextWatcher {
        private var isFormatting = false

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            if (isFormatting) return

            isFormatting = true

            // Extraer solo los dígitos del input
            val input = s.toString().replace("[^0-9]".toRegex(), "")

            // Limitar a máximo 9 caracteres
            if (input.length > 9) {
                editText.setText(input.substring(0, 9))
                editText.setSelection(9)
                isFormatting = false
                return
            }

            // Formatear solo con guión final si hay 9 dígitos
            val formatted = if (input.length == 9) {
                "${input.substring(0, 8)}-${input.substring(8)}"
            } else {
                input
            }

            if (formatted != s.toString()) {
                editText.setText(formatted)
                editText.setSelection(formatted.length)
            }

            isFormatting = false
        }
    }

    private fun searchStudent() {
        val rut = binding.etRut.text.toString().trim()

        Log.d("StudentLogin", "RUT ingresado: '$rut'")

        if (rut.isEmpty()) {
            binding.tilRut.error = "Ingresa tu RUT"
            return
        }

        if (!isValidRut(rut)) {
            Log.d("StudentLogin", "RUT inválido: '$rut'")
            binding.tilRut.error = "RUT inválido"
            return
        }

        binding.tilRut.error = null
        val formattedRut = formatRut(rut)

        Log.d("StudentLogin", "RUT formateado: '$formattedRut'")

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSearchStudent.isEnabled = false
        binding.layoutNotFound.visibility = View.GONE

        lifecycleScope.launch {
            try {
                Log.d("StudentLogin", "Llamando API con RUT: '$formattedRut'")

                // Obtener información completa del alumno de la API
                val fullInfoResponse = withContext(Dispatchers.IO) {
                    ApiClient.getApiService().getStudentFullInfo(formattedRut)
                }

                binding.progressBar.visibility = View.GONE
                binding.btnSearchStudent.isEnabled = true

                Log.d("StudentLogin", "Respuesta recibida: ${fullInfoResponse.statusCode}")

                if (fullInfoResponse.statusCode == 200 && fullInfoResponse.data != null) {
                    Log.d("StudentLogin", "Datos válidos recibidos")
                    val studentData = fullInfoResponse.data!!
                    val generalInfo = studentData.generalInfo

                    // Guardar datos del estudiante
                    val fullName = "${generalInfo.firstName} ${generalInfo.lastName}"
                    val studentNumericId = generalInfo.id

                    Log.d("StudentLogin", "========================================")
                    Log.d("StudentLogin", "DATOS DEL ALUMNO RECIBIDOS:")
                    Log.d("StudentLogin", "ID numérico del alumno: $studentNumericId")
                    Log.d("StudentLogin", "Nombre: $fullName")
                    Log.d("StudentLogin", "Curso: '${generalInfo.courseLevel}'")
                    Log.d("StudentLogin", "Colegio: '${generalInfo.schoolName}'")
                    Log.d("StudentLogin", "========================================")

                    if (studentNumericId == 0) {
                        Log.e("StudentLogin", "⚠️ ADVERTENCIA: El ID del alumno es 0! Revisa el JSON de la API")
                    }

                    prefs.saveStudentData(
                        studentId = formattedRut,
                        studentName = fullName,
                        studentRut = formattedRut,
                        schoolName = generalInfo.schoolName,
                        studentCourse = generalInfo.courseLevel
                    )

                    // Guardar el ID numérico del alumno (necesario para la API de registro de dispositivo)
                    prefs.saveStudentNumericId(studentNumericId)

                    // Verificar que se guardó correctamente
                    val savedCourse = prefs.getStudentCourse()
                    Log.d("StudentLogin", "Curso guardado en preferences: '$savedCourse'")

                    // Convertir horarios de la API al formato de Schedule
                    val appSchedules = convertApiSchedulesToAppSchedules(studentData.schedules)
                    prefs.saveSchedules(appSchedules)

                    // Guardar apps permitidas
                    prefs.saveAllowedApps(setOf(
                        "com.google.android.calculator",
                        "com.android.calculator2",
                        "com.sec.android.app.popupcalculator",
                        "com.example.pocu"
                    ))

                    // Obtener información del dispositivo
                    val deviceSerial = getDeviceSerial()
                    val deviceBrand = Build.MANUFACTURER ?: "Desconocido"
                    val deviceModel = Build.MODEL ?: "Desconocido"

                    // Guardar serial del dispositivo
                    prefs.saveDeviceSerial(deviceSerial)

                    // Registrar dispositivo en la API (usando el ID numérico del alumno)
                    registerDeviceInApi(studentNumericId, deviceSerial, deviceBrand, deviceModel)

                    // Marcar como registrado
                    prefs.saveDeviceId("DEVICE-${System.currentTimeMillis()}")
                    prefs.saveStudentEmail("")
                    prefs.setStudentRegistered(true)

                    // Activar servicio por defecto (el alumno no lo administra)
                    prefs.setServiceEnabled(true)

                    // Iniciar servicios de bloqueo
                    com.example.pocu.service.AppBlockerService.start(this@StudentLoginActivity)
                    com.example.pocu.service.PermissionMonitorService.start(this@StudentLoginActivity)

                    // Mostrar horarios al alumno
                    showSchedulesDialog(appSchedules, fullName)

                } else {
                    Log.d("StudentLogin", "Respuesta inválida: ${fullInfoResponse.statusDesc}")
                    binding.layoutNotFound.visibility = View.VISIBLE
                    binding.tvNotFoundMessage.text = fullInfoResponse.statusDesc
                        ?: "Tu RUT aún no ha sido registrado en el sistema.\n\nContacta a tu establecimiento para que te registren."
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnSearchStudent.isEnabled = true

                Log.e("StudentLogin", "Error: ${e.message}", e)
                e.printStackTrace()
                Toast.makeText(
                    this@StudentLoginActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun isValidRut(rut: String): Boolean {
        // Limpiar el RUT: remover puntos y guiones
        val cleanRut = rut.replace(".", "").replace("-", "").uppercase()

        // Validar longitud exacta: 9 caracteres (8 dígitos + 1 DV)
        if (cleanRut.length != 9) return false

        // Los primeros 8 caracteres deben ser dígitos
        val body = cleanRut.substring(0, 8)
        if (!body.all { it.isDigit() }) return false

        // El último carácter debe ser un dígito o K
        val dv = cleanRut[8]
        if (!dv.isDigit() && dv != 'K') return false

        // Si pasa estas validaciones, es un RUT válido
        // (La validación del dígito verificador se puede agregar después si es necesario)
        return true
    }

    private fun formatRut(rut: String): String {
        // Limpiar el RUT: remover todos los puntos y guiones
        val clean = rut.replace(".", "").replace("-", "").uppercase()
        if (clean.length < 2) return rut

        // Si ya tiene exactamente 9 caracteres
        if (clean.length == 9) {
            // Formatear como XXXXXXXX-X (sin puntos, solo guión final)
            return "${clean.substring(0, 8)}-${clean.substring(8)}"
        }

        return clean
    }

    private fun goToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * Convierte los horarios de la API al formato de Schedule que usa la app
     * Los horarios de la API tienen días específicos (Lunes, Martes, etc.)
     * Se convierten a números de día (2=Lunes, 3=Martes, etc.)
     * Detecta automáticamente recreos, descansos y almuerzos
     */
    private fun convertApiSchedulesToAppSchedules(apiSchedules: List<com.example.pocu.network.StudentScheduleRaw>): List<com.example.pocu.data.Schedule> {
        val dayMap = mapOf(
            "Lunes" to 2,
            "Martes" to 3,
            "Miercoles" to 4,
            "Jueves" to 5,
            "Viernes" to 6,
            "Sabado" to 7,
            "Domingo" to 1
        )

        // Agrupar horarios por rangos de tiempo para crear Schedule únicos
        val scheduleMap = mutableMapOf<Pair<String, String>, MutableList<Int>>()
        var scheduleId = 1L

        apiSchedules.forEach { rawSchedule ->
            val dayNum = dayMap[rawSchedule.day] ?: return@forEach
            val timeKey = rawSchedule.startTime to rawSchedule.endTime

            if (!scheduleMap.containsKey(timeKey)) {
                scheduleMap[timeKey] = mutableListOf()
            }
            scheduleMap[timeKey]!!.add(dayNum)
        }

        // Crear Schedule objects
        val schedules = scheduleMap.map { (timeKey, days) ->
            val (startTimeStr, endTimeStr) = timeKey
            val (startHour, startMin) = startTimeStr.split(":").let { it[0].toInt() to it[1].toInt() }
            val (endHour, endMin) = endTimeStr.split(":").let { it[0].toInt() to it[1].toInt() }

            // Detectar si es recreo, descanso o almuerzo basándose en la duración
            // Recreos típicamente duran 15-30 minutos
            // Almuerzo típicamente dura 30-60 minutos
            val duration = (endHour * 60 + endMin) - (startHour * 60 + startMin)
            val isRecreOrLunch = duration <= 60 // Si dura menos de 1 hora, es recreo o almuerzo

            val scheduleName = if (isRecreOrLunch) {
                // Detectar el tipo específico por duración
                if (duration <= 20) "Recreo"
                else if (duration <= 45) "Descanso"
                else "Almuerzo"
            } else {
                "Hora de clase"
            }

            com.example.pocu.data.Schedule(
                id = scheduleId++,
                name = scheduleName,
                startHour = startHour,
                startMinute = startMin,
                endHour = endHour,
                endMinute = endMin,
                daysOfWeek = days.sorted(),
                enabled = true,
                isClassTime = !isRecreOrLunch // Si es recreo/descanso/almuerzo, no es hora de clase
            )
        }.sortedBy { it.startHour * 60 + it.startMinute }

        return schedules
    }

    private fun showSchedulesDialog(schedules: List<com.example.pocu.data.Schedule>, studentName: String) {
        // Ordenar por hora de inicio
        val sortedSchedules = schedules.sortedBy { it.startHour * 60 + it.startMinute }

        val message = StringBuilder()
        message.append("Tu horario de Lunes a Viernes:\n")
        message.append("═══════════════════════════════════\n\n")

        sortedSchedules.forEach { schedule ->
            val startTime = String.format("%02d:%02d", schedule.startHour, schedule.startMinute)
            val endTime = String.format("%02d:%02d", schedule.endHour, schedule.endMinute)

            // Mostrar icono, tipo y nombre
            if (schedule.isClassTime) {
                // Hora de clase - BLOQUEADA
                message.append("🔒 $startTime - $endTime\n")
                message.append("   CLASE: ${schedule.name}\n")
                message.append("   (Aplicación BLOQUEADA)\n\n")
            } else {
                // Recreo/Descanso/Almuerzo - PERMITIDA
                message.append("✅ $startTime - $endTime\n")
                message.append("   ${schedule.name.uppercase()}\n")
                message.append("   (Aplicación permitida)\n\n")
            }
        }

        message.append("═══════════════════════════════════\n\n")
        message.append("🔒 = Bloqueado (estudiar)\n")
        message.append("✅ = Permitido (descansar)")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🎉 ¡Bienvenido, $studentName!")
            .setMessage(message.toString())
            .setPositiveButton("Continuar") { _, _ ->
                // Ir a MainActivity
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Registra el dispositivo automáticamente cuando el usuario ya tiene datos guardados
     * y va directamente a MainActivity
     */
    private fun registerDeviceAutomatically(studentRut: String) {
        lifecycleScope.launch {
            try {
                // Obtener el ID numérico del alumno guardado
                var studentNumericId = prefs.getStudentNumericId()

                // Si no hay ID numérico guardado, consultamos la API para obtenerlo
                if (studentNumericId == -1) {
                    Log.d("StudentLogin", "No hay ID numérico guardado, consultando API...")

                    try {
                        val fullInfoResponse = withContext(Dispatchers.IO) {
                            ApiClient.getApiService().getStudentFullInfo(studentRut)
                        }

                        if (fullInfoResponse.statusCode == 200 && fullInfoResponse.data != null) {
                            studentNumericId = fullInfoResponse.data.generalInfo.id
                            // Guardar para futuras llamadas
                            prefs.saveStudentNumericId(studentNumericId)
                            Log.d("StudentLogin", "ID numérico obtenido de API: $studentNumericId")
                        } else {
                            Log.w("StudentLogin", "No se pudo obtener ID de la API, saltando registro")
                            goToMainActivity()
                            return@launch
                        }
                    } catch (e: Exception) {
                        Log.e("StudentLogin", "Error consultando API para obtener ID: ${e.message}")
                        goToMainActivity()
                        return@launch
                    }
                }

                val deviceSerial = getDeviceSerial()
                val deviceBrand = Build.MANUFACTURER ?: "Desconocido"
                val deviceModel = Build.MODEL ?: "Desconocido"

                Log.d("StudentLogin", "========================================")
                Log.d("StudentLogin", "Registro AUTOMÁTICO de dispositivo")
                Log.d("StudentLogin", "Alumno ID: $studentNumericId")
                Log.d("StudentLogin", "RUT: $studentRut")
                Log.d("StudentLogin", "Serial: $deviceSerial")
                Log.d("StudentLogin", "Marca: $deviceBrand")
                Log.d("StudentLogin", "Modelo: $deviceModel")
                Log.d("StudentLogin", "========================================")

                val request = AddDeviceRequest(
                    studentId = studentNumericId,
                    brand = deviceBrand,
                    model = deviceModel,
                    serial = deviceSerial
                )

                val response = withContext(Dispatchers.IO) {
                    ApiClient.getApiService().addDevice(request)
                }

                Log.d("StudentLogin", "Response code: ${response.code()}")

                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d("StudentLogin", "✅ Dispositivo registrado: ${body?.message}")
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.w("StudentLogin", "⚠️ Registro retornó ${response.code()}: $errorBody")
                }

            } catch (e: Exception) {
                Log.e("StudentLogin", "❌ Error en registro automático: ${e.message}", e)
            }

            // Siempre ir a MainActivity después del intento de registro
            goToMainActivity()
        }
    }

    private fun getDaysText(daysOfWeek: List<Int>): String {
        val dayNames = mapOf(
            1 to "Dom", 2 to "Lun", 3 to "Mar",
            4 to "Mié", 5 to "Jue", 6 to "Vie", 7 to "Sáb"
        )
        return daysOfWeek.sorted().mapNotNull { dayNames[it] }.joinToString(", ")
    }

    /**
     * Obtiene el serial/identificador único del dispositivo
     */
    @Suppress("DEPRECATION", "HardwareIds")
    private fun getDeviceSerial(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // En Android 8+, usamos Android ID como identificador único
                Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
            } else {
                Build.SERIAL ?: ""
            }
        } catch (e: Exception) {
            Log.e("StudentLogin", "Error obteniendo serial del dispositivo", e)
            "UNKNOWN-${System.currentTimeMillis()}"
        }
    }

    /**
     * Registra el dispositivo en la API del servidor
     * Se llama cada vez que un estudiante inicia sesión en un dispositivo nuevo
     */
    private fun registerDeviceInApi(
        studentId: Int,
        deviceSerial: String,
        brand: String,
        model: String
    ) {
        lifecycleScope.launch {
            try {
                Log.d("StudentLogin", "========================================")
                Log.d("StudentLogin", "Registrando dispositivo en API...")
                Log.d("StudentLogin", "Alumno ID: $studentId")
                Log.d("StudentLogin", "Serial: $deviceSerial")
                Log.d("StudentLogin", "Marca: $brand")
                Log.d("StudentLogin", "Modelo: $model")
                Log.d("StudentLogin", "========================================")

                val request = AddDeviceRequest(
                    studentId = studentId,
                    brand = brand,
                    model = model,
                    serial = deviceSerial
                )

                val response = withContext(Dispatchers.IO) {
                    ApiClient.getApiService().addDevice(request)
                }

                Log.d("StudentLogin", "Response code: ${response.code()}")
                Log.d("StudentLogin", "Response success: ${response.isSuccessful}")

                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d("StudentLogin", "Response body: success=${body?.success}, message=${body?.message}, deviceId=${body?.deviceId}")

                    if (body?.success == true) {
                        Log.d("StudentLogin", "✅ Dispositivo registrado exitosamente: ${body.message}")
                    } else {
                        Log.w("StudentLogin", "⚠️ API respondió pero success=false: ${body?.message}")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("StudentLogin", "❌ Error HTTP ${response.code()}: ${response.message()}")
                    Log.e("StudentLogin", "Error body: $errorBody")
                }
            } catch (e: Exception) {
                Log.e("StudentLogin", "❌ Exception registrando dispositivo en API: ${e.message}", e)
            }
        }
    }
}

