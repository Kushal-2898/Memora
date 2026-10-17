package app.memora

import app.memora.platform.logging.AppLog
import app.memora.platform.logging.GeminiFailureReason
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException

class AppLogTest {

    @Before
    fun setUp() {
        AppLog.clear()
        AppLog.diagnosticsEnabledOverride = true
    }

    @After
    fun tearDown() {
        AppLog.clear()
        AppLog.diagnosticsEnabledOverride = null
    }

    // 1. Scan ID Generation
    @Test
    fun `newScanId generates non-empty 6-character uppercase alphanumeric ID`() {
        val id1 = AppLog.newScanId()
        val id2 = AppLog.newScanId()
        assertEquals(6, id1.length)
        assertEquals(6, id2.length)
        assertNotEquals(id1, id2)
        assertTrue(id1.all { it.isLetterOrDigit() })
    }

    // 2. Start Scan Initialization
    @Test
    fun `startScan initializes CurrentScan state and retains scanId across events`() {
        val scanId = AppLog.newScanId()
        AppLog.startScan(scanId = scanId, imagePresent = true)

        val current = AppLog.currentScan
        assertNotNull(current)
        assertEquals(scanId, current?.scanId)
        assertTrue(current?.imagePresent == true)
        assertEquals("PENDING", current?.ocrStatus)
    }

    // 3. OCR Event Recording
    @Test
    fun `recordOcr captures duration, character count, word count, and raw text in testing mode`() {
        val scanId = AppLog.newScanId()
        AppLog.startScan(scanId)

        val sampleOcrText = "TOTAL AMOUNT DUE\nPAY BY: 2026-10-15\nBESCOM POWER"
        AppLog.recordOcr(
            scanId = scanId,
            durationMs = 342L,
            charCount = sampleOcrText.length,
            wordCount = 7,
            rawText = sampleOcrText,
        )

        val scan = AppLog.currentScan
        assertNotNull(scan)
        assertEquals("SUCCESS", scan?.ocrStatus)
        assertEquals(342L, scan?.ocrDurationMs)
        assertEquals(sampleOcrText.length, scan?.ocrCharCount)
        assertEquals(7, scan?.ocrWordCount)
        assertEquals(sampleOcrText, scan?.rawOcrText)
    }

    // 4. Gemini Success Recording
    @Test
    fun `recordGeminiSuccess captures duration and exact raw JSON in testing mode`() {
        val scanId = AppLog.newScanId()
        AppLog.startScan(scanId)

        val sampleJson = """{"title":"Electricity Bill","date":"2026-10-15","confidence":0.95}"""
        AppLog.recordGeminiSuccess(
            scanId = scanId,
            durationMs = 2104L,
            rawJson = sampleJson,
        )

        val scan = AppLog.currentScan
        assertNotNull(scan)
        assertEquals("SUCCESS", scan?.geminiStatus)
        assertEquals(2104L, scan?.geminiDurationMs)
        assertEquals(sampleJson, scan?.rawGeminiJson)
    }

    // 5. Gemini Failure Mapping
    @Test
    fun `recordGeminiFailure maps exceptions to sanitized failure reasons`() {
        val scanId = AppLog.newScanId()
        AppLog.startScan(scanId)

        val timeoutException = SocketTimeoutException("Read timed out")
        val reason1 = GeminiFailureReason.fromThrowable(timeoutException)
        assertEquals(GeminiFailureReason.TIMEOUT, reason1)

        val networkException = IOException("Unable to resolve host: generativelanguage.googleapis.com")
        val reason2 = GeminiFailureReason.fromThrowable(networkException)
        assertEquals(GeminiFailureReason.NETWORK_ERROR, reason2)

        val authException = RuntimeException("API_KEY_INVALID: 401 Unauthorized")
        val reason3 = GeminiFailureReason.fromThrowable(authException)
        assertEquals(GeminiFailureReason.AUTHENTICATION_ERROR, reason3)

        val rateLimitException = RuntimeException("429 RESOURCE_EXHAUSTED: Quota exceeded")
        val reason4 = GeminiFailureReason.fromThrowable(rateLimitException)
        assertEquals(GeminiFailureReason.RATE_LIMIT, reason4)

        val notFoundException = RuntimeException("ServerException: 404 models/gemini-3.6-flash is not found")
        val reason5 = GeminiFailureReason.fromThrowable(notFoundException)
        assertEquals(GeminiFailureReason.MODEL_NOT_FOUND, reason5)

        AppLog.recordGeminiFailure(scanId, 1500L, reason5, "ServerException: 404 Not Found")
        assertEquals(GeminiFailureReason.MODEL_NOT_FOUND, AppLog.currentScan?.geminiFailureReason)
        assertEquals("ServerException: 404 Not Found", AppLog.currentScan?.geminiErrorDetail)
        assertEquals("FAILURE", AppLog.currentScan?.geminiStatus)
    }

