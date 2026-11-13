package app.memora.domain.extraction

import android.graphics.Rect
import app.memora.platform.ocr.TextBlockInfo
import app.memora.platform.ocr.TextElementInfo
import kotlin.math.abs
import kotlin.math.hypot

data class SpatialExtractedEntity(
    val text: String,
    val boundingBox: Rect?,
    val rayConfidence: Float,
)

/**
 * 2D Spatial Layout & Geometric Ray-Casting Extractor.
 *
 * Dissects OCR bounding box coordinates within the camera scanning frame:
 * 1. Title Inference: Ranks text by font height and vertical zoning (top 35% priority).
 * 2. Directional Ray-Casting: Evaluates horizontal (+x) and vertical (+y) vectors from anchor
 *    keywords to value tokens, penalizing off-axis diagonal noise.
 */
class SpatialTokenGraphExtractor {

    companion object {
        private val NOISE_TITLE_WORDS = setOf(
            "EXP", "EXPIRY", "MFG", "PKD", "PACKED", "BATCH", "LOT",
            "BEST", "BEFORE", "USE", "BY", "INGREDIENTS", "NET", "WT",
            "NUTRITION", "FACTS", "MRP", "FSSAI", "LIC", "NO", "CUSTOMER",
            "CARE", "VEG", "NON-VEG", "STORE", "COOL", "DRY", "PLACE"
        )
    }

    /**
     * Infers product title by ranking bounding box height and vertical position in reticle.
     */
    fun inferTitle(blocks: List<TextBlockInfo>, frameHeight: Int): String? {
        if (blocks.isEmpty()) return null

        val candidates = mutableListOf<Pair<String, Float>>()

        for (block in blocks) {
            val text = block.text.trim()
            val box = block.boundingBox ?: continue
            
            // Clean lines within block
            val lines = if (block.lines.isNotEmpty()) block.lines.map { it.text.trim() } else listOf(text)

            for (line in lines) {
                if (line.length < 3) continue
                
                // Exclude pure noise lines
                val upperWords = line.uppercase().split(Regex("""[\s,.:;/-]+""")).filter { it.isNotBlank() }
                if (upperWords.isEmpty()) continue
                if (upperWords.all { it in NOISE_TITLE_WORDS || it.all { c -> c.isDigit() } }) continue
                if (upperWords.first() in NOISE_TITLE_WORDS && upperWords.size <= 3) continue

                val fontHeight = box.height().toFloat().coerceAtLeast(1f)
                val topY = box.top.toFloat().coerceAtLeast(0f)
                val normY = (topY / frameHeight.coerceAtLeast(1)).coerceIn(0f, 1f)

                // Top 40% of the frame receives positive vertical weight
                val positionWeight = 1.0f - (0.6f * normY)
                
                // Length penalty: 4 to 35 chars is sweet spot for product titles
                val lenWeight = when {
                    line.length in 4..35 -> 1.0f
                    line.length in 36..55 -> 0.85f
                    else -> 0.5f
                }

                val score = fontHeight * positionWeight * lenWeight
                candidates.add(line to score)
            }
        }

        return candidates.maxByOrNull { it.second }?.first
    }

    /**
     * Casts horizontal and vertical directional rays from keyword anchors to find the true value match.
     */
    fun findBestRayCastedMatch(
        keywords: List<TextElementInfo>,
        candidates: List<TextElementInfo>,
    ): SpatialExtractedEntity? {
        if (keywords.isEmpty() || candidates.isEmpty()) return null

        var bestMatch: SpatialExtractedEntity? = null
        var maxConfidence = 0f

        for (kw in keywords) {
            val kBox = kw.boundingBox ?: continue
            val kHeight = kBox.height().toFloat().coerceAtLeast(1f)
            val kWidth = kBox.width().toFloat().coerceAtLeast(1f)

            for (cand in candidates) {
                val cBox = cand.boundingBox ?: continue
                val confidence: Float

                // 1. Horizontal Ray (+x corridor)
                val isRight = cBox.left >= (kBox.right - (kWidth * 0.15f))
                val vertDelta = abs(cBox.centerY() - kBox.centerY()).toFloat()
                val isHorizCorridor = isRight && vertDelta <= (kHeight * 0.85f)

                // 2. Vertical Ray (+y corridor)
                val isBelow = cBox.top >= (kBox.bottom - (kHeight * 0.15f))
                val horizDelta = abs(cBox.centerX() - kBox.centerX()).toFloat()
                val isVertCorridor = isBelow && horizDelta <= (kWidth * 0.85f)

                if (isHorizCorridor) {
                    val dx = (cBox.left - kBox.right).coerceAtLeast(0).toFloat()
                    confidence = 1.0f / (1.0f + (dx / (kHeight * 4.0f)))
                } else if (isVertCorridor) {
                    val dy = (cBox.top - kBox.bottom).coerceAtLeast(0).toFloat()
                    confidence = 0.95f / (1.0f + (dy / (kHeight * 3.5f)))
                } else {
                    // 3. Diagonal / Off-axis penalty (Euclidean distance with heavy discount)
                    val dist = hypot(
                        (cBox.centerX() - kBox.centerX()).toDouble(),
                        (cBox.centerY() - kBox.centerY()).toDouble()
                    ).toFloat()
                    confidence = (0.35f / (1.0f + (dist / (kHeight * 4.0f))))
                }

                if (confidence > maxConfidence) {
                    maxConfidence = confidence
                    bestMatch = SpatialExtractedEntity(
                        text = cand.text,
                        boundingBox = cBox,
                        rayConfidence = confidence
                    )
                }
            }
        }

        return bestMatch
    }
}
