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

            fun countRidgeTransitions(y: Int): Int {
                var transitions = 0
                var prevDiff = 0
                var lastPixel = 0

                val rowStart = y * rowStride
                val startX = width * 15 / 100
                val endX = width * 85 / 100

                var x = startX
                while (x < endX) {
                    val index = rowStart + (x * pixelStride)
                    if (index >= yBuffer.limit()) break

                    val pixel = yBuffer.get(index).toInt() and 0xFF
                    val diff = pixel - lastPixel
                    // Distinct peak/trough transitions of physical embossed ridges (threshold 12)
                    if ((diff > 12 && prevDiff < -12) || (diff < -12 && prevDiff > 12)) {
                        transitions++
                    }
                    if (Math.abs(diff) > 8) {
                        prevDiff = diff
                    }
                    lastPixel = pixel
                    x += 2
                }
                return transitions
            }

            // 1. Check for full-frame repeating patterns (keyboards, blinds, monitors, grills).
            // A physical tube crimp is strictly localized at ONE extreme edge (top OR bottom).
            // The middle body of a tube (35%-65%) is smooth without high-frequency periodic ridges.
            val midRows = listOf(height * 35 / 100, height * 45 / 100, height * 55 / 100, height * 65 / 100)
            val midMatches = midRows.count { countRidgeTransitions(it) in 16..50 }
            if (midMatches >= 2) return false

            // 2. Sample top band (10%, 15%, 20%, 25%) and bottom band (75%, 80%, 85%, 90%)
            val topRows = listOf(height * 10 / 100, height * 15 / 100, height * 20 / 100, height * 25 / 100)
            val bottomRows = listOf(height * 75 / 100, height * 80 / 100, height * 85 / 100, height * 90 / 100)

            val topMatches = topRows.count { countRidgeTransitions(it) in 16..50 }
            val bottomMatches = bottomRows.count { countRidgeTransitions(it) in 16..50 }

            // A physical tube has only ONE sealed crimp edge. If both top AND bottom match,
            // the entire frame has repeating stripes/keys across both halves (e.g. keyboard).
            if (topMatches >= 2 && bottomMatches >= 2) return false

            // Exactly one end must display the characteristic periodic crimp texture, while middle remains smooth.
            (topMatches >= 2 || bottomMatches >= 2) && midMatches == 0
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

        // Hardware / keyboard reject filter: ignore periodic crimp false positives from laptop keys
        val isKeyboardOrComputer = hasWord("ctrl") || hasWord("alt") || hasWord("shift") ||
            hasWord("enter") || hasWord("caps") || hasWord("tab") || hasWord("fn") ||
            hasWord("esc") || hasWord("del") || hasWord("ins") ||
            lower.contains("backspace") || lower.contains("qwerty") ||
            lower.contains("caps lock") || lower.contains("prtsc") ||
            lower.contains("inser") || lower.contains("scroll") ||
            lower.contains("numlk") || lower.contains("pgup") ||
            lower.contains("pgdn") ||
            Regex("""\b(?:f[1-9]|f1[0-2])\b""", RegexOption.IGNORE_CASE).containsMatchIn(rawText)
        if (isKeyboardOrComputer) {
            return PackagingDetectionResult()
        }

        // 1. Tube / Crimp (Highest priority for embossed same-color packaging)
        val hasTubeKeyword = lower.contains("ointment") || lower.contains("paste") ||
            lower.contains("cream") || hasWord("gel") || hasWord("tube") ||
            lower.contains("toothpaste") || lower.contains("sunscreen")
        if (isCrimpPresent || hasTubeKeyword) {
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
