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
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.vishguard.ai.utils.SoundFeedbackHelper

data class VishingResponse(
    val nivel_riesgo: String? = null,
    val score: Int? = 0,
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val TAG = "VishGuardHTTP"

    companion object {
        const val EXTRA_TEXTO = "extra_texto_llamada"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        setupOverlayWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val textoRecibido = intent?.getStringExtra(EXTRA_TEXTO)

        if (!textoRecibido.isNullOrBlank()) {
            enviarTextoParaAnalizar(textoRecibido)
        } else {
            updateOverlayUI(
                VishingResponse(
                    nivel_riesgo = "INICIAL",
                    score = 0,
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
        val urlServer = "http://192.168.160.195:8000/analizar-llamada"

        Handler(Looper.getMainLooper()).post {
            tvShieldStatus.text = "🔍 Analizando intención..."
            tvRecommendation.text = "Procesando mensaje con el cerebro de IA..."
        }

        val jsonBody = mapOf("texto" to textoLlamada)
        val body = gson.toJson(jsonBody).toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(urlServer)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                if (response.isSuccessful && responseData != null) {
                    try {
                        val resultado = gson.fromJson(responseData, VishingResponse::class.java)
                        if (resultado != null) {
                            updateOverlayUI(resultado)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parseo: ${e.message}")
                    }
                }
            }
        })
    }

    private fun updateOverlayUI(data: VishingResponse) {
        Handler(Looper.getMainLooper()).post {
            val score = data.score ?: 0
            val nivel = data.nivel_riesgo?.uppercase() ?: "INICIAL"

            tvScore.text = "$score%"

            when {
                score > 60 -> {
                    // 🛑 ¡Sonido de emergencia/alarma de fraude!
                    SoundFeedbackHelper.playFraudAlertSound(this@OverlayService)
                }
                score in 26..60 -> {
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
                score <= 25 -> Triple(
                    "#1B5E20",
                    "🛡️ Llamada Segura",
                    data.recomendacion ?: "Conversación sin indicadores de riesgo."
                )
                score in 26..60 -> Triple(
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
            tvRecommendation.text = recomendacion

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

            // Si está minimizada y surge un riesgo alto (>60%), se restaura sola para advertir
            if (isMinimized && score > 60) {
                restaurarTarjetaCompleta()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {}
            overlayView = null
        }
    }
}