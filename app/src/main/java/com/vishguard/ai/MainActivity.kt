package com.vishguard.ai

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.vishguard.ai.services.OverlayService
import com.vishguard.ai.utils.SoundFeedbackHelper

class MainActivity : AppCompatActivity() {

    private var isServiceRunning = false
    private lateinit var btnToggleService: View
    private lateinit var viewGlowRing: View
    private lateinit var viewOrbitLight: View
    private lateinit var tvShieldIcon: TextView
    private lateinit var tvBtnAction: TextView
    private lateinit var tvBtnSub: TextView
    private lateinit var tvStatusInfo: TextView
    private lateinit var viewStatusDot: View
    private lateinit var cardBottomOptions: View
    private lateinit var btnSettings: TextView

    // Vistas de Métricas
    private lateinit var tvTotalCallsMetric: TextView
    private lateinit var tvThreatsBlockedMetric: TextView

    private var totalSesionesCount = 0
    private var amenazasBloqueadasCount = 0

    private var pulseAnimator: ObjectAnimator? = null
    private var orbitRotationAnimator: ObjectAnimator? = null
    private var dotPulseAnimator: ObjectAnimator? = null

    // Handler para giro recurrente del escudo (cada 4.5s)
    private val shieldSpinHandler = Handler(Looper.getMainLooper())
    private val shieldSpinInterval: Long = 4500
    private val shieldSpinRunnable = object : Runnable {
        override fun run() {
            if (isServiceRunning) {
                animarGiroEscudo()
                shieldSpinHandler.postDelayed(this, shieldSpinInterval)
            }
        }
    }

    companion object {
        private const val OVERLAY_PERMISSION_REQ_CODE = 1234
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggleService = findViewById(R.id.btnToggleService)
        viewGlowRing = findViewById(R.id.viewGlowRing)
        viewOrbitLight = findViewById(R.id.viewOrbitLight)
        tvShieldIcon = findViewById(R.id.tvShieldIcon)
        tvBtnAction = findViewById(R.id.tvBtnAction)
        tvBtnSub = findViewById(R.id.tvBtnSub)
        tvStatusInfo = findViewById(R.id.tvStatusInfo)
        viewStatusDot = findViewById(R.id.viewStatusDot)
        cardBottomOptions = findViewById(R.id.cardBottomOptions)
        btnSettings = findViewById(R.id.btnSettings)

        tvTotalCallsMetric = findViewById(R.id.tvTotalCallsMetric)
        tvThreatsBlockedMetric = findViewById(R.id.tvThreatsBlockedMetric)

        setupPulseAnimation()
        setupOrbitAnimation()

        btnToggleService.setOnClickListener {
            animarPulsacionBoton()
            if (isServiceRunning) {
                detenerOverlayService()
            } else {
                verificarPermisosYIniciar()
            }
        }

        btnSettings.setOnClickListener { view ->
            SoundFeedbackHelper.playSubtleTapSound(view)
            Toast.makeText(this, "VishGuard AI: Escuchando backend Twilio", Toast.LENGTH_SHORT).show()
        }

        cardBottomOptions.setOnClickListener { view ->
            SoundFeedbackHelper.playSubtleTapSound(view)
            mostrarBottomSheetOpciones()
        }
    }

    private fun verificarPermisosYIniciar() {
        // Únicamente requiere permiso de Overlay (SYSTEM_ALERT_WINDOW)
        if (!checkOverlayPermission()) {
            requestOverlayPermission()
            return
        }

        iniciarOverlayService()
    }

    private fun mostrarBottomSheetOpciones() {
        val bottomSheet = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_options, null)
        bottomSheet.setContentView(sheetView)

        val cardSimular = sheetView.findViewById<View>(R.id.cardOptionSimular)
        val cardHistorial = sheetView.findViewById<View>(R.id.cardOptionHistorial)

        cardSimular?.setOnClickListener {
            SoundFeedbackHelper.playSubtleTapSound(it)
            bottomSheet.dismiss()
            mostrarDialogoSimulacion()
        }

        cardHistorial?.setOnClickListener {
            SoundFeedbackHelper.playSubtleTapSound(it)
            bottomSheet.dismiss()
            Toast.makeText(
                this,
                "Auditoría: $totalSesionesCount alertas evaluadas ($amenazasBloqueadasCount amenazas)",
                Toast.LENGTH_LONG
            ).show()
        }

