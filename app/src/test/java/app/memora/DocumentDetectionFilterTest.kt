package app.memora

import app.memora.platform.camera.DocumentDetectionFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentDetectionFilterTest {

    // ==========================================
    // 1. BASELINE REGRESSION TESTS (EXISTING BEHAVIOR)
    // ==========================================

    @Test
    fun blankOrEmptyText_returnsFalse() {
        assertFalse(DocumentDetectionFilter.isDocumentCandidate("", 0))
        assertFalse(DocumentDetectionFilter.isDocumentCandidate("    ", 0))
        assertFalse(DocumentDetectionFilter.isDocumentCandidate("\n\t", 0))
    }

    @Test
    fun shortGibberishUnderThreshold_returnsFalse() {
        assertFalse(DocumentDetectionFilter.isDocumentCandidate("ab", 1))
        assertFalse(DocumentDetectionFilter.isDocumentCandidate("123", 1))
        assertFalse(DocumentDetectionFilter.isDocumentCandidate("ok", 0))
    }

    @Test
    fun multiLineDocumentWithAdequateCharacters_returnsTrue() {
        val rawText = "BESCOM BILL\nAmount: Rs 1450\nDue: 25/11/2026"
        assertTrue(DocumentDetectionFilter.isDocumentCandidate(rawText, 3))
    }

    @Test
    fun gasBillKeywords_returnsTrue() {
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Indane Gas Refill Due Date: 12/04/2026", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Adani Total Gas SCM units due 05/11", 1))
    }

    @Test
    fun expiryOrMfgKeywords_returnsTrue() {
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("EXP: 10/2027", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Mfg Date 01/24 Best Before 24 months", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Batch No: 9982 Exp 12/28", 1))
    }

    @Test
    fun milkOrGroceryKeywords_returnsTrue() {
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Amul Taaza Toned Milk use by 22/09", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Nandini GoodLife Milk Pack", 1))
    }

    @Test
    fun invoiceOrPaymentKeywords_returnsTrue() {
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Tax Invoice Total: ₹1,299", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Pay before 30th Sep to avoid penalty", 1))
    }

    @Test
    fun generalSufficientLengthText_returnsTrue() {
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Paracetamol Tablets 500mg", 1))
    }

    @Test
    fun crimpSealDetected_returnsTrueEvenWithZeroInkText() {
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("", 0, isCrimpDetected = true))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("   ", 0, isCrimpDetected = true))
    }

    // ==========================================
    // 2. PHASE A: SPARSE SINGLE-LINE DATE ACCEPTANCE
    // ==========================================

    @Test
    fun sparseSingleLineDate_withoutKeywords_returnsTrue() {
        // Can bottom, bottle neck, dot-matrix marking without labels
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("12/2026", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("25/08/2026", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("2026-11-30", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("08 2026", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("10.27", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("25-AUG-26", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("AUG 2026", 1))
    }

    // ==========================================
    // 3. PHASE A: SHORT EXPIRY / BEST BEFORE LABELS
    // ==========================================

    @Test
    fun shortExpiryOrBestBeforeMarkings_returnsTrue() {
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("BB 12/26", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("BB 08/2027", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("UB 10/26", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("USE BY 15/10/26", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("EXP 05/27", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("BEST BY 12/28", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("DOM 04/2025", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("PKD 03/26", 1))
    }

    // ==========================================
    // 4. PHASE A: NUMERIC-HEAVY AND BATCH MARKINGS
    // ==========================================

    @Test
    fun numericHeavyAndBatchCodes_returnsTrue() {
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("LOT 44B", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("BATCH #9921", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("B: 8812", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("L: 1402 12/26", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("SN: 991823", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("B.NO 102", 1))
    }

    // ==========================================
    // 5. PHASE A: COSMETIC PAO MARKINGS
    // ==========================================

    @Test
    fun cosmeticPeriodAfterOpening_returnsTrue() {
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("12M", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("6M", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("24M", 1))
    }

    // ==========================================
    // 6. PHASE A: UNRECOGNIZED / INTERNATIONAL PRODUCTS
    // ==========================================

    @Test
    fun unrecognizedProductText_withAdequateLength_returnsTrue() {
        // Legitimate packaging in other languages or unfamiliar brands
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Crème Hydratante Visage", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Cold Soda Beverage 330ml", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Organic Green Tea Leaves", 1))
    }

    // ==========================================
    // 7. PHASE A: FALSE-POSITIVE REJECTION CONTROLS
    // ==========================================

    @Test
    fun nonDocumentGibberish_underThreshold_returnsFalse() {
        // Isolated words without keywords, dates, or codes
        assertFalse(DocumentDetectionFilter.isDocumentCandidate("random", 1))
        assertFalse(DocumentDetectionFilter.isDocumentCandidate("1234", 1))
        assertFalse(DocumentDetectionFilter.isDocumentCandidate("abc def", 1))
        assertFalse(DocumentDetectionFilter.isDocumentCandidate("hello world", 1))
    }

    @Test
    fun noiseBelowThreeChars_returnsFalse() {
        assertFalse(DocumentDetectionFilter.isDocumentCandidate("a", 1))
        assertFalse(DocumentDetectionFilter.isDocumentCandidate("ok", 1))
        assertFalse(DocumentDetectionFilter.isDocumentCandidate("12", 1))
        assertFalse(DocumentDetectionFilter.isDocumentCandidate("--", 1))
        assertFalse(DocumentDetectionFilter.isDocumentCandidate("::", 1))
    }

    // ==========================================
    // 8. PHASE A: GENERIC OPERATIONAL EVIDENCE (NO BRANDS)
    // ==========================================

    @Test
    fun genericOperationalTokens_withoutBrandNames_qualify() {
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Due: 15th", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Warranty 2 Years", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Tax ₹500", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Total: $12.50", 1))
        assertTrue(DocumentDetectionFilter.isDocumentCandidate("Bill Period: Sep", 1))
    }
}
