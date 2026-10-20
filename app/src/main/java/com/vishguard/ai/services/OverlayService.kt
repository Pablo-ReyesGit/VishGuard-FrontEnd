package com.vishguard.ai.services

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.vishguard.ai.R
import com.vishguard.ai.utils.SoundFeedbackHelper
import okhttp3.*
import java.util.concurrent.TimeUnit

// Data Class adaptada al JSON que emite el backend FastAPI
data class AlertaResultado(
    val hablante: String? = null,        // "llamante" | "receptor"
    val texto: String? = null,           // Texto sanitizado
    val nivel_riesgo: String? = null,    // "alto" | "medio" | "bajo"
    val score: Double? = 0.0,            // Puntuación del análisis
    val inicio_ms: Long? = null,
    val recomendacion: String? = null
)

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var cardContainer: LinearLayout
    private lateinit var layoutBubbleMinimized: FrameLayout
    private lateinit var tvShieldStatus: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvRecommendation: TextView
    private lateinit var btnCloseOverlay: TextView

    private var currentColor: Int = Color.parseColor("#1B5E20")
    private var isMinimized: Boolean = false

    // WebSocket / Network Client
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private val gson = Gson()

    private val TAG = "VishGuardOverlay"

    // Número de destino registrado en el backend
    private val NUMERO_DESTINO = "+50246225899"
    // URL activa de Ngrok
    private val WS_BASE_URL = "wss://dealmaker-sputter-antiviral.ngrok-free.dev"

    companion object {
        const val EXTRA_TEXTO = "extra_texto_llamada"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 OverlayService creado. Conectando al WebSocket de alertas...")
        startForegroundServiceNotification()
        setupOverlayWindow()
        conectarWebSocketAlertas()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val textoRecibido = intent?.getStringExtra(EXTRA_TEXTO)

        if (!textoRecibido.isNullOrBlank()) {
            // Prueba manual desde el diálogo de simulación
            simularAlertaLocal(textoRecibido)
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "vishguard_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VishGuard Protection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("VishGuard AI Activo")
            .setContentText("Monitoreando eventos de llamadas...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1, notification)
        }
    }

    private fun setupOverlayWindow() {
        if (overlayView != null) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_layout, null)

        cardContainer = overlayView!!.findViewById(R.id.overlayContainer)
        layoutBubbleMinimized = overlayView!!.findViewById(R.id.layoutBubbleMinimized)
        tvShieldStatus = overlayView!!.findViewById(R.id.tvShieldStatus)
        tvScore = overlayView!!.findViewById(R.id.tvScore)
        tvRecommendation = overlayView!!.findViewById(R.id.tvRecommendation)
        btnCloseOverlay = overlayView!!.findViewById(R.id.btnCloseOverlay)

        btnCloseOverlay.setOnClickListener {
            minimizarABurbuja()
        }

        layoutBubbleMinimized.setOnClickListener {
            restaurarTarjetaCompleta()
        }

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 80

        overlayView!!.alpha = 0f
        overlayView!!.translationY = -60f
        windowManager.addView(overlayView, params)

        overlayView!!.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .start()
    }

    private fun minimizarABurbuja() {
        isMinimized = true
        cardContainer.animate()
            .alpha(0f)
            .scaleX(0.7f)
            .scaleY(0.7f)
            .setDuration(220)
            .withEndAction {
                cardContainer.visibility = View.GONE

                val bgBubble = layoutBubbleMinimized.background as? GradientDrawable
                bgBubble?.setColor(currentColor)

                layoutBubbleMinimized.visibility = View.VISIBLE
                layoutBubbleMinimized.alpha = 0f
                layoutBubbleMinimized.scaleX = 0.4f
                layoutBubbleMinimized.scaleY = 0.4f

                layoutBubbleMinimized.animate()
                    .alpha(1f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(260)
                    .setInterpolator(OvershootInterpolator(2.0f))
                    .start()
            }
            .start()
    }

    private fun restaurarTarjetaCompleta() {
        isMinimized = false
        layoutBubbleMinimized.animate()
            .alpha(0f)
            .scaleX(0.4f)
            .scaleY(0.4f)
            .setDuration(180)
            .withEndAction {
                layoutBubbleMinimized.visibility = View.GONE

                cardContainer.visibility = View.VISIBLE
                cardContainer.alpha = 0f
                cardContainer.scaleX = 0.85f
                cardContainer.scaleY = 0.85f

                cardContainer.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(250)
                    .setInterpolator(OvershootInterpolator(1.4f))
                    .start()
            }
            .start()
    }

    // Conexión como cliente receptor del WebSocket de alertas del backend
    private fun conectarWebSocketAlertas() {
        // 1. Limpiar el número de teléfono eliminando el símbolo '+'
        val numeroLimpio = NUMERO_DESTINO.replace("+", "").trim()

        // 2. Apuntar al endpoint original '/ws/stream' usando Query Param 'numero'
        val url = "$WS_BASE_URL/ws/stream?numero=$numeroLimpio"

        // 3. Agregar las cabeceras requeridas para evitar el bloqueo 403 Forbidden de Ngrok
        val request = Request.Builder()
            .url(url)
            .addHeader("ngrok-skip-browser-warning", "true")
            .addHeader("User-Agent", "VishGuardAndroidApp/1.0")
            .build()

        Log.i(TAG, "⚡ Conectando a canal de alertas: $url")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "✅ Conectado al backend. Escuchando notificaciones de fraude.")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "📩 Alerta recibida: $text")
                try {
                    val alerta = gson.fromJson(text, AlertaResultado::class.java)
                    updateOverlayUI(alerta)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error parseando JSON de alerta: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "❌ Error en WebSocket de alertas: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "⚠ WebSocket cerrado: $reason")
            }
        })
    }

    private fun simularAlertaLocal(texto: String) {
        val alertaSimulada = AlertaResultado(
            hablante = "llamante",
            texto = texto,
            nivel_riesgo = "medio",
            score = 50.0,
            recomendacion = "Simulación realizada localmente."
        )
        updateOverlayUI(alertaSimulada)
    }

    // Actualización dinámica de la ventana flotante con animaciones y efectos de sonido
    private fun updateOverlayUI(alerta: AlertaResultado) {
        Handler(Looper.getMainLooper()).post {
            val scorePercent = (alerta.score ?: 0.0).toInt()
            val nivelRiesgo = alerta.nivel_riesgo?.lowercase() ?: "bajo"

            tvScore.text = "$scorePercent%"

            // Reproducción de sonido según riesgo
            when (nivelRiesgo) {
                "alto", "peligroso" -> SoundFeedbackHelper.playFraudAlertSound(this@OverlayService)
                "medio" -> SoundFeedbackHelper.playWarningSound(this@OverlayService)
            }

            val (targetColorHex, titulo) = when (nivelRiesgo) {
                "alto", "peligroso" -> Pair("#B71C1C", "🛑 ALERTA DE FRAUDE")
                "medio" -> Pair("#E65100", "⚠️ Sospecha Detectada")
                else -> Pair("#1B5E20", "🛡️ Llamada Segura")
            }

            val hablanteEtiqueta = if (alerta.hablante == "llamante") "Llamante" else "Receptor"
            val mensajeDetalle = if (!alerta.texto.isNullOrBlank()) {
                "[$hablanteEtiqueta]: \"${alerta.texto}\"\n${alerta.recomendacion ?: ""}"
            } else {
                alerta.recomendacion ?: "Sin observaciones registradas."
            }

            tvShieldStatus.text = titulo
            tvRecommendation.text = mensajeDetalle

            val newColor = Color.parseColor(targetColorHex)

            // Transición de color en la UI
            val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, newColor)
            colorAnimation.duration = 400
            colorAnimation.addUpdateListener { animator ->
                val animatedColor = animator.animatedValue as Int
                (cardContainer.background as? GradientDrawable)?.setColor(animatedColor)
                (layoutBubbleMinimized.background as? GradientDrawable)?.setColor(animatedColor)
            }
            colorAnimation.start()
            currentColor = newColor

            // Si se detecta riesgo alto estando minimizado, se auto-expande
            if (isMinimized && (nivelRiesgo == "alto" || nivelRiesgo == "peligroso")) {
                restaurarTarjetaCompleta()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 Cerrando servicio y WebSocket...")
        webSocket?.close(1000, "Servicio finalizado")
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {}
            overlayView = null
        }
    }
}