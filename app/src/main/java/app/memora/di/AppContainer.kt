package app.memora.di

import android.content.Context
import app.memora.data.local.MemoraDatabase
import app.memora.data.repository.DefaultMemoryRepository
import app.memora.data.repository.DefaultReminderRepository
import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.domain.repository.MemoryRepository
import app.memora.domain.repository.ReminderRepository
import app.memora.domain.scheduler.ClockProvider
import app.memora.domain.scheduler.SystemClockProvider
import app.memora.domain.usecase.CalculateReminderStatusUseCase
import app.memora.domain.usecase.CompleteReminderUseCase
import app.memora.domain.usecase.UncompleteReminderUseCase
import app.memora.domain.usecase.CreateMemoryUseCase
import app.memora.domain.usecase.CreateReminderUseCase
import app.memora.domain.usecase.DeleteMemoryUseCase
import app.memora.domain.usecase.DeleteReminderUseCase
import app.memora.domain.usecase.ExtractCapturedImageUseCase
import app.memora.domain.usecase.ExtractLocallyUseCase
import app.memora.domain.usecase.RefineWithGeminiUseCase
import app.memora.domain.usecase.GetMemoryUseCase
import app.memora.domain.usecase.GetReminderUseCase
import app.memora.domain.usecase.IsReminderNotificationActiveUseCase
import app.memora.domain.usecase.ObserveMemoriesUseCase
import app.memora.domain.usecase.ObserveRemindersUseCase
import app.memora.domain.usecase.ProcessCapturedImageUseCase
import app.memora.platform.camera.CameraXImageCapture
import app.memora.platform.camera.CameraXManager
import app.memora.platform.camera.ImageCapture
import app.memora.platform.camera.Stage1ImageCapture
import app.memora.platform.image.AppImageStorage
import app.memora.platform.image.ImagePicker
import app.memora.platform.image.ImageStorage
import app.memora.platform.image.Stage1ImagePicker
import app.memora.platform.image.SystemPhotoPicker
import app.memora.platform.notification.MemoraNotificationScheduler
import app.memora.platform.notification.NotificationScheduler
import app.memora.platform.notification.Stage1NotificationScheduler
import app.memora.domain.extraction.DeterministicMemoryExtractor
import app.memora.domain.extraction.MemoryExtractor
import app.memora.platform.ocr.MlKitTextExtractor
import app.memora.platform.ocr.Stage1TextExtractor
import app.memora.platform.ocr.TextExtractor
import app.memora.platform.ocr.BarcodeExtractor
import app.memora.platform.ocr.MlKitBarcodeExtractor
import java.io.File
import java.time.Instant

import app.memora.domain.gateway.BillingGateway
import app.memora.domain.repository.EntitlementRepository
import app.memora.domain.usecase.GetEntitlementUseCase
import app.memora.domain.usecase.CanScanUseCase
import app.memora.domain.usecase.CanSaveMemoryUseCase

/**
 * Dependency container defining application-wide dependencies.
 * Avoids global mutable singletons and heavy DI frameworks.
 *
 * CRITICAL ARCHITECTURAL CONTRACT:
 * Production persistence is strictly backed by [MemoraDatabase] (Room SQLite).
 * No in-memory fallback is permitted in production.
 */
interface AppContainer {
    val database: MemoraDatabase
    var clockProvider: ClockProvider
    val memoryRepository: MemoryRepository
    val reminderRepository: ReminderRepository
    val entitlementRepository: EntitlementRepository
    val authRepository: app.memora.domain.repository.AuthRepository
    val googleAuthClient: app.memora.platform.auth.GoogleAuthClient
    val firestoreSyncManager: app.memora.data.sync.FirestoreSyncManager
    val billingGateway: BillingGateway
    val crashReporter: app.memora.domain.logging.CrashReporter
    val analyticsLogger: app.memora.domain.logging.AnalyticsLogger

    // Use cases
    val getEntitlementUseCase: GetEntitlementUseCase
    val canScanUseCase: CanScanUseCase
    val canSaveMemoryUseCase: CanSaveMemoryUseCase
    val calculateReminderStatusUseCase: CalculateReminderStatusUseCase
    val isReminderNotificationActiveUseCase: IsReminderNotificationActiveUseCase
    val observeMemoriesUseCase: ObserveMemoriesUseCase
    val observeRemindersUseCase: ObserveRemindersUseCase
    val getMemoryUseCase: GetMemoryUseCase
    val getReminderUseCase: GetReminderUseCase
    val createMemoryUseCase: CreateMemoryUseCase
    val updateMemoryUseCase: app.memora.domain.usecase.UpdateMemoryUseCase
    val createReminderUseCase: CreateReminderUseCase
    val completeReminderUseCase: CompleteReminderUseCase
    val uncompleteReminderUseCase: UncompleteReminderUseCase
    val deleteMemoryUseCase: DeleteMemoryUseCase
    val deleteReminderUseCase: DeleteReminderUseCase
    val snoozeReminderUseCase: app.memora.domain.usecase.SnoozeReminderUseCase

