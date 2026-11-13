package app.memora.domain.extraction

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class RegulatoryExtractionResult(
    val calculatedExpiryDate: LocalDate?,
    val mfgDate: LocalDate?,
    val isRelativeCalculation: Boolean,
    val discardedNoiseCount: Int = 0,
)

/**
 * Statutory and Regulatory Packaging Rule Engine.
 *
 * Implements packaging compliance resolution aligned with global packaging standards (FSSAI, FDA, EU):
 * 1. Statutory Relative Statements: "Best before X months from packaging/manufacture" + MFG Date -> Expiry.
 * 2. Past Manufacturing Disqualification: Disqualifies past dates labeled as MFG/PKD from false alert alarms.
 * 3. Noise Stripping: Removes 14-digit FSSAI licenses, batch codes, GSTINs, and tracking barcodes.
 */
class RegulatoryPackagingRuleEngine {

    companion object {
        // 14-digit FSSAI license numbers and statutory codes
        private val REGEX_FSSAI = Regex("""\b(?:FSSAI\s*(?:LIC\s*NO)?[ :.\-]*|LIC\s*NO[ :.\-]*)(\d{14})\b""", RegexOption.IGNORE_CASE)
        private val REGEX_STANDALONE_14_DIGIT = Regex("""\b\d{14}\b""")
        
        // Batch and lot numbers (e.g. BATCH NO: 202611, LOT 4920A, BN: 9482, B.NO 1234)
        private val REGEX_BATCH_CODE = Regex(
            """\b(?:BATCH|LOT|BN|B\.NO)(?:\s*(?:NO|NUMBER)[\s.:#\-]*)?[\s.:#\-]*([A-Z0-9]{4,14})\b""",
            RegexOption.IGNORE_CASE
        )
        
        // GSTIN Indian tax identifier
        private val REGEX_GSTIN = Regex("""\b\d{2}[A-Z]{5}\d{4}[A-Z]{1}[A-Z0-9]{1}Z[A-Z0-9]{1}\b""", RegexOption.IGNORE_CASE)

        // US NDC (National Drug Code): 10 or 11 digits in 4-4-2, 5-3-2, or 5-4-1 format
        private val REGEX_NDC = Regex("""\bNDC\s*\d{4,5}[-\s]\d{3,4}[-\s]\d{1,2}\b""", RegexOption.IGNORE_CASE)

        // USDA Establishment inspection numbers (EST. 38, USDA EST 19894)
        private val REGEX_USDA_EST = Regex("""\b(?:USDA\s*)?EST\.?\s*\d{1,6}[A-Z]?\b""", RegexOption.IGNORE_CASE)

        // USDA Egg Plant codes (e.g. P-1488) and Julian day (e.g. 288)
        private val REGEX_EGG_PLANT = Regex("""\bP-\d{3,5}(?:\s+\d{3})?\b""", RegexOption.IGNORE_CASE)

        // Interstate Milk Shipper (IMS) Plant codes (e.g. PLANT 06-12)
        private val REGEX_MILK_PLANT = Regex("""\b(?:PLANT|IMS)[\s.:#-]*\d{2}-\d{2,4}\b""", RegexOption.IGNORE_CASE)

        // Timestamps (e.g. 14:02, 09:23, 14:23:05)
        private val REGEX_TIMESTAMP = Regex("""\b[0-2]?\d:[0-5]\d(?::[0-5]\d)?\s*(?:AM|PM)?\b""", RegexOption.IGNORE_CASE)

        // US Currency & Price stamps ($15.99/LB, $34.22)
        private val REGEX_PRICE = Regex("""\$\s*\d+(?:\.\d{2})?(?:/LB)?\b""", RegexOption.IGNORE_CASE)

        // US Weight specifications (NET WT 7.25 OZ, NET WT 2.14 LB)
        private val REGEX_WEIGHT = Regex("""\bNET\s*WT\s*\d+(?:\.\d+)?\s*(?:LB|OZ|G)\b""", RegexOption.IGNORE_CASE)

        // Statutory relative shelf life phrasing (global & US)
        private val REGEX_RELATIVE_SHELF_LIFE = Regex(
            """(?:BEST\s+(?:IF\s+|WHEN\s+)?USED\s+BY|BEST\s+(?:IF\s+|WHEN\s+)?USED\s+BEFORE|BEST\s+BEFORE|USE\s+WITHIN|SHELF\s+LIFE|VALID\s+FOR|SELL\s+BY|FREEZE\s+BY)\s+(\d{1,3})\s+(MONTHS?|DAYS?|WEEKS?|YEARS?)\s+(?:FROM|OF)\s+(?:MANUFACTURE|MFG|PACKAGING|PKD|DATE)""",
            RegexOption.IGNORE_CASE
        )

        // General relative phrase e.g. "BEST BEFORE 6 MONTHS"
        private val REGEX_SIMPLE_RELATIVE = Regex(
            """(?:BEST\s+(?:IF\s+|WHEN\s+)?USED\s+BY|BEST\s+(?:IF\s+|WHEN\s+)?USED\s+BEFORE|BEST\s+BEFORE|USE\s+WITHIN|SHELF\s+LIFE|VALID\s+FOR|SELL\s+BY|FREEZE\s+BY)\s+(\d{1,3})\s+(MONTHS?|DAYS?|WEEKS?|YEARS?)""",
            RegexOption.IGNORE_CASE
        )

        // MFG / PKD / PACKED / BORN ON date anchors
        private val REGEX_MFG_DATE = Regex(
            """(?:MFG|MFD|MFR|PKD|PACKED|PACKED\s+ON|MANUFACTURE|PROD|BORN\s+ON)[A-Z\s.:\-]*(\d{1,2}[./\-]\d{1,2}[./\-]\d{2,4}|\d{1,2}[./\-]\d{4})""",
            RegexOption.IGNORE_CASE
        )

        // Generic date matching for parsing DD/MM/YYYY or MM/YYYY
        private val REGEX_GENERIC_DATE = Regex("""\b(\d{1,2})[./\-](\d{1,2})[./\-](\d{2,4})\b""")
        private val REGEX_MONTH_YEAR = Regex("""\b(\d{1,2})[./\-](\d{4})\b""")
    }

