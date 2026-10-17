package app.memora.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.memora.domain.extraction.DeterministicMemoryExtractor
import app.memora.domain.model.EventType
import app.memora.domain.scheduler.ClockProvider
import app.memora.platform.ocr.TextBlockInfo
import app.memora.platform.ocr.TextElementInfo
import app.memora.platform.ocr.TextLineInfo
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class DeterministicMemoryExtractorTargetedTest {

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
    fun test1_lineExpColon122026() {
        val result = extractor.extractLocalResult("EXP: 12/2026")
        assertEquals(LocalDate.of(2026, 12, 1), result.selectedEventDate)
        assertEquals(EventType.EXPIRATION, result.eventType)
    }

    @Test
    fun test2_elementsExpColonAnd122026Joined() {
        val line = TextLineInfo(
            text = "EXP: 12/2026",
            elements = listOf(
                TextElementInfo(text = "EXP:"),
                TextElementInfo(text = "12/2026")
            )
        )
        val block = TextBlockInfo(
            text = "EXP: 12/2026",
            lines = listOf(line)
        )
        val result = extractor.extractLocalResult(listOf(block))
        assertEquals(LocalDate.of(2026, 12, 1), result.selectedEventDate)
    }

    @Test
    fun test3_lineExpSpace122026() {
        val result = extractor.extractLocalResult("EXP 12/2026")
        assertEquals(LocalDate.of(2026, 12, 1), result.selectedEventDate)
    }

    @Test
    fun test4_lineExpHyphen122026() {
        val result = extractor.extractLocalResult("EXP-12/2026")
        assertEquals(LocalDate.of(2026, 12, 1), result.selectedEventDate)
    }

    @Test
    fun test5_lineMfgColon012024() {
        val result = extractor.extractLocalResult("MFG: 01/2024")
        assertEquals("2024-01-01", result.fields["MFG Date"])
        assertEquals(null, result.selectedEventDate)
    }

    @Test
    fun test6_lineParacetamol500mg() {
        val result = extractor.extractLocalResult("PARACETAMOL 500MG")
        assertEquals("Medicine", result.title)
    }

    @Test
    fun test7_threeLineInput() {
        val input = "PARACETAMOL 500MG\nMFG: 01/2024\nEXP: 12/2026"
        val result = extractor.extractLocalResult(input)
        assertEquals("Medicine", result.title)
        assertEquals("2024-01-01", result.fields["MFG Date"])
        assertEquals(LocalDate.of(2026, 12, 1), result.selectedEventDate)
    }

    @Test
    fun test8_rapidOcrShapedStructure() {
        val line = TextLineInfo(
            text = "EXP: 12/2026",
            elements = listOf(
                TextElementInfo(text = "EXP: 12/2026")
            )
        )
        val block = TextBlockInfo(
            text = "EXP: 12/2026",
            lines = listOf(line)
        )
        val result = extractor.extractLocalResult(listOf(block))
        assertEquals(LocalDate.of(2026, 12, 1), result.selectedEventDate)
    }

    @Test
    fun test9_mlKitShapedStructure() {
        val line = TextLineInfo(
            text = "EXP: 12/2026",
            elements = listOf(
                TextElementInfo(text = "EXP:"),
                TextElementInfo(text = "12/2026")
            )
        )
        val block = TextBlockInfo(
            text = "EXP: 12/2026",
            lines = listOf(line)
        )
        val result = extractor.extractLocalResult(listOf(block))
        assertEquals(LocalDate.of(2026, 12, 1), result.selectedEventDate)
    }

    @Test
    fun test10_emptyOcrResult() {
        val result = extractor.extractLocalResult(emptyList<TextBlockInfo>())
        assertEquals(null, result.selectedEventDate)
        assertEquals(null, result.title)
        assertEquals(0f, result.confidence, 0.001f)
    }
}