        bottomSheet.show()
    }

    private fun mostrarDialogoSimulacion() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_simular_frase)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etFrase = dialog.findViewById<EditText>(R.id.etDialogFrase)
        val btnCancelar = dialog.findViewById<Button>(R.id.btnDialogCancelar)
        val btnEnviar = dialog.findViewById<Button>(R.id.btnDialogEnviar)

        btnCancelar?.setOnClickListener {
            SoundFeedbackHelper.playSubtleTapSound(it)
            dialog.dismiss()
        }

        btnEnviar?.setOnClickListener {
            SoundFeedbackHelper.playSubtleTapSound(it)
            val frase = etFrase?.text.toString().trim()
            if (!isServiceRunning) {
                Toast.makeText(this, "Activa primero la protección central", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (frase.isNotEmpty()) {
                totalSesionesCount++
                tvTotalCallsMetric.text = "$totalSesionesCount Analizadas"

                val intent = Intent(this, OverlayService::class.java).apply {
                    putExtra(OverlayService.EXTRA_TEXTO, frase)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }

                dialog.dismiss()
            } else {
                Toast.makeText(this, "Escribe una frase antes de analizar", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun setupPulseAnimation() {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.35f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.35f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.75f, 0.05f)

        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(viewGlowRing, scaleX, scaleY, alpha).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        dotPulseAnimator = ObjectAnimator.ofFloat(viewStatusDot, View.ALPHA, 1.0f, 0.2f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
    }

    private fun setupOrbitAnimation() {
        orbitRotationAnimator = ObjectAnimator.ofFloat(viewOrbitLight, View.ROTATION, 0f, 360f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }

    private fun animarPulsacionBoton() {
        btnToggleService.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(120)
            .withEndAction {
                btnToggleService.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(160)
                    .setInterpolator(OvershootInterpolator(2.5f))
                    .start()
            }
            .start()
    }

    private fun animarGiroEscudo() {
        val rotateY = ObjectAnimator.ofFloat(tvShieldIcon, View.ROTATION_Y, 0f, 360f).apply {
            duration = 750
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleX = ObjectAnimator.ofFloat(tvShieldIcon, View.SCALE_X, 1f, 1.25f, 1f).apply {
            duration = 750
            interpolator = OvershootInterpolator(1.8f)
        }
        val scaleY = ObjectAnimator.ofFloat(tvShieldIcon, View.SCALE_Y, 1f, 1.25f, 1f).apply {
            duration = 750
            interpolator = OvershootInterpolator(1.8f)
        }

        AnimatorSet().apply {
            playTogether(rotateY, scaleX, scaleY)
            start()
        }
    }

    private fun iniciarOverlayService() {
        val intent = Intent(this, OverlayService::class.java)

        SoundFeedbackHelper.playActivationSound(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServiceRunning = true

        tvBtnAction.text = "DESACTIVAR"
        tvBtnSub.text = "MONITOREANDO"
        tvBtnSub.setTextColor(Color.parseColor("#EF9A9A"))
        tvStatusInfo.text = "Estado: Escuchando eventos del servidor"
        tvStatusInfo.setTextColor(Color.parseColor("#4CAF50"))

        viewStatusDot.setBackgroundColor(Color.parseColor("#4CAF50"))
        dotPulseAnimator?.start()

        viewGlowRing.visibility = View.VISIBLE
        pulseAnimator?.start()

        viewOrbitLight.visibility = View.VISIBLE
        orbitRotationAnimator?.start()

        animarGiroEscudo()
        shieldSpinHandler.postDelayed(shieldSpinRunnable, shieldSpinInterval)
    }

    private fun detenerOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        SoundFeedbackHelper.playDeactivationSound(this)

        stopService(intent)
        isServiceRunning = false

        shieldSpinHandler.removeCallbacks(shieldSpinRunnable)

        tvShieldIcon.rotationY = 0f
        tvShieldIcon.scaleX = 1f
        tvShieldIcon.scaleY = 1f

        tvBtnAction.text = "ACTIVAR"
        tvBtnSub.text = "PROTECCIÓN"
        tvBtnSub.setTextColor(Color.parseColor("#90CAF9"))
        tvStatusInfo.text = "Estado: Inactivo"
        tvStatusInfo.setTextColor(Color.parseColor("#546E7A"))

        dotPulseAnimator?.cancel()
        viewStatusDot.alpha = 1.0f
        viewStatusDot.setBackgroundColor(Color.parseColor("#546E7A"))

        pulseAnimator?.cancel()
        viewGlowRing.scaleX = 1f
        viewGlowRing.scaleY = 1f
        viewGlowRing.visibility = View.INVISIBLE

        orbitRotationAnimator?.cancel()
        viewOrbitLight.rotation = 0f
        viewOrbitLight.visibility = View.INVISIBLE
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (checkOverlayPermission()) {
                verificarPermisosYIniciar()
            } else {
                Toast.makeText(this, "Permiso de ventana flotante denegado.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shieldSpinHandler.removeCallbacks(shieldSpinRunnable)
        dotPulseAnimator?.cancel()
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Toast.makeText(
                this,
                "Por favor, concede el permiso para mostrar la alerta sobre otras aplicaciones.",
                Toast.LENGTH_LONG
            ).show()

            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
        }
    }
}