package com.vishguard.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.vishguard.ai.network.ApiUser
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Token y perfil cifrados con una clave local del dispositivo. No guarda contraseñas. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("vishguard_session", Context.MODE_PRIVATE)
    private val gson = Gson()
    private data class SavedSession(val token: String, val user: ApiUser)

    val token: String? get() = read()?.token
    val user: ApiUser? get() = read()?.user

    fun save(token: String, user: ApiUser) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(gson.toJson(SavedSession(token, user)).toByteArray(Charsets.UTF_8))
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString("session", value).apply()
    }

    fun clear() { prefs.edit().clear().apply() }

    private fun read(): SavedSession? {
        val value = prefs.getString("session", null) ?: return null
        return runCatching {
            val parts = value.split(":")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(),
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            val json = String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
            gson.fromJson(json, SavedSession::class.java)
        }.getOrElse { clear(); null }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    companion object { private const val KEY_ALIAS = "vishguard_session_key" }
}
