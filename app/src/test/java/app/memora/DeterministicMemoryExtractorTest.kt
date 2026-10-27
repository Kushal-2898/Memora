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

    // ==========================================
    // Task 5 Generic Rules Tests (Rules 1 - 9)
    // ==========================================

    @Test
    fun `Rule 1 - Anchor scoping nearest anchor binds and dual dates on line`() {
        // USE BY 18 SEP 25 17 SEP 26: USE BY binds to nearest following date 17 SEP 26, earlier is MFG
        val useByText = "USE BY 18 SEP 25 17 SEP 26"
        val useByResult = extractor.extractLocalResult(useByText)
        assertEquals(LocalDate.of(2026, 9, 17), useByResult.selectedEventDate)
        assertEquals(EventType.BEST_BEFORE, useByResult.eventType)
        assertEquals("2025-09-18", useByResult.fields["MFG Date"])

        // MFG.JAN.2026 EXP.DEC.2029: MFG anchor does not apply to later date with own EXP anchor
        val dualAnchorText = "MFG.JAN.2026 EXP.DEC.2029"
        val dualAnchorResult = extractor.extractLocalResult(dualAnchorText)
        assertEquals(LocalDate.of(2029, 12, 1), dualAnchorResult.selectedEventDate)
        assertEquals(EventType.EXPIRATION, dualAnchorResult.eventType)
        assertEquals("2026-01-01", dualAnchorResult.fields["MFG Date"])
    }

    @Test
    fun `Rule 2 - Boundaries split glued anchor date and date junk`() {
        // EXPIRYAPR/2027
        val gluedExp = "EXPIRYAPR/2027"
        val res1 = extractor.extractLocalResult(gluedExp)
        assertEquals(LocalDate.of(2027, 4, 1), res1.selectedEventDate)

        // R25/FEB28
        val gluedPrefix = "R25/FEB28"
        val res2 = extractor.extractLocalResult(gluedPrefix)
        assertEquals(LocalDate.of(2028, 2, 1), res2.selectedEventDate)

        // 04/28B.NO:
        val gluedJunk = "EXP: 04/28B.NO:123"
        val res3 = extractor.extractLocalResult(gluedJunk)
        assertEquals(LocalDate.of(2028, 4, 1), res3.selectedEventDate)

        // 15JUN26711MRR2745
        val dualGlued = "15JUN26711MRR2745"
        val res4 = extractor.extractLocalResult(dualGlued)
        assertEquals(LocalDate.of(2027, 3, 11), res4.selectedEventDate)
    }

    @Test
    fun `Rule 3 - Fuzzy anchors and months within edit distance 1`() {
        val fuzzyAnchor = "EXF:07/2027"
        val res1 = extractor.extractLocalResult(fuzzyAnchor)
        assertEquals(LocalDate.of(2027, 7, 1), res1.selectedEventDate)
        assertEquals(EventType.EXPIRATION, res1.eventType)

        val fuzzyMonth = "EXP: 11MRR27"
        val res2 = extractor.extractLocalResult(fuzzyMonth)
        assertEquals(LocalDate.of(2027, 3, 11), res2.selectedEventDate)
    }

    @Test
    fun `Rule 4 - Non-alphanumeric separators like greater-than`() {
        val gtDate = "EXP 13-01>27"
        val res1 = extractor.extractLocalResult(gtDate)
        assertEquals(LocalDate.of(2027, 1, 13), res1.selectedEventDate)

        val gtDate2 = "EXP 15>09/26"
        val res2 = extractor.extractLocalResult(gtDate2)
        assertEquals(LocalDate.of(2026, 9, 15), res2.selectedEventDate)
    }

    @Test
    fun `Rule 5 - Slash repair only in date-shaped tokens`() {
        // 06109/2026 -> 06/09/2026
        val d1 = "EXP: 06109/2026"
        assertEquals(LocalDate.of(2026, 9, 6), extractor.extractLocalResult(d1).selectedEventDate)

        // 08712126 -> 08/12/2026
        val d2 = "EXP: 08712126"
        assertEquals(LocalDate.of(2026, 12, 8), extractor.extractLocalResult(d2).selectedEventDate)

        // 0776027 -> 07/2027 with year repair 6<->2
        val d3 = "EXP: 0776027"
        assertEquals(LocalDate.of(2027, 7, 1), extractor.extractLocalResult(d3).selectedEventDate)

        // 0712028 -> 07/2028
        val d4 = "EXP: 0712028"
        assertEquals(LocalDate.of(2028, 7, 1), extractor.extractLocalResult(d4).selectedEventDate)

        // 05726 -> 05/26
        val d5 = "EXP: 05726"
        assertEquals(LocalDate.of(2026, 5, 1), extractor.extractLocalResult(d5).selectedEventDate)

        // 0972026 -> 09/2026
        val d6 = "EXP: 0972026"
        assertEquals(LocalDate.of(2026, 9, 1), extractor.extractLocalResult(d6).selectedEventDate)
    }

    @Test
    fun `Rule 6 - Dot-separated dates next to expiry anchor`() {
        val dotDate = "ExpiryDate: 13.09.2027"
        val res = extractor.extractLocalResult(dotDate)
        assertEquals(LocalDate.of(2027, 9, 13), res.selectedEventDate)
        assertEquals(EventType.EXPIRATION, res.eventType)
    }

    @Test
    fun `Rule 7 - Candidate choice anchored beats unanchored and isolated unanchored returns null`() {
        // Anchored beats unanchored
        val competing = "2025-05-10\nEXP: 2027-08-15"
        val res1 = extractor.extractLocalResult(competing)
        assertEquals(LocalDate.of(2027, 8, 15), res1.selectedEventDate)

        // Isolated unanchored packaging/import date returns null expiry
        val importText = "PRODUCT OF INDIA\nDATE OF IMPORT: 01-11-2025\nNET WT 500g"
        val res2 = extractor.extractLocalResult(importText)
        assertNull("Isolated import date should not be returned as expiry", res2.selectedEventDate)
    }

    @Test
    fun `Rule 8 - Shelf life Best before N months with printed MFG date`() {
        val shelfText = "MFG: 09/2026\nBest before 24 months"
        val res = extractor.extractLocalResult(shelfText)
        assertEquals(LocalDate.of(2028, 9, 1), res.selectedEventDate)
        assertEquals(EventType.BEST_BEFORE, res.eventType)

        // Multi-pass aggregation via extractFromPasses
        val pass1 = "MFG: 05/26\nBatch No: NA6E015"
        val pass2 = "Best before 24 months"
        val multiPassRes = extractor.extractFromPasses(listOf(pass1, pass2))
        assertEquals(LocalDate.of(2028, 5, 1), multiPassRes.selectedEventDate)
    }

    @Test
    fun `Rule 9 - Never rewrite characters outside date-shaped tokens`() {
        val raw = "LOT 4B7 TIME 12:30 MFG 01/2026 EXP 01/2028"
        val normalized = DeterministicMemoryExtractor.normalizeForExtraction(raw)
        assertTrue("LOT 4B7 must remain intact", normalized.contains("LOT 4B7"))
        val res = extractor.extractLocalResult(raw)
        assertEquals(LocalDate.of(2028, 1, 1), res.selectedEventDate)
    }

    @Test
    fun `Task 5b Item 2 - Unsafe year completion prevented for truncated or single-digit years`() {
        // "04:202" must NOT become 2020-04. A year must strictly be 2 or 4 digits.
        val res1 = extractor.extractLocalResult("04:202")
        assertNull("Truncated year 04:202 must return null (none)", res1.selectedEventDate)

        val res2 = extractor.extractLocalResult("Exp. Date 04:202")
        assertNull("Exp. Date 04:202 must return null (none)", res2.selectedEventDate)

        // Single digit year (e.g. 04/2 or 04:2) must also return null
        val res3 = extractor.extractLocalResult("EXP: 04/2")
        assertNull("Single digit year 04/2 must return null", res3.selectedEventDate)

        // 3-digit year in other forms
        val res4 = extractor.extractLocalResult("USE BY 15/09/202")
        assertNull("3-digit year 15/09/202 must return null", res4.selectedEventDate)
    }

    @Test
    fun `Task 6 - Glued digit and EXP anchor decoupling`() {
        val text = ".FOR15TABS.RS.32.12INCL.OFL N.2026EXP.DEC.2029 MFG.JAN. DOBS4352"
        val norm = DeterministicMemoryExtractor.normalizeForExtraction(text)
        assertTrue("EXP must be decoupled from preceding year digits", norm.contains("2026 EXP."))
        val res = extractor.extractLocalResult(text)
        assertEquals(LocalDate.of(2029, 12, 1), res.selectedEventDate)
        assertEquals(EventType.EXPIRATION, res.eventType)
        assertEquals(DeterministicMemoryExtractor.CONFIDENCE_HIGH, res.confidence)
    }

    @Test
    fun `Task 6 - Fuzzy PIRY anchor next to date-shaped token`() {
        val text1 = "PIRYAPR/2027 59.17"
        val res1 = extractor.extractLocalResult(text1)
        assertEquals(LocalDate.of(2027, 4, 1), res1.selectedEventDate)
        assertEquals(EventType.EXPIRATION, res1.eventType)
        assertEquals(DeterministicMemoryExtractor.CONFIDENCE_HIGH, res1.confidence)

        val text2 = "EXPIRYAPR/2027"
        val res2 = extractor.extractLocalResult(text2)
        assertEquals(LocalDate.of(2027, 4, 1), res2.selectedEventDate)
        assertEquals(EventType.EXPIRATION, res2.eventType)
        assertEquals(DeterministicMemoryExtractor.CONFIDENCE_HIGH, res2.confidence)
    }

    @Test
    fun `Task 6 - Import date rejected as non-actionable historical date`() {
        val text = "Month of Import: NOK KHET LANE, BANG KH BANGKOK 10120 THAILAND 4 HLO,BANG KHO LAEM, NOVEMBER2025 Rs 219.00"
        val res = extractor.extractLocalResult(text)
        assertNull("Dates labeled with Month of Import must be filtered out", res.selectedEventDate)
        assertEquals(0f, res.confidence)
    }

    @Test
    fun `Task 6 - Derived shelf life calculation has low confidence, never dangerous high confidence`() {
        val text = "MFG: 01/09/2026\nBEST BEFORE 6 MONTHS FROM DATE OF MFG"
        val res = extractor.extractLocalResult(text)
        assertEquals(LocalDate.of(2027, 3, 1), res.selectedEventDate)
        assertEquals(DeterministicMemoryExtractor.CONFIDENCE_LOW, res.confidence)
    }

    @Test
    fun `Task 7 - Unreadable shelf life duration treats bare date as MFG returning null expiry`() {
        // When "Best before months from mfg" exists without readable count N, bare date is treated as MFG date and not returned as expiry
        val text = "17080 8068 Batch No. apgba MRP(): so) 99.00 05726 NA6E015\nBestbeforemonths fromthedateofmlg Manufactured by:"
        val res = extractor.extractLocalResult(text)
        assertNull("Bare date must be treated as MFG and return null expiry when shelf life duration is unreadable", res.selectedEventDate)
        assertEquals(0f, res.confidence)
    }

    @Test
    fun `memora testing - Dot-matrix n year repair parses accurately`() {
        val text = "Pkd.. J60810B1 06/05n7 10/08/26"
        val res = extractor.extractLocalResult(text)
        assertEquals(LocalDate.of(2027, 5, 6), res.selectedEventDate)
    }

    @Test
    fun `memora testing - Dual fused dates on single line resolves to expiry`() {
        val text = "100CRM/RS.50RS.0.1/-09/07/2026/09/01/2 BESTBEFORE 6 MONTHS FROM THE DATEOF"
        val res = extractor.extractLocalResult(text)
        assertEquals(LocalDate.of(2027, 1, 9), res.selectedEventDate)
    }

    @Test
    fun `memora testing - Capsule strip fused blister pack expiry anchor`() {
        val text = "EXPIRYAPR2"
        val res = extractor.extractLocalResult(text)
        assertEquals(LocalDate.of(2027, 4, 1), res.selectedEventDate)
        assertEquals(EventType.EXPIRATION, res.eventType)
    }

    @Test
    fun `memora testing - Batch date with 0CI OCR error and Use By on packaging`() {
        val text = "Batch No.: 21 0CI 2023\nPkd.. 20 APR 2025\nUse By."
        val res = extractor.extractLocalResult(text)
        assertEquals(LocalDate.of(2025, 4, 20), res.selectedEventDate)
        assertEquals(EventType.EXPIRATION, res.eventType)
    }

    @Test
    fun `memora testing - MTG OCR variant shelf life derivation`() {
        val text = "MFG: 05/26\nBest before 24 months from the date of mtg"
        val res = extractor.extractLocalResult(text)
        assertEquals(LocalDate.of(2028, 5, 1), res.selectedEventDate)
        assertEquals(EventType.BEST_BEFORE, res.eventType)
    }

    @Test
    fun `memora testing - Generalized dual fused dates resolve dynamic years`() {
        val text2028 = "100CRM/RS.50-09/07/2026/09/01/2028 BESTBEFORE"
        val res2028 = extractor.extractLocalResult(text2028)
        assertEquals(LocalDate.of(2028, 1, 9), res2028.selectedEventDate)

        val text2029 = "100CRM/RS.50-09/07/2026/09/01/29 BESTBEFORE"
        val res2029 = extractor.extractLocalResult(text2029)
        assertEquals(LocalDate.of(2029, 1, 9), res2029.selectedEventDate)
    }

    @Test
    fun `memora testing - Generalized blister pack expiry anchors across months and years`() {
        val textMay28 = "EXPIRYMAY28"
        val resMay28 = extractor.extractLocalResult(textMay28)
        assertEquals(LocalDate.of(2028, 5, 1), resMay28.selectedEventDate)

        val textDec29 = "EXPIRYDEC29"
        val resDec29 = extractor.extractLocalResult(textDec29)
        assertEquals(LocalDate.of(2029, 12, 1), resDec29.selectedEventDate)
    }

    @Test
    fun `memora testing - Dettol handwash contrast pass extracts 2028-07`() {
        val text = "ECL25534\n08/2025\n1 0712028"
        val res = extractor.extractLocalResult(text)
        assertEquals(LocalDate.of(2028, 7, 1), res.selectedEventDate)
        assertEquals(EventType.OTHER, res.eventType)
    }

    @Test
    fun `memora testing - Expanded brand title detection`() {
        val res1 = extractor.extractLocalResult("DAAWAT Traditional Basmati Rice\nPKD 15/06/2026\nUSE BY 14/06/2028")
        assertEquals("Grocery / Rice", res1.title)

        val res2 = extractor.extractLocalResult("ZANDU BALM 04/2028")
        assertEquals("Healthcare / Balm", res2.title)

        val res3 = extractor.extractLocalResult("Dettol Handwash Liquid 08/2025")
        assertEquals("Personal Care", res3.title)

        val res4 = extractor.extractLocalResult("Rowezi Premium Footwear Invoice")
        assertEquals("Product Warranty", res4.title)
    }
}