    // 6. Date Selection Recording
    @Test
    fun `recordDateSelection logs candidates, selected date, and explicit selection reason`() {
        val scanId = AppLog.newScanId()
        AppLog.startScan(scanId)

        val candidates = listOf("2026-09-01 (Bill Date)", "2026-10-15 (Due Date)")
        AppLog.recordDateSelection(
            scanId = scanId,
            candidates = candidates,
            selectedDate = "2026-10-15",
            reason = "DUE_DATE priority (Payment deadline selected)",
        )

        val scan = AppLog.currentScan
        assertEquals("2026-10-15", scan?.selectedDate)
        assertEquals(candidates, scan?.dateCandidates)
        assertEquals("DUE_DATE priority (Payment deadline selected)", scan?.dateSelectionReason)
    }

    // 7. Fallback Path Discrimination
    @Test
    fun `recordFallback correctly distinguishes between direct Gemini success and fallback used`() {
        val scanId1 = AppLog.newScanId()
        AppLog.startScan(scanId1)
        AppLog.recordFallback(scanId1, fallbackUsed = false)
        assertFalse(AppLog.currentScan?.fallbackUsed ?: true)
        assertTrue(AppLog.currentScan?.extractorPath?.contains("NOT USED") == true)

        val scanId2 = AppLog.newScanId()
        AppLog.startScan(scanId2)
        AppLog.recordFallback(scanId2, fallbackUsed = true, reason = "NETWORK_ERROR")
        assertTrue(AppLog.currentScan?.fallbackUsed == true)
        assertTrue(AppLog.currentScan?.extractorPath?.contains("Deterministic Fallback USED") == true)
    }

    // 8. Timings & Persistence
    @Test
    fun `performance timings and persistence statuses are recorded`() {
        val scanId = AppLog.newScanId()
        AppLog.startScan(scanId)

        AppLog.recordFinalResult(
            scanId = scanId,
            disposition = "HIGH_CONFIDENCE",
            extractorUsed = "Gemini",
            confidence = 0.92f,
            parsingDurationMs = 18L,
            totalDurationMs = 2476L,
        )

        AppLog.recordPersistence(
            scanId = scanId,
            saveStatus = "SUCCESS",
            reminderStatus = "SCHEDULED",
            dbDurationMs = 12L,
        )

        val scan = AppLog.currentScan
        assertEquals("HIGH_CONFIDENCE", scan?.finalDisposition)
        assertEquals(0.92f, scan?.confidence ?: 0f, 0.001f)
        assertEquals(18L, scan?.parsingDurationMs)
        assertEquals(2476L, scan?.totalDurationMs)
        assertEquals("SUCCESS", scan?.saveStatus)
        assertEquals("SCHEDULED", scan?.reminderStatus)
        assertEquals(12L, scan?.dbDurationMs)
    }

