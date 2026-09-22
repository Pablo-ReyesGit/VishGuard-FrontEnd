package com.vishguard.ai.data.remote

import android.content.Context
import android.util.Log
import com.vishguard.ai.data.local.TokenManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Apunta a localhost desde el emulador Android o a la IP local de tu PC si usas celular físico
    private const val BASE_URL = "http://192.168.6.220:8000/"
    //ip de mi casa, cambiar por la ip local de su maquina
    fun getAuthService(context: Context): AuthApiService {
        val tokenManager = TokenManager(context)

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()

                // 🔍 LOG 1: Muestra la URL completa a la que se está intentando conectar
                Log.d("RETROFIT_DEBUG", "--> Petición iniciada a URL: ${originalRequest.url}")
                Log.d("RETROFIT_DEBUG", "--> Método HTTP: ${originalRequest.method}")

                val token = tokenManager.getToken()
                val requestBuilder = originalRequest.newBuilder()

                if (token != null) {
                    Log.d("RETROFIT_DEBUG", "--> Adjuntando Token JWT en header Authorization")
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                } else {
                    Log.d("RETROFIT_DEBUG", "--> Sin token previo guardado (Login inicial)")
                }

                val request = requestBuilder.build()

                try {
                    val response = chain.proceed(request)
                    // 🔍 LOG 2: Si el servidor responde, muestra el código HTTP (200, 401, 422, 500, etc.)
                    Log.d("RETROFIT_DEBUG", "<-- Respuesta recibida del servidor. Código HTTP: ${response.code}")
                    response
                } catch (e: Exception) {
                    // 🔍 LOG 3: Si la conexión falla (timeout, sin red, servidor apagado), imprime la causa exacta
                    Log.e("RETROFIT_DEBUG", "<-- ERROR DE CONEXIÓN EN OKHTTP: ${e.message}", e)
                    throw e
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }
}