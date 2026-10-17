package app.memora.platform.camera

import java.util.Locale

/**
 * Multi-signal evidence gate determining whether a live camera frame contains
 * sufficient physical, temporal, or structural evidence of a scannable document,
 * product packaging, utility bill, or expiry/reminder item.
 *
 * Implements Phase A Evidence-Based Capture Gating:
 * - Accepts sparse single-line markings (cans, bottles, crimps).
 * - Accepts numeric-heavy and date-only markings without requiring keyword prefixes.
 * - Accepts short packaging codes (BB, UB, EXP, MFG, LOT, PKD, B.NO).
 * - Accepts physical substrate signals (crimp seals, foil margins).
 * - Contains ZERO brand names, medicine names, or country-specific utilities.
 * - Rejects blank frames, isolated noise, and unstructured gibberish.
 */
object DocumentDetectionFilter {

    // 1. Multi-word operational phrases
    private val OPERATIONAL_PHRASES = listOf(
        "best before", "best by", "use by", "use before", "packed on",
        "mfg date", "exp date", "expiry date", "due date", "pay by",
        "pay before", "total amount", "tax invoice", "bill date",
        "bill period", "statement date", "meter reading"
    )

    // 2. Generic single-token operational vocabulary (bounded by word boundaries \b)
    // Excludes all brand names, product titles, utilities, and medicine names.
    private val OPERATIONAL_TOKEN_REGEX = Regex(
        """\b(?:exp|expiry|expires|expire|expiration|bb|ub|valid|mfg|mfd|mfgd|pkd|pack|packed|packaging|batch|lot|dom|prod|bill|due|pay|invoice|inv|receipt|tax|total|amt|amount|subtotal|tariff|charges|statement|date|warranty|warr|guarantee|order|mrp|rs|inr|bno)\b""",
        RegexOption.IGNORE_CASE
    )

    // 3. Monetary and code punctuation prefixes
    private val CODE_OR_CURRENCY_REGEX = Regex(
        """[₹$€£¥]|(?:b\.no|no\.|rs\.)""",
        RegexOption.IGNORE_CASE
    )

    // 4. Date patterns across supported international formats
    // Standard delimited dates (e.g., 25/08/2026, 15.10.26, 01-02-2025, 1/2/26)
    private val REGEX_DATE_DELIMITED = Regex(
        """\b(0?[1-9]|[12]\d|3[01])[-/.](0?[1-9]|1[0-2])[-/.](20\d{2}|\d{2})\b"""
    )

    // Month/Year (e.g., 12/2026, 12/26, 08.26, 10-27, 05/2027)
    // Negative lookbehind/lookahead ensures this is not part of a larger 3-part date
    private val REGEX_MONTH_YEAR = Regex(
        """(?<!\d[-/.])\b(0?[1-9]|1[0-2])[-/.](20\d{2}|\d{2})\b(?![/.-]\d)"""
    )

    // ISO dates (e.g., 2026-11-30, 2026.08.15)
    private val REGEX_ISO_DATE = Regex(
        """\b(20\d{2})[-/.](0?[1-9]|1[0-2])[-/.](0?[1-9]|[12]\d|3[01])\b"""
    )

    // ISO Year-Month (e.g., 2026-12, 2027.05)
    private val REGEX_ISO_YEAR_MONTH = Regex(
        """(?<!\d[-/.])\b(20\d{2})[-/.](0?[1-9]|1[0-2])\b(?![/.-]\d)"""
    )

    // Textual Month dates (e.g., AUG 26, 15 OCT 2026, 25-AUG-26, OCT 2027)
    private val REGEX_MONTH_NAME = Regex(
        """\b(?:(\d{1,2})[\s.-]+)?(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|SEPT|OCT|NOV|DEC)[\s.-]+(?:\d{1,2}[\s.-]+)?(20\d{2}|\d{2})\b""",
        RegexOption.IGNORE_CASE
    )