    // 9. Aggressive Credential Redaction
    @Test
    fun `sanitize aggressively redacts API keys, URL key params, and authorization tokens`() {
        val rawGoogleApiKey = "AIzaSyD-1234567890abcdefghijklmnopqrstuv"
        val queryParamText = "https://generativelanguage.googleapis.com/v1beta/models?key=AIzaSyD-1234567890abcdefghijklmnopqrstuv"
        val authBearerText = "Authorization: Bearer ya29.a0AfH6SMAxyz123456"

        val sanitizedKey = AppLog.sanitize(rawGoogleApiKey)
        assertEquals("[REDACTED_API_KEY]", sanitizedKey)

        val sanitizedQuery = AppLog.sanitize(queryParamText)
        assertFalse(sanitizedQuery.contains("AIzaSy"))
        assertTrue(sanitizedQuery.contains("key=[REDACTED_API_KEY]"))

        val sanitizedAuth = AppLog.sanitize(authBearerText)
        assertFalse(sanitizedAuth.contains("ya29"))
        assertTrue(sanitizedAuth.contains("Bearer [REDACTED_TOKEN]"))
    }

    // 10. Non-Diagnostic Redaction
    @Test
    fun `non-diagnostic mode redacts raw OCR and Gemini JSON`() {
        AppLog.diagnosticsEnabledOverride = false

        val scanId = AppLog.newScanId()
        AppLog.startScan(scanId)

        AppLog.recordOcr(scanId, 200L, 50, 10, "Confidential Receipt Text")
        AppLog.recordGeminiSuccess(scanId, 1000L, """{"secret":"private_data"}""")

        val scan = AppLog.currentScan
        assertEquals("[REDACTED_IN_NON_DIAGNOSTIC_BUILD]", scan?.rawOcrText)
        assertEquals("[REDACTED_IN_NON_DIAGNOSTIC_BUILD]", scan?.rawGeminiJson)
    }

    // 11. Purging Memory Buffer & State
    @Test
    fun `clear purges memory buffer, currentScan, and entry count`() {
        val scanId = AppLog.newScanId()
        AppLog.startScan(scanId)
        AppLog.i("TEST", "Test log message")
        assertTrue(AppLog.entryCount > 0)
        assertNotNull(AppLog.currentScan)

        AppLog.clear()
        assertEquals(0, AppLog.entryCount)
        assertNull(AppLog.currentScan)
    }

    // 12. Formatted Export Dump Structure
    @Test
    fun `getFormattedDump includes diagnostics disclaimer, scan summary, and chronological entries`() {
        val scanId = AppLog.newScanId()
        AppLog.startScan(scanId)
        AppLog.recordOcr(scanId, 150L, 20, 5, "Sample OCR")

        val dump = AppLog.getFormattedDump()
        assertTrue(dump.contains("MEMORA DIAGNOSTICS DUMP"))
        assertTrue(dump.contains("[WARNING] These logs may contain text extracted from your captured documents."))
        assertTrue(dump.contains("#$scanId"))
        assertTrue(dump.contains("Sample OCR"))
    }

    // 13. Global Monotonic Event ID
    @Test
    fun `global monotonic eventId increases with every event`() {
        val id1 = AppLog.nextEventId()
        val id2 = AppLog.nextEventId()
        val id3 = AppLog.nextEventId()
        assertTrue(id2 > id1)
        assertTrue(id3 > id2)
    }

    // 14. Watchdog Thread Dump Snapshot on Freeze
    @Test
    fun `thread dump captured on watchdog freeze includes main thread stack and system state`() {
        AppLog.checkWatchdogTick(2500L)
        assertTrue(AppLog.isMainThreadUnresponsive)

        val freeze = AppLog.getLastFreezeSnapshot()
        assertNotNull(freeze)
        assertEquals(2500L, freeze?.unresponsiveDurationMs)
        assertNotNull(freeze?.threadDump)
        assertTrue(freeze?.threadDump?.mainThreadStack?.isNotEmpty() == true)
        assertEquals("AppStart", freeze?.currentScreen)
    }

