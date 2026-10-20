package com.vishguard.ai.services

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
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
import com.vishguard.ai.BackendConfig
import com.vishguard.ai.SessionStore
import com.vishguard.ai.AnalysisMetrics
import com.vishguard.ai.AnalysisRisk
import com.vishguard.ai.network.AnalysisEvaluation as VishingResponse
import com.vishguard.ai.network.BackendApi
import com.vishguard.ai.network.BackendException
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit
import com.vishguard.ai.utils.SoundFeedbackHelper

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

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private var alertSocket: WebSocket? = null
    private val socketHandler = Handler(Looper.getMainLooper())
    private var stopping = false
    private val reconnect = Runnable { connectAlerts() }

    companion object {
        const val EXTRA_TEXTO = "extra_texto_llamada"
        @Volatile var isRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        setupOverlayWindow()
        isRunning = true
        connectAlerts()
    }

    private fun connectAlerts() {
        if (stopping) return
        val session = SessionStore(this)
        val token = session.token ?: return
        val phone = session.user?.phone_number ?: return
        val url = BackendConfig.url(this).toHttpUrl().newBuilder()
            .addPathSegments("ws/stream")
            .addQueryParameter("numero", phone)
            .addQueryParameter("token", token)
            .build().toString()
        alertSocket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socketHandler.post {
                    if (!stopping) tvRecommendation.text = "Conectado. Esperando alertas de la llamada."
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { gson.fromJson(text, VishingResponse::class.java) }
                    .getOrNull()?.let { updateOverlayUI(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // No registrar la URL: contiene el JWT de la sesión.
                if (response?.code in listOf(401, 403)) invalidSession()
                else scheduleReconnect()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (code == 1008) invalidSession() else scheduleReconnect()
            }
        })
    }

    private fun invalidSession() {
        socketHandler.post {
            if (!stopping) {
                socketHandler.removeCallbacks(reconnect)
                SessionStore(this).clear()
                tvShieldStatus.text = "Inicia sesión nuevamente"
                tvRecommendation.text = "Revisa la cuenta y el teléfono desde ajustes."
            }
        }
    }

    private fun scheduleReconnect() {
        socketHandler.post {
            if (!stopping) {
                tvRecommendation.text = "Sin conexión al servidor. Reintentando…"
                socketHandler.removeCallbacks(reconnect)
                socketHandler.postDelayed(reconnect, 5000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val textoRecibido = intent?.getStringExtra(EXTRA_TEXTO)

        if (!textoRecibido.isNullOrBlank()) {
            enviarTextoParaAnalizar(textoRecibido)
        } else {
            updateOverlayUI(
                VishingResponse(
                    nivel_riesgo = "INICIAL",
                    score = 0.0,
                    recomendacion = "Escaneando llamada en tiempo real..."
                )
            )
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("VishGuard AI Activo")
            .setContentText("Escaneando llamada en tiempo real...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
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

        // 1. Al presionar ✕: Minimiza a la burbuja circular derecha
        btnCloseOverlay.setOnClickListener {
            minimizarABurbuja()
        }

        // 2. Al tocar la burbuja circular: Se restaura la tarjeta completa
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

                // Aplicar el color actual a la burbuja circular
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

    fun enviarTextoParaAnalizar(textoLlamada: String) {
        val token = SessionStore(this).token
        if (token == null) {
            invalidSession()
            return
        }

        Handler(Looper.getMainLooper()).post {
            tvShieldStatus.text = "🔍 Analizando intención..."
            tvRecommendation.text = "Procesando mensaje con el cerebro de IA..."
        }

        BackendApi(BackendConfig.url(this), client).simulate(token, textoLlamada) { result ->
            result.fold(onSuccess = { updateOverlayUI(it) }, onFailure = { error ->
                if (error is BackendException && error.statusCode in listOf(401, 403)) {
                    invalidSession()
                } else {
                    showAnalysisError(error.message ?: "No se pudo guardar la simulación.")
                }
            })
        }
    }

    private fun updateOverlayUI(data: VishingResponse) {
        Handler(Looper.getMainLooper()).post {
            if (stopping) return@post
            // El contrato del backend expresa score en porcentaje (0 a 100).
            val score = (data.score ?: 0.0).toInt().coerceIn(0, 100)
            val nivel = data.nivel_riesgo?.uppercase() ?: "INICIAL"
            val risk = AnalysisRisk.fromLevel(nivel)
            if (risk == null && nivel != "INICIAL") return@post
            if (risk != null) {
                val rawScore = data.score
                // No contar respuestas incompletas, errores de red ni el estado inicial.
                if (rawScore == null || !rawScore.isFinite() || rawScore !in 0.0..100.0) return@post
                AnalysisMetrics(this@OverlayService).record(nivel)
            }

            tvScore.text = "$score%"

            when {
                risk == AnalysisRisk.HIGH -> {
                    // 🛑 ¡Sonido de emergencia/alarma de fraude!
                    SoundFeedbackHelper.playFraudAlertSound(this@OverlayService)
                }
                risk == AnalysisRisk.MEDIUM -> {
                    // ⚠️ Sonido de advertencia intermedia
                    SoundFeedbackHelper.playWarningSound(this@OverlayService)
                }
            }

            val (targetColorHex, titulo, recomendacion) = when {
                nivel == "INICIAL" && score == 0 -> Triple(
                    "#1B5E20",
                    "🛡️ VishGuard Activo",
                    "Escaneando llamada en tiempo real..."
                )
                risk == AnalysisRisk.LOW -> Triple(
                    "#1B5E20",
                    "🛡️ Llamada Segura",
                    data.recomendacion ?: "Conversación sin indicadores de riesgo."
                )
                risk == AnalysisRisk.MEDIUM -> Triple(
                    "#E65100",
                    "⚠️ Sospecha Detectada",
                    data.recomendacion ?: "Precaución: Valide la identidad del interlocutor."
                )
                else -> Triple(
                    "#B71C1C",
                    "🛑 ALERTA DE FRAUDE",
                    data.recomendacion ?: "¡Peligro! No proporcione claves, códigos SMS ni datos personales."
                )
            }

            tvShieldStatus.text = titulo
            tvRecommendation.text = recomendacion + if (data.es_simulacion && data.alerta_guardada == true)
                "\nSimulación guardada en tu historial." else ""

            val newColor = Color.parseColor(targetColorHex)

            // Transición suave de color para ambos elementos (tarjeta y burbuja)
            val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, newColor)
            colorAnimation.duration = 400
            colorAnimation.addUpdateListener { animator ->
                val animatedColor = animator.animatedValue as Int
                val bgCard = cardContainer.background as? GradientDrawable
                bgCard?.setColor(animatedColor)

                val bgBubble = layoutBubbleMinimized.background as? GradientDrawable
                bgBubble?.setColor(animatedColor)
            }
            colorAnimation.start()
            currentColor = newColor

            // Si está minimizada y surge una amenaza, se restaura sola para advertir.
            if (isMinimized && risk == AnalysisRisk.HIGH) {
                restaurarTarjetaCompleta()
            }
        }
    }

    private fun showAnalysisError(message: String) {
        socketHandler.post {
            if (!stopping) {
                tvShieldStatus.text = "Análisis no disponible"
                tvRecommendation.text = message
            }
        }
    }

    override fun onDestroy() {
        stopping = true
        isRunning = false
        socketHandler.removeCallbacksAndMessages(null)
        alertSocket?.cancel()
        client.dispatcher.cancelAll()
        super.onDestroy()
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {}
            overlayView = null
        }
    }
}
