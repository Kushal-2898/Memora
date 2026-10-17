package app.memora

import app.memora.platform.ocr.RapidOcrTextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class RapidOcrTextNormalizerTest {

    @Test
    fun `converts CJK full-width punctuation and letters to standard ASCII`() {
        val input = "ＥＸＰ：　２０２６／１２／３１"
        val expected = "EXP: 2026/12/31"
        assertEquals(expected, RapidOcrTextNormalizer.normalize(input))
    }

    @Test
    fun `disambiguates letter O to zero in numeric dates and years`() {
        assertEquals("2026", RapidOcrTextNormalizer.normalize("2O26"))
        assertEquals("2027", RapidOcrTextNormalizer.normalize("2o27"))
        assertEquals("30/12/2026", RapidOcrTextNormalizer.normalize("3O/12/2O26"))
        assertEquals("10/2025", RapidOcrTextNormalizer.normalize("1O/2O25"))
    }

    @Test
    fun `disambiguates lowercase l and uppercase I to 1 in dates`() {
        assertEquals("15/10/2026", RapidOcrTextNormalizer.normalize("15/l0/2026"))
        assertEquals("12/2026", RapidOcrTextNormalizer.normalize("l2/2026"))
        assertEquals("01/11/2026", RapidOcrTextNormalizer.normalize("01/I1/2026"))
    }

    @Test
    fun `standardizes anchor colons and whitespace`() {
        assertEquals("EXP: 25/12/2026", RapidOcrTextNormalizer.normalize("EXP  :   25/12/2026"))
        assertEquals("MFG: 01/2025", RapidOcrTextNormalizer.normalize("MFG : 01/2025"))
        assertEquals("USE BY: 10/10/2026", RapidOcrTextNormalizer.normalize("USE BY : 10/10/2026"))
    }

    @Test
    fun `handles empty or blank input safely`() {
        assertEquals("", RapidOcrTextNormalizer.normalize(""))
        assertEquals("", RapidOcrTextNormalizer.normalize("   "))
    }
}
