package app.memora.domain.extraction

import android.graphics.Rect
import app.memora.platform.ocr.TextBlockInfo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Geometric spatial linker that pairs detached date labels and date tokens.
 *
 * Implements Phase 4:
 * 1. Groups words/elements into horizontal rows (|ΔY| < 0.6 × median text height).
 * 2. Pairs same-row-right first (e.g. "EXP" followed by "12/2026").
 * 3. Pairs vertically below using column horizontal overlap (e.g. packaging tables: "Use By" on row 1, date below on row 2).
 * 4. Labels paired anchors explicitly ("EXP: <date>", "MFG: <date>") to prevent MFG from being chosen as expiry.
 */
object SpatialDatePairer {

    private val EXP_COMPOUND = listOf("USE BY", "BEST BEFORE", "USE BEFORE", "BEST BY", "EXP DATE", "EXPIRY DATE", "VALID TILL", "VALID UNTIL")
    private val EXP_WORDS = setOf("EXP", "EXPIRY", "EXPIRES", "EXPIRATION", "EXPD", "EXRK", "BB")

    private val MFG_COMPOUND = listOf("MFG DATE", "MFD DATE", "PACKED ON", "DATE OF MFG", "DATE OF MANUFACTURE", "DATE OF PACKAGING", "PROD DATE", "PRODUCTION DATE", "MONTH OF MFG")
    private val MFG_WORDS = setOf("MFG", "MFD", "MFGD", "PKD", "PACKED", "PACK", "DOM", "PROD")

    private val DATE_TOKEN_REGEX = Regex(
        """(?:\d{1,2}[/.\-\s]\d{1,2}[/.\-\s]\d{2,4})|(?:\d{1,2}[/.\-]\d{2,4})|(?:\d{4}[/.\-]\d{2}[/.\-]\d{2})|(?:\b\d{1,2}\s+(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\w*\s+\d{2,4}\b)|(?:\b(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\w*[\s,.-]+\d{2,4}\b)|(?:\b\d{1,2}[-/.](?:0[1-9]|1[0-2])\d{2,4}\b)""",
        RegexOption.IGNORE_CASE
    )

    private data class ElementBox(
        val text: String,
        val rect: Rect,
        val height: Int = rect.height().coerceAtLeast(10),
        val width: Int = rect.width().coerceAtLeast(10),
    )

    private enum class AnchorType { EXPIRY, MFG }

    private data class AnchorBox(
        val text: String,
        val type: AnchorType,
        val rect: Rect,
        val height: Int = rect.height().coerceAtLeast(10),
    )

