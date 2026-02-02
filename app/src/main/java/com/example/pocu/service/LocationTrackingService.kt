package com.example.pocu.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.pocu.R
import com.example.pocu.data.AppPreferences
import com.example.pocu.network.ApiClient
import com.example.pocu.network.LocationUpdateRequest
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.util.Calendar

/**
 * Servicio de rastreo GPS durante horario escolar
 * Envía ubicación cada 20 minutos solo durante horario de clases
 * Se detiene automáticamente al finalizar la última clase del día
 */
class LocationTrackingService : Service() {

    private lateinit var prefs: AppPreferences
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var lastLocation: Location? = null

    companion object {
        private const val TAG = "LocationTrackingService"
        private const val NOTIFICATION_ID = 4001
        private const val CHANNEL_ID = "location_tracking_channel"

        // Intervalo de envío de ubicación: 20 minutos
        private const val LOCATION_UPDATE_INTERVAL = 20 * 60 * 1000L // 20 minutos en milisegundos

        // Intervalo de verificación de horario: cada minuto
        private const val SCHEDULE_CHECK_INTERVAL = 60 * 1000L // 1 minuto

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }

        fun isLocationPermissionGranted(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        setupLocationCallback()
        Log.d(TAG, "LocationTrackingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        if (!isRunning) {
            isRunning = true
            startLocationTracking()
            startScheduleChecker()
            Log.d(TAG, "LocationTrackingService started")
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        stopLocationUpdates()
        Log.d(TAG, "LocationTrackingService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ubicación Escolar",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                description = "Rastreo de ubicación durante horario escolar"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pocu")
            .setContentText("Ubicación activa durante horario escolar")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    lastLocation = location
                    Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
                }
            }
        }
    }

    private fun startLocationTracking() {
        if (!isLocationPermissionGranted(this)) {
            Log.w(TAG, "Location permission not granted, stopping service")
            stopSelf()
            return
        }

        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                LOCATION_UPDATE_INTERVAL
            ).apply {
                setMinUpdateIntervalMillis(LOCATION_UPDATE_INTERVAL / 2)
                setWaitForAccurateLocation(false)
            }.build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // Enviar ubicación inmediatamente y luego cada 20 minutos
            sendLocationUpdate()
            startLocationSendLoop()

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception requesting location", e)
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
    }

    private fun startLocationSendLoop() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isRunning && isWithinClassHours()) {
                    sendLocationUpdate()
                    handler.postDelayed(this, LOCATION_UPDATE_INTERVAL)
                } else if (isRunning && !isWithinClassHours()) {
                    Log.d(TAG, "Outside class hours, stopping location updates")
                    // Continuamos el servicio pero no enviamos ubicación
                    handler.postDelayed(this, LOCATION_UPDATE_INTERVAL)
                }
            }
        }, LOCATION_UPDATE_INTERVAL)
    }

    private fun startScheduleChecker() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isRunning) {
                    checkAndStopIfNeeded()
                    handler.postDelayed(this, SCHEDULE_CHECK_INTERVAL)
                }
            }
        }, SCHEDULE_CHECK_INTERVAL)
    }

    /**
     * Verifica si estamos dentro del horario de clases
     * Considera desde la primera clase hasta la última clase del día
     */
    private fun isWithinClassHours(): Boolean {
        val schedules = prefs.getSchedules().filter { it.enabled && it.isClassTime }
        if (schedules.isEmpty()) return false

        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val currentTimeInMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        // Filtrar horarios para hoy
        val todaySchedules = schedules.filter { it.daysOfWeek.contains(currentDayOfWeek) }
        if (todaySchedules.isEmpty()) return false

        // Encontrar el inicio de la primera clase y el fin de la última clase
        val firstClassStart = todaySchedules.minOf { it.startHour * 60 + it.startMinute }
        val lastClassEnd = todaySchedules.maxOf { it.endHour * 60 + it.endMinute }

        return currentTimeInMinutes in firstClassStart..lastClassEnd
    }

    /**
     * Obtiene la hora de finalización de la última clase del día
     * Retorna null si no hay clases hoy
     */
    private fun getLastClassEndTime(): Int? {
        val schedules = prefs.getSchedules().filter { it.enabled && it.isClassTime }
        if (schedules.isEmpty()) return null

        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        val todaySchedules = schedules.filter { it.daysOfWeek.contains(currentDayOfWeek) }
        if (todaySchedules.isEmpty()) return null

        return todaySchedules.maxOf { it.endHour * 60 + it.endMinute }
    }

    /**
     * Verifica si debemos detener el servicio (después de la última clase)
     */
    private fun checkAndStopIfNeeded() {
        val lastClassEnd = getLastClassEndTime() ?: return

        val calendar = Calendar.getInstance()
        val currentTimeInMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

        // Si ya pasó la hora de finalización de la última clase, detener el servicio
        if (currentTimeInMinutes > lastClassEnd) {
            Log.d(TAG, "Last class ended at ${lastClassEnd / 60}:${lastClassEnd % 60}, stopping service")
            stopSelf()
        }
    }

    private fun sendLocationUpdate() {
        if (!isWithinClassHours()) {
            Log.d(TAG, "Outside class hours, skipping location update")
            return
        }

        val location = lastLocation
        if (location == null) {
            // Intentar obtener última ubicación conocida
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    loc?.let {
                        lastLocation = it
                        performLocationUpdate(it)
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception getting last location", e)
            }
            return
        }

        performLocationUpdate(location)
    }

    private fun performLocationUpdate(location: Location) {
        serviceScope.launch {
            try {
                val deviceSerial = prefs.getDeviceSerial() ?: run {
                    Log.w(TAG, "Device serial not found, skipping location update")
                    return@launch
                }

                val studentRut = prefs.getStudentRut() ?: run {
                    Log.w(TAG, "Student RUT not found, skipping location update")
                    return@launch
                }

                val request = LocationUpdateRequest(
                    deviceSerial = deviceSerial,
                    studentRut = studentRut,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    timestamp = System.currentTimeMillis(),
                    batteryLevel = getBatteryLevel()
                )

                Log.d(TAG, "Sending location: ${location.latitude}, ${location.longitude}")

                val response = ApiClient.getApiService().sendDeviceLocation(request)

                if (response.isSuccessful) {
                    Log.d(TAG, "Location sent successfully")
                } else {
                    Log.e(TAG, "Error sending location: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending location update", e)
            }
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }
}
