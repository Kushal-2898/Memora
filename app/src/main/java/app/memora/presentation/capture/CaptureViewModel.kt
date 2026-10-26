package app.memora.presentation.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.domain.model.EventType
import app.memora.domain.model.MemoraError
import app.memora.domain.model.Reminder
import app.memora.domain.usecase.CreateMemoryUseCase
import app.memora.domain.usecase.DeleteMemoryUseCase
import app.memora.domain.usecase.ExtractCapturedImageUseCase
import app.memora.domain.usecase.ExtractLocallyUseCase
import app.memora.domain.usecase.ProcessCapturedImageUseCase
import app.memora.domain.usecase.RefineWithGeminiUseCase
import app.memora.platform.camera.PackagingDetectionResult
import app.memora.platform.image.ImageStorage
import app.memora.platform.notification.NotificationScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import app.memora.platform.logging.AppLog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

sealed interface CaptureNavEvent {
    data object NavigateToHome : CaptureNavEvent
}

typealias ScannerNavEvent = CaptureNavEvent
typealias ScannerViewModel = CaptureViewModel
typealias ScannerUiState = CaptureUiState

/**
 * ViewModel governing Memora's capture pipeline (Stage 2 & Stage 5).
 *
 * Implements strict MVVM separation and deterministic temporary image lifecycle ownership:
 * - Coordinates user intent from UI entry points (Take Photo, Choose Image, Import Screenshot).
 * - Delegates validation and storage streaming to [ProcessCapturedImageUseCase].
 * - Surfaces deterministic, mutually exclusive [CaptureUiState] transitions.
 * - Manages temporary image lifecycle: deletes abandoned or replaced temporary images,
 *   preserving active images until replacement succeeds, and releasing resources on clear.
 * - Stage 3.5: Runs on-device local OCR + deterministic extraction before user confirmation.
 * - Stage 4: AI refinement via Gemini only runs after explicit user confirmation.
 * - Stage 5: Handles [saveMemory] persistence into Room via [CreateMemoryUseCase], schedules the reminder,
 *   cleans up temporary image files, and emits navigation events to Home.
 */
