package app.memora.platform.ocr

/**
 * Intelligent text normalization specifically tuned for PaddleOCR / RapidOCR outputs.
 *
 * Solves real-world OCR recognition anomalies on consumer packaging, receipts, and medicine:
 * 1. Full-Width to Half-Width ASCII Conversion: Converts CJK full-width characters (e.g. ‘：’, ‘／’, ‘－’, ‘０-９’)
 *    to their exact ASCII equivalents.
 * 2. Dot-Matrix Character Disambiguation: Corrects numeric date digit confusions in dot-matrix/inkjet stamps:
 *    - 'O' or 'o' inside digits, before/after date separators, or in years (e.g. "2O26" -> "2026", "3O/12" -> "30/12").
 *    - Lowercase 'l' or uppercase 'I' inside digits or before/after date separators (e.g. "15/l0/2026" -> "15/10/2026").
 * 3. Whitespace & Punctuation Cleanup: Standardizes quotes, dashes, and spacing while preserving date separators.
 */
object RapidOcrTextNormalizer {

    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""

        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                // Full-width ASCII variants (U+FF01 to U+FF5E) -> Half-width standard ASCII (U+0021 to U+007E)
                in '\uFF01'..'\uFF5E' -> sb.append((ch.code - 0xFEE0).toChar())
                // Full-width space (U+3000) -> standard space
                '\u3000' -> sb.append(' ')
                // Quotations
                '“', '”' -> sb.append('"')
                '‘', '’' -> sb.append('\'')
                // Dashes & hyphens
                '—', '–', '‒', '―' -> sb.append('-')
                else -> sb.append(ch)
            }
        }

        var text = sb.toString().trim().replace(Regex("[ \\t]+"), " ")

        // Fix common OCR dot-matrix digit confusions in date-like contexts:
        // 1. Replace 'O' or 'o' between digits (e.g. "1O0" -> "100")
        text = text.replace(Regex("""(?<=\d)[Oo](?=\d)"""), "0")
        // 2. Replace 'O' or 'o' between digit and date separator (e.g. "3O/" -> "30/", "1O-" -> "10-")
        text = text.replace(Regex("""(?<=\d)[Oo](?=[/.-])"""), "0")
        // 3. Replace 'O' or 'o' between date separator and digit (e.g. "/O5/" -> "/05/")
        text = text.replace(Regex("""(?<=[/.-])[Oo](?=\d)"""), "0")
        // 4. Replace 'O' or 'o' between date separator and date separator (e.g. "/O/" -> "/0/")
        text = text.replace(Regex("""(?<=[/.-])[Oo](?=[/.-])"""), "0")
        // 5. Replace 'O' or 'o' in 4-digit years: 2020-2039
        text = text.replace(Regex("""\b2[Oo]2([0-9])\b"""), "202$1")
        text = text.replace(Regex("""\b2[Oo]3([0-9])\b"""), "203$1")
        text = text.replace(Regex("""\b202[Oo]\b"""), "2020")
        text = text.replace(Regex("""\b203[Oo]\b"""), "2030")
        // 6. Replace 'O' or 'o' in 2-digit year after separator: e.g. "/2O\b" -> "/20"
        text = text.replace(Regex("""(?<=[/.-])2[Oo]\b"""), "20")
        // 6b. Replace leading 'O' or 'o' before a digit and date separator: e.g. "O9/08/2024" -> "09/08/2024"
        text = text.replace(Regex("""\b[Oo](?=[0-9][/.-])"""), "0")

        // 7. Replace 'l' or 'I' between digits: e.g. "15/l0/2026" -> "15/10/2026"
        text = text.replace(Regex("""(?<=\d)[lI](?=\d)"""), "1")
        // 8. Replace leading 'l' or 'I' before a digit and date separator: e.g. "l2/2026" -> "12/2026"
        text = text.replace(Regex("""\b[lI](?=[0-9][/.-])"""), "1")
        // 9. Replace 'l' or 'I' between date separator and digit: e.g. "/l0/" -> "/10/", "/I1/" -> "/11/"
        text = text.replace(Regex("""(?<=[/.-])[lI](?=\d)"""), "1")
        // 10. Replace 'l' or 'I' between digit and date separator: e.g. "3l/" -> "31/"
        text = text.replace(Regex("""(?<=\d)[lI](?=[/.-])"""), "1")

        // 11. Fix space separated colon after anchors like "EXP : " -> "EXP: "
        text = text.replace(Regex("""\b(EXP|MFG|MFD|PKD|BB|USE BY)\s*:\s*""", RegexOption.IGNORE_CASE), "$1: ")

        return text.trim()
    }
}
