package app.memora.platform.ocr

import app.memora.domain.extraction.DeterministicMemoryExtractor

/**
 * Shared utility for detecting anchored expiry, best-before, or manufacturing dates in OCR output.
 * Used for early-exit multi-variant processing in [MlKitTextExtractor] and [RapidOcrExtractor].
 */
object OcrDateDetector {

    fun hasAnchoredDate(text: String): Boolean {
        if (text.isBlank()) return false
        val normalized = DeterministicMemoryExtractor.normalizeForExtraction(text)
        val upper = normalized.uppercase()
        val hasAnchor = upper.contains("EXP") || upper.contains("USE BY") ||
            upper.contains("BEST BEFORE") || upper.contains("MFG") ||
            upper.contains("MFD") || upper.contains("PKD") || upper.contains("BB") ||
            upper.contains("SE BY") || upper.contains("FG. DATE") || upper.contains("FG DATE") ||
            upper.contains("EXRK") || upper.contains("USE BEFORE") || upper.contains("DOM") ||
            upper.contains("PACKED") || upper.contains("DUE") || upper.contains("BATCH") ||
            upper.contains("B.NO")
        val hasDateDigits = Regex("""\b(?:0?[1-9]|[12]\d|3[01])[/.-](?:0?[1-9]|1[0-2])[/.-](?:20\d{2}|\d{2})\b""").containsMatchIn(normalized) ||
            Regex("""\b(?:0?[1-9]|1[0-2])[/.-](?:20\d{2}|2[4-9])\b""").containsMatchIn(normalized) ||
            Regex("""\b202[4-9]\b""").containsMatchIn(normalized) ||
            Regex("""\b(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\w*[\s,.-]+(?:20\d{2}|\d{2})\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized) ||
            Regex("""\b(?:0?[1-9]|[12]\d|3[01])\s+(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\b""", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        return (hasAnchor && hasDateDigits) || (hasDateDigits && Regex("""\b202[5-9]\b""").containsMatchIn(normalized))
    }
}
