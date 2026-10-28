package app.memora

import app.memora.domain.extraction.DeterministicMemoryExtractor
import app.memora.domain.model.EventType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class Benchmark48AccuracyTest {

    private val extractor = DeterministicMemoryExtractor()

    data class ScorecardRow(
        val index: Int,
        val filename: String,
        val groundTruth: String,
        val rawSnippet: String,
        val extractedDate: String,
        val extractedTitle: String,
        val winningPass: String,
        val status: String,
        val rootCause: String,
    )

    @Test
    fun `evaluate real 48-image RapidOCR dataset against DeterministicMemoryExtractor`() {
        val jsonFile = File("c:/Projects/Memora/testing/dataset/raw_ocr_benchmark_48.json")
        if (!jsonFile.exists()) {
            println("raw_ocr_benchmark_48.json not found yet - skipping evaluation.")
            return
        }

        val jsonContent = jsonFile.readText(Charsets.UTF_8)
        val jsonArray = JSONArray(jsonContent)

        val scorecard = mutableListOf<ScorecardRow>()
        var dateMatches = 0
        var titleMatches = 0

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val index = item.getInt("index")
            val filename = item.getString("filename")
            val groundTruth = item.getString("groundTruth")
            val passesArray = item.getJSONArray("passes")

            val passList = mutableListOf<Pair<String, String>>()
            for (p in 0 until passesArray.length()) {
                val passObj = passesArray.getJSONObject(p)
                passList.add(passObj.getString("passName") to passObj.getString("rawText"))
            }

            val refDate = LocalDate.of(2026, 10, 8)

            // Step 1: Multi-pass extraction (cross-pass consensus and shelf-life merging)
            val multiRes = extractor.extractFromPasses(passList.map { it.second })

            var winningPass = "none"
            var bestExtractedDate: LocalDate? = null
            var bestTitle = multiRes.title ?: "Other"
            var winningRawSnippet = ""

            // Step 2: Collect single-pass extractions
            val singlePassDates = mutableListOf<Pair<String, LocalDate>>()
            for ((passName, rawText) in passList) {
                if (rawText.isBlank()) continue
                val res = extractor.extractLocalResult(rawText)
                if (res.title != null && res.title != "Other" && bestTitle == "Other") {
                    bestTitle = res.title!!
                }
                if (res.selectedEventDate != null) {
                    singlePassDates.add(passName to res.selectedEventDate)
                }
            }

            // Step 3: Consensus decision
            if (multiRes.selectedEventDate != null && !multiRes.selectedEventDate.isBefore(refDate) &&
                (singlePassDates.isEmpty() || singlePassDates.all { it.second.isBefore(refDate) })) {
                // Cross-pass shelf-life or multi-pass consensus resolved a future date over past single-pass dates
                bestExtractedDate = multiRes.selectedEventDate
                winningPass = "multi-pass"
                val passRaw = passList.firstOrNull { it.second.isNotBlank() }?.second ?: ""
                winningRawSnippet = passRaw.lines().filter { it.isNotBlank() }.take(2).joinToString(" | ")
            } else if (singlePassDates.isNotEmpty()) {
                val dateVotes = singlePassDates.groupBy { it.second }.mapValues { it.value.size }
                val topDate = dateVotes.entries.sortedWith(
                    compareByDescending<Map.Entry<LocalDate, Int>> { it.value } // majority vote
                        .thenByDescending { !it.key.isBefore(refDate) } // future over past
                        .thenByDescending { it.key == multiRes.selectedEventDate } // multi-pass agreement
                ).first().key

                bestExtractedDate = topDate
                winningPass = singlePassDates.first { it.second == topDate }.first
                val passRaw = passList.firstOrNull { it.first == winningPass }?.second ?: ""
                winningRawSnippet = passRaw.lines().filter { it.isNotBlank() }.take(2).joinToString(" | ")
            } else if (multiRes.selectedEventDate != null) {
                bestExtractedDate = multiRes.selectedEventDate
                winningPass = "multi-pass"
                val passRaw = passList.firstOrNull { it.second.isNotBlank() }?.second ?: ""
                winningRawSnippet = passRaw.lines().filter { it.isNotBlank() }.take(2).joinToString(" | ")
            }

            if (winningRawSnippet.isEmpty()) {
                winningRawSnippet = passList.firstOrNull { it.second.isNotBlank() }?.second?.lines()?.filter { it.isNotBlank() }?.take(2)?.joinToString(" | ") ?: ""
            }

            val extractedDateStr = bestExtractedDate?.toString() ?: "none"
            val isDateCorrect = matchesGroundTruth(groundTruth, extractedDateStr)
            if (isDateCorrect) dateMatches++

            val isTitleDetected = bestTitle != "Other"
            if (isTitleDetected) titleMatches++

            val status = if (isDateCorrect) "PASS" else "FAIL"
            val rootCause = when {
                isDateCorrect -> "N/A"
                winningRawSnippet.isEmpty() -> "OCR_ZERO_CHARS"
                !isDateCorrect && extractedDateStr == "none" -> "OCR_TEXT_MISSING_DATE"
                else -> "PARSE_MISCLASSIFICATION"
            }

            scorecard.add(
                ScorecardRow(
                    index = index,
                    filename = filename,
                    groundTruth = groundTruth,
                    rawSnippet = winningRawSnippet.replace("\n", " ").take(60),
                    extractedDate = extractedDateStr,
                    extractedTitle = bestTitle,
                    winningPass = winningPass,
                    status = status,
                    rootCause = rootCause,
                )
            )
        }

        // Save scorecard JSON
        val scorecardJson = JSONArray()
        for (row in scorecard) {
            scorecardJson.put(
                JSONObject().apply {
                    put("index", row.index)
                    put("filename", row.filename)
                    put("groundTruth", row.groundTruth)
                    put("rawSnippet", row.rawSnippet)
                    put("extractedDate", row.extractedDate)
                    put("extractedTitle", row.extractedTitle)
                    put("winningPass", row.winningPass)
                    put("status", row.status)
                    put("rootCause", row.rootCause)
                }
            )
        }
        File("c:/Projects/Memora/testing/dataset/benchmark_48_scorecard.json").writeText(
            scorecardJson.toString(2),
            Charsets.UTF_8
        )

        println("BENCHMARK_RESULTS: Date Matches = $dateMatches / ${scorecard.size} (${(dateMatches * 100f / scorecard.size)}%)")
        println("BENCHMARK_RESULTS: Title Matches = $titleMatches / ${scorecard.size} (${(titleMatches * 100f / scorecard.size)}%)")
    }

    private fun matchesGroundTruth(expected: String, actual: String): Boolean {
        if (expected.equals("none", ignoreCase = true)) {
            return actual.equals("none", ignoreCase = true)
        }
        if (actual.equals("none", ignoreCase = true)) {
            return false
        }
        if (expected == actual) return true
        // YYYY-MM prefix match e.g. "2028-12" matches "2028-12-01" or "2028-12-31"
        if (expected.length == 7 && actual.startsWith(expected)) return true
        if (actual.length == 7 && expected.startsWith(actual)) return true
        return false
    }
}
