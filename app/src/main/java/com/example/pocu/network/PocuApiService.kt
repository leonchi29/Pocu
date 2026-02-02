package com.example.pocu.network

import retrofit2.Response
import retrofit2.http.*

/**
 * API Interface para comunicación con el servidor web
 */
interface PocuApiService {

    /**
     * Registrar dispositivo con una cuenta de padre/administrador
     */
    @POST("api/devices/register")
    suspend fun registerDevice(
        @Body request: RegisterDeviceRequest
    ): Response<RegisterDeviceResponse>

    /**
     * Enviar heartbeat (señal de vida) al servidor
     * El servidor puede responder con comandos
     */
    @POST("api/devices/heartbeat")
    suspend fun sendHeartbeat(
        @Header("Authorization") token: String,
        @Body request: HeartbeatRequest
    ): Response<ServerResponse>

    /**
     * Obtener configuración remota (horarios, apps permitidas)
     */
    @GET("api/devices/{deviceId}/config")
    suspend fun getRemoteConfig(
        @Header("Authorization") token: String,
        @Path("deviceId") deviceId: String
    ): Response<RemoteConfig>

    /**
     * Enviar alerta al servidor (intento de desinstalación, cambio de permisos)
     */
    @POST("api/alerts")
    suspend fun sendAlert(
        @Header("Authorization") token: String,
        @Body alert: AlertEvent
    ): Response<ServerResponse>

    /**
     * Confirmar que se recibió un comando
     */
    @POST("api/commands/{commandId}/ack")
    suspend fun acknowledgeCommand(
        @Header("Authorization") token: String,
        @Path("commandId") commandId: String
    ): Response<ServerResponse>

    /**
     * Verificar si el servidor está disponible
     */
    @GET("api/health")
    suspend fun healthCheck(): Response<ServerResponse>

    // ============ Student Login Endpoints ============

    /**
     * Buscar estudiante por RUT
     */
    @POST("api/students/search")
    suspend fun searchStudent(
        @Body request: StudentSearchRequest
    ): Response<StudentSearchResponse>

    /**
     * Obtener información completa del alumno (datos generales, dispositivos, horarios)
     * Endpoint: https://pocu-api.azurewebsites.net/api/v1/Alumnos/full-info/{rut}
     * Formato del RUT: XXXXXXXX-X (sin puntos, solo guión final)
     * Ejemplo: 24609128-4
     */
    @GET("api/v1/Alumnos/full-info/{rut}")
    suspend fun getStudentFullInfo(
        @Path("rut") rut: String
    ): StudentFullInfoResponse

    /**
     * Registrar dispositivo de estudiante
     */
    @POST("api/students/register-device")
    suspend fun registerStudentDevice(
        @Body request: DeviceRegistrationRequest
    ): Response<DeviceRegistrationResponse>

    /**
     * Enviar ubicación GPS del dispositivo durante horario escolar
     * Endpoint: https://pocu-api.azurewebsites.net/api/v1/dispositivos/ubicacion
     */
    @POST("api/v1/dispositivos/ubicacion")
    suspend fun sendDeviceLocation(
        @Body request: LocationUpdateRequest
    ): Response<LocationUpdateResponse>

    /**
     * Registrar un nuevo dispositivo asociado a un estudiante
     * Se llama cuando el estudiante inicia sesión por primera vez en un dispositivo
     * Endpoint: https://pocu-api.azurewebsites.net/api/v1/dispositivos/add
     */
    @POST("api/v1/dispositivos/add")
    suspend fun addDevice(
        @Body request: AddDeviceRequest
    ): Response<AddDeviceResponse>
}

