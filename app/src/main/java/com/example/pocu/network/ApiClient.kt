package com.example.pocu.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton para manejar la instancia de Retrofit y la API
 */
object ApiClient {
    private const val TAG = "ApiClient"
    private const val DEFAULT_BASE_URL = "https://pocu-api.azurewebsites.net/"

    private var baseUrl: String = DEFAULT_BASE_URL
    private var retrofit: Retrofit? = null
    private var apiService: PocuApiService? = null

    private val gson: Gson = GsonBuilder()
        .setLenient()
        .registerTypeAdapter(
            StudentFullInfoResponse::class.java,
            StudentFullInfoResponseDeserializer()
        )
        .create()

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d(TAG, message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .method(original.method, original.body)
                .build()

            val response = chain.proceed(request)

            // Log de la respuesta
            Log.d(TAG, "Response Code: ${response.code}")
            Log.d(TAG, "Response Headers: ${response.headers}")

            response
        }
        .build()

    /**
     * Configurar la URL base del servidor
     */
    fun setBaseUrl(url: String) {
        if (url != baseUrl) {
            baseUrl = url
            retrofit = null
            apiService = null
        }
    }

    /**
     * Obtener la URL base actual
     */
    fun getBaseUrl(): String = baseUrl

    /**
     * Verificar si el servidor está configurado
     */
    fun isServerConfigured(): Boolean {
        return baseUrl != DEFAULT_BASE_URL && baseUrl.isNotBlank()
    }

    /**
     * Obtener instancia de Retrofit
     */
    private fun getRetrofit(): Retrofit {
        if (retrofit == null) {
            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
        }
        return retrofit!!
    }

    /**
     * Obtener instancia del servicio API
     */
    fun getApiService(): PocuApiService {
        if (apiService == null) {
            apiService = getRetrofit().create(PocuApiService::class.java)
        }
        return apiService!!
    }
}
