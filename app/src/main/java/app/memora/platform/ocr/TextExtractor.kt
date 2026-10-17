package app.memora.platform.ocr

/**
 * Spatial layout information for extracted text.
 */
data class TextBlockInfo(
    val text: String,
    val boundingBox: android.graphics.Rect? = null,
    val cornerPoints: List<android.graphics.Point>? = null,
    val lines: List<TextLineInfo> = emptyList(),
)

data class TextLineInfo(
    val text: String,
    val boundingBox: android.graphics.Rect? = null,
    val elements: List<TextElementInfo> = emptyList(),
)

data class TextElementInfo(
    val text: String,
    val boundingBox: android.graphics.Rect? = null,
)

/**
 * Result data holder for OCR processing.
 */
data class ExtractedText(
    val rawText: String,
    val suggestedTitle: String? = null,
    val suggestedDateIso: String? = null,
    val blocks: List<TextBlockInfo> = emptyList(),
)

/**
 * Platform abstraction for on-device OCR text extraction.
 *
 * Concrete ML Kit / on-device vision implementation will be introduced in subsequent stages.
 */
interface TextExtractor {
    /**
     * Extracts text from the given image URI.
     */
    suspend fun extractText(imageUri: String): Result<ExtractedText>

    /**
     * Extracts text from the given image URI with correlation tracking scanId.
     */
    suspend fun extractText(imageUri: String, scanId: String? = null): Result<ExtractedText> =
        extractText(imageUri)

    /**
     * Indicates whether the OCR engine is available locally.
     */
    val isAvailable: Boolean
}

/**
 * Stage 1 platform placeholder.
 */
class Stage1TextExtractor : TextExtractor {
    override suspend fun extractText(imageUri: String): Result<ExtractedText> {
        return Result.failure(UnsupportedOperationException("OCR text extraction is enabled in Stage 2."))
    }

    override val isAvailable: Boolean = false
}
