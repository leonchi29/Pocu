package com.example.pocu.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pocu.data.AppPreferences
import com.example.pocu.databinding.ActivityStudentLoginBinding
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

        // Si ya está registrado, ir directamente a MainActivity
        if (prefs.isStudentRegistered()) {
            goToMainActivity()
            return
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
                    Log.d("StudentLogin", "Nombre del alumno: $fullName")
                    Log.d("StudentLogin", "Curso del alumno: '${generalInfo.courseLevel}'")
                    Log.d("StudentLogin", "Colegio: '${generalInfo.schoolName}'")

                    prefs.saveStudentData(
                        studentId = formattedRut,
                        studentName = fullName,
                        studentRut = formattedRut,
                        schoolName = generalInfo.schoolName,
                        studentCourse = generalInfo.courseLevel
                    )

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

    private fun getDaysText(daysOfWeek: List<Int>): String {
        val dayNames = mapOf(
            1 to "Dom", 2 to "Lun", 3 to "Mar",
            4 to "Mié", 5 to "Jue", 6 to "Vie", 7 to "Sáb"
        )
        return daysOfWeek.sorted().mapNotNull { dayNames[it] }.joinToString(", ")
    }
}

