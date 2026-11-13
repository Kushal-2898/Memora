package app.memora

import app.memora.domain.extraction.RegulatoryExtractionResult
import app.memora.domain.extraction.RegulatoryPackagingRuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RegulatoryPackagingRuleEngineTest {

    private val engine = RegulatoryPackagingRuleEngine()
    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun resolveRelativeShelfLife_calculatesExactExpiryFromManufactureDate() {
        val rawText = """
            PURE HARVEST ORGANIC OATS
            MFG DATE: 15/10/2026
            BEST BEFORE 6 MONTHS FROM MANUFACTURE
            BATCH NO: 9482A
        """.trimIndent()

        val result = engine.evaluate(rawText, today)

        assertNotNull(result)
        // 15/10/2026 + 6 months = 15/04/2027
        assertEquals(LocalDate.of(2027, 4, 15), result.calculatedExpiryDate)
        assertTrue("Should be flagged as a relative calculation", result.isRelativeCalculation)
        assertEquals(LocalDate.of(2026, 10, 15), result.mfgDate)
    }

    @Test
    fun pastDateLabeledAsMfg_isDisqualifiedFromExpiryDate() {
        val rawText = """
            PREMIUM GREEN TEA
            PKD ON: 10/01/2025
            FSSAI LIC NO: 10014022000123
        """.trimIndent()

        val result = engine.evaluate(rawText, today)

        // The date 10/01/2025 is in the past and labeled as PKD, so it must NOT be marked as an upcoming expiry date
        assertNull("Past manufacturing date should not be returned as upcoming expiry", result.calculatedExpiryDate)
        assertEquals(LocalDate.of(2025, 1, 10), result.mfgDate)
    }

    @Test
    fun statutoryNoiseFilter_disqualifies14DigitLicenseAndBatchNumbers() {
        val textWithNoise = "FSSAI 10014022000123 BATCH NO 202611 GSTIN 29AAAAA0000A1Z5"
        
        val sanitized = engine.sanitizeRegulatoryNoise(textWithNoise)

        assertFalse("14-digit FSSAI number must be stripped", sanitized.contains("10014022000123"))
        assertFalse("Batch code must be stripped", sanitized.contains("202611"))
    }
}
