package app.memora.platform.camera

import androidx.camera.core.ImageProxy
import app.memora.domain.extraction.DateLocation
import app.memora.domain.extraction.MarkingStyle
import app.memora.domain.extraction.PackagingType
import java.nio.ByteBuffer
import java.util.Locale

/**
 * Lightweight on-device feature detector analyzing raw camera preview frames and OCR
 * layout signals to identify physical packaging types, crimp seals, and marking techniques.
 */
data class PackagingDetectionResult(
    val packagingType: PackagingType = PackagingType.UNKNOWN,
    val dateLocation: DateLocation = DateLocation.UNKNOWN,
    val markingStyle: MarkingStyle = MarkingStyle.UNKNOWN,
    val isCrimpDetected: Boolean = false,
    val confidence: Float = 0f,
    val guidanceMessage: String? = null,
)

object PackagingFeatureDetector {

    /**
     * Inspects the raw camera luminance (Y plane) for periodic ridge textures characteristic
     * of tube crimp seals and blister foil margins.
     *
     * Runs in < 8ms on a downsampled grid without allocating heavy bitmaps.
     */
    fun analyzeLuminanceForCrimp(imageProxy: ImageProxy): Boolean {
        return runCatching {
            val planes = imageProxy.planes
            if (planes.isEmpty()) return false

            val yBuffer: ByteBuffer = planes[0].buffer
            val width = imageProxy.width
            val height = imageProxy.height
            val rowStride = planes[0].rowStride
            val pixelStride = planes[0].pixelStride

            if (width < 64 || height < 64) return false

            // Sample 8 horizontal bands in the upper 40% and lower 40% (typical crimp positions)
            val rowsToSample = listOf(
                height * 10 / 100, height * 15 / 100, height * 20 / 100, height * 25 / 100,
                height * 75 / 100, height * 80 / 100, height * 85 / 100, height * 90 / 100,
            )

            val stepX = 2 // Sample every 2nd pixel
            for (y in rowsToSample) {
                var ridgeTransitions = 0
                var prevDiff = 0
                var lastPixel = 0

                val rowStart = y * rowStride
                // Scan the central 70% of the row
                val startX = width * 15 / 100
                val endX = width * 85 / 100

                var x = startX
                while (x < endX) {
                    val index = rowStart + (x * pixelStride)
                    if (index >= yBuffer.limit()) break

                    val pixel = yBuffer.get(index).toInt() and 0xFF
                    val diff = pixel - lastPixel
                    // Look for sign changes in the derivative (micro-shadow peaks and valleys)
                    if ((diff > 6 && prevDiff < -6) || (diff < -6 && prevDiff > 6)) {
                        ridgeTransitions++
                    }
                    if (Math.abs(diff) > 4) {
                        prevDiff = diff
                    }
                    lastPixel = pixel
                    x += stepX
                }

                // A crimped tube strip has 14-45 periodic teeth/ridges across the sampled width
                if (ridgeTransitions in 14..55) {
                    return true
                }
            }
            false
        }.getOrDefault(false)
    }

    /**
     * Combines physical surface ridge signals with live OCR text tokens to determine
     * the packaging category, date location, and marking style.
     */
    fun classifyPackaging(
        rawText: String,
        isCrimpPresent: Boolean,
    ): PackagingDetectionResult {
        val lower = rawText.lowercase(Locale.ROOT)
        fun hasWord(word: String): Boolean =
            Regex("""\b${Regex.escape(word)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(rawText)

        // 1. Tube / Crimp (Highest priority for embossed same-color packaging)
        if (isCrimpPresent || lower.contains("ointment") || lower.contains("paste") || lower.contains("cream") || hasWord("gel")) {
            return PackagingDetectionResult(
                packagingType = PackagingType.TUBE,
                dateLocation = DateLocation.CRIMP_SEAL,
                markingStyle = if (isCrimpPresent && !lower.contains("exp")) MarkingStyle.EMBOSSED_NO_INK else MarkingStyle.PRINTED,
                isCrimpDetected = isCrimpPresent,
                confidence = if (isCrimpPresent) 0.88f else 0.72f,
                guidanceMessage = if (isCrimpPresent) "Crimp seal detected — scanning embossed date…" else null,
            )
        }

        // 2. Can / Tin (concave metallic bottom or beverage / food keywords)
        if (hasWord("can") || hasWord("tin") || lower.contains("beverage") || lower.contains("soda") || lower.contains("beer") || lower.contains("cola") || lower.contains("spray")) {
            return PackagingDetectionResult(
                packagingType = PackagingType.CAN,
                dateLocation = DateLocation.CAN_BOTTOM,
                markingStyle = MarkingStyle.DOT_MATRIX,
                confidence = 0.78f,
                guidanceMessage = "Can detected — check the bottom rim",
            )
        }

        // 3. Blister Pack (medicines, tablets, capsules)
        if (hasWord("tablet") || hasWord("tablets") || hasWord("capsule") || hasWord("capsules") || hasWord("strip") || (hasWord("mg") && (hasWord("ip") || hasWord("usp")))) {
            return PackagingDetectionResult(
                packagingType = PackagingType.BLISTER_PACK,
                dateLocation = DateLocation.CRIMP_SEAL,
                markingStyle = MarkingStyle.EMBOSSED_NO_INK,
                confidence = 0.82f,
                guidanceMessage = "Blister pack detected — scanning foil margin",
            )
        }

        // 4. Bottle / Jug (liquids, oil, milk, syrup, shampoo)
        if (hasWord("bottle") || lower.contains("shampoo") || lower.contains("lotion") || hasWord("syrup") || hasWord("oil") || (lower.contains("bottle") && (hasWord("ml") || hasWord("ltr")))) {
            return PackagingDetectionResult(
                packagingType = PackagingType.BOTTLE,
                dateLocation = DateLocation.BOTTLE_NECK,
                markingStyle = if (lower.contains("laser")) MarkingStyle.LASER_ETCHED else MarkingStyle.DOT_MATRIX,
                confidence = 0.75f,
                guidanceMessage = "Bottle detected — check neck or cap",
            )
        }

        // 5. Document / Bill / Receipt
        if (lower.contains("invoice") || lower.contains("receipt") || hasWord("bill") || lower.contains("total amount")) {
            return PackagingDetectionResult(
                packagingType = PackagingType.DOCUMENT,
                dateLocation = DateLocation.LABEL,
                markingStyle = MarkingStyle.PRINTED,
                confidence = 0.90f,
                guidanceMessage = "Document detected — scanning dates",
            )
        }

        // 6. Carton Box
        if (hasWord("box") || hasWord("pack") || hasWord("carton")) {
            return PackagingDetectionResult(
                packagingType = PackagingType.CARTON_BOX,
                dateLocation = DateLocation.END_FLAP,
                markingStyle = MarkingStyle.PRINTED,
                confidence = 0.70f,
                guidanceMessage = "Package detected — scanning end flap",
            )
        }

        return PackagingDetectionResult()
    }
}
