package app.memora

import app.memora.data.local.OfflineProductCatalog
import app.memora.domain.extraction.DeterministicMemoryExtractor
import app.memora.domain.extraction.RegulatoryPackagingRuleEngine
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
class UsPackagingRuleTest {

    private class FakeBarcodeExtractor(private val barcode: String? = null) : BarcodeExtractor {
        override suspend fun extractBarcode(imageUri: String): Result<String?> = Result.success(barcode)
    }

    private class FakeTextExtractor(private val extractedText: ExtractedText) : TextExtractor {
        override suspend fun extractText(imageUri: String): Result<ExtractedText> = Result.success(extractedText)
        override val isAvailable: Boolean = true
    }

    private val testImage = CapturedImage(
        uri = "file:///dummy/us_test.jpg",
        source = CaptureSource.CAMERA,
        capturedAt = Instant.now(),
        mimeType = "image/jpeg",
        fileSize = 2048L
    )

    private val regulatoryEngine = RegulatoryPackagingRuleEngine()

    @Test
    fun milkJug_withPlantCodeAndTimestamp_extractsSellByCorrectly() = runTest {
        val ocr = ExtractedText(
            rawText = """
                BEST IF USED BY
                OCT 28 26
                06-12 14:02
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

        println("MILK JUG OUTPUT: ${output.localResult}")
        println("MILK JUG CANDIDATES: ${output.localResult.dateCandidates}")

        assertNotNull(output.localResult.selectedEventDate)
        assertEquals(LocalDate.of(2026, 10, 28), output.localResult.selectedEventDate)
        assertEquals(EventType.BEST_BEFORE, output.localResult.eventType)
    }

    @Test
    fun eggCarton_withUSDAJulianCodeAndPlantNumber_extractsSellByCorrectly() = runTest {
        val ocr = ExtractedText(
            rawText = """
                SELL BY NOV 14 26
                P-1488 288
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

        assertNotNull(output.localResult.selectedEventDate)
        assertEquals(LocalDate.of(2026, 11, 14), output.localResult.selectedEventDate)
    }

    @Test
    fun prescriptionVial_prioritizesDiscardAfterOverRefillDateAndStripsNdc() = runTest {
        val ocr = ExtractedText(
            rawText = """
                CVS Pharmacy
                ATORVASTATIN 20MG TABLETS
                DISCARD AFTER: 10/24/2027
                REFILL 2 TIMES BEFORE 08/15/2027
                NDC 50580-498-01
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

        assertEquals(LocalDate.of(2027, 10, 24), output.localResult.selectedEventDate)
        assertEquals(EventType.EXPIRATION, output.localResult.eventType)
    }

    @Test
    fun meatButcherLabel_prioritizesFreezeByOverPackedOnAndPrice() = runTest {
        val ocr = ExtractedText(
            rawText = """
                USDA CHOICE ANGUS RIBEYE
                PACKED ON: 10/21/26
                SELL BY: 10/26/26
                FREEZE BY: 10/28/26
                NET WT 2.14 LB  |  $15.99/LB  |  TOTAL $34.22
                EST. 38
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

        println("MEAT LABEL OUTPUT: ${output.localResult}")
        println("MEAT LABEL CANDIDATES: ${output.localResult.dateCandidates}")

        // Either FREEZE BY (10/28) or SELL BY (10/26) is valid, but PACKED ON (10/21) must be rejected
        assertNotNull(output.localResult.selectedEventDate)
        val date = output.localResult.selectedEventDate!!
        assertTrue(date == LocalDate.of(2026, 10, 28) || date == LocalDate.of(2026, 10, 26))
    }

    @Test
    fun cannedSoup_withConcaveBottomTimestamp_extractsBestBy() = runTest {
        val ocr = ExtractedText(
            rawText = """
                BEST BY 04 DEC 27
                EST 384 B 09:23
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

        assertEquals(LocalDate.of(2027, 12, 4), output.localResult.selectedEventDate)
        assertEquals(EventType.BEST_BEFORE, output.localResult.eventType)
    }

    @Test
    fun usMiddleEndianDate_withUsUnits_disambiguatesToMonthFirst() = runTest {
        val ocr = ExtractedText(
            rawText = """
                KRAFT MACARONI & CHEESE
                NET WT 7.25 OZ (206g)
                BEST WHEN USED BY
                05/06/26
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

        println("US MIDDLE ENDIAN OUTPUT: ${output.localResult}")

        // 05/06/26 in US is May 6, 2026 (Month 5, Day 6)
        assertEquals(LocalDate.of(2026, 5, 6), output.localResult.selectedEventDate)
    }

    @Test
    fun cosmeticsPao_computesOffsetFromOpening() = runTest {
        val ocr = ExtractedText(
            rawText = """
                CeraVe Moisturizing Cream
                12M
                NET WT 16 OZ
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

        val expected = LocalDate.now().plusMonths(12)
        assertEquals(expected, output.localResult.selectedEventDate)
        assertEquals(EventType.EXPIRATION, output.localResult.eventType)
        assertEquals("12 months", output.localResult.fields["PAO"])
    }

    @Test
    fun usUpcBarcodeLookup_anchorsUsBrandOffline() {
        val catalog = OfflineProductCatalog()
        // Heinz Tomato Ketchup 20oz UPC-A
        val heinz = catalog.lookup("013000006408")
        assertNotNull(heinz)
        assertTrue(heinz?.productName?.contains("Heinz", ignoreCase = true) == true)
        assertEquals("United States / Canada", heinz?.originCountry)

        // General Mills Cheerios UPC-A
        val cheerios = catalog.lookup("016000275270")
        assertNotNull(cheerios)
        assertTrue(cheerios?.productName?.contains("Cheerios", ignoreCase = true) == true)
    }
}
