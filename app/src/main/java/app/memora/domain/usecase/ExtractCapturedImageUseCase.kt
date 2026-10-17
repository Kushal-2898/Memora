package app.memora.domain.usecase

import app.memora.domain.extraction.ExtractedMemoryCandidate
import app.memora.domain.extraction.ExtractionDisposition
import app.memora.domain.extraction.ExtractedDateCandidate
import app.memora.domain.extraction.MemoryExtractor
import app.memora.domain.extraction.Stage3ExtractionResult
import app.memora.platform.ocr.TextExtractor
import app.memora.domain.model.CapturedImage

class ExtractCapturedImageUseCase(
    private val textExtractor: TextExtractor,
    private val memoryExtractor: MemoryExtractor,
) {
    suspend operator fun invoke(image: CapturedImage, scanId: String? = null): Result<Stage3ExtractionResult> {
        val id = scanId ?: "N/A"
        val totalStartTime = System.currentTimeMillis()
        return textExtractor.extractText(image.uri, scanId).fold(
            onSuccess = { extractedText ->
                val androidUri = runCatching {
                    val parsedUri = android.net.Uri.parse(image.uri)
                    if (parsedUri.scheme.isNullOrEmpty()) {
                        android.net.Uri.fromFile(java.io.File(image.uri))
                    } else {
                        parsedUri
                    }
                }.getOrNull()
                val memoryStartTime = System.currentTimeMillis()
                memoryExtractor.extractCandidate(extractedText.rawText, androidUri, scanId).map { candidate ->
                    val parsingDuration = System.currentTimeMillis() - memoryStartTime
                    val totalDuration = System.currentTimeMillis() - totalStartTime
                    val normalized = extractedText.rawText.lines().joinToString("\n") { it.trim().replace(Regex("\\s+"), " ") }.trim()
                    val disposition = when {
                        candidate.alternatives.isNotEmpty() -> ExtractionDisposition.AMBIGUOUS
                        candidate.suggestedEventDate == null -> ExtractionDisposition.NO_ACTIONABLE_DATE
                        candidate.requiresConfirmation && candidate.confidence >= 0.9f -> ExtractionDisposition.AMBIGUOUS
                        candidate.confidence >= 0.9f -> ExtractionDisposition.HIGH_CONFIDENCE
                        else -> ExtractionDisposition.LOW_CONFIDENCE
                    }
                    val extractorUsed = memoryExtractor.javaClass.simpleName
                    app.memora.platform.logging.AppLog.recordFinalResult(
                        scanId = id,
                        disposition = disposition.name,
                        extractorUsed = extractorUsed,
                        confidence = candidate.confidence,
                        parsingDurationMs = parsingDuration,
                        totalDurationMs = totalDuration,
                    )
                    Stage3ExtractionResult(
                        candidate = candidate,
                        rawOcrText = extractedText.rawText,
                        normalizedText = normalized,
                        disposition = disposition,
                        alternatives = candidate.alternatives
                    )
                }
            },
            onFailure = {
                val totalDuration = System.currentTimeMillis() - totalStartTime
                app.memora.platform.logging.AppLog.recordFinalResult(
                    scanId = id,
                    disposition = "OCR_FAILED",
                    extractorUsed = "None",
                    confidence = 0f,
                    parsingDurationMs = 0L,
                    totalDurationMs = totalDuration,
                )
                Result.failure(it)
            },
        )
    }
}
