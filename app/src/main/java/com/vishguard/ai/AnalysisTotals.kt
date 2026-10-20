package com.vishguard.ai

import java.time.LocalDate
import java.util.Locale

enum class AnalysisRisk {
    LOW, MEDIUM, HIGH;

    companion object {
        fun fromLevel(level: String?): AnalysisRisk? = when (level?.uppercase(Locale.ROOT)) {
            "BAJO" -> LOW
            "MEDIO" -> MEDIUM
            "PELIGROSO", "FRAUDE" -> HIGH
            else -> null
        }
    }
}

data class AnalysisTotals(
    val day: LocalDate,
    val analyses: Int = 0,
    val threats: Int = 0
) {
    fun onDay(today: LocalDate): AnalysisTotals =
        if (day == today) this else AnalysisTotals(today)

    fun record(level: String?, today: LocalDate): AnalysisTotals {
        val current = onDay(today)
        val risk = AnalysisRisk.fromLevel(level) ?: return current
        return current.copy(
            analyses = current.analyses + 1,
            threats = current.threats + if (risk == AnalysisRisk.HIGH) 1 else 0
        )
    }
}
