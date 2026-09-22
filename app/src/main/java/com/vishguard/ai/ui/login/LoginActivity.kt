package com.vishguard.ai.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vishguard.ai.MainActivity
import com.vishguard.ai.R
import com.vishguard.ai.data.local.TokenManager
import com.vishguard.ai.data.remote.RetrofitClient
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenManager = TokenManager(this)

        // Si ya hay sesión activa con token guardado, saltar el Login e ir a MainActivity
        if (tokenManager.getToken() != null) {
            irAHome()
            return
        }

        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                realizarLogin(email, password)
            } else {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun realizarLogin(email: String, pass: String) {
        lifecycleScope.launch {
            try {
                android.util.Log.d("LOGIN_DEBUG", "Iniciando petición de login a FastAPI...")
                val service = RetrofitClient.getAuthService(this@LoginActivity)
                val response = service.login(email, pass)

                if (response.isSuccessful && response.body() != null) {
                    val token = response.body()!!.access_token
                    tokenManager.saveToken(token)
                    android.util.Log.d("LOGIN_DEBUG", "Login exitoso! Token guardado.")
                    irAHome()
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("LOGIN_DEBUG", "Error de respuesta HTTP ${response.code()}: $errorBody")
                    Toast.makeText(this@LoginActivity, "Credenciales incorrectas (${response.code()})", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("LOGIN_DEBUG", "Excepción atrapada al conectar:", e)
                Toast.makeText(this@LoginActivity, "Error de red: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun irAHome() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}