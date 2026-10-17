package app.memora.domain.model

/**
 * Distinguishes the originating input source for captured memories.
 *
 * All sources converge to a unified CapturedImage domain representation,
 * ensuring downstream processing (Stage 3 OCR/extraction) is completely source-agnostic.
 */
enum class CaptureSource {
    CAMERA,
    GALLERY,
    SCREENSHOT,
}
