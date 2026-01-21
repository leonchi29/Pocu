package com.example.pocu.network

import com.google.gson.annotations.SerializedName

/**
 * Modelos para comunicación con el servidor web
 */

// Información del dispositivo que se envía al servidor
data class DeviceInfo(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_model") val deviceModel: String,
    @SerializedName("android_version") val androidVersion: String,
    @SerializedName("app_version") val appVersion: String
)

// Heartbeat - señal de vida que envía el dispositivo periódicamente
data class HeartbeatRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("is_service_enabled") val isServiceEnabled: Boolean,
    @SerializedName("is_lockdown_mode") val isLockdownMode: Boolean,
    @SerializedName("permissions") val permissions: PermissionsStatus,
    @SerializedName("battery_level") val batteryLevel: Int
)

data class PermissionsStatus(
    @SerializedName("device_admin") val deviceAdmin: Boolean,
    @SerializedName("accessibility") val accessibility: Boolean,
    @SerializedName("overlay") val overlay: Boolean,
    @SerializedName("usage_stats") val usageStats: Boolean
)

// Respuesta del servidor con comandos
data class ServerResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("commands") val commands: List<ServerCommand>?
)

data class ServerCommand(
    @SerializedName("type") val type: String, // "lockdown", "unlock", "update_schedule", "update_allowed_apps"
    @SerializedName("data") val data: Map<String, Any>?
)

// Registro de dispositivo
data class RegisterDeviceRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_info") val deviceInfo: DeviceInfo,
    @SerializedName("parent_code") val parentCode: String // Código para vincular con cuenta del padre
)

data class RegisterDeviceResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("device_token") val deviceToken: String? // Token único para este dispositivo
)

// Configuración remota (horarios, apps permitidas, etc.)
data class RemoteConfig(
    @SerializedName("schedules") val schedules: List<RemoteSchedule>?,
    @SerializedName("allowed_apps") val allowedApps: List<String>?,
    @SerializedName("lockdown_enabled") val lockdownEnabled: Boolean?,
    @SerializedName("service_enabled") val serviceEnabled: Boolean?
)

data class RemoteSchedule(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("start_hour") val startHour: Int,
    @SerializedName("start_minute") val startMinute: Int,
    @SerializedName("end_hour") val endHour: Int,
    @SerializedName("end_minute") val endMinute: Int,
    @SerializedName("days_of_week") val daysOfWeek: List<Int>,
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("is_class_time") val isClassTime: Boolean
)

// Evento de alerta (intento de desinstalación, cambio de permisos, etc.)
data class AlertEvent(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("event_type") val eventType: String, // "permission_revoked", "uninstall_attempt", "lockdown_activated"
    @SerializedName("details") val details: String?
)

// ============ Student Login Models ============

// Búsqueda de estudiante por RUT
data class StudentSearchRequest(
    @SerializedName("rut") val rut: String
)

data class StudentSearchResponse(
    @SerializedName("found") val found: Boolean,
    @SerializedName("student_id") val studentId: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("school_name") val schoolName: String?,
    @SerializedName("message") val message: String?
)

// Registro de dispositivo de estudiante
data class DeviceRegistrationRequest(
    @SerializedName("student_id") val studentId: String,
    @SerializedName("student_rut") val studentRut: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_serial") val deviceSerial: String,
    @SerializedName("device_model") val deviceModel: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("android_version") val androidVersion: String,
    @SerializedName("email") val email: String,
    @SerializedName("app_version") val appVersion: String
)

data class DeviceRegistrationResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("device_token") val deviceToken: String?,
    @SerializedName("schedules") val schedules: List<Map<String, Any>>?,
    @SerializedName("allowed_apps") val allowedApps: List<String>?
)

// ============ Full Student Info API Models ============

/**
 * Respuesta de la API de información completa del alumno
 * Endpoint: https://pocu-api.azurewebsites.net/api/v1/Alumnos/full-info/{rut}
 */
data class StudentFullInfoResponse(
    @SerializedName("statusCod") val statusCode: Int,
    @SerializedName("statusDesc") val statusDesc: String,
    @SerializedName("data") val data: StudentFullData?
)

data class StudentFullData(
    @SerializedName("infoGeneral") val generalInfo: StudentGeneralInfo,
    @SerializedName("dispositivos") val devices: List<StudentDevice>,
    @SerializedName("horarios") val schedules: List<StudentScheduleRaw>
)

data class StudentGeneralInfo(
    @SerializedName("nombre") val firstName: String,
    @SerializedName("apellido") val lastName: String,
    @SerializedName("curso") val courseLevel: String,
    @SerializedName("colegio") val schoolName: String
)

data class StudentDevice(
    @SerializedName("marca") val brand: String,
    @SerializedName("modelo") val model: String,
    @SerializedName("serial") val serial: String
)

data class StudentScheduleRaw(
    @SerializedName("dia") val day: String,
    @SerializedName("inicio") val startTime: String, // formato: "HH:mm:ss"
    @SerializedName("fin") val endTime: String       // formato: "HH:mm:ss"
)