    fun pairDetachedDates(blocks: List<TextBlockInfo>): List<String> {
        val anchorBoxes = mutableListOf<AnchorBox>()
        val dateBoxes = mutableListOf<ElementBox>()
        val allBoxes = mutableListOf<ElementBox>()

        for (b in blocks) {
            for (l in b.lines) {
                val lineBox = l.boundingBox ?: b.boundingBox ?: continue
                if (lineBox.width() <= 0 || lineBox.height() <= 0 || l.text.isBlank()) continue

                val lineText = DeterministicMemoryExtractor.normalizeForExtraction(l.text.trim())
                allBoxes.add(ElementBox(lineText, lineBox))

                val upperLine = lineText.uppercase()
                val lineHasDate = DATE_TOKEN_REGEX.containsMatchIn(lineText)

                // Check if the entire line is a compound or single anchor without its date
                val isLineExp = EXP_COMPOUND.any { upperLine.contains(it) } ||
                    EXP_WORDS.any { Regex("""\b$it\b""", RegexOption.IGNORE_CASE).containsMatchIn(lineText) }
                val isLineMfg = MFG_COMPOUND.any { upperLine.contains(it) } ||
                    MFG_WORDS.any { Regex("""\b$it\b""", RegexOption.IGNORE_CASE).containsMatchIn(lineText) }

                if (!lineHasDate && (isLineExp || isLineMfg)) {
                    // Line is purely an anchor label
                    val type = if (isLineExp) AnchorType.EXPIRY else AnchorType.MFG
                    anchorBoxes.add(AnchorBox(lineText, type, lineBox))
                } else if (l.elements.size > 1) {
                    // Line might contain detached header columns (e.g. "Mfg. Date    Exp. Date")
                    // or header followed by date in separate element
                    for (e in l.elements) {
                        val eBox = e.boundingBox ?: continue
                        if (eBox.width() <= 0 || eBox.height() <= 0 || e.text.isBlank()) continue
                        val eText = DeterministicMemoryExtractor.normalizeForExtraction(e.text.trim())
                        val eUpper = eText.uppercase().replace(Regex("[^A-Z]"), "")
                        if (eUpper in EXP_WORDS || eUpper.startsWith("EXP")) {
                            anchorBoxes.add(AnchorBox(eText, AnchorType.EXPIRY, eBox))
                        } else if (eUpper in MFG_WORDS || eUpper.startsWith("MFG") || eUpper.startsWith("PKD") || eUpper.startsWith("PACK") || eUpper.startsWith("MTD")) {
                            anchorBoxes.add(AnchorBox(eText, AnchorType.MFG, eBox))
                        } else if (DATE_TOKEN_REGEX.containsMatchIn(eText)) {
                            dateBoxes.add(ElementBox(eText, eBox))
                        }
                    }
                }

                if (lineHasDate) {
                    dateBoxes.add(ElementBox(lineText, lineBox))
                }
            }
        }

        if (anchorBoxes.isEmpty() || dateBoxes.isEmpty()) return emptyList()

        val heights = (anchorBoxes.map { it.height } + dateBoxes.map { it.height }).sorted()
        val medianHeight = heights[heights.size / 2].coerceAtLeast(10)

        val pairedResults = mutableListOf<String>()
        val usedDates = mutableSetOf<ElementBox>()
        val usedAnchors = mutableSetOf<AnchorBox>()

        // Sort anchors by Y then X
        val sortedAnchors = anchorBoxes.sortedWith(compareBy({ it.rect.top }, { it.rect.left }))

        for (anchor in sortedAnchors) {
            if (anchor in usedAnchors) continue
            val prefix = if (anchor.type == AnchorType.EXPIRY) "EXP: " else "MFD: "

            // 1. Same-row-right: Y delta < 1.2 * medianHeight and date is strictly to the right
            val sameRowRight = dateBoxes
                .filter { it !in usedDates }
                .filter { de ->
                    val dy = abs(anchor.rect.centerY() - de.rect.centerY())
                    val dx = de.rect.left - anchor.rect.right
                    dy < 1.2 * medianHeight && dx in 0..(8 * medianHeight)
                }
                .minByOrNull { it.rect.left - anchor.rect.right }

            if (sameRowRight != null) {
                usedDates.add(sameRowRight)
                usedAnchors.add(anchor)
                pairedResults.add("$prefix${sameRowRight.text}")
                continue
            }

            // 2. Tabular below: date is below anchor with column horizontal overlap
            val below = dateBoxes
                .filter { it !in usedDates }
                .filter { de ->
                    val dy = de.rect.top - anchor.rect.bottom
                    val overlap = min(anchor.rect.right, de.rect.right) - max(anchor.rect.left, de.rect.left)
                    val horizontalMatch = overlap > 0 || abs(anchor.rect.centerX() - de.rect.centerX()) < (1.8 * medianHeight)
                    dy in 0..(12 * medianHeight) && horizontalMatch
                }
                .minByOrNull { it.rect.top - anchor.rect.bottom }

            if (below != null) {
                usedDates.add(below)
                usedAnchors.add(anchor)
                pairedResults.add("$prefix${below.text}")
            }
        }

        return pairedResults
    }
}
