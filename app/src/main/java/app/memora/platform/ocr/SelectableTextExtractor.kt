package app.memora.platform.ocr

/**
 * Enumeration of available on-device OCR engines.
 */
enum class ActiveOcrEngine {
    ML_KIT,
    RAPID_OCR,
}

/**
 * Delegating [TextExtractor] that allows switching between [MlKitTextExtractor]
 * and [RapidOcrExtractor] at runtime.
 *
 * This provides side-by-side evaluation, A/B benchmarking, and seamless engine
 * selection without requiring rebuilds or refactoring.
 */
class SelectableTextExtractor(
    val mlKitExtractor: TextExtractor,
    val rapidOcrExtractor: TextExtractor,
    var activeEngine: ActiveOcrEngine = ActiveOcrEngine.RAPID_OCR,
) : TextExtractor {

    val activeExtractor: TextExtractor
        get() = when (activeEngine) {
            ActiveOcrEngine.ML_KIT -> mlKitExtractor
            ActiveOcrEngine.RAPID_OCR -> rapidOcrExtractor
        }

    override suspend fun extractText(imageUri: String): Result<ExtractedText> =
        activeExtractor.extractText(imageUri)

    override suspend fun extractText(imageUri: String, scanId: String?): Result<ExtractedText> =
        activeExtractor.extractText(imageUri, scanId)

    override val isAvailable: Boolean
        get() = activeExtractor.isAvailable
}
