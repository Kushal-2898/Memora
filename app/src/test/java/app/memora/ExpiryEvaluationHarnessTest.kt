package app.memora

import app.memora.domain.benchmark.ExpiryEvaluationHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ExpiryEvaluationHarnessTest {

    @Test
    fun isDateMatch_matchesExactIsoDate() {
        assertTrue(ExpiryEvaluationHarness.isDateMatch("2026-12-01", "2026-12-01"))
        assertFalse(ExpiryEvaluationHarness.isDateMatch("2026-12-01", "2026-11-01"))
    }

    @Test
    fun isDateMatch_matchesMonthOnlyExpected() {
        assertTrue(ExpiryEvaluationHarness.isDateMatch("2026-12", "2026-12-01"))
        assertTrue(ExpiryEvaluationHarness.isDateMatch("2026-12", "2026-12-31"))
        assertFalse(ExpiryEvaluationHarness.isDateMatch("2026-12", "2026-11-01"))
    }

    @Test
    fun isDateMatch_matchesNoneWhenNoDateExpected() {
        assertTrue(ExpiryEvaluationHarness.isDateMatch("none", "none"))
        assertTrue(ExpiryEvaluationHarness.isDateMatch("NONE", "none"))
        assertFalse(ExpiryEvaluationHarness.isDateMatch("none", "2026-12-01"))
        assertFalse(ExpiryEvaluationHarness.isDateMatch("2026-12-01", "none"))
    }

    @Test
    fun classifyFailureType_returnsNone_whenCorrect() {
        val result = ExpiryEvaluationHarness.classifyFailureType(
            isCorrect = true,
            expectedDateStr = "2026-12-01",
            extractedDateStr = "2026-12-01",
            rawTextsOfAllPasses = listOf("PARACETAMOL EXP: 12/2026"),
            allParsedCandidates = listOf(LocalDate.of(2026, 12, 1)),
            mfgDateStr = null
        )
        assertEquals("NONE", result)
    }

    @Test
    fun classifyFailureType_returnsOcrMissing_whenDateNotInAnyPass() {
        val result = ExpiryEvaluationHarness.classifyFailureType(
            isCorrect = false,
            expectedDateStr = "2026-12-01",
            extractedDateStr = "none",
            rawTextsOfAllPasses = listOf("PARACETAMOL 500MG BATCH NO 1234"),
            allParsedCandidates = emptyList(),
            mfgDateStr = null
        )
        assertEquals("OCR_MISSING", result)
    }

    @Test
    fun classifyFailureType_returnsParseWrong_whenDateInOcrButExtractedNone() {
        val result = ExpiryEvaluationHarness.classifyFailureType(
            isCorrect = false,
            expectedDateStr = "2026-12-01",
            extractedDateStr = "none",
            rawTextsOfAllPasses = listOf("PARACETAMOL EXP 12/2026"),
            allParsedCandidates = emptyList(),
            mfgDateStr = null
        )
        assertEquals("PARSE_WRONG", result)
    }

    @Test
    fun classifyFailureType_returnsWrongPick_whenPickedMfgInsteadOfExp() {
        val result = ExpiryEvaluationHarness.classifyFailureType(
            isCorrect = false,
            expectedDateStr = "2026-12-01",
            extractedDateStr = "2024-01-01",
            rawTextsOfAllPasses = listOf("MFG: 01/2024 EXP: 12/2026"),
            allParsedCandidates = listOf(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 1)),
            mfgDateStr = "2024-01-01"
        )
        assertEquals("WRONG_PICK", result)
    }

    @Test
    fun parseGroundTruthCsv_correctlyReadsHeaderAndRows() {
        val tempCsv = File.createTempFile("test_truth", ".csv").apply { deleteOnExit() }
        tempCsv.writeText(
            """
            filename,expected_date
            sample1.jpg,2026-12-01
            sample2.jpg,none
            sample3.jpg,2027-05
            """.trimIndent()
        )

        val records = ExpiryEvaluationHarness.parseGroundTruthCsv(tempCsv)
        assertEquals(3, records.size)
        assertEquals("sample1.jpg" to "2026-12-01", records[0])
        assertEquals("sample2.jpg" to "none", records[1])
        assertEquals("sample3.jpg" to "2027-05", records[2])
    }

    @Test
    fun computeSummary_calculatesMetricsCorrectly() {
        val tempCsv = File.createTempFile("eval_out", ".csv").apply { deleteOnExit() }
        val rows = listOf(
            ExpiryEvaluationHarness.EvaluationRow(
                filename = "f1.jpg", expected = "2026-12-01", extracted = "2026-12-01",
                correct = "Y", confidence = 0.9f, failureType = "NONE",
                winningPass = "original", totalMs = 150L, msPerPass = "original: 150ms",
                rawOcrTextOfEachPass = "raw1"
            ),
            ExpiryEvaluationHarness.EvaluationRow(
                filename = "f2.jpg", expected = "2027-01-01", extracted = "none",
                correct = "N", confidence = 0.0f, failureType = "OCR_MISSING",
                winningPass = "contrast", totalMs = 250L, msPerPass = "contrast: 250ms",
                rawOcrTextOfEachPass = "raw2"
            ),
            ExpiryEvaluationHarness.EvaluationRow(
                filename = "f3.jpg", expected = "none", extracted = "none",
                correct = "Y", confidence = 0.0f, failureType = "NONE",
                winningPass = "original", totalMs = 100L, msPerPass = "original: 100ms",
                rawOcrTextOfEachPass = "raw3"
            ),
            ExpiryEvaluationHarness.EvaluationRow(
                filename = "f4.jpg", expected = "2028-05-01", extracted = "2025-01-01",
                correct = "N", confidence = 0.5f, failureType = "WRONG_PICK",
                winningPass = "rotated", totalMs = 300L, msPerPass = "rotated: 300ms",
                rawOcrTextOfEachPass = "raw4"
            ),
        )

        val summary = ExpiryEvaluationHarness.computeSummary(rows, tempCsv)
        assertEquals(4, summary.totalCount)
        assertEquals(2, summary.correctCount)
        assertEquals(50.0, summary.accuracyPercent, 0.01)
        assertEquals(2, summary.countsByFailureType["NONE"])
        assertEquals(1, summary.countsByFailureType["OCR_MISSING"])
        assertEquals(1, summary.countsByFailureType["WRONG_PICK"])
        assertEquals(0, summary.countsByFailureType["PARSE_WRONG"])
        assertEquals(200.0, summary.averageTotalMs, 0.01)
        assertEquals(300L, summary.worstCaseTotalMs)
        assertEquals("f4.jpg", summary.worstCaseFilename)
        assertEquals("original", summary.mostFrequentWinningPass)

        assertTrue(summary.formattedSummary.contains("Overall Accuracy       : 50.00%"))
        assertTrue(summary.formattedSummary.contains("OCR_MISSING        : 1"))
        assertTrue(summary.formattedSummary.contains("WRONG_PICK         : 1"))
        assertTrue(summary.formattedSummary.contains("Worst-Case         : 300 ms"))
    }

    @Test
    fun computeSummary_reportsTuningAndHeldoutSeparately() {
        val tempCsv = File.createTempFile("eval_splits", ".csv").apply { deleteOnExit() }
        val rows = listOf(
            ExpiryEvaluationHarness.EvaluationRow(
                split = "tuning",
                filename = "t1.jpg", expected = "2026-12-01", extracted = "2026-12-01",
                correct = "Y", confidence = 0.9f, failureType = "NONE",
                winningPass = "original", totalMs = 120L, msPerPass = "original: 120ms",
                rawOcrTextOfEachPass = "raw1",
                passDiagnosticOutcomes = listOf(
                    ExpiryEvaluationHarness.PassDiagnosticOutcome("original", 120L, "2026-12-01", true, "raw1"),
                    ExpiryEvaluationHarness.PassDiagnosticOutcome("contrast", 140L, "2026-12-01", true, "raw1"),
                )
            ),
            ExpiryEvaluationHarness.EvaluationRow(
                split = "heldout",
                filename = "h1.jpg", expected = "2027-05-01", extracted = "none",
                correct = "N", confidence = 0.0f, failureType = "OCR_MISSING",
                winningPass = "original", totalMs = 200L, msPerPass = "original: 200ms",
                rawOcrTextOfEachPass = "raw2",
                passDiagnosticOutcomes = listOf(
                    ExpiryEvaluationHarness.PassDiagnosticOutcome("original", 200L, "none", false, "raw2"),
                    ExpiryEvaluationHarness.PassDiagnosticOutcome("contrast", 180L, "none", false, "raw2"),
                )
            ),
        )

        val summary = ExpiryEvaluationHarness.computeSummary(rows, tempCsv, runAllPasses = true)
        assertEquals(100.0, summary.tuningSummary?.accuracyPercent ?: 0.0, 0.01)
        assertEquals(0.0, summary.heldoutSummary?.accuracyPercent ?: -1.0, 0.01)
        assertEquals(50.0, summary.accuracyPercent, 0.01)
        assertTrue(summary.formattedSummary.contains("Tuning Accuracy      : 100.00%"))
        assertTrue(summary.formattedSummary.contains("Heldout Accuracy     : 0.00%"))
        assertTrue(summary.formattedSummary.contains("Standalone Pass Recall"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseAndValidateLabelsCsv_refusesToRunWhenLabelsAreEmpty() {
        val tempCsv = File.createTempFile("unlabeled_template", ".csv").apply { deleteOnExit() }
        tempCsv.writeText(
            """
            filename,expiry_date,mfg_date,date_format_printed,difficulty,notes
            IMG_1.jpg,2026-12-01,2024-01-01,MM/YYYY,easy,
            IMG_2.jpg,,,,,
            """.trimIndent()
        )
        ExpiryEvaluationHarness.parseAndValidateLabelsCsv(tempCsv)
    }

    @Test
    fun parseSplitCsv_readsTuningAndHeldoutAssignments() {
        val tempSplit = File.createTempFile("split_test", ".csv").apply { deleteOnExit() }
        tempSplit.writeText(
            """
            filename,split
            IMG_1.jpg,tuning
            IMG_2.jpg,heldout
            """.trimIndent()
        )
        val splitMap = ExpiryEvaluationHarness.parseSplitCsv(tempSplit)
        assertEquals("tuning", splitMap["IMG_1.jpg"])
        assertEquals("heldout", splitMap["IMG_2.jpg"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseAndValidateLabelsCsv_rejectsInvalidExpiryDateFormat() {
        val tempCsv = File.createTempFile("invalid_date_format", ".csv").apply { deleteOnExit() }
        tempCsv.writeText(
            """
            filename,product_id,expiry_date,mfg_date,date_format_printed,difficulty,notes
            IMG_1.jpg,prod_1,12/2026,2024-01-01,MM/YYYY,easy,
            """.trimIndent()
        )
        ExpiryEvaluationHarness.parseAndValidateLabelsCsv(tempCsv)
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateSplitAndProductIsolation_rejectsProductIdInBothSplits() {
        val recordsWithSplit = listOf(
            ExpiryEvaluationHarness.LabelRecord(
                filename = "WhatsApp Image 1.jpeg",
                productId = "pack_A",
                expiryDate = "2026-12-01",
                difficulty = "easy"
            ) to "tuning",
            ExpiryEvaluationHarness.LabelRecord(
                filename = "WhatsApp Image 2.jpeg",
                productId = "pack_A",
                expiryDate = "2026-12-01",
                difficulty = "easy"
            ) to "heldout"
        )
        ExpiryEvaluationHarness.validateSplitAndProductIsolation(recordsWithSplit)
    }

    @Test(expected = IllegalArgumentException::class)
    fun validateSplitAndProductIsolation_rejectsHistoricalImg20261008InHeldout() {
        val recordsWithSplit = listOf(
            ExpiryEvaluationHarness.LabelRecord(
                filename = "IMG_20261008_162834.jpg",
                productId = "pack_hist_1",
                expiryDate = "2026-12-01",
                difficulty = "easy"
            ) to "heldout"
        )
        ExpiryEvaluationHarness.validateSplitAndProductIsolation(recordsWithSplit)
    }
}