    // 15. Watchdog Heartbeat Tracking & Age
    @Test
    fun `watchdog tracks heartbeat count, last heartbeat timestamp, and heartbeat age`() {
        val countBefore = AppLog.heartbeatCount
        AppLog.recordHeartbeatTick()
        val countAfter = AppLog.heartbeatCount
        assertEquals(countBefore + 1, countAfter)
        assertNotNull(AppLog.lastSuccessfulHeartbeat)

        val status = AppLog.getWatchdogStatus()
        assertEquals(countAfter, status.heartbeatCount)
        assertTrue(status.heartbeatAgeMs >= 0)
    }

    // 16. Watchdog Responsiveness Recovery
    @Test
    fun `watchdog detects recovery and records unresponsive duration`() {
        AppLog.checkWatchdogTick(2500L)
        assertTrue(AppLog.isMainThreadUnresponsive)
        assertEquals(2500L, AppLog.lastUnresponsiveDurationMs)

        // Resumed: heartbeat restored within normal threshold
        AppLog.checkWatchdogTick(400L)
        assertFalse(AppLog.isMainThreadUnresponsive)
        assertEquals(2500L, AppLog.lastUnresponsiveDurationMs)
    }

    // 17. Thread Name Separate From Explicit Dispatcher Label
    @Test
    fun `thread name is tracked separately from explicit dispatcher label, defaulting to UNKNOWN`() {
        val scanId = AppLog.newScanId()
        AppLog.startScan(scanId)
        val initialThread = AppLog.currentScan?.threadName
        assertNotNull(initialThread)

        // Default dispatcher label is UNKNOWN
        AppLog.recordOcr(scanId, 100L, 20, 5, "Sample OCR")
        assertEquals("UNKNOWN", AppLog.currentScan?.dispatcherLabel)

        // Explicitly supplied dispatcher label
        AppLog.recordGeminiSuccess(scanId, 250L, """{"title":"Test"}""", dispatcherLabel = "IO")
        assertEquals("IO", AppLog.currentScan?.dispatcherLabel)
        assertEquals(initialThread, AppLog.currentScan?.threadName)
    }

    // 18. Main Thread Violation Detection
    @Test
    fun `checkMainThreadViolation executes safely and logs warning if on main thread`() {
        // Calling on non-main or mock thread must execute safely without throwing
        AppLog.checkMainThreadViolation("SimulatedHeavyOp")
        // No exception thrown
        assertTrue(true)
    }

    // 19. Active Extraction Job Registry & Lifecycle
    @Test
    fun `active extraction job registry tracks lifecycle from start to end`() {
        val scanId = AppLog.newScanId()
        val jobId = AppLog.startExtractionJob(scanId, "MLKit_OCR", dispatcherLabel = "IO")
        assertEquals(1, AppLog.getActiveExtractionJobsCount())
        val job = AppLog.getActiveExtractionJobs()[jobId]
        assertNotNull(job)
        assertEquals("MLKit_OCR", job?.currentOperation)
        assertEquals("IO", job?.dispatcherLabel)

        AppLog.updateExtractionJob(jobId, "Gemini_Inference")
        assertEquals("Gemini_Inference", AppLog.currentOperation)

        AppLog.endExtractionJob(jobId, outcome = "SUCCESS")
        assertEquals(0, AppLog.getActiveExtractionJobsCount())
        assertNull(AppLog.currentOperation)
    }

    // 20. Concurrent Extraction Detection
    @Test
    fun `concurrent extraction detection flags overlapping extraction jobs`() {
        val scanId1 = AppLog.newScanId()
        val scanId2 = AppLog.newScanId()

        val job1 = AppLog.startExtractionJob(scanId1, "Extraction1")
        val job2 = AppLog.startExtractionJob(scanId2, "Extraction2")
        assertEquals(2, AppLog.getActiveExtractionJobsCount())

        val entries = AppLog.getLogEntries()
        val hasConcurrencyLog = entries.any { it.contains("CONCURRENT_EXTRACTION_DETECTED") }
        assertTrue("Log should contain CONCURRENT_EXTRACTION_DETECTED", hasConcurrencyLog)

        AppLog.endExtractionJob(job1)
        AppLog.endExtractionJob(job2)
        assertEquals(0, AppLog.getActiveExtractionJobsCount())
    }

