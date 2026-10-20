package com.vishguard.ai.network

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ApiUser(
    val id: Int,
    val email: String,
    val full_name: String?,
    val phone_number: String?
)

data class ApiAlert(
    val id: Int,
    val nivel_riesgo: String,
    val score: Int,
    val mensaje_alerta: String,
    val recomendacion: String?,
    val fecha_alerta: String,
    val es_simulacion: Boolean = false
)

data class AnalysisEvaluation(
    val nivel_riesgo: String? = null,
    val score: Double? = null,
    val recomendacion: String? = null,
    val mensaje_alerta: String? = null,
    val es_simulacion: Boolean = false,
    val alerta_guardada: Boolean? = null,
    val alerta_id: Int? = null
)

class BackendException(val statusCode: Int, message: String) : IOException(message)

class BackendApi(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()

    fun register(name: String, email: String, password: String, phone: String,
                 callback: (Result<ApiUser>) -> Unit) {
        val body = gson.toJson(mapOf(
            "full_name" to name, "email" to email, "password" to password, "phone_number" to phone
        )).toRequestBody("application/json; charset=utf-8".toMediaType())
        execute(Request.Builder().url(baseUrl + "/api/v1/users/signup").post(body).build(),
            { gson.fromJson(it, ApiUser::class.java) }, callback)
    }

    fun login(email: String, password: String, callback: (Result<String>) -> Unit) {
        val body = FormBody.Builder().add("username", email).add("password", password).build()
        execute(Request.Builder().url(baseUrl + "/api/v1/login/access-token").post(body).build(),
            { JsonParser.parseString(it).asJsonObject.get("access_token").asString }, callback)
    }

    fun profile(token: String, callback: (Result<ApiUser>) -> Unit) {
        execute(authorized("/api/v1/users/me", token).get().build(),
            { gson.fromJson(it, ApiUser::class.java) }, callback)
    }

    fun updatePhone(token: String, phone: String, callback: (Result<ApiUser>) -> Unit) {
        val body = gson.toJson(mapOf("phone_number" to phone))
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        execute(authorized("/api/v1/users/me", token).patch(body).build(),
            { gson.fromJson(it, ApiUser::class.java) }, callback)
    }

    fun alerts(token: String, callback: (Result<List<ApiAlert>>) -> Unit) {
        val type = object : TypeToken<List<ApiAlert>>() {}.type
        execute(authorized("/alerts", token).get().build(),
            { gson.fromJson<List<ApiAlert>>(it, type) }, callback)
    }

    fun simulate(token: String, text: String, callback: (Result<AnalysisEvaluation>) -> Unit) {
        val body = gson.toJson(mapOf("texto" to text))
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        execute(authorized("/analizar-llamada", token).post(body).build(), {
            val result = gson.fromJson(it, AnalysisEvaluation::class.java)
            if (!result.es_simulacion || result.alerta_guardada != true || (result.alerta_id ?: 0) <= 0) {
                throw IOException("El servidor no confirmó el guardado. Comprueba que el backend esté actualizado.")
            }
            result
        }, callback)
    }

    private fun authorized(path: String, token: String) = Request.Builder()
        .url(baseUrl + path).header("Authorization", "Bearer " + token)

    private fun <T> execute(request: Request, parse: (String) -> T, callback: (Result<T>) -> Unit) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(IOException(
                    "No se pudo conectar al servidor. Revisa la dirección, la red y que el backend esté encendido.", e
                )))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        val content = it.body?.string().orEmpty()
                        if (!it.isSuccessful) throw BackendException(it.code, errorMessage(it.code, content))
                        parse(content)
                    }
                }
                callback(result)
            }
        })
    }

    private fun errorMessage(code: Int, content: String): String {
        val detail = runCatching { JsonParser.parseString(content).asJsonObject.get("detail") }.getOrNull()
        if (detail?.isJsonPrimitive == true) return detail.asString
        return when (code) {
            401, 403 -> "Tu sesión venció. Inicia sesión nuevamente."
            409 -> "El correo o teléfono ya está registrado."
            422 -> "Revisa los datos: correo válido, contraseña de 8 a 128 caracteres y teléfono con código de país."
            else -> "El servidor no pudo completar la solicitud (HTTP " + code + ")."
        }
    }
}
