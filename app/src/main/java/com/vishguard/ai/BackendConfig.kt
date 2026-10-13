package com.vishguard.ai

import android.os.Build

object BackendConfig {
    // 10.0.2.2 es la dirección IP especial del router virtual del emulador Android para alcanzar el localhost de la PC
    const val EMULATOR_HOST = "10.0.2.2:8000"
    // IP de la red Wi-Fi de la PC para cuando se prueba en un dispositivo físico
    const val LAN_HOST = "192.168.1.28:8000"

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
        get() = if (isEmulator) "http://$EMULATOR_HOST" else "http://$LAN_HOST"
}
