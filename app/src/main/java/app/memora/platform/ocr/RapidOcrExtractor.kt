package app.memora.platform.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import app.memora.platform.image.ImagePreprocessor
import app.memora.platform.image.ScanRegionCropper
import app.memora.platform.logging.AppLog
import com.benjaminwan.ocrlibrary.OcrEngine
import com.benjaminwan.ocrlibrary.OcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.memora.domain.extraction.DateRole
import java.io.File

/**
 * Concrete [TextExtractor] backed by RapidOCR / NCNN PaddleOCR.
 *
 * Implements advanced on-device extraction tuned for packaging and consumer goods:
 * 1. High-Density Viewfinder Reticle Cropping via [ScanRegionCropper].
 * 2. Multi-Orientation & Multi-Variant Sequential Inference:
 *    - 0°, 90°, and 270° orientations for sideways packages and vertical crimps.
 *    - Original high-DPI reticle crop (fastest).
 *    - Contrast-enhanced grayscale for faint inkjet & dot-matrix stamps.
 *    - Inverted color for reflective aluminum foil seals, silver cans, and dark packaging.
 * 3. Independent per-box angle classification (`mostAngle = false`) to handle mixed
 *    horizontal brand text and vertical expiry stamps.
 * 4. DBNet parameter tuning: `unClipRatio = 2.0f`, `boxScoreThresh = 0.40f`, `boxThresh = 0.20f`.
 * 5. Full-width ASCII & dot-matrix digit disambiguation via [RapidOcrTextNormalizer].
 * 6. Early exit on detection of anchored dates, maintaining <= 2 bitmaps resident in memory.
 */
data class RapidOcrConfig(
    val maxSideLen: Int = 1536,
    val boxScoreThresh: Float = 0.40f,
    val boxThresh: Float = 0.20f,
    val unClipRatio: Float = 2.0f,
    val numThreads: Int = 4,
    val doAngle: Boolean = true,
    val mostAngle: Boolean = false,
    val isTwoStage: Boolean = false,
) {
    companion object {
        val DEFAULT = RapidOcrConfig()
    }
}

