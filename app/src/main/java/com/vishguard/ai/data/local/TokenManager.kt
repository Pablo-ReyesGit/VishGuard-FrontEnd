package com.vishguard.ai.data.local

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

class TokenManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secret_shared_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        prefs.edit().putString("JWT_TOKEN", token).apply()
    }

    fun getToken(): String? {
        return prefs.getString("JWT_TOKEN", null)
    }

    fun clearToken() {
        prefs.edit().remove("JWT_TOKEN").apply()
    }

    /**
     * VALIDACIÓN: confirma que hay un token guardado, que tiene la forma de
     * un JWT (3 partes separadas por punto) y que no venció según su claim
     * "exp". No verifica la firma -- eso solo el backend puede garantizarlo,
     * porque la app no tiene el SECRET_KEY -- pero evita tratar como "sesión
     * activa" un token corrupto, vacío o ya expirado.
     */
    fun hasValidToken(): Boolean {
        val token = getToken()
        if (token.isNullOrBlank()) return false

        return try {
            val payload = decodePayload(token)
            val exp = payload.optLong("exp", -1L)
            if (exp == -1L) {
                true // el token no trae "exp" -> no podemos invalidarlo por expiración
            } else {
                val ahoraEnSegundos = System.currentTimeMillis() / 1000
                exp > ahoraEnSegundos
            }
        } catch (e: Exception) {
            false // formato inválido -> se trata como no autenticado
        }
    }

    /**
     * MANIPULACIÓN: decodifica el payload (segunda parte) del JWT actual y
     * lo devuelve como JSONObject, para leer claims como "sub" o "exp" sin
     * depender de una librería externa de JWT.
     */
    fun decodePayloadDelTokenActual(): JSONObject? {
        val token = getToken() ?: return null
        return try {
            decodePayload(token)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodePayload(token: String): JSONObject {
        val partes = token.split(".")
        require(partes.size == 3) { "Token JWT mal formado (se esperaban 3 partes, hay ${partes.size})" }

        // El JWT usa Base64 URL-safe sin padding, distinto del Base64 por defecto de Android
        val payloadBytes = Base64.decode(partes[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return JSONObject(String(payloadBytes, Charsets.UTF_8))
    }
}