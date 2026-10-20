package com.vishguard.ai

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.time.LocalDate

/** Contadores diarios locales, separados por servidor y cuenta. */
class AnalysisMetrics(private val context: Context) {
    private val prefs = context.getSharedPreferences("vishguard_metrics", Context.MODE_PRIVATE)

    private fun scope(): String? {
        val user = SessionStore(context).user ?: return null
        val value = BackendConfig.url(context) + "|" + user.id
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun read(): AnalysisTotals {
        val today = LocalDate.now()
        val key = scope() ?: return AnalysisTotals(today)
        return read(key, today)
    }

    private fun read(key: String, today: LocalDate): AnalysisTotals {
        val date = runCatching { LocalDate.parse(prefs.getString(key + "_day", "")) }
            .getOrDefault(today)
        return AnalysisTotals(date, prefs.getInt(key + "_analyses", 0),
            prefs.getInt(key + "_threats", 0)).onDay(today)
    }

    // OverlayService llama este método desde el hilo principal una vez por respuesta válida.
    fun record(level: String?) {
        if (AnalysisRisk.fromLevel(level) == null) return
        val key = scope() ?: return
        val today = LocalDate.now()
        val totals = read(key, today).record(level, today)
        prefs.edit().putString(key + "_day", today.toString())
            .putInt(key + "_analyses", totals.analyses)
            .putInt(key + "_threats", totals.threats).apply()
    }

    fun observe(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun stopObserving(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
