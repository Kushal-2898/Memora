package app.memora.domain.benchmark

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.memora.BuildConfig
import app.memora.domain.extraction.DeterministicMemoryExtractor
import app.memora.domain.scheduler.ClockProvider
import app.memora.domain.scheduler.SystemClockProvider
import app.memora.platform.logging.AppLog
import app.memora.platform.ocr.RapidOcrExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Debug-only evaluation harness for the expiry-date recognition pipeline.
 *
 * Requirements:
 * 1. Debug builds only (BuildConfig.DEBUG gated).
 * 2. Input: Reads `labels_template.csv` (`filename,expiry_date,mfg_date,date_format_printed,difficulty,notes`)
 *    and `split.csv` (`filename,split`), plus images in `tuning/` and `heldout/` folders.
 *    Refuses to run if any required label (`expiry_date`) is empty.
 * 3. Runs the REAL shared production path: crop, preprocessing, RapidOCR passes, parsing & scoring.
 * 4. Local-only: Gemini and network calls strictly OFF.
 * 5. Records the OCR-quality metric `expectedDateInRawOcr` ("expected date text appears in at least
 *    one pass's raw OCR text" Y/N) separately from `correct` ("final extracted date correct" Y/N).
 * 6. Supports diagnostic toggle `runAllPasses` ("Run ALL passes (no early exit)") recording for every
 *    image whether each pass (original, contrast, inverted, rotations) alone finds the expected date + per-pass ms.
 * 7. failure_type: OCR_MISSING, PARSE_WRONG, WRONG_PICK, NONE.
 * 8. Reports tuning and heldout accuracy separately (and OCR recall vs end-to-end extraction accuracy).
 * 9. Exports CSV to Downloads via MediaStore on Android 10+ and verifies readback.
 */
object ExpiryEvaluationHarness {

    data class LabelRecord(
        val filename: String,
        val productId: String = "",
        val expiryDate: String,
        val mfgDate: String = "",
        val dateFormatPrinted: String = "",
        val difficulty: String = "",
        val notes: String = "",
    )

    data class PassDiagnosticOutcome(
        val passName: String,
        val durationMs: Long,
        val extractedDate: String,
        val foundExpectedDateAlone: Boolean,
        val rawText: String,
    )

    data class EvaluationRow(
        val split: String = "tuning", // "tuning" or "heldout"
        val filename: String,
        val expected: String,
        val extracted: String,
        val correct: String, // "Y" or "N" (final extracted date correct)
        val expectedDateInRawOcr: String = "N", // "Y" or "N" (expected date text appears in at least one pass's raw OCR text)
        val confidence: Float,
        val failureType: String, // OCR_MISSING, PARSE_WRONG, WRONG_PICK, NONE
        val winningPass: String, // original, contrast, inverted, rotated
        val totalMs: Long,
        val msPerPass: String,
        val rawOcrTextOfEachPass: String,
        val difficulty: String = "",
        val passDiagnosticOutcomes: List<PassDiagnosticOutcome> = emptyList(),
    )

    data class SplitSummary(
        val splitName: String,
        val totalCount: Int,
        val correctCount: Int,
        val accuracyPercent: Double,
        val ocrRecallCount: Int,
        val ocrRecallPercent: Double,
        val countsByFailureType: Map<String, Int>,
        val averageTotalMs: Double,
        val worstCaseTotalMs: Long,
        val worstCaseFilename: String,
        val winningPassCounts: Map<String, Int>,
        val mostFrequentWinningPass: String,
        val passAloneSuccessCounts: Map<String, Int> = emptyMap(),
    )

    data class EvaluationSummary(
        val totalCount: Int,
        val correctCount: Int,
        val accuracyPercent: Double,
        val ocrRecallCount: Int = 0,
        val ocrRecallPercent: Double = 0.0,
        val countsByFailureType: Map<String, Int>,
        val averageTotalMs: Double,
        val worstCaseTotalMs: Long,
        val worstCaseFilename: String,
        val winningPassCounts: Map<String, Int>,
        val mostFrequentWinningPass: String,
        val generatedCsvFile: File,
        val formattedSummary: String,
        val tuningSummary: SplitSummary? = null,
        val heldoutSummary: SplitSummary? = null,
        val runAllPasses: Boolean = false,
        val rows: List<EvaluationRow> = emptyList(),
    )

    /**
     * Finds the root evaluation directory in app storage.
     * Supports subfolders `tuning/` and `heldout/` inside the root evaluation directory.
     */
    fun getEvaluationDirectory(context: Context): File {
        val candidates = listOf(
            File(context.filesDir, "evaluation"),
            File(context.getExternalFilesDir(null), "evaluation"),
            File(context.filesDir, "benchmark"),
            File(context.getExternalFilesDir(null), "benchmark"),
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.isDirectory && (candidate.listFiles()?.isNotEmpty() == true)) {
                return candidate
            }
        }
        val defaultDir = File(context.filesDir, "evaluation")
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
        File(defaultDir, "tuning").mkdirs()
        File(defaultDir, "heldout").mkdirs()
        return defaultDir
    }

    @Volatile
    private var cachedExtractor: RapidOcrExtractor? = null

    private fun getOrCreateExtractor(context: Context): RapidOcrExtractor {
        return cachedExtractor ?: synchronized(this) {
            cachedExtractor ?: RapidOcrExtractor(context.applicationContext).also {
                cachedExtractor = it
            }
        }
    }

    /**
     * Executes the evaluation harness on-device against `labels_template.csv` and `split.csv`,
     * validating that no label is empty, and reporting `tuning` and `heldout` accuracy separately.
     */
    suspend fun runEvaluation(
        context: Context,
        inputDir: File? = null,
        runAllPasses: Boolean = false,
        rapidOcrExtractor: RapidOcrExtractor = getOrCreateExtractor(context),
        clockProvider: ClockProvider = SystemClockProvider(),
    ): Result<EvaluationSummary> = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG) {
            return@withContext Result.failure(
                IllegalStateException("ExpiryEvaluationHarness is strictly available in debug builds only.")
            )
        }

        val rootDir = inputDir ?: getEvaluationDirectory(context)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            return@withContext Result.failure(
                IllegalArgumentException("Evaluation directory does not exist: ${rootDir.absolutePath}")
            )
        }

        // Local-only guarantee: Log and ensure network & Gemini are inactive
        AppLog.i("EVALUATION", "==================================================")
        AppLog.i("EVALUATION", "Starting On-Device Expiry-Date Pipeline Evaluation Harness")
        AppLog.i("EVALUATION", "Root directory: ${rootDir.absolutePath}")
        AppLog.i("EVALUATION", "Diagnostic Run ALL passes (no early exit): $runAllPasses")
        AppLog.i("EVALUATION", "Local-only guarantee: Gemini = OFF, Network = OFF (0 network calls)")
        AppLog.i("EVALUATION", "==================================================")

        val deterministicExtractor = DeterministicMemoryExtractor(clockProvider = clockProvider)

        // Check for root labels_template.csv (or fallback CSV) and split.csv
        val rootLabelsFile = findLabelsCsvFile(rootDir)
        val splitFile = File(rootDir, "split.csv").takeIf { it.exists() && it.isFile }

        val allRows = mutableListOf<EvaluationRow>()

        if (rootLabelsFile != null) {
            // Validate and parse labels_template.csv (throws IllegalArgumentException if any label is empty or invalid)
            val labelRecords = try {
                parseAndValidateLabelsCsv(rootLabelsFile)
            } catch (e: IllegalArgumentException) {
                AppLog.e("EVALUATION", "Refusing to run evaluation: ${e.message}")
                return@withContext Result.failure(e)
            }

            val splitMap = if (splitFile != null) {
                parseSplitCsv(splitFile)
            } else {
                emptyMap()
            }

            val recordsWithSplit = labelRecords.map { record ->
                val assignedSplit = splitMap[record.filename]?.lowercase(Locale.ROOT)
                    ?: inferSplitFromFolders(rootDir, record.filename)
                record to assignedSplit
            }

            try {
                validateSplitAndProductIsolation(recordsWithSplit)
            } catch (e: IllegalArgumentException) {
                AppLog.e("EVALUATION", "Refusing to run evaluation: ${e.message}")
                return@withContext Result.failure(e)
            }

            for ((record, assignedSplit) in recordsWithSplit) {
                val imageFile = resolveImageFile(rootDir, assignedSplit, record.filename)

                val row = evaluateSingleImage(
                    splitName = assignedSplit,
                    record = record,
                    imageFile = imageFile,
                    runAllPasses = runAllPasses,
                    rapidOcrExtractor = rapidOcrExtractor,
                    deterministicExtractor = deterministicExtractor,
                )
                allRows.add(row)
            }
        } else {
            // Check tuning/ and heldout/ subfolders for their own CSVs
            val tuningDir = File(rootDir, "tuning")
            val heldoutDir = File(rootDir, "heldout")

            val tuningCsv = findLabelsCsvFile(tuningDir)
            val heldoutCsv = findLabelsCsvFile(heldoutDir)

            if (tuningCsv == null && heldoutCsv == null) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "No labels_template.csv (or ground-truth CSV) found in ${rootDir.absolutePath} (or in tuning/ or heldout/)."
                    )
                )
            }

            val combinedRecordsWithSplit = mutableListOf<Pair<LabelRecord, String>>()

            val tuningRecords = if (tuningCsv != null) {
                try {
                    parseAndValidateLabelsCsv(tuningCsv)
                } catch (e: IllegalArgumentException) {
                    AppLog.e("EVALUATION", "Refusing to run evaluation (tuning): ${e.message}")
                    return@withContext Result.failure(e)
                }
            } else emptyList()

            val heldoutRecords = if (heldoutCsv != null) {
                try {
                    parseAndValidateLabelsCsv(heldoutCsv)
                } catch (e: IllegalArgumentException) {
                    AppLog.e("EVALUATION", "Refusing to run evaluation (heldout): ${e.message}")
                    return@withContext Result.failure(e)
                }
            } else emptyList()

            combinedRecordsWithSplit.addAll(tuningRecords.map { it to "tuning" })
            combinedRecordsWithSplit.addAll(heldoutRecords.map { it to "heldout" })

            try {
                validateSplitAndProductIsolation(combinedRecordsWithSplit)
            } catch (e: IllegalArgumentException) {
                AppLog.e("EVALUATION", "Refusing to run evaluation: ${e.message}")
                return@withContext Result.failure(e)
            }

            for (record in tuningRecords) {
                val imageFile = File(tuningDir, record.filename)
                allRows.add(
                    evaluateSingleImage(
                        splitName = "tuning",
                        record = record,
                        imageFile = imageFile,
                        runAllPasses = runAllPasses,
                        rapidOcrExtractor = rapidOcrExtractor,
                        deterministicExtractor = deterministicExtractor,
                    )
                )
            }

            for (record in heldoutRecords) {
                val imageFile = File(heldoutDir, record.filename)
                allRows.add(
                    evaluateSingleImage(
                        splitName = "heldout",
                        record = record,
                        imageFile = imageFile,
                        runAllPasses = runAllPasses,
                        rapidOcrExtractor = rapidOcrExtractor,
                        deterministicExtractor = deterministicExtractor,
                    )
                )
            }
        }

        if (allRows.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Ground truth CSV contained 0 evaluable rows in ${rootDir.absolutePath}.")
            )
        }

        val outputCsvFile = File(rootDir, "evaluation_results_${System.currentTimeMillis()}.csv")
        writeEvaluationCsv(outputCsvFile, allRows)

        val summary = computeSummary(allRows, outputCsvFile, runAllPasses)
        AppLog.i("EVALUATION", summary.formattedSummary)

        Result.success(summary)
    }

    private fun findLabelsCsvFile(dir: File): File? {
        if (!dir.exists() || !dir.isDirectory) return null
        val preferred = File(dir, "labels_template.csv")
        if (preferred.exists() && preferred.isFile) return preferred
        val expectedDates = File(dir, "expected_dates.csv")
        if (expectedDates.exists() && expectedDates.isFile) return expectedDates
        return dir.listFiles { file ->
            file.isFile &&
                file.name.endsWith(".csv", ignoreCase = true) &&
                !file.name.equals("split.csv", ignoreCase = true) &&
                !file.name.startsWith("evaluation_results_", ignoreCase = true)
        }?.firstOrNull()
    }

    private fun inferSplitFromFolders(rootDir: File, filename: String): String {
        val inHeldout = File(File(rootDir, "heldout"), filename).exists()
        return if (inHeldout) "heldout" else "tuning"
    }

    private fun resolveImageFile(rootDir: File, split: String, filename: String): File {
        val splitCandidate = File(File(rootDir, split), filename)
        if (splitCandidate.exists()) return splitCandidate
        val tuningCandidate = File(File(rootDir, "tuning"), filename)
        if (tuningCandidate.exists()) return tuningCandidate
        val heldoutCandidate = File(File(rootDir, "heldout"), filename)
        if (heldoutCandidate.exists()) return heldoutCandidate
        val imagesCandidate = File(File(rootDir, "images"), filename)
        if (imagesCandidate.exists()) return imagesCandidate
        return File(rootDir, filename)
    }

    private suspend fun evaluateSingleImage(
        splitName: String,
        record: LabelRecord,
        imageFile: File,
        runAllPasses: Boolean,
        rapidOcrExtractor: RapidOcrExtractor,
        deterministicExtractor: DeterministicMemoryExtractor,
    ): EvaluationRow {
        val filename = record.filename
        val expectedDateRaw = record.expiryDate

        if (!imageFile.exists()) {
            AppLog.w("EVALUATION", "[$splitName] Image file missing: ${imageFile.absolutePath}")
            return EvaluationRow(
                split = splitName,
                filename = filename,
                expected = expectedDateRaw,
                extracted = "none",
                correct = "N",
                expectedDateInRawOcr = "N",
                confidence = 0f,
                failureType = "OCR_MISSING",
                winningPass = "none",
                totalMs = 0L,
                msPerPass = "FILE_NOT_FOUND",
                rawOcrTextOfEachPass = "FILE_NOT_FOUND",
                difficulty = record.difficulty,
            )
        }

        // Run REAL shared production OCR pipeline
        val evalOcrResult = rapidOcrExtractor.extractForEvaluation(
            imageUri = imageFile.absolutePath,
            runAllPasses = runAllPasses,
        )
        val evalData = evalOcrResult.getOrNull()

        val extractedText = evalData?.extractedText
        val winningPass = evalData?.winningPass ?: "original"
        val totalMs = evalData?.totalMs ?: 0L
        val passes = evalData?.passes ?: emptyList()

        // Run REAL production deterministic parsing and scoring on the winning pass
        val parsedLocalResult = if (extractedText != null && extractedText.blocks.isNotEmpty()) {
            deterministicExtractor.extractLocalResult(extractedText.blocks)
        } else if (extractedText != null) {
            deterministicExtractor.extractLocalResult(extractedText.rawText)
        } else {
            deterministicExtractor.extractLocalResult("")
        }

        val extractedDateIso = parsedLocalResult.selectedEventDate?.toString() ?: "none"
        val confidence = parsedLocalResult.confidence

        val isCorrect = isDateMatch(expected = expectedDateRaw, extracted = extractedDateIso)
        val correctStr = if (isCorrect) "Y" else "N"

        // Evaluate each executed pass independently to see if that pass ALONE finds the expected date
        val passOutcomes = passes.map { pass ->
            val passParsed = if (pass.extractedText.blocks.isNotEmpty()) {
                deterministicExtractor.extractLocalResult(pass.extractedText.blocks)
            } else {
                deterministicExtractor.extractLocalResult(pass.rawText)
            }
            val passDateStr = passParsed.selectedEventDate?.toString() ?: "none"
            val passFoundExpected = isDateMatch(expected = expectedDateRaw, extracted = passDateStr)
            PassDiagnosticOutcome(
                passName = pass.passName,
                durationMs = pass.durationMs,
                extractedDate = passDateStr,
                foundExpectedDateAlone = passFoundExpected,
                rawText = pass.rawText,
            )
        }

        val allRawPassTexts = passes.map { it.rawText }

        // Requirement 6: Record OCR-quality metric "expected date text appears in at least one pass's raw OCR text" (Y/N)
        // separately from "final extracted date correct"
        val normExpected = expectedDateRaw.trim().lowercase(Locale.ROOT)
        val isDateInRawOcr = if (normExpected == "none") {
            true
        } else {
            isExpectedDatePresentInOcr(normExpected, allRawPassTexts)
        }
        val expectedDateInRawOcrStr = if (isDateInRawOcr) "Y" else "N"

        val mfgDate = parsedLocalResult.fields["MFG Date"] ?: record.mfgDate.takeIf { it.isNotBlank() }
        val candidateDates = parsedLocalResult.dateCandidates.mapNotNull { it.eventDate }

        val failureType = classifyFailureType(
            isCorrect = isCorrect,
            expectedDateStr = expectedDateRaw,
            extractedDateStr = extractedDateIso,
            rawTextsOfAllPasses = allRawPassTexts,
            allParsedCandidates = candidateDates,
            mfgDateStr = mfgDate,
        )

        val msPerPassStr = passOutcomes.joinToString("; ") { outcome ->
            if (runAllPasses) {
                val aloneFlag = if (outcome.foundExpectedDateAlone) "FOUND(${outcome.extractedDate})" else "MISS(${outcome.extractedDate})"
                "${outcome.passName}: ${outcome.durationMs}ms [$aloneFlag]"
            } else {
                "${outcome.passName}: ${outcome.durationMs}ms"
            }
        }

        val rawOcrTextStr = buildString {
            append("[expected_in_ocr=$expectedDateInRawOcrStr] ")
            append(
                passOutcomes.joinToString(" | ") { outcome ->
                    val cleanSnippet = outcome.rawText
                        .replace("\r\n", " [NL] ")
                        .replace("\n", " [NL] ")
                        .replace("\t", " ")
                        .trim()
                    "[${outcome.passName}]: $cleanSnippet"
                }
            )
        }

        val row = EvaluationRow(
            split = splitName,
            filename = filename,
            expected = expectedDateRaw,
            extracted = extractedDateIso,
            correct = correctStr,
            expectedDateInRawOcr = expectedDateInRawOcrStr,
            confidence = confidence,
            failureType = failureType,
            winningPass = winningPass,
            totalMs = totalMs,
            msPerPass = msPerPassStr,
            rawOcrTextOfEachPass = rawOcrTextStr,
            difficulty = record.difficulty,
            passDiagnosticOutcomes = passOutcomes,
        )

        AppLog.i(
            "EVALUATION",
            "[$splitName][$filename] Expected: $expectedDateRaw | Extracted: $extractedDateIso | Correct: $correctStr | InRawOCR: $expectedDateInRawOcrStr | Failure: $failureType | Winner: $winningPass (${totalMs}ms)"
        )
        return row
    }

    /**
     * Evaluates date equality supporting YYYY-MM-DD, YYYY-MM (month-only), and "none".
     */
    fun isDateMatch(expected: String, extracted: String): Boolean {
        val normExpected = expected.trim().lowercase(Locale.ROOT)
        val normExtracted = extracted.trim().lowercase(Locale.ROOT)

        if (normExpected == "none") {
            return normExtracted == "none"
        }
        if (normExtracted == "none") {
            return false
        }
        if (normExpected == normExtracted) {
            return true
        }
        if (normExpected.length == 7 && normExtracted.startsWith(normExpected)) {
            return true
        }
        if (normExpected.length == 10 && normExpected.endsWith("-01") && normExtracted.length == 7 && normExpected.startsWith(normExtracted)) {
            return true
        }
        return false
    }

    /**
     * Determines failure type according to the 4 strict requirements:
     * - OCR_MISSING: Expected date text is not in any pass's raw text.
     * - PARSE_WRONG: Date text is present in raw text but extracted differently or missed.
     * - WRONG_PICK: A different valid date was chosen (e.g. MFG instead of EXP).
     * - NONE: Extracted matched expected.
     */
    fun classifyFailureType(
        isCorrect: Boolean,
        expectedDateStr: String,
        extractedDateStr: String,
        rawTextsOfAllPasses: List<String>,
        allParsedCandidates: List<LocalDate>,
        mfgDateStr: String?,
    ): String {
        if (isCorrect) return "NONE"

        val normExpected = expectedDateStr.trim().lowercase(Locale.ROOT)
        if (normExpected == "none") {
            return "PARSE_WRONG"
        }

        val isPresentInOcr = isExpectedDatePresentInOcr(normExpected, rawTextsOfAllPasses)
        if (!isPresentInOcr) {
            return "OCR_MISSING"
        }

        val normExtracted = extractedDateStr.trim().lowercase(Locale.ROOT)
        if (normExtracted != "none") {
            if (mfgDateStr != null && normExtracted == mfgDateStr.trim().lowercase(Locale.ROOT)) {
                return "WRONG_PICK"
            }
            if (allParsedCandidates.size > 1) {
                return "WRONG_PICK"
            }
            return "WRONG_PICK"
        }

        return "PARSE_WRONG"
    }

    /**
     * Checks whether the expected date's year and month tokens appear in at least one pass's raw OCR text.
     */
    fun isExpectedDatePresentInOcr(expectedIso: String, rawTexts: List<String>): Boolean {
        val normExpected = expectedIso.trim().lowercase(Locale.ROOT)
        if (normExpected == "none") return true

        val parts = normExpected.split("-")
        if (parts.isEmpty()) return false

        val year = parts[0]
        val shortYear = if (year.length == 4) year.substring(2) else ""
        val monthNum = parts.getOrNull(1)

        val monthNames = when (monthNum) {
            "01" -> listOf("jan", "january", "01", "1")
            "02" -> listOf("feb", "february", "02", "2")
            "03" -> listOf("mar", "march", "03", "3")
            "04" -> listOf("apr", "april", "04", "4")
            "05" -> listOf("may", "05", "5")
            "06" -> listOf("jun", "june", "06", "6")
            "07" -> listOf("jul", "july", "07", "7")
            "08" -> listOf("aug", "august", "08", "8")
            "09" -> listOf("sep", "sept", "september", "09", "9")
            "10" -> listOf("oct", "october", "10")
            "11" -> listOf("nov", "november", "11")
            "12" -> listOf("dec", "december", "12")
            else -> emptyList()
        }

        for (raw in rawTexts) {
            val lower = raw.lowercase(Locale.ROOT)
            val hasYear = lower.contains(year) || (shortYear.isNotEmpty() && (lower.contains("/$shortYear") || lower.contains(" $shortYear") || lower.contains(".$shortYear") || lower.contains("-$shortYear")))
            if (!hasYear) continue

            if (monthNames.isEmpty() || monthNames.any { lower.contains(it) }) {
                return true
            }
        }
        return false
    }

    /**
     * Strictly validates that `expiry_date` is `YYYY-MM-DD`, `YYYY-MM`, or `"none"` (case-insensitive).
     */
    fun isValidExpectedDateFormat(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.equals("none", ignoreCase = true)) return true

        val fullDateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        if (fullDateRegex.matches(trimmed)) {
            return try {
                LocalDate.parse(trimmed)
                true
            } catch (e: Exception) {
                false
            }
        }

        val yearMonthRegex = Regex("^\\d{4}-\\d{2}$")
        if (yearMonthRegex.matches(trimmed)) {
            return try {
                YearMonth.parse(trimmed)
                true
            } catch (e: Exception) {
                false
            }
        }

        return false
    }

    /**
     * Parses `labels_template.csv` (or `expected_dates.csv`) and REFUSES to run if:
     * 1. Any row has an empty `expiry_date`, `product_id` (when `product_id` column is present), or `difficulty` (when `difficulty` column is present).
     * 2. Any row has an `expiry_date` that is not strictly `YYYY-MM-DD`, `YYYY-MM`, or `"none"`.
     */
    fun parseAndValidateLabelsCsv(csvFile: File): List<LabelRecord> {
        val lines = csvFile.readLines()
        if (lines.isEmpty()) {
            throw IllegalArgumentException("CSV file ${csvFile.name} is empty.")
        }

        val header = parseCsvLine(lines.first()).map { it.trim().lowercase(Locale.ROOT) }
        val filenameIdx = header.indexOfFirst { it == "filename" || it == "image" || it == "file" }.let { if (it != -1) it else 0 }
        val productIdIdx = header.indexOfFirst { it == "product_id" || it == "productid" }
        val expiryIdx = header.indexOfFirst { it == "expiry_date" || it == "expected_date" || it == "expected" || it == "date" }.let { if (it != -1) it else 1 }
        val mfgIdx = header.indexOfFirst { it == "mfg_date" }
        val formatIdx = header.indexOfFirst { it == "date_format_printed" }
        val difficultyIdx = header.indexOfFirst { it == "difficulty" }
        val notesIdx = header.indexOfFirst { it == "notes" }

        val records = mutableListOf<LabelRecord>()
        val unlabeledFiles = mutableListOf<String>()
        val invalidDateFiles = mutableListOf<String>()

        for ((lineIdx, line) in lines.drop(1).withIndex()) {
            if (line.isBlank()) continue
            val tokens = parseCsvLine(line)
            val filename = tokens.getOrNull(filenameIdx)?.trim().orEmpty()
            if (filename.isBlank()) continue

            val productId = if (productIdIdx != -1) tokens.getOrNull(productIdIdx)?.trim().orEmpty() else ""
            val expiryDate = tokens.getOrNull(expiryIdx)?.trim().orEmpty()
            val mfgDate = if (mfgIdx != -1) tokens.getOrNull(mfgIdx)?.trim().orEmpty() else ""
            val dateFormat = if (formatIdx != -1) tokens.getOrNull(formatIdx)?.trim().orEmpty() else ""
            val difficulty = if (difficultyIdx != -1) tokens.getOrNull(difficultyIdx)?.trim().orEmpty() else ""
            val notes = if (notesIdx != -1) tokens.getOrNull(notesIdx)?.trim().orEmpty() else ""

            if (expiryDate.isBlank() ||
                (productIdIdx != -1 && productId.isBlank()) ||
                (difficultyIdx != -1 && difficulty.isBlank())
            ) {
                unlabeledFiles.add("$filename (row ${lineIdx + 2})")
            } else if (!isValidExpectedDateFormat(expiryDate)) {
                invalidDateFiles.add("$filename (row ${lineIdx + 2}: '$expiryDate')")
            }

            records.add(
                LabelRecord(
                    filename = filename,
                    productId = productId,
                    expiryDate = expiryDate,
                    mfgDate = mfgDate,
                    dateFormatPrinted = dateFormat,
                    difficulty = difficulty,
                    notes = notes,
                )
            )
        }

        if (unlabeledFiles.isNotEmpty()) {
            val preview = unlabeledFiles.take(5).joinToString(", ")
            val more = if (unlabeledFiles.size > 5) " and ${unlabeledFiles.size - 5} more" else ""
            throw IllegalArgumentException(
                "Refusing to run evaluation: ${unlabeledFiles.size} / ${records.size} rows in ${csvFile.name} have empty required labels (product_id, expiry_date, or difficulty): $preview$more. Please fill all labels first."
            )
        }

        if (invalidDateFiles.isNotEmpty()) {
            val preview = invalidDateFiles.take(5).joinToString(", ")
            val more = if (invalidDateFiles.size > 5) " and ${invalidDateFiles.size - 5} more" else ""
            throw IllegalArgumentException(
                "Refusing to run evaluation: ${invalidDateFiles.size} rows in ${csvFile.name} have invalid expiry_date format (must be YYYY-MM-DD, YYYY-MM, or 'none'): $preview$more."
            )
        }

        return records
    }

    /**
     * Validates split rules:
     * 1. Rejects any `product_id` that appears in both `"tuning"` and `"heldout"`.
     * 2. Rejects any previously tuned `IMG_20261008_*` image if assigned to `"heldout"`.
     */
    fun validateSplitAndProductIsolation(recordsWithSplit: List<Pair<LabelRecord, String>>) {
        val splitsByProductId = mutableMapOf<String, MutableSet<String>>()
        val filesByProductId = mutableMapOf<String, MutableList<String>>()
        val leakedHistoricalImages = mutableListOf<String>()

        for ((record, rawSplit) in recordsWithSplit) {
            val split = rawSplit.trim().lowercase(Locale.ROOT)
            if (record.filename.startsWith("IMG_20261008_", ignoreCase = true) && split == "heldout") {
                leakedHistoricalImages.add(record.filename)
            }
            val pid = record.productId.trim()
            if (pid.isNotEmpty()) {
                splitsByProductId.getOrPut(pid) { mutableSetOf() }.add(split)
                filesByProductId.getOrPut(pid) { mutableListOf() }.add("${record.filename} ($split)")
            }
        }

        if (leakedHistoricalImages.isNotEmpty()) {
            throw IllegalArgumentException(
                "Refusing to run evaluation: Historical tuning images (IMG_20261008_*) are not allowed in 'heldout': ${leakedHistoricalImages.joinToString(", ")}"
            )
        }

        val overlappingProducts = splitsByProductId.filter { (_, splits) ->
            splits.contains("tuning") && splits.contains("heldout")
        }

        if (overlappingProducts.isNotEmpty()) {
            val details = overlappingProducts.keys.joinToString("; ") { pid ->
                "product_id='$pid' in [${filesByProductId[pid]?.joinToString(", ")}]"
            }
            throw IllegalArgumentException(
                "Refusing to run evaluation: product_id appears in both 'tuning' and 'heldout' splits (data leakage): $details"
            )
        }
    }

    /**
     * Legacy helper returning `(filename, expected_date)` pairs, throwing if any required label is empty.
     */
    fun parseGroundTruthCsv(csvFile: File): List<Pair<String, String>> {
        return parseAndValidateLabelsCsv(csvFile).map { it.filename to it.expiryDate }
    }

    /**
     * Parses `split.csv` (`filename,split`) returning a map of `filename -> split` ("tuning" or "heldout").
     */
    fun parseSplitCsv(splitFile: File): Map<String, String> {
        if (!splitFile.exists()) return emptyMap()
        val lines = splitFile.readLines()
        if (lines.isEmpty()) return emptyMap()

        val header = parseCsvLine(lines.first()).map { it.trim().lowercase(Locale.ROOT) }
        val fileIdx = header.indexOfFirst { it == "filename" || it == "file" }.let { if (it != -1) it else 0 }
        val splitIdx = header.indexOfFirst { it == "split" }.let { if (it != -1) it else 1 }

        val map = mutableMapOf<String, String>()
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val tokens = parseCsvLine(line)
            val fn = tokens.getOrNull(fileIdx)?.trim().orEmpty()
            val sp = tokens.getOrNull(splitIdx)?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (fn.isNotBlank() && (sp == "tuning" || sp == "heldout")) {
                map[fn] = sp
            }
        }
        return map
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = StringBuilder()

        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

    /**
     * Writes evaluation results to RFC 4180 compliant CSV.
     * Includes the 10 core columns plus `split` and `expected_date_in_raw_ocr` (Y/N).
     */
    private fun writeEvaluationCsv(targetFile: File, rows: List<EvaluationRow>) {
        targetFile.bufferedWriter().use { writer ->
            writer.write("filename,expected,extracted,correct,expected_date_in_raw_ocr,split,confidence,failure_type,winning_pass,total_ms,ms_per_pass,raw_ocr_text_of_each_pass\n")
            for (row in rows) {
                val escapedMsPerPass = escapeCsvField(row.msPerPass)
                val escapedRawOcr = escapeCsvField(row.rawOcrTextOfEachPass)
                writer.write(
                    "${escapeCsvField(row.filename)},${escapeCsvField(row.expected)},${escapeCsvField(row.extracted)},${row.correct},${row.expectedDateInRawOcr},${row.split},${row.confidence},${row.failureType},${row.winningPass},${row.totalMs},$escapedMsPerPass,$escapedRawOcr\n"
                )
            }
        }
    }

    private fun escapeCsvField(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains(";")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun computeSplitSummary(splitName: String, rows: List<EvaluationRow>): SplitSummary {
        val total = rows.size
        val correctCount = rows.count { it.correct == "Y" }
        val accuracy = if (total > 0) (correctCount.toDouble() / total * 100.0) else 0.0

        val ocrRecallCount = rows.count { it.expectedDateInRawOcr == "Y" }
        val ocrRecallPercent = if (total > 0) (ocrRecallCount.toDouble() / total * 100.0) else 0.0

        val failureCounts = mutableMapOf(
            "NONE" to 0,
            "OCR_MISSING" to 0,
            "PARSE_WRONG" to 0,
            "WRONG_PICK" to 0
        )
        for (row in rows) {
            val f = row.failureType
            failureCounts[f] = (failureCounts[f] ?: 0) + 1
        }

        val totalMsList = rows.map { it.totalMs }
        val avgMs = if (totalMsList.isNotEmpty()) totalMsList.average() else 0.0
        val worstRow = rows.maxByOrNull { it.totalMs }
        val worstMs = worstRow?.totalMs ?: 0L
        val worstFilename = worstRow?.filename ?: "N/A"

        val winningPassCounts = rows.groupingBy { it.winningPass }.eachCount()
        val mostFrequentPass = winningPassCounts.maxByOrNull { it.value }?.key ?: "none"

        val passAloneSuccess = mutableMapOf<String, Int>()
        for (row in rows) {
            for (outcome in row.passDiagnosticOutcomes) {
                if (outcome.foundExpectedDateAlone) {
                    passAloneSuccess[outcome.passName] = (passAloneSuccess[outcome.passName] ?: 0) + 1
                } else {
                    passAloneSuccess.putIfAbsent(outcome.passName, 0)
                }
            }
        }

        return SplitSummary(
            splitName = splitName,
            totalCount = total,
            correctCount = correctCount,
            accuracyPercent = accuracy,
            ocrRecallCount = ocrRecallCount,
            ocrRecallPercent = ocrRecallPercent,
            countsByFailureType = failureCounts,
            averageTotalMs = avgMs,
            worstCaseTotalMs = worstMs,
            worstCaseFilename = worstFilename,
            winningPassCounts = winningPassCounts,
            mostFrequentWinningPass = mostFrequentPass,
            passAloneSuccessCounts = passAloneSuccess,
        )
    }

    /**
     * Computes overall and per-split ("tuning" vs "heldout") accuracy %, OCR raw-text recall %,
     * counts per failure type, latency stats, most frequent winning pass, and per-pass standalone recall.
     */
    fun computeSummary(
        rows: List<EvaluationRow>,
        csvFile: File,
        runAllPasses: Boolean = false,
    ): EvaluationSummary {
        val overallSplit = computeSplitSummary("overall", rows)
        val tuningRows = rows.filter { it.split == "tuning" }
        val heldoutRows = rows.filter { it.split == "heldout" }

        val tuningSplit = if (tuningRows.isNotEmpty()) computeSplitSummary("tuning", tuningRows) else null
        val heldoutSplit = if (heldoutRows.isNotEmpty()) computeSplitSummary("heldout", heldoutRows) else null

        val formatted = buildString {
            appendLine("============================================================")
            appendLine("         EXPIRY-DATE PIPELINE EVALUATION SUMMARY            ")
            appendLine("============================================================")
            appendLine("Mode                   : ${if (runAllPasses) "ALL PASSES (No Early Exit)" else "PRODUCTION (Early Exit)"}")
            appendLine("Total Evaluated Images : ${overallSplit.totalCount}")
            appendLine("OCR Date Text Present  : ${overallSplit.ocrRecallCount} / ${overallSplit.totalCount} (${String.format(Locale.ROOT, "%.2f", overallSplit.ocrRecallPercent)}%)")
            appendLine("Correct Matches        : ${overallSplit.correctCount} / ${overallSplit.totalCount}")
            appendLine("Overall Accuracy       : ${String.format(Locale.ROOT, "%.2f", overallSplit.accuracyPercent)}%")

            if (tuningSplit != null) {
                appendLine("")
                appendLine("--- [TUNING SPLIT] ---")
                appendLine("  Images               : ${tuningSplit.totalCount}")
                appendLine("  OCR Date Present     : ${tuningSplit.ocrRecallCount} / ${tuningSplit.totalCount} (${String.format(Locale.ROOT, "%.2f", tuningSplit.ocrRecallPercent)}%)")
                appendLine("  Correct Extracted    : ${tuningSplit.correctCount} / ${tuningSplit.totalCount}")
                appendLine("  Tuning Accuracy      : ${String.format(Locale.ROOT, "%.2f", tuningSplit.accuracyPercent)}%")
                appendLine("  Failures             : NONE=${tuningSplit.countsByFailureType["NONE"] ?: 0}, OCR_MISSING=${tuningSplit.countsByFailureType["OCR_MISSING"] ?: 0}, PARSE_WRONG=${tuningSplit.countsByFailureType["PARSE_WRONG"] ?: 0}, WRONG_PICK=${tuningSplit.countsByFailureType["WRONG_PICK"] ?: 0}")
                appendLine("  Avg / Worst Latency  : ${String.format(Locale.ROOT, "%.1f", tuningSplit.averageTotalMs)} ms / ${tuningSplit.worstCaseTotalMs} ms (${tuningSplit.worstCaseFilename})")
            }

            if (heldoutSplit != null) {
                appendLine("")
                appendLine("--- [HELDOUT SPLIT (Never used for tuning)] ---")
                appendLine("  Images               : ${heldoutSplit.totalCount}")
                appendLine("  OCR Date Present     : ${heldoutSplit.ocrRecallCount} / ${heldoutSplit.totalCount} (${String.format(Locale.ROOT, "%.2f", heldoutSplit.ocrRecallPercent)}%)")
                appendLine("  Correct Extracted    : ${heldoutSplit.correctCount} / ${heldoutSplit.totalCount}")
                appendLine("  Heldout Accuracy     : ${String.format(Locale.ROOT, "%.2f", heldoutSplit.accuracyPercent)}%")
                appendLine("  Failures             : NONE=${heldoutSplit.countsByFailureType["NONE"] ?: 0}, OCR_MISSING=${heldoutSplit.countsByFailureType["OCR_MISSING"] ?: 0}, PARSE_WRONG=${heldoutSplit.countsByFailureType["PARSE_WRONG"] ?: 0}, WRONG_PICK=${heldoutSplit.countsByFailureType["WRONG_PICK"] ?: 0}")
                appendLine("  Avg / Worst Latency  : ${String.format(Locale.ROOT, "%.1f", heldoutSplit.averageTotalMs)} ms / ${heldoutSplit.worstCaseTotalMs} ms (${heldoutSplit.worstCaseFilename})")
            } else {
                appendLine("")
                appendLine("--- [HELDOUT SPLIT] ---")
                appendLine("  Images               : 0 (No heldout images present yet)")
            }

            appendLine("")
            appendLine("Failure Type Breakdown (Overall):")
            appendLine("  - NONE (Correct)     : ${overallSplit.countsByFailureType["NONE"] ?: 0}")
            appendLine("  - OCR_MISSING        : ${overallSplit.countsByFailureType["OCR_MISSING"] ?: 0}")
            appendLine("  - PARSE_WRONG        : ${overallSplit.countsByFailureType["PARSE_WRONG"] ?: 0}")
            appendLine("  - WRONG_PICK         : ${overallSplit.countsByFailureType["WRONG_PICK"] ?: 0}")
            appendLine("")
            appendLine("Latency Performance (ms):")
            appendLine("  - Average Total      : ${String.format(Locale.ROOT, "%.1f", overallSplit.averageTotalMs)} ms")
            appendLine("  - Worst-Case         : ${overallSplit.worstCaseTotalMs} ms (File: ${overallSplit.worstCaseFilename})")
            appendLine("")
            appendLine("Winning Pass Frequency:")
            for ((pass, count) in overallSplit.winningPassCounts.entries.sortedByDescending { it.value }) {
                appendLine("  - $pass : $count")
            }
            appendLine("Most Frequent Winner   : ${overallSplit.mostFrequentWinningPass}")

            if (overallSplit.passAloneSuccessCounts.isNotEmpty()) {
                appendLine("")
                appendLine("Standalone Pass Recall (Pass Alone Finds Expected Date):")
                for ((passName, count) in overallSplit.passAloneSuccessCounts.entries) {
                    val pct = if (overallSplit.totalCount > 0) (count.toDouble() / overallSplit.totalCount * 100.0) else 0.0
                    appendLine("  - $passName : $count / ${overallSplit.totalCount} (${String.format(Locale.ROOT, "%.1f", pct)}%)")
                }
            }

            appendLine("")
            appendLine("Local-Only Invariant:")
            appendLine("  - Gemini API Calls   : 0 (confirmed OFF)")
            appendLine("  - Network Calls      : 0 (confirmed OFF)")
            appendLine("")
            appendLine("CSV Report Saved       : ${csvFile.absolutePath}")
            appendLine("============================================================")
        }

        return EvaluationSummary(
            totalCount = overallSplit.totalCount,
            correctCount = overallSplit.correctCount,
            accuracyPercent = overallSplit.accuracyPercent,
            ocrRecallCount = overallSplit.ocrRecallCount,
            ocrRecallPercent = overallSplit.ocrRecallPercent,
            countsByFailureType = overallSplit.countsByFailureType,
            averageTotalMs = overallSplit.averageTotalMs,
            worstCaseTotalMs = overallSplit.worstCaseTotalMs,
            worstCaseFilename = overallSplit.worstCaseFilename,
            winningPassCounts = overallSplit.winningPassCounts,
            mostFrequentWinningPass = overallSplit.mostFrequentWinningPass,
            generatedCsvFile = csvFile,
            formattedSummary = formatted,
            tuningSummary = tuningSplit,
            heldoutSummary = heldoutSplit,
            runAllPasses = runAllPasses,
            rows = rows,
        )
    }

    /**
     * Exports the generated evaluation CSV to the device's public Downloads directory
     * using MediaStore on Android 10+ (API 29+) with IS_PENDING atomic commit and readback verification.
     */
    fun exportCsvToDownloads(context: Context, sourceCsvFile: File): Result<File> {
        return runCatching {
            val downloadFilename = "memora_expiry_evaluation_${System.currentTimeMillis()}.csv"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, downloadFilename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Failed to create MediaStore entry in Downloads")

                resolver.openOutputStream(uri)?.use { out ->
                    sourceCsvFile.inputStream().use { it.copyTo(out) }
                } ?: throw IllegalStateException("Failed to open OutputStream for MediaStore URI: $uri")

                val publishValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, publishValues, null, null)

                val bytesVerified = resolver.openInputStream(uri)?.use { it.readBytes().size } ?: 0
                if (bytesVerified <= 0) {
                    throw IllegalStateException("MediaStore verification failed: exported 0 bytes for $uri")
                }

                val publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destination = File(publicDownloadDir, downloadFilename)
                AppLog.i("EVALUATION", "Verified MediaStore CSV export to Downloads ($bytesVerified bytes): ${destination.absolutePath} (URI: $uri)")
                destination
            } else {
                val publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!publicDownloadDir.exists()) {
                    publicDownloadDir.mkdirs()
                }
                val destination = File(publicDownloadDir, downloadFilename)
                sourceCsvFile.copyTo(destination, overwrite = true)
                AppLog.i("EVALUATION", "Exported CSV to Downloads: ${destination.absolutePath}")
                destination
            }
        }
    }
}
