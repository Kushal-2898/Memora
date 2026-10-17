package app.memora

import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.domain.model.MemoraError
import app.memora.domain.usecase.ProcessCapturedImageUseCase
import app.memora.platform.image.ImageStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class ProcessCapturedImageUseCaseTest {

    private class FakeImageStorage(
        private val shouldFail: Boolean = false,
    ) : ImageStorage {
        var deletedUri: String? = null

        override fun createCaptureFile(prefix: String): File {
            return File.createTempFile(prefix, ".jpg")
        }

        override suspend fun importFromUri(uriString: String, source: CaptureSource): Result<CapturedImage> {
            if (shouldFail) {
                return Result.failure(MemoraError.Capture.ImportFailed("Stream reading failed"))
            }
            return Result.success(
                CapturedImage(
                    uri = "file:///data/user/0/app.memora/files/captures/imported.jpg",
                    source = source,
                    capturedAt = Instant.parse("2026-09-03T10:00:00Z"),
                    mimeType = "image/jpeg",
                    fileSize = 2048L,
                )
            )
        }

        override fun createCapturedImageFromFile(file: File, source: CaptureSource): CapturedImage {
            return CapturedImage(
                uri = file.toURI().toString(),
                source = source,
            )
        }

        override fun deleteImage(uriString: String): Boolean {
            deletedUri = uriString
            return true
        }
    }

    @Test
    fun `blank uri returns validation error`() = runTest {
        val useCase = ProcessCapturedImageUseCase(FakeImageStorage())
        val result = useCase("", CaptureSource.GALLERY)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is MemoraError.Validation)
        assertEquals("Validation failed: Image URI cannot be empty.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `successful import returns domain CapturedImage with correct source`() = runTest {
        val useCase = ProcessCapturedImageUseCase(FakeImageStorage())
        val result = useCase("content://media/picker/123", CaptureSource.SCREENSHOT)

        assertTrue(result.isSuccess)
        val image = result.getOrThrow()
        assertEquals(CaptureSource.SCREENSHOT, image.source)
        assertEquals("file:///data/user/0/app.memora/files/captures/imported.jpg", image.uri)
        assertEquals("image/jpeg", image.mimeType)
        assertEquals(2048L, image.fileSize)
    }

    @Test
    fun `storage failure propagates clean domain error`() = runTest {
        val useCase = ProcessCapturedImageUseCase(FakeImageStorage(shouldFail = true))
        val result = useCase("content://media/picker/999", CaptureSource.GALLERY)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is MemoraError.Capture.ImportFailed)
        assertEquals("Failed to import selected image: Stream reading failed", result.exceptionOrNull()?.message)
    }
}
