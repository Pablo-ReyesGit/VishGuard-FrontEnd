package com.vishguard.ai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.vishguard.ai.network.BackendApi
import com.vishguard.ai.network.BackendException
import com.vishguard.ai.services.OverlayService

class AccountActivity : AppCompatActivity() {
    private lateinit var session: SessionStore
    private lateinit var server: EditText
    private lateinit var name: EditText
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var phone: EditText
    private lateinit var status: TextView
    private lateinit var submit: Button
    private lateinit var mode: Button
    private lateinit var proceed: Button
    private lateinit var logout: Button
    private var registerMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)
        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        session = SessionStore(this)
        server = findViewById(R.id.etBackendUrl)
        name = findViewById(R.id.etAccountName)
        email = findViewById(R.id.etAccountEmail)
        password = findViewById(R.id.etAccountPassword)
        phone = findViewById(R.id.etAccountPhone)
        status = findViewById(R.id.tvAccountStatus)
        submit = findViewById(R.id.btnAccountSubmit)
        mode = findViewById(R.id.btnAccountMode)
        proceed = findViewById(R.id.btnAccountContinue)
        logout = findViewById(R.id.btnAccountLogout)
        server.setText(BackendConfig.url(this))
        mode.setOnClickListener { registerMode = !registerMode; render() }
        submit.setOnClickListener { submit() }
        proceed.setOnClickListener { openProtection() }
        logout.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            session.clear()
            password.text.clear()
            render()
        }
        render()
    }

    private fun render() {
        val user = session.user
        val signedIn = user != null && session.token != null
        findViewById<TextView>(R.id.tvAccountTitle).text =
            if (signedIn) "Mi cuenta" else if (registerMode) "Crear cuenta" else "Iniciar sesión"
        name.visibility = if (registerMode && !signedIn) View.VISIBLE else View.GONE
        password.visibility = if (signedIn) View.GONE else View.VISIBLE
        phone.visibility = if (signedIn || registerMode) View.VISIBLE else View.GONE
        email.isEnabled = !signedIn
        if (signedIn) {
            email.setText(user?.email)
            phone.setText(user?.phone_number.orEmpty())
        }
        submit.text = if (signedIn) "Guardar teléfono" else if (registerMode) "Crear cuenta" else "Iniciar sesión"
        mode.text = if (registerMode) "Ya tengo cuenta" else "Crear una cuenta"
        mode.visibility = if (signedIn) View.GONE else View.VISIBLE
        proceed.visibility = if (signedIn && !user?.phone_number.isNullOrBlank()) View.VISIBLE else View.GONE
        logout.visibility = if (signedIn) View.VISIBLE else View.GONE
        status.text = if (signedIn && user?.phone_number.isNullOrBlank())
            "Agrega tu teléfono con código de país para recibir las alertas."
        else if (signedIn) "Cuenta conectada. El teléfono debe coincidir con el destino de la llamada."
        else ""
        busy(false)
    }

    private fun submit() {
        val wasSignedIn = session.token != null
        val previousServer = BackendConfig.url(this)
        val baseUrl = try { BackendConfig.saveUrl(this, server.text.toString()) }
        catch (e: IllegalArgumentException) { status.text = e.message; return }
        if (wasSignedIn && previousServer != baseUrl) {
            stopService(Intent(this, OverlayService::class.java))
            render()
            status.text = "Servidor actualizado. Inicia sesión en ese servidor."
            return
        }
        val api = BackendApi(baseUrl)
        val token = session.token
        val recipient = phone.text.toString().trim()
        if ((token != null || registerMode) && !recipient.replace(Regex("[\\s()\\-]"), "")
                .matches(Regex("\\+[1-9][0-9]{7,14}"))) {
            phone.error = "Incluye + y el código de país, por ejemplo +50212345678"
            return
        }
        if (token != null) {
            busy(true)
            api.updatePhone(token, recipient) { result -> ui {
                result.fold(onSuccess = { user ->
                    runCatching { session.save(token, user) }.fold(onSuccess = {
                        stopService(Intent(this, OverlayService::class.java))
                        render()
                        status.text = "Teléfono guardado. Puedes activar la protección."
                    }, onFailure = { fail(it) })
                }, onFailure = { fail(it) })
            } }
            return
        }
        val address = email.text.toString().trim()
        val secret = password.text.toString()
        if (address.isBlank()) { email.error = "Ingresa tu correo"; return }
        if (secret.isEmpty() || (registerMode && secret.length !in 8..128)) {
            password.error = "Usa una contraseña de 8 a 128 caracteres"
            return
        }
        busy(true)
        if (registerMode) {
            api.register(name.text.toString().trim(), address, secret, recipient) { result -> ui {
                result.fold(onSuccess = {
                    // La cuenta existe aunque falle el inicio de sesión posterior.
                    registerMode = false
                    render()
                    busy(true)
                    status.text = "Cuenta creada. Iniciando sesión…"
                    login(api, address, secret)
                }, onFailure = { fail(it) })
            } }
        } else login(api, address, secret)
    }

    private fun login(api: BackendApi, address: String, secret: String) {
        api.login(address, secret) { result -> ui {
            result.fold(onSuccess = { token ->
                api.profile(token) { profile -> ui {
                    profile.fold(onSuccess = { user ->
                        runCatching { session.save(token, user) }.fold(onSuccess = {
                            password.text.clear()
                            render()
                            if (!user.phone_number.isNullOrBlank()) openProtection()
                        }, onFailure = { fail(it) })
                    }, onFailure = { fail(it) })
                } }
            }, onFailure = { fail(it) })
        } }
    }

    private fun fail(error: Throwable) {
        if (error is BackendException && error.statusCode in listOf(401, 403)) {
            session.clear()
            render()
        }
        busy(false)
        status.text = error.message ?: "No se pudo completar la solicitud."
    }

    private fun busy(value: Boolean) {
        findViewById<ProgressBar>(R.id.progressAccount).visibility = if (value) View.VISIBLE else View.GONE
        listOf(submit, mode, proceed, logout, server, name, email, password, phone).forEach { it.isEnabled = !value }
        if (!value && session.token != null) email.isEnabled = false
    }

    private fun openProtection() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    private fun ui(action: () -> Unit) {
        runOnUiThread { if (!isFinishing && !isDestroyed) action() }
    }
}
