package app.memora.domain.model

import java.time.Instant

/**
 * Platform-independent domain representation of a captured or imported image.
 *
 * This model strictly decouples the core domain and future extraction pipelines from
 * platform-specific types such as Android Bitmap, CameraX use cases, Activity instances,
 * or UI components.
 *
 * @property uri Lightweight string representation of the image URI (e.g. file:// or content://).
 * @property source The origin method used to capture the image.
 * @property capturedAt The timestamp when the capture or import occurred.
 * @property mimeType The detected MIME type (e.g., "image/jpeg", "image/png").
 * @property fileSize File size in bytes for diagnostic and memory awareness.
 * @property width Optional image width in pixels.
 * @property height Optional image height in pixels.
 */
data class CapturedImage(
    val uri: String,
    val source: CaptureSource,
    val capturedAt: Instant = Instant.now(),
    val mimeType: String = "image/jpeg",
    val fileSize: Long = 0L,
    val width: Int? = null,
    val height: Int? = null,
)
