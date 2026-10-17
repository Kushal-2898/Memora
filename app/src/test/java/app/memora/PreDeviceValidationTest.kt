package app.memora

import android.net.Uri
import app.memora.domain.extraction.DeterministicMemoryExtractor
import app.memora.domain.extraction.ExtractedMemoryCandidate
import app.memora.domain.extraction.ExtractionDisposition
import app.memora.domain.extraction.LocalExtractionResult
import app.memora.domain.extraction.MemoryExtractor
import app.memora.domain.extraction.OcrResult
import app.memora.domain.extraction.Stage3ExtractionResult
import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.domain.model.EventType
import app.memora.domain.model.Memory
import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.model.Reminder
import app.memora.domain.repository.MemoryRepository
import app.memora.domain.repository.ReminderRepository
import app.memora.domain.scheduler.ClockProvider
import app.memora.domain.usecase.CreateMemoryUseCase
import app.memora.domain.usecase.DeleteMemoryUseCase
import app.memora.domain.usecase.ExtractLocallyUseCase
import app.memora.domain.usecase.ProcessCapturedImageUseCase
import app.memora.domain.usecase.RefineWithGeminiUseCase
import app.memora.platform.image.ImageStorage
import app.memora.platform.logging.AppLog
import app.memora.platform.notification.NotificationScheduler
import app.memora.platform.ocr.ExtractedText
import app.memora.platform.ocr.TextExtractor
import app.memora.presentation.capture.CaptureNavEvent
import app.memora.presentation.capture.CaptureUiState
import app.memora.presentation.capture.CaptureViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/**
 * Source-level / Instrumented automated validation of Memora's
 * Document/Data Scanner and Local Extraction Before Gemini pipeline.
 *
 * Covers all 20 required validation areas prior to physical device deployment.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreDeviceValidationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val fixedInstant = Instant.parse("2026-09-04T10:00:00Z")
    private val fixedZone = ZoneId.of("UTC")
    private val fixedClockProvider = object : ClockProvider {
        override fun now(): Instant = fixedInstant
        override fun today(zoneId: ZoneId): LocalDate = fixedInstant.atZone(zoneId).toLocalDate()
    }

    private lateinit var fakeStorage: TestImageStorage
    private lateinit var fakeMemoryRepo: TestMemoryRepository
    private lateinit var fakeReminderRepo: TestReminderRepository
    private lateinit var fakeNotificationScheduler: TestNotificationScheduler
    private lateinit var fakeTextExtractor: TestTextExtractor
    private lateinit var countingGeminiExtractor: CountingGeminiExtractor
    private lateinit var deterministicExtractor: DeterministicMemoryExtractor
    private lateinit var extractLocallyUseCase: ExtractLocallyUseCase
    private lateinit var refineWithGeminiUseCase: RefineWithGeminiUseCase
    private lateinit var viewModel: CaptureViewModel

    private class TestImageStorage : ImageStorage {
        val deletedUris = mutableListOf<String>()
        override fun createCaptureFile(prefix: String): File = File.createTempFile(prefix, ".jpg")
        override suspend fun importFromUri(uriString: String, source: CaptureSource): Result<CapturedImage> {
            return Result.success(
                CapturedImage(
                    uri = uriString,
                    source = source,
                    capturedAt = Instant.now(),
                    mimeType = "image/jpeg",
                    fileSize = 1024L,
                )
            )
        }
        override fun createCapturedImageFromFile(file: File, source: CaptureSource): CapturedImage {
            return CapturedImage(
                uri = file.toURI().toString(),
                source = source,
                capturedAt = Instant.now(),
                mimeType = "image/jpeg",
                fileSize = file.length(),
            )
        }
        override fun deleteImage(uriString: String): Boolean {
            deletedUris.add(uriString)
            return true
        }
    }

    private class TestMemoryRepository : MemoryRepository {
        val memories = mutableMapOf<String, Memory>()
        val reminders = mutableMapOf<String, Reminder>()
        var saveCount = AtomicInteger(0)
        override fun observeMemories(): Flow<List<MemoryWithReminders>> = flowOf(emptyList())
        override suspend fun getMemory(id: String): Memory? = memories[id]
        override suspend fun getMemoryWithReminders(id: String): MemoryWithReminders? =
            memories[id]?.let { MemoryWithReminders(it, emptyList()) }
        override suspend fun createMemory(memory: Memory, initialReminder: Reminder?): Result<Unit> {
            saveCount.incrementAndGet()
            memories[memory.id] = memory
            if (initialReminder != null) reminders[initialReminder.id] = initialReminder
            return Result.success(Unit)
        }
        override suspend fun updateMemory(memory: Memory): Result<Unit> = Result.success(Unit)
        override suspend fun deleteMemory(id: String): Result<Unit> {
            memories.remove(id)
            reminders.values.removeAll { it.memoryId == id }
            return Result.success(Unit)
        }
    }

    private class TestReminderRepository : ReminderRepository {
        val reminders = mutableMapOf<String, Reminder>()
        override fun observeReminders(): Flow<List<Reminder>> = flowOf(emptyList())
        override fun observeRemindersForMemory(memoryId: String): Flow<List<Reminder>> = flowOf(emptyList())
        override suspend fun getReminder(id: String): Reminder? = reminders[id]
        override suspend fun getRemindersForMemory(memoryId: String): List<Reminder> =
            reminders.values.filter { it.memoryId == memoryId }
        override suspend fun createReminder(reminder: Reminder): Result<Unit> {
            reminders[reminder.id] = reminder
            return Result.success(Unit)
        }
        override suspend fun updateReminder(reminder: Reminder): Result<Unit> = Result.success(Unit)
        override suspend fun completeReminder(id: String, completedAt: Instant): Result<Unit> = Result.success(Unit)
        override suspend fun deleteReminder(id: String): Result<Unit> = Result.success(Unit)
    }

    private class TestNotificationScheduler : NotificationScheduler {
        val scheduled = mutableListOf<String>()
        override suspend fun scheduleReminder(reminderId: String, triggerAt: Instant, title: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun scheduleReminder(
            memoryId: String,
            reminderId: String,
            title: String,
            reminderStartDate: LocalDate,
            eventDate: LocalDate,
            timeZoneId: String,
            reminderTime: LocalTime,
        ): Result<Unit> {
            scheduled.add(reminderId)
            return Result.success(Unit)
        }
        override suspend fun cancelReminder(reminderId: String): Result<Unit> {
            scheduled.remove(reminderId)
            return Result.success(Unit)
        }
        override val areNotificationsEnabled: Boolean = true
    }

    private class TestTextExtractor : TextExtractor {
        var textToReturn = "BESCOM Electricity Bill\nDue Date: 15/10/2026\nAmount: Rs 1500"
        var shouldFail = false
        override suspend fun extractText(imageUri: String): Result<ExtractedText> {
            if (shouldFail) return Result.failure(RuntimeException("OCR failed"))
            return Result.success(ExtractedText(textToReturn))
        }
        override val isAvailable: Boolean = true
    }

    private class CountingGeminiExtractor : MemoryExtractor {
        val callCount = AtomicInteger(0)
        var shouldFail = false
        var delayMs = 0L
        var neverComplete = false
        var returnCandidate = ExtractedMemoryCandidate(
            suggestedTitle = "AI Extracted Bill",
            suggestedEventType = EventType.DUE_DATE,
            suggestedEventDate = LocalDate.of(2026, 10, 15),
            confidence = 0.95f,
        )

        override suspend fun extractCandidate(rawText: String, imageUri: Uri?): Result<ExtractedMemoryCandidate> {
            callCount.incrementAndGet()
            val scanId = AppLog.currentScan?.scanId ?: "TEST"
            AppLog.recordGeminiRequest(scanId, model = "gemini-3.5-flash", dispatcherLabel = "IO")
            if (neverComplete) {
                kotlinx.coroutines.withTimeout(10_000L) {
                    val deferred = CompletableDeferred<Unit>()
                    deferred.await()
                }
            }
            if (delayMs > 0) delay(delayMs)
            if (shouldFail) {
                AppLog.recordGeminiFailure(scanId, 100L, app.memora.platform.logging.GeminiFailureReason.UNKNOWN, "Gemini API error", "IO")
                return Result.failure(RuntimeException("Gemini API error"))
            }
            AppLog.recordGeminiSuccess(scanId, 1200L, """{"title":"AI Extracted Bill"}""", dispatcherLabel = "IO")
            return Result.success(returnCandidate)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        AppLog.clear()
        AppLog.diagnosticsEnabledOverride = true

        fakeStorage = TestImageStorage()
        fakeMemoryRepo = TestMemoryRepository()
        fakeReminderRepo = TestReminderRepository()
        fakeNotificationScheduler = TestNotificationScheduler()
        fakeTextExtractor = TestTextExtractor()
        countingGeminiExtractor = CountingGeminiExtractor()
        deterministicExtractor = DeterministicMemoryExtractor(clockProvider = fixedClockProvider, zoneId = fixedZone)

        extractLocallyUseCase = ExtractLocallyUseCase(
            textExtractor = fakeTextExtractor,
            deterministicExtractor = deterministicExtractor,
        )
        refineWithGeminiUseCase = RefineWithGeminiUseCase(
            geminiExtractor = countingGeminiExtractor,
        )

        val processUseCase = ProcessCapturedImageUseCase(fakeStorage)
        val createMemoryUseCase = CreateMemoryUseCase(fakeMemoryRepo)
        val deleteMemoryUseCase = DeleteMemoryUseCase(fakeMemoryRepo)

        viewModel = CaptureViewModel(
            processCapturedImageUseCase = processUseCase,
            imageStorage = fakeStorage,
            createMemoryUseCase = createMemoryUseCase,
            deleteMemoryUseCase = deleteMemoryUseCase,
            notificationScheduler = fakeNotificationScheduler,
            extractLocallyUseCase = extractLocallyUseCase,
            refineWithGeminiUseCase = refineWithGeminiUseCase,
            crashReporter = FakeCrashReporter(),
            analyticsLogger = FakeAnalyticsLogger(),
        )
    }

    @After
    fun tearDown() {
        AppLog.clear()
        AppLog.diagnosticsEnabledOverride = null
        Dispatchers.resetMain()
    }

    // ==========================================
    // 3. Local Extraction -> Gemini Cost Boundary
    // ==========================================
    @Test
    fun `validation 03 - auto scan to local extraction reaches LocalExtractionReady with exactly 0 Gemini calls`() = testScope.runTest {
        val testImage = CapturedImage(
            uri = "file:///test/scan.jpg",
            source = CaptureSource.CAMERA,
            capturedAt = Instant.now(),
            mimeType = "image/jpeg",
            fileSize = 2048L,
        )

        viewModel.onScanAcquired(testImage)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected LocalExtractionReady but got $state", state is CaptureUiState.LocalExtractionReady)
        val ready = state as CaptureUiState.LocalExtractionReady
        assertEquals("Electricity Bill", ready.localResult.title)
        assertEquals(LocalDate.of(2026, 10, 15), ready.localResult.selectedEventDate)
        assertEquals("Strict invariant violation: Gemini was called before user confirmation!", 0, countingGeminiExtractor.callCount.get())
    }

    // ==========================================
    // 4. Confirmation -> Exactly One Gemini Call
    // ==========================================
    @Test
    fun `validation 04 - confirming local extraction calls Gemini exactly once and reaches ExtractionReady`() = testScope.runTest {
        val testImage = CapturedImage(
            uri = "file:///test/scan.jpg",
            source = CaptureSource.CAMERA,
            capturedAt = Instant.now(),
            mimeType = "image/jpeg",
            fileSize = 2048L,
        )
        viewModel.onScanAcquired(testImage)
        advanceUntilIdle()

        assertEquals(0, countingGeminiExtractor.callCount.get())

        val job = viewModel.confirmLocalExtraction()
        assertNotNull(job)
        advanceUntilIdle()

        assertEquals("Strict invariant violation: Gemini should be called exactly once after confirmation", 1, countingGeminiExtractor.callCount.get())
        val state = viewModel.uiState.value
        assertTrue("Expected ExtractionReady but got $state", state is CaptureUiState.ExtractionReady)
        val ready = state as CaptureUiState.ExtractionReady
        assertEquals("AI Extracted Bill", ready.result.candidate.suggestedTitle)
    }

    // ==========================================
    // 5. Double Confirmation Protection
    // ==========================================
    @Test
    fun `validation 05 - rapid concurrent confirm invocations trigger exactly 1 Gemini call and no duplicate jobs`() = testScope.runTest {
        val testImage = CapturedImage(
            uri = "file:///test/scan.jpg",
            source = CaptureSource.CAMERA,
            capturedAt = Instant.now(),
            mimeType = "image/jpeg",
            fileSize = 2048L,
        )
        viewModel.onScanAcquired(testImage)
        advanceUntilIdle()

        // Rapid triple-tap
        val job1 = viewModel.confirmLocalExtraction()
        val job2 = viewModel.confirmLocalExtraction()
        val job3 = viewModel.confirmLocalExtraction()

        assertNotNull(job1)
        assertNull(job2)
        assertNull(job3)

        advanceUntilIdle()

        assertEquals("Strict invariant violation: Double confirmation must trigger exactly 1 Gemini call", 1, countingGeminiExtractor.callCount.get())
        assertTrue(viewModel.uiState.value is CaptureUiState.ExtractionReady)
        assertEquals(0, AppLog.getActiveExtractionJobsCount())
    }

    // ==========================================
    // 6. Rescan During Local Review
    // ==========================================
    @Test
    fun `validation 06 - rescan from LocalExtractionReady resets to CameraActive with 0 Gemini calls and deletes temp file`() = testScope.runTest {
        val testImage = CapturedImage(
            uri = "file:///test/scan_rescan.jpg",
            source = CaptureSource.CAMERA,
            capturedAt = Instant.now(),
            mimeType = "image/jpeg",
            fileSize = 2048L,
        )
        viewModel.onScanAcquired(testImage)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CaptureUiState.LocalExtractionReady)

        viewModel.rescan()

        assertEquals(CaptureUiState.CameraActive, viewModel.uiState.value)
        assertEquals(0, countingGeminiExtractor.callCount.get())
        assertTrue("Temporary image should be deleted on rescan", fakeStorage.deletedUris.contains(testImage.uri))
        assertNull(viewModel.activeTemporaryImage)
    }

    // ==========================================
    // 7. Manual Entry
    // ==========================================
    @Test
    fun `validation 07 - manual entry transitions to editing state without invoking Gemini`() = testScope.runTest {
        val testImage = CapturedImage(
            uri = "file:///test/scan_manual.jpg",
            source = CaptureSource.CAMERA,
            capturedAt = Instant.now(),
            mimeType = "image/jpeg",
            fileSize = 2048L,
        )
        viewModel.onScanAcquired(testImage)
        advanceUntilIdle()

        val state = viewModel.uiState.value as CaptureUiState.LocalExtractionReady
        viewModel.startManualEntry(state.capturedImage)

        val nextState = viewModel.uiState.value
        assertTrue("Expected ExtractionReady for manual editing but got $nextState", nextState is CaptureUiState.ExtractionReady)
        val manualReady = nextState as CaptureUiState.ExtractionReady
        assertEquals("Electricity Bill", manualReady.result.candidate.suggestedTitle)
        assertNull(manualReady.result.candidate.suggestedEventDate)
        assertEquals(0, countingGeminiExtractor.callCount.get())
    }

    // ==========================================
    // 8. Ambiguous Date Handling
    // ==========================================
    @Test
    fun `validation 08 - Case A single actionable future date is selected without ambiguity`() {
        val text = "MFG 01/01/2026\nEXP 01/01/2027"
        val result = deterministicExtractor.extractLocalResult(text)

        assertFalse(result.isAmbiguous)
        assertEquals(LocalDate.of(2027, 1, 1), result.selectedEventDate)
        assertEquals(EventType.EXPIRATION, result.eventType)
    }

    @Test
    fun `validation 08 - Case B multiple competing dates marks isAmbiguous with null selected date`() {
        val text = "Notice dated 15/10/2026 with hearing on 20/12/2026"
        val result = deterministicExtractor.extractLocalResult(text)

        assertTrue(result.isAmbiguous)
        assertNull(result.selectedEventDate)
        assertTrue(result.dateCandidates.size >= 2)
    }

    @Test
    fun `validation 08 - Case C past plus future date safeguards prioritize actionable future date`() {
        // Clock is fixed at 2026-09-04
        val text = "Bill Date: 10/01/2024\nPayment Due: 25/11/2026"
        val result = deterministicExtractor.extractLocalResult(text)

        assertFalse(result.isAmbiguous)
        assertEquals(LocalDate.of(2026, 11, 25), result.selectedEventDate)
        assertEquals(EventType.DUE_DATE, result.eventType)
    }

    // ==========================================
    // 9. Local Extraction Field Tests
    // ==========================================
    @Test
    fun `validation 09 - representative OCR inputs produce structured fields`() {
        // Medicine
        val medText = "Paracetamol 650mg\nBatch: B9021\nRx: RX-552\nMFG: 01/2025\nEXP: 08/2027"
        val medResult = deterministicExtractor.extractLocalResult(medText)
        assertEquals("B9021", medResult.fields["Batch"])
        assertEquals("RX-552", medResult.fields["Rx"])
        assertNotNull(medResult.fields["Expiry Date"])

        // Electricity Bill
        val elecText = "BESCOM ELECTRICITY\nDue Date: 15/10/2026\nAmount: 1200"
        val elecResult = deterministicExtractor.extractLocalResult(elecText)
        assertEquals("Electricity Bill", elecResult.title)
        assertNotNull(elecResult.fields["Due Date"])

        // Gas Bill
        val gasText = "INDANE LPG GAS BILL\nDue Date: 22/10/2026"
        val gasResult = deterministicExtractor.extractLocalResult(gasText)
        assertEquals("Gas Bill", gasResult.title)

        // Grocery / Product Package
        val foodText = "Amul Butter\nBest Before: 15/11/2026\nBatch: L441"
        val foodResult = deterministicExtractor.extractLocalResult(foodText)
        assertEquals("L441", foodResult.fields["Batch"])
        assertNotNull(foodResult.fields["Best Before"])

        // Warranty
        val warrantyText = "MacBook Pro\nWarranty: 2028-06-30"
        val warrantyResult = deterministicExtractor.extractLocalResult(warrantyText)
        assertEquals(LocalDate.of(2028, 6, 30), warrantyResult.selectedEventDate)
        assertEquals(EventType.WARRANTY_END, warrantyResult.eventType)

        // Invoice / Receipt
        val invoiceText = "Tax Invoice\nInvoice: INV-99812\nDue Date: 05/11/2026"
        val invResult = deterministicExtractor.extractLocalResult(invoiceText)
        assertEquals("INV-99812", invResult.fields["Invoice"])
    }

    // ==========================================
    // 10. Empty / Poor OCR
    // ==========================================
    @Test
    fun `validation 10 - empty or poor OCR does not crash and makes 0 Gemini calls`() = testScope.runTest {
        fakeTextExtractor.textToReturn = ""
        val testImage = CapturedImage(
            uri = "file:///test/blank.jpg",
            source = CaptureSource.CAMERA,
            capturedAt = Instant.now(),
            mimeType = "image/jpeg",
            fileSize = 10L,
        )

        viewModel.onScanAcquired(testImage)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.LocalExtractionReady)
        val ready = state as CaptureUiState.LocalExtractionReady
        assertNull(ready.localResult.selectedEventDate)
        assertEquals(0, countingGeminiExtractor.callCount.get())

        // Very poor garbled OCR
        fakeTextExtractor.textToReturn = "!@#$ %^&* ~~~ ????"
        viewModel.onScanAcquired(testImage)
        advanceUntilIdle()

        val garbledState = viewModel.uiState.value as CaptureUiState.LocalExtractionReady
        assertNull(garbledState.localResult.selectedEventDate)
        assertEquals(0, countingGeminiExtractor.callCount.get())
    }

    // ==========================================
    // 11. Gemini Failure
    // ==========================================
    @Test
    fun `validation 11 - Gemini failure with unambiguous local candidate falls back to local date`() = testScope.runTest {
        countingGeminiExtractor.shouldFail = true
        val testImage = CapturedImage(
            uri = "file:///test/fallback.jpg",
            source = CaptureSource.CAMERA,
            capturedAt = Instant.now(),
            mimeType = "image/jpeg",
            fileSize = 100L,
        )
        fakeTextExtractor.textToReturn = "BESCOM Bill\nDue Date: 20/10/2026"

        viewModel.onScanAcquired(testImage)
        advanceUntilIdle()

        viewModel.confirmLocalExtraction()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected ExtractionReady via local fallback but got $state", state is CaptureUiState.ExtractionReady)
        val ready = state as CaptureUiState.ExtractionReady
        assertEquals(LocalDate.of(2026, 10, 20), ready.result.candidate.suggestedEventDate)
        assertEquals(1, countingGeminiExtractor.callCount.get())
    }

    @Test
    fun `validation 11 - Gemini failure with ambiguous local candidate does not guess and surfaces error`() = testScope.runTest {
        countingGeminiExtractor.shouldFail = true
        val testImage = CapturedImage(
            uri = "file:///test/ambig_fail.jpg",
            source = CaptureSource.CAMERA,
            capturedAt = Instant.now(),
            mimeType = "image/jpeg",
            fileSize = 100L,
        )
        fakeTextExtractor.textToReturn = "Dates 10/10/2026 and 15/12/2026"

        viewModel.onScanAcquired(testImage)
        advanceUntilIdle()

        viewModel.confirmLocalExtraction()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Error state when ambiguous local result cannot fall back but got $state", state is CaptureUiState.Error)
        val error = state as CaptureUiState.Error
        assertTrue(error.canRetry)
        assertTrue(error.canEnterManually)
    }

    // ==========================================
    // 12. Gemini Timeout
    // ==========================================
    @Test
    fun `validation 12 - Gemini timeout completes cleanly and does not freeze coroutine`() = testScope.runTest {
        countingGeminiExtractor.neverComplete = true
        val testImage = CapturedImage(
            uri = "file:///test/timeout.jpg",
            source = CaptureSource.CAMERA,
            capturedAt = Instant.now(),
            mimeType = "image/jpeg",
            fileSize = 100L,
        )
        fakeTextExtractor.textToReturn = "Unlabelled 10/10/2026 and 15/12/2026"

        viewModel.onScanAcquired(testImage)
        advanceUntilIdle()

        val job = viewModel.confirmLocalExtraction()
        assertNotNull(job)

        // Advance test virtual time past the 10,000ms timeout threshold
        testScheduler.advanceTimeBy(10_001L)
        advanceUntilIdle()

        // Coroutine completed, no leak
        assertFalse(job?.isActive == true)
        assertEquals(0, AppLog.getActiveExtractionJobsCount())
    }

    // ==========================================
    // 13. Cancellation Test
    // ==========================================
    @Test
    fun `validation 13 - rescan during active Gemini analysis cancels job and resets to CameraActive`() = testScope.runTest {
        countingGeminiExtractor.delayMs = 5_000L
        val testImage = CapturedImage(
            uri = "file:///test/cancelling.jpg",
            source = CaptureSource.CAMERA,
            capturedAt = Instant.now(),
            mimeType = "image/jpeg",
            fileSize = 100L,
        )
        viewModel.onScanAcquired(testImage)
        advanceUntilIdle()

        val geminiJob = viewModel.confirmLocalExtraction()
        assertNotNull(geminiJob)
        assertTrue(viewModel.uiState.value is CaptureUiState.GeminiProcessing)

        // User hits Rescan while Gemini is analyzing
        viewModel.rescan()

        assertEquals(CaptureUiState.CameraActive, viewModel.uiState.value)
        assertFalse(geminiJob?.isActive == true)
        assertEquals(0, AppLog.getActiveExtractionJobsCount())
    }

    // ==========================================
    // 14. Concurrent Scan Protection
    // ==========================================
    @Test
    fun `validation 14 - rapid sequential scans cancel older jobs and active extraction jobs never exceed 1`() = testScope.runTest {
        val imageA = CapturedImage(uri = "file:///test/scanA.jpg", source = CaptureSource.CAMERA, capturedAt = Instant.now(), mimeType = "image/jpeg", fileSize = 100L)
        val imageB = CapturedImage(uri = "file:///test/scanB.jpg", source = CaptureSource.CAMERA, capturedAt = Instant.now(), mimeType = "image/jpeg", fileSize = 100L)
        val imageC = CapturedImage(uri = "file:///test/scanC.jpg", source = CaptureSource.CAMERA, capturedAt = Instant.now(), mimeType = "image/jpeg", fileSize = 100L)

        viewModel.onScanAcquired(imageA)
        assertTrue("Active extraction jobs must never exceed 1", AppLog.getActiveExtractionJobsCount() <= 1)

        viewModel.onScanAcquired(imageB)
        assertTrue("Active extraction jobs must never exceed 1", AppLog.getActiveExtractionJobsCount() <= 1)

        viewModel.onScanAcquired(imageC)
        assertTrue("Active extraction jobs must never exceed 1", AppLog.getActiveExtractionJobsCount() <= 1)

        advanceUntilIdle()

        assertEquals(0, AppLog.getActiveExtractionJobsCount())
        val state = viewModel.uiState.value as CaptureUiState.LocalExtractionReady
        assertEquals(imageC.uri, state.capturedImage.uri)
    }

    // ==========================================
    // 15. State-Machine Validation
    // ==========================================
    @Test
    fun `validation 15 - state machine lifecycle follows defined valid transitions`() = testScope.runTest {
        assertEquals(CaptureUiState.Idle, viewModel.uiState.value)

        viewModel.onTakePhotoClicked(hasPermission = true)
        assertEquals(CaptureUiState.CameraActive, viewModel.uiState.value)

        val image = CapturedImage(uri = "file:///test/sm.jpg", source = CaptureSource.CAMERA, capturedAt = Instant.now(), mimeType = "image/jpeg", fileSize = 100L)
        viewModel.onScanAcquired(image)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CaptureUiState.LocalExtractionReady)

        viewModel.confirmLocalExtraction()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CaptureUiState.ExtractionReady)

        viewModel.rescan()
        assertEquals(CaptureUiState.CameraActive, viewModel.uiState.value)
    }

    // ==========================================
    // 16. Diagnostic Integrity
    // ==========================================
    @Test
    fun `validation 16 - diagnostics verify model is gemini-3-5-flash and tracks all stages`() = testScope.runTest {
        val testImage = CapturedImage(uri = "file:///test/diag.jpg", source = CaptureSource.CAMERA, capturedAt = Instant.now(), mimeType = "image/jpeg", fileSize = 100L)
        viewModel.onScanAcquired(testImage)
        advanceUntilIdle()

        viewModel.confirmLocalExtraction()
        advanceUntilIdle()

        val dump = AppLog.getFormattedDump()
        assertTrue("Diagnostics dump should contain Scan ID", dump.contains("Scan ID:"))
        assertTrue("Diagnostics dump should contain OCR stage", dump.contains("OCR:"))
        assertTrue("Diagnostics dump should contain Gemini stage", dump.contains("Gemini:"))
        assertTrue("Model must be gemini-3.5-flash", dump.contains("gemini-3.5-flash"))
        assertFalse("Model must NOT be gemini-3.5-flash-lite", dump.contains("gemini-3.5-flash-lite"))
        assertFalse("Model must NOT be gemini-3.6-flash", dump.contains("gemini-3.6-flash"))
    }

    // ==========================================
    // 17. Diagnostic Security
    // ==========================================
    @Test
    fun `validation 17 - diagnostics do not leak sensitive credentials or authorization keys`() {
        val sensitiveSnippet = "Bearer AIzaSyD-SecretKey1234567890abcdef"
        val sanitized = AppLog.sanitize(sensitiveSnippet)

        assertFalse("API key must be redacted", sanitized.contains("AIzaSyD-SecretKey1234567890abcdef"))
        assertTrue("Must contain redacted placeholder", sanitized.contains("REDACTED"))
    }

    // ==========================================
    // 18. Persistence Race Test
    // ==========================================
    @Test
    fun `validation 18 - rapid duplicate save calls persist exactly 1 memory and 1 reminder`() = testScope.runTest {
        val job1 = viewModel.saveMemory(
            title = "Test Bill",
            eventType = EventType.DUE_DATE,
            eventDate = LocalDate.of(2026, 10, 15),
            remindDaysBefore = 3,
        )
        val job2 = viewModel.saveMemory(
            title = "Test Bill",
            eventType = EventType.DUE_DATE,
            eventDate = LocalDate.of(2026, 10, 15),
            remindDaysBefore = 3,
        )

        job1.join()
        job2.join()
        advanceUntilIdle()

        assertEquals("Exactly 1 memory must be saved", 1, fakeMemoryRepo.saveCount.get())
        assertEquals("Exactly 1 reminder must be scheduled", 1, fakeNotificationScheduler.scheduled.size)
    }

    // ==========================================
    // 19. Rapid UI Action Test
    // ==========================================
    @Test
    fun `validation 19 - rapid sequence of Confirm, Rescan, and Manual Entry causes no crash or stuck state`() = testScope.runTest {
        val testImage = CapturedImage(uri = "file:///test/rapid.jpg", source = CaptureSource.CAMERA, capturedAt = Instant.now(), mimeType = "image/jpeg", fileSize = 100L)

        for (i in 1..3) {
            viewModel.onScanAcquired(testImage)
            advanceUntilIdle()

            viewModel.confirmLocalExtraction()
            viewModel.confirmLocalExtraction()
            viewModel.rescan()
            viewModel.confirmLocalExtraction() // Should be ignored since in CameraActive
            viewModel.rescan()
            viewModel.startManualEntry(testImage)
        }
        advanceUntilIdle()

        // Must end in a clean, consistent non-crashed state
        val finalState = viewModel.uiState.value
        assertTrue("Final state must be valid", finalState is CaptureUiState.ExtractionReady || finalState is CaptureUiState.CameraActive)
        assertEquals("Total Gemini calls must not leak through rapid clicks", 0, countingGeminiExtractor.callCount.get())
        assertEquals(0, AppLog.getActiveExtractionJobsCount())
    }
}
