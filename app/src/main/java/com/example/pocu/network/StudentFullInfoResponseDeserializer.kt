package com.example.pocu.network

import com.google.gson.*
import java.lang.reflect.Type

/**
 * ╔════════════════════════════════════════════════════════════════╗
 * ║  DESERIALIZER PERSONALIZADO - RESPUESTA ALUMNO POCU            ║
 * ║  Convierte JSON de la API a objetos Kotlin                     ║
 * ║  ✅ Sin validación de estado de servicio (siempre activo)      ║
 * ╚════════════════════════════════════════════════════════════════╝
 */
class StudentFullInfoResponseDeserializer : JsonDeserializer<StudentFullInfoResponse> {

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): StudentFullInfoResponse {
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 1️⃣ VALIDAR Y OBTENER JSON OBJECT
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        val jsonObject = json?.asJsonObject ?: throw JsonParseException("JSON is null")

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 2️⃣ EXTRAER CÓDIGO Y DESCRIPCIÓN DE ESTADO
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        val statusCode = jsonObject.get("statusCod")?.asInt ?: 0
        val statusDesc = jsonObject.get("statusDesc")?.asString ?: ""
        val dataElement = jsonObject.get("data")

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 3️⃣ PROCESAR DATOS DEL ALUMNO (si existen)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        val data = if (dataElement != null && !dataElement.isJsonNull) {
            val dataObj = dataElement.asJsonObject

            // 📋 Información General del Alumno
            val infoGeneral = parseGeneralInfo(dataObj.get("infoGeneral")?.asJsonObject)

            // 📱 Dispositivos Registrados
            val devices = parseDevices(dataObj.get("dispositivos")?.asJsonArray ?: JsonArray())

            // ⏰ Horarios de Clases y Recreos
            val schedules = parseSchedules(dataObj.get("horarios")?.asJsonArray ?: JsonArray())

            StudentFullData(
                generalInfo = infoGeneral,
                devices = devices,
                schedules = schedules
            )
        } else {
            null
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 4️⃣ RETORNAR RESPUESTA COMPLETA
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        return StudentFullInfoResponse(
            statusCode = statusCode,
            statusDesc = statusDesc,
            data = data
        )
    }

    /**
     * 📝 Parsear información general del alumno
     * @param obj JSON object con nombre, apellido, curso, colegio
     */
    private fun parseGeneralInfo(obj: JsonObject?): StudentGeneralInfo {
        return StudentGeneralInfo(
            firstName = obj?.get("nombre")?.asString ?: "",
            lastName = obj?.get("apellido")?.asString ?: "",
            courseLevel = obj?.get("curso")?.asString ?: "",
            schoolName = obj?.get("colegio")?.asString ?: ""
        )
    }

    /**
     * 📱 Parsear dispositivos registrados
     * @param array JSON array con marca, modelo, serial
     */
    private fun parseDevices(array: JsonArray): List<StudentDevice> {
        return array.mapNotNull { element ->
            val obj = element.asJsonObject
            StudentDevice(
                brand = obj.get("marca")?.asString ?: return@mapNotNull null,
                model = obj.get("modelo")?.asString ?: return@mapNotNull null,
                serial = obj.get("serial")?.asString ?: return@mapNotNull null
            )
        }
    }

    /**
     * ⏰ Parsear horarios de clases y recreos
     * @param array JSON array con dia, inicio, fin
     */
    private fun parseSchedules(array: JsonArray): List<StudentScheduleRaw> {
        return array.mapNotNull { element ->
            val obj = element.asJsonObject
            StudentScheduleRaw(
                day = obj.get("dia")?.asString ?: return@mapNotNull null,
                startTime = obj.get("inicio")?.asString ?: return@mapNotNull null,
                endTime = obj.get("fin")?.asString ?: return@mapNotNull null
            )
        }
    }

    // ╔════════════════════════════════════════════════════════════════╗
    // ║  FIN DEL DESERIALIZER - POCU APP BLOCKER v1.0                 ║
    // ║  🚀 Conversión segura de datos desde API a objetos Kotlin     ║
    // ╚════════════════════════════════════════════════════════════════╝
}


