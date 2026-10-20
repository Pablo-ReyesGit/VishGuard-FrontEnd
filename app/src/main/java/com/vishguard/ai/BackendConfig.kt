package com.vishguard.ai

import android.os.Build
import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object BackendConfig {
    // 10.0.2.2 es la dirección IP especial del router virtual del emulador Android para alcanzar el localhost de la PC
    const val EMULATOR_HOST = "10.0.2.2:8000"
    // Teléfono por USB con "adb reverse tcp:8000 tcp:8000".
    // Por Wi-Fi se configura la dirección de la PC desde la pantalla de cuenta.
    const val DEVICE_HOST = "127.0.0.1:8000"

    /**
     * Detecta si la aplicación se está ejecutando en un emulador Android (por ejemplo, el Pixel 8 del emulador)
     */
    val isEmulator: Boolean
        get() = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_gphone")

    /**
     * Retorna la URL base correspondiente al entorno de ejecución
     */
    val baseUrl: String
        get() = if (isEmulator) "http://$EMULATOR_HOST" else "http://$DEVICE_HOST"

    fun url(context: Context): String = context.getSharedPreferences("vishguard_config", Context.MODE_PRIVATE)
        .getString("backend_url", null) ?: baseUrl

    fun saveUrl(context: Context, value: String): String {
        val normalized = normalizeUrl(value)
        if (normalized != url(context)) SessionStore(context).clear()
        context.getSharedPreferences("vishguard_config", Context.MODE_PRIVATE)
            .edit().putString("backend_url", normalized).apply()
        return normalized
    }

    fun normalizeUrl(value: String): String {
        val parsed = value.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Escribe una dirección http:// o https:// válida.")
        require(parsed.username.isEmpty() && parsed.password.isEmpty() &&
            parsed.encodedPath == "/" && parsed.query == null && parsed.fragment == null) {
            "Usa solo la dirección del servidor, sin /docs, rutas ni credenciales."
        }
        return parsed.toString().trimEnd('/')
    }
}
