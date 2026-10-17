package app.memora.domain.usecase

import app.memora.domain.extraction.DateLocation
import app.memora.domain.extraction.DeterministicMemoryExtractor
import app.memora.domain.extraction.LocalScanOutput
import app.memora.domain.extraction.MarkingStyle
import app.memora.domain.extraction.OcrResult
import app.memora.domain.extraction.PackagingType
import app.memora.domain.model.CapturedImage
import app.memora.platform.logging.AppLog
import app.memora.platform.ocr.TextExtractor
import app.memora.platform.api.OpenFoodFactsApi
import app.memora.platform.ocr.BarcodeExtractor
import app.memora.domain.benchmark.ScannerBenchmarkEngine

/**
 * UseCase orchestrating local on-device extraction:
 * 1. Runs ML Kit local OCR on the captured image URI.
 * 2. Runs pure deterministic local extraction without external dependencies.
 * 3. Records diagnostics to AppLog outside the pure extractor boundary.
 * 4. Yields structured [LocalScanOutput] containing separated [OcrResult] and [LocalExtractionResult].
 *
 * Guaranteed Cost Invariant: Exactly 0 Gemini network calls occur during this execution.
 */
class ExtractLocallyUseCase(
    private val textExtractor: TextExtractor,
    private val barcodeExtractor: BarcodeExtractor,
    private val deterministicExtractor: DeterministicMemoryExtractor,
) {
    constructor(
        textExtractor: TextExtractor,
        deterministicExtractor: DeterministicMemoryExtractor,
    ) : this(
        textExtractor = textExtractor,
        barcodeExtractor = object : BarcodeExtractor {
            override suspend fun extractBarcode(imageUri: String): Result<String?> = Result.success(null)
        },
        deterministicExtractor = deterministicExtractor,
    )

    suspend operator fun invoke(
        image: CapturedImage,
        scanId: String? = null,
        packagingType: PackagingType = PackagingType.UNKNOWN,
        dateLocation: DateLocation = DateLocation.UNKNOWN,
        markingStyle: MarkingStyle = MarkingStyle.UNKNOWN,
    ): Result<LocalScanOutput> {
        val id = scanId ?: "N/A"
        
        ScannerBenchmarkEngine.startRun(inputId = "SCAN_${id}")
        
        val startTime = System.currentTimeMillis()
        
        ScannerBenchmarkEngine.markOcrStart()
        val textResult = textExtractor.extractText(image.uri, scanId)

        return textResult.fold(
            onSuccess = { extractedText ->
                
                ScannerBenchmarkEngine.markOcrComplete(
                    success = true,
                    textBlockCount = extractedText.blocks.size,
                    lineCount = extractedText.blocks.sumOf { it.lines.size },
                    elementCount = extractedText.blocks.sumOf { b -> b.lines.sumOf { l -> l.elements.size } }
                )
                
                val ocrResult = OcrResult(
                    rawText = extractedText.rawText,
                    confidence = null,
                )
                
                ScannerBenchmarkEngine.markParsingStart()
                val parseStartTime = System.currentTimeMillis()

                // Extract deterministic memory fields from structured blocks in natural reading order
                val parsedLocalResult = if (extractedText.blocks.isNotEmpty()) {
                    deterministicExtractor.extractLocalResult(extractedText.blocks)
                } else {
                    deterministicExtractor.extractLocalResult(extractedText.rawText)
                }
                
                ScannerBenchmarkEngine.markParsingComplete(
                    titleLength = parsedLocalResult.title?.length ?: 0,
                    date = parsedLocalResult.selectedEventDate?.toString()
                )
                
                var localResult = parsedLocalResult.copy(
                    packagingType = if (packagingType != PackagingType.UNKNOWN) packagingType else parsedLocalResult.packagingType,
                    dateLocation = if (dateLocation != DateLocation.UNKNOWN) dateLocation else parsedLocalResult.dateLocation,
                    markingStyle = if (markingStyle != MarkingStyle.UNKNOWN) markingStyle else parsedLocalResult.markingStyle,
                )

                // --- Phase 3: Barcode Lookup ---
                ScannerBenchmarkEngine.markBarcodeStart()
                val barcodeResult = barcodeExtractor.extractBarcode(image.uri)
                if (barcodeResult.isSuccess) {
                    val barcode = barcodeResult.getOrNull()
                    if (!barcode.isNullOrBlank()) {
                        val productTitle = OpenFoodFactsApi.lookupBarcode(barcode)
                        if (!productTitle.isNullOrBlank()) {
                            // Only overwrite if the local extractor didn't find a meaningful title.
                            // Preserves specific OCR titles (e.g. "Paracetamol 500mg Expiry")
                            // from being replaced by generic barcode names.
                            if (localResult.title.isNullOrBlank() || localResult.title == "Untitled Memory") {
                                localResult = localResult.copy(title = productTitle)
                            }
                        } else if (localResult.title.isNullOrBlank() || localResult.title == "Untitled Memory") {
                            localResult = localResult.copy(title = "Barcode: $barcode")
                        }
                    }
                }
                ScannerBenchmarkEngine.markBarcodeComplete()
                ScannerBenchmarkEngine.finishRun()

                val parseDuration = System.currentTimeMillis() - parseStartTime
                val totalDuration = System.currentTimeMillis() - startTime

                // Record diagnostics outside pure extractor
                localResult.dateCandidates.forEach { candidate ->
                    AppLog.recordDateCandidate(
                        scanId = id,
                        rawCandidate = candidate.context, // The original snippet
                        normalizedDate = candidate.eventDate.toString(),
                        contextLabel = candidate.context,
                        classification = candidate.eventType.name,
                        actionable = true // From deterministic extractor, these are parsed
                    )
                }

                AppLog.recordCandidateParsed(
                    scanId = id,
                    title = localResult.title,
                    date = localResult.selectedEventDate?.toString(),
                    eventType = (localResult.eventType ?: app.memora.domain.model.EventType.OTHER).name,
                    confidence = localResult.confidence,
                    alternatives = localResult.dateCandidates.map { "${it.eventDate} (${it.context})" }
                )

                AppLog.recordDateSelection(
                    scanId = id,
                    candidates = localResult.dateCandidates.map { "${it.eventDate} (${it.context})" },
                    selectedDate = localResult.selectedEventDate?.toString(),
                    reason = when {
                        localResult.isAmbiguous -> "Ambiguous: multiple candidate dates detected"
                        localResult.selectedEventDate != null -> "Deterministic local extraction selected ${localResult.selectedEventDate}"
                        else -> "No actionable dates found in local OCR"
                    }
                )

                AppLog.recordFinalResult(
                    scanId = id,
                    disposition = if (localResult.isAmbiguous) "AMBIGUOUS" else if (localResult.selectedEventDate != null) "LOCAL_EXTRACTED" else "NO_ACTIONABLE_DATE",
                    extractorUsed = "DeterministicMemoryExtractor",
                    confidence = localResult.confidence,
                    parsingDurationMs = parseDuration,
                    totalDurationMs = totalDuration,
                )

                Result.success(LocalScanOutput(ocrResult, localResult))
            },
            onFailure = { error ->
                val totalDuration = System.currentTimeMillis() - startTime
                AppLog.recordFinalResult(
                    scanId = id,
                    disposition = "OCR_FAILED",
                    extractorUsed = "None",
                    confidence = 0f,
                    parsingDurationMs = 0L,
                    totalDurationMs = totalDuration,
                )
                Result.failure(error)
            }
        )
    }

    /**
     * Reconstructs OCR text using spatial bounding boxes so that labels like "EXP" or "BEST BEFORE"
     * are placed on the same line as their nearest date, even if ML Kit put them on different lines.
     *
     * For each text element that looks like a date keyword, finds the nearest date element
     * by center-point pixel distance and merges them into one line.
     */
    private fun buildSpatialText(blocks: List<app.memora.platform.ocr.TextBlockInfo>): String {
        // Collect all individual text elements with their center coordinates
        data class PositionedElement(val text: String, val centerX: Int, val centerY: Int)

        val elements = mutableListOf<PositionedElement>()
        for (block in blocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val box = element.boundingBox ?: continue
                    elements.add(PositionedElement(
                        text = element.text,
                        centerX = box.centerX(),
                        centerY = box.centerY(),
                    ))
                }
            }
        }

        if (elements.isEmpty()) {
            return blocks.joinToString("\n") { it.text }
        }

        // Identify keyword elements and date-like elements
        val dateKeywords = setOf("EXP", "EXPIRY", "EXPIRES", "EXPIRATION", "BEST", "BEFORE",
            "USE", "BY", "MFG", "MFD", "MFGD", "PKD", "DUE", "PAY", "BB", "WARRANTY")
        val datePattern = Regex("""(?:\d{1,2}[/.\-\s]\d{1,2}[/.\-\s]\d{2,4})|(?:\d{1,2}[/.\-]\d{2,4})|(?:\d{4}[/.\-]\d{2}[/.\-]\d{2})""")

        val keywordElements = elements.filter { it.text.uppercase().trim() in dateKeywords }
        val dateElements = elements.filter { datePattern.containsMatchIn(it.text) }

        // For each keyword element, find the nearest date element by pixel distance
        // and group them together as "KEYWORD DATE" on one line
        val pairedDates = mutableSetOf<PositionedElement>()
        val mergedLines = mutableListOf<String>()

        for (kw in keywordElements) {
            val nearest = dateElements
                .filter { it !in pairedDates }
                .minByOrNull { de ->
                    val dx = (kw.centerX - de.centerX).toDouble()
                    val dy = (kw.centerY - de.centerY).toDouble()
                    kotlin.math.sqrt(dx * dx + dy * dy)
                }
            if (nearest != null) {
                val dx = (kw.centerX - nearest.centerX).toDouble()
                val dy = (kw.centerY - nearest.centerY).toDouble()
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                // Only pair if within reasonable proximity (150px covers most label-to-date gaps)
                if (dist < 300) {
                    pairedDates.add(nearest)
                    mergedLines.add("${kw.text} ${nearest.text}")
                }
            }
        }

        // Also include the original block-level text to preserve all other information
        val originalText = blocks.joinToString("\n") { it.text }
        val spatialAdditions = mergedLines.joinToString("\n")

        return if (spatialAdditions.isNotBlank()) {
            "$spatialAdditions\n$originalText"
        } else {
            originalText
        }
    }
}