class RapidOcrExtractor(
    private val context: Context,
    val config: RapidOcrConfig = RapidOcrConfig.DEFAULT,
) : TextExtractor {

    companion object {
        const val SCAN_TIME_BUDGET_MS = 8000L
    }

    private val deterministicExtractor by lazy { app.memora.domain.extraction.DeterministicMemoryExtractor() }

    // Eager/lazy initialization of RapidOCR engine with tuned parameters.
    private val ocrEngine by lazy {
        OcrEngine(context).apply {
            padding = 10
            boxScoreThresh = config.boxScoreThresh
            boxThresh = config.boxThresh
            unClipRatio = config.unClipRatio
            doAngle = config.doAngle
            mostAngle = config.mostAngle
            if (config.numThreads != 4) {
                init(
                    context.assets,
                    config.numThreads,
                    "ch_PP-OCRv3_det_infer",
                    "ch_ppocr_mobile_v2.0_cls_infer",
                    "ch_PP-OCRv3_rec_infer",
                    "ppocr_keys_v1.txt"
                )
            }
        }
    }

    override suspend fun extractText(imageUri: String): Result<ExtractedText> =
        extractText(imageUri, null)

    override suspend fun extractText(imageUri: String, scanId: String?): Result<ExtractedText> = withContext(Dispatchers.IO) {
        val id = scanId ?: "N/A"
        val startTime = System.currentTimeMillis()
        try {
            AppLog.checkMainThreadViolation("OCR")
            AppLog.recordOcrStart(id, triggerReason = "UNKNOWN", dispatcherLabel = "IO")
            AppLog.i("OCR", "[#$id] RapidOCR multi-variant processing started | dispatcher=IO")

            val pipelineResult = runProductionOcrPipeline(
                imageUri = imageUri,
                scanId = id,
                runAllPasses = false,
                captureEvaluationMetrics = false,
            ).getOrThrow()

            val finalResult = pipelineResult.extractedText
            val duration = System.currentTimeMillis() - startTime
            val rawText = finalResult.rawText
            val charCount = rawText.length
            val wordCount = rawText.split(Regex("\\s+")).count { it.isNotBlank() }

            AppLog.i("OCR", "[#$id] RapidOCR completed in ${duration}ms using ${pipelineResult.winningVariant} | chars=$charCount")
            AppLog.recordOcr(
                scanId = id,
                durationMs = duration,
                charCount = charCount,
                wordCount = wordCount,
                rawText = rawText,
                dispatcherLabel = "IO",
            )

            Result.success(finalResult)
        } catch (e: Throwable) {
            val duration = System.currentTimeMillis() - startTime
            AppLog.recordOcr(
                scanId = id,
                durationMs = duration,
                charCount = 0,
                wordCount = 0,
                rawText = null,
                error = e.message ?: "RapidOCR error",
                dispatcherLabel = "IO",
            )
            Result.failure(e)
        }
    }

    /**
     * Evaluation wrapper that invokes the exact same [runProductionOcrPipeline] as [extractText],
     * capturing per-pass latency, raw OCR text, and structured blocks.
     *
     * @param runAllPasses When true ("Run ALL passes (no early exit)" toggle), executes every
     *   preprocessing and rotation pass even after an anchored date is found, while still
     *   preserving the production winner selection rules for `extractedText` and `winningPass`.
     */
    suspend fun extractForEvaluation(
        imageUri: String,
        runAllPasses: Boolean = false,
        forceFallbackCrop: Boolean = false,
    ): Result<RapidOcrEvaluationData> = withContext(Dispatchers.IO) {
        runProductionOcrPipeline(
            imageUri = imageUri,
            scanId = null,
            runAllPasses = runAllPasses,
            captureEvaluationMetrics = true,
            forceFallbackCrop = forceFallbackCrop,
        )
    }

    /**
     * Single shared production OCR pipeline called by both [extractText] and [extractForEvaluation].
     * No duplicated scan logic: identical cropping, rotation, contrast/inversion preprocessing,
     * full-image fallback, and winner selection.
     */
    private fun runProductionOcrPipeline(
        imageUri: String,
        scanId: String?,
        runAllPasses: Boolean,
        captureEvaluationMetrics: Boolean,
        forceFallbackCrop: Boolean = false,
    ): Result<RapidOcrEvaluationData> {
        val startTime = System.currentTimeMillis()
        return try {
            val parsedUri = Uri.parse(imageUri)
            val filePath = if (parsedUri.scheme == null || parsedUri.scheme == "file") {
                parsedUri.path ?: imageUri
            } else {
                imageUri
            }

            val file = File(filePath)
            if (!file.exists()) {
                return Result.failure(IllegalArgumentException("Image file not found: $imageUri"))
            }

            val passesList = mutableListOf<RapidOcrPassInfo>()
            var bestResult: ExtractedText? = null
            var bestScore = -1
            var winningVariant = "NONE"

            fun recordPass(
                passName: String,
                durationMs: Long,
                result: ExtractedText,
                decodeCropMs: Long = 0L,
                detMs: Double = 0.0,
                recMs: Double = 0.0,
                boxesDetected: Int = 0,
                boxesRecognized: Int = 0,
                msPerBox: Double = 0.0,
            ) {
                AppLog.i(
                    "OCR_STAGE",
                    "Stage timing [pass=$passName]: decodeCrop=${decodeCropMs}ms, det=${detMs.toLong()}ms, rec=${recMs.toLong()}ms, boxes=$boxesDetected (rec=$boxesRecognized), ms_per_box=${String.format(java.util.Locale.US, "%.1f", msPerBox)}ms, passMs=${durationMs}ms"
                )
                if (captureEvaluationMetrics) {
                    passesList.add(
                        RapidOcrPassInfo(
                            passName = passName,
                            durationMs = durationMs,
                            rawText = result.rawText,
                            hasAnchoredDate = OcrDateDetector.hasAnchoredDate(result.rawText),
                            extractedText = result,
                            decodeCropMs = decodeCropMs,
                            detMs = detMs,
                            recMs = recMs,
                            boxesDetected = boxesDetected,
                            boxesRecognized = boxesRecognized,
                            msPerBox = msPerBox,
                        )
                    )
                }
            }

            fun updateBestCandidate(variantName: String, result: ExtractedText) {
                val score = scoreCandidateQuality(result)
                if (bestResult == null || score > bestScore) {
                    bestResult = result
                    bestScore = score
                    winningVariant = variantName
                }
            }

            var budgetHit = false
            fun isBudgetExceeded(): Boolean {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= SCAN_TIME_BUDGET_MS) {
                    if (!budgetHit) {
                        AppLog.w("OCR", "budget_hit: time budget of ${SCAN_TIME_BUDGET_MS}ms exceeded ($elapsed ms)")
                        budgetHit = true
                    }
                    return true
                }
                return false
            }

            var consecutiveEmptyPasses = 0
            var showsVerticalText = false

            // Step 1: Decode reticle crop via ScanRegionCropper
            val tCrop = System.currentTimeMillis()
            val baseCroppedBitmap = ScanRegionCropper.cropReticleRegion(filePath, forceFallbackCrop = forceFallbackCrop)
            val cropDurationMs = System.currentTimeMillis() - tCrop
            AppLog.i("OCR_STAGE", "Stage timing [decode_crop]: duration=${cropDurationMs}ms")

            if (baseCroppedBitmap != null) {
                try {
                    if (scanId != null) {
                        AppLog.recordImageMemory(baseCroppedBitmap.width, baseCroppedBitmap.height, "ARGB_8888", scanId = scanId)
                    }

                    // Pass 1: Original reticle crop (0 deg)
                    val initialMaxSide = if (config.isTwoStage) 1024 else config.maxSideLen
                    val tOrig = System.currentTimeMillis()
                    val v1Det = detectBitmap(baseCroppedBitmap, initialMaxSide)
                    val v1Result = v1Det.extractedText
                    recordPass("original", System.currentTimeMillis() - tOrig, v1Result, cropDurationMs, v1Det.dbNetTimeMs, v1Det.recTimeMs, v1Det.boxesDetected, v1Det.boxesRecognized, v1Det.msPerBox)
                    updateBestCandidate("RETICLE_ORIGINAL", v1Result)
                    if (hasVerticalTextEvidence(v1Result)) showsVerticalText = true

                    // Two-stage read: re-read at full resolution (1536) only if no date is found
                    if (config.isTwoStage && bestScore < 5000 && (runAllPasses || !isBudgetExceeded())) {
                        val tOrigFull = System.currentTimeMillis()
                        val v1FullDet = detectBitmap(baseCroppedBitmap, 1536)
                        val v1FullResult = v1FullDet.extractedText
                        recordPass("original_full", System.currentTimeMillis() - tOrigFull, v1FullResult, 0L, v1FullDet.dbNetTimeMs, v1FullDet.recTimeMs, v1FullDet.boxesDetected, v1FullDet.boxesRecognized, v1FullDet.msPerBox)
                        updateBestCandidate("RETICLE_ORIGINAL_FULL", v1FullResult)
                        if (hasVerticalTextEvidence(v1FullResult)) showsVerticalText = true
                    }

                    val hasDateOrKw1 = hasPackagingKeyword(v1Result.rawText) || hasDateShapedToken(v1Result.rawText)
                    if (!hasDateOrKw1) consecutiveEmptyPasses++ else consecutiveEmptyPasses = 0

                    // Stop early on an anchored date
                    var stopEarly = !runAllPasses && bestScore >= 10000

                    // Pass 2: Contrast-enhanced grayscale
                    if (!stopEarly && (runAllPasses || !isBudgetExceeded())) {
                        val tContrast = System.currentTimeMillis()
                        val v2Bitmap = ImagePreprocessor.createContrastEnhancedVariant(baseCroppedBitmap)
                        val v2Det = try {
                            detectBitmap(v2Bitmap, config.maxSideLen)
                        } finally {
                            v2Bitmap.recycle()
                        }
                        val v2Result = v2Det.extractedText
                        recordPass("contrast", System.currentTimeMillis() - tContrast, v2Result, 0L, v2Det.dbNetTimeMs, v2Det.recTimeMs, v2Det.boxesDetected, v2Det.boxesRecognized, v2Det.msPerBox)
                        updateBestCandidate("RETICLE_CONTRAST", v2Result)
                        if (hasVerticalTextEvidence(v2Result)) showsVerticalText = true

                        val hasDateOrKw2 = hasPackagingKeyword(v2Result.rawText) || hasDateShapedToken(v2Result.rawText)
                        if (!hasDateOrKw2) consecutiveEmptyPasses++ else consecutiveEmptyPasses = 0

                        if (!runAllPasses && (bestScore >= 10000 || consecutiveEmptyPasses >= 2)) {
                            stopEarly = true
                        }
                    }

                    // Pass 3: Inverted color (foil packaging, silver tins, dark bottles)
                    if (!stopEarly && (runAllPasses || !isBudgetExceeded())) {
                        val tInverted = System.currentTimeMillis()
                        val v3Bitmap = ImagePreprocessor.createInvertedVariant(baseCroppedBitmap)
                        val v3Det = try {
                            detectBitmap(v3Bitmap, config.maxSideLen)
                        } finally {
                            v3Bitmap.recycle()
                        }
                        val v3Result = v3Det.extractedText
                        recordPass("inverted", System.currentTimeMillis() - tInverted, v3Result, 0L, v3Det.dbNetTimeMs, v3Det.recTimeMs, v3Det.boxesDetected, v3Det.boxesRecognized, v3Det.msPerBox)
                        updateBestCandidate("RETICLE_INVERTED", v3Result)
                        if (hasVerticalTextEvidence(v3Result)) showsVerticalText = true

                        val hasDateOrKw3 = hasPackagingKeyword(v3Result.rawText) || hasDateShapedToken(v3Result.rawText)
                        if (!hasDateOrKw3) consecutiveEmptyPasses++ else consecutiveEmptyPasses = 0

                        if (!runAllPasses && (bestScore >= 10000 || consecutiveEmptyPasses >= 2)) {
                            stopEarly = true
                        }
                    }

                    // Rotations: 90/270 rotations only if earlier passes show vertical text
                    val needRotations = showsVerticalText && bestScore < 10000 && consecutiveEmptyPasses < 2
                    if (!stopEarly && (needRotations || runAllPasses)) {
                        // Pass 4: Rotated 90 (essential for vertical packaging)
                        if (runAllPasses || !isBudgetExceeded()) {
                            val tRot90 = System.currentTimeMillis()
                            val rot90Bmp = rotateBitmap(baseCroppedBitmap, 90f)
                            val rot90Det = try {
                                detectBitmap(rot90Bmp, config.maxSideLen)
                            } finally {
                                rot90Bmp.recycle()
                            }
                            val rot90Result = rot90Det.extractedText
                            recordPass("rotated_90", System.currentTimeMillis() - tRot90, rot90Result, 0L, rot90Det.dbNetTimeMs, rot90Det.recTimeMs, rot90Det.boxesDetected, rot90Det.boxesRecognized, rot90Det.msPerBox)
                            updateBestCandidate("RETICLE_ORIGINAL_90DEG", rot90Result)

                            if (!runAllPasses && bestScore >= 10000) {
                                stopEarly = true
                            }
                        }

                        // Pass 5: Rotated 270 (only if still no anchored date AND under budget)
                        if (!stopEarly && (bestScore < 10000 || runAllPasses) && (runAllPasses || !isBudgetExceeded())) {
                            val tRot270 = System.currentTimeMillis()
                            val rot270Bmp = rotateBitmap(baseCroppedBitmap, 270f)
                            val rot270Det = try {
                                detectBitmap(rot270Bmp, config.maxSideLen)
                            } finally {
                                rot270Bmp.recycle()
                            }
                            val rot270Result = rot270Det.extractedText
                            recordPass("rotated_270", System.currentTimeMillis() - tRot270, rot270Result, 0L, rot270Det.dbNetTimeMs, rot270Det.recTimeMs, rot270Det.boxesDetected, rot270Det.boxesRecognized, rot270Det.msPerBox)
                            updateBestCandidate("RETICLE_ORIGINAL_270DEG", rot270Result)

                            if (!runAllPasses && bestScore >= 10000) {
                                stopEarly = true
                            }
                        }

                        // Pass 6: Contrast 90 (only if still no anchored date AND under budget)
                        if (!stopEarly && (bestScore < 10000 || runAllPasses) && (runAllPasses || !isBudgetExceeded())) {
                            val tContrast90 = System.currentTimeMillis()
                            val v2Bmp = ImagePreprocessor.createContrastEnhancedVariant(baseCroppedBitmap)
                            val v2Rot90 = rotateBitmap(v2Bmp, 90f)
                            v2Bmp.recycle()
                            val v2Rot90Det = try {
                                detectBitmap(v2Rot90, config.maxSideLen)
                            } finally {
                                v2Rot90.recycle()
                            }
                            val v2Rot90Result = v2Rot90Det.extractedText
                            recordPass("contrast_90", System.currentTimeMillis() - tContrast90, v2Rot90Result, 0L, v2Rot90Det.dbNetTimeMs, v2Rot90Det.recTimeMs, v2Rot90Det.boxesDetected, v2Rot90Det.boxesRecognized, v2Rot90Det.msPerBox)
                            updateBestCandidate("RETICLE_CONTRAST_90DEG", v2Rot90Result)
                        }
                    }
                } finally {
                    baseCroppedBitmap.recycle()
                }
            }

            val finalResult = bestResult ?: ExtractedText(rawText = "", blocks = emptyList())
            val totalMs = System.currentTimeMillis() - startTime
            val normalizedWinningPass = mapVariantToPassCategory(winningVariant)

            Result.success(
                RapidOcrEvaluationData(
                    extractedText = finalResult,
                    winningVariant = winningVariant,
                    winningPass = normalizedWinningPass,
                    totalMs = totalMs,
                    passes = passesList,
                    isBudgetExceeded = budgetHit,
                )
            )
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun mapVariantToPassCategory(variant: String): String = when (variant) {
        "RETICLE_ORIGINAL", "RETICLE_ORIGINAL_FULL", "FULL_IMAGE_FALLBACK" -> "original"
        "RETICLE_CONTRAST" -> "contrast"
        "RETICLE_INVERTED" -> "inverted"
        "RETICLE_ORIGINAL_90DEG",
        "RETICLE_ORIGINAL_270DEG",
        "RETICLE_CONTRAST_90DEG",
        "RETICLE_INVERTED_90DEG",
        "FULL_IMAGE_FALLBACK_90DEG" -> "rotated"
        else -> "original"
    }

    private fun hasPackagingKeyword(text: String): Boolean {
        if (text.isBlank()) return false
        val normalized = app.memora.domain.extraction.DeterministicMemoryExtractor.normalizeForExtraction(text).uppercase()
        return normalized.contains("EXP") || normalized.contains("USE BY") ||
            normalized.contains("BEST BEFORE") || normalized.contains("MFG") ||
            normalized.contains("MFD") || normalized.contains("PKD") ||
            normalized.contains("BB") || normalized.contains("SE BY") ||
            normalized.contains("USE BEFORE") || normalized.contains("PACKED") ||
            normalized.contains("VALID") || normalized.contains("SHELF") ||
            normalized.contains("CONSUME") || normalized.contains("BATCH") ||
            normalized.contains("B.NO")
    }

    private fun hasDateShapedToken(text: String): Boolean {
        if (text.isBlank()) return false
        val normalized = app.memora.domain.extraction.DeterministicMemoryExtractor.normalizeForExtraction(text)
        return Regex("""\b(?:0?[1-9]|[12]\d|3[01])[/.-](?:0?[1-9]|1[0-2])[/.-](?:20\d{2}|\d{2})\b""").containsMatchIn(normalized) ||
            Regex("""\b(?:0?[1-9]|1[0-2])[/.-](?:20\d{2}|2[4-9])\b""").containsMatchIn(normalized) ||
            Regex("""\b202[4-9]\b""").containsMatchIn(normalized) ||
            Regex("""\b(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\w*[\s,.-]*(?:20\d{2}|\d{2})\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) ||
            Regex("""\b(?:0?[1-9]|[12]\d|3[01])\s*(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) ||
            Regex("""(?<!\d)\d{6,8}(?!\d)""").containsMatchIn(normalized)
    }

    private fun hasVerticalTextEvidence(extractedText: ExtractedText): Boolean {
        // 1. Text elements with height significantly greater than width
        val hasTallElement = extractedText.blocks.any { block ->
            block.lines.any { line ->
                line.elements.any { elem ->
                    val box = elem.boundingBox
                    box != null && box.height() > box.width() * 1.25f && box.height() > 20
                }
            }
        }
        if (hasTallElement) return true

        // 2. Lines that are taller than wide
        val hasTallLine = extractedText.blocks.any { block ->
            block.lines.any { line ->
                val box = line.boundingBox
                box != null && box.height() > box.width() * 1.0f && box.height() > 15
            }
        }
        if (hasTallLine) return true

        // 3. Spaced single characters indicating horizontal scan cutting across vertical columns
        val singleCharPattern = Regex("""(?:^|\s)[A-Za-z0-9\u4e00-\u9fff](\s+[A-Za-z0-9\u4e00-\u9fff]){3,}(?:\s|$)""")
        if (singleCharPattern.containsMatchIn(extractedText.rawText)) return true

        // 4. Multiple single-character lines
        val lines = extractedText.rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val shortLinesCount = lines.count { it.length <= 2 }
        if (shortLinesCount >= 2) return true

        // 5. High frequency of isolated 1-character tokens or Chinese characters from slicing vertical English text
        val tokens = extractedText.rawText.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val singleTokens = tokens.count { it.length == 1 }
        val chineseCount = tokens.count { Regex("""[\u4e00-\u9fff]""").containsMatchIn(it) }
        if (singleTokens >= 10 || chineseCount >= 3) return true

        return false
    }

    private fun scoreCandidateQuality(extractedText: ExtractedText): Int {
        if (extractedText.rawText.isBlank()) return 0
        val localResult = deterministicExtractor.extractLocalResult(extractedText.rawText)
        val selectedDate = localResult.selectedEventDate
        val conf = localResult.confidence
        val isAnchoredExpiry = selectedDate != null && conf >= 0.8f
        val hasMfgAnchor = localResult.dateCandidates.any { !it.role.isActionableReminder }
        val isUnanchoredDate = selectedDate != null && conf < 0.8f

        return when {
            isAnchoredExpiry -> 10000 + (conf * 1000).toInt()
            hasMfgAnchor && selectedDate == null -> 6000 + (localResult.dateCandidates.maxOfOrNull { it.scoreComponents.totalScore }?.toInt() ?: 0)
            isUnanchoredDate -> 5000 + (conf * 1000).toInt()
            localResult.dateCandidates.isNotEmpty() -> 1000 + minOf(500, localResult.dateCandidates.size * 100)
            hasPackagingKeyword(extractedText.rawText) -> 500
            hasDateShapedToken(extractedText.rawText) -> 300
            extractedText.blocks.isNotEmpty() -> 100
            else -> 10
        }
    }

    override val isAvailable: Boolean = true

    data class BitmapDetectionResult(
        val extractedText: ExtractedText,
        val dbNetTimeMs: Double,
        val recTimeMs: Double,
        val detectTimeMs: Double,
        val boxesDetected: Int = 0,
        val boxesRecognized: Int = 0,
        val msPerBox: Double = 0.0,
    )

    /**
     * Executes RapidOCR inference on [bitmap] at the given [maxSideLen],
     * strictly recycling the intermediate box image and any temporary bitmap copy.
     */
    private fun detectBitmap(bitmap: Bitmap, maxSideLen: Int): BitmapDetectionResult {
        val argbBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888 || !bitmap.isMutable) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            bitmap
        }
        val boxImg = Bitmap.createBitmap(argbBitmap.width, argbBitmap.height, Bitmap.Config.ARGB_8888)
        val ocrResult = try {
            ocrEngine.detect(argbBitmap, boxImg, maxSideLen)
        } finally {
            boxImg.recycle()
            if (argbBitmap != bitmap) {
                argbBitmap.recycle()
            }
        }

        val detTime = ocrResult.dbNetTime
        val totalTime = ocrResult.detectTime
        val recTime = (totalTime - detTime).coerceAtLeast(0.0)
        val boxesDetected = ocrResult.textBlocks.size
        val extracted = processOcrResult(ocrResult)
        val boxesRecognized = extracted.blocks.sumOf { it.lines.sumOf { line -> line.elements.size } }
        val msPerBox = if (boxesDetected > 0) recTime / boxesDetected else 0.0

        return BitmapDetectionResult(
            extractedText = extracted,
            dbNetTimeMs = detTime,
            recTimeMs = recTime,
            detectTimeMs = totalTime,
            boxesDetected = boxesDetected,
            boxesRecognized = boxesRecognized,
            msPerBox = msPerBox,
        )
    }

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Maps RapidOCR detections into structured lines and blocks, preserving
     * bounding boxes, corner points, confidence scores, and reading order.
     */
    private fun processOcrResult(ocrResult: OcrResult): ExtractedText {
        val textBlocks = ocrResult.textBlocks
        if (textBlocks.isEmpty()) {
            return ExtractedText(rawText = "", blocks = emptyList())
        }

        val rawDetections = textBlocks.mapNotNull { block ->
            val normalizedText = RapidOcrTextNormalizer.normalize(block.text)
            if (normalizedText.isBlank()) return@mapNotNull null

            // Filter out low-confidence tiny noise specks
            if (block.boxScore < 0.35f && normalizedText.length <= 2) return@mapNotNull null

            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            val cornerPoints = mutableListOf<Point>()

            for (pt in block.boxPoint) {
                val x = pt.x
                val y = pt.y
                cornerPoints.add(Point(x, y))
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }

            val rect = Rect(minX, minY, maxX, maxY)
            RawDetection(
                text = normalizedText,
                boundingBox = rect,
                cornerPoints = cornerPoints,
                score = block.boxScore,
                angleIndex = block.angleIndex,
            )
        }

        if (rawDetections.isEmpty()) {
            return ExtractedText(rawText = "", blocks = emptyList())
        }

        // 1. Group detections on the same visual line (top to bottom, then left to right)
        val lines = groupDetectionsIntoLines(rawDetections)

        // 2. Group lines into visual blocks
        val blocks = groupLinesIntoBlocks(lines)
        val rawText = blocks.joinToString("\n\n") { it.text }

        return ExtractedText(
            rawText = rawText,
            suggestedTitle = null,
            suggestedDateIso = null,
            blocks = blocks,
        )
    }

    private data class RawDetection(
        val text: String,
        val boundingBox: Rect,
        val cornerPoints: List<Point>,
        val score: Float,
        val angleIndex: Int,
    ) {
        val height: Int get() = maxOf(1, boundingBox.height())
    }

    /**
     * Groups raw detections that share visual horizontal lines based on vertical overlap.
     * Orders lines top-to-bottom, and elements within each line left-to-right.
     */
    private fun groupDetectionsIntoLines(detections: List<RawDetection>): List<TextLineInfo> {
        val sorted = detections.sortedBy { it.boundingBox.top }
        val lineBuckets = mutableListOf<MutableList<RawDetection>>()

        for (det in sorted) {
            val matchingBucket = lineBuckets.firstOrNull { bucket ->
                val bucketTop = bucket.minOf { it.boundingBox.top }
                val bucketBottom = bucket.maxOf { it.boundingBox.bottom }
                val bucketHeight = maxOf(1, bucketBottom - bucketTop)
                val overlapTop = maxOf(det.boundingBox.top, bucketTop)
                val overlapBottom = minOf(det.boundingBox.bottom, bucketBottom)
                val overlapHeight = maxOf(0, overlapBottom - overlapTop)
                val minHeight = minOf(det.height, bucketHeight)
                overlapHeight >= minHeight * 0.4f
            }

            if (matchingBucket != null) {
                matchingBucket.add(det)
            } else {
                lineBuckets.add(mutableListOf(det))
            }
        }

        return lineBuckets.map { bucket ->
            val elementsInLine = bucket.sortedBy { it.boundingBox.left }
            val elementInfos = elementsInLine.map { det ->
                TextElementInfo(
                    text = det.text,
                    boundingBox = det.boundingBox,
                )
            }

            val lineText = elementInfos.joinToString(" ") { it.text }
            val minX = elementInfos.minOf { it.boundingBox?.left ?: 0 }
            val minY = elementInfos.minOf { it.boundingBox?.top ?: 0 }
            val maxX = elementInfos.maxOf { it.boundingBox?.right ?: 0 }
            val maxY = elementInfos.maxOf { it.boundingBox?.bottom ?: 0 }
            val lineBox = Rect(minX, minY, maxX, maxY)

            TextLineInfo(
                text = lineText,
                boundingBox = lineBox,
                elements = elementInfos,
            )
        }.sortedBy { it.boundingBox?.top ?: 0 }
    }

    /**
     * Groups visual lines into blocks based on vertical proximity.
     */
    private fun groupLinesIntoBlocks(lines: List<TextLineInfo>): List<TextBlockInfo> {
        if (lines.isEmpty()) return emptyList()

        val blockBuckets = mutableListOf<MutableList<TextLineInfo>>()
        for (line in lines) {
            val lastBucket = blockBuckets.lastOrNull()
            if (lastBucket == null) {
                blockBuckets.add(mutableListOf(line))
            } else {
                val prevLine = lastBucket.last()
                val prevBottom = prevLine.boundingBox?.bottom ?: 0
                val currTop = line.boundingBox?.top ?: 0
                val avgHeight = maxOf(1, ((prevLine.boundingBox?.height() ?: 0) + (line.boundingBox?.height() ?: 0)) / 2)
                val verticalGap = currTop - prevBottom

                if (verticalGap <= avgHeight * 2.5f) {
                    lastBucket.add(line)
                } else {
                    blockBuckets.add(mutableListOf(line))
                }
            }
        }

        return blockBuckets.map { linesInBlock ->
            val blockText = linesInBlock.joinToString("\n") { it.text }
            val minX = linesInBlock.minOf { it.boundingBox?.left ?: 0 }
            val minY = linesInBlock.minOf { it.boundingBox?.top ?: 0 }
            val maxX = linesInBlock.maxOf { it.boundingBox?.right ?: 0 }
            val maxY = linesInBlock.maxOf { it.boundingBox?.bottom ?: 0 }
            val blockBox = Rect(minX, minY, maxX, maxY)
            val cornerPoints = listOf(
                Point(minX, minY),
                Point(maxX, minY),
                Point(maxX, maxY),
                Point(minX, maxY)
            )

            TextBlockInfo(
                text = blockText,
                boundingBox = blockBox,
                cornerPoints = cornerPoints,
                lines = linesInBlock,
            )
        }
    }
}

data class RapidOcrPassInfo(
    val passName: String,
    val durationMs: Long,
    val rawText: String,
    val hasAnchoredDate: Boolean,
    val extractedText: ExtractedText = ExtractedText(rawText = rawText, blocks = emptyList()),
    val decodeCropMs: Long = 0L,
    val detMs: Double = 0.0,
    val recMs: Double = 0.0,
    val boxesDetected: Int = 0,
    val boxesRecognized: Int = 0,
    val msPerBox: Double = 0.0,
)

data class RapidOcrEvaluationData(
    val extractedText: ExtractedText,
    val winningVariant: String = "RETICLE_ORIGINAL",
    val winningPass: String,
    val totalMs: Long,
    val passes: List<RapidOcrPassInfo>,
    val isBudgetExceeded: Boolean = false,
)

