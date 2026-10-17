package app.memora.domain.usecase

import android.net.Uri
import app.memora.domain.extraction.ExtractedMemoryCandidate
import app.memora.domain.extraction.ExtractionDisposition
import app.memora.domain.extraction.LocalExtractionResult
import app.memora.domain.extraction.MemoryExtractor
import app.memora.domain.extraction.OcrResult
import app.memora.domain.extraction.Stage3ExtractionResult
import app.memora.domain.model.CapturedImage
import app.memora.domain.model.EventType
import app.memora.platform.logging.AppLog
import java.io.File

/**
 * UseCase coordinating AI refinement of locally extracted memory candidates.
 *
 * CRITICAL COST AND BEHAVIOR CONTRACT:
 * - Invoked ONLY upon explicit user confirmation from the local review UI.
 * - Passes locally detected signals (title, dates, fields) to Gemini as multimodal evidence, not absolute truth.
 * - Graceful Fallback: On network or Gemini failure, falls back to the local extraction ONLY if the
 *   local candidate is unambiguous and has a valid selected event date.
 * - If ambiguous or no date is present, it does NOT silently invent a date; it returns failure so the user
 *   can retry, rescan, or enter manually.
 */
class RefineWithGeminiUseCase(
    private val geminiExtractor: MemoryExtractor,
) {
    suspend operator fun invoke(
        image: CapturedImage,
        ocrResult: OcrResult,
        localResult: LocalExtractionResult,
        scanId: String? = null,
    ): Result<Stage3ExtractionResult> {
        val id = scanId ?: "N/A"
        val startTime = System.currentTimeMillis()

        // Build evidence summary prompt for Gemini
        val fieldsSummary = if (localResult.fields.isNotEmpty()) {
            localResult.fields.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        } else {
            "None"
        }

        val prompt = buildString {
            appendLine("=== LOCAL ON-DEVICE PRE-ANALYSIS EVIDENCE (Treat as hint/evidence, NOT ground truth) ===")
            appendLine("Locally Detected Title: ${localResult.title ?: "None"}")
            appendLine("Locally Detected Event Type: ${localResult.eventType?.name ?: "None"}")
            appendLine("Locally Selected Date: ${localResult.selectedEventDate ?: "None"}")
            appendLine("Date Type: ${localResult.dateType ?: "None"}")
            appendLine("Packaging Type: ${localResult.packagingType.displayName}")
            appendLine("Expected Date Location: ${localResult.dateLocation.displayName}")
            appendLine("Marking Technique: ${localResult.markingStyle.displayName}")
            if (localResult.packagingType == app.memora.domain.extraction.PackagingType.TUBE || localResult.dateLocation == app.memora.domain.extraction.DateLocation.CRIMP_SEAL) {
                appendLine("Special Packaging Hint: Heat-sealed tube crimp detected. The expiry or manufacture date is heat-embossed directly into the plastic crimp seal at the top or sealed end with NO colored ink contrast. Inspect the serrated/ribbed crimp texture closely for debossed or embossed digits.")
            } else if (localResult.packagingType == app.memora.domain.extraction.PackagingType.CAN) {
                appendLine("Special Packaging Hint: Can/tin detected. Expiry/mfg dates are usually dot-matrix printed on the bottom concave surface or rim.")
            } else if (localResult.packagingType == app.memora.domain.extraction.PackagingType.BLISTER_PACK) {
                appendLine("Special Packaging Hint: Blister pack detected. Expiry date is often debossed into the foil crimp border or printed along the margin.")
            } else if (localResult.packagingType == app.memora.domain.extraction.PackagingType.BOTTLE) {
                appendLine("Special Packaging Hint: Bottle detected. Check the neck, shoulder, or cap for laser-etched or inkjet dot-matrix dates.")
            }
            appendLine("Is Ambiguous: ${localResult.isAmbiguous}")
            appendLine("Extracted Fields: $fieldsSummary")
            appendLine()
            appendLine("=== RAW OCR TEXT ===")
            appendLine(ocrResult.rawText)
        }

        val androidUri = runCatching {
            val parsedUri = Uri.parse(image.uri)
            if (parsedUri.scheme.isNullOrEmpty()) {
                Uri.fromFile(File(image.uri))
            } else {
                parsedUri
            }
        }.getOrNull()

        val geminiResult = geminiExtractor.extractCandidate(prompt, androidUri, scanId)

        return geminiResult.fold(
            onSuccess = { candidate ->
                val duration = System.currentTimeMillis() - startTime
                val disposition = when {
                    candidate.alternatives.isNotEmpty() -> ExtractionDisposition.AMBIGUOUS
                    candidate.suggestedEventDate == null -> ExtractionDisposition.NO_ACTIONABLE_DATE
                    candidate.requiresConfirmation && candidate.confidence >= 0.9f -> ExtractionDisposition.AMBIGUOUS
                    candidate.confidence >= 0.9f -> ExtractionDisposition.HIGH_CONFIDENCE
                    else -> ExtractionDisposition.LOW_CONFIDENCE
                }

                AppLog.recordFinalResult(
                    scanId = id,
                    disposition = disposition.name,
                    extractorUsed = "GeminiMemoryExtractor",
                    confidence = candidate.confidence,
                    parsingDurationMs = duration,
                    totalDurationMs = duration,
                )

                Result.success(
                    Stage3ExtractionResult(
                        candidate = candidate,
                        rawOcrText = ocrResult.rawText,
                        normalizedText = ocrResult.rawText.trim(),
                        disposition = disposition,
                        alternatives = candidate.alternatives,
                    )
                )
            },
            onFailure = { error ->
                AppLog.w("GEMINI_REFINE", "[#$id] Gemini refinement failed: ${error.message}")

                // Fallback rules: Only fall back to local result if unambiguous and has valid date!
                if (!localResult.isAmbiguous && localResult.selectedEventDate != null) {
                    AppLog.i("GEMINI_REFINE", "[#$id] Unambiguous local result present (${localResult.selectedEventDate}); falling back to local extraction")
                    val fallbackCandidate = ExtractedMemoryCandidate(
                        suggestedTitle = localResult.title,
                        suggestedEventType = localResult.eventType ?: EventType.OTHER,
                        suggestedEventDate = localResult.selectedEventDate,
                        confidence = localResult.confidence,
                        rawTextSnippet = ocrResult.rawText.take(200).ifBlank { null },
                        requiresConfirmation = true,
                        alternatives = localResult.dateCandidates,
                    )
                    Result.success(
                        Stage3ExtractionResult(
                            candidate = fallbackCandidate,
                            rawOcrText = ocrResult.rawText,
                            normalizedText = ocrResult.rawText.trim(),
                            disposition = ExtractionDisposition.HIGH_CONFIDENCE,
                            alternatives = localResult.dateCandidates,
                        )
                    )
                } else {
                    // If ambiguous or missing date, do not guess! Surface failure for user retry, manual entry, or rescan
                    AppLog.w("GEMINI_REFINE", "[#$id] Local result is ambiguous or missing date; propagating Gemini failure")
                    Result.failure(error)
                }
            }
        )
    }
}