    /**
     * Strips regulatory license numbers, batch codes, and tax identifiers that could be misread as dates.
     */
    fun sanitizeRegulatoryNoise(rawText: String): String {
        var text = rawText
        text = text.replace(REGEX_FSSAI) { match -> match.value.replace(match.groupValues[1], "[LICENSE_STRIPPED]") }
        text = text.replace(REGEX_STANDALONE_14_DIGIT, "[14DIGIT_STRIPPED]")
        text = text.replace(REGEX_BATCH_CODE) { match -> match.value.replace(match.groupValues[1], "[BATCH_STRIPPED]") }
        text = text.replace(REGEX_GSTIN, "[GSTIN_STRIPPED]")
        text = text.replace(REGEX_NDC, "[NDC_STRIPPED]")
        text = text.replace(REGEX_USDA_EST, "[EST_STRIPPED]")
        text = text.replace(REGEX_EGG_PLANT, "[EGG_PLANT_STRIPPED]")
        text = text.replace(REGEX_MILK_PLANT, "[PLANT_STRIPPED]")
        text = text.replace(REGEX_TIMESTAMP, "[TIME_STRIPPED]")
        text = text.replace(REGEX_PRICE, "[PRICE_STRIPPED]")
        text = text.replace(REGEX_WEIGHT, "[WEIGHT_STRIPPED]")
        return text
    }

    /**
     * Evaluates text for statutory relative statements and manufacturing date offsets.
     */
    fun evaluate(rawText: String, today: LocalDate): RegulatoryExtractionResult {
        var discardedCount = 0

        // 1. Detect MFG date
        var mfgDate: LocalDate? = null
        val mfgMatch = REGEX_MFG_DATE.find(rawText)
        if (mfgMatch != null) {
            val dateStr = mfgMatch.groupValues[1]
            mfgDate = parseDateString(dateStr)
        }

        // 2. Check for statutory relative shelf life
        var relativeSpan: Pair<Long, String>? = null
        val fullRelativeMatch = REGEX_RELATIVE_SHELF_LIFE.find(rawText)
        if (fullRelativeMatch != null) {
            val num = fullRelativeMatch.groupValues[1].toLongOrNull() ?: 0L
            val unit = fullRelativeMatch.groupValues[2].uppercase()
            relativeSpan = num to unit
        } else {
            val simpleMatch = REGEX_SIMPLE_RELATIVE.find(rawText)
            if (simpleMatch != null) {
                val num = simpleMatch.groupValues[1].toLongOrNull() ?: 0L
                val unit = simpleMatch.groupValues[2].uppercase()
                relativeSpan = num to unit
            }
        }

        // 3. Compute relative date if MFG is known
        if (relativeSpan != null && mfgDate != null) {
            val (num, unit) = relativeSpan
            val calculated = when {
                unit.startsWith("MONTH") -> mfgDate.plusMonths(num)
                unit.startsWith("DAY") -> mfgDate.plusDays(num)
                unit.startsWith("WEEK") -> mfgDate.plusWeeks(num)
                unit.startsWith("YEAR") -> mfgDate.plusYears(num)
                else -> null
            }

            if (calculated != null) {
                return RegulatoryExtractionResult(
                    calculatedExpiryDate = calculated,
                    mfgDate = mfgDate,
                    isRelativeCalculation = true,
                    discardedNoiseCount = discardedCount
                )
            }
        }

        // 4. If mfgDate is in the past and no explicit future expiry was computed
        if (mfgDate != null && mfgDate.isBefore(today)) {
            discardedCount++
            return RegulatoryExtractionResult(
                calculatedExpiryDate = null,
                mfgDate = mfgDate,
                isRelativeCalculation = false,
                discardedNoiseCount = discardedCount
            )
        }

        return RegulatoryExtractionResult(
            calculatedExpiryDate = null,
            mfgDate = mfgDate,
            isRelativeCalculation = false,
            discardedNoiseCount = discardedCount
        )
    }

    private fun parseDateString(raw: String): LocalDate? {
        val parts = raw.split(Regex("""[./\-]"""))
        return try {
            when (parts.size) {
                3 -> {
                    val p0 = parts[0].toInt()
                    val p1 = parts[1].toInt()
                    val rawYear = parts[2].toInt()
                    val year = if (rawYear < 100) 2000 + rawYear else rawYear
                    
                    // Assume DD/MM/YYYY if p0 <= 31 and p1 <= 12
                    if (p1 in 1..12 && p0 in 1..31) {
                        LocalDate.of(year, p1, p0)
                    } else if (p0 in 1..12 && p1 in 1..31) {
                        LocalDate.of(year, p0, p1)
                    } else null
                }
                2 -> {
                    val month = parts[0].toInt()
                    val rawYear = parts[1].toInt()
                    val year = if (rawYear < 100) 2000 + rawYear else rawYear
                    if (month in 1..12) {
                        LocalDate.of(year, month, 1)
                    } else null
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