    // Stage 2: Capture system dependencies
    val imageStorage: ImageStorage
    val cameraXManager: CameraXManager?
    val processCapturedImageUseCase: ProcessCapturedImageUseCase
    val extractCapturedImageUseCase: ExtractCapturedImageUseCase
    val extractLocallyUseCase: ExtractLocallyUseCase
    val refineWithGeminiUseCase: RefineWithGeminiUseCase

    // Platform abstractions
    val imageCapture: ImageCapture
    val imagePicker: ImagePicker
    val textExtractor: TextExtractor
    val barcodeExtractor: BarcodeExtractor
    val memoryExtractor: MemoryExtractor
    val notificationScheduler: NotificationScheduler
    val selectableTextExtractor: app.memora.platform.ocr.SelectableTextExtractor? get() = null
    val onboardingPreferences: app.memora.data.local.OnboardingPreferences
}

/**
 * Default production implementation of [AppContainer] wiring dependencies cleanly.
 */
class DefaultAppContainer(
    private val context: Context? = null,
    providedDatabase: MemoraDatabase? = null,
) : AppContainer {

    override val database: MemoraDatabase by lazy {
        providedDatabase
            ?: context?.let { MemoraDatabase.getInstance(it.applicationContext) }
            ?: throw IllegalStateException(
                "A non-null Context or MemoraDatabase instance must be provided to DefaultAppContainer. " +
                "Production persistence strictly requires Room."
            )
    }

    override var clockProvider: ClockProvider = SystemClockProvider()

    override val memoryRepository: MemoryRepository by lazy {
        DefaultMemoryRepository(database.memoryDao(), database.reminderDao())
    }

    override val reminderRepository: ReminderRepository by lazy {
        DefaultReminderRepository(database.reminderDao(), database.memoryDao())
    }

    override val entitlementRepository: EntitlementRepository by lazy {
        app.memora.data.repository.DefaultEntitlementRepository(onboardingPreferences)
    }

    override val authRepository: app.memora.domain.repository.AuthRepository by lazy {
        app.memora.data.repository.DefaultAuthRepository(
            onboardingPreferences = onboardingPreferences,
            onSignOutCleanup = {
                database.clearAllTables()
            },
        )
    }

    override val googleAuthClient: app.memora.platform.auth.GoogleAuthClient by lazy {
        app.memora.platform.auth.GoogleAuthClient(context)
    }

    override val firestoreSyncManager: app.memora.data.sync.FirestoreSyncManager by lazy {
        app.memora.data.sync.FirestoreSyncManager(
            memoryDao = database.memoryDao(),
            reminderDao = database.reminderDao(),
            authRepository = authRepository,
            notificationScheduler = notificationScheduler,
            scanEvidenceDao = database.scanEvidenceDao(),
            onboardingPreferences = onboardingPreferences,
        )
    }

    override val billingGateway: BillingGateway by lazy {
        val isRobolectric = runCatching { Class.forName("org.robolectric.Robolectric") }.isSuccess
        if (isRobolectric || context == null) {
            app.memora.data.gateway.DefaultBillingGateway()
        } else {
            app.memora.platform.billing.PlayBillingGateway(
                context = context,
                onboardingPreferences = onboardingPreferences,
            )
        }
    }

    override val getEntitlementUseCase: GetEntitlementUseCase by lazy {
        GetEntitlementUseCase(entitlementRepository)
    }

    override val canScanUseCase: CanScanUseCase by lazy {
        CanScanUseCase(entitlementRepository)
    }

    override val canSaveMemoryUseCase: CanSaveMemoryUseCase by lazy {
        CanSaveMemoryUseCase(entitlementRepository)
    }

    override val calculateReminderStatusUseCase: CalculateReminderStatusUseCase by lazy {
        CalculateReminderStatusUseCase(clockProvider)
    }

    override val isReminderNotificationActiveUseCase: IsReminderNotificationActiveUseCase by lazy {
        IsReminderNotificationActiveUseCase(calculateReminderStatusUseCase)
    }

    override val observeMemoriesUseCase: ObserveMemoriesUseCase by lazy {
        ObserveMemoriesUseCase(memoryRepository, calculateReminderStatusUseCase)
    }

    override val observeRemindersUseCase: ObserveRemindersUseCase by lazy {
        ObserveRemindersUseCase(reminderRepository, memoryRepository, calculateReminderStatusUseCase)
    }

    override val getMemoryUseCase: GetMemoryUseCase by lazy {
        GetMemoryUseCase(memoryRepository, calculateReminderStatusUseCase)
    }

    override val getReminderUseCase: GetReminderUseCase by lazy {
        GetReminderUseCase(reminderRepository, memoryRepository, calculateReminderStatusUseCase)
    }

    override val createMemoryUseCase: CreateMemoryUseCase by lazy {
        CreateMemoryUseCase(memoryRepository)
    }

    override val updateMemoryUseCase: app.memora.domain.usecase.UpdateMemoryUseCase by lazy {
        app.memora.domain.usecase.UpdateMemoryUseCase(memoryRepository, reminderRepository)
    }

    override val createReminderUseCase: CreateReminderUseCase by lazy {
        CreateReminderUseCase(reminderRepository, memoryRepository)
    }

    override val completeReminderUseCase: CompleteReminderUseCase by lazy {
        CompleteReminderUseCase(reminderRepository, clockProvider)
    }

    override val uncompleteReminderUseCase: app.memora.domain.usecase.UncompleteReminderUseCase by lazy {
        app.memora.domain.usecase.UncompleteReminderUseCase(reminderRepository)
    }

    override val deleteMemoryUseCase: DeleteMemoryUseCase by lazy {
        DeleteMemoryUseCase(memoryRepository) { id ->
            firestoreSyncManager.deleteRemoteMemory(id)
        }
    }

    override val deleteReminderUseCase: DeleteReminderUseCase by lazy {
        DeleteReminderUseCase(reminderRepository)
    }

    override val snoozeReminderUseCase: app.memora.domain.usecase.SnoozeReminderUseCase by lazy {
        app.memora.domain.usecase.SnoozeReminderUseCase(
            reminderRepository = reminderRepository,
            memoryRepository = memoryRepository,
            notificationScheduler = notificationScheduler,
            clockProvider = clockProvider,
        )
    }

    // Stage 2 Capture System
    override val imageStorage: ImageStorage by lazy {
        if (context != null) {
            AppImageStorage(context.applicationContext)
        } else {
            InMemoryImageStorage()
        }
    }

    override val cameraXManager: CameraXManager? by lazy {
        context?.let { CameraXManager(it.applicationContext) }
    }

    override val processCapturedImageUseCase: ProcessCapturedImageUseCase by lazy {
        ProcessCapturedImageUseCase(imageStorage)
    }

    override val extractCapturedImageUseCase: ExtractCapturedImageUseCase by lazy {
        ExtractCapturedImageUseCase(textExtractor, memoryExtractor)
    }

    override val extractLocallyUseCase: ExtractLocallyUseCase by lazy {
        val barcodePrefs = context?.applicationContext?.getSharedPreferences(
            "memora_barcode_catalog",
            Context.MODE_PRIVATE
        )
        ExtractLocallyUseCase(
            textExtractor = textExtractor,
            barcodeExtractor = barcodeExtractor,
            deterministicExtractor = DeterministicMemoryExtractor(clockProvider),
            offlineCatalog = app.memora.data.local.OfflineProductCatalog(barcodePrefs),
        )
    }

    override val refineWithGeminiUseCase: RefineWithGeminiUseCase by lazy {
        RefineWithGeminiUseCase(
            geminiExtractor = if (context != null) {
                app.memora.domain.extraction.CloudGeminiMemoryExtractor(
                    context.applicationContext
                )
            } else {
                DeterministicMemoryExtractor(clockProvider)
            }
        )
    }

    // Platform abstractions
    override val imageCapture: ImageCapture by lazy {
        val manager = cameraXManager
        if (manager != null) {
            CameraXImageCapture(manager, imageStorage)
        } else {
            Stage1ImageCapture()
        }
    }

    override val imagePicker: ImagePicker by lazy {
        if (context != null) {
            SystemPhotoPicker(context.applicationContext)
        } else {
            Stage1ImagePicker()
        }
    }

    val mlKitTextExtractor: TextExtractor by lazy {
        if (context != null) {
            MlKitTextExtractor(context.applicationContext)
        } else {
            Stage1TextExtractor()
        }
    }

    override val selectableTextExtractor: app.memora.platform.ocr.SelectableTextExtractor by lazy {
        app.memora.platform.ocr.SelectableTextExtractor(
            mlKitExtractor = mlKitTextExtractor,
            rapidOcrExtractor = mlKitTextExtractor,
            activeEngine = app.memora.platform.ocr.ActiveOcrEngine.ML_KIT,
        )
    }

    override val textExtractor: TextExtractor by lazy {
        mlKitTextExtractor
    }

    override val barcodeExtractor: app.memora.platform.ocr.BarcodeExtractor by lazy {
        if (context != null) {
            app.memora.platform.ocr.MlKitBarcodeExtractor(context.applicationContext)
        } else {
            object : app.memora.platform.ocr.BarcodeExtractor {
                override suspend fun extractBarcode(imageUri: String) = Result.success(null)
            }
        }
    }

    override val memoryExtractor: MemoryExtractor by lazy {
        if (context != null) {
            app.memora.domain.extraction.CompositeMemoryExtractor(
                primary = app.memora.domain.extraction.CloudGeminiMemoryExtractor(
                    context.applicationContext
                ),
                fallback = DeterministicMemoryExtractor(clockProvider)
            )
        } else {
            DeterministicMemoryExtractor(clockProvider)
        }
    }

    override val notificationScheduler: NotificationScheduler by lazy {
        if (context != null) {
            MemoraNotificationScheduler(context.applicationContext, clockProvider)
        } else {
            Stage1NotificationScheduler()
        }
    }
    override val crashReporter: app.memora.domain.logging.CrashReporter by lazy {
        if (context != null) {
            app.memora.platform.logging.FirebaseCrashReporter()
        } else {
            object : app.memora.domain.logging.CrashReporter {
                override fun recordException(throwable: Throwable) {}
                override fun log(message: String) {}
                override fun setUserId(userId: String) {}
                override fun setCustomKey(key: String, value: String) {}
            }
        }
    }

    override val analyticsLogger: app.memora.domain.logging.AnalyticsLogger by lazy {
        if (context != null) {
            app.memora.platform.logging.FirebaseAnalyticsLogger(context.applicationContext)
        } else {
            object : app.memora.domain.logging.AnalyticsLogger {
                override fun logEvent(eventName: String, params: Map<String, String>?) {}
                override fun setUserProperty(name: String, value: String) {}
            }
        }
    }

    override val onboardingPreferences: app.memora.data.local.OnboardingPreferences by lazy {
        if (context != null) {
            app.memora.data.local.DefaultOnboardingPreferences(context.applicationContext)
        } else {
            object : app.memora.data.local.OnboardingPreferences {
                private var completed = true
                private var categories = app.memora.data.local.DefaultOnboardingPreferences.defaultCategories
                private var reminderTime = java.time.LocalTime.of(9, 0)
                private var scanCount = 0
                private var isPro = false
                override fun hasCompletedOnboarding(): Boolean = completed
                override fun setOnboardingCompleted(completed: Boolean) { this.completed = completed }
                override fun getSelectedCategories(): Set<String> = categories
                override fun setSelectedCategories(categories: Set<String>) { this.categories = categories }
                override fun getDefaultReminderTime(): java.time.LocalTime = reminderTime
                override fun setDefaultReminderTime(time: java.time.LocalTime) { this.reminderTime = time }
                override fun getMonthlyScanCount(): Int = scanCount
                override fun incrementMonthlyScanCount(): Int = ++scanCount
                override fun getRemainingMonthlyScans(limit: Int): Int = (limit - scanCount).coerceAtLeast(0)
                override fun isProUser(): Boolean = isPro
                override fun setProUser(isPro: Boolean) { this.isPro = isPro }
                private var customCategories = mutableSetOf<String>()
                override fun getCustomCategories(): Set<String> = customCategories
                override fun addCustomCategory(category: String) { customCategories.add(category) }
                private var authUid: String? = null
                private var authEmail: String? = null
                private var authName: String? = null
                private var authPhoto: String? = null
                override fun getAuthUid(): String? = authUid
                override fun getAuthEmail(): String? = authEmail
                override fun getAuthDisplayName(): String? = authName
                override fun getAuthPhotoUrl(): String? = authPhoto
                override fun setAuth(uid: String, email: String?, displayName: String?, photoUrl: String?) {
                    this.authUid = uid
                    this.authEmail = email
                    this.authName = displayName
                    this.authPhoto = photoUrl
                }
                override fun clearAuth() {
                    this.authUid = null
                    this.authEmail = null
                    this.authName = null
                    this.authPhoto = null
                }
                private var promptedNotification = false
                override fun hasPromptedNotificationPermission(): Boolean = promptedNotification
                override fun setPromptedNotificationPermission(prompted: Boolean) { this.promptedNotification = prompted }
            }
        }
    }
}

/**
 * Lightweight in-memory fallback for testing or non-Context environments.
 */
class InMemoryImageStorage : ImageStorage {
    override fun createCaptureFile(prefix: String): File {
        return File.createTempFile(prefix, ".jpg").apply { deleteOnExit() }
    }

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
            fileSize = if (file.exists()) file.length() else 0L,
        )
    }

    override fun deleteImage(uriString: String): Boolean = true

    override suspend fun compressForEvidence(uriString: String): ByteArray? {
        return if (uriString.isNotBlank()) "compressed:$uriString".toByteArray(Charsets.UTF_8) else null
    }
}
