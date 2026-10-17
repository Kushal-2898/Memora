package app.memora.platform.image

import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import java.io.File

/**
 * Manages the persistence lifecycle, file allocation, and stream-based import of
 * captured and selected images.
 *
 * Keeps image storage strictly application-controlled, secure, and offline to ensure
 * downstream stages (such as Stage 3 OCR) can access images without permission expiry.
 */
interface ImageStorage {
    /**
     * Allocates a new target file in managed application storage for camera capture.
     */
    fun createCaptureFile(prefix: String = "photo"): File

    /**
     * Imports an external image URI (such as a Photo Picker content URI) into managed
     * application storage via streaming, without allocating large memory buffers or Bitmaps.
     */
    suspend fun importFromUri(uriString: String, source: CaptureSource): Result<CapturedImage>

    /**
     * Wraps a successfully captured local file into a domain CapturedImage.
     */
    fun createCapturedImageFromFile(file: File, source: CaptureSource): CapturedImage

    /**
     * Deletes a managed capture file if an operation is cancelled or replaced.
     */
    fun deleteImage(uriString: String): Boolean
}
