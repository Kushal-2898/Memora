package app.memora.platform.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import app.memora.platform.image.ImagePreprocessor
import app.memora.platform.image.ScanRegionCropper
import app.memora.platform.logging.AppLog
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Concrete [TextExtractor] backed by Google ML Kit on-device Text Recognition.
 *
 * Implements Phase 1 & 2 multi-variant sequential processing:
 * 1. Uses [ScanRegionCropper] with BitmapRegionDecoder to extract high-density reticle crop.
 * 2. Processes variants sequentially with early exit upon detecting an anchored expiry date:
 *    - Variant 1: Original reticle crop (fastest, cheapest).
 *    - Variant 2: Contrast-enhanced grayscale (reveals faint/dot-matrix stamps).
 *    - Variant 3: Inverted color (recovers white text on dark/reflective silver tins & crimps).
 * 3. Falls back safely to full uncropped image if reticle crop yields no text.
 * 4. Strictly recycles all intermediate bitmaps, maintaining <= 2 bitmaps in memory.
 */
class MlKitTextExtractor(
    private val context: Context,
) : TextExtractor {

    override suspend fun extractText(imageUri: String): Result<ExtractedText> =
        extractText(imageUri, null)

    override suspend fun extractText(imageUri: String, scanId: String?): Result<ExtractedText> = withContext(Dispatchers.IO) {
        val id = scanId ?: "N/A"
        val startTime = System.currentTimeMillis()
        try {
            AppLog.checkMainThreadViolation("OCR")
            AppLog.recordOcrStart(id, triggerReason = "UNKNOWN", dispatcherLabel = "IO")
            AppLog.i("OCR", "[#$id] Multi-variant OCR processing started | dispatcher=IO")

            val parsedUri = Uri.parse(imageUri)
            val filePath = if (parsedUri.scheme == null || parsedUri.scheme == "file") {
                parsedUri.path ?: imageUri
            } else {
                imageUri
            }

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            // Step 1: Decode reticle crop via ScanRegionCropper
            val baseCroppedBitmap = ScanRegionCropper.cropReticleRegion(filePath)

            var bestVisionText: Text? = null
            var winningVariant = "ORIGINAL_FULL"

            if (baseCroppedBitmap != null) {
                try {
                    // Variant 1: Original reticle crop (0 deg)
                    AppLog.recordImageMemory(baseCroppedBitmap.width, baseCroppedBitmap.height, "ARGB_8888", scanId = id)
                    val v1Text = processBitmap(recognizer, baseCroppedBitmap, 0)
                    bestVisionText = v1Text
                    winningVariant = "RETICLE_ORIGINAL"

                    // Multi-orientation check (zero bitmap allocation): 90 deg and 270 deg
                    if (!hasAnchoredDate(v1Text.text)) {
                        val v1Rot90 = processBitmap(recognizer, baseCroppedBitmap, 90)
                        if (hasAnchoredDate(v1Rot90.text)) {
                            bestVisionText = v1Rot90
                            winningVariant = "RETICLE_ORIGINAL_90DEG"
                        } else {
                            val v1Rot270 = processBitmap(recognizer, baseCroppedBitmap, 270)
                            if (hasAnchoredDate(v1Rot270.text)) {
                                bestVisionText = v1Rot270
                                winningVariant = "RETICLE_ORIGINAL_270DEG"
                            }
                        }
                    }

                    if (!hasAnchoredDate(bestVisionText?.text.orEmpty())) {
                        // Variant 2: Contrast-enhanced grayscale
                        val v2Bitmap = ImagePreprocessor.createContrastEnhancedVariant(baseCroppedBitmap)
                        try {
                            val v2Text = processBitmap(recognizer, v2Bitmap, 0)
                            if (hasAnchoredDate(v2Text.text) || v2Text.text.length > (bestVisionText?.text?.length ?: 0)) {
                                bestVisionText = v2Text
                                winningVariant = "RETICLE_CONTRAST"
                            }
                            if (!hasAnchoredDate(bestVisionText?.text.orEmpty())) {
                                val v2Rot90 = processBitmap(recognizer, v2Bitmap, 90)
                                if (hasAnchoredDate(v2Rot90.text)) {
                                    bestVisionText = v2Rot90
                                    winningVariant = "RETICLE_CONTRAST_90DEG"
                                }
                            }
                        } finally {
                            v2Bitmap.recycle()
                        }

                        if (!hasAnchoredDate(bestVisionText?.text.orEmpty())) {
                            // Variant 3: Inverted color (silver cans, dark bottles)
                            val v3Bitmap = ImagePreprocessor.createInvertedVariant(baseCroppedBitmap)
                            try {
                                val v3Text = processBitmap(recognizer, v3Bitmap, 0)
                                if (hasAnchoredDate(v3Text.text) || v3Text.text.length > (bestVisionText?.text?.length ?: 0)) {
                                    bestVisionText = v3Text
                                    winningVariant = "RETICLE_INVERTED"
                                }
                                if (!hasAnchoredDate(bestVisionText?.text.orEmpty())) {
                                    val v3Rot90 = processBitmap(recognizer, v3Bitmap, 90)
                                    if (hasAnchoredDate(v3Rot90.text)) {
                                        bestVisionText = v3Rot90
                                        winningVariant = "RETICLE_INVERTED_90DEG"
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

            // Fallback: If crop yielded no text or missed the anchored date, test full uncropped image
            if (bestVisionText == null || bestVisionText.textBlocks.isEmpty() || !hasAnchoredDate(bestVisionText.text)) {
                try {
                    val file = File(filePath)
                    if (file.exists() && file.length() > 0) {
                        val fullBmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        if (fullBmp != null) {
                            try {
                                val fullVisionText = processBitmap(recognizer, fullBmp, 0)
                                if (bestVisionText == null || bestVisionText.textBlocks.isEmpty() ||
                                    (hasAnchoredDate(fullVisionText.text) && !hasAnchoredDate(bestVisionText.text)) ||
                                    fullVisionText.text.length > (bestVisionText.text.length * 2)
                                ) {
                                    bestVisionText = fullVisionText
                                    winningVariant = "FULL_IMAGE_FALLBACK"
                                }
                                if (!hasAnchoredDate(bestVisionText.text)) {
                                    val fullRot90 = processBitmap(recognizer, fullBmp, 90)
                                    if (hasAnchoredDate(fullRot90.text)) {
                                        bestVisionText = fullRot90
                                        winningVariant = "FULL_IMAGE_FALLBACK_90DEG"
                                    }
                                }
                            } finally {
                                fullBmp.recycle()
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLog.w("OCR", "Full image fallback failed: ${e.message}")
                }
            }

            val visionText = bestVisionText ?: return@withContext Result.failure(IllegalStateException("No OCR text extracted"))
            val duration = System.currentTimeMillis() - startTime
            val rawText = visionText.text
            val charCount = rawText.length
            val wordCount = rawText.split(Regex("\\s+")).count { it.isNotBlank() }

            AppLog.i("OCR", "[#$id] Completed in ${duration}ms using $winningVariant | chars=$charCount")
            AppLog.recordOcr(
                scanId = id,
                durationMs = duration,
                charCount = charCount,
                wordCount = wordCount,
                rawText = rawText,
                dispatcherLabel = "IO",
            )

            val blocks = visionText.textBlocks.map { mlkBlock ->
                TextBlockInfo(
                    text = mlkBlock.text,
                    boundingBox = mlkBlock.boundingBox,
                    cornerPoints = mlkBlock.cornerPoints?.toList(),
                    lines = mlkBlock.lines.map { mlkLine ->
                        TextLineInfo(
                            text = mlkLine.text,
                            boundingBox = mlkLine.boundingBox,
                            elements = mlkLine.elements.map { mlkElement ->
                                TextElementInfo(
                                    text = mlkElement.text,
                                    boundingBox = mlkElement.boundingBox,
                                )
                            }
                        )
                    }
                )
            }

            Result.success(
                ExtractedText(
                    rawText = visionText.text,
                    suggestedTitle = null,
                    suggestedDateIso = null,
                    blocks = blocks,
                )
            )
        } catch (e: Throwable) {
            val duration = System.currentTimeMillis() - startTime
            AppLog.recordOcr(
                scanId = id,
                durationMs = duration,
                charCount = 0,
                wordCount = 0,
                rawText = null,
                error = e.message ?: "OCR error",
                dispatcherLabel = "IO",
            )
            Result.failure(e)
        }
    }

    private suspend fun processBitmap(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
    ): Text = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
        recognizer.process(inputImage)
            .addOnSuccessListener { text ->
                if (continuation.isActive) continuation.resume(text)
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
    }

    private fun hasAnchoredDate(text: String): Boolean =
        OcrDateDetector.hasAnchoredDate(text)

    override val isAvailable: Boolean = true
}
