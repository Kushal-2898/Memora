package app.memora

import app.memora.data.local.BarcodeMetadata
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalBarcodeScannerTest {

    private class FakeBarcodeExtractor(private val barcodeToReturn: String?) : BarcodeExtractor {
        override suspend fun extractBarcode(imageUri: String): Result<String?> = Result.success(barcodeToReturn)
    }

    private class FakeTextExtractor(private val text: String) : TextExtractor {
        override suspend fun extractText(imageUri: String): Result<ExtractedText> =
            Result.success(ExtractedText(rawText = text))
        override val isAvailable: Boolean = true
    }

    private val testImage = CapturedImage(
        uri = "file:///dummy/path.jpg",
        source = CaptureSource.CAMERA,
        capturedAt = Instant.now(),
        mimeType = "image/jpeg",
        fileSize = 1024L
    )

    @Test
    fun barcodeDetected_offlineCatalogAnchorsProductTitleAndCategory() = runTest {
        val fakeBarcode = "8901499008236" // Quaker Rolled Oats
        val offlineCatalog = OfflineProductCatalog()
        val useCase = ExtractLocallyUseCase(
            textExtractor = FakeTextExtractor("12/2026"),
            barcodeExtractor = FakeBarcodeExtractor(fakeBarcode),
            deterministicExtractor = DeterministicMemoryExtractor(),
            offlineCatalog = offlineCatalog
        )

        val result = useCase(testImage)
        assertTrue(result.isSuccess)
        val output = result.getOrThrow()

        // Title should be anchored by offline catalog
        assertTrue(output.localResult.title?.contains("Oats", ignoreCase = true) == true)
        assertEquals(EventType.EXPIRATION, output.localResult.eventType)
    }

    @Test
    fun userCachedBarcode_anchorsCustomTitleOffline() = runTest {
        val customBarcode = "1122334455667"
        val offlineCatalog = OfflineProductCatalog()
        offlineCatalog.cacheProduct(
            customBarcode,
            BarcodeMetadata(
                productName = "Custom Almond Milk 1L",
                brand = "Silk",
                category = EventType.EXPIRATION,
                defaultShelfLifeMonths = 12
            )
        )

        val useCase = ExtractLocallyUseCase(
            textExtractor = FakeTextExtractor("10/2027"),
            barcodeExtractor = FakeBarcodeExtractor(customBarcode),
            deterministicExtractor = DeterministicMemoryExtractor(),
            offlineCatalog = offlineCatalog
        )

        val result = useCase(testImage)
        assertTrue(result.isSuccess)
        val output = result.getOrThrow()

        assertEquals("Custom Almond Milk 1L", output.localResult.title)
        assertEquals(EventType.EXPIRATION, output.localResult.eventType)
    }
}
