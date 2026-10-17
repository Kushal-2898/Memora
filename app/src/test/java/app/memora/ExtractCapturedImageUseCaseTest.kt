package app.memora

import android.net.Uri
import app.memora.domain.extraction.ExtractedMemoryCandidate
import app.memora.domain.extraction.MemoryExtractor
import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.domain.model.EventType
import app.memora.domain.usecase.ExtractCapturedImageUseCase
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
class ExtractCapturedImageUseCaseTest {

    private class RecordingMemoryExtractor : MemoryExtractor {
        var lastExtractedRawText: String? = null
        var lastExtractedImageUri: Uri? = null
        var candidateToReturn = ExtractedMemoryCandidate(
            suggestedTitle = "Gas Bill",
            suggestedEventType = EventType.DUE_DATE,
            suggestedEventDate = LocalDate.of(2026, 10, 15),
            confidence = 0.95f
        )

        override suspend fun extractCandidate(rawText: String, imageUri: Uri?): Result<ExtractedMemoryCandidate> {
            lastExtractedRawText = rawText
            lastExtractedImageUri = imageUri
            return Result.success(candidateToReturn)
        }
    }

    private class FakeTextExtractor(
        var textToReturn: String = "",
        var shouldFail: Boolean = false,
    ) : TextExtractor {
        override suspend fun extractText(imageUri: String): Result<ExtractedText> {
            if (shouldFail) return Result.failure(RuntimeException("OCR failed"))
            return Result.success(ExtractedText(rawText = textToReturn))
        }

        override val isAvailable: Boolean = true
    }

    private val testImage = CapturedImage(
        uri = "file:///data/user/0/app.memora/files/captures/gas_bill.jpg",
        source = CaptureSource.CAMERA,
        capturedAt = Instant.parse("2026-09-03T10:00:00Z"),
        mimeType = "image/jpeg",
        fileSize = 4096L,
    )

    @Test
    fun `when OCR text is empty string, memoryExtractor is still called with image URI and empty OCR text`() = runTest {
        val fakeTextExtractor = FakeTextExtractor(textToReturn = "")
        val recordingMemoryExtractor = RecordingMemoryExtractor()
        val useCase = ExtractCapturedImageUseCase(fakeTextExtractor, recordingMemoryExtractor)

        val result = useCase(testImage)

        assertTrue(result.isSuccess)
        assertEquals("", recordingMemoryExtractor.lastExtractedRawText)
        assertNotNull(recordingMemoryExtractor.lastExtractedImageUri)
        assertEquals("file:///data/user/0/app.memora/files/captures/gas_bill.jpg", recordingMemoryExtractor.lastExtractedImageUri.toString())
        assertEquals("Gas Bill", result.getOrThrow().candidate.suggestedTitle)
        assertEquals(LocalDate.of(2026, 10, 15), result.getOrThrow().candidate.suggestedEventDate)
    }

    @Test
    fun `when OCR text is whitespace only, memoryExtractor is still called with image URI and whitespace text`() = runTest {
        val fakeTextExtractor = FakeTextExtractor(textToReturn = "   \n\t  ")
        val recordingMemoryExtractor = RecordingMemoryExtractor()
        val useCase = ExtractCapturedImageUseCase(fakeTextExtractor, recordingMemoryExtractor)

        val result = useCase(testImage)

        assertTrue(result.isSuccess)
        assertEquals("   \n\t  ", recordingMemoryExtractor.lastExtractedRawText)
        assertNotNull(recordingMemoryExtractor.lastExtractedImageUri)
        assertEquals("file:///data/user/0/app.memora/files/captures/gas_bill.jpg", recordingMemoryExtractor.lastExtractedImageUri.toString())
        assertEquals("Gas Bill", result.getOrThrow().candidate.suggestedTitle)
    }

    @Test
    fun `when OCR text has content, memoryExtractor is called with that text and image URI`() = runTest {
        val fakeTextExtractor = FakeTextExtractor(textToReturn = "Indane Gas Payment Due 15/10/2026")
        val recordingMemoryExtractor = RecordingMemoryExtractor()
        val useCase = ExtractCapturedImageUseCase(fakeTextExtractor, recordingMemoryExtractor)

        val result = useCase(testImage)

        assertTrue(result.isSuccess)
        assertEquals("Indane Gas Payment Due 15/10/2026", recordingMemoryExtractor.lastExtractedRawText)
        assertEquals("file:///data/user/0/app.memora/files/captures/gas_bill.jpg", recordingMemoryExtractor.lastExtractedImageUri.toString())
    }

    @Test
    fun `when textExtractor fails, failure is propagated`() = runTest {
        val fakeTextExtractor = FakeTextExtractor(shouldFail = true)
        val recordingMemoryExtractor = RecordingMemoryExtractor()
        val useCase = ExtractCapturedImageUseCase(fakeTextExtractor, recordingMemoryExtractor)

        val result = useCase(testImage)

        assertTrue(result.isFailure)
        assertEquals("OCR failed", result.exceptionOrNull()?.message)
    }
}