class CaptureViewModel(
    private val processCapturedImageUseCase: ProcessCapturedImageUseCase,
    private val imageStorage: ImageStorage,
    private val createMemoryUseCase: CreateMemoryUseCase,
    private val deleteMemoryUseCase: DeleteMemoryUseCase,
    private val notificationScheduler: NotificationScheduler,
    private val extractLocallyUseCase: ExtractLocallyUseCase,
    private val refineWithGeminiUseCase: RefineWithGeminiUseCase,
    private val crashReporter: app.memora.domain.logging.CrashReporter,
    private val analyticsLogger: app.memora.domain.logging.AnalyticsLogger,
    private val extractCapturedImageUseCase: ExtractCapturedImageUseCase? = null,
    private val canScanUseCase: app.memora.domain.usecase.CanScanUseCase? = null,
) : ViewModel() {

    // Backward-compatible secondary constructor for existing callers/tests
    constructor(
        processCapturedImageUseCase: ProcessCapturedImageUseCase,
        imageStorage: ImageStorage,
        createMemoryUseCase: CreateMemoryUseCase,
        deleteMemoryUseCase: DeleteMemoryUseCase,
        notificationScheduler: NotificationScheduler,
        extractCapturedImageUseCase: ExtractCapturedImageUseCase,
        crashReporter: app.memora.domain.logging.CrashReporter,
        analyticsLogger: app.memora.domain.logging.AnalyticsLogger,
    ) : this(
        processCapturedImageUseCase = processCapturedImageUseCase,
        imageStorage = imageStorage,
        createMemoryUseCase = createMemoryUseCase,
        deleteMemoryUseCase = deleteMemoryUseCase,
        notificationScheduler = notificationScheduler,
        extractLocallyUseCase = ExtractLocallyUseCase(
            textExtractor = object : app.memora.platform.ocr.TextExtractor {
                override suspend fun extractText(imageUri: String) = Result.success(app.memora.platform.ocr.ExtractedText(""))
                override val isAvailable: Boolean = true
            },
            barcodeExtractor = object : app.memora.platform.ocr.BarcodeExtractor {
                override suspend fun extractBarcode(imageUri: String) = Result.success(null)
            },
            deterministicExtractor = app.memora.domain.extraction.DeterministicMemoryExtractor(),
        ),
        refineWithGeminiUseCase = RefineWithGeminiUseCase(
            geminiExtractor = app.memora.domain.extraction.DeterministicMemoryExtractor(),
        ),
        crashReporter = crashReporter,
        analyticsLogger = analyticsLogger,
        extractCapturedImageUseCase = extractCapturedImageUseCase,
    )

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<CaptureNavEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val navigationEvents: SharedFlow<CaptureNavEvent> = _navigationEvents.asSharedFlow()

    private var lastCapturedImageUri: String? = null
    private var isGeminiRunning = false
    private var inFlightExtractionJob: Job? = null
    private var inFlightExtractionJobId: Long? = null
    private var currentScanId: String? = null
    private var isSaving = false
    private var inFlightSaveJob: Job? = null
    private var cameraTimeoutJob: Job? = null

    var lastPackagingDetection: PackagingDetectionResult? = null
        private set

    init {
        viewModelScope.launch {
            _uiState.collect { state ->
                if (state is CaptureUiState.CameraActive) {
                    startCameraTimeout()
                } else {
                    cameraTimeoutJob?.cancel()
                }
            }
        }
    }

    private fun startCameraTimeout() {
        cameraTimeoutJob?.cancel()
        cameraTimeoutJob = viewModelScope.launch {
            kotlinx.coroutines.delay(60_000L) // 60 seconds timeout
            val action = app.memora.platform.logging.AppLog.recordUserAction("Scanner Timeout")
            app.memora.platform.logging.AppLog.recordStateTransition(action, "CaptureUiState: Error (Timeout)")
            _uiState.value = CaptureUiState.Error(
                message = "Could not identify a document. Please try again or enter details manually.",
                returnState = CaptureUiState.CameraActive,
                canRetry = true,
                canEnterManually = true,
            )
        }
    }

    fun onPackagingDetected(result: PackagingDetectionResult) {
        lastPackagingDetection = result
    }

    /**
     * Active temporary image reference, if present in any active capture state.
     */
    val activeTemporaryImage: CapturedImage?
        get() = when (val state = _uiState.value) {
            is CaptureUiState.Preview -> state.capturedImage
            is CaptureUiState.Confirmed -> state.capturedImage
            is CaptureUiState.Extracting -> state.capturedImage
            is CaptureUiState.LocalExtractionReady -> state.capturedImage
            is CaptureUiState.GeminiProcessing -> state.capturedImage
            is CaptureUiState.ExtractionReady -> state.capturedImage
            is CaptureUiState.Error -> state.capturedImage
            else -> null
        }

    /**
     * Activates the automatic camera scanner.
     */
    fun startScanner(hasPermission: Boolean) {
        val action = app.memora.platform.logging.AppLog.recordUserAction("Start Scanner")
        if (hasPermission) {
            app.memora.platform.logging.AppLog.recordStateTransition(action, "CaptureUiState: CameraActive")
            _uiState.value = CaptureUiState.CameraActive
        } else {
            app.memora.platform.logging.AppLog.recordStateTransition(action, "CaptureUiState: PermissionRequired")
            _uiState.value = CaptureUiState.PermissionRequired(permanentlyDenied = false)
        }
    }

    /**
     * Triggered when the user taps "Take Photo".
     * Checks whether runtime camera permission is already granted.
     */
    fun onTakePhotoClicked(hasPermission: Boolean) {
        startScanner(hasPermission)
    }

    /**
     * Handles the outcome of the camera permission request.
     */
    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean = false) {
        val action = app.memora.platform.logging.AppLog.recordUserAction("Camera Permission Result: granted=$granted")
        if (granted) {
            app.memora.platform.logging.AppLog.recordStateTransition(action, "CaptureUiState: CameraActive")
            _uiState.value = CaptureUiState.CameraActive
        } else {
            app.memora.platform.logging.AppLog.recordStateTransition(action, "CaptureUiState: PermissionRequired (denied)", result = "FAILED")
            _uiState.value = CaptureUiState.PermissionRequired(permanentlyDenied = permanentlyDenied)
        }
    }

    /**
     * Handles successful camera photo capture from CameraX.
     */
    fun onPhotoCaptured(file: File) {
        val action = app.memora.platform.logging.AppLog.recordUserAction("Photo captured")
        if (!file.exists() || file.length() == 0L) {
            app.memora.platform.logging.AppLog.recordStateTransition(action, "CaptureUiState: Error (empty photo file)", result = "FAILED")
            _uiState.value = CaptureUiState.Error(
                message = "Captured photo was empty or could not be saved.",
                returnState = CaptureUiState.CameraActive,
            )
            return
        }

        val previousImage = (_uiState.value as? CaptureUiState.Preview)?.capturedImage
        val capturedImage = imageStorage.createCapturedImageFromFile(file, CaptureSource.CAMERA)
        lastCapturedImageUri = capturedImage.uri

        // Delete old image only after new image is successfully acquired
        if (previousImage != null && previousImage.uri != capturedImage.uri) {
            imageStorage.deleteImage(previousImage.uri)
        }

        app.memora.platform.logging.AppLog.recordStateTransition(action, "CaptureUiState: Preview")
        _uiState.value = CaptureUiState.Preview(capturedImage)
    }

    /**
     * Handles image selection from Photo Picker or Screenshot Importer.
     */
    fun onImageSelected(uriString: String?, source: CaptureSource) {
        val actionLabel = if (source == CaptureSource.GALLERY) "Choose Image clicked" else "Import Screenshot clicked"
        val action = app.memora.platform.logging.AppLog.recordUserAction(actionLabel)
        val previousState = _uiState.value
        val oldImage = (previousState as? CaptureUiState.Preview)?.capturedImage

        if (uriString.isNullOrBlank()) {
            app.memora.platform.logging.AppLog.recordStateTransition(action, "Picker dismissed (no selection)")
            if (previousState !is CaptureUiState.Preview && previousState !is CaptureUiState.Confirmed) {
                _uiState.value = CaptureUiState.Idle
            }
            return
        }

        app.memora.platform.logging.AppLog.recordStateTransition(action, "CaptureUiState: Processing (Importing image)")
        _uiState.value = CaptureUiState.Processing("Importing image securely…")

        viewModelScope.launch {
            val result = processCapturedImageUseCase(uriString, source)
            result.fold(
                onSuccess = { newCapturedImage ->
                    lastCapturedImageUri = newCapturedImage.uri
                    // Delete old temporary image only after new replacement has been successfully created
                    if (oldImage != null && oldImage.uri != newCapturedImage.uri) {
                        imageStorage.deleteImage(oldImage.uri)
                    }
                    app.memora.platform.logging.AppLog.recordStateTransition(action, "CaptureUiState: Preview")
                    _uiState.value = CaptureUiState.Preview(newCapturedImage)
                },
                onFailure = { error ->
                    val userMessage = when (error) {
                        is MemoraError.Capture -> error.message
                        else -> error.message ?: "Failed to import selected image."
                    }
                    app.memora.platform.logging.AppLog.recordStateTransition(action, "CaptureUiState: Error (${error.javaClass.simpleName})", result = "FAILED")
                    _uiState.value = CaptureUiState.Error(
                        message = userMessage,
                        canRetry = true,
                        returnState = if (oldImage != null) CaptureUiState.Preview(oldImage) else CaptureUiState.Idle,
                    )
                }
            )
        }
    }

    /**
     * Handles camera hardware or capture failure.
     */
    fun onCaptureError(error: Throwable) {
        crashReporter.recordException(error)
        val userMessage = when (error) {
            is MemoraError.Capture -> error.message
            else -> error.message ?: "Camera capture failed."
        }
        _uiState.value = CaptureUiState.Error(
            message = userMessage,
            returnState = CaptureUiState.Idle,
        )
    }

    /**
     * Confirms the previewed image and begins local on-device extraction (0 Gemini calls).
     * The confirmed [CapturedImage] reference is maintained throughout the flow.
     */
    fun confirmImage(): CapturedImage? {
        val currentState = _uiState.value
        return if (currentState is CaptureUiState.Preview) {
            val image = currentState.capturedImage
            val scanId = AppLog.newScanId()
            currentScanId = scanId
            AppLog.startScan(scanId, imagePresent = true)
            val action = AppLog.recordUserAction("Capture Screen", "Confirm Image clicked", "scanId=$scanId")
            AppLog.recordStateTransition(action, "CaptureUiState: Confirmed")
            _uiState.value = CaptureUiState.Confirmed(image)
            processLocalExtraction(image, scanId, lastPackagingDetection)
            image
        } else null
    }

    /**
     * Triggers local OCR and deterministic extraction on a captured image.
     * Guaranteed Cost Invariant: 0 Gemini calls.
     */
    fun processLocalExtraction(
        image: CapturedImage,
        scanId: String = AppLog.newScanId(),
        packagingResult: PackagingDetectionResult? = lastPackagingDetection,
    ): Job {
        currentScanId = scanId
        if (AppLog.currentScan?.scanId != scanId) {
            AppLog.startScan(scanId, imagePresent = true)
        }
        val action = AppLog.recordUserAction("Capture Screen", "Process Local Extraction", "scanId=$scanId")

        inFlightExtractionJobId?.let { id ->
            AppLog.endExtractionJob(id, outcome = "CANCELLED")
        }
        inFlightExtractionJob?.cancel()

        val extractionJobId = AppLog.startExtractionJob(scanId = scanId, operation = "ExtractLocally", dispatcherLabel = "IO")
        inFlightExtractionJobId = extractionJobId

        val job = viewModelScope.launch {
            AppLog.recordStateTransition(action, "CaptureUiState: Extracting")
            _uiState.value = CaptureUiState.Extracting(image)
            try {
                val outputResult = kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                    extractLocallyUseCase(
                        image = image,
                        scanId = scanId,
                        packagingType = packagingResult?.packagingType ?: app.memora.domain.extraction.PackagingType.UNKNOWN,
                        dateLocation = packagingResult?.dateLocation ?: app.memora.domain.extraction.DateLocation.UNKNOWN,
                        markingStyle = packagingResult?.markingStyle ?: app.memora.domain.extraction.MarkingStyle.UNKNOWN,
                    )
                }
                if (currentScanId != scanId) {
                    AppLog.endExtractionJob(extractionJobId, outcome = "CANCELLED")
                    return@launch
                }
                if (outputResult == null) {
                    AppLog.endExtractionJob(extractionJobId, outcome = "TIMEOUT", error = "Local extraction exceeded 8s watchdog")
                    AppLog.recordStateTransition(action, "CaptureUiState: Error (Timeout)", result = "FAILED")
                    _uiState.value = CaptureUiState.Error(
                        message = "Scanning took longer than expected. You can try again or enter details manually.",
                        returnState = CaptureUiState.CameraActive,
                        canRetry = true,
                        canEnterManually = true,
                        capturedImage = image,
                    )
                    return@launch
                }
                outputResult.fold(
                    onSuccess = { output ->
                        if (currentScanId != scanId) return@fold
                        AppLog.endExtractionJob(extractionJobId, outcome = "SUCCESS")
                        AppLog.recordStateTransition(action, "CaptureUiState: LocalExtractionReady", result = "SUCCESS")
                        _uiState.value = CaptureUiState.LocalExtractionReady(
                            capturedImage = image,
                            ocrResult = output.ocrResult,
                            localResult = output.localResult,
                        )
                    },
                    onFailure = { error ->
                        if (currentScanId != scanId) return@fold
                        AppLog.endExtractionJob(extractionJobId, outcome = "FAILED", error = error.message)
                        AppLog.recordStateTransition(action, "CaptureUiState: Error (${error.javaClass.simpleName})", result = "FAILED")
                        crashReporter.recordException(error)
                        _uiState.value = CaptureUiState.Error(
                            message = "Unable to recognise details from this scan. You can try again or enter the details manually.",
                            returnState = CaptureUiState.CameraActive,
                            canEnterManually = true,
                            capturedImage = image,
                        )
                    }
                )
            } catch (c: CancellationException) {
                AppLog.endExtractionJob(extractionJobId, outcome = "CANCELLED")
                throw c
            } finally {
                if (inFlightExtractionJobId == extractionJobId) {
                    inFlightExtractionJobId = null
                }
            }
        }
        inFlightExtractionJob = job
        return job
    }

    /**
     * Direct callback when an automatic document scan frame is acquired by the scanner.
     * Guaranteed Cost Invariant: 0 Gemini calls.
     */
    fun onScanAcquired(
        capturedImage: CapturedImage,
        packagingResult: PackagingDetectionResult? = lastPackagingDetection,
    ) {
        lastCapturedImageUri = capturedImage.uri
        processLocalExtraction(capturedImage, packagingResult = packagingResult)
    }

    /**
     * Bypasses the Gemini AI step entirely, wrapping the local extraction result directly into
     * an ExtractionReady state for immediate user editing.
     */
    fun skipGeminiAndProceed() {
        if (isGeminiRunning) return
        val currentState = _uiState.value as? CaptureUiState.LocalExtractionReady ?: return
        
        val image = currentState.capturedImage
        val localResult = currentState.localResult
        val scanId = currentScanId ?: AppLog.newScanId()

        val action = AppLog.recordUserAction("Capture Screen", "Review and Save (Bypass AI) clicked", "scanId=$scanId")

        val candidate = app.memora.domain.extraction.ExtractedMemoryCandidate(
            suggestedTitle = localResult.title,
            suggestedEventType = localResult.eventType ?: app.memora.domain.model.EventType.OTHER,
            suggestedEventDate = localResult.selectedEventDate,
            confidence = localResult.confidence,
            rawTextSnippet = currentState.ocrResult.rawText,
            requiresConfirmation = true,
            alternatives = localResult.dateCandidates
        )

        val stage3Result = app.memora.domain.extraction.Stage3ExtractionResult(
            candidate = candidate,
            rawOcrText = currentState.ocrResult.rawText,
            normalizedText = currentState.ocrResult.rawText,
            disposition = app.memora.domain.extraction.ExtractionDisposition.HIGH_CONFIDENCE,
            alternatives = localResult.dateCandidates
        )

        AppLog.recordStateTransition(action, "CaptureUiState: ExtractionReady")
        _uiState.value = CaptureUiState.ExtractionReady(image, stage3Result)
    }

    /**
     * User explicitly confirmed the local review on the bottom sheet.
     * Invokes Gemini for refinement with double-tap protection.
     * Guaranteed Cost Invariant: Exactly 1 Gemini call across rapid repeated taps.
     */
    fun confirmLocalExtraction(): Job? {
        if (isGeminiRunning) return null
        val currentState = _uiState.value as? CaptureUiState.LocalExtractionReady ?: return null
        isGeminiRunning = true

        val image = currentState.capturedImage
        val ocrResult = currentState.ocrResult
        val localResult = currentState.localResult
        val scanId = currentScanId ?: AppLog.newScanId()

        val action = AppLog.recordUserAction("Capture Screen", "Confirm & Analyze with AI clicked", "scanId=$scanId")
        AppLog.recordStateTransition(action, "CaptureUiState: GeminiProcessing")
        _uiState.value = CaptureUiState.GeminiProcessing(image, "Analyzing with AI…")

        inFlightExtractionJobId?.let { id ->
            AppLog.endExtractionJob(id, outcome = "CANCELLED")
        }
        inFlightExtractionJob?.cancel()

        val extractionJobId = AppLog.startExtractionJob(scanId = scanId, operation = "RefineWithGemini", dispatcherLabel = "IO")
        inFlightExtractionJobId = extractionJobId

        val job = viewModelScope.launch {
            try {
                val canUseCloud = canScanUseCase?.invoke() ?: true
                if (!canUseCloud) {
                    AppLog.recordUserAction("Capture Screen", "Cloud quota reached: fallback to local scan")
                    AppLog.endExtractionJob(extractionJobId, outcome = "FALLBACK_QUOTA")
                    isGeminiRunning = false
                    val fallbackCandidate = app.memora.domain.extraction.ExtractedMemoryCandidate(
                        suggestedTitle = localResult.title,
                        suggestedEventType = localResult.eventType ?: EventType.OTHER,
                        suggestedEventDate = localResult.selectedEventDate,
                        confidence = localResult.confidence,
                        rawTextSnippet = ocrResult.rawText,
                        requiresConfirmation = true,
                        alternatives = localResult.dateCandidates,
                        extractionSource = app.memora.domain.extraction.ExtractionSource.LOCAL_OCR,
                    )
                    val fallbackStage3 = app.memora.domain.extraction.Stage3ExtractionResult(
                        candidate = fallbackCandidate,
                        rawOcrText = ocrResult.rawText,
                        normalizedText = ocrResult.rawText,
                        disposition = app.memora.domain.extraction.ExtractionDisposition.HIGH_CONFIDENCE,
                        alternatives = localResult.dateCandidates,
                    )
                    _uiState.value = CaptureUiState.ExtractionReady(image, fallbackStage3)
                    return@launch
                }
                val refineResult = refineWithGeminiUseCase(image, ocrResult, localResult, scanId)
                if (currentScanId != scanId) {
                    AppLog.endExtractionJob(extractionJobId, outcome = "CANCELLED")
                    return@launch
                }
                refineResult.fold(
                    onSuccess = { result ->
                        if (currentScanId != scanId) return@fold
                        AppLog.endExtractionJob(extractionJobId, outcome = "SUCCESS")
                        AppLog.recordStateTransition(action, "CaptureUiState: ExtractionReady", result = "SUCCESS")
                        _uiState.value = CaptureUiState.ExtractionReady(image, result)
                    },
                    onFailure = { error ->
                        if (currentScanId != scanId) return@fold
                        AppLog.endExtractionJob(extractionJobId, outcome = "FAILED", error = error.message)
                        AppLog.recordStateTransition(action, "CaptureUiState: Error (${error.javaClass.simpleName})", result = "FAILED")
                        crashReporter.recordException(error)
                        _uiState.value = CaptureUiState.Error(
                            message = "AI analysis was unable to determine details. You can retry, enter manually, or rescan.",
                            returnState = CaptureUiState.CameraActive,
                            canRetry = true,
                            canEnterManually = true,
                            capturedImage = image,
                        )
                    }
                )
            } catch (c: CancellationException) {
                AppLog.endExtractionJob(extractionJobId, outcome = "CANCELLED")
                throw c
            } finally {
                isGeminiRunning = false
                if (inFlightExtractionJobId == extractionJobId) {
                    inFlightExtractionJobId = null
                }
            }
        }
        inFlightExtractionJob = job
        return job
    }

    /**
     * Discards the active scan and resets immediately to the Camera viewfinder.
     * Cancels any in-flight extraction job and deletes temporary storage.
     * Guaranteed Cost Invariant: 0 Gemini calls.
     */
    fun rescan() {
        val action = AppLog.recordUserAction("Capture Screen", "Rescan clicked")
        inFlightExtractionJobId?.let { id ->
            AppLog.endExtractionJob(id, outcome = "CANCELLED")
        }
        inFlightExtractionJobId = null
        inFlightExtractionJob?.cancel()
        inFlightExtractionJob = null
        isGeminiRunning = false
        lastPackagingDetection = null

        val uriToDelete = activeTemporaryImage?.uri ?: lastCapturedImageUri
        if (uriToDelete != null) {
            imageStorage.deleteImage(uriToDelete)
            lastCapturedImageUri = null
        }

        AppLog.recordStateTransition(action, "CaptureUiState: CameraActive")
        _uiState.value = CaptureUiState.CameraActive
    }

    fun startManualEntry(image: CapturedImage) {
        val action = AppLog.recordUserAction("Capture Screen", "Enter Manually clicked", "uri=${image.uri}")
        inFlightExtractionJobId?.let { id ->
            AppLog.endExtractionJob(id, outcome = "CANCELLED")
        }
        inFlightExtractionJobId = null
        inFlightExtractionJob?.cancel()
        inFlightExtractionJob = null
        isGeminiRunning = false

        val existingLocal = (_uiState.value as? CaptureUiState.LocalExtractionReady)?.localResult
        val preservedTitle = existingLocal?.title ?: ""
        val preservedEventType = existingLocal?.eventType ?: EventType.OTHER

        AppLog.recordStateTransition(action, "CaptureUiState: ExtractionReady (Manual)")
        val emptyResult = app.memora.domain.extraction.Stage3ExtractionResult(
            candidate = app.memora.domain.extraction.ExtractedMemoryCandidate(
                suggestedTitle = preservedTitle,
                suggestedEventType = preservedEventType,
                suggestedEventDate = null,
                requiresConfirmation = true,
                alternatives = existingLocal?.dateCandidates ?: emptyList(),
            ),
            rawOcrText = "",
            normalizedText = "",
            disposition = app.memora.domain.extraction.ExtractionDisposition.NO_ACTIONABLE_DATE
        )
        _uiState.value = CaptureUiState.ExtractionReady(image, emptyResult)
    }

    /**
     * User requested retaking (camera) or replacing (picker) the image.
     */
    fun retakeOrReplaceImage(onLaunchPicker: ((CaptureSource) -> Unit)? = null) {
        val currentState = _uiState.value
        if (currentState is CaptureUiState.Preview) {
            val previousImage = currentState.capturedImage
            val action = AppLog.recordUserAction("Capture Screen", "Retake or Replace Image clicked", "source=${previousImage.source}")
            when (previousImage.source) {
                CaptureSource.CAMERA -> {
                    // Retake immediately returns to camera viewfinder; old photo discarded
                    imageStorage.deleteImage(previousImage.uri)
                    AppLog.recordStateTransition(action, "CaptureUiState: CameraActive")
                    _uiState.value = CaptureUiState.CameraActive
                }
                CaptureSource.GALLERY, CaptureSource.SCREENSHOT -> {
                    // For picker replacement: invoke picker launcher.
                    // DO NOT delete old image yet! Wait until replacement succeeds.
                    AppLog.recordStateTransition(action, "CaptureUiState: Picker Launch")
                    onLaunchPicker?.invoke(previousImage.source)
                }
            }
        } else {
            val action = AppLog.recordUserAction("Capture Screen", "Retake or Replace Image clicked (Fallback)")
            AppLog.recordStateTransition(action, "CaptureUiState: Idle")
            _uiState.value = CaptureUiState.Idle
        }
    }

    /**
     * Backward-compatible alias for retakeOrReplaceImage.
     */
    fun replaceImage(onLaunchPicker: ((CaptureSource) -> Unit)? = null) {
        retakeOrReplaceImage(onLaunchPicker)
    }

    /**
     * User cancelled capture or navigated back.
     * Any unconfirmed active temporary image is cleaned up from storage.
     */
    fun cancelCapture() {
        val action = AppLog.recordUserAction("Capture Screen", "Cancel Capture clicked")
        val currentState = _uiState.value
        if (currentState is CaptureUiState.Preview) {
            imageStorage.deleteImage(currentState.capturedImage.uri)
        }
        AppLog.recordStateTransition(action, "CaptureUiState: Idle")
        _uiState.value = CaptureUiState.Idle
    }

    /**
     * Closes camera viewfinder or resets capture flow back to the primary options.
     */
    fun resetToIdle() {
        cancelCapture()
    }

    /**
     * Dismisses active error and returns to the safe preceding state.
     */
    fun dismissError() {
        val action = AppLog.recordUserAction("Capture Screen", "Dismiss Error clicked")
        val currentState = _uiState.value
        if (currentState is CaptureUiState.Error) {
            AppLog.recordStateTransition(action, "CaptureUiState: ${currentState.returnState.javaClass.simpleName}")
            _uiState.value = currentState.returnState
        } else {
            AppLog.recordStateTransition(action, "CaptureUiState: Idle")
            _uiState.value = CaptureUiState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure no unconfirmed temporary image leaks on ViewModel destruction
        val currentState = _uiState.value
        activeTemporaryImage?.let { imageStorage.deleteImage(it.uri) }
    }

    /**
     * Stage 5: Wire onSave from ExtractedMemoryBottomSheet:
     * - Call CreateMemoryUseCase → returns memoryId
     * - Calculate reminderStartDate = eventDate.minusDays(remindDaysBefore)
     * - Persist Memory + initial Reminder atomically
     * - Delete temporary image via ImageStorage
     * - Emit navigation event → Home
     */
    fun saveMemory(
        title: String,
        eventType: EventType,
        eventDate: LocalDate,
        remindDaysBefore: Int,
        timeZoneId: String = ZoneId.systemDefault().id,
    ): Job {
        if (isSaving && inFlightSaveJob?.isActive == true) {
            return inFlightSaveJob!!
        }
        isSaving = true
        val job = viewModelScope.launch {
            val action = AppLog.recordUserAction("Capture Screen", "Save Memory clicked", "title=$title, date=$eventDate")
            AppLog.recordStateTransition(action, "CaptureUiState: Processing (Saving memory)")
            _uiState.value = CaptureUiState.Processing("Saving memory...")
            val memoryId = UUID.randomUUID().toString()
            val reminderId = UUID.randomUUID().toString()
            val now = java.time.Instant.now()
            val reminderStartDate = eventDate.minusDays(remindDaysBefore.toLong())
            val initialReminder = Reminder(
                id = reminderId,
                memoryId = memoryId,
                reminderStartDate = reminderStartDate,
                createdAt = now,
                updatedAt = now,
            )

            val dbStartTime = System.currentTimeMillis()
            val currentScanId = AppLog.currentScan?.scanId ?: "N/A"
            try {
                // Persist Memory + initial Reminder atomically through the repository boundary.
                createMemoryUseCase(
                    title = title,
                    eventType = eventType,
                    eventDate = eventDate,
                    timeZoneId = timeZoneId,
                    id = memoryId,
                    initialReminder = initialReminder,
                ).getOrThrow()

                // Scheduling is an external side effect. Treat failure as save failure and
                // compensate by removing the atomically-created Memory + Reminder. Keep the
                // temporary image so the user can retry instead of losing their capture.
                val schedulingResult = notificationScheduler.scheduleReminder(
                    memoryId = memoryId,
                    reminderId = reminderId,
                    title = title,
                    reminderStartDate = reminderStartDate,
                    eventDate = eventDate,
                    timeZoneId = timeZoneId,
                    reminderTime = initialReminder.reminderTime ?: java.time.LocalTime.of(9, 0),
                )
                schedulingResult.getOrElse { schedulingError ->
                    // Best-effort cancellation handles an ambiguous scheduler failure where the
                    // platform may have accepted work before reporting an error.
                    notificationScheduler.cancelReminder(reminderId)
                    deleteMemoryUseCase(memoryId)
                        .getOrElse { rollbackError ->
                            throw IllegalStateException(
                                "Notification scheduling failed and database rollback also failed.",
                                rollbackError,
                            )
                        }
                    throw IllegalStateException(
                        "Notification scheduling failed; Memory creation was rolled back.",
                        schedulingError,
                    )
                }

                val dbDuration = System.currentTimeMillis() - dbStartTime
                AppLog.recordPersistence(
                    scanId = currentScanId,
                    saveStatus = "SUCCESS",
                    reminderStatus = "SCHEDULED",
                    dbDurationMs = dbDuration,
                )

                val uriToDelete = activeTemporaryImage?.uri ?: lastCapturedImageUri
                if (uriToDelete != null) {
                    imageStorage.deleteImage(uriToDelete)
                    lastCapturedImageUri = null
                }

                analyticsLogger.logEvent("memory_saved")
                AppLog.recordStateTransition(action, "CaptureUiState: Idle / NavigateToHome", result = "SUCCESS")
                _uiState.value = CaptureUiState.Idle
                _navigationEvents.emit(CaptureNavEvent.NavigateToHome)
            } catch (e: Throwable) {
                val dbDuration = System.currentTimeMillis() - dbStartTime
                AppLog.recordPersistence(
                    scanId = currentScanId,
                    saveStatus = "FAILED",
                    reminderStatus = "FAILED",
                    dbDurationMs = dbDuration,
                )
                AppLog.recordStateTransition(action, "CaptureUiState: Error (${e.javaClass.simpleName})", result = "FAILED")
                if (e is CancellationException) throw e
                crashReporter.recordException(e)
                _uiState.value = CaptureUiState.Error(
                    message = "Failed to save. Please try again.",
                    returnState = CaptureUiState.Idle
                )
            } finally {
                isSaving = false
                inFlightSaveJob = null
            }
        }
        inFlightSaveJob = job
        return job
    }

    class Factory(
        private val processCapturedImageUseCase: ProcessCapturedImageUseCase,
        private val imageStorage: ImageStorage,
        private val createMemoryUseCase: CreateMemoryUseCase,
        private val deleteMemoryUseCase: DeleteMemoryUseCase,
        private val notificationScheduler: NotificationScheduler,
        private val extractLocallyUseCase: ExtractLocallyUseCase,
        private val refineWithGeminiUseCase: RefineWithGeminiUseCase,
        private val crashReporter: app.memora.domain.logging.CrashReporter,
        private val analyticsLogger: app.memora.domain.logging.AnalyticsLogger,
        private val extractCapturedImageUseCase: ExtractCapturedImageUseCase? = null,
        private val canScanUseCase: app.memora.domain.usecase.CanScanUseCase? = null,
    ) : ViewModelProvider.Factory {

        constructor(
            processCapturedImageUseCase: ProcessCapturedImageUseCase,
            imageStorage: ImageStorage,
            createMemoryUseCase: CreateMemoryUseCase,
            deleteMemoryUseCase: DeleteMemoryUseCase,
            notificationScheduler: NotificationScheduler,
            extractCapturedImageUseCase: ExtractCapturedImageUseCase,
            crashReporter: app.memora.domain.logging.CrashReporter,
            analyticsLogger: app.memora.domain.logging.AnalyticsLogger,
        ) : this(
            processCapturedImageUseCase = processCapturedImageUseCase,
            imageStorage = imageStorage,
            createMemoryUseCase = createMemoryUseCase,
            deleteMemoryUseCase = deleteMemoryUseCase,
            notificationScheduler = notificationScheduler,
            extractLocallyUseCase = ExtractLocallyUseCase(
                textExtractor = object : app.memora.platform.ocr.TextExtractor {
                    override suspend fun extractText(imageUri: String) = Result.success(app.memora.platform.ocr.ExtractedText(""))
                    override val isAvailable: Boolean = true
                },
                barcodeExtractor = object : app.memora.platform.ocr.BarcodeExtractor {
                    override suspend fun extractBarcode(imageUri: String) = Result.success(null)
                },
                deterministicExtractor = app.memora.domain.extraction.DeterministicMemoryExtractor(),
            ),
            refineWithGeminiUseCase = RefineWithGeminiUseCase(
                geminiExtractor = app.memora.domain.extraction.DeterministicMemoryExtractor(),
            ),
            crashReporter = crashReporter,
            analyticsLogger = analyticsLogger,
            extractCapturedImageUseCase = extractCapturedImageUseCase,
        )

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CaptureViewModel::class.java)) {
                return CaptureViewModel(
                    processCapturedImageUseCase = processCapturedImageUseCase,
                    imageStorage = imageStorage,
                    createMemoryUseCase = createMemoryUseCase,
                    deleteMemoryUseCase = deleteMemoryUseCase,
                    notificationScheduler = notificationScheduler,
                    extractLocallyUseCase = extractLocallyUseCase,
                    refineWithGeminiUseCase = refineWithGeminiUseCase,
                    crashReporter = crashReporter,
                    analyticsLogger = analyticsLogger,
                    extractCapturedImageUseCase = extractCapturedImageUseCase,
                    canScanUseCase = canScanUseCase,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
