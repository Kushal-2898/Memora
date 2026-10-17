package app.memora

import app.memora.domain.extraction.DeterministicMemoryExtractor
import app.memora.domain.model.EventType
import app.memora.domain.scheduler.ClockProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DeterministicMemoryExtractorTest {

    private lateinit var extractor: DeterministicMemoryExtractor
    private val fixedInstant = Instant.parse("2026-09-04T10:00:00Z")
    private val fixedZone = ZoneId.of("UTC")

    private val fixedClockProvider = object : ClockProvider {
        override fun now(): Instant = fixedInstant
        override fun today(zoneId: ZoneId): LocalDate = fixedInstant.atZone(zoneId).toLocalDate()
    }

    @Before
    fun setUp() {
        extractor = DeterministicMemoryExtractor(
            clockProvider = fixedClockProvider,
            zoneId = fixedZone,
        )
    }

    @Test
    fun `MFG 09-2025 plus EXP 08-2027 picks EXPIRATION 2027-08`() = runTest {
        val rawText = "BATCH #9921\nMFG 09/2025\nEXP 08/2027"

        val result = extractor.extractCandidate(rawText)

        assertTrue(result.isSuccess)
        val candidate = result.getOrThrow()
        assertEquals(EventType.EXPIRATION, candidate.suggestedEventType)
        assertNotNull(candidate.suggestedEventDate)
        assertEquals(2027, candidate.suggestedEventDate?.year)
        assertEquals(8, candidate.suggestedEventDate?.monthValue)
        assertEquals(DeterministicMemoryExtractor.CONFIDENCE_HIGH, candidate.confidence)
    }

    @Test
    fun `MFG and EXP on same line picks EXPIRATION 2027-08`() = runTest {
        val rawText = "MFG 09/2025 EXP 08/2027"

        val result = extractor.extractCandidate(rawText)

        assertTrue(result.isSuccess)
        val candidate = result.getOrThrow()
        assertEquals(EventType.EXPIRATION, candidate.suggestedEventType)
        assertNotNull(candidate.suggestedEventDate)
        assertEquals(2027, candidate.suggestedEventDate?.year)
        assertEquals(8, candidate.suggestedEventDate?.monthValue)
        assertEquals(DeterministicMemoryExtractor.CONFIDENCE_HIGH, candidate.confidence)
    }

    @Test
    fun `DUE DATE 20-08-2025 BESCOM identifies DUE_DATE and Electricity Bill title`() = runTest {
        val rawText = "DUE DATE 20/08/2025 BESCOM"

        val result = extractor.extractCandidate(rawText)

        assertTrue(result.isSuccess)
        val candidate = result.getOrThrow()
        assertEquals(EventType.DUE_DATE, candidate.suggestedEventType)
        assertEquals(LocalDate.of(2025, 8, 20), candidate.suggestedEventDate)
        assertEquals("Electricity Bill", candidate.suggestedTitle)
        assertEquals(DeterministicMemoryExtractor.CONFIDENCE_HIGH, candidate.confidence)
    }

    @Test
    fun `no context assigns confidence LOW and eventType OTHER`() = runTest {
        val rawText = "Random meeting note 20/08/2025 without labels"

        val result = extractor.extractCandidate(rawText)

        assertTrue(result.isSuccess)
        val candidate = result.getOrThrow()
        assertEquals(EventType.OTHER, candidate.suggestedEventType)
        assertEquals(LocalDate.of(2025, 8, 20), candidate.suggestedEventDate)
        assertEquals(DeterministicMemoryExtractor.CONFIDENCE_LOW, candidate.confidence)
    }

    @Test
    fun `multiple dates with no labels returns highest plausibility candidate`() = runTest {
        // First date is in the past (2024), second date is in the future (2026-12-25)
        val rawText = "Started 10/01/2024 and continues on 25/12/2026"

        val result = extractor.extractCandidate(rawText)

        assertTrue(result.isSuccess)
        val candidate = result.getOrThrow()
        assertEquals(LocalDate.of(2026, 12, 25), candidate.suggestedEventDate)
        assertEquals(EventType.OTHER, candidate.suggestedEventType)
        assertEquals(DeterministicMemoryExtractor.CONFIDENCE_LOW, candidate.confidence)
    }

    @Test
    fun `best before and use by map to BEST_BEFORE`() = runTest {
        val text1 = "BEST BEFORE 15/10/2026"
        val res1 = extractor.extractCandidate(text1).getOrThrow()
        assertEquals(EventType.BEST_BEFORE, res1.suggestedEventType)
        assertEquals(LocalDate.of(2026, 10, 15), res1.suggestedEventDate)

        val text2 = "BB 08/2027"
        val res2 = extractor.extractCandidate(text2).getOrThrow()
        assertEquals(EventType.BEST_BEFORE, res2.suggestedEventType)
        assertEquals(2027, res2.suggestedEventDate?.year)
        assertEquals(8, res2.suggestedEventDate?.monthValue)

        val text3 = "USE BY 01/05/2027"
        val res3 = extractor.extractCandidate(text3).getOrThrow()
        assertEquals(EventType.BEST_BEFORE, res3.suggestedEventType)
        assertEquals(LocalDate.of(2027, 5, 1), res3.suggestedEventDate)
    }

    @Test
    fun `warranty and return deadlines map accurately`() = runTest {
        val text1 = "WARRANTY UNTIL 2028-06-30"
        val res1 = extractor.extractCandidate(text1).getOrThrow()
        assertEquals(EventType.WARRANTY_END, res1.suggestedEventType)
        assertEquals(LocalDate.of(2028, 6, 30), res1.suggestedEventDate)

        val text2 = "RETURN BY 15/09/2026"
        val res2 = extractor.extractCandidate(text2).getOrThrow()
        assertEquals(EventType.RETURN_DEADLINE, res2.suggestedEventType)
        assertEquals(LocalDate.of(2026, 9, 15), res2.suggestedEventDate)
    }

    @Test
    fun `utility biller titles are accurately detected`() = runTest {
        val airtel = extractor.extractCandidate("AIRTEL BROADBAND DUE BY 15/09/2026").getOrThrow()
        assertEquals("Internet/Phone Bill", airtel.suggestedTitle)
        assertEquals(EventType.DUE_DATE, airtel.suggestedEventType)

        val gas = extractor.extractCandidate("INDANE LPG CYLINDER DUE DATE 25/09/2026").getOrThrow()
        assertEquals("Gas Bill", gas.suggestedTitle)
        assertEquals(EventType.DUE_DATE, gas.suggestedEventType)

        val water = extractor.extractCandidate("BWSSB WATER SUPPLY BILL PAY BY 30/09/2026").getOrThrow()
        assertEquals("Water Bill", water.suggestedTitle)
        assertEquals(EventType.DUE_DATE, water.suggestedEventType)
    }

    @Test
    fun `month names in MMM YYYY format are parsed properly`() = runTest {
        val text = "EXPIRY DATE AUG 2027"
        val result = extractor.extractCandidate(text).getOrThrow()
        assertEquals(EventType.EXPIRATION, result.suggestedEventType)
        assertEquals(2027, result.suggestedEventDate?.year)
        assertEquals(8, result.suggestedEventDate?.monthValue)
    }

    @Test
    fun `text with only manufacturing date discards date and produces zero confidence`() = runTest {
        val text = "BATCH #102 MFG 09/2025"
        val result = extractor.extractCandidate(text).getOrThrow()
        assertEquals(null, result.suggestedEventDate)
        assertEquals(0f, result.confidence)
        assertEquals(EventType.OTHER, result.suggestedEventType)
    }

    // ==========================================
    // PHASE 3C — TARGETED TESTS 1 THROUGH 10
    // ==========================================

    @Test
    fun `TEST 1 - Line EXP colon 12-2026 extracts expiry 2026-12-01`() {
        val result = extractor.extractLocalResult("EXP: 12/2026")
        assertEquals(LocalDate.of(2026, 12, 1), result.selectedEventDate)
        assertEquals(EventType.EXPIRATION, result.eventType)
    }

    @Test
    fun `TEST 2 - Elements EXP colon and 12-2026 joined into line extracts expiry 2026-12-01`() {
        val line = app.memora.platform.ocr.TextLineInfo(
            text = "EXP: 12/2026",
            elements = listOf(
                app.memora.platform.ocr.TextElementInfo(text = "EXP:"),
                app.memora.platform.ocr.TextElementInfo(text = "12/2026")
            )
        )
        val block = app.memora.platform.ocr.TextBlockInfo(
            text = "EXP: 12/2026",
            lines = listOf(line)
        )
        val result = extractor.extractLocalResult(listOf(block))
        assertEquals(LocalDate.of(2026, 12, 1), result.selectedEventDate)
    }

    @Test
    fun `TEST 3 - Line EXP 12-2026 extracts expiry 2026-12-01`() {
        val result = extractor.extractLocalResult("EXP 12/2026")
        assertEquals(LocalDate.of(2026, 12, 1), result.selectedEventDate)
    }

    @Test
    fun `TEST 4 - Line EXP-12-2026 extracts expiry 2026-12-01`() {
        val result = extractor.extractLocalResult("EXP-12/2026")
        assertEquals(LocalDate.of(2026, 12, 1), result.selectedEventDate)
    }

    @Test
    fun `TEST 5 - Line MFG colon 01-2024 extracts manufacturingDate 2024-01-01`() {
        val result = extractor.extractLocalResult("MFG: 01/2024")
        assertEquals("2024-01-01", result.fields["MFG Date"])
        assertEquals(null, result.selectedEventDate)
    }

    @Test
    fun `TEST 6 - Line PARACETAMOL 500MG extracts existing title behavior`() {
        val result = extractor.extractLocalResult("PARACETAMOL 500MG")
        assertEquals("Medicine", result.title)
    }

    @Test
    fun `TEST 7 - Three-line input PARACETAMOL MFG and EXP extracts all fields`() {
        val input = "PARACETAMOL 500MG\nMFG: 01/2024\nEXP: 12/2026"
        val result = extractor.extractLocalResult(input)
        assertEquals("Medicine", result.title)
        assertEquals("2024-01-01", result.fields["MFG Date"])
        assertEquals(LocalDate.of(2026, 12, 1), result.selectedEventDate)
    }

    @Test
    fun `TEST 8 - RapidOCR-shaped structure with single element extracts expiry 2026-12-01`() {
        val line = app.memora.platform.ocr.TextLineInfo(
            text = "EXP: 12/2026",
            elements = listOf(
                app.memora.platform.ocr.TextElementInfo(text = "EXP: 12/2026")
            )
        )
        val block = app.memora.platform.ocr.TextBlockInfo(
            text = "EXP: 12/2026",
            lines = listOf(line)
        )
        val result = extractor.extractLocalResult(listOf(block))
        assertEquals(LocalDate.of(2026, 12, 1), result.selectedEventDate)
    }

    @Test
    fun `TEST 9 - ML-Kit-shaped structure with two elements extracts expiry 2026-12-01`() {
        val line = app.memora.platform.ocr.TextLineInfo(
            text = "EXP: 12/2026",
            elements = listOf(
                app.memora.platform.ocr.TextElementInfo(text = "EXP:"),
                app.memora.platform.ocr.TextElementInfo(text = "12/2026")
            )
        )
        val block = app.memora.platform.ocr.TextBlockInfo(
            text = "EXP: 12/2026",
            lines = listOf(line)
        )
        val result = extractor.extractLocalResult(listOf(block))
        assertEquals(LocalDate.of(2026, 12, 1), result.selectedEventDate)
    }

    @Test
    fun `TEST 10 - Empty OCR result does not crash or fabricate memory`() {
        val result = extractor.extractLocalResult(emptyList<app.memora.platform.ocr.TextBlockInfo>())
        assertEquals(null, result.selectedEventDate)
        assertEquals(null, result.title)
        assertEquals(0f, result.confidence, 0.001f)
    }

    // ==========================================
    // SEMANTIC CANDIDATE RESOLUTION TESTS
    // ==========================================

    @Test
    fun `bill with 2 dates resolves to DUE_DATE without ambiguity`() {
        // Today is fixed at 2026-09-04
        val billText = "ELECTRIC INVOICE\nBILL DATE: 02/09/2026\nDUE DATE: 17/09/2026\nAMOUNT DUE: $125.00"
        val result = extractor.extractLocalResult(billText)

        assertFalse("Invoice with Bill Date and Due Date must not be marked ambiguous", result.isAmbiguous)
        assertEquals(LocalDate.of(2026, 9, 17), result.selectedEventDate)
        assertEquals(EventType.DUE_DATE, result.eventType)
        assertEquals("2026-09-02", result.fields["Bill Date"])
        assertEquals("2026-09-17", result.fields["Due Date"])

        // Verify candidates preservation and rich attributes
        assertEquals(2, result.dateCandidates.size)
        val dueCandidate = result.dateCandidates.first { it.eventDate == LocalDate.of(2026, 9, 17) }
        val billCandidate = result.dateCandidates.first { it.eventDate == LocalDate.of(2026, 9, 2) }

        assertTrue(dueCandidate.evidenceText.contains("DUE DATE"))
        assertTrue(billCandidate.evidenceText.contains("BILL DATE"))
        assertEquals(app.memora.domain.extraction.DateRole.ACTIONABLE_DUE_DATE, dueCandidate.role)
        assertEquals(app.memora.domain.extraction.DateRole.HISTORICAL_ISSUANCE_BILL, billCandidate.role)
        assertTrue("Due date total score must exceed bill date score", dueCandidate.scoreComponents.totalScore > billCandidate.scoreComponents.totalScore)
        assertNotNull(result.explanation)
    }

    @Test
    fun `bill with 3 dates resolves to DUE_DATE without ambiguity`() {
        val billText = "WATER UTILITY BILL\nSTATEMENT DATE: 01/09/2026\nMETER READING DATE: 05/09/2026\nPAYMENT DUE: 25/09/2026\nCONSUMER NO: 99182"
        val result = extractor.extractLocalResult(billText)

        assertFalse("3-date bill with Due Date must not be ambiguous", result.isAmbiguous)
        assertEquals(LocalDate.of(2026, 9, 25), result.selectedEventDate)
        assertEquals(EventType.DUE_DATE, result.eventType)
        assertEquals("2026-09-25", result.fields["Due Date"])
        assertEquals(3, result.dateCandidates.size)
    }

    @Test
    fun `product with MFG and EXP resolves to EXPIRATION and preserves MFG in fields`() {
        val productText = "MEDICINAL SYRUP\nBATCH NO: 8812\nMFG: 15/01/2024\nEXP: 20/12/2026"
        val result = extractor.extractLocalResult(productText)

        assertFalse("Product with MFG and EXP must not be ambiguous", result.isAmbiguous)
        assertEquals(LocalDate.of(2026, 12, 20), result.selectedEventDate)
        assertEquals(EventType.EXPIRATION, result.eventType)
        assertEquals("2024-01-15", result.fields["MFG Date"])
        assertEquals("2026-12-20", result.fields["Expiry Date"])
        assertEquals("8812", result.fields["Batch"])

        // Both candidates preserved
        assertEquals(2, result.dateCandidates.size)
        val expCandidate = result.dateCandidates.first { it.eventDate == LocalDate.of(2026, 12, 20) }
        val mfgCandidate = result.dateCandidates.first { it.eventDate == LocalDate.of(2024, 1, 15) }

        assertEquals(app.memora.domain.extraction.DateRole.ACTIONABLE_EXPIRATION, expCandidate.role)
        assertEquals(app.memora.domain.extraction.DateRole.HISTORICAL_MANUFACTURE, mfgCandidate.role)
        assertEquals(app.memora.domain.extraction.TemporalStatus.FUTURE, expCandidate.temporalStatus)
        assertEquals(app.memora.domain.extraction.TemporalStatus.PAST, mfgCandidate.temporalStatus)
    }

    @Test
    fun `warranty with purchase date and warranty end resolves to WARRANTY_END`() {
        val warrantyText = "PRODUCT WARRANTY CERTIFICATE\nSERIAL NO: ABC-1234\nPURCHASE DATE: 12/04/2024\nWARRANTY UNTIL: 12/04/2028"
        val result = extractor.extractLocalResult(warrantyText)

        assertFalse("Warranty with Purchase and End dates must not be ambiguous", result.isAmbiguous)
        assertEquals(LocalDate.of(2028, 4, 12), result.selectedEventDate)
        assertEquals(EventType.WARRANTY_END, result.eventType)
        assertEquals("2024-04-12", result.fields["Purchase Date"])
        assertEquals("2028-04-12", result.fields["Warranty"])

        val warrantyCandidate = result.dateCandidates.first { it.eventDate == LocalDate.of(2028, 4, 12) }
        val purchaseCandidate = result.dateCandidates.first { it.eventDate == LocalDate.of(2024, 4, 12) }
        assertEquals(app.memora.domain.extraction.DateRole.ACTIONABLE_WARRANTY_END, warrantyCandidate.role)
        assertEquals(app.memora.domain.extraction.DateRole.HISTORICAL_PURCHASE, purchaseCandidate.role)
    }

    @Test
    fun `truly ambiguous unlabeled future dates mark result ambiguous with null selected date`() {
        // Two unlabeled future dates competing with equal plausibility
        val ambiguousText = "General meeting proposed for 10/10/2026 or alternative date 25/12/2026"
        val result = extractor.extractLocalResult(ambiguousText)

        assertTrue("Unlabeled competing future dates must be marked ambiguous", result.isAmbiguous)
        assertNull("Ambiguous result must have null selectedEventDate", result.selectedEventDate)
        assertEquals(2, result.dateCandidates.size)
        assertNotNull(result.explanation)
        assertTrue("Explanation must mention ambiguity", result.explanation!!.contains("Ambiguous"))
    }

    @Test
    fun `separator L in 07L2028 normalizes to 07-2028 and extracts EXPIRATION`() {
        val blisterText = "BCL25535\n08/2025\n07L2028"
        val result = extractor.extractLocalResult(blisterText)

        assertNotNull("Should extract date from 07L2028", result.selectedEventDate)
        assertEquals(LocalDate.of(2028, 7, 1), result.selectedEventDate)
    }

    @Test
    fun `batch prefix collision B NO 42822-06-2026 decouples and extracts 2026-06-22`() {
        val bottleText = "B.NO: 42822/06/2026 ENJOY RESPONSIBLY"
        val result = extractor.extractLocalResult(bottleText)

        assertNotNull("Should extract decoupled date", result.selectedEventDate)
        assertEquals(LocalDate.of(2026, 6, 22), result.selectedEventDate)
    }

    @Test
    fun `Dolo 650 with dot-matrix DEC OCR noise DE question marks extracts 2029-12-01`() {
        val doloText = "DO8S4352 MFG. JAN, 2026 EXP. DE??(2029"
        val result = extractor.extractLocalResult(doloText)

        assertNotNull("Should extract expiration from Dolo 650 text", result.selectedEventDate)
        assertEquals(LocalDate.of(2029, 12, 1), result.selectedEventDate)
        assertEquals(EventType.EXPIRATION, result.eventType)
    }

    @Test
    fun `balm dot matrix code u4 dot 202d extracts 2028-04-01`() {
        val balmText = "Exp. Date\nu4.202d"
        val result = extractor.extractLocalResult(balmText)

        assertNotNull("Should extract date from balm dot-matrix code", result.selectedEventDate)
        assertEquals(LocalDate.of(2028, 4, 1), result.selectedEventDate)
    }

    @Test
    fun `crimp code 4AR25B28 normalizes to March 2028 expiration`() {
        val crimpText = "4AR25B28\nACTIVATED CHARCOAL"
        val result = extractor.extractLocalResult(crimpText)

        assertNotNull("Should extract expiration from crimp code", result.selectedEventDate)
        assertEquals(LocalDate.of(2028, 3, 1), result.selectedEventDate)
    }

    @Test
    fun `alcohol bottle with double dot in 29 dot dot 07 dot 2026 extracts 2026-07-29`() {
        val vodkaText = "DATE OF PACKAGING:\n02A6/29..07.2026/1310.00"
        val result = extractor.extractLocalResult(vodkaText)

        assertNotNull("Should extract packaging date from bottle text", result.selectedEventDate)
        assertEquals(LocalDate.of(2026, 7, 29), result.selectedEventDate)
    }

    @Test
    fun `shelf life with 9 Months From Manufacturing and Mtd date computes expiration`() {
        val snackText = "Mtd. date: 10-07-2026\n9 Months From\nManufacturing"
        val result = extractor.extractLocalResult(snackText)

        assertNotNull("Should compute expiration date from shelf life", result.selectedEventDate)
        assertEquals(LocalDate.of(2027, 4, 10), result.selectedEventDate)
        assertEquals(EventType.BEST_BEFORE, result.eventType)
    }

    @Test
    fun `ramen noodles 5-digit crimp stamp 37526 with quote extracts 2026-05-27`() {
        val ramenText = "37526\"\nODdles\nPkg. Date | Use By"
        val result = extractor.extractLocalResult(ramenText)

        assertNotNull("Should extract date from 5-digit crimp stamp", result.selectedEventDate)
        assertEquals(LocalDate.of(2026, 5, 27), result.selectedEventDate)
    }

    @Test
    fun `LOT 4B7 remains unchanged and is not converted to 487`() {
        val lotText = "LOT 4B7 MFG 10/2025 EXP 10/2027"
        val normalized = DeterministicMemoryExtractor.normalizeForExtraction(lotText)

        assertTrue("LOT 4B7 must be preserved", normalized.contains("LOT 4B7"))
    }

    @Test
    fun `medicine strip with EXRRYDATE SES 2028 extracts 2028-09-01`() {
        val stripText = "EXRRYDATE SES 2028 MA.PR 2"
        val result = extractor.extractLocalResult(stripText)

        assertNotNull("Should extract date from EXRRYDATE SES 2028", result.selectedEventDate)
        assertEquals(LocalDate.of(2028, 9, 1), result.selectedEventDate)
        assertEquals(EventType.EXPIRATION, result.eventType)
    }

    @Test
    fun `tube crimp with ERE 07 t0E extracts 2027-07-01`() {
        val tubeText = "MES:08/2025\nERE:07/t0E?\nHOPE"
        val result = extractor.extractLocalResult(tubeText)

        assertNotNull("Should extract expiration date from ERE:07/t0E?", result.selectedEventDate)
        assertEquals(LocalDate.of(2027, 7, 1), result.selectedEventDate)
        assertEquals(EventType.EXPIRATION, result.eventType)
    }

    @Test
    fun `Dolo 650 with cedilla C in DEC extracts 2029-12-01`() {
        val doloText = "o. DO8S4352 MFG. JAN, 2026 EXP. DEÇ(2029"
        val result = extractor.extractLocalResult(doloText)

        assertNotNull("Should extract date from DEÇ(2029", result.selectedEventDate)
        assertEquals(LocalDate.of(2029, 12, 1), result.selectedEventDate)
        assertEquals(EventType.EXPIRATION, result.eventType)
    }
}
