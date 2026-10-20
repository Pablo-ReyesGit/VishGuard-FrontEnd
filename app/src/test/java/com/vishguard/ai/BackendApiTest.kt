package com.vishguard.ai

import com.google.gson.JsonParser
import com.vishguard.ai.network.BackendApi
import com.vishguard.ai.network.BackendException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BackendApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var api: BackendApi

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
        api = BackendApi(server.url("/").toString().trimEnd('/'), client)
    }

    @After fun tearDown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        server.shutdown()
    }

    private fun <T> await(start: ((Result<T>) -> Unit) -> Unit): Result<T> {
        val done = CountDownLatch(1)
        var result: Result<T>? = null
        start { result = it; done.countDown() }
        assertTrue("La solicitud no terminó", done.await(5, TimeUnit.SECONDS))
        return result!!
    }

    @Test fun registrationSendsPhoneToSignup() {
        server.enqueue(MockResponse().setBody(
            """{"id":1,"email":"test@example.com","full_name":"Prueba","phone_number":"+50212345678"}"""
        ))
        val result = await { callback ->
            api.register("Prueba", "test@example.com", "test-pass-123", "+50212345678", callback)
        }
        assertTrue(result.isSuccess)
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/users/signup", request.path)
        assertEquals("+50212345678",
            JsonParser.parseString(request.body.readUtf8()).asJsonObject.get("phone_number").asString)
    }

    @Test fun loginUsesOAuthFormInsteadOfJson() {
        server.enqueue(MockResponse().setBody("""{"access_token":"test-token","token_type":"bearer"}"""))
        val result = await { callback -> api.login("test@example.com", "test-pass-123", callback) }
        assertEquals("test-token", result.getOrThrow())
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/login/access-token", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
        val form = request.body.readUtf8()
        assertTrue(form.contains("username=test%40example.com"))
        assertTrue(form.contains("password=test-pass-123"))
    }

    @Test fun historySendsBearerTokenAndParsesEmptyList() {
        server.enqueue(MockResponse().setBody("[]"))
        val result = await { callback -> api.alerts("private-test-token", callback) }
        assertTrue(result.getOrThrow().isEmpty())
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/alerts", request.path)
        assertEquals("Bearer private-test-token", request.getHeader("Authorization"))
    }

    @Test fun duplicatePhoneBecomesVisibleError() {
        server.enqueue(MockResponse().setResponseCode(409)
            .setBody("""{"detail":"El teléfono ya está registrado"}"""))
        val result = await { callback -> api.updatePhone("test-token", "+50212345678", callback) }
        val error = result.exceptionOrNull() as BackendException
        assertEquals(409, error.statusCode)
        assertEquals("El teléfono ya está registrado", error.message)
    }

    @Test fun serverAddressRejectsPathsAndCredentials() {
        assertEquals("http://10.0.2.2:8000", BackendConfig.normalizeUrl("http://10.0.2.2:8000/"))
        for (value in listOf("http://host/docs", "http://user:secret@host/", "https://host/?token=123")) {
            assertTrue(runCatching { BackendConfig.normalizeUrl(value) }.isFailure)
        }
    }

    @Test fun simulationSendsTokenAndReceivesSavedRecordId() {
        server.enqueue(MockResponse().setBody(
            """{"nivel_riesgo":"PELIGROSO","score":95,"mensaje_alerta":"Prueba","recomendacion":"Verifica","es_simulacion":true,"alerta_guardada":true,"alerta_id":12}"""
        ))
        val result = await { callback -> api.simulate("test-token", "Frase de prueba", callback) }
        assertEquals(12, result.getOrThrow().alerta_id)
        assertEquals(true, result.getOrThrow().alerta_guardada)
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/analizar-llamada", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        val payload = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("Frase de prueba", payload.get("texto").asString)
        assertFalse(payload.has("usuario_id"))
    }

    @Test fun failedSimulationSaveIsNotReportedAsSuccessful() {
        server.enqueue(MockResponse().setResponseCode(503)
            .setBody("""{"detail":"No se pudo guardar la simulación. Inténtalo nuevamente."}"""))
        val result = await { callback -> api.simulate("test-token", "Frase de prueba", callback) }
        assertEquals(503, (result.exceptionOrNull() as BackendException).statusCode)
    }

    @Test fun oldBackendWithoutSaveConfirmationIsRejected() {
        server.enqueue(MockResponse().setBody(
            """{"nivel_riesgo":"PELIGROSO","score":95,"recomendacion":"Verifica"}"""
        ))
        val result = await { callback -> api.simulate("test-token", "Frase de prueba", callback) }
        assertTrue(result.isFailure)
    }
}
