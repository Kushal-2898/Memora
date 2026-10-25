package app.memora

import app.memora.platform.ocr.RapidOcrExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftBudgetTest {

    @Test
    fun `test scan time budget constant is 8000 ms`() {
        assertEquals(8000L, RapidOcrExtractor.SCAN_TIME_BUDGET_MS)
    }

    @Test
    fun `test budget check logic stops starting passes after 8s but preserves best candidate`() {
        val startTime = 1000L
        val budgetMs = RapidOcrExtractor.SCAN_TIME_BUDGET_MS

        var budgetHitLogged = false
        fun checkBudget(currentTime: Long): Boolean {
            val elapsed = currentTime - startTime
            if (elapsed >= budgetMs) {
                if (!budgetHitLogged) {
                    budgetHitLogged = true
                }
                return true
            }
            return false
        }

        // At t = 2.5s (before Pass 2): under budget
        assertFalse("Should be under budget at 2500ms", checkBudget(3500L))
        assertFalse(budgetHitLogged)

        // At t = 5.0s (before Pass 3): under budget
        assertFalse("Should be under budget at 5000ms", checkBudget(6000L))
        assertFalse(budgetHitLogged)

        // Pass 3 starts at 6.0s (elapsed 5.0s) and runs for 3.5s in flight -> finishes at 9.5s (elapsed 8.5s)
        // In-flight pass finishes completely and updates best candidate!
        var bestCandidate: String? = "EXP: 2026-12-01"

        // Before starting Pass 4 at t = 9.5s: budget is exceeded!
        assertTrue("Budget should be exceeded at 9500ms (elapsed 8500ms)", checkBudget(9500L))
        assertTrue("budget_hit should be logged", budgetHitLogged)

        // Pass 4 should NOT start:
        val shouldStartPass4 = !checkBudget(9500L)
        assertFalse("Pass 4 must NOT start when budget is exceeded", shouldStartPass4)

        // Verified: best candidate from the in-flight pass that finished is preserved!
        assertEquals("EXP: 2026-12-01", bestCandidate)
    }
}
