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
class RapidOcrExtractor(
    private val context: Context,
) : TextExtractor {

    // Eager/lazy initialization of RapidOCR engine with tuned parameters.
    private val ocrEngine by lazy {
        OcrEngine(context).apply {
            padding = 10
            boxScoreThresh = 0.40f
            boxThresh = 0.20f
            unClipRatio = 2.0f
            doAngle = true
            mostAngle = false // Independent angle classification for each text box
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
    ): Result<RapidOcrEvaluationData> = withContext(Dispatchers.IO) {
        runProductionOcrPipeline(
            imageUri = imageUri,
            scanId = null,
            runAllPasses = runAllPasses,
            captureEvaluationMetrics = true,
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
            var winningVariant = "NONE"

            fun recordPass(passName: String, durationMs: Long, result: ExtractedText) {
                if (captureEvaluationMetrics) {
                    passesList.add(
                        RapidOcrPassInfo(
                            passName = passName,
                            durationMs = durationMs,
                            rawText = result.rawText,
                            hasAnchoredDate = OcrDateDetector.hasAnchoredDate(result.rawText),
                            extractedText = result,
                        )
                    )
                }
            }

            // Step 1: Decode reticle crop via ScanRegionCropper
            val baseCroppedBitmap = ScanRegionCropper.cropReticleRegion(filePath)

            if (baseCroppedBitmap != null) {
                try {
                    if (scanId != null) {
                        AppLog.recordImageMemory(baseCroppedBitmap.width, baseCroppedBitmap.height, "ARGB_8888", scanId = scanId)
                    }

                    // Variant 1: Original reticle crop (0 deg)
                    val tOrig = System.currentTimeMillis()
                    val v1Result = detectBitmap(baseCroppedBitmap, 1536)
                    recordPass("original", System.currentTimeMillis() - tOrig, v1Result)
                    bestResult = v1Result
                    winningVariant = "RETICLE_ORIGINAL"

                    // Multi-orientation check (90 deg, 270 deg) if anchored date not yet found (or if runAllPasses is enabled)
                    val needRot90 = !OcrDateDetector.hasAnchoredDate(bestResult.rawText)
                    if (needRot90 || runAllPasses) {
                        val tRot90 = System.currentTimeMillis()
                        val rot90Bmp = rotateBitmap(baseCroppedBitmap, 90f)
                        try {
                            val rot90Result = detectBitmap(rot90Bmp, 1536)
                            recordPass("rotated_90", System.currentTimeMillis() - tRot90, rot90Result)
                            if (needRot90) {
                                if (OcrDateDetector.hasAnchoredDate(rot90Result.rawText)) {
                                    bestResult = rot90Result
                                    winningVariant = "RETICLE_ORIGINAL_90DEG"
                                } else if (rot90Result.rawText.length > (bestResult?.rawText?.length ?: 0) * 1.5) {
                                    bestResult = rot90Result
                                    winningVariant = "RETICLE_ORIGINAL_90DEG"
                                }
                            }
                        } finally {
                            rot90Bmp.recycle()
                        }

                        val needRot270 = !OcrDateDetector.hasAnchoredDate(bestResult?.rawText.orEmpty())
                        if (needRot270 || runAllPasses) {
                            val tRot270 = System.currentTimeMillis()
                            val rot270Bmp = rotateBitmap(baseCroppedBitmap, 270f)
                            try {
                                val rot270Result = detectBitmap(rot270Bmp, 1536)
                                recordPass("rotated_270", System.currentTimeMillis() - tRot270, rot270Result)
                                if (needRot270 && OcrDateDetector.hasAnchoredDate(rot270Result.rawText)) {
                                    bestResult = rot270Result
                                    winningVariant = "RETICLE_ORIGINAL_270DEG"
                                }
                            } finally {
                                rot270Bmp.recycle()
                            }
                        }
                    }

                    // Variant 2: Contrast-enhanced grayscale if anchored date still not found (or if runAllPasses is enabled)
                    val needContrast = !OcrDateDetector.hasAnchoredDate(bestResult?.rawText.orEmpty())
                    if (needContrast || runAllPasses) {
                        val tContrast = System.currentTimeMillis()
                        val v2Bitmap = ImagePreprocessor.createContrastEnhancedVariant(baseCroppedBitmap)
                        try {
                            val v2Result = detectBitmap(v2Bitmap, 1536)
                            recordPass("contrast", System.currentTimeMillis() - tContrast, v2Result)
                            if (needContrast && (OcrDateDetector.hasAnchoredDate(v2Result.rawText) ||
                                    v2Result.rawText.length > (bestResult?.rawText?.length ?: 0))
                            ) {
                                bestResult = v2Result
                                winningVariant = "RETICLE_CONTRAST"
                            }

                            val needContrastRot90 = !OcrDateDetector.hasAnchoredDate(bestResult?.rawText.orEmpty())
                            if (needContrastRot90 || runAllPasses) {
                                val tContrast90 = System.currentTimeMillis()
                                val v2Rot90 = rotateBitmap(v2Bitmap, 90f)
                                try {
                                    val v2Rot90Result = detectBitmap(v2Rot90, 1536)
                                    recordPass("contrast_90", System.currentTimeMillis() - tContrast90, v2Rot90Result)
                                    if (needContrastRot90 && OcrDateDetector.hasAnchoredDate(v2Rot90Result.rawText)) {
                                        bestResult = v2Rot90Result
                                        winningVariant = "RETICLE_CONTRAST_90DEG"
                                    }
                                } finally {
                                    v2Rot90.recycle()
                                }
                            }
                        } finally {
                            v2Bitmap.recycle()
                        }

                        // Variant 3: Inverted color (foil packaging, silver tins, dark bottles)
                        val needInverted = !OcrDateDetector.hasAnchoredDate(bestResult?.rawText.orEmpty())
                        if (needInverted || runAllPasses) {
                            val tInverted = System.currentTimeMillis()
                            val v3Bitmap = ImagePreprocessor.createInvertedVariant(baseCroppedBitmap)
                            try {
                                val v3Result = detectBitmap(v3Bitmap, 1536)
                                recordPass("inverted", System.currentTimeMillis() - tInverted, v3Result)
                                if (needInverted && (OcrDateDetector.hasAnchoredDate(v3Result.rawText) ||
                                        v3Result.rawText.length > (bestResult?.rawText?.length ?: 0))
                                ) {
                                    bestResult = v3Result
                                    winningVariant = "RETICLE_INVERTED"
                                }

                                val needInvertedRot90 = !OcrDateDetector.hasAnchoredDate(bestResult?.rawText.orEmpty())
                                if (needInvertedRot90 || runAllPasses) {
                                    val tInverted90 = System.currentTimeMillis()
                                    val v3Rot90 = rotateBitmap(v3Bitmap, 90f)
                                    try {
                                        val v3Rot90Result = detectBitmap(v3Rot90, 1536)
                                        recordPass("inverted_90", System.currentTimeMillis() - tInverted90, v3Rot90Result)
                                        if (needInvertedRot90 && OcrDateDetector.hasAnchoredDate(v3Rot90Result.rawText)) {
                                            bestResult = v3Rot90Result
                                            winningVariant = "RETICLE_INVERTED_90DEG"
                                        }
                                    } finally {
                                        v3Rot90.recycle()
                                    }
                                }
                            } finally {
                                v3Bitmap.recycle()
                            }
                        }
                    }
                } finally {
                    baseCroppedBitmap.recycle()
                }
            }

            // Fallback: If reticle crop yielded no text or missed the anchored date, test full uncropped image
            val needFullFallback = bestResult == null || bestResult.blocks.isEmpty() || !OcrDateDetector.hasAnchoredDate(bestResult.rawText)
            if (needFullFallback || runAllPasses) {
                try {
                    if (file.exists() && file.length() > 0) {
                        val tFull = System.currentTimeMillis()
                        val fullBmp = BitmapFactory.decodeFile(file.absolutePath)
                        if (fullBmp != null) {
                            try {
                                val fullResult = detectBitmap(fullBmp, 1536)
                                recordPass("full_image", System.currentTimeMillis() - tFull, fullResult)
                                if (needFullFallback && (bestResult == null || bestResult.blocks.isEmpty() ||
                                        (OcrDateDetector.hasAnchoredDate(fullResult.rawText) && !OcrDateDetector.hasAnchoredDate(bestResult.rawText)) ||
                                        fullResult.rawText.length > (bestResult.rawText.length * 2))
                                ) {
                                    bestResult = fullResult
                                    winningVariant = "FULL_IMAGE_FALLBACK"
                                }

                                val needFullRot90 = !OcrDateDetector.hasAnchoredDate(bestResult?.rawText.orEmpty())
                                if (needFullRot90 || runAllPasses) {
                                    val tFull90 = System.currentTimeMillis()
                                    val fullRot90 = rotateBitmap(fullBmp, 90f)
                                    try {
                                        val fullRot90Result = detectBitmap(fullRot90, 1536)
                                        recordPass("full_image_90", System.currentTimeMillis() - tFull90, fullRot90Result)
                                        if (needFullRot90 && OcrDateDetector.hasAnchoredDate(fullRot90Result.rawText)) {
                                            bestResult = fullRot90Result
                                            winningVariant = "FULL_IMAGE_FALLBACK_90DEG"
                                        }
                                    } finally {
                                        fullRot90.recycle()
                                    }
                                }
                            } finally {
                                fullBmp.recycle()
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLog.w("OCR", "RapidOCR full image fallback failed: ${e.message}")
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
                )
            )
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun mapVariantToPassCategory(variant: String): String = when (variant) {
        "RETICLE_ORIGINAL", "FULL_IMAGE_FALLBACK" -> "original"
        "RETICLE_CONTRAST" -> "contrast"
        "RETICLE_INVERTED" -> "inverted"
        "RETICLE_ORIGINAL_90DEG",
        "RETICLE_ORIGINAL_270DEG",
        "RETICLE_CONTRAST_90DEG",
        "RETICLE_INVERTED_90DEG",
        "FULL_IMAGE_FALLBACK_90DEG" -> "rotated"
        else -> "original"
    }

    override val isAvailable: Boolean = true

    /**
     * Executes RapidOCR inference on [bitmap] at the given [maxSideLen],
     * strictly recycling the intermediate box image and any temporary bitmap copy.
     */
    private fun detectBitmap(bitmap: Bitmap, maxSideLen: Int): ExtractedText {
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

        return processOcrResult(ocrResult)
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
)

data class RapidOcrEvaluationData(
    val extractedText: ExtractedText,
    val winningVariant: String = "RETICLE_ORIGINAL",
    val winningPass: String,
    val totalMs: Long,
    val passes: List<RapidOcrPassInfo>,
)