    // Space-separated numeric dates (e.g., 25 08 26, 08 2026, 12 2026)
    private val REGEX_SPACE_SEPARATED_DATE = Regex(
        """\b(?:(0?[1-9]|[12]\d|3[01])\s+)?(0?[1-9]|1[0-2])\s+(20\d{2})\b"""
    )

    // Period-after-Opening (PAO) cosmetic lifespan markings (e.g., 12M, 6M, 24M)
    private val REGEX_PAO_MARKING = Regex(
        """\b([1-9]|[12]\d)\s*M\b""",
        RegexOption.IGNORE_CASE
    )

    // 5. Structured Lot / Batch / Serial markings (e.g., "LOT 44B", "BATCH #9921", "B: 8812", "L: 1402")
    private val REGEX_STRUCTURED_CODE = Regex(
        """\b(?:BATCH|LOT|B\.NO|BNO|B|L|SN|NO)[.:\s#]+[A-Z0-9\-_/]+\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Determines whether the detected text or physical packaging feature qualifies
     * as a legitimate candidate for capture and interpretation.
     *
     * @param rawText OCR text recognized on the camera frame.
     * @param lineCount Number of recognized text lines.
     * @param isCrimpDetected True if periodic luminance ridges (crimp seal) were identified.
     * @return True if sufficient evidence exists to capture and process the frame.
     */
    fun isDocumentCandidate(
        rawText: String,
        lineCount: Int = 0,
        isCrimpDetected: Boolean = false,
    ): Boolean {
        // 1. Physical substrate feature lock-on (tube crimp, blister margin)
        if (isCrimpDetected) return true

        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return false

        // 2. Date / Temporal Pattern Evidence (Sparse Markings & PAO codes)
        // Accepts cans, bottles, crimps, dot-matrix dates even without any labels
        if (hasDateEvidence(trimmed)) return true

        val nonWhitespaceChars = trimmed.count { !it.isWhitespace() }
        // Filter out isolated single/double character noise (e.g. "a", "ok", "12", "--")
        if (nonWhitespaceChars < 3) return false

        // 3. Generic Operational / Packaging Vocabulary
        val hasKeyword = hasOperationalEvidence(trimmed)
        if (hasKeyword && nonWhitespaceChars >= 4) return true

        // 4. Structured Batch / Lot / Serial Code Evidence
        if (hasStructuredCodeEvidence(trimmed)) return true

        // 5. Multi-line Structured Layout Evidence (receipts, address blocks, tables)
        val effectiveLines = if (lineCount > 0) lineCount else trimmed.lines().count { it.isNotBlank() }
        if (effectiveLines >= 2 && nonWhitespaceChars >= 8) return true

        // 6. Substantial Readable Text Density Fallback (documents, labels, descriptions)
        if (nonWhitespaceChars >= 12) return true

        return false
    }

    private fun hasDateEvidence(text: String): Boolean {
        return REGEX_DATE_DELIMITED.containsMatchIn(text) ||
            REGEX_MONTH_YEAR.containsMatchIn(text) ||
            REGEX_ISO_DATE.containsMatchIn(text) ||
            REGEX_ISO_YEAR_MONTH.containsMatchIn(text) ||
            REGEX_MONTH_NAME.containsMatchIn(text) ||
            REGEX_SPACE_SEPARATED_DATE.containsMatchIn(text) ||
            REGEX_PAO_MARKING.containsMatchIn(text)
    }

    private fun hasOperationalEvidence(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        if (OPERATIONAL_PHRASES.any { lower.contains(it) }) return true
        if (OPERATIONAL_TOKEN_REGEX.containsMatchIn(text)) return true
        if (CODE_OR_CURRENCY_REGEX.containsMatchIn(text)) return true
        return false
    }

    private fun hasStructuredCodeEvidence(text: String): Boolean {
        return REGEX_STRUCTURED_CODE.containsMatchIn(text)
    }
}
