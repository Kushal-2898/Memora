package app.memora

import app.memora.data.local.OfflineProductCatalog
import app.memora.domain.extraction.DeterministicMemoryExtractor
import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.domain.model.EventType
import app.memora.domain.usecase.ExtractLocallyUseCase
import app.memora.platform.ocr.BarcodeExtractor
import app.memora.platform.ocr.ExtractedText
import app.memora.platform.ocr.TextExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExtractLocallyUseCaseTest {

    private class FakeBarcodeExtractor(private val barcode: String? = null) : BarcodeExtractor {
        override suspend fun extractBarcode(imageUri: String): Result<String?> = Result.success(barcode)
    }

    private class FakeTextExtractor(private val extractedText: ExtractedText) : TextExtractor {
        override suspend fun extractText(imageUri: String): Result<ExtractedText> = Result.success(extractedText)
        override val isAvailable: Boolean = true
    }

    private val testImage = CapturedImage(
        uri = "file:///dummy/test.jpg",
        source = CaptureSource.CAMERA,
        capturedAt = Instant.now(),
        mimeType = "image/jpeg",
        fileSize = 2048L
    )

    @Test
    fun extractPouch_computesRegulatoryRelativeShelfLife() = runTest {
        val ocr = ExtractedText(
            rawText = """
                Organic Oats Pouch
                MFG DATE: 01/2026
                BEST BEFORE 9 MONTHS FROM MANUFACTURE
                BATCH NO: B-7712
            """.trimIndent()
        )

        val useCase = ExtractLocallyUseCase(
            textExtractor = FakeTextExtractor(ocr),
            barcodeExtractor = FakeBarcodeExtractor(null),
            deterministicExtractor = DeterministicMemoryExtractor(),
            offlineCatalog = OfflineProductCatalog()
        )

        val result = useCase(testImage)
        assertTrue(result.isSuccess)
        val output = result.getOrThrow()

        // Shelf life calculation: 01/2026 + 9 months = 10/2026
        assertNotNull(output.localResult.selectedEventDate)
        assertEquals(LocalDate.of(2026, 10, 1), output.localResult.selectedEventDate)
        assertTrue(output.localResult.title?.contains("Oats", ignoreCase = true) == true)
    }

    @Test
    fun extractMedicineStrip_discardsBatchCodeAndExtractsTitleAndExpiry() = runTest {
        val ocr = ExtractedText(
            rawText = """
                Paracetamol IP 500mg
                BATCH NO: PAC-9042
                MFG: 05/2025
                EXP: 12/2027
            """.trimIndent()
        )

        val useCase = ExtractLocallyUseCase(
            textExtractor = FakeTextExtractor(ocr),
            barcodeExtractor = FakeBarcodeExtractor(null),
            deterministicExtractor = DeterministicMemoryExtractor(),
            offlineCatalog = OfflineProductCatalog()
        )

        val result = useCase(testImage)
        assertTrue(result.isSuccess)
        val output = result.getOrThrow()

        assertEquals("Medicine", output.localResult.title)
        assertEquals(LocalDate.of(2027, 12, 1), output.localResult.selectedEventDate)
        assertEquals(EventType.EXPIRATION, output.localResult.eventType)
    }

    @Test
    fun extractUtilityBill_extractsBillerTitleAndDueDate() = runTest {
        val ocr = ExtractedText(
            rawText = """
                Torrent Power Electricity Bill
                Consumer No: 9876543210
                Bill Date: 01/10/2026
                Due Date: 15/10/2026
                Amount Payable: Rs 1450.00
            """.trimIndent()
        )

        val useCase = ExtractLocallyUseCase(
            textExtractor = FakeTextExtractor(ocr),
            barcodeExtractor = FakeBarcodeExtractor(null),
            deterministicExtractor = DeterministicMemoryExtractor(),
            offlineCatalog = OfflineProductCatalog()
        )

        val result = useCase(testImage)
        assertTrue(result.isSuccess)
        val output = result.getOrThrow()

        assertEquals("Electricity Bill", output.localResult.title)
        assertEquals(LocalDate.of(2026, 10, 15), output.localResult.selectedEventDate)
        assertEquals(EventType.DUE_DATE, output.localResult.eventType)
    }

    @Test
    fun extractBarcodeAnchoredProduct_anchorsBrandAndCalculatesExpiry() = runTest {
        // Quaker Rolled Oats EAN-13 barcode
        val barcode = "8901499008236"
        val ocr = ExtractedText(
            rawText = "EXP: 31/12/2026"
        )

        val useCase = ExtractLocallyUseCase(
            textExtractor = FakeTextExtractor(ocr),
            barcodeExtractor = FakeBarcodeExtractor(barcode),
            deterministicExtractor = DeterministicMemoryExtractor(),
            offlineCatalog = OfflineProductCatalog()
        )

        val result = useCase(testImage)
        assertTrue(result.isSuccess)
        val output = result.getOrThrow()

        assertEquals("Quaker Rolled Oats 500g", output.localResult.title)
        assertEquals(LocalDate.of(2026, 12, 31), output.localResult.selectedEventDate)
    }
}
