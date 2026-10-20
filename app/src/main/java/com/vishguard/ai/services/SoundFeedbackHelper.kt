package com.vishguard.ai.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.SoundEffectConstants
import android.view.View

object SoundFeedbackHelper {

    private var toneGen: ToneGenerator? = null

    init {
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 90)
        } catch (_: Exception) {}
    }

    // 🔊 Función que te está pidiendo MainActivity
    fun playSubtleTapSound(view: View) {
        try {
            view.playSoundEffect(SoundEffectConstants.CLICK)
            vibrateQuick(view.context, 12, 70)
        } catch (_: Exception) {}
    }

    fun playActivationSound(context: Context) {
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 80)
            vibrateQuick(context, 40, 180)
        } catch (_: Exception) {}
    }

    fun playDeactivationSound(context: Context) {
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 60)
            vibrateQuick(context, 25, 100)
        } catch (_: Exception) {}
    }

    fun playWarningSound(context: Context) {
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 140)
            vibratePattern(context, longArrayOf(0, 60, 60, 60), intArrayOf(0, 190, 0, 190))
        } catch (_: Exception) {}
    }

    fun playFraudAlertSound(context: Context) {
        try {
            toneGen?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 350)
            vibratePattern(context, longArrayOf(0, 180, 80, 260), intArrayOf(0, 255, 0, 255))
        } catch (_: Exception) {}
    }

    private fun vibrateQuick(context: Context, durationMs: Long, amplitude: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, amplitude)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
    }

    private fun vibratePattern(context: Context, timings: LongArray, amplitudes: IntArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(timings, -1)
                }
            }
        } catch (_: Exception) {}
    }
}