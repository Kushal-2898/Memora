package app.memora.domain.usecase

import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.domain.model.MemoraError
import app.memora.platform.image.ImageStorage

/**
 * Domain use case responsible for validating and importing captured images
 * into the common [CapturedImage] domain representation.
 *
 * Accepts images from Camera, Device Gallery, or Screenshots, delegating to [ImageStorage]
 * to ensure persistent access without keeping large in-memory Bitmaps.
 */
class ProcessCapturedImageUseCase(
    private val imageStorage: ImageStorage,
) {
    suspend operator fun invoke(
        uriString: String,
        source: CaptureSource,
    ): Result<CapturedImage> {
        if (uriString.isBlank()) {
            return Result.failure(MemoraError.Validation("Image URI cannot be empty."))
        }

        return imageStorage.importFromUri(uriString, source)
    }
}
