package app.memora

import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.domain.model.EventType
import app.memora.domain.model.MemoraError
import app.memora.domain.model.Memory
import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.model.Reminder
import app.memora.domain.repository.MemoryRepository
import app.memora.domain.repository.ReminderRepository
import app.memora.domain.usecase.CreateMemoryUseCase
import app.memora.domain.usecase.DeleteMemoryUseCase
import app.memora.domain.usecase.ProcessCapturedImageUseCase
import app.memora.platform.image.ImageStorage
import app.memora.platform.notification.NotificationScheduler
import app.memora.presentation.capture.CaptureUiState
import app.memora.presentation.capture.CaptureViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeStorage: FakeImageStorage
    private lateinit var fakeMemoryRepository: FakeMemoryRepository
    private lateinit var fakeReminderRepository: FakeReminderRepository
    private lateinit var createMemoryUseCase: CreateMemoryUseCase
    private lateinit var deleteMemoryUseCase: DeleteMemoryUseCase
    private lateinit var useCase: ProcessCapturedImageUseCase
    private lateinit var fakeNotificationScheduler: FakeNotificationScheduler
    private lateinit var fakeTextExtractor: FakeTextExtractor
    private lateinit var recordingGeminiExtractor: RecordingGeminiExtractor
    private lateinit var extractLocallyUseCase: app.memora.domain.usecase.ExtractLocallyUseCase
    private lateinit var refineWithGeminiUseCase: app.memora.domain.usecase.RefineWithGeminiUseCase
    private lateinit var viewModel: CaptureViewModel

    private class FakeNotificationScheduler : NotificationScheduler {
        data class ScheduledReminder(
            val memoryId: String,
            val reminderId: String,
            val title: String,
            val reminderStartDate: LocalDate,
            val eventDate: LocalDate,
            val timeZoneId: String,
        )
        val scheduledReminders = mutableListOf<ScheduledReminder>()
        val cancelledReminders = mutableListOf<String>()
        var shouldFailScheduling = false

        override suspend fun scheduleReminder(reminderId: String, triggerAt: Instant, title: String): Result<Unit> = Result.success(Unit)

        override suspend fun scheduleReminder(
            memoryId: String,
            reminderId: String,
            title: String,
            reminderStartDate: LocalDate,
            eventDate: LocalDate,
            timeZoneId: String,
            reminderTime: java.time.LocalTime,
        ): Result<Unit> {
            if (shouldFailScheduling) return Result.failure(RuntimeException("Scheduler failure"))
            scheduledReminders.add(ScheduledReminder(memoryId, reminderId, title, reminderStartDate, eventDate, timeZoneId))
            return Result.success(Unit)
        }

        override suspend fun cancelReminder(reminderId: String): Result<Unit> {
            cancelledReminders.add(reminderId)
            return Result.success(Unit)
        }

        override val areNotificationsEnabled: Boolean = true
    }

    private class FakeMemoryRepository : MemoryRepository {
        var shouldFail = false
        val memories = mutableMapOf<String, Memory>()
        val reminders = mutableMapOf<String, Reminder>()
        override fun observeMemories(): Flow<List<MemoryWithReminders>> = flowOf(emptyList())
        override suspend fun getMemory(id: String): Memory? = memories[id]
        override suspend fun getMemoryWithReminders(id: String): MemoryWithReminders? = memories[id]?.let { MemoryWithReminders(it, emptyList()) }
        override suspend fun createMemory(memory: Memory, initialReminder: Reminder?): Result<Unit> {
            if (shouldFail) return Result.failure(RuntimeException("Database insert error"))
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

    private class FakeReminderRepository : ReminderRepository {
        var shouldFail = false
        val reminders = mutableMapOf<String, Reminder>()
        override fun observeReminders(): Flow<List<Reminder>> = flowOf(emptyList())
        override fun observeRemindersForMemory(memoryId: String): Flow<List<Reminder>> = flowOf(emptyList())
        override suspend fun getReminder(id: String): Reminder? = reminders[id]
        override suspend fun getRemindersForMemory(memoryId: String): List<Reminder> = reminders.values.filter { it.memoryId == memoryId }
        override suspend fun createReminder(reminder: Reminder): Result<Unit> {
            if (shouldFail) return Result.failure(RuntimeException("Database insert error"))
            reminders[reminder.id] = reminder
            return Result.success(Unit)
        }
        override suspend fun updateReminder(reminder: Reminder): Result<Unit> = Result.success(Unit)
        override suspend fun completeReminder(id: String, completedAt: Instant): Result<Unit> = Result.success(Unit)
        override suspend fun deleteReminder(id: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeImageStorage : ImageStorage {
        var shouldFailImport = false
        var deletedUri: String? = null
        var nextUri: String? = null

        override fun createCaptureFile(prefix: String): File {
            return File.createTempFile(prefix, ".jpg")
        }

        override suspend fun importFromUri(uriString: String, source: CaptureSource): Result<CapturedImage> {
            if (shouldFailImport) {
                return Result.failure(MemoraError.Capture.ImportFailed("Storage stream read failed."))
            }
            val returnUri = nextUri ?: "file:///data/user/0/app.memora/files/captures/test_image.jpg"
            return Result.success(
                CapturedImage(
                    uri = returnUri,
                    source = source,
                    capturedAt = Instant.parse("2026-09-03T12:00:00Z"),
                    mimeType = "image/jpeg",
                    fileSize = 4096L,
                )
            )
        }

        override fun createCapturedImageFromFile(file: File, source: CaptureSource): CapturedImage {
            return CapturedImage(
                uri = file.toURI().toString(),
                source = source,
                capturedAt = Instant.parse("2026-09-03T12:00:00Z"),
                mimeType = "image/jpeg",
                fileSize = if (file.exists()) file.length() else 0L,
            )
        }

        override fun deleteImage(uriString: String): Boolean {
            deletedUri = uriString
            return true
        }
    }

    private class FakeTextExtractor : app.memora.platform.ocr.TextExtractor {
        var textToReturn = "BESCOM Electricity Bill\nDue Date: 15/10/2026\nAmount: Rs 1500"
        var shouldFail = false
        var lastUri: String? = null
        override suspend fun extractText(imageUri: String): Result<app.memora.platform.ocr.ExtractedText> {
            lastUri = imageUri
            if (shouldFail) return Result.failure(RuntimeException("OCR failed"))
            return Result.success(app.memora.platform.ocr.ExtractedText(textToReturn))
        }
        override val isAvailable: Boolean = true
    }

    private class RecordingGeminiExtractor : app.memora.domain.extraction.MemoryExtractor {
        var callCount = 0
        var shouldFail = false
        var lastPrompt: String? = null
        var returnCandidate = app.memora.domain.extraction.ExtractedMemoryCandidate(
            suggestedTitle = "AI Extracted Bill",
            suggestedEventType = EventType.DUE_DATE,
            suggestedEventDate = LocalDate.of(2026, 10, 15),
            confidence = 0.95f,
        )

        override suspend fun extractCandidate(rawText: String, imageUri: android.net.Uri?): Result<app.memora.domain.extraction.ExtractedMemoryCandidate> {
            callCount++
            lastPrompt = rawText
            if (shouldFail) return Result.failure(RuntimeException("Gemini network error"))
            return Result.success(returnCandidate)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeStorage = FakeImageStorage()
        useCase = ProcessCapturedImageUseCase(fakeStorage)
        fakeMemoryRepository = FakeMemoryRepository()
        fakeReminderRepository = FakeReminderRepository()
        createMemoryUseCase = CreateMemoryUseCase(fakeMemoryRepository)
        deleteMemoryUseCase = DeleteMemoryUseCase(fakeMemoryRepository)
        fakeNotificationScheduler = FakeNotificationScheduler()
        fakeTextExtractor = FakeTextExtractor()
        recordingGeminiExtractor = RecordingGeminiExtractor()
        extractLocallyUseCase = app.memora.domain.usecase.ExtractLocallyUseCase(
            textExtractor = fakeTextExtractor,
            deterministicExtractor = app.memora.domain.extraction.DeterministicMemoryExtractor(),
        )
        refineWithGeminiUseCase = app.memora.domain.usecase.RefineWithGeminiUseCase(
            geminiExtractor = recordingGeminiExtractor,
        )
        viewModel = CaptureViewModel(
            processCapturedImageUseCase = useCase,
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
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(CaptureUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `onTakePhotoClicked with permission transitions to CameraActive`() {
        viewModel.onTakePhotoClicked(hasPermission = true)
        assertEquals(CaptureUiState.CameraActive, viewModel.uiState.value)
    }

    @Test
    fun `onTakePhotoClicked without permission transitions to PermissionRequired`() {
        viewModel.onTakePhotoClicked(hasPermission = false)
        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.PermissionRequired)
        assertEquals(false, (state as CaptureUiState.PermissionRequired).permanentlyDenied)
    }

    @Test
    fun `onPermissionResult permanently denied updates PermissionRequired`() {
        viewModel.onPermissionResult(granted = false, permanentlyDenied = true)
        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.PermissionRequired)
        assertEquals(true, (state as CaptureUiState.PermissionRequired).permanentlyDenied)
    }

    @Test
    fun `onPermissionResult granted opens CameraActive`() {
        viewModel.onPermissionResult(granted = true)
        assertEquals(CaptureUiState.CameraActive, viewModel.uiState.value)
    }

    @Test
    fun `successful camera capture transitions to Preview state with CAMERA source`() {
        val tempFile = File.createTempFile("photo_valid", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        viewModel.onPhotoCaptured(tempFile)

        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.Preview)
        val preview = state as CaptureUiState.Preview
        assertEquals(CaptureSource.CAMERA, preview.capturedImage.source)
        assertEquals(tempFile.toURI().toString(), preview.capturedImage.uri)
        assertEquals(4L, preview.capturedImage.fileSize)

        tempFile.delete()
    }

    @Test
    fun `empty camera file produces Error state`() {
        val emptyFile = File.createTempFile("photo_empty", ".jpg")

        viewModel.onPhotoCaptured(emptyFile)

        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.Error)
        val error = state as CaptureUiState.Error
        assertEquals("Captured photo was empty or could not be saved.", error.message)

        emptyFile.delete()
    }

    @Test
    fun `gallery image selection transitions to Preview state with GALLERY source`() = testScope.runTest {
        viewModel.onImageSelected("content://media/external/images/1", CaptureSource.GALLERY)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.Preview)
        val preview = state as CaptureUiState.Preview
        assertEquals(CaptureSource.GALLERY, preview.capturedImage.source)
        assertEquals("file:///data/user/0/app.memora/files/captures/test_image.jpg", preview.capturedImage.uri)
    }

    @Test
    fun `screenshot selection transitions to Preview state with SCREENSHOT source`() = testScope.runTest {
        viewModel.onImageSelected("content://media/external/images/screenshot_1", CaptureSource.SCREENSHOT)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.Preview)
        val preview = state as CaptureUiState.Preview
        assertEquals(CaptureSource.SCREENSHOT, preview.capturedImage.source)
    }

    @Test
    fun `picker cancellation returns to Idle state`() {
        viewModel.onImageSelected(null, CaptureSource.GALLERY)
        assertEquals(CaptureUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `import failure produces Error state`() = testScope.runTest {
        fakeStorage.shouldFailImport = true
        viewModel.onImageSelected("content://media/external/invalid", CaptureSource.GALLERY)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.Error)
        val error = state as CaptureUiState.Error
        assertEquals("Failed to import selected image: Storage stream read failed.", error.message)
    }

    @Test
    fun `confirmImage transitions to Confirmed state`() = testScope.runTest {
        viewModel.onImageSelected("content://media/external/1", CaptureSource.GALLERY)
        advanceUntilIdle()

        viewModel.confirmImage()

        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.Confirmed)
        val confirmed = state as CaptureUiState.Confirmed
        assertEquals(CaptureSource.GALLERY, confirmed.capturedImage.source)
    }

    @Test
    fun `replaceImage for camera returns to CameraActive`() {
        val tempFile = File.createTempFile("camera_retake", ".jpg").apply {
            writeBytes(byteArrayOf(9, 9, 9))
        }
        viewModel.onPhotoCaptured(tempFile)
        assertTrue(viewModel.uiState.value is CaptureUiState.Preview)

        viewModel.replaceImage()

        assertEquals(CaptureUiState.CameraActive, viewModel.uiState.value)
        assertEquals(tempFile.toURI().toString(), fakeStorage.deletedUri)

        tempFile.delete()
    }

    @Test
    fun `replaceImage for gallery invokes picker launch callback and keeps preview active`() = testScope.runTest {
        viewModel.onImageSelected("content://media/picker/photo_1", CaptureSource.GALLERY)
        advanceUntilIdle()

        var launchedSource: CaptureSource? = null
        viewModel.retakeOrReplaceImage { source ->
            launchedSource = source
        }

        assertEquals(CaptureSource.GALLERY, launchedSource)
        // Old image must NOT be deleted before replacement succeeds
        assertEquals(null, fakeStorage.deletedUri)
        assertTrue(viewModel.uiState.value is CaptureUiState.Preview)
    }

    @Test
    fun `successful replacement deletes old image and activates new image`() = testScope.runTest {
        viewModel.onImageSelected("content://media/picker/photo_1", CaptureSource.GALLERY)
        advanceUntilIdle()
        val firstState = viewModel.uiState.value as CaptureUiState.Preview
        val firstUri = firstState.capturedImage.uri

        // Select a replacement image with a different URI
        fakeStorage.nextUri = "file:///data/user/0/app.memora/files/captures/photo_2.jpg"
        viewModel.onImageSelected("content://media/picker/photo_2", CaptureSource.GALLERY)
        advanceUntilIdle()

        val secondState = viewModel.uiState.value as CaptureUiState.Preview
        assertEquals("file:///data/user/0/app.memora/files/captures/photo_2.jpg", secondState.capturedImage.uri)
        // Old image must have been deleted
        assertEquals(firstUri, fakeStorage.deletedUri)
    }

    @Test
    fun `failed replacement does not destroy existing active image`() = testScope.runTest {
        viewModel.onImageSelected("content://media/picker/photo_1", CaptureSource.GALLERY)
        advanceUntilIdle()
        val initialPreview = viewModel.uiState.value as CaptureUiState.Preview

        // Attempt replacement that fails
        fakeStorage.shouldFailImport = true
        fakeStorage.deletedUri = null
        viewModel.onImageSelected("content://media/picker/corrupt", CaptureSource.GALLERY)
        advanceUntilIdle()

        val errorState = viewModel.uiState.value as CaptureUiState.Error
        assertEquals(CaptureUiState.Preview(initialPreview.capturedImage), errorState.returnState)
        // Active image was NOT deleted
        assertEquals(null, fakeStorage.deletedUri)

        // Dismiss error returns to the preserved active image preview
        viewModel.dismissError()
        assertEquals(CaptureUiState.Preview(initialPreview.capturedImage), viewModel.uiState.value)
    }

    @Test
    fun `picker cancellation while in preview preserves active image`() = testScope.runTest {
        viewModel.onImageSelected("content://media/picker/photo_1", CaptureSource.GALLERY)
        advanceUntilIdle()
        val initialPreview = viewModel.uiState.value as CaptureUiState.Preview

        // User cancels picker (returns null)
        viewModel.onImageSelected(null, CaptureSource.GALLERY)
        advanceUntilIdle()

        // State is still Preview with original image
        assertEquals(initialPreview, viewModel.uiState.value)
        assertEquals(null, fakeStorage.deletedUri)
    }

    @Test
    fun `cancelCapture deletes temporary image and resets to Idle`() = testScope.runTest {
        viewModel.onImageSelected("content://media/picker/photo_1", CaptureSource.GALLERY)
        advanceUntilIdle()
        val state = viewModel.uiState.value as CaptureUiState.Preview

        viewModel.cancelCapture()

        assertEquals(state.capturedImage.uri, fakeStorage.deletedUri)
        assertEquals(CaptureUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `resetToIdle transitions back to Idle and cleans up`() {
        viewModel.onTakePhotoClicked(hasPermission = true)
        assertEquals(CaptureUiState.CameraActive, viewModel.uiState.value)

        viewModel.resetToIdle()
        assertEquals(CaptureUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `dismissError returns to returnState`() {
        viewModel.onCaptureError(Exception("Test failure"))
        assertTrue(viewModel.uiState.value is CaptureUiState.Error)

        viewModel.dismissError()
        assertEquals(CaptureUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `saveMemory failure sets Error state with returnState Idle`() = testScope.runTest {
        fakeMemoryRepository.shouldFail = true

        val job = viewModel.saveMemory(
            title = "Test Bill",
            eventType = EventType.DUE_DATE,
            eventDate = LocalDate.of(2026, 9, 20),
            remindDaysBefore = 3,
        )
        job.join()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.Error)
        val errorState = state as CaptureUiState.Error
        assertEquals("Failed to save. Please try again.", errorState.message)
        assertEquals(CaptureUiState.Idle, errorState.returnState)
    }

    @Test
    fun `saveMemory schedules notification with NotificationScheduler`() = testScope.runTest {
        val eventDate = LocalDate.of(2026, 9, 20)
        val remindDaysBefore = 3
        val job = viewModel.saveMemory(
            title = "Test Dentist",
            eventType = EventType.DUE_DATE,
            eventDate = eventDate,
            remindDaysBefore = remindDaysBefore,
            timeZoneId = "UTC",
        )
        job.join()
        advanceUntilIdle()

        assertEquals(1, fakeNotificationScheduler.scheduledReminders.size)
        val scheduled = fakeNotificationScheduler.scheduledReminders.first()
        assertEquals("Test Dentist", scheduled.title)
        assertEquals(eventDate, scheduled.eventDate)
        assertEquals(LocalDate.of(2026, 9, 17), scheduled.reminderStartDate)
        assertEquals("UTC", scheduled.timeZoneId)
        assertTrue(scheduled.reminderId.isNotBlank())
        assertTrue(scheduled.memoryId.isNotBlank())
    }
    @Test
    fun `saveMemory rolls back persisted memory when notification scheduling fails`() = testScope.runTest {
        fakeNotificationScheduler.shouldFailScheduling = true

        val job = viewModel.saveMemory(
            title = "Scheduler Failure",
            eventType = EventType.DUE_DATE,
            eventDate = LocalDate.of(2026, 9, 20),
            remindDaysBefore = 3,
            timeZoneId = "UTC",
        )
        job.join()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CaptureUiState.Error)
        assertTrue(fakeMemoryRepository.memories.isEmpty())
        assertTrue(fakeMemoryRepository.reminders.isEmpty())
        assertTrue(fakeReminderRepository.reminders.isEmpty())
        assertTrue(fakeNotificationScheduler.scheduledReminders.isEmpty())
        assertEquals(CaptureUiState.Idle, (viewModel.uiState.value as CaptureUiState.Error).returnState)
    }

    @Test
    fun `local extraction on scan acquisition yields LocalExtractionReady and makes 0 Gemini calls`() = testScope.runTest {
        viewModel.onImageSelected("content://media/external/1", CaptureSource.GALLERY)
        advanceUntilIdle()

        viewModel.confirmImage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.LocalExtractionReady)
        val ready = state as CaptureUiState.LocalExtractionReady
        assertEquals("Electricity Bill", ready.localResult.title)
        assertEquals(LocalDate.of(2026, 10, 15), ready.localResult.selectedEventDate)
        assertEquals(0, recordingGeminiExtractor.callCount)
    }

    @Test
    fun `rescan from LocalExtractionReady resets to CameraActive and makes 0 Gemini calls`() = testScope.runTest {
        viewModel.onImageSelected("content://media/external/1", CaptureSource.GALLERY)
        advanceUntilIdle()
        viewModel.confirmImage()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CaptureUiState.LocalExtractionReady)

        viewModel.rescan()

        assertEquals(CaptureUiState.CameraActive, viewModel.uiState.value)
        assertEquals(0, recordingGeminiExtractor.callCount)
    }

    @Test
    fun `startManualEntry sets ExtractionReady with local title and makes 0 Gemini calls`() = testScope.runTest {
        viewModel.onImageSelected("content://media/external/1", CaptureSource.GALLERY)
        advanceUntilIdle()
        viewModel.confirmImage()
        advanceUntilIdle()

        val state = viewModel.uiState.value as CaptureUiState.LocalExtractionReady
        viewModel.startManualEntry(state.capturedImage)

        val nextState = viewModel.uiState.value
        assertTrue(nextState is CaptureUiState.ExtractionReady)
        val ready = nextState as CaptureUiState.ExtractionReady
        assertEquals("Electricity Bill", ready.result.candidate.suggestedTitle)
        assertNull(ready.result.candidate.suggestedEventDate)
        assertEquals(0, recordingGeminiExtractor.callCount)
    }

    @Test
    fun `confirmLocalExtraction invokes Gemini exactly once`() = testScope.runTest {
        viewModel.onImageSelected("content://media/external/1", CaptureSource.GALLERY)
        advanceUntilIdle()
        viewModel.confirmImage()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CaptureUiState.LocalExtractionReady)

        val job = viewModel.confirmLocalExtraction()
        assertNotNull(job)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CaptureUiState.ExtractionReady)
        assertEquals(1, recordingGeminiExtractor.callCount)
    }

    @Test
    fun `rapid repeated confirmLocalExtraction calls trigger exactly 1 Gemini call (double-confirm protection)`() = testScope.runTest {
        viewModel.onImageSelected("content://media/external/1", CaptureSource.GALLERY)
        advanceUntilIdle()
        viewModel.confirmImage()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CaptureUiState.LocalExtractionReady)

        // Simulate rapid double-tapping on "Confirm & Analyze with AI"
        val job1 = viewModel.confirmLocalExtraction()
        val job2 = viewModel.confirmLocalExtraction()

        assertNotNull(job1)
        assertNull(job2) // Double-tap rejected!

        advanceUntilIdle()

        assertEquals(1, recordingGeminiExtractor.callCount)
        assertTrue(viewModel.uiState.value is CaptureUiState.ExtractionReady)
    }

    @Test
    fun `DeterministicMemoryExtractor marks multiple competing unlabelled dates as ambiguous`() {
        val extractor = app.memora.domain.extraction.DeterministicMemoryExtractor()
        val text = "Reference 10/05/2026 with notice date 20/08/2026"
        val result = extractor.extractLocalResult(text)

        assertTrue(result.isAmbiguous)
        assertNull(result.selectedEventDate)
        assertEquals(2, result.dateCandidates.size)
    }

    @Test
    fun `RefineWithGeminiUseCase falls back to unambiguous local candidate when Gemini fails`() = testScope.runTest {
        recordingGeminiExtractor.shouldFail = true
        val localDate = LocalDate.of(2026, 11, 20)
        val localResult = app.memora.domain.extraction.LocalExtractionResult(
            title = "Airtel Bill",
            eventType = EventType.DUE_DATE,
            selectedEventDate = localDate,
            dateType = "DUE_DATE",
            dateCandidates = emptyList(),
            fields = mapOf("Due Date" to "2026-11-20"),
            confidence = 0.9f,
            isAmbiguous = false,
        )
        val image = CapturedImage(
            uri = "file:///test/airtel.jpg",
            source = CaptureSource.CAMERA,
            capturedAt = Instant.now(),
            mimeType = "image/jpeg",
            fileSize = 100L,
        )

        val result = refineWithGeminiUseCase(
            image = image,
            ocrResult = app.memora.domain.extraction.OcrResult("Airtel Due: 20/11/2026"),
            localResult = localResult,
            scanId = "TEST-FALLBACK",
        )

        assertTrue(result.isSuccess)
        val stage3Result = result.getOrNull()
        assertNotNull(stage3Result)
        assertEquals(localDate, stage3Result?.candidate?.suggestedEventDate)
        assertEquals("Airtel Bill", stage3Result?.candidate?.suggestedTitle)
    }

    @Test
    fun `RefineWithGeminiUseCase propagates failure without silent guessing when Gemini fails on ambiguous local candidate`() = testScope.runTest {
        recordingGeminiExtractor.shouldFail = true
        val localResult = app.memora.domain.extraction.LocalExtractionResult(
            title = "Unknown",
            eventType = null,
            selectedEventDate = null,
            dateType = null,
            dateCandidates = emptyList(),
            fields = emptyMap(),
            confidence = 0.3f,
            isAmbiguous = true,
        )
        val image = CapturedImage(
            uri = "file:///test/ambiguous.jpg",
            source = CaptureSource.CAMERA,
            capturedAt = Instant.now(),
            mimeType = "image/jpeg",
            fileSize = 100L,
        )

        val result = refineWithGeminiUseCase(
            image = image,
            ocrResult = app.memora.domain.extraction.OcrResult("Ambiguous dates: 12/04/2026 and 14/09/2026"),
            localResult = localResult,
            scanId = "TEST-FAIL",
        )

        assertTrue(result.isFailure)
    }
}
