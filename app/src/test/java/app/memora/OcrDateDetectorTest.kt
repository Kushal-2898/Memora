package app.memora

import app.memora.platform.ocr.OcrDateDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrDateDetectorTest {

    @Test
    fun `detects standard EXP date formats`() {
        assertTrue(OcrDateDetector.hasAnchoredDate("EXP: 25/12/2026"))
        assertTrue(OcrDateDetector.hasAnchoredDate("EXPIRY 10-11-2027"))
        assertTrue(OcrDateDetector.hasAnchoredDate("EXP 12/26"))
        assertTrue(OcrDateDetector.hasAnchoredDate("EXP: DEC 2026"))
        assertTrue(OcrDateDetector.hasAnchoredDate("EXP 15 OCT 2026"))
    }

    @Test
    fun `detects BEST BEFORE and USE BY anchors`() {
        assertTrue(OcrDateDetector.hasAnchoredDate("BEST BEFORE 30/06/2027"))
        assertTrue(OcrDateDetector.hasAnchoredDate("USE BY: 05/01/2028"))
        assertTrue(OcrDateDetector.hasAnchoredDate("BB: 15/08/2026"))
    }

    @Test
    fun `detects manufacturing and packaging stamps`() {
        assertTrue(OcrDateDetector.hasAnchoredDate("MFG: 01/2025 EXP: 12/2026"))
        assertTrue(OcrDateDetector.hasAnchoredDate("PKD 10/2025"))
        assertTrue(OcrDateDetector.hasAnchoredDate("BATCH 492 MFG 15/03/2025"))
    }

    @Test
    fun `returns false for text without date digits or anchors`() {
        assertFalse(OcrDateDetector.hasAnchoredDate(""))
        assertFalse(OcrDateDetector.hasAnchoredDate("   "))
        assertFalse(OcrDateDetector.hasAnchoredDate("Indane Cooking Gas 14.2kg"))
        assertFalse(OcrDateDetector.hasAnchoredDate("BESCOM Electricity Board Bengaluru"))
        assertFalse(OcrDateDetector.hasAnchoredDate("Store in a cool dry place"))
    }

    @Test
    fun `returns true for bare future date years without anchor`() {
        assertTrue(OcrDateDetector.hasAnchoredDate("Net Wt 500g 15/10/2026"))
    }
}