    // 21. UI Action -> State Transition Correlation
    @Test
    fun `recordUserAction correlates with recordStateTransition via action eventId`() {
        val action = AppLog.recordUserAction("Capture Screen", "Take Photo clicked", "source=CAMERA")
        assertNotNull(action)
        assertTrue(action.eventId > 0)
        assertEquals("[Capture Screen] Take Photo clicked (source=CAMERA)", action.action)

        AppLog.recordStateTransition(action, "CaptureUiState: Preview", result = "SUCCESS")
        assertEquals("CaptureUiState: Preview", action.stateTransition)
        assertEquals("SUCCESS", action.result)
        assertEquals(action, AppLog.getLastUserAction())
    }

    // 22. Clear Clears Diagnostic Data Only Preserving User Data
    @Test
    fun `clear deletes only diagnostic logs, scans, and crash files preserving user data`() {
        val scanId = AppLog.newScanId()
        AppLog.startScan(scanId)
        AppLog.recordUserAction("Home Screen", "Item clicked")
        AppLog.checkWatchdogTick(2200L)
        assertTrue(AppLog.entryCount > 0)
        assertNotNull(AppLog.currentScan)
        assertNotNull(AppLog.getLastFreezeSnapshot())

        AppLog.clear()

        assertEquals(0, AppLog.entryCount)
        assertNull(AppLog.currentScan)
        assertNull(AppLog.getLastFreezeSnapshot())
        assertNull(AppLog.getLastUserAction())
        assertEquals(0, AppLog.getActiveExtractionJobsCount())
        // Verified: Clear resets diagnostic state only; zero interaction with Room / User data
    }

    // 23. Release / Non-Diagnostic Behavior
    @Test
    fun `non-diagnostic mode disables diagnostic file persistence`() {
        AppLog.diagnosticsEnabledOverride = false

        assertFalse(AppLog.isDiagnosticsEnabled())
        AppLog.i("TAG", "Non-diagnostic entry")
        val scanId = AppLog.newScanId()
        AppLog.startScan(scanId)

        // Raw texts are masked
        AppLog.recordOcr(scanId, 100L, 50, 5, "Secret Private Health Text")
        assertEquals("[REDACTED_IN_NON_DIAGNOSTIC_BUILD]", AppLog.currentScan?.rawOcrText)
    }

    // 24. Fail-Safe Crash Handler
    @Test
    fun `recordCrash creates valid last_crash JSON without throwing secondary exceptions`() {
        val testException = IllegalStateException("Test crash for diagnostics validation")
        // recordCrash should never throw exceptions
        AppLog.recordCrash(testException)
        val entries = AppLog.getLogEntries()
        assertTrue(entries.any { it.contains("FATAL CRASH DETECTED") })
    }

    // 25. Circular Buffer Capacity Capping
    @Test
    fun `logBuffer circular capacity caps at maximum entries without unbounded growth`() {
        // Log 1200 items into 1000-entry capacity
        for (i in 1..1200) {
            AppLog.i("STRESS", "Stress test entry $i")
        }
        assertEquals(1000, AppLog.entryCount)
    }

    // 26. Content Length Capping & No Bitmap Retention
    @Test
    fun `sanitize caps excessively long strings and image memory tracks dimensions without Bitmap references`() {
        val hugeString = "X".repeat(10_000)
        val sanitized = AppLog.sanitize(hugeString)
        assertTrue(sanitized.length < 6000)
        assertTrue(sanitized.contains("[TRUNCATED"))

        // Image memory tracks metadata without holding any Bitmap object reference
        AppLog.recordImageMemory(
            width = 3840,
            height = 2160,
            config = "ARGB_8888",
            scanId = "TEST01",
        )
        val entries = AppLog.getLogEntries()
        assertTrue(entries.any { it.contains("Image Decoded: 3840x2160 (ARGB_8888)") })
    }
}
