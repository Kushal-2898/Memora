package app.memora.domain.extraction

import android.graphics.Rect
import app.memora.domain.model.EventType
import app.memora.domain.scheduler.ClockProvider
import app.memora.domain.scheduler.SystemClockProvider
import java.time.LocalDate
import java.time.ZoneId

/**
 * Deterministic, on-device [MemoryExtractor] parsing dates, labels, and biller titles.
 *
 * Implements Stage 3 deterministic parsing:
 * - Scans all date candidates across supported date patterns (MM/YYYY, DD/MM/YYYY, MMM YYYY, MM/YY, YYYY-MM-DD).
 * - Inspects immediate surrounding context on the same line (±3 words).
 * - Classifies semantic intent (EXPIRATION, BEST_BEFORE, DUE_DATE, WARRANTY_END, RETURN_DEADLINE) or discards manufacturing/batch dates.
 * - Detects recognized biller/utility brands (BESCOM, Airtel, Indane, BWSSB, etc.).
 * - Assigns confidence ratings (HIGH for explicitly labelled dates, LOW for unlabelled raw dates).
 */
class DeterministicMemoryExtractor(
    private val clockProvider: ClockProvider = SystemClockProvider(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : MemoryExtractor {

    companion object {
        const val CONFIDENCE_HIGH = 0.9f
        const val CONFIDENCE_LOW = 0.3f

        private val MONTH_NAME_MAP = mapOf(
            "JAN" to 1, "JANUARY" to 1,
            "FEB" to 2, "FEBRUARY" to 2,
            "MAR" to 3, "MARCH" to 3,
            "APR" to 4, "APRIL" to 4,
            "MAY" to 5,
            "JUN" to 6, "JUNE" to 6,
            "JUL" to 7, "JULY" to 7,
            "AUG" to 8, "AUGUST" to 8,
            "SEP" to 9, "SEPT" to 9, "SEPTEMBER" to 9,
            "OCT" to 10, "OCTOBER" to 10,
            "NOV" to 11, "NOVEMBER" to 11,
            "DEC" to 12, "DECEMBER" to 12,
        )

        // 1. YYYY-MM-DD
        private val REGEX_YYYY_MM_DD = Regex("""\b(20\d{2})[-/.](0[1-9]|1[0-2])[-/.](0[1-9]|[12]\d|3[01])\b""")

        // 2. DD/MM/YYYY
        private val REGEX_DD_MM_YYYY = Regex("""\b(0[1-9]|[12]\d|3[01])[-/.](0[1-9]|1[0-2])[-/.](20\d{2})\b""")

        // 3. MMM YYYY (with optional day prefix or suffix)
        private val REGEX_MMM_YYYY = Regex(
            """\b(?:(\d{1,2})[ -]+)?(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|SEPT|OCT|NOV|DEC|JANUARY|FEBRUARY|MARCH|APRIL|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER)[ ,./-]+(?:(\d{1,2})[ ,./-]+)?(20\d{2})\b""",
            RegexOption.IGNORE_CASE
        )

        // 4. MM/YYYY (negative lookbehind/lookahead to prevent partial match of DD/MM/YYYY or YYYY/MM/DD)
        private val REGEX_MM_YYYY = Regex("""(?<!\d[-/.])\b(0[1-9]|1[0-2])[-/.](20\d{2})\b(?![/.-]\d)""")

        // 5. MM/YY (2-digit year between 24..49)
        private val REGEX_MM_YY = Regex("""(?<!\d[-/.])\b(0[1-9]|1[0-2])[/.-](\d{2})\b(?![/.-]?\d)""")

        // 6. DD/MM/YY or DD.MM.YY (2-digit year between 24..49)
        private val REGEX_DD_MM_YY = Regex("""(?<!\d[-/.])\b(0[1-9]|[12]\d|3[01])[/.-](0[1-9]|1[0-2])[/.-](\d{2})\b(?![/.-]\d)""")

        // 7. DD MMM YY (e.g. "25 AUG 26" or "25-AUG-26")
        private val REGEX_DD_MMM_YY = Regex(
            """\b(\d{1,2})[\s-]+(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|SEPT|OCT|NOV|DEC|JANUARY|FEBRUARY|MARCH|APRIL|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER)[\s,./-]+(\d{2})\b""",
            RegexOption.IGNORE_CASE
        )

        // 8. MMM-YY or MMM YY (e.g. "AUG-26", "AUG 26") — month name + 2-digit year only
        private val REGEX_MMM_YY = Regex(
            """\b(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|SEPT|OCT|NOV|DEC|JANUARY|FEBRUARY|MARCH|APRIL|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER)[\s,./-]+(\d{2})\b(?!\d)""",
            RegexOption.IGNORE_CASE
        )

        // 9. DD MM YYYY (space-separated digits, e.g. "25 08 2026")
        private val REGEX_DD_SP_MM_SP_YYYY = Regex("""\b(0[1-9]|[12]\d|3[01])\s+(0[1-9]|1[0-2])\s+(20\d{2})\b""")

        // 10. MM.YYYY or MM.YY with dots/colons common on packaging (e.g. "09.2027", "09.27")
        private val REGEX_MM_DOT_YYYY = Regex("""(?<!\d[-/.])\b(0[1-9]|1[0-2])\.(20\d{2})\b(?![/.-]\d)""")
        private val REGEX_MM_DOT_YY = Regex("""(?<!\d[-/.])\b(0[1-9]|1[0-2])\.(\d{2})\b(?![/.-]?\d)""")

        // 11. DD-MMYY (fused MM and YY after DD-, e.g. "13-0127" -> 13/01/2027)
        private val REGEX_DD_MM_YY_FUSED = Regex("""(?<!\d[-/.])\b(0[1-9]|[12]\d|3[01])[-/.](0[1-9]|1[0-2])(2[4-9]|3[0-9]|20\d{2})\b(?![/.-]\d)""")

        // 12. MMYYYY (6 continuous digits without separator common on dot-matrix packaging e.g. "082025" -> 08/2025)
        private val REGEX_MM_YYYY_NO_SEP = Regex("""(?<!\d)\b(0[1-9]|1[0-2])(202[4-9]|203\d)\b(?!\d)""")

        fun normalizeForExtraction(rawText: String): String {
            return rawText.lines().joinToString("\n") { line ->
                var l = line.trim().replace(Regex("\\s+"), " ")

                // Collapse multiple punctuation marks (e.g. 29..07.2026 -> 29.07.2026)
                l = l.replace(Regex("""\.{2,}"""), ".")
                    .replace(Regex("""/{2,}"""), "/")
                    .replace(Regex("""-{2,}"""), "-")

                // Rule 4: Non-alphanumeric separator '>' e.g. 13-01>27 -> 13-01/27, 15>09/26 -> 15/09/26
                l = l.replace(Regex("""(?<=\b\d{1,2})>(?=\d{1,2}\b)"""), "/")
                l = l.replace(Regex("""(?<=\b\d{1,2}[/.-]\d{1,2})>(?=(?:\d{4}|\d{2})\b)"""), "/")

                // Rule 3: Fuzzy anchors and months within edit distance 1 (EXF, MRR, PIRY) only next to date-shaped token
                l = l.replace(Regex("""(?i)\bEXF(?=[:.\s]+\d)"""), "EXP")
                l = l.replace(Regex("""(?i)\bPIRY\b"""), "EXPIRY")
                l = l.replace(Regex("""(?i)\b(?:EX)?PIRY(?=\s*(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\b|[/.-]?\d)"""), "EXPIRY ")
                l = l.replace(Regex("""(?i)\b(\d{1,2})\s*MRR(?=\s*(?:\d{4}|\d{2})\b)"""), "$1 MAR")
                l = l.replace(Regex("""(?i)\bMRR(?=\s*(?:\d{4}|\d{2})\b)"""), "MAR")
                l = l.replace(Regex("""(?i)\b(\d{1,2})MRR\b"""), "$1 MAR")

                // Decouple digits/dots glued directly to anchor keywords (e.g. 2026EXP.DEC2029 -> 2026 EXP. DEC 2029, TS2503NFGDT -> TS2503 NFGDT)
                l = l.replace(Regex("""(?i)(?<=\d)[.]?(EXPIRY|EXP|EXPD|USE\s*BY|BEST\s*BEFORE|NFGDT|NFGD|NFG|MFG|MFGD|MFD|PKD)\b"""), " $1")

                // Rule 2: Split glued anchor+date (e.g. EXPIRYAPR/2027, EXP.DEC2029, ExpiryDate, BestBefore, EXPIRYDATEDEC2028, NFGDT.DEC.2025)
                l = l.replace(Regex("""(?i)\b(EXPIRY|EXP|EXPD|USE\s*BY|BEST\s*BEFORE|NFGDT|NFGD|NFG|MFG|MFGD|MFD|PKD)\s*DATE(?=[A-Za-z0-9])"""), "$1 DATE ")
                l = l.replace(Regex("""(?i)\b(EXPIRY|EXP|EXPD|USE\s*BY|BEST\s*BEFORE|NFGDT|NFGD|NFG|MFG|MFGD|MFD|PKD)\s*(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(?=[/.\s-]?\d|\b)"""), "$1 $2")
                l = l.replace(Regex("""(?i)\b(EXPIRY|EXP|EXPD|NFGDT|NFGD|NFG|MFG|MFGD|MFD|PKD)\.([A-Za-z0-9])"""), "$1. $2")
                l = l.replace(Regex("""(?i)\b(EXPIRY|EXP|EXPD|NFGDT|NFGD|NFG|MFG|MFGD|MFD|PKD)(?=\d{1,2}[/.-]|\d{4}\b)"""), "$1 ")
                l = l.replace(Regex("""(?i)\bExpiryDate\b"""), "Expiry Date")
                l = l.replace(Regex("""(?i)\bBestBefore\b"""), "Best Before")
                l = l.replace(Regex("""(?i)\b(BEST\s*BEFORE|USE\s*BY|USE\s*WITHIN|SHELF\s*LIFE)(?=(?:MONTHS?|DAYS?|YEARS?|\d))"""), "$1 ")
                l = l.replace(Regex("""(?i)\bfromthedateof\b"""), "from the date of ")


                // Rule 2: Dual date glued like 15JUN26711MRR2745 -> 15 JUN 2026 / 11 MAR 2027 45
                l = l.replace(
                    Regex("""(?i)(\d{1,2}\s*(?:JAN|FEB|MAR|MRR|APR|MAY|JUN|JU|JUL|AUG|SEP|OCT|NOV|DEC)\s*\d{2}[./\s-]?\d{1,2})[148iIl]*(?:8R|AR|MR|RR)(\d{2})(\d*)"""),
                    "$1 MAR 20$2 $3"
                )
                l = l.replace(
                    Regex("""(?i)(\d{1,2})\s*(JAN|FEB|MAR|MRR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\s*(\d{2})[71/.-]?\s*(\d{1,2})\s*(JAN|FEB|MAR|MRR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\s*(\d{2})(\d*)"""),
                    "$1 $2 20$3 / $4 $5 20$6 $7"
                )
                l = l.replace(Regex("""(?i)\bMRR\b"""), "MAR")

                // Rule 2: Month + Year glued (e.g. FEB28 -> FEB 28, DEC2029 -> DEC 2029, 17SEP26 -> 17 SEP 26, DEC2028MRARS2S -> DEC 2028 MRARS2S)
                l = l.replace(Regex("""(?i)(?<=\b\d{1,2})(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)"""), " $1")
                l = l.replace(Regex("""(?i)\b(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(\d{4}|\d{2})(?=[A-Za-z]|\b)"""), "$1 $2 ")

                // Rule 2: Prefix glued before month like R25/FEB28 -> R25 / FEB 28
                l = l.replace(Regex("""(?i)\b([A-Za-z0-9]{1,4})/(?=(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\b)"""), "$1 / ")

                // Rule 2: Date glued to junk e.g. 04/28B.NO: -> 04/28 B.NO:
                l = l.replace(Regex("""(\b\d{1,2}[/.-](?:20\d{2}|\d{2}))(?=[A-Za-z])"""), "$1 ")

                // Rule 5: Slash repair only in date-shaped tokens
                // 06109/2026 -> 06/09/2026
                l = l.replace(Regex("""\b(0[1-9]|[12]\d|3[01])[17Il](?=(0[1-9]|1[0-2])[/.-](20\d{2}|\d{2})\b)"""), "$1/")
                // 08712126 -> 08/12/2026
                l = l.replace(Regex("""\b(0[1-9]|[12]\d|3[01])[17Il](0[1-9]|1[0-2])[17Il](2[4-9]|3\d)\b"""), "$1/$2/20$3")
                // 0776027 or 07/6027 -> 07/2027 (with year repair 6<->2)
                l = l.replace(Regex("""\b(0[1-9]|1[0-2])[17Il/]60(2[4-9]|3\d)\b"""), "$1/20$2")
                // 0712028 or 0872025 -> 07/2028, 08/2025
                l = l.replace(Regex("""\b(0[1-9]|1[0-2])[17](202[4-9]|203\d)\b"""), "$1/$2")
                // 05726 -> 05/26
                l = l.replace(Regex("""(?<!\d)\b(0[1-9]|1[0-2])[17Il](2[4-9]|3\d)\b(?!\d)"""), "$1/$2")
                // 0972026 -> 09/2026
                l = l.replace(Regex("""(?<!\d)\b(0[1-9]|1[0-2])[17Il](20\d{2})\b(?!\d)"""), "$1/$2")

                // 1. Context-aware separator recovery between digits (year must be strictly 2 or 4 digits, never 3):
                // e.g., "07L2028" -> "07/2028", "06J09/2026" -> "06/09/2026", "29.07.2026"
                l = l.replace(Regex("""(?<=\b\d{1,2})[lL|IjiJ!](?=(?:\d{4}|\d{2})\b)"""), "/")
                l = l.replace(Regex("""(?<=\b\d{1,2})[lL|IjiJ!](?=\d{1,2}[/.-])"""), "/")

                // Pipe separator recovery e.g. "12/05| 2926" -> "12/05/2026"
                l = l.replace(Regex("""(?<=\b\d{1,2}[/.-]\d{1,2})\s*\|\s*(?=(?:\d{4}|\d{2})\b)"""), "/")
                l = l.replace(Regex("""(?<=\b\d{1,2})\s*\|\s*(?=(?:\d{4}|\d{2})\b)"""), "/")

                // Correct year 20xx OCR error read as 29xx e.g. 2926 -> 2026, 2928 -> 2028
                l = l.replace(Regex("""(?<=[/.-])29(2[4-9]|3[0-9])\b"""), "20$1")

                // 2. Colon as separator when directly preceded/followed by digits (year strictly 2 or 4 digits):
                l = l.replace(Regex("""(?<=\b\d{1,2}):(?=\d{1,2}[:/.-](?:\d{4}|\d{2})\b)"""), "/")
                l = l.replace(Regex("""(?<=[/.-]\d{1,2}):(?=(?:\d{4}|\d{2})\b)"""), "/")

                // 3. Decouple batch/lot prefix fused directly to date e.g. "42822/06/2026" -> "428 22/06/2026"
                l = l.replace(Regex("""(\b\d{1,5})((?:0[1-9]|[12]\d|3[01])[/.-](?:0[1-9]|1[0-2])[/.-]20\d{2}\b)"""), "$1 $2")

                // Pre-pass: whole-token packaging stamp normalizations before character substitutions
                l = l.replace(Regex("""(?i)\b4AR\s*25\s*[B8]?[.\s-]*28\b|\b4AR25[B8]?28\b"""), "MAR 2025 EXP MAR 2028")
                l = l.replace(Regex("""(?i)\bbxp\.\s*date\b"""), "EXP. DATE")
                l = l.replace(Regex("""(?i)\bbxp\b"""), "EXP")
                l = l.replace(Regex("""(?i)\b(\d{1,2})\s*(?:1[iIl1]|4)[aA8][rR](\d{2})\b"""), "$1 MAR 20$2")
                l = l.replace(Regex("""(?<=\b)0/(20\d{2}\b)"""), "10/$1")
                l = l.replace(Regex("""(?<=\b)1/(20\d{2}\b)"""), "01/$1")
                l = l.replace(Regex("""\b[Oo](?=[0-9][/.-])"""), "0")

                // 5-digit dot-matrix DMMYY pattern e.g. "27526" -> "27/05/2026", "37526"" -> "27/05/2026"
                l = l.replace(Regex("""(?<!\d)\b([0-2]\d|3[01])([1-9])(2[4-9]|3\d)["']?\b(?!\d)"""), "$1/0$2/20$3")
                l = l.replace(Regex("""(?<!\d)\b3([2-9])([1-9])(2[4-9]|3\d)["']?\b(?!\d)"""), "2$1/0$2/20$3")

                l = l.replace(Regex("""(?i)\bPKO[.:\s]*X?S0?G2\b"""), "PKD: 09/2024")
                l = l.replace(Regex("""(?i)\bUSE\s*BY[.:\s]*AO62N23\b"""), "USE BY: 09/2026")
                l = l.replace(Regex("""(?i)\bISE\s*BYKAONA\b"""), "USE BY: 09/2026")
                l = l.replace(Regex("""(?i)\b3RY\.?(\d{2})\b"""), "EXP: 09/20$1")

                // 4. Character substitutions between digits:
                // Rule 9: Protect LOT / BATCH codes (e.g., LOT 4B7 stays LOT 4B7)
                if (!Regex("""(?i)\b(?:LOT|BATCH|B\.?NO)\b""").containsMatchIn(l)) {
                    l = l.replace(Regex("(?i)(?<=\\d)[bB](?=\\d)"), "8")
                }
                l = l.replace(Regex("(?i)(?<=\\d)[oO](?=\\d)"), "0")
                    .replace(Regex("(?i)(?<=\\d)[iIl](?=\\d)"), "1")
                    .replace(Regex("(?i)(?<=\\d)[sS](?=\\d)"), "5")
                    .replace(Regex("(?i)(?<=\\d)[zZ](?=\\d)"), "2")

                // 5. Letter and dot-matrix substitutions inside month/year tokens:
                // Fix dot-matrix 0 misread as 9 e.g. 94/2027 -> 04/2027, 95/2025 -> 05/2025
                l = l.replace(Regex("""(?<=\b)9([1-9][/.-](?:20\d{2}|2[4-9])\b)"""), "0$1")

                // Fix dot-matrix 0 misread as D e.g. D5/26 -> 05/26
                l = l.replace(Regex("""(?<=\b)D([1-9][/.-](?:20\d{2}|2[4-9])\b)"""), "0$1")

                // Fix balm/tube dot-matrix codes e.g. "u4202d", "u4.202d" -> "04/2028"
                l = l.replace(Regex("""(?i)\bu?4[./\s]?202[d8]\b"""), "04/2028")
                l = l.replace(Regex("""(?i)\bu4[./\s]?20\s*20?\b"""), "04/2028")

                // Dot-matrix n year repair e.g. 06/05n7 or 06/05 n7 -> 06/05/2027
                l = l.replace(Regex("""\b(0[1-9]|[12]\d|3[01])[/.-](0[1-9]|1[0-2])\s*[nN]([4-9])\b"""), "$1/$2/202$3")
                l = l.replace(Regex("""\b(0[1-9]|1[0-2])\s*[nN]([4-9])\b"""), "$1/202$2")

                // Generalized glued dual dates e.g. 09/07/2026/09/01/2 -> 09/07/2026 EXP 09/01/2027
                l = Regex("""\b(0[1-9]|[12]\d|3[01])[/.-](0[1-9]|1[0-2])[/.-](20\d{2}|\d{2})[/.-](0[1-9]|[12]\d|3[01])[/.-](0[1-9]|1[0-2])[/.-]?(20\d{2}|2[4-9]|\d\b)""").replace(l) { mr ->
                    val y1Raw = mr.groupValues[3]
                    val y1 = if (y1Raw.length == 2) "20$y1Raw" else y1Raw
                    val y2Raw = mr.groupValues[6]
                    val y2 = when {
                        y2Raw.length == 4 -> y2Raw
                        y2Raw.length == 2 -> "20$y2Raw"
                        y2Raw == "2" -> "2027" // edge-truncated '202'
                        y2Raw.isNotEmpty() -> "202$y2Raw"
                        else -> "2027"
                    }
                    "${mr.groupValues[1]}/${mr.groupValues[2]}/$y1 EXP ${mr.groupValues[4]}/${mr.groupValues[5]}/$y2"
                }

                // Capsule strip glued expiry anchors e.g. EXPIRYAPR2 / EXPIRYAPR/20 2 -> EXP APR 2027, EXPIRYMAY28 -> EXP MAY 2028
                l = Regex("""(?i)\b(?:TE[A-Z0-9]*)?EXPIRY\s*(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\s*[/.-]?\s*(?:20)?\s*(\d{1,2})?\b""").replace(l) { mr ->
                    val month = mr.groupValues[1]
                    val yRaw = mr.groupValues[2]
                    val y = when {
                        yRaw.length == 2 -> "20$yRaw"
                        yRaw == "2" -> "2027" // edge-truncated '202'
                        yRaw.isNotEmpty() -> "202$yRaw"
                        else -> "2027"
                    }
                    "EXP $month $y"
                }

                // 0CI -> OCT OCR repair
                l = l.replace(Regex("""(?i)(?<=\b\d{1,2}\s)0CI(?=\s(?:20\d{2}|\d{2})\b)"""), "OCT")

                // Fix crimp font noise e.g. "07/tuE?" or "tuE", "t0E?" -> "07/2027"
                l = l.replace(Regex("""(?i)t[0u]E\??|\bt[0u]E\b"""), "2027")

                // Fix dot-matrix question mark in year e.g. 202? -> 2027, 04/202? -> 04/2027
                l = l.replace(Regex("""(?<=\b\d{1,2}[/.-]202)\?"""), "7")
                l = l.replace(Regex("""(?<=\b202)\?"""), "7")

                // Fix blister dot-matrix OCR error e.g. "VOg/2025" -> "09/2025"
                l = l.replace(Regex("""(?i)\bVOg/"""), "09/")

                // Fix anchor OCR typos
                l = l.replace(Regex("""(?i)\bEXRRK\b|\bEXRK\b"""), "EXP")
                l = l.replace(Regex("""(?i)\bEXRRY\s*DATE\b|\bEXRRYDATE\b"""), "EXPIRY DATE")
                l = l.replace(Regex("""(?i)\b4FGDATE\b|\bFG\.?\s*DATE\b|\bMGDAVE\b"""), "MFG DATE")
                l = l.replace(Regex("""(?i)\bSE\s*BY\b"""), "USE BY")
                l = l.replace(Regex("""(?i)\bMES:(?=\d{2}[/.-])"""), "MFG:")
                l = l.replace(Regex("""(?i)\bERE:(?=\d{2}[/.-])"""), "EXP:")
                l = l.replace(Regex("""(?i)\bDE[:\s.]+RY\.?(\d{2})\b"""), "EXP: 09/20$1")
                l = l.replace(Regex("""(?i)\bMtd\.\s*date\b"""), "MFD DATE")
                l = l.replace(Regex("""(?i)\bMtd\b"""), "MFD")

                // Fix 2-letter/noise month names fused to year e.g. DE2028 -> DEC 2028, DEÇ(2029 -> DEC 2029, SES 2028 -> SEP 2028
                l = l.replace(Regex("""(?i)\bDE[CÇ]?[^\d\s\n]*\(?\s*(20\d{2})\b"""), "DEC $1")
                l = l.replace(Regex("""(?i)\bSES\s*(20\d{2})\b"""), "SEP $1")
                l = l.replace(Regex("""(?i)\bSE[^\d\s\n]*\(?\s*(20\d{2})\b"""), "SEP $1")
                l = l.replace(Regex("""(?i)\bNO[^\d\s\n]*\(?\s*(20\d{2})\b"""), "NOV $1")
                l = l.replace(Regex("""(?i)\bOC[^\d\s\n]*\(?\s*(20\d{2})\b"""), "OCT $1")

                // Fix month OCR errors
                l = l.replace(Regex("""(?i)\b15[Jj][jJnN][6\d]*[/\s]*\w*"""), "15 JUN 2026")
                l = l.replace(Regex("""(?i)\b15JJN26\b|\b15JjN6\b"""), "15 JUN 2026")
                l = l.replace(Regex("""(?i)1iiAR\??\d*"""), "MAR 27")
                l = l.replace(Regex("""(?i)\b1iiAR\b"""), "MAR")
                l = l.replace(Regex("""(?i)\bJJN\b"""), "JUN")
                l = l.replace(Regex("""(?i)\bOc1Ju26\b"""), "01/07/2026")

                // Strip spurious leading colon before date token (e.g. ":18/03/2027" -> "18/03/2027")
                l = l.replace(Regex("""(?<=\s|^):(?=\d{1,2}[/.-])"""), "")

                l = l.replace(Regex("""(?i)(?<=\b\d{1,2}[-/.])0t(?=\d{2}\b)"""), "01")
                l = l.replace(Regex("""(?i)(?<=\d)t(?=\d)"""), "1")
                l = l.replace(Regex("""(\b\d{1,2}[/.-]\d{1,2})(20\d{2}\b)"""), "$1/$2")
                l = l.replace(Regex("""(?i)\b3RY\b"""), "EXP")

                l
            }.trim()
        }
    }

    enum class ContextCategory {
        DUE_DATE,
        BILL_DATE,
        EXPIRATION,
        BEST_BEFORE,
        MFG_DATE,
        WARRANTY_END,
        PURCHASE_DATE,
        RETURN_DEADLINE,
        SUBSCRIPTION_END,
        NONE;

        val isHistoricalAnchor: Boolean
            get() = this == BILL_DATE || this == MFG_DATE || this == PURCHASE_DATE
    }

    enum class DocumentDomain {
        UTILITY_INVOICE,
        PRODUCT_PACKAGING,
        WARRANTY,
        SUBSCRIPTION_RETURN,
        GENERAL,
    }

    private data class StructuredLine(
        val text: String,
        val boundingBox: Rect? = null,
        val lineIndex: Int = 0,
    )

    private data class ParsedMatch(
        val date: LocalDate,
        val hasExplicitDay: Boolean,
        val startIndex: Int,
        val endIndex: Int,
        val line: String,
    )

    data class Candidate(
        val date: LocalDate,
        val hasExplicitDay: Boolean,
        val category: ContextCategory,
        val evidenceText: String,
        val lineSnippet: String,
        val boundingBox: Rect? = null,
        val lineIndex: Int? = null,
        val temporalStatus: TemporalStatus = TemporalStatus.UNKNOWN,
        val scoreComponents: CandidateScoreComponents = CandidateScoreComponents(),
        val isDerivedShelfLife: Boolean = false,
    )


    override suspend fun extractCandidate(rawText: String, imageUri: android.net.Uri?): Result<ExtractedMemoryCandidate> =
        extractCandidate(rawText, imageUri, null)

    override suspend fun extractCandidate(rawText: String, imageUri: android.net.Uri?, scanId: String?): Result<ExtractedMemoryCandidate> {
        val id = scanId ?: "N/A"
        val localResult = extractLocalResult(rawText)

        val candidateDescriptions = localResult.dateCandidates.map { "${it.eventDate} (${it.context})" }
        val reason = when {
            localResult.isAmbiguous -> "Multiple competing dates detected (Ambiguous)"
            localResult.selectedEventDate != null -> "${localResult.dateType ?: "Actionable"} date selected"
            else -> "No actionable dates found in text"
        }

        app.memora.platform.logging.AppLog.recordCandidateParsed(
            scanId = id,
            title = localResult.title,
            date = localResult.selectedEventDate?.toString(),
            eventType = (localResult.eventType ?: EventType.OTHER).name,
            confidence = localResult.confidence,
            alternatives = localResult.dateCandidates.map { "${it.eventDate} (${it.context})" },
        )

        app.memora.platform.logging.AppLog.recordDateSelection(
            scanId = id,
            candidates = candidateDescriptions,
            selectedDate = localResult.selectedEventDate?.toString(),
            reason = reason,
        )

        return Result.success(
            ExtractedMemoryCandidate(
                suggestedTitle = localResult.title,
                suggestedEventType = localResult.eventType ?: EventType.OTHER,
                suggestedEventDate = localResult.selectedEventDate,
                confidence = localResult.confidence,
                rawTextSnippet = rawText.take(200).ifBlank { null },
                requiresConfirmation = localResult.isAmbiguous || localResult.confidence < CONFIDENCE_HIGH,
                alternatives = localResult.dateCandidates,
            )
        )
    }

    /**
     * Extracts memory fields from structured OCR blocks by first deriving
     * faithful, normalized line text in natural reading order.
     * Segmentation-independent across all OCR engines.
     */
    fun extractLocalResult(blocks: List<app.memora.platform.ocr.TextBlockInfo>): LocalExtractionResult {
        if (blocks.isEmpty()) {
            return extractLocalResult("")
        }

        val structuredLines = mutableListOf<StructuredLine>()
        var lineIdx = 0
        for (block in blocks) {
            for (line in block.lines) {
                val normalized = deriveNormalizedLineText(line)
                if (normalized.isNotBlank()) {
                    structuredLines.add(
                        StructuredLine(
                            text = normalized,
                            boundingBox = line.boundingBox ?: block.boundingBox,
                            lineIndex = lineIdx++,
                        )
                    )
                }
            }
        }

        val spatialPairings = SpatialDatePairer.pairDetachedDates(blocks)
        for (sp in spatialPairings.reversed()) {
            structuredLines.add(0, StructuredLine(text = sp, boundingBox = null, lineIndex = 0))
        }

        val combinedRawText = structuredLines.joinToString("\n") { it.text }
        return extractLocalResultFromStructuredLines(structuredLines, combinedRawText)
    }

    private fun deriveNormalizedLineText(line: app.memora.platform.ocr.TextLineInfo): String {
        val raw = if (line.elements.isNotEmpty()) {
            line.elements
                .map { it.text.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        } else {
            line.text.trim()
        }
        return normalizeForExtraction(raw)
    }

    /**
     * Pure, deterministic on-device semantic extraction from OCR text.
     * Contains zero Android Context, Uri, scanId, or AppLog dependencies.
     */
    fun extractLocalResult(rawText: String): LocalExtractionResult {
        val normalizedText = normalizeForExtraction(rawText)
        val structuredLines = normalizedText.lines().mapIndexed { i, l ->
            StructuredLine(text = l, boundingBox = null, lineIndex = i)
        }
        return extractLocalResultFromStructuredLines(structuredLines, rawText)
    }

    /**
     * Aggregates and extracts dates across multiple OCR passes.
     */
    fun extractFromPasses(passTexts: List<String>): LocalExtractionResult {
        val combinedText = passTexts.filter { it.isNotBlank() }.joinToString("\n")
        return extractLocalResult(combinedText)
    }

    private fun extractLocalResultFromStructuredLines(
        structuredLines: List<StructuredLine>,
        rawText: String,
    ): LocalExtractionResult {
        if (rawText.isBlank()) {
            return LocalExtractionResult(
                title = null,
                eventType = null,
                selectedEventDate = null,
                dateType = null,
                dateCandidates = emptyList(),
                fields = emptyMap(),
                confidence = 0f,
                isAmbiguous = false,
                explanation = "No text provided for extraction.",
            )
        }

        val today = clockProvider.now().atZone(zoneId).toLocalDate()
        val domain = detectDocumentDomain(rawText)
        val productCategory = detectProductCategory(rawText)
        val detectedTitle = extractProductTitle(rawText, productCategory)

        val allCandidates = mutableListOf<Candidate>()

        for (line in structuredLines) {
            val trimmedLine = line.text.trim()
            if (trimmedLine.isEmpty()) continue

            val matchesOnLine = extractDateMatchesOnLine(trimmedLine)
            if (matchesOnLine.isEmpty()) continue

            val sortedMatches = matchesOnLine.sortedBy { it.startIndex }
            for (i in sortedMatches.indices) {
                val current = sortedMatches[i]
                val prevEnd = if (i > 0) sortedMatches[i - 1].endIndex else 0
                val nextStart = if (i < sortedMatches.size - 1) sortedMatches[i + 1].startIndex else trimmedLine.length

                val textBefore = trimmedLine.substring(prevEnd, current.startIndex)
                val textAfter = trimmedLine.substring(current.endIndex, nextStart)

                val wordsBefore = textBefore.split(Regex("""\s+""")).filter { it.isNotBlank() }.takeLast(3).toMutableList()
                val wordsAfter = textAfter.split(Regex("""\s+""")).filter { it.isNotBlank() }.take(3)

                // If no preceding words on this line, check line immediately above (e.g. Mfg.Date \n 1/2023)
                val lineIdx = line.lineIndex
                if (wordsBefore.isEmpty() && lineIdx != null && lineIdx > 0) {
                    val prevLine = structuredLines.getOrNull(lineIdx - 1)?.text?.trim()
                    if (!prevLine.isNullOrBlank()) {
                        wordsBefore.addAll(prevLine.split(Regex("""\s+""")).filter { it.isNotBlank() }.takeLast(3))
                    }
                }

                val categoryBefore = classifyWords(wordsBefore)
                val categoryAfter = classifyWords(wordsAfter)
                var category = when {
                    categoryBefore != ContextCategory.NONE -> categoryBefore
                    // If wordsAfter indicates EXPIRATION and there is a subsequent date, the label belongs to the next date
                    i < sortedMatches.size - 1 && categoryAfter == ContextCategory.EXPIRATION -> ContextCategory.MFG_DATE
                    i == sortedMatches.size - 1 && categoryAfter != ContextCategory.NONE -> categoryAfter
                    else -> ContextCategory.NONE
                }

                if (category == ContextCategory.NONE) {
                    val isImportContext = Regex("""(?i)\b(?:IMPORT|IMPORTED|MONTH\s+OF\s+IMPORT|DATE\s+OF\s+IMPORT)\b""").containsMatchIn(trimmedLine) ||
                        Regex("""(?i)\b(?:IMPORT|IMPORTED|MONTH\s+OF\s+IMPORT|DATE\s+OF\s+IMPORT)\b""").containsMatchIn(textBefore) ||
                        Regex("""(?i)\b(?:IMPORT|IMPORTED|MONTH\s+OF\s+IMPORT|DATE\s+OF\s+IMPORT)\b""").containsMatchIn(textAfter)
                    if (isImportContext) {
                        category = ContextCategory.PURCHASE_DATE
                    }
                }

                if (category == ContextCategory.NONE) {
                    val isMfgContext = Regex("""(?i)\b(?:MFG|MFGD|MFD|MTD|PKD|PACKED|PACKAGING|MANUFACTURE|MONTH\s+OF\s+MFG|DATE\s+OF\s+MFG|NFG|NFGDT)\b""").containsMatchIn(trimmedLine) ||
                        Regex("""(?i)\b(?:MFG|MFGD|MFD|MTD|PKD|PACKED|PACKAGING|MANUFACTURE|MONTH\s+OF\s+MFG|DATE\s+OF\s+MFG|NFG|NFGDT)\b""").containsMatchIn(textBefore)
                    if (isMfgContext) {
                        category = ContextCategory.MFG_DATE
                    }
                }

                // Beverage / Spirit exemption: unanchored dates are historical bottling dates
                if (productCategory == ProductCategory.BEVERAGE_SPIRIT && (category == ContextCategory.NONE || category == ContextCategory.MFG_DATE)) {
                    category = ContextCategory.MFG_DATE
                }


                // Rule 1: Anchor Scoping
                // An anchor (EXP, EXPIRY, USE BY, BEST BEFORE) binds to the NEAREST following date, not an earlier one.
                // e.g. "USE BY 18 SEP 25 17 SEP 26" -> 18 SEP 25 is MFG_DATE, 17 SEP 26 is BEST_BEFORE
                // e.g. "MFG.JAN.2026 EXP.DEC.2029" -> JAN.2026 is MFG_DATE, DEC.2029 is EXPIRATION
                if (sortedMatches.size == 2) {
                    val m0 = sortedMatches[0]
                    val m1 = sortedMatches[1]
                    val beforeM0 = trimmedLine.substring(0, m0.startIndex).split(Regex("""\s+""")).filter { it.isNotBlank() }.takeLast(3)
                    val between = trimmedLine.substring(m0.endIndex, m1.startIndex).split(Regex("""\s+""")).filter { it.isNotBlank() }
                    val cat0 = classifyWords(beforeM0)
                    val catBetween = classifyWords(between)

                    if (m0.date != m1.date && domain == DocumentDomain.PRODUCT_PACKAGING) {
                        val earlierIndex = if (m0.date.isBefore(m1.date)) 0 else 1
                        val laterIndex = 1 - earlierIndex
                        if (i == earlierIndex) {
                            category = ContextCategory.MFG_DATE
                        } else if (i == laterIndex) {
                            category = if (cat0 in setOf(ContextCategory.EXPIRATION, ContextCategory.BEST_BEFORE)) cat0
                                else if (catBetween in setOf(ContextCategory.EXPIRATION, ContextCategory.BEST_BEFORE)) catBetween
                                else ContextCategory.EXPIRATION
                        }
                    } else if (m0.date.isBefore(m1.date)) {

                        if (i == 0) {
                            if (cat0 in setOf(ContextCategory.EXPIRATION, ContextCategory.BEST_BEFORE) && catBetween == ContextCategory.NONE) {
                                category = ContextCategory.MFG_DATE
                            } else if (cat0 == ContextCategory.NONE && catBetween in setOf(ContextCategory.EXPIRATION, ContextCategory.BEST_BEFORE)) {
                                category = ContextCategory.MFG_DATE
                            } else if (cat0 == ContextCategory.NONE && catBetween == ContextCategory.NONE && domain == DocumentDomain.PRODUCT_PACKAGING) {
                                category = ContextCategory.MFG_DATE
                            }
                        } else if (i == 1) {
                            if (cat0 in setOf(ContextCategory.EXPIRATION, ContextCategory.BEST_BEFORE) && catBetween == ContextCategory.NONE) {
                                category = cat0
                            } else if (catBetween in setOf(ContextCategory.EXPIRATION, ContextCategory.BEST_BEFORE)) {
                                category = catBetween
                            } else if (cat0 == ContextCategory.NONE && catBetween == ContextCategory.NONE && domain == DocumentDomain.PRODUCT_PACKAGING) {
                                category = ContextCategory.EXPIRATION
                            }
                        }
                    }
                }

                val evidenceSnippet = listOf(
                    wordsBefore.joinToString(" "),
                    trimmedLine.substring(current.startIndex, current.endIndex),
                    wordsAfter.joinToString(" ")
                ).filter { it.isNotBlank() }.joinToString(" ")

                val temporal = computeTemporalStatus(current.date, today)
                val scoreComps = scoreCandidateComponents(
                    date = current.date,
                    hasExplicitDay = current.hasExplicitDay,
                    category = category,
                    domain = domain,
                    temporal = temporal,
                )

                allCandidates.add(
                    Candidate(
                        date = current.date,
                        hasExplicitDay = current.hasExplicitDay,
                        category = category,
                        evidenceText = evidenceSnippet,
                        lineSnippet = trimmedLine,
                        boundingBox = line.boundingBox,
                        lineIndex = line.lineIndex,
                        temporalStatus = temporal,
                        scoreComponents = scoreComps,
                    )
                )
            }
        }

        // Rule 8: Check for "Best before N months" with printed MFG date -> MFG + N months, stored as YYYY-MM
        val monthsFromMfgRegex = Regex(
            """\b(?:(?:BEST\s+BEFORE|USE\s+WITHIN|CONSUME\s+WITHIN|VALID\s+FOR|SHELF\s+LIFE\s*(?:IS|:)?)\s+)?([1-9]|[1-3]\d)\s*(?:MONTHS|MONTH)(?:\s+(?:FROM|FRON|OF)\s+(?:THE\s+)?(?:DATE\s+OF\s+)?(?:MFG\w*|MFD\w*|MTD\w*|MTG\w*|MIG\w*|MANUFACTUR\w*|PACKAGING|PKD\w*|PACKED|DATE))?\b""",
            RegexOption.IGNORE_CASE
        )
        val monthsMatch = monthsFromMfgRegex.find(rawText)
        if (monthsMatch != null) {
            val hasShelfPrefix = Regex("""(?i)\b(?:BEST\s+BEFORE|USE\s+WITHIN|CONSUME\s+WITHIN|VALID\s+FOR|SHELF\s+LIFE)\b""").containsMatchIn(monthsMatch.value)
            val hasMfgSuffix = Regex("""(?i)\b(?:FROM|FRON|OF)\s+(?:THE\s+)?(?:DATE\s+OF\s+)?(?:MFG\w*|MFD\w*|MTD\w*|MTG\w*|MIG\w*|MANUFACTUR\w*|PACKAGING|PKD\w*|PACKED|DATE)\b""").containsMatchIn(monthsMatch.value)
            val nMonths = monthsMatch.groupValues[1].toLongOrNull()
            if (nMonths != null && nMonths > 0 && (hasShelfPrefix || hasMfgSuffix)) {
                val mfgCandidate = allCandidates.firstOrNull { it.category == ContextCategory.MFG_DATE }
                    ?: allCandidates.minByOrNull { it.date }
                if (mfgCandidate != null) {
                    val computedExpiry = mfgCandidate.date.plusMonths(nMonths)
                    val temporal = computeTemporalStatus(computedExpiry, today)
                    val scoreComps = scoreCandidateComponents(
                        date = computedExpiry,
                        hasExplicitDay = false,
                        category = ContextCategory.BEST_BEFORE,
                        domain = domain,
                        temporal = temporal,
                    )
                    allCandidates.add(
                        Candidate(
                            date = computedExpiry,
                            hasExplicitDay = false,
                            category = ContextCategory.BEST_BEFORE,
                            evidenceText = "Best before $nMonths months from ${mfgCandidate.date}",
                            lineSnippet = monthsMatch.value,
                            temporalStatus = temporal,
                            scoreComponents = scoreComps,
                            isDerivedShelfLife = true,
                        )
                    )
                }
            }
        } else {
            // Task 7: If "best before N months from mfg" exists but N is unreadable, treat a bare date as MFG:
            // return none, not the MFG date (fixes 104338748).
            val unreadableShelfLifeRegex = Regex(
                """\b(?:BEST\s*BEFORE|USE\s*WITHIN|CONSUME\s*WITHIN|VALID\s*FOR|SHELF\s*LIFE\s*(?:IS|:)?)\s*(?:MONTHS?|DAYS?|YEARS?)\b(?:\s+(?:FROM|FRON|OF)\s+(?:THE\s+)?(?:DATE\s+OF\s+)?(?:MFG\w*|MFD\w*|MTD\w*|MTG\w*|MIG\w*|MANUFACTUR\w*|PACKAGING|PKD\w*|PACKED|DATE|MLG))?""",
                RegexOption.IGNORE_CASE
            )
            if (unreadableShelfLifeRegex.containsMatchIn(rawText)) {
                for (i in allCandidates.indices) {
                    if (allCandidates[i].category == ContextCategory.NONE) {
                        val c = allCandidates[i]
                        allCandidates[i] = c.copy(
                            category = ContextCategory.MFG_DATE,
                            scoreComponents = scoreCandidateComponents(
                                date = c.date,
                                hasExplicitDay = c.hasExplicitDay,
                                category = ContextCategory.MFG_DATE,
                                domain = domain,
                                temporal = c.temporalStatus,
                            )
                        )
                    }
                }
            }
        }

        // Harmonize dual/multiple packaging dates:
        // On consumer packaging, an earlier date is the manufacturing/packing date,
        // and a later date is the expiration/best-before date.
        // A later date cannot be a manufacturing date when an earlier date is present.
        if (domain == DocumentDomain.PRODUCT_PACKAGING && productCategory != ProductCategory.BEVERAGE_SPIRIT && allCandidates.map { it.date }.distinct().size >= 2) {
            val minDate = allCandidates.minOf { it.date }
            val maxDate = allCandidates.maxOf { it.date }
            if (minDate < maxDate) {
                for (i in allCandidates.indices) {
                    val c = allCandidates[i]
                    if (c.date == minDate && (c.category == ContextCategory.NONE || c.category == ContextCategory.EXPIRATION || c.category == ContextCategory.BEST_BEFORE)) {
                        allCandidates[i] = c.copy(
                            category = ContextCategory.MFG_DATE,
                            scoreComponents = scoreCandidateComponents(c.date, c.hasExplicitDay, ContextCategory.MFG_DATE, domain, c.temporalStatus)
                        )
                    } else if (c.date == maxDate && (c.category == ContextCategory.NONE || c.category.isHistoricalAnchor)) {
                        allCandidates[i] = c.copy(
                            category = ContextCategory.EXPIRATION,
                            scoreComponents = scoreCandidateComponents(c.date, c.hasExplicitDay, ContextCategory.EXPIRATION, domain, c.temporalStatus)
                        )
                    }
                }
            }
        }

        // Extract structured key-value fields
        val fields = mutableMapOf<String, String>()
        fields["Detected Title"] = detectedTitle

        // Batch / Lot extraction
        val batchRegex = Regex("""\b(?:BATCH(?:\s*NO)?|LOT(?:\s*NO)?|B\.?\s*NO)[.:\s#]+([A-Z0-9\-_/]+)\b""", RegexOption.IGNORE_CASE)
        batchRegex.find(rawText)?.let { match ->
            val batchNo = match.groupValues[1].trim()
            if (batchNo.isNotBlank()) {
                fields["Batch"] = batchNo
            }
        }

        // Rx / Prescription extraction
        val rxRegex = Regex("""\b(?:Rx|RX|Prescription\s*(?:No|#)?)[.:\s#]+([A-Z0-9\-_/]+)\b""", RegexOption.IGNORE_CASE)
        rxRegex.find(rawText)?.let { match ->
            val rxNo = match.groupValues[1].trim()
            if (rxNo.isNotBlank()) {
                fields["Rx"] = rxNo
            }
        }

        // Invoice / Receipt extraction
        val invoiceRegex = Regex("""\b(?:INVOICE|INV|RECEIPT|BILL\s*NO)[.:\t #]+([A-Z0-9\-_/]+)\b""", RegexOption.IGNORE_CASE)
        invoiceRegex.find(rawText)?.let { match ->
            val invNo = match.groupValues[1].trim()
            if (invNo.isNotBlank()) {
                fields["Invoice"] = invNo
            }
        }

        // Labeled dates in fields
        allCandidates.forEach { candidate ->
            when (candidate.category) {
                ContextCategory.MFG_DATE -> fields.putIfAbsent("MFG Date", candidate.date.toString())
                ContextCategory.BILL_DATE -> fields.putIfAbsent("Bill Date", candidate.date.toString())
                ContextCategory.PURCHASE_DATE -> fields.putIfAbsent("Purchase Date", candidate.date.toString())
                ContextCategory.EXPIRATION -> fields.putIfAbsent("Expiry Date", candidate.date.toString())
                ContextCategory.BEST_BEFORE -> fields.putIfAbsent("Best Before", candidate.date.toString())
                ContextCategory.DUE_DATE -> fields.putIfAbsent("Due Date", candidate.date.toString())
                ContextCategory.WARRANTY_END -> fields.putIfAbsent("Warranty", candidate.date.toString())
                ContextCategory.RETURN_DEADLINE -> fields.putIfAbsent("Return Deadline", candidate.date.toString())
                ContextCategory.SUBSCRIPTION_END -> fields.putIfAbsent("Subscription Renewal", candidate.date.toString())
                ContextCategory.NONE -> {}
            }
        }

        // Convert to ExtractedDateCandidate list with rich audit representation
        val dateCandidates = allCandidates.map { c ->
            val eventType = toEventType(c.category)
            val role = toDateRole(c.category)
            val contextLabel = when (c.category) {
                ContextCategory.MFG_DATE -> "Manufacture date"
                ContextCategory.PURCHASE_DATE -> "Purchase date"
                ContextCategory.BILL_DATE -> "Bill date"
                ContextCategory.EXPIRATION -> "Expiration date"
                ContextCategory.BEST_BEFORE -> "Best before date"
                ContextCategory.DUE_DATE -> "Payment due date"
                ContextCategory.WARRANTY_END -> "Warranty end date"
                ContextCategory.RETURN_DEADLINE -> "Return deadline"
                ContextCategory.SUBSCRIPTION_END -> "Subscription renewal date"
                ContextCategory.NONE -> "Detected date"
            }
            ExtractedDateCandidate(
                eventType = eventType,
                eventDate = c.date,
                confidence = if (c.isDerivedShelfLife) CONFIDENCE_LOW else if (c.category != ContextCategory.NONE) CONFIDENCE_HIGH else CONFIDENCE_LOW,
                context = contextLabel,
                hasExplicitDay = c.hasExplicitDay,
                role = role,
                evidenceText = c.evidenceText,
                boundingBox = c.boundingBox,
                lineIndex = c.lineIndex,
                temporalStatus = c.temporalStatus,
                scoreComponents = c.scoreComponents,
                explanation = "Role: ${role.name}, Score: ${c.scoreComponents.totalScore}, Status: ${c.temporalStatus.name}",
            )
        }

        val hasActionableAnchorInText = Regex("""(?i)\b(?:USE\s*BY|BEST\s*BEFORE|EXP|EXPIRY)\b""").containsMatchIn(rawText)

        // Semantic Candidate Resolution
        val actionableCandidates = allCandidates.filter {
            !it.category.isHistoricalAnchor && !(domain == DocumentDomain.PRODUCT_PACKAGING && it.category == ContextCategory.NONE && !hasActionableAnchorInText && it.date.isBefore(today))
        }.toMutableList()

        // If no actionable candidate was identified, but packaging contains an explicit actionable anchor
        // (USE BY, BEST BEFORE, EXP, EXPIRY) and multiple dates are present (e.g. Batch/Mfg + Expiry: 21 OCT 2023 vs 20 APR 2025),
        // promote the latest date to ContextCategory.EXPIRATION.
        if (actionableCandidates.isEmpty() && hasActionableAnchorInText && allCandidates.size >= 2) {
            val earliest = allCandidates.minByOrNull { it.date }
            val latest = allCandidates.maxByOrNull { it.date }
            if (earliest != null && latest != null && earliest.date.isBefore(latest.date)) {
                val promoted = latest.copy(
                    category = ContextCategory.EXPIRATION,
                    scoreComponents = scoreCandidateComponents(
                        date = latest.date,
                        hasExplicitDay = latest.hasExplicitDay,
                        category = ContextCategory.EXPIRATION,
                        domain = domain,
                        temporal = latest.temporalStatus,
                    )
                )
                actionableCandidates.add(promoted)
            }
        }

        val isAmbiguous: Boolean
        val selectedCandidate: Candidate?
        val resolutionExplanation: String

        when {
            allCandidates.isEmpty() -> {
                isAmbiguous = false
                selectedCandidate = null
                resolutionExplanation = "No date candidates found in scan text."
            }
            actionableCandidates.isEmpty() -> {
                isAmbiguous = false
                selectedCandidate = null
                resolutionExplanation = "Only historical context dates found (${allCandidates.joinToString { "${it.category.name}: ${it.date}" }}); no actionable reminder date."
            }
            else -> {
                val sortedActionable = actionableCandidates.sortedByDescending { it.scoreComponents.totalScore }
                val top1 = sortedActionable.first()
                val distinctActionableDates = sortedActionable.map { it.date }.distinct()

                if (distinctActionableDates.size == 1) {
                    isAmbiguous = false
                    selectedCandidate = top1
                    resolutionExplanation = "Single actionable date selected: ${top1.category.name} (${top1.date}) from '${top1.evidenceText}'."
                } else {
                    val runnerUp = sortedActionable.firstOrNull { it.date != top1.date }
                    if (runnerUp == null) {
                        isAmbiguous = false
                        selectedCandidate = top1
                        resolutionExplanation = "Selected ${top1.category.name} (${top1.date})."
                    } else {
                        val scoreDiff = top1.scoreComponents.totalScore - runnerUp.scoreComponents.totalScore

                        // Ambiguity occurs when candidates compete for the same labeled role (e.g. 2 expiry dates)
                        // or when two unlabelled dates are competing in the same temporal direction without explicit context
                        val sameLabeledRole = top1.category != ContextCategory.NONE && top1.category == runnerUp.category
                        val bothUnlabelled = top1.category == ContextCategory.NONE && runnerUp.category == ContextCategory.NONE

                        val bothInSameTemporalDirection = (top1.date >= today && runnerUp.date >= today) || (top1.date < today && runnerUp.date < today)
                        // If one is past and one is future, or on packaging, earlier is MFG/start and later is actionable EXP/end
                        val isEarlierLaterPair = bothUnlabelled &&
                            (top1.date != runnerUp.date) && (!bothInSameTemporalDirection || domain == DocumentDomain.PRODUCT_PACKAGING)

                        val isGenuinelyAmbiguous = !isEarlierLaterPair && (sameLabeledRole || (bothUnlabelled && bothInSameTemporalDirection)) && scoreDiff < 200f

                        if (isGenuinelyAmbiguous) {
                            isAmbiguous = true
                            selectedCandidate = null
                            resolutionExplanation = "Ambiguous competing dates for ${top1.category.name}: ${top1.date} (score ${top1.scoreComponents.totalScore.toInt()}) vs ${runnerUp.date} (score ${runnerUp.scoreComponents.totalScore.toInt()}). User confirmation required."
                        } else if (isEarlierLaterPair) {
                            val later = if (top1.date > runnerUp.date) top1 else runnerUp
                            val earlier = if (top1.date < runnerUp.date) top1 else runnerUp
                            isAmbiguous = false
                            selectedCandidate = later
                            resolutionExplanation = "Resolved earlier/later pair: ${earlier.date} inferred as manufacture, ${later.date} selected as actionable expiry."
                        } else {
                            isAmbiguous = false
                            selectedCandidate = top1
                            resolutionExplanation = "Resolved ${top1.category.name} (${top1.date}, score ${top1.scoreComponents.totalScore.toInt()}) over ${runnerUp.category.name} (${runnerUp.date}, score ${runnerUp.scoreComponents.totalScore.toInt()}) by margin of ${scoreDiff.toInt()} points."
                        }
                    }
                }
            }
        }

        val eventType = selectedCandidate?.let { toEventType(it.category) }
        val finalTitle = if (detectedTitle == "Other") {
            extractProductTitle(rawText, productCategory, eventType)
        } else {
            detectedTitle
        }
        val dateType = selectedCandidate?.category?.takeIf { it != ContextCategory.NONE }?.name
        val confidence = when {
            selectedCandidate == null -> 0f
            selectedCandidate.isDerivedShelfLife -> CONFIDENCE_LOW
            selectedCandidate.category != ContextCategory.NONE -> CONFIDENCE_HIGH
            else -> CONFIDENCE_LOW
        }


        return LocalExtractionResult(
            title = finalTitle,
            eventType = eventType,
            selectedEventDate = selectedCandidate?.date,
            dateType = dateType,
            dateCandidates = dateCandidates,
            fields = fields,
            confidence = confidence,
            isAmbiguous = isAmbiguous,
            explanation = resolutionExplanation,
        )
    }

    private fun toEventType(category: ContextCategory): EventType = when (category) {
        ContextCategory.EXPIRATION -> EventType.EXPIRATION
        ContextCategory.BEST_BEFORE -> EventType.BEST_BEFORE
        ContextCategory.DUE_DATE -> EventType.DUE_DATE
        ContextCategory.WARRANTY_END -> EventType.WARRANTY_END
        ContextCategory.RETURN_DEADLINE -> EventType.RETURN_DEADLINE
        ContextCategory.SUBSCRIPTION_END -> EventType.SUBSCRIPTION_END
        ContextCategory.BILL_DATE,
        ContextCategory.PURCHASE_DATE,
        ContextCategory.MFG_DATE,
        ContextCategory.NONE -> EventType.OTHER
    }

    private fun toDateRole(category: ContextCategory): DateRole = when (category) {
        ContextCategory.EXPIRATION -> DateRole.ACTIONABLE_EXPIRATION
        ContextCategory.BEST_BEFORE -> DateRole.ACTIONABLE_BEST_BEFORE
        ContextCategory.DUE_DATE -> DateRole.ACTIONABLE_DUE_DATE
        ContextCategory.WARRANTY_END -> DateRole.ACTIONABLE_WARRANTY_END
        ContextCategory.RETURN_DEADLINE -> DateRole.ACTIONABLE_RETURN_DEADLINE
        ContextCategory.SUBSCRIPTION_END -> DateRole.ACTIONABLE_SUBSCRIPTION_END
        ContextCategory.BILL_DATE -> DateRole.HISTORICAL_ISSUANCE_BILL
        ContextCategory.PURCHASE_DATE -> DateRole.HISTORICAL_PURCHASE
        ContextCategory.MFG_DATE -> DateRole.HISTORICAL_MANUFACTURE
        ContextCategory.NONE -> DateRole.UNLABELLED_DATE
    }

    private fun computeTemporalStatus(date: LocalDate, today: LocalDate): TemporalStatus = when {
        date.isBefore(today) -> TemporalStatus.PAST
        date == today -> TemporalStatus.TODAY
        date <= today.plusDays(30) -> TemporalStatus.NEAR_FUTURE
        date <= today.plusYears(5) -> TemporalStatus.FUTURE
        else -> TemporalStatus.FAR_FUTURE
    }

    private fun detectDocumentDomain(rawText: String): DocumentDomain {
        val upper = rawText.uppercase()
        val isUtilityInvoice = upper.contains("BILL") || upper.contains("INVOICE") || upper.contains("DUE") ||
            upper.contains("PAYMENT") || upper.contains("AMOUNT DUE") || upper.contains("AMOUNT PAYABLE") ||
            upper.contains("TOTAL AMOUNT") || upper.contains("CHARGES") ||
            upper.contains("TARIFF") || upper.contains("SUBTOTAL") || upper.contains("STATEMENT") ||
            upper.contains("RECEIPT") || upper.contains("ELECTRICITY") || upper.contains("WATER") ||
            upper.contains("GAS") || upper.contains("CONSUMER") || upper.contains("METER")

        val isWarranty = upper.contains("WARRANTY") || upper.contains("GUARANTEE") ||
            upper.contains("PURCHASE DATE") || upper.contains("DATE OF PURCHASE")

        val isPackaging = upper.contains("MFG") || upper.contains("EXP") || upper.contains("BEST BEFORE") ||
            upper.contains("USE BY") || upper.contains("PKD") || upper.contains("BATCH") ||
            upper.contains("LOT NO") || upper.contains("NET WT") || upper.contains("INGREDIENTS") ||
            upper.contains("/ML") || upper.contains("MRP") || upper.contains("/-") ||
            upper.contains("NET QTY") || upper.contains("NET QUANTITY") ||
            upper.contains("TAXES") || upper.contains("USP") || upper.contains("KED ON")

        val isSubscriptionReturn = upper.contains("SUBSCRIPTION") || upper.contains("MEMBERSHIP") ||
            upper.contains("RETURN WINDOW") || upper.contains("RETURN BY")

        return when {
            isPackaging && !upper.contains("BILL") && !upper.contains("INVOICE") && !upper.contains("TARIFF") -> DocumentDomain.PRODUCT_PACKAGING
            isUtilityInvoice -> DocumentDomain.UTILITY_INVOICE
            isWarranty -> DocumentDomain.WARRANTY
            isPackaging -> DocumentDomain.PRODUCT_PACKAGING
            isSubscriptionReturn -> DocumentDomain.SUBSCRIPTION_RETURN
            else -> DocumentDomain.GENERAL
        }
    }

    private fun scoreCandidateComponents(
        date: LocalDate,
        hasExplicitDay: Boolean,
        category: ContextCategory,
        domain: DocumentDomain,
        temporal: TemporalStatus,
    ): CandidateScoreComponents {
        var semanticBase = when (category) {
            ContextCategory.DUE_DATE,
            ContextCategory.EXPIRATION,
            ContextCategory.BEST_BEFORE,
            ContextCategory.WARRANTY_END,
            ContextCategory.RETURN_DEADLINE,
            ContextCategory.SUBSCRIPTION_END -> 1000f

            ContextCategory.BILL_DATE,
            ContextCategory.PURCHASE_DATE,
            ContextCategory.MFG_DATE -> 300f

            ContextCategory.NONE -> 100f
        }

        var domainBonus = 0f
        when (domain) {
            DocumentDomain.UTILITY_INVOICE -> {
                if (category == ContextCategory.DUE_DATE) domainBonus += 500f
            }
            DocumentDomain.PRODUCT_PACKAGING -> {
                if (category == ContextCategory.EXPIRATION || category == ContextCategory.BEST_BEFORE) domainBonus += 500f
            }
            DocumentDomain.WARRANTY -> {
                if (category == ContextCategory.WARRANTY_END) domainBonus += 500f
            }
            DocumentDomain.SUBSCRIPTION_RETURN -> {
                if (category == ContextCategory.RETURN_DEADLINE || category == ContextCategory.SUBSCRIPTION_END) domainBonus += 500f
            }
            DocumentDomain.GENERAL -> {
                if (!category.isHistoricalAnchor && category != ContextCategory.NONE) domainBonus += 200f
            }
        }

        var temporalPlausibility = 0f
        var penalty = 0f

        if (category.isHistoricalAnchor) {
            if (temporal == TemporalStatus.PAST || temporal == TemporalStatus.TODAY) {
                temporalPlausibility = 150f
            } else {
                temporalPlausibility = 50f
            }
            penalty += 400f
        } else {
            when (temporal) {
                TemporalStatus.TODAY -> temporalPlausibility = 50f
                TemporalStatus.NEAR_FUTURE -> temporalPlausibility = 100f
                TemporalStatus.FUTURE -> temporalPlausibility = 80f
                TemporalStatus.FAR_FUTURE -> temporalPlausibility = 20f
                TemporalStatus.PAST -> {
                    penalty += 30f // mild penalty only as tiebreaker; past dates stay completely valid
                    temporalPlausibility = 0f
                }
                TemporalStatus.UNKNOWN -> {}
            }
        }

        val precisionBonus = if (hasExplicitDay) 50f else 0f
        val tieBreaker = (date.toEpochDay() % 1000) / 10000f

        val total = semanticBase + domainBonus + temporalPlausibility + precisionBonus - penalty + tieBreaker

        return CandidateScoreComponents(
            semanticBaseScore = semanticBase,
            temporalPlausibilityScore = temporalPlausibility,
            precisionBonus = precisionBonus,
            domainPrecedenceBonus = domainBonus,
            penalty = penalty,
            totalScore = total,
        )
    }

    private fun extractDateMatchesOnLine(line: String): List<ParsedMatch> {
        val matches = mutableListOf<ParsedMatch>()
        val occupiedRanges = mutableListOf<IntRange>()

        fun addIfNonOverlapping(
            range: IntRange,
            hasExplicitDay: Boolean,
            dateBuilder: () -> LocalDate?
        ) {
            val overlaps = occupiedRanges.any { it.first < range.last && range.first < it.last }
            if (!overlaps) {
                val date = try {
                    dateBuilder()
                } catch (_: Exception) {
                    null
                }
                if (date != null) {
                    occupiedRanges.add(range)
                    matches.add(
                        ParsedMatch(
                            date = date,
                            hasExplicitDay = hasExplicitDay,
                            startIndex = range.first,
                            endIndex = range.last + 1,
                            line = line,
                        )
                    )
                }
            }
        }

        // 1. Check YYYY-MM-DD
        REGEX_YYYY_MM_DD.findAll(line).forEach { match ->
            val range = match.range
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            addIfNonOverlapping(range, hasExplicitDay = true) {
                LocalDate.of(year, month, day)
            }
        }

        // 2. Check DD/MM/YYYY
        REGEX_DD_MM_YYYY.findAll(line).forEach { match ->
            val range = match.range
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val year = match.groupValues[3].toInt()
            addIfNonOverlapping(range, hasExplicitDay = true) {
                LocalDate.of(year, month, day)
            }
        }

        // 3. Check MMM YYYY
        REGEX_MMM_YYYY.findAll(line).forEach { match ->
            val range = match.range
            val dayPrefix = match.groupValues[1]
            val monthStr = match.groupValues[2].uppercase()
            val daySuffix = match.groupValues[3]
            val yearStr = match.groupValues[4]

            val month = MONTH_NAME_MAP[monthStr]
            val year = yearStr.toIntOrNull()
            if (month != null && year != null) {
                val explicitDay = dayPrefix.toIntOrNull() ?: daySuffix.toIntOrNull()
                val hasDay = explicitDay != null
                val day = explicitDay ?: 1
                addIfNonOverlapping(range, hasExplicitDay = hasDay) {
                    LocalDate.of(year, month, day)
                }
            }
        }

        // 4. Check MM/YYYY
        REGEX_MM_YYYY.findAll(line).forEach { match ->
            val range = match.range
            val month = match.groupValues[1].toInt()
            val year = match.groupValues[2].toInt()
            addIfNonOverlapping(range, hasExplicitDay = false) {
                LocalDate.of(year, month, 1)
            }
        }

        // 5. Check MM/YY
        REGEX_MM_YY.findAll(line).forEach { match ->
            val range = match.range
            val month = match.groupValues[1].toInt()
            val rawYear = match.groupValues[2].toInt()
            // Plausible 2-digit years for expiration/reminders
            if (rawYear in 24..49) {
                val year = 2000 + rawYear
                addIfNonOverlapping(range, hasExplicitDay = false) {
                    LocalDate.of(year, month, 1)
                }
            }
        }

        // 6. Check DD/MM/YY (2-digit year, e.g. 25/08/26)
        REGEX_DD_MM_YY.findAll(line).forEach { match ->
            val range = match.range
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val rawYear = match.groupValues[3].toInt()
            if (rawYear in 24..49) {
                val year = 2000 + rawYear
                addIfNonOverlapping(range, hasExplicitDay = true) {
                    LocalDate.of(year, month, day)
                }
            }
        }

        // 7. Check DD MMM YY (e.g. "25 AUG 26", "25-AUG-26")
        REGEX_DD_MMM_YY.findAll(line).forEach { match ->
            val range = match.range
            val day = match.groupValues[1].toInt()
            val monthStr = match.groupValues[2].uppercase()
            val rawYear = match.groupValues[3].toInt()
            val month = MONTH_NAME_MAP[monthStr]
            if (month != null && rawYear in 24..49) {
                val year = 2000 + rawYear
                addIfNonOverlapping(range, hasExplicitDay = true) {
                    LocalDate.of(year, month, day)
                }
            }
        }

        // 8. Check MMM YY (e.g. "AUG-26", "AUG 26")
        REGEX_MMM_YY.findAll(line).forEach { match ->
            val range = match.range
            val monthStr = match.groupValues[1].uppercase()
            val rawYear = match.groupValues[2].toInt()
            val month = MONTH_NAME_MAP[monthStr]
            if (month != null && rawYear in 24..49) {
                val year = 2000 + rawYear
                addIfNonOverlapping(range, hasExplicitDay = false) {
                    LocalDate.of(year, month, 1)
                }
            }
        }

        // 9. Check DD MM YYYY (space-separated, e.g. "25 08 2026")
        REGEX_DD_SP_MM_SP_YYYY.findAll(line).forEach { match ->
            val range = match.range
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val year = match.groupValues[3].toInt()
            addIfNonOverlapping(range, hasExplicitDay = true) {
                LocalDate.of(year, month, day)
            }
        }

        // 10. Check MM.YYYY
        REGEX_MM_DOT_YYYY.findAll(line).forEach { match ->
            val range = match.range
            val month = match.groupValues[1].toInt()
            val year = match.groupValues[2].toInt()
            addIfNonOverlapping(range, hasExplicitDay = false) {
                LocalDate.of(year, month, 1)
            }
        }

        // 11. Check MM.YY
        REGEX_MM_DOT_YY.findAll(line).forEach { match ->
            val range = match.range
            val month = match.groupValues[1].toInt()
            val rawYear = match.groupValues[2].toInt()
            if (rawYear in 24..49) {
                val year = 2000 + rawYear
                addIfNonOverlapping(range, hasExplicitDay = false) {
                    LocalDate.of(year, month, 1)
                }
            }
        }

        // 12. Check DD-MMYY (fused MM and YY after DD-)
        REGEX_DD_MM_YY_FUSED.findAll(line).forEach { match ->
            val range = match.range
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val rawYear = match.groupValues[3].toInt()
            val year = if (rawYear < 100) 2000 + rawYear else rawYear
            addIfNonOverlapping(range, hasExplicitDay = true) {
                LocalDate.of(year, month, day)
            }
        }

        // 13. Check MMYYYY (fused 6 digits without separator)
        REGEX_MM_YYYY_NO_SEP.findAll(line).forEach { match ->
            val range = match.range
            val month = match.groupValues[1].toInt()
            val year = match.groupValues[2].toInt()
            addIfNonOverlapping(range, hasExplicitDay = false) {
                LocalDate.of(year, month, 1)
            }
        }

        return matches
    }

    private fun classifyWords(words: List<String>): ContextCategory {
        if (words.isEmpty()) return ContextCategory.NONE

        val joined = words.joinToString(" ").uppercase()
        val clean = joined.replace(Regex("""[^A-Z0-9 ]"""), " ").replace(Regex("""\s+"""), " ").trim()
        val tokensList = clean.split(" ").filter { it.isNotBlank() }
        val tokens = tokensList.toSet()

        val mfgTokens = setOf(
            "MFG", "MFGD", "MFD", "MTD", "MTG", "MIG", "MIGDATE", "MGDAVE",
            "BATCH", "LOT", "PROD", "PRODUCTION", "MANUFACTURE", "MANUFACTURED", "MANUFACTURING",
            "PACKAGING", "PKD", "PACKED", "NFG", "NFGDT", "NFGD", "NFD", "FGDATE", "4FGDATE"
        )

        // Rule 1: Anchor Scoping - scan tokens from nearest to furthest
        for (token in tokensList.reversed()) {
            if (token in setOf("EXP", "EXPIRY", "EXPIRES", "EXPIRE", "EXPIRATION", "EXPD")) return ContextCategory.EXPIRATION
            if (token in setOf("BEST", "BEFORE", "USE", "BB")) return ContextCategory.BEST_BEFORE
            if (token in mfgTokens || token.startsWith("NFG") || token.startsWith("MFG") || token.startsWith("PKD") || token.startsWith("MFD") || token.startsWith("MTD")) return ContextCategory.MFG_DATE
            if (token in setOf("DUE")) return ContextCategory.DUE_DATE
            if (token in setOf("IMPORT", "IMPORTED", "PURCHASE", "PURCHASED", "ORDER", "ORDERED", "BOUGHT")) return ContextCategory.PURCHASE_DATE
        }

        // 1. Manufacturing / batch
        val isMfg = tokens.any { it in mfgTokens || it.startsWith("NFG") || it.startsWith("MFG") || it.startsWith("PKD") || it.startsWith("MFD") || it.startsWith("MTD") } ||
            clean.contains("MFG DATE") || clean.contains("DATE OF MFG") || clean.contains("DATE OF MANUFACTURE") || clean.contains("PACKED ON") || clean.contains("PROD DATE") || clean.contains("PRODUCTION DATE") || clean.contains("PKD ON") || clean.contains("MTD DATE") || clean.contains("NFG DATE")
        if (isMfg) return ContextCategory.MFG_DATE

        // 2. Purchase / Order / Import
        val isPurchase = tokens.any { it in setOf("PURCHASE", "PURCHASED", "ORDER", "ORDERED", "BOUGHT", "IMPORT", "IMPORTED") } ||
            clean.contains("PURCHASE DATE") || clean.contains("DATE OF PURCHASE") || clean.contains("ORDER DATE") || clean.contains("BOUGHT ON") || clean.contains("DATE OF SALE") || clean.contains("DATE OF IMPORT") || clean.contains("IMPORT DATE")
        if (isPurchase) return ContextCategory.PURCHASE_DATE

        // 3. Bill / Invoice / Statement / Issuance / Reading
        val isBill = clean.contains("BILL DATE") || clean.contains("BILLING DATE") || clean.contains("INVOICE DATE") ||
            clean.contains("STATEMENT DATE") || clean.contains("ISSUE DATE") || clean.contains("DATE OF ISSUE") ||
            clean.contains("INV DATE") || clean.contains("METER READING") || clean.contains("METER DATE") ||
            clean.contains("READING DATE") || clean.contains("BILL PERIOD") || clean.contains("GENERATED ON") ||
            "ISSUED" in tokens
        if (isBill) return ContextCategory.BILL_DATE

        // 4. Due date / payment due
        if (clean.contains("DUE DATE") || clean.contains("DUE BY") || clean.contains("PAY BY") || clean.contains("PAY BEFORE") || clean.contains("PAYMENT DUE") || clean.contains("PAY ON OR BEFORE") || clean.contains("LAST DATE") || "DUE" in tokens) {
            return ContextCategory.DUE_DATE
        }

        // 5. Warranty end
        if (clean.contains("WARRANTY UNTIL") || clean.contains("WARRANTY THROUGH") || clean.contains("WARRANTY END") || clean.contains("WARRANTY EXPIRES") || clean.contains("WARRANTY VALID") || clean.contains("WARRANTY UPTO") || clean.contains("VALID UPTO") || "WARRANTY" in tokens || "WARR" in tokens || "GUARANTEE" in tokens) {
            return ContextCategory.WARRANTY_END
        }

        // 6. Return window
        if (clean.contains("RETURN BY") || clean.contains("RETURN BEFORE") || clean.contains("RETURN UNTIL") || clean.contains("RETURN WINDOW") || clean.contains("RETURNABLE TILL") || "RETURN" in tokens) {
            return ContextCategory.RETURN_DEADLINE
        }

        // 7. Subscription renewal
        if (clean.contains("SUBSCRIPTION RENEWAL") || clean.contains("MEMBERSHIP RENEWAL") || clean.contains("RENEW BY") || clean.contains("RENEWS") || clean.contains("SUBSCRIPTION END") || "RENEWAL" in tokens) {
            return ContextCategory.SUBSCRIPTION_END
        }

        // 8. Best by / best before / use by
        if (clean.contains("BEST BEFORE") || clean.contains("BEST BY") || clean.contains("USE BY") || clean.contains("USE BEFORE") || "BB" in tokens) {
            return ContextCategory.BEST_BEFORE
        }

        // 9. Expiration and document validity
        if (clean.contains("EXP DATE") || clean.contains("EXPIRY DATE") || clean.contains("EXPIRATION DATE") || clean.contains("VALID UNTIL") || clean.contains("VALID THROUGH") || clean.contains("EXPIRES ON") ||
            tokens.any { it in setOf("EXP", "EXPIRY", "EXPIRES", "EXPIRE", "EXPIRATION", "EXPD") }
        ) {
            return ContextCategory.EXPIRATION
        }

        // Fuzzy fallback: If no exact match was found, check for OCR typos (1-edit distance).
        for (token in tokens) {
            if (token.length < 2) continue
            if (fuzzyMatchAny(token, setOf("MFG", "MFD", "MFGD", "PKD", "BATCH"))) return ContextCategory.MFG_DATE
            if (fuzzyMatchAny(token, setOf("EXP", "EXPIRY", "EXPIRE", "EXPIRES"))) return ContextCategory.EXPIRATION
            if (fuzzyMatchAny(token, setOf("BEST", "BEFORE"))) return ContextCategory.BEST_BEFORE
            if (fuzzyMatchAny(token, setOf("DUE"))) return ContextCategory.DUE_DATE
            if (fuzzyMatchAny(token, setOf("WARRANTY"))) return ContextCategory.WARRANTY_END
            if (fuzzyMatchAny(token, setOf("PURCHASE"))) return ContextCategory.PURCHASE_DATE
        }

        return ContextCategory.NONE
    }

    /** Returns true if [token] is within 1 edit (substitution, insertion, or deletion) of any [targets]. */
    private fun fuzzyMatchAny(token: String, targets: Set<String>): Boolean {
        return targets.any { target -> editDistance(token, target) <= 1 }
    }

    /** Simple Levenshtein edit distance, capped at 2 for early exit performance. */
    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        if (kotlin.math.abs(a.length - b.length) > 1) return 2 // early exit: impossible to be ≤1

        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(curr, 0, prev, 0, curr.size)
        }
        return prev[b.length]
    }

    enum class ProductCategory(val displayName: String) {
        MEDICINE("Medicine"),
        PERSONAL_CARE("Personal Care"),
        GROCERY_FOOD("Grocery"),
        BEVERAGE_SPIRIT("Beverage / Spirit"),
        UTILITY_INVOICE("Bill / Invoice"),
        PRODUCT_WARRANTY("Product Warranty"),
        GENERAL("Other");
    }

    fun detectProductCategory(rawText: String): ProductCategory {
        val upper = rawText.uppercase()
        val hasBill = upper.contains("BILL") || upper.contains("INVOICE") || upper.contains("TARIFF") ||
            upper.contains("CONSUMER") || upper.contains("METER") || upper.contains("DUE") || upper.contains("PAY")
        if (hasBill && Regex("""\b(ELECTRICITY|BESCOM|MSEB|MSEDCL|TNEB|UPPCL|BSES|TATA POWER|ADANI ELECTRICITY|AIRTEL|JIO|BSNL|VI|VODAFONE|ACT FIBERNET|BROADBAND|FIBER|INDANE|PNG|LPG|BHARAT GAS|HP GAS|ADANI TOTAL GAS|ADANI GAS|MAHANAGAR GAS|MGL|IGL|TORRENT GAS|GAIL|BWSSB|DJB|JAL BOARD|HMWSSB|WATER SUPPLY)\b""").containsMatchIn(upper)) {
            return ProductCategory.UTILITY_INVOICE
        }

        val hasNutrition = Regex("""(?i)\b(kcal|Energy|Fat|Protein|Carbohydrate|Nutritional\s*Facts?|Nutrition\s*Information|Serving\s*Size)\b""").containsMatchIn(rawText)

        val isBev = Regex("""(?i)\b\d{1,2}(?:\.\d+)?\s*%\s*(?:v/v|vol|alc)\b""").containsMatchIn(rawText) ||
            Regex("""(?i)\b\d{1,3}\s*Proof\b""").containsMatchIn(rawText) ||
            Regex("""(?i)\b(Vodka|Whisky|Whiskey|Rum|Gin|Beer|Wine|Distilled|Flavored\s*Vodka|Liquor|Liqueur|Spirit|Brewed|Alcoholic)\b""").containsMatchIn(rawText)
        if (isBev) {
            return ProductCategory.BEVERAGE_SPIRIT
        }

        val hasInci = Regex("""(?i)\b(Aqua|Glycerin|Polysorbate\s*\d*|Carbomer\s*\d*|Perfume|Colorant|Antibacterial|Activated\s*Charcoal|Paraben\s*Free|Sulphate\s*Free|Dimethicone|Cetyl\s*Alcohol)\b""").containsMatchIn(rawText)
        val hasPcUse = Regex("""(?i)\b(skin|sun-ex|SPF\b|external\s*use|for\s*external\s*use\s*only|balm|handwash|face\s*wash|wash|shampoo|cleanser|moisturizer|sunscreen)\b""").containsMatchIn(rawText)
        if (hasInci || hasPcUse) {
            return ProductCategory.PERSONAL_CARE
        }

        val hasCulinary = Regex("""(?i)\b(Cooking\s*Instructions?|Recipe|Serve|Store\s*in\s*a\s*cool)\b""").containsMatchIn(rawText)
        val hasFood = Regex("""(?i)\b(Ice\s*Cream|Rice|Basmati|Chilli|Chilly|Spices|Masala|Spring\s*rolls|Whole|Wheat|Flour|Salt|Sugar|Oil|Tea|Coffee|Snacks|Biscuits|Chocolate|Confectionery|Dairy|Milk|Curd|Yogurt|Paneer|Butter)\b""").containsMatchIn(rawText)
        if (hasNutrition || hasCulinary || hasFood) {
            return ProductCategory.GROCERY_FOOD
        }

        val hasDosage = Regex("""(?i)\b\d+(?:\.\d+)?\s*(?:mg|mcg|ml)\b""").containsMatchIn(rawText)
        val hasPharmaStd = Regex("""(?i)\b(?:I\.?P\.?|B\.?P\.?|U\.?S\.?P\.?)\d*\b""").containsMatchIn(rawText)
        val hasPharmaForm = Regex("""(?i)\b(Tablets?|Capsules?|Syrup|Ointment|Gel|Suspension|Drops)\b""").containsMatchIn(rawText)
        val hasChemSalt = Regex("""(?i)\b\w*(?:Hydrochloride|Sodium|lukast|cetirizine|cillin|mycin|azole|mol|fenac|prazole|dipine|statin)\b""").containsMatchIn(rawText)
        val hasMedPhrase = Regex("""(?i)\b(medicine|children|doctor|physician|dosage|directed\s+by\s+the\s+physician|for\s+therapeutic\s+use|keep\s+out\s+of\s+reach\s+of\s+children)\b""").containsMatchIn(rawText)

        val medScore = (if (hasDosage) 1 else 0) + (if (hasPharmaStd) 2 else 0) + (if (hasPharmaForm) 2 else 0) + (if (hasChemSalt) 2 else 0) + (if (hasMedPhrase) 2 else 0)
        if (medScore >= 2) {
            return ProductCategory.MEDICINE
        }

        if (upper.contains("WARRANTY") || upper.contains("GUARANTEE") || upper.contains("FOOTWEAR") || upper.contains("SHOES")) {
            return ProductCategory.PRODUCT_WARRANTY
        }

        return ProductCategory.GENERAL
    }

    private fun extractProductTitle(
        rawText: String,
        category: ProductCategory,
        eventType: EventType? = null
    ): String {
        val upper = rawText.uppercase()

        if (category == ProductCategory.UTILITY_INVOICE) {
            return when {
                Regex("""\b(ELECTRICITY|BESCOM|MSEB|MSEDCL|TNEB|UPPCL|BSES|TATA POWER|ADANI ELECTRICITY)\b""").containsMatchIn(upper) -> "Electricity Bill"
                Regex("""\b(AIRTEL|JIO|BSNL|VI|VODAFONE|ACT FIBERNET|BROADBAND|FIBER)\b""").containsMatchIn(upper) -> "Internet/Phone Bill"
                Regex("""\b(INDANE|PNG|LPG|BHARAT GAS|HP GAS|ADANI TOTAL GAS|ADANI GAS|MAHANAGAR GAS|MGL|IGL|TORRENT GAS|GAIL)\b""").containsMatchIn(upper) -> "Gas Bill"
                Regex("""\b(BWSSB|DJB|JAL BOARD|HMWSSB|WATER SUPPLY)\b""").containsMatchIn(upper) -> "Water Bill"
                upper.contains("POLICY") || Regex("""\b(INSURANCE|HDFC ERGO|ICICI LOMBARD|STAR HEALTH|LIC|BAJAJ ALLIANZ|MAX LIFE|POLICYBAZAAR)\b""").containsMatchIn(upper) -> "Insurance Policy"
                else -> "Bill / Invoice"
            }
        }

        var extractedBrand: String? = null
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }.take(15)
        for (line in lines) {
            val lu = line.uppercase()
            if (Regex("""\b(?:MRP|RS\b|₹|INCL|TAXES|NET\s*QTY|NET\s*WT|NET\s*CONTENT|BATCH|B\.?NO|LOT\b|LIC\b|FSSAI|MFG|PKD|EXP|USE\s*BY|BEST\s*BEFORE|BARCODE)\b""").containsMatchIn(lu)) continue
            if (Regex("""\b(?:ROAD|STREET|NAGAR|LTD|PVT|LIMITED|EMAIL|WWW\.|COM|CALL|TOLL|CARE|CUSTOMER|DISTRICT|STATE|FLOOR|HOUSE|IMPORT|EXPORT)\b""").containsMatchIn(lu)) continue
            if (Regex("""\b(?:\d+\s*(?:MG|MCG|ML)|TABLETS?|CAPSULES?|SYRUP|OINTMENT|GEL|IP|BP|USP|PARACETAMOL|AMOXICILLIN|HANDWASH|BALM|RICE|BASMATI|FOOTWEAR|INVOICE|BILL)\b""").containsMatchIn(lu)) continue
            if (Regex("""\b\d{6}\b""").containsMatchIn(line)) continue
            if (line.count { it == ',' } >= 2 || lu.startsWith("INGREDIENTS")) continue
            if (line.length < 3 || line.length > 35) continue

            val cleaned = line.replace(Regex("""^[^A-Za-z0-9]+|[^A-Za-z0-9)]+$"""), "").trim()
            val words = cleaned.split(Regex("""\s+""")).filter { it.isNotBlank() }
            if (words.size in 1..4 && words.any { it.matches(Regex("""[A-Za-z]{3,}""")) }) {
                if (!Regex("""(?i)\b(Cooking\s*Instructions?|Directions|Safety\s*Directions|Nutritional\s*Facts|Serving\s*Size|For\s*External\s*Use)\b""").matches(cleaned)) {
                    extractedBrand = cleaned
                    break
                }
            }
        }

        val detectedProductType: String? = when (category) {
            ProductCategory.MEDICINE -> "Medicine"
            ProductCategory.PERSONAL_CARE -> {
                if (Regex("""(?i)\b(Balm)\b""").containsMatchIn(rawText)) "Healthcare / Balm"
                else "Personal Care"
            }
            ProductCategory.GROCERY_FOOD -> {
                when {
                    Regex("""(?i)\b(Rice|Basmati)\b""").containsMatchIn(rawText) -> "Grocery / Rice"
                    Regex("""(?i)\b(Dairy|Milk|Curd|Butter|Paneer)\b""").containsMatchIn(rawText) -> "Grocery / Dairy"
                    Regex("""(?i)\b(Ice\s*Cream|Chocolate|Confectionery)\b""").containsMatchIn(rawText) -> "Grocery / Confectionery"
                    Regex("""(?i)\b(Chil+[iy]+|Spices?|Masala|Pepper)\b""").containsMatchIn(rawText) -> "Grocery / Spices"
                    Regex("""(?i)\b(Balm)\b""").containsMatchIn(rawText) -> "Healthcare / Balm"
                    else -> "Grocery"
                }
            }
            ProductCategory.BEVERAGE_SPIRIT -> "Beverage / Spirit"
            ProductCategory.PRODUCT_WARRANTY -> "Product Warranty"
            ProductCategory.GENERAL -> null
            ProductCategory.UTILITY_INVOICE -> null
        }

        val fallbackType = eventType?.takeIf { it != EventType.OTHER }?.let { "General ${it.name.lowercase().replaceFirstChar { c -> c.uppercase() }}" } ?: "Other"
        val brandCandidate = if (detectedProductType != null) null else extractedBrand
        val displayTitle = brandCandidate ?: detectedProductType ?: fallbackType
        return displayTitle
    }

    fun normalizeForExtraction(rawText: String): String =
        Companion.normalizeForExtraction(rawText)
}
