package app.memora

import app.memora.platform.ocr.ActiveOcrEngine
import app.memora.platform.ocr.ExtractedText
import app.memora.platform.ocr.SelectableTextExtractor
import app.memora.platform.ocr.TextExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectableTextExtractorTest {

    private class FakeExtractor(val tag: String) : TextExtractor {
        var extractCalls = 0
        override suspend fun extractText(imageUri: String): Result<ExtractedText> =
            extractText(imageUri, null)

        override suspend fun extractText(imageUri: String, scanId: String?): Result<ExtractedText> {
            extractCalls++
            return Result.success(ExtractedText(rawText = "Result from $tag: $imageUri"))
        }

        override val isAvailable: Boolean = true
    }

    @Test
    fun `defaults to RAPID_OCR and delegates extraction accordingly`() = runTest {
        val mlKitFake = FakeExtractor("ML_KIT")
        val rapidFake = FakeExtractor("RAPID_OCR")

        val selectable = SelectableTextExtractor(
            mlKitExtractor = mlKitFake,
            rapidOcrExtractor = rapidFake,
            activeEngine = ActiveOcrEngine.RAPID_OCR
        )

        assertEquals(ActiveOcrEngine.RAPID_OCR, selectable.activeEngine)
        assertTrue(selectable.isAvailable)

        val result = selectable.extractText("file:///test.jpg", "SCAN_1")
        assertTrue(result.isSuccess)
        assertEquals("Result from RAPID_OCR: file:///test.jpg", result.getOrNull()?.rawText)
        assertEquals(1, rapidFake.extractCalls)
        assertEquals(0, mlKitFake.extractCalls)
    }

    @Test
    fun `switches dynamically between RAPID_OCR and ML_KIT`() = runTest {
        val mlKitFake = FakeExtractor("ML_KIT")
        val rapidFake = FakeExtractor("RAPID_OCR")

        val selectable = SelectableTextExtractor(
            mlKitExtractor = mlKitFake,
            rapidOcrExtractor = rapidFake,
            activeEngine = ActiveOcrEngine.RAPID_OCR
        )

        selectable.extractText("file:///test1.jpg")
        assertEquals(1, rapidFake.extractCalls)
        assertEquals(0, mlKitFake.extractCalls)

        // Switch to ML Kit
        selectable.activeEngine = ActiveOcrEngine.ML_KIT
        val result = selectable.extractText("file:///test2.jpg")

        assertTrue(result.isSuccess)
        assertEquals("Result from ML_KIT: file:///test2.jpg", result.getOrNull()?.rawText)
        assertEquals(1, rapidFake.extractCalls)
        assertEquals(1, mlKitFake.extractCalls)
    }

    @Test
    fun `ExtractLocallyUseCase triggers cascade fallback when primary yields no date`() = runTest {
        val rapidFake = object : TextExtractor {
            var calls = 0
            override suspend fun extractText(imageUri: String): Result<ExtractedText> = extractText(imageUri, null)
            override suspend fun extractText(imageUri: String, scanId: String?): Result<ExtractedText> {
                calls++
                return Result.success(ExtractedText(rawText = "Organic Rolled Oats no date here"))
            }
            override val isAvailable: Boolean = true
        }

        val mlKitFake = object : TextExtractor {
            var calls = 0
            override suspend fun extractText(imageUri: String): Result<ExtractedText> = extractText(imageUri, null)
            override suspend fun extractText(imageUri: String, scanId: String?): Result<ExtractedText> {
                calls++
                return Result.success(ExtractedText(rawText = "Organic Rolled Oats EXP 14/11/2026"))
            }
            override val isAvailable: Boolean = true
        }

        val selectable = SelectableTextExtractor(
            mlKitExtractor = mlKitFake,
            rapidOcrExtractor = rapidFake,
            activeEngine = ActiveOcrEngine.RAPID_OCR
        )

        val useCase = app.memora.domain.usecase.ExtractLocallyUseCase(
            textExtractor = selectable,
            deterministicExtractor = app.memora.domain.extraction.DeterministicMemoryExtractor()
        )

        val image = app.memora.domain.model.CapturedImage(
            uri = "file:///sample.jpg",
            source = app.memora.domain.model.CaptureSource.CAMERA,
            capturedAt = java.time.Instant.now(),
            mimeType = "image/jpeg",
            fileSize = 1000L
        )

        val output = useCase(image).getOrThrow()
        assertEquals(1, rapidFake.calls)
        assertEquals(1, mlKitFake.calls)
        assertEquals(java.time.LocalDate.of(2026, 11, 14), output.localResult.selectedEventDate)
    }
}
