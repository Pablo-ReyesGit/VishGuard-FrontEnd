package com.vishguard.ai

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AnalysisTotalsTest {
    private val today = LocalDate.of(2026, 10, 16)

    @Test fun dangerousResultCountsOneAnalysisAndOneThreat() {
        val totals = AnalysisTotals(today).record("PELIGROSO", today)
        assertEquals(1, totals.analyses)
        assertEquals(1, totals.threats)
    }

    @Test fun safeAndMediumResultsCountAnalysesWithoutInflatingThreats() {
        val totals = AnalysisTotals(today).record("BAJO", today).record("MEDIO", today)
            .record("FRAUDE", today)
        assertEquals(3, totals.analyses)
        assertEquals(1, totals.threats)
    }

    @Test fun initialStateAndInvalidResponsesDoNotCount() {
        val totals = AnalysisTotals(today)
        for (level in listOf(null, "", "INICIAL", "ERROR")) {
            assertEquals(totals, totals.record(level, today))
        }
    }

    @Test fun returningToScreenDoesNotChangeTotals() {
        val totals = AnalysisTotals(today).record("PELIGROSO", today)
        assertEquals(totals, totals.onDay(today))
    }

    @Test fun nextDayResetsBeforeCountingNewResult() {
        val yesterday = today.minusDays(1)
        val previous = AnalysisTotals(yesterday, analyses = 5, threats = 3)
        assertEquals(AnalysisTotals(today), previous.onDay(today))
        assertEquals(AnalysisTotals(today, analyses = 1, threats = 1),
            previous.record("PELIGROSO", today))
    }
}
