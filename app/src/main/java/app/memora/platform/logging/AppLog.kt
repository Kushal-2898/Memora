package app.memora.platform.logging

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import timber.log.Timber
import app.memora.BuildConfig
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

/**
 * Diagnostic failure classification for Gemini API interactions.
 * Kept for backward compatibility with Gemini extractor.
 */
enum class GeminiFailureReason {
    AUTHENTICATION_ERROR,
    NETWORK_ERROR,
    TIMEOUT,
    RATE_LIMIT,
    INVALID_REQUEST,
    MODEL_NOT_FOUND,
    SAFETY_BLOCK,
    SERVER_ERROR,
    MALFORMED_RESPONSE,
    UNKNOWN;

    companion object {
        fun fromThrowable(t: Throwable): GeminiFailureReason {
            val msg = t.message?.lowercase().orEmpty()
            val className = t.javaClass.simpleName.lowercase()
            val causeMsg = t.cause?.message?.lowercase().orEmpty()
            val causeClass = t.cause?.javaClass?.simpleName?.lowercase().orEmpty()
            val fullMsg = "$msg $causeMsg"
            
            // Strongest signals first based on HTTP codes or explicit blocks
            return when {
                msg.contains("safety") || causeMsg.contains("safety") || className.contains("safetypolicy") -> SAFETY_BLOCK
                msg.contains("429") || msg.contains("resource_exhausted") || msg.contains("quota") || msg.contains("too many requests") -> RATE_LIMIT
                msg.contains("401") || msg.contains("unauthorized") || msg.contains("403") || msg.contains("forbidden") || msg.contains("api_key") -> AUTHENTICATION_ERROR
                msg.contains("404") || msg.contains("not found") -> MODEL_NOT_FOUND
                msg.contains("400") || msg.contains("bad request") || (msg.contains("invalid") && msg.contains("request")) -> INVALID_REQUEST
                msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") || msg.contains("internal server error") || msg.contains("unavailable") -> SERVER_ERROR
                
                // Connection/Timeout signals
                msg.contains("timeout") || className.contains("timeout") || causeMsg.contains("timeout") || causeClass.contains("timeout") -> TIMEOUT
                msg.contains("unable to resolve host") || msg.contains("connect") || className.contains("unknownhost") || className.contains("socket") || className.contains("ioexception") || causeClass.contains("ioexception") -> NETWORK_ERROR
                
                // Parsing signals
                msg.contains("json") || className.contains("json") || msg.contains("parse") || msg.contains("validation") || msg.contains("schema") -> MALFORMED_RESPONSE
                
                // Fallbacks to exception types
                className.contains("serverexception") -> SERVER_ERROR
                className.contains("serialization") -> MALFORMED_RESPONSE
                
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Comprehensive error category taxonomy for system-wide failure diagnosis.
 */
enum class DiagnosticErrorCategory {
    NETWORK_UNAVAILABLE,
    NETWORK_TIMEOUT,
    HTTP_4XX,
    HTTP_401,
    HTTP_403,
    HTTP_429,
    HTTP_5XX,
    SDK_ERROR,
    PARSE_ERROR,
    INVALID_RESPONSE,
    CANCELLATION,
    OUT_OF_MEMORY,
    DATABASE_ERROR,
    NOTIFICATION_ERROR,
    PERMISSION_ERROR,
    UNKNOWN;

    companion object {
        fun fromThrowable(t: Throwable): DiagnosticErrorCategory {
            val msg = t.message?.lowercase().orEmpty()
            val className = t.javaClass.simpleName.lowercase()
            return when {
                t is CancellationException || className.contains("cancellation") -> CANCELLATION
                t is OutOfMemoryError || className.contains("outofmemory") -> OUT_OF_MEMORY
                msg.contains("timeout") || className.contains("timeout") -> NETWORK_TIMEOUT
                msg.contains("unable to resolve host") || msg.contains("connect") ||
                    className.contains("unknownhost") || className.contains("socket") ||
                    className.contains("ioexception") -> NETWORK_UNAVAILABLE
                msg.contains("401") || msg.contains("unauthorized") -> HTTP_401
                msg.contains("403") || msg.contains("forbidden") -> HTTP_403
                msg.contains("429") || msg.contains("resource_exhausted") || msg.contains("quota") -> HTTP_429
                msg.contains("404") || msg.contains("400") || msg.contains("bad request") || msg.contains("not found") -> HTTP_4XX
                msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") || msg.contains("unavailable") -> HTTP_5XX
                className.contains("database") || className.contains("sqlite") || className.contains("room") || msg.contains("database") -> DATABASE_ERROR
                className.contains("security") || msg.contains("permission") || className.contains("permission") -> PERMISSION_ERROR
                className.contains("workmanager") || className.contains("alarm") || className.contains("notification") -> NOTIFICATION_ERROR
                msg.contains("json") || className.contains("json") || msg.contains("parse") -> PARSE_ERROR
                className.contains("generativeai") || className.contains("serverexception") -> SDK_ERROR
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Coroutine lifecycle stages for asynchronous operations.
 */
enum class CoroutineLifecycle {
    STARTED,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMEOUT
}

/**
 * Thread dump snapshot captured during an unresponsiveness incident.
 */
data class ThreadDump(
    val mainThreadStack: List<String>,
    val otherThreads: Map<String, List<String>>,
)

/**
 * Complete freeze evidence captured by the main-thread watchdog.
 */
data class WatchdogFreezeEvidence(
    val timestamp: Instant,
    val eventId: Long,
    val scanId: String?,
    val currentScreen: String,
    val lastUserAction: String?,
    val currentOperation: String?,
    val unresponsiveDurationMs: Long,
    val threadDump: ThreadDump,
)

/**
 * Snapshot of the watchdog's current heartbeat and operational health.
 */
data class WatchdogStatus(
    val running: Boolean,
    val lastHeartbeatMs: Long,
    val heartbeatAgeMs: Long,
    val heartbeatCount: Long,
    val lastUnresponsiveDurationMs: Long,
)

/**
 * Active extraction job record for concurrency and lifecycle tracking.
 */
data class ExtractionJobRecord(
    val extractionJobId: Long,
    val scanId: String,
    val startTimestamp: Instant = Instant.now(),
    var state: String = "RUNNING", // RUNNING, COMPLETED, FAILED, CANCELLED
    var currentOperation: String,
    var threadName: String,
    var dispatcherLabel: String = "UNKNOWN",
    var durationMs: Long = 0L,
)

/**
 * User action record for UI action -> state transition correlation.
 */
data class UiActionRecord(
    val eventId: Long,
    val timestamp: Instant = Instant.now(),
    val action: String,
    var stateTransition: String? = null,
    var coroutineJobId: Long? = null,
    var result: String? = null, // SUCCESS, FAILED
)

/**
 * Memory footprint snapshot without retaining object references.
 */
data class MemorySnapshot(
    val usedHeapBytes: Long,
    val maxHeapBytes: Long,
    val freeHeapBytes: Long,
    val isHighPressure: Boolean,
)

/**
 * Safe network status snapshot.
 */
data class NetworkSnapshot(
    val isConnected: Boolean,
    val transportType: String,
    val isMetered: Boolean,
)

data class CrashReport(
    val timestamp: Instant,
    val eventId: Long,
    val threadName: String,
    val exceptionClass: String,
    val sanitizedMessage: String,
    val stackTrace: List<String>,
    val scanId: String?,
    val lastUserAction: String?,
    val currentScreen: String?,
)

data class DateCandidateDiagnostics(
    val rawCandidate: String,
    val normalizedDate: String?,
    val contextLabel: String?,
    val classification: String,
    val actionable: Boolean,
    var selected: Boolean = false,
    var rejectionReason: String? = null
)

data class LifecycleEventRecord(
    val scanId: String,
    val timestamp: Instant = Instant.now(),
    val eventReason: String,
    val previousState: String?,
    val newState: String?,
    val threadName: String
)

data class OcrAttemptRecord(
    val scanId: String,
    val attemptNumber: Int,
    val startTimestamp: Instant,
    var endTimestamp: Instant? = null,
    var durationMs: Long = 0L,
    val triggerReason: String,
    val previousState: String,
    var endState: String? = null,
    var status: String = "RUNNING",
    val threadName: String,
    val isConcurrent: Boolean,
    val isDuplicateResult: Boolean
)

data class GeminiAttemptRecord(
    val scanId: String,
    val attemptNumber: Int,
    val startTimestamp: Instant,
    var endTimestamp: Instant? = null,
    var durationMs: Long = 0L,
    var status: String = "RUNNING",
    var failureCategory: GeminiFailureReason? = null,
    var httpStatus: Int? = null,
    var exceptionType: String? = null,
    var sanitizedMessage: String? = null,
    var retryable: Boolean = false,
    val threadName: String,
    val dispatcherLabel: String,
    val model: String
)


data class ScanDiagnostics(
    val scanId: String,
    val timestamp: Instant = Instant.now(),
    val imagePresent: Boolean = true,

    // Stage 1: OCR
    var ocrStatus: String = "PENDING", // SUCCESS, FAILURE
    var ocrDurationMs: Long = 0L,
    var ocrCharCount: Int = 0,
    var ocrWordCount: Int = 0,
    var rawOcrText: String? = null,
    var ocrError: String? = null,

    // Stage 2: Gemini
    var geminiStatus: String = "PENDING", // SUCCESS, FAILURE, SKIPPED
    var geminiModel: String = "gemini-3.5-flash",
    var geminiDurationMs: Long = 0L,
    var geminiFailureReason: GeminiFailureReason? = null,
    var geminiErrorDetail: String? = null,
    var rawGeminiJson: String? = null,

    // Stage 3: Candidate Parsing
    var parsedCandidateTitle: String? = null,
    var parsedCandidateDate: String? = null,
    var parsedCandidateEventType: String? = null,
    var parsedCandidateConfidence: Float = 0f,
    var parsedAlternatives: List<String> = emptyList(),

    // Stage 4: Date Selection & Reasoning
    var dateCandidates: List<String> = emptyList(),
    var selectedDate: String? = null,
    var dateSelectionReason: String? = null,

    // Extractor Path & Fallback
    var extractorPath: String = "OCR -> Gemini",
    var fallbackUsed: Boolean = false,
    var fallbackReason: String? = null,

    // Final Disposition & Timing
    var finalDisposition: String? = null,
    var extractorUsed: String? = null,
    var confidence: Float = 0f,
    var parsingDurationMs: Long = 0L,
    var dbDurationMs: Long = 0L,
    var totalDurationMs: Long = 0L,

    // Stage 5: Persistence
    var saveStatus: String? = null, // SUCCESS, FAILED
    var reminderStatus: String? = null,

    // Runtime, Thread & Dispatcher Diagnostics
    var threadName: String? = null,
    var dispatcherLabel: String = "UNKNOWN",
    var isMainThread: Boolean = false,
    var networkState: String? = null,
    var memoryUsedBytes: Long = 0L,
    var memoryMaxBytes: Long = 0L,
    var exceptionCategory: DiagnosticErrorCategory? = null,

    // Advanced Diagnostics tracking
    var ocrAttempts: MutableList<OcrAttemptRecord> = mutableListOf(),
    var geminiAttempts: MutableList<GeminiAttemptRecord> = mutableListOf(),
    var dateCandidateDetails: MutableList<DateCandidateDiagnostics> = mutableListOf(),
    var lifecycleEvents: MutableList<LifecycleEventRecord> = mutableListOf(),
)

/**
 * Thread-safe, non-blocking, non-throwing diagnostic logging and inspection engine.
 *
 * Guarantees:
 * - Never throws exceptions that could disrupt application features.
 * - Never blocks the main thread for I/O operations.
 * - Strictly redacts credentials, API keys, and sensitive tokens.
 * - Retains no Bitmaps, Activity references, or Context references.
 * - Clear operation clears ONLY diagnostic data and files; never user data.
 */
object AppLog {

    private const val TAG = "MemoraLog"
    private const val MAX_LOG_ENTRIES = 1000
    private const val MAX_CACHE_FILE_BYTES = 512 * 1024L // 512 KB
    private const val MAX_STACK_FRAMES_MAIN = 20
    private const val MAX_STACK_FRAMES_OTHER = 5
    private const val MAX_OTHER_THREADS = 8
    const val WATCHDOG_THRESHOLD_MS = 2000L

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    private val globalEventId = AtomicLong(0)
    private val nextExtractionJobId = AtomicLong(0)

    private val logBuffer = ArrayDeque<String>(MAX_LOG_ENTRIES)
    private val bufferLock = Any()

    private val uiActionHistory = ArrayDeque<UiActionRecord>(20)
    private val lifecycleEvents = ArrayDeque<String>(20)
    private val activeJobs = ConcurrentHashMap<Long, ExtractionJobRecord>()
    private val jobHistory = ArrayDeque<ExtractionJobRecord>(10)

    @Volatile
    private var cacheLogFile: File? = null

    @Volatile
    private var lastScanFile: File? = null

    @Volatile
    private var lastCrashFile: File? = null

    @Volatile
    var currentScan: ScanDiagnostics? = null
        private set

    @Volatile
    var currentScreen: String = "AppStart"
        private set

    @Volatile
    var isAppForeground: Boolean = true
        private set

    @Volatile
    var currentOperation: String? = null
        private set

    // Watchdog state
    @Volatile
    var lastSuccessfulHeartbeat: Instant? = null
        private set

    @Volatile
    var heartbeatCount: Long = 0L
        private set

    @Volatile
    var isMainThreadUnresponsive: Boolean = false
        private set

    @Volatile
    var watchdogIncidents: Int = 0
        private set

    @Volatile
    var lastUnresponsiveDurationMs: Long = 0L
        private set

    @Volatile
    var latestFreezeEvidence: WatchdogFreezeEvidence? = null
        private set

    @Volatile
    private var watchdogThread: Thread? = null
    private val isWatchdogRunning = AtomicBoolean(false)

    private var previousExceptionHandler: Thread.UncaughtExceptionHandler? = null

    @Volatile
    private var isTimberPlanted: Boolean = false

    /**
     * Test hook to override [BuildConfig.MEMORA_DIAGNOSTICS] behavior in unit tests.
     */
    @Volatile
    var diagnosticsEnabledOverride: Boolean? = null

    fun isDiagnosticsEnabled(): Boolean {
        return diagnosticsEnabledOverride ?: runCatching { BuildConfig.MEMORA_DIAGNOSTICS }.getOrDefault(false)
    }

    fun nextEventId(): Long = globalEventId.incrementAndGet()

    fun currentThreadName(): String = Thread.currentThread().name

    fun isMainThread(): Boolean = runCatching {
        Looper.myLooper() != null && Looper.myLooper() == Looper.getMainLooper()
    }.getOrDefault(false)

    /**
     * Initializes diagnostic logging, restores file state, and sets up files.
     */
    fun init(context: Context) {
        runCatching {
            val logDir = File(context.cacheDir, "diagnostics")
            if (!logDir.exists()) logDir.mkdirs()
            cacheLogFile = File(logDir, "memora_diagnostics.log")
            lastScanFile = File(logDir, "last_scan.json")
            lastCrashFile = File(logDir, "last_crash.json")

            // Restore persistent entries from previous session if file exists
            val file = cacheLogFile
            if (file != null && file.exists() && file.length() > 0) {
                synchronized(bufferLock) {
                    val existingLines = runCatching { file.readLines().takeLast(MAX_LOG_ENTRIES) }.getOrDefault(emptyList())
                    existingLines.forEach { line ->
                        if (logBuffer.size < MAX_LOG_ENTRIES) {
                            logBuffer.addLast(line)
                        }
                    }
                }
            }

            // Restore last scan diagnostics if present
            val scanFile = lastScanFile
            if (scanFile != null && scanFile.exists()) {
                deserializeScanDiagnostics(scanFile)
            }

            installCrashHandler()

            // Plant Timber tree for structured logcat output.
            // Timber is an optional sink — if it fails, AppLog continues normally.
            if (!isTimberPlanted) {
                runCatching {
                    Timber.plant(Timber.DebugTree())
                    isTimberPlanted = true
                }
            }

            if (isDiagnosticsEnabled()) {
                startWatchdog()
            }

            i("INIT", "Diagnostic logger initialized. Diagnostics enabled: ${isDiagnosticsEnabled()}")
        }
    }

    /**
     * Installs fail-safe uncaught exception handler that records crash metadata without swallowing.
     */
    fun installCrashHandler() {
        if (previousExceptionHandler != null) return
        previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                if (isDiagnosticsEnabled()) {
                    recordCrash(thread, throwable)
                }
            } catch (t: Throwable) {
                // Fail-safe: NEVER crash or swallow inside crash handler
            } finally {
                previousExceptionHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Records crash metadata and flushes it to disk.
     */
    fun recordCrash(thread: Thread, throwable: Throwable) {
        runCatching {
            val eventId = nextEventId()
            val sw = java.io.StringWriter()
            throwable.printStackTrace(java.io.PrintWriter(sw))
            val stackTraceLines = sw.toString().lines().take(30).map { sanitize(it) }
            val lastAction = getLastUserAction()?.action
            val currentScanId = currentScan?.scanId
            val screen = currentScreen

            e("CRASH", "FATAL CRASH DETECTED on thread '${thread.name}': ${throwable.javaClass.simpleName} - ${throwable.message}\n${stackTraceLines.joinToString("\n")}")

            runCatching {
                val json = JSONObject().apply {
                    put("timestamp", Instant.now().toString())
                    put("eventId", eventId)
                    put("threadName", thread.name)
                    put("exceptionClass", throwable.javaClass.name)
                    put("sanitizedMessage", sanitize(throwable.message ?: "No message"))
                    put("stackTrace", JSONArray(stackTraceLines))
                    put("scanId", currentScanId)
                    put("lastUserAction", lastAction)
                    put("currentScreen", screen)
                }
                lastCrashFile?.writeText(json.toString())
            }
        }
    }

    /**
     * Starts the lightweight main-thread watchdog daemon thread.
     */
    fun startWatchdog() {
        if (isWatchdogRunning.getAndSet(true)) return
        lastSuccessfulHeartbeat = Instant.now()

        val mainHandler = runCatching { Handler(Looper.getMainLooper()) }.getOrNull()
        if (mainHandler == null) return // Testing environment without main looper

        watchdogThread = Thread({
            while (isWatchdogRunning.get()) {
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    break
                }

                if (!isDiagnosticsEnabled() || !isAppForeground) {
                    // Update heartbeat while backgrounded to prevent false positives on resume
                    lastSuccessfulHeartbeat = Instant.now()
                    continue
                }

                // Post a lightweight heartbeat tick to the main thread
                mainHandler.post {
                    lastSuccessfulHeartbeat = Instant.now()
                    heartbeatCount++
                }

                val lastHeartbeat = lastSuccessfulHeartbeat ?: Instant.now()
                val ageMs = Duration.between(lastHeartbeat, Instant.now()).toMillis()

                if (ageMs > WATCHDOG_THRESHOLD_MS && !isMainThreadUnresponsive) {
                    // Freeze detected!
                    handleFreezeDetected(ageMs)
                } else if (ageMs <= WATCHDOG_THRESHOLD_MS && isMainThreadUnresponsive) {
                    // Main thread recovered and resumed!
                    handleFreezeRecovered(ageMs)
                }
            }
        }, "MemoraWatchdog").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Stops the watchdog thread.
     */
    fun stopWatchdog() {
        isWatchdogRunning.set(false)
        watchdogThread?.interrupt()
        watchdogThread = null
    }

    /**
     * Internal handler invoked when the main thread stops responding for > threshold.
     */
    fun handleFreezeDetected(ageMs: Long) {
        runCatching {
            isMainThreadUnresponsive = true
            watchdogIncidents++
            lastUnresponsiveDurationMs = ageMs

            val eventId = nextEventId()
            val dump = captureThreadDump()
            val evidence = WatchdogFreezeEvidence(
                timestamp = Instant.now(),
                eventId = eventId,
                scanId = currentScan?.scanId,
                currentScreen = currentScreen,
                lastUserAction = getLastUserAction()?.action,
                currentOperation = currentOperation,
                unresponsiveDurationMs = ageMs,
                threadDump = dump,
            )
            latestFreezeEvidence = evidence

            val mainStackSummary = dump.mainThreadStack.take(5).joinToString("\n    ")
            val otherThreadsSummary = dump.otherThreads.entries.take(4).joinToString("\n  ") { (t, s) ->
                "$t:\n    ${s.take(2).joinToString("\n    ")}"
            }

            w("WATCHDOG", "ANR WATCHDOG: Main thread unresponsive for ${ageMs}ms | screen=$currentScreen | lastAction=${evidence.lastUserAction} | op=$currentOperation\nMAIN THREAD STACK:\n    $mainStackSummary\nOTHER THREADS:\n  $otherThreadsSummary")
        }
    }

    /**
     * Internal handler invoked when responsiveness resumes after a freeze.
     */
    fun handleFreezeRecovered(ageMs: Long) {
        runCatching {
            isMainThreadUnresponsive = false
            i("WATCHDOG", "MAIN_THREAD_RESPONSIVE: Main thread resumed after ${lastUnresponsiveDurationMs}ms (current age: ${ageMs}ms)")
        }
    }

    /**
     * Test hook to simulate a watchdog check without wall-clock sleep.
     */
    fun checkWatchdogTick(simulatedAgeMs: Long) {
        if (simulatedAgeMs > WATCHDOG_THRESHOLD_MS && !isMainThreadUnresponsive) {
            handleFreezeDetected(simulatedAgeMs)
        } else if (simulatedAgeMs <= WATCHDOG_THRESHOLD_MS && isMainThreadUnresponsive) {
            handleFreezeRecovered(simulatedAgeMs)
        }
    }

    /**
     * Test hook to record a heartbeat tick directly.
     */
    fun recordHeartbeatTick() {
        lastSuccessfulHeartbeat = Instant.now()
        heartbeatCount++
    }

    /**
     * Safely captures all thread stacks, capped to avoid memory/log explosion.
     * Sanitizes all output with regex redaction. Never blocks the main thread.
     */
    fun captureThreadDump(): ThreadDump {
        return try {
            val allStacks = Thread.getAllStackTraces()
            val mainThread = allStacks.keys.firstOrNull {
                it.name == "main" || runCatching { it.id == Looper.getMainLooper().thread.id }.getOrDefault(false)
            }
            val mainStack = mainThread?.let { allStacks[it] }?.take(MAX_STACK_FRAMES_MAIN)?.map { ste ->
                sanitize("${ste.className}.${ste.methodName}(${ste.fileName}:${ste.lineNumber})")
            } ?: listOf("Main thread not found in stack traces")

            val otherThreads = allStacks.filter { (t, _) ->
                t != mainThread && t.name != "MemoraWatchdog" && t.state != Thread.State.NEW && t.state != Thread.State.TERMINATED
            }.entries.take(MAX_OTHER_THREADS).associate { (t, stack) ->
                "${t.name} [${t.state}]" to stack.take(MAX_STACK_FRAMES_OTHER).map { ste ->
                    sanitize("${ste.className}.${ste.methodName}(${ste.fileName}:${ste.lineNumber})")
                }
            }

            ThreadDump(mainStack, otherThreads)
        } catch (t: Throwable) {
            ThreadDump(listOf("Failed to capture thread dump: ${t.javaClass.simpleName}"), emptyMap())
        }
    }

    /**
     * Warns if an expensive operation executes on the main thread.
     */
    fun checkMainThreadViolation(operation: String) {
        if (isMainThread()) {
            w("THREAD", "[WARN] Expensive operation '$operation' executed on MAIN thread!")
        }
    }

    /**
     * Generates a 6-character alphanumeric scan correlation ID.
     */
    fun newScanId(): String {
        return UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
    }

    /**
     * Initiates diagnostic tracking for a capture/scan lifecycle.
     */
    fun startScan(scanId: String, imagePresent: Boolean = true) {
        runCatching {
            val scan = ScanDiagnostics(
                scanId = scanId,
                timestamp = Instant.now(),
                imagePresent = imagePresent,
                threadName = currentThreadName(),
                isMainThread = isMainThread(),
            )
            synchronized(bufferLock) {
                currentScan = scan
            }
            persistCurrentScan(scan)
            i("SCAN", "[#$scanId] Scan started | imagePresent=$imagePresent | thread=${scan.threadName}")
            recordScanLifecycleEvent(scanId, "SCAN_CREATED")
        }
    }

    // Active extraction job tracking and concurrency detection
    fun startExtractionJob(scanId: String, operation: String, dispatcherLabel: String = "UNKNOWN"): Long {
        val jobId = nextExtractionJobId.incrementAndGet()
        val record = ExtractionJobRecord(
            extractionJobId = jobId,
            scanId = scanId,
            currentOperation = operation,
            threadName = currentThreadName(),
            dispatcherLabel = dispatcherLabel,
        )
        activeJobs[jobId] = record
        val activeCount = activeJobs.size
        if (activeCount > 1) {
            w("CONCURRENCY", "[#$scanId] CONCURRENT_EXTRACTION_DETECTED | Active jobs: $activeCount | Job IDs: ${activeJobs.keys}")
        } else {
            i("JOB", "[#$scanId] ExtractionJob #$jobId STARTED: $operation | dispatcher=$dispatcherLabel | thread=${record.threadName}")
        }
        return jobId
    }

    fun updateExtractionJob(jobId: Long, operation: String) {
        activeJobs[jobId]?.let {
            it.currentOperation = operation
            it.threadName = currentThreadName()
        }
        currentOperation = operation
    }

    fun endExtractionJob(
        jobId: Long,
        finalState: String = "COMPLETED",
        durationMs: Long = 0L,
        outcome: String = finalState,
        error: String? = null,
    ) {
        val stateToRecord = if (error != null) "$outcome ($error)" else outcome
        val record = activeJobs.remove(jobId) ?: return
        record.state = stateToRecord
        val actualDuration = if (durationMs > 0) durationMs else Duration.between(record.startTimestamp, Instant.now()).toMillis()
        record.durationMs = actualDuration
        synchronized(bufferLock) {
            if (jobHistory.size >= 10) jobHistory.removeFirst()
            jobHistory.addLast(record)
        }
        if (activeJobs.isEmpty()) {
            currentOperation = null
        }
        i("JOB", "[#${record.scanId}] ExtractionJob #$jobId $stateToRecord in ${actualDuration}ms | op=${record.currentOperation}")
    }

    fun getActiveExtractionJobsCount(): Int = activeJobs.size

    fun getActiveExtractionJobs(): Map<Long, ExtractionJobRecord> = activeJobs.toMap()

    // User action and state transition tracking
    fun recordUserAction(action: String): UiActionRecord {
        val eventId = nextEventId()
        val cleanAction = sanitize(action)
        val record = UiActionRecord(eventId = eventId, action = cleanAction)
        synchronized(bufferLock) {
            if (uiActionHistory.size >= 20) uiActionHistory.removeFirst()
            uiActionHistory.addLast(record)
        }
        i("USER_ACTION", "[UI ACTION] $cleanAction")
        return record
    }

    fun recordUserAction(screen: String, action: String, detail: String? = null): UiActionRecord {
        val fullAction = if (detail != null) "[$screen] $action ($detail)" else "[$screen] $action"
        return recordUserAction(fullAction)
    }

    fun recordStateTransition(actionRecord: UiActionRecord?, transition: String, result: String = "SUCCESS") {
        actionRecord?.let {
            it.stateTransition = transition
            it.result = result
        }
        i("STATE", "[STATE] $transition | result=$result")
    }

    fun getLastUserAction(): UiActionRecord? = synchronized(bufferLock) { uiActionHistory.lastOrNull() }

    fun getWatchdogStatus(): WatchdogStatus {
        val lastHb = lastSuccessfulHeartbeat
        val age = if (lastHb != null) Duration.between(lastHb, Instant.now()).toMillis() else -1L
        val hbEpoch = lastHb?.toEpochMilli() ?: -1L
        return WatchdogStatus(
            running = isWatchdogRunning.get(),
            lastHeartbeatMs = hbEpoch,
            heartbeatAgeMs = age,
            heartbeatCount = heartbeatCount,
            lastUnresponsiveDurationMs = lastUnresponsiveDurationMs,
        )
    }

    fun getLastFreezeSnapshot(): WatchdogFreezeEvidence? = latestFreezeEvidence

    fun recordCrash(throwable: Throwable) {
        recordCrash(Thread.currentThread(), throwable)
    }

    fun getLastCrash(): JSONObject? {
        val file = lastCrashFile ?: return null
        return if (file.exists() && file.length() > 0) {
            runCatching { JSONObject(file.readText()) }.getOrNull()
        } else null
    }

    val entryCount: Int
        get() = synchronized(bufferLock) { logBuffer.size }

    fun getFormattedDump(context: Context? = null): String = buildFormattedDiagnosticsDump(context)

    // Screen and Lifecycle tracking
    fun recordScreen(screenName: String) {
        currentScreen = sanitize(screenName)
        i("LIFECYCLE", "Screen navigated: $currentScreen")
    }

    fun recordActivityLifecycle(activityName: String, stage: String) {
        val entry = "$activityName: $stage"
        synchronized(bufferLock) {
            if (lifecycleEvents.size >= 20) lifecycleEvents.removeFirst()
            lifecycleEvents.addLast(entry)
        }
        if (stage == "RESUMED") isAppForeground = true
        if (stage == "STOPPED") isAppForeground = false
        i("LIFECYCLE", entry)
    }

    // Memory diagnostics
    fun getMemorySnapshot(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val total = runtime.totalMemory()
        val free = runtime.freeMemory()
        val max = runtime.maxMemory()
        val used = total - free
        val isPressure = if (max > 0) (used.toDouble() / max.toDouble()) > 0.85 else false
        return MemorySnapshot(used, max, free, isPressure)
    }

    fun recordImageMemory(width: Int, height: Int, config: String?, scanId: String? = null) {
        val bytesPerPixel = if (config == "RGB_565") 2 else 4
        val estimatedBytes = width.toLong() * height.toLong() * bytesPerPixel
        val heap = getMemorySnapshot()
        val idStr = if (scanId != null) "[#$scanId] " else ""
        i("MEMORY", "${idStr}Image Decoded: ${width}x${height} ($config) ~${estimatedBytes / 1024}KB | Heap Used: ${heap.usedHeapBytes / 1024 / 1024}MB / ${heap.maxHeapBytes / 1024 / 1024}MB${if (heap.isHighPressure) " [HIGH MEMORY PRESSURE!]" else ""}")
        if (heap.isHighPressure) {
            w("MEMORY", "${idStr}[WARN] High memory pressure detected (>85% heap used)!")
        }
    }

    // Network diagnostics
    fun getNetworkSnapshot(context: Context?): NetworkSnapshot {
        if (context == null) return NetworkSnapshot(isConnected = true, transportType = "UNKNOWN", isMetered = false)
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(activeNetwork)
            val isConnected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val transport = when {
                caps == null -> "NONE"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "OTHER"
            }
            val isMetered = cm?.isActiveNetworkMetered ?: false
            NetworkSnapshot(isConnected, transport, isMetered)
        } catch (t: Throwable) {
            NetworkSnapshot(isConnected = true, transportType = "UNKNOWN", isMetered = false)
        }
    }

    fun recordScanLifecycleEvent(scanId: String, eventReason: String) {
        runCatching {
            synchronized(bufferLock) {
                currentScan?.takeIf { it.scanId == scanId }?.let { scan ->
                    val previousState = scan.lifecycleEvents.lastOrNull()?.newState ?: "CREATED"
                    scan.lifecycleEvents.add(LifecycleEventRecord(
                        scanId = scanId,
                        eventReason = eventReason,
                        previousState = previousState,
                        newState = eventReason,
                        threadName = currentThreadName()
                    ))
                }
            }
        }
    }

    fun recordOcrStart(scanId: String, triggerReason: String = "UNKNOWN", dispatcherLabel: String = "UNKNOWN") {
        recordScanLifecycleEvent(scanId, "OCR_STARTED ($triggerReason)")
        runCatching {
            synchronized(bufferLock) {
                currentScan?.takeIf { it.scanId == scanId }?.let { scan ->
                    val attemptNumber = scan.ocrAttempts.size + 1
                    val isConcurrent = scan.ocrAttempts.any { it.status == "RUNNING" }
                    val isDuplicateResult = scan.ocrStatus == "SUCCESS"
                    val previousState = scan.ocrStatus
                    
                    val record = OcrAttemptRecord(
                        scanId = scanId,
                        attemptNumber = attemptNumber,
                        startTimestamp = Instant.now(),
                        triggerReason = triggerReason,
                        previousState = previousState,
                        threadName = currentThreadName(),
                        isConcurrent = isConcurrent,
                        isDuplicateResult = isDuplicateResult
                    )
                    scan.ocrAttempts.add(record)
                    scan.ocrStatus = "RUNNING"
                }
            }
            i("OCR", "[#$scanId] OCR START | Attempt: ${currentScan?.ocrAttempts?.size} | Trigger: $triggerReason | Concurrent: ${currentScan?.ocrAttempts?.lastOrNull()?.isConcurrent}")
        }
    }

    fun recordOcr(
        scanId: String,
        durationMs: Long,
        charCount: Int,
        wordCount: Int,
        rawText: String?,
        error: String? = null,
        dispatcherLabel: String = "UNKNOWN",
        triggerReason: String = "UNKNOWN"
    ) {
        if (currentScan?.ocrAttempts?.any { it.status == "RUNNING" } != true) {
            recordOcrStart(scanId, triggerReason, dispatcherLabel)
        }
        if (error == null && rawText != null) {
            recordOcrSuccess(scanId, durationMs, rawText, dispatcherLabel, explicitWordCount = wordCount)
        } else {
            recordOcrFailure(scanId, durationMs, error ?: "OCR error", dispatcherLabel)
        }
    }

    fun recordOcrSuccess(
        scanId: String,
        durationMs: Long,
        rawText: String,
        dispatcherLabel: String = "UNKNOWN",
        explicitWordCount: Int? = null,
    ) {
        recordScanLifecycleEvent(scanId, "OCR_SUCCESS")
        runCatching {
            val charCount = rawText.length
            val wordCount = explicitWordCount ?: if (rawText.isBlank()) 0 else rawText.trim().split(Regex("\\s+")).size
            synchronized(bufferLock) {
                currentScan?.takeIf { it.scanId == scanId }?.let { scan ->
                    scan.ocrStatus = "SUCCESS"
                    scan.ocrDurationMs = durationMs
                    scan.ocrCharCount = charCount
                    scan.ocrWordCount = wordCount
                    scan.rawOcrText = if (isDiagnosticsEnabled()) rawText else "[REDACTED_IN_NON_DIAGNOSTIC_BUILD]"
                    scan.threadName = currentThreadName()
                    scan.dispatcherLabel = dispatcherLabel
                    scan.isMainThread = isMainThread()
                    
                    val attempt = scan.ocrAttempts.lastOrNull { it.status == "RUNNING" }
                    attempt?.let {
                        it.endTimestamp = Instant.now()
                        it.durationMs = durationMs
                        it.status = "SUCCESS"
                        it.endState = scan.ocrStatus
                    }
                    persistCurrentScan(scan)
                }
            }
            i("OCR", "[#$scanId] OCR completed in ${durationMs}ms | Chars: $charCount | Words: $wordCount | dispatcher=$dispatcherLabel | thread=${currentThreadName()}")
            if (isDiagnosticsEnabled() && rawText.isNotBlank()) {
                val preview = rawText.lines().take(5).joinToString("\n")
                d("OCR", "[#$scanId] Raw OCR Preview (first 5 lines):\n$preview")
            }
        }
    }

    fun recordOcrFailure(scanId: String, durationMs: Long, error: String?, dispatcherLabel: String = "UNKNOWN") {
        recordScanLifecycleEvent(scanId, "OCR_FAILURE")
        runCatching {
            synchronized(bufferLock) {
                currentScan?.takeIf { it.scanId == scanId }?.let { scan ->
                    scan.ocrStatus = "FAILURE"
                    scan.ocrDurationMs = durationMs
                    scan.ocrError = error
                    scan.threadName = currentThreadName()
                    scan.dispatcherLabel = dispatcherLabel
                    scan.isMainThread = isMainThread()
                    
                    val attempt = scan.ocrAttempts.lastOrNull { it.status == "RUNNING" }
                    attempt?.let {
                        it.endTimestamp = Instant.now()
                        it.durationMs = durationMs
                        it.status = "FAILURE"
                        it.endState = scan.ocrStatus
                    }
                    persistCurrentScan(scan)
                }
            }
            e("OCR", "[#$scanId] OCR failed after ${durationMs}ms: $error | dispatcher=$dispatcherLabel | thread=${currentThreadName()}")
        }
    }

    fun recordGeminiRequest(scanId: String, model: String = "gemini-3.5-flash", dispatcherLabel: String = "UNKNOWN") {
        recordScanLifecycleEvent(scanId, "GEMINI_REQUESTED")
        runCatching {
            currentOperation = "Gemini extraction"
            synchronized(bufferLock) {
                currentScan?.takeIf { it.scanId == scanId }?.let { scan ->
                    val attemptNumber = scan.geminiAttempts.size + 1
                    scan.geminiAttempts.add(GeminiAttemptRecord(
                        scanId = scanId,
                        attemptNumber = attemptNumber,
                        startTimestamp = Instant.now(),
                        threadName = currentThreadName(),
                        dispatcherLabel = dispatcherLabel,
                        model = model
                    ))
                    scan.geminiModel = model
                    scan.geminiStatus = "RUNNING"
                    scan.threadName = currentThreadName()
                    scan.dispatcherLabel = dispatcherLabel
                    scan.isMainThread = isMainThread()
                    persistCurrentScan(scan)
                }
            }
            i("GEMINI", "[#$scanId] Gemini request started | attempt=${currentScan?.geminiAttempts?.size} | model=$model | dispatcher=$dispatcherLabel | thread=${currentThreadName()}")
        }
    }

    fun recordGeminiSuccess(scanId: String, durationMs: Long, rawJson: String, dispatcherLabel: String = "UNKNOWN") {
        recordScanLifecycleEvent(scanId, "GEMINI_SUCCESS")
        runCatching {
            currentOperation = null
            synchronized(bufferLock) {
                currentScan?.takeIf { it.scanId == scanId }?.let { scan ->
                    scan.geminiStatus = "SUCCESS"
                    scan.geminiDurationMs = durationMs
                    scan.rawGeminiJson = if (isDiagnosticsEnabled()) rawJson else "[REDACTED_IN_NON_DIAGNOSTIC_BUILD]"
                    scan.threadName = currentThreadName()
                    scan.dispatcherLabel = dispatcherLabel
                    scan.isMainThread = isMainThread()
                    
                    val attempt = scan.geminiAttempts.lastOrNull { it.status == "RUNNING" }
                    attempt?.let {
                        it.endTimestamp = Instant.now()
                        it.durationMs = durationMs
                        it.status = "SUCCESS"
                    }
                    persistCurrentScan(scan)
                }
            }
            i("GEMINI", "[#$scanId] Gemini response received in ${durationMs}ms | dispatcher=$dispatcherLabel | thread=${currentThreadName()}")
            if (isDiagnosticsEnabled() && rawJson.isNotBlank()) {
                d("GEMINI", "[#$scanId] Raw Gemini JSON Response:\n--- START GEMINI JSON ---\n$rawJson\n--- END GEMINI JSON ---")
            }
        }
    }

    fun recordGeminiFailure(
        scanId: String,
        durationMs: Long,
        reason: GeminiFailureReason,
        errorDetail: String? = null,
        dispatcherLabel: String = "UNKNOWN",
        throwable: Throwable? = null
    ) {
        recordScanLifecycleEvent(scanId, "GEMINI_FAILURE")
        runCatching {
            currentOperation = null
            val cleanDetail = errorDetail?.let { sanitize(it) }
            val exceptionClass = throwable?.javaClass?.simpleName
            
            // Extract HTTP status if present in the message (e.g. 429, 500)
            val httpStatus = cleanDetail?.let { msg ->
                val regex = Regex("\\b(4[0-9]{2}|5[0-9]{2})\\b")
                regex.find(msg)?.value?.toIntOrNull()
            }

            synchronized(bufferLock) {
                currentScan?.takeIf { it.scanId == scanId }?.let { scan ->
                    scan.geminiStatus = "FAILURE"
                    scan.geminiDurationMs = durationMs
                    scan.geminiFailureReason = reason
                    scan.geminiErrorDetail = cleanDetail
                    scan.threadName = currentThreadName()
                    scan.dispatcherLabel = dispatcherLabel
                    scan.isMainThread = isMainThread()
                    
                    val attempt = scan.geminiAttempts.lastOrNull { it.status == "RUNNING" }
                    attempt?.let {
                        it.endTimestamp = Instant.now()
                        it.durationMs = durationMs
                        it.status = "FAILURE"
                        it.failureCategory = reason
                        it.sanitizedMessage = cleanDetail
                        it.exceptionType = exceptionClass
                        it.httpStatus = httpStatus
                        it.retryable = reason == GeminiFailureReason.RATE_LIMIT || reason == GeminiFailureReason.TIMEOUT || reason == GeminiFailureReason.SERVER_ERROR || reason == GeminiFailureReason.NETWORK_ERROR
                    }
                    persistCurrentScan(scan)
                }
            }
            val detailMsg = if (!cleanDetail.isNullOrBlank()) " | detail=$cleanDetail" else ""
            w("GEMINI", "[#$scanId] Gemini failed after ${durationMs}ms | reason=$reason$detailMsg | dispatcher=$dispatcherLabel | thread=${currentThreadName()}")
        }
    }

    fun recordCandidateParsed(
        scanId: String,
        title: String?,
        date: String?,
        eventType: String?,
        confidence: Float,
        alternatives: List<String> = emptyList(),
    ) {
        runCatching {
            synchronized(bufferLock) {
                currentScan?.takeIf { it.scanId == scanId }?.let { scan ->
                    scan.parsedCandidateTitle = title
                    scan.parsedCandidateDate = date
                    scan.parsedCandidateEventType = eventType
                    scan.parsedCandidateConfidence = confidence
                    scan.parsedAlternatives = alternatives
                    persistCurrentScan(scan)
                }
            }
            i("PARSE", "[#$scanId] Candidate parsed: Title='$title' | Date='$date' | Type='$eventType' | Confidence=$confidence | Alternatives=${alternatives.size}")
        }
    }

    fun recordDateCandidate(
        scanId: String,
        rawCandidate: String,
        normalizedDate: String?,
        contextLabel: String?,
        classification: String,
        actionable: Boolean
    ) {
        runCatching {
            synchronized(bufferLock) {
                currentScan?.takeIf { it.scanId == scanId }?.let { scan ->
                    scan.dateCandidateDetails.add(DateCandidateDiagnostics(
                        rawCandidate = rawCandidate,
                        normalizedDate = normalizedDate,
                        contextLabel = contextLabel,
                        classification = classification,
                        actionable = actionable
                    ))
                    persistCurrentScan(scan)
                }
            }
        }
    }

    fun recordDateSelection(
        scanId: String,
        candidates: List<String>,
        selectedDate: String?,
        reason: String,
    ) {
        runCatching {
            synchronized(bufferLock) {
                currentScan?.takeIf { it.scanId == scanId }?.let { scan ->
                    scan.dateCandidates = candidates
                    scan.selectedDate = selectedDate
                    scan.dateSelectionReason = reason
                    
                    // Update the detailed models if they match the selected string
                    scan.dateCandidateDetails.forEach { cand ->
                        if (selectedDate != null && cand.normalizedDate == selectedDate) {
                            cand.selected = true
                        } else {
                            cand.selected = false
                            cand.rejectionReason = reason
                        }
                    }
                    persistCurrentScan(scan)
                }
            }
            i("SELECTION", "[#$scanId] Date Selected: '$selectedDate' | Candidates: $candidates | Reason: $reason")
        }
    }

    fun recordFallback(scanId: String, fallbackUsed: Boolean, reason: String? = null) {
        runCatching {
            synchronized(bufferLock) {
                currentScan?.takeIf { it.scanId == scanId }?.let { scan ->
                    scan.fallbackUsed = fallbackUsed
                    scan.fallbackReason = reason
                    scan.extractorPath = if (fallbackUsed) {
                        "OCR -> Gemini FAILED ($reason) -> Deterministic Fallback USED"
                    } else {
                        "OCR -> Gemini -> Gemini SUCCESS -> Deterministic Fallback NOT USED"
                    }
                    persistCurrentScan(scan)
                }
            }
            if (fallbackUsed) {
                w("FALLBACK", "[#$scanId] Fallback triggered: Deterministic fallback USED | reason=$reason")
            } else {
                i("FALLBACK", "[#$scanId] Extractor path: Gemini SUCCESS | Deterministic fallback NOT USED")
            }
        }
    }

    fun recordFinalResult(
        scanId: String,
        disposition: String,
        extractorUsed: String,
        confidence: Float,
        parsingDurationMs: Long,
        totalDurationMs: Long, // Still accepted for compatibility, but overridden safely
    ) {
        recordScanLifecycleEvent(scanId, "FINAL_DISPOSITION_REACHED ($disposition)")
        runCatching {
            synchronized(bufferLock) {
                currentScan?.takeIf { it.scanId == scanId }?.let { scan ->
                    scan.finalDisposition = disposition
                    scan.extractorUsed = extractorUsed
                    scan.confidence = confidence
                    scan.parsingDurationMs = parsingDurationMs
                    val trueTotalDurationMs = if (totalDurationMs > 0) totalDurationMs else Duration.between(scan.timestamp, Instant.now()).toMillis()
                    scan.totalDurationMs = trueTotalDurationMs
                    persistCurrentScan(scan)
                    i("RESULT", "[#$scanId] Final disposition: $disposition | Extractor: $extractorUsed | Confidence: $confidence | Total: ${trueTotalDurationMs}ms (Parsing: ${parsingDurationMs}ms)")
                }
            }
        }
    }

    fun recordPersistence(
        scanId: String,
        saveStatus: String,
        reminderStatus: String,
        dbDurationMs: Long,
    ) {
        runCatching {
            synchronized(bufferLock) {
                currentScan?.takeIf { it.scanId == scanId }?.let { scan ->
                    scan.saveStatus = saveStatus
                    scan.reminderStatus = reminderStatus
                    scan.dbDurationMs = dbDurationMs
                    persistCurrentScan(scan)
                }
            }
            i("PERSIST", "[#$scanId] Persistence: Save=$saveStatus | Reminder=$reminderStatus | Duration=${dbDurationMs}ms")
        }
    }

    // Database & Notification Operation Diagnostics
    fun recordDatabaseOperation(operation: String, durationMs: Long, isSuccess: Boolean, errorCategory: DiagnosticErrorCategory? = null, scanId: String? = null) {
        val idStr = if (scanId != null) "[#$scanId] " else ""
        val status = if (isSuccess) "SUCCESS" else "FAILED ($errorCategory)"
        i("DATABASE", "${idStr}DB Op '$operation': $status in ${durationMs}ms | thread=${currentThreadName()}")
    }

    fun recordNotificationOperation(operation: String, durationMs: Long, isSuccess: Boolean, scheduler: String = "WorkManager", errorCategory: DiagnosticErrorCategory? = null, scanId: String? = null) {
        val idStr = if (scanId != null) "[#$scanId] " else ""
        val status = if (isSuccess) "SUCCESS" else "FAILED ($errorCategory)"
        i("NOTIFICATION", "${idStr}Notification Op '$operation' via $scheduler: $status in ${durationMs}ms | thread=${currentThreadName()}")
    }

    // Standard logging entry points
    fun d(tag: String, message: String) = log("DEBUG", tag, message)
    fun i(tag: String, message: String) = log("INFO", tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log("WARN", tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log("ERROR", tag, message, throwable)

    private fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        runCatching {
            val sanitizedMsg = sanitize(message)
            val stackTrace = throwable?.let {
                val sw = java.io.StringWriter()
                it.printStackTrace(java.io.PrintWriter(sw))
                "\n" + sanitize(sw.toString().lines().take(15).joinToString("\n"))
            }.orEmpty()
            val fullMsg = sanitizedMsg + stackTrace
            val eventId = nextEventId()
            val timestamp = timeFormatter.format(Instant.now())
            val entry = "[$timestamp] [#$eventId] [$level/$tag] $fullMsg"

            // Mirror to Logcat via Timber (optional sink — failure is safe)
            runCatching {
                when (level) {
                    "DEBUG" -> Timber.tag(tag).d(fullMsg)
                    "INFO" -> Timber.tag(tag).i(fullMsg)
                    "WARN" -> Timber.tag(tag).w(fullMsg)
                    "ERROR" -> Timber.tag(tag).e(fullMsg)
                }
            }

            synchronized(bufferLock) {
                if (logBuffer.size >= MAX_LOG_ENTRIES) {
                    logBuffer.removeFirst()
                }
                logBuffer.addLast(entry)
            }

            appendToFile(entry)
        }
    }

    fun sanitize(input: String): String {
        var s = if (input.length > 5000) {
            input.take(5000) + "… [TRUNCATED ${input.length - 5000} chars]"
        } else input
        s = s.replace(Regex("AIza[0-9A-Za-z\\-_]{30,}"), "[REDACTED_API_KEY]")
        s = s.replace(Regex("(?i)(key|apikey|api_key)=([^&\\s]+)"), "$1=[REDACTED_API_KEY]")
        s = s.replace(Regex("(?i)bearer\\s+([a-zA-Z0-9\\-_.]+)"), "Bearer [REDACTED_TOKEN]")
        s = s.replace(Regex("(?i)authorization[:=]\\s*(?!bearer|\\[redacted)[^\\s]+"), "Authorization: [REDACTED]")
        return s
    }

    private fun appendToFile(entry: String) {
        if (!isDiagnosticsEnabled()) return
        val file = cacheLogFile ?: return
        runCatching {
            if (file.exists() && file.length() > MAX_CACHE_FILE_BYTES) {
                file.delete()
            }
            file.appendText(entry + "\n")
        }
    }

    private fun persistCurrentScan(scan: ScanDiagnostics) {
        if (!isDiagnosticsEnabled()) return
        val file = lastScanFile ?: return
        runCatching {
            val json = JSONObject().apply {
                put("scanId", scan.scanId)
                put("timestamp", scan.timestamp.toString())
                put("imagePresent", scan.imagePresent)
                put("ocrStatus", scan.ocrStatus)
                put("ocrDurationMs", scan.ocrDurationMs)
                put("ocrCharCount", scan.ocrCharCount)
                put("ocrWordCount", scan.ocrWordCount)
                put("rawOcrText", scan.rawOcrText)
                put("ocrError", scan.ocrError)
                put("geminiStatus", scan.geminiStatus)
                put("geminiModel", scan.geminiModel)
                put("geminiDurationMs", scan.geminiDurationMs)
                put("geminiFailureReason", scan.geminiFailureReason?.name)
                put("geminiErrorDetail", scan.geminiErrorDetail)
                put("rawGeminiJson", scan.rawGeminiJson)
                put("parsedCandidateTitle", scan.parsedCandidateTitle)
                put("parsedCandidateDate", scan.parsedCandidateDate)
                put("parsedCandidateEventType", scan.parsedCandidateEventType)
                put("parsedCandidateConfidence", scan.parsedCandidateConfidence.toDouble())
                put("parsedAlternatives", JSONArray(scan.parsedAlternatives))
                put("dateCandidates", JSONArray(scan.dateCandidates))
                put("selectedDate", scan.selectedDate)
                put("dateSelectionReason", scan.dateSelectionReason)
                put("extractorPath", scan.extractorPath)
                put("fallbackUsed", scan.fallbackUsed)
                put("fallbackReason", scan.fallbackReason)
                put("finalDisposition", scan.finalDisposition)
                put("extractorUsed", scan.extractorUsed)
                put("confidence", scan.confidence.toDouble())
                put("parsingDurationMs", scan.parsingDurationMs)
                put("dbDurationMs", scan.dbDurationMs)
                put("totalDurationMs", scan.totalDurationMs)
                put("saveStatus", scan.saveStatus)
                put("reminderStatus", scan.reminderStatus)
                put("threadName", scan.threadName)
                put("dispatcherLabel", scan.dispatcherLabel)
                put("isMainThread", scan.isMainThread)
            }
            file.writeText(json.toString())
        }
    }

    private fun deserializeScanDiagnostics(file: File) {
        runCatching {
            val text = file.readText()
            if (text.isNotBlank()) {
                val json = JSONObject(text)
                val scan = ScanDiagnostics(
                    scanId = json.optString("scanId"),
                    timestamp = runCatching { Instant.parse(json.optString("timestamp")) }.getOrDefault(Instant.now()),
                    imagePresent = json.optBoolean("imagePresent", true),
                    ocrStatus = json.optString("ocrStatus", "PENDING"),
                    ocrDurationMs = json.optLong("ocrDurationMs", 0L),
                    ocrCharCount = json.optInt("ocrCharCount", 0),
                    ocrWordCount = json.optInt("ocrWordCount", 0),
                    rawOcrText = json.optString("rawOcrText").takeIf { it.isNotBlank() && it != "null" },
                    ocrError = json.optString("ocrError").takeIf { it.isNotBlank() && it != "null" },
                    geminiStatus = json.optString("geminiStatus", "PENDING"),
                    geminiModel = json.optString("geminiModel", "gemini-3.5-flash"),
                    geminiDurationMs = json.optLong("geminiDurationMs", 0L),
                    geminiFailureReason = runCatching { GeminiFailureReason.valueOf(json.optString("geminiFailureReason")) }.getOrNull(),
                    geminiErrorDetail = json.optString("geminiErrorDetail").takeIf { it.isNotBlank() && it != "null" },
                    rawGeminiJson = json.optString("rawGeminiJson").takeIf { it.isNotBlank() && it != "null" },
                    parsedCandidateTitle = json.optString("parsedCandidateTitle").takeIf { it.isNotBlank() && it != "null" },
                    parsedCandidateDate = json.optString("parsedCandidateDate").takeIf { it.isNotBlank() && it != "null" },
                    parsedCandidateEventType = json.optString("parsedCandidateEventType").takeIf { it.isNotBlank() && it != "null" },
                    parsedCandidateConfidence = json.optDouble("parsedCandidateConfidence", 0.0).toFloat(),
                    parsedAlternatives = json.optJSONArray("parsedAlternatives")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                    dateCandidates = json.optJSONArray("dateCandidates")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
                    selectedDate = json.optString("selectedDate").takeIf { it.isNotBlank() && it != "null" },
                    dateSelectionReason = json.optString("dateSelectionReason").takeIf { it.isNotBlank() && it != "null" },
                    extractorPath = json.optString("extractorPath", "OCR -> Gemini"),
                    fallbackUsed = json.optBoolean("fallbackUsed", false),
                    fallbackReason = json.optString("fallbackReason").takeIf { it.isNotBlank() && it != "null" },
                    finalDisposition = json.optString("finalDisposition").takeIf { it.isNotBlank() && it != "null" },
                    extractorUsed = json.optString("extractorUsed").takeIf { it.isNotBlank() && it != "null" },
                    confidence = json.optDouble("confidence", 0.0).toFloat(),
                    parsingDurationMs = json.optLong("parsingDurationMs", 0L),
                    dbDurationMs = json.optLong("dbDurationMs", 0L),
                    totalDurationMs = json.optLong("totalDurationMs", 0L),
                    saveStatus = json.optString("saveStatus").takeIf { it.isNotBlank() && it != "null" },
                    reminderStatus = json.optString("reminderStatus").takeIf { it.isNotBlank() && it != "null" },
                    threadName = json.optString("threadName").takeIf { it.isNotBlank() && it != "null" },
                    dispatcherLabel = json.optString("dispatcherLabel", "UNKNOWN"),
                    isMainThread = json.optBoolean("isMainThread", false),
                )
                synchronized(bufferLock) {
                    currentScan = scan
                }
            }
        }
    }

    /**
     * Clears DIAGNOSTIC DATA ONLY.
     * NEVER deletes Room entities, SQLite tables, preferences, or user memories.
     */
    fun clear() {
        runCatching {
            cacheLogFile?.delete()
            lastScanFile?.delete()
            lastCrashFile?.delete()
            synchronized(bufferLock) {
                logBuffer.clear()
                currentScan = null
                uiActionHistory.clear()
                lifecycleEvents.clear()
                activeJobs.clear()
                jobHistory.clear()
                latestFreezeEvidence = null
                isMainThreadUnresponsive = false
            }
        }
    }

    fun getLogEntries(): List<String> {
        return synchronized(bufferLock) {
            logBuffer.toList()
        }
    }

    /**
     * Builds the complete 9-section formatted diagnostics export dump.
     */
    fun buildFormattedDiagnosticsDump(context: Context? = null): String {
        return runCatching {
            val sb = StringBuilder()
            val now = Instant.now()
            val entryCount = synchronized(bufferLock) { logBuffer.size }

            sb.appendLine("==================================================")
            sb.appendLine("        MEMORA DIAGNOSTICS DUMP (TESTING MODE)     ")
            sb.appendLine("==================================================")
            sb.appendLine("Generated At: $now")
            sb.appendLine("Diagnostics Enabled: ${isDiagnosticsEnabled()}")
            sb.appendLine()
            sb.appendLine("[WARNING] These logs may contain text extracted from your captured documents.")
            sb.appendLine("Use only with test data and do not share logs containing personal information.")
            sb.appendLine("==================================================")
            sb.appendLine()

            // SECTION 1: APP / DEVICE / RUNTIME
            sb.appendLine("--- SECTION 1: APP / DEVICE / RUNTIME ---")
            sb.appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            sb.appendLine("Android SDK: ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})")
            sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            sb.appendLine("Current Screen: $currentScreen")
            sb.appendLine("Foreground: $isAppForeground")
            context?.let { ctx ->
                val freeSpaceMb = runCatching { ctx.filesDir.freeSpace / (1024 * 1024) }.getOrDefault(-1L)
                sb.appendLine("Available Storage: ${freeSpaceMb} MB")
            }
            sb.appendLine()

            // SECTION 2: SCAN LIFECYCLE
            sb.appendLine("--- SECTION 2: SCAN LIFECYCLE ---")
            val scan = synchronized(bufferLock) { currentScan }
            if (scan != null) {
                sb.appendLine("Scan ID: #${scan.scanId}")
                sb.appendLine("Created At: ${scan.timestamp}")
                sb.appendLine("Final Disposition: ${scan.finalDisposition}")
                sb.appendLine("Lifecycle Events:")
                if (scan.lifecycleEvents.isEmpty()) {
                    sb.appendLine("  No lifecycle events recorded.")
                } else {
                    scan.lifecycleEvents.forEach { ev ->
                        sb.appendLine("  [${ev.timestamp}] ${ev.eventReason} | State: ${ev.previousState} -> ${ev.newState}")
                    }
                }
            } else {
                sb.appendLine("No active scan.")
            }
            sb.appendLine()

            // SECTION 3: OCR DIAGNOSTICS
            sb.appendLine("--- SECTION 3: OCR DIAGNOSTICS ---")
            if (scan != null) {
                sb.appendLine("OCR: ${scan.ocrStatus}")
                sb.appendLine("OCR Status: ${scan.ocrStatus}")
                if (scan.rawOcrText != null) {
                    sb.appendLine("Raw OCR Text: ${scan.rawOcrText}")
                }
                sb.appendLine("Attempts: ${scan.ocrAttempts.size}")
                scan.ocrAttempts.forEach { att ->
                    sb.appendLine("  Attempt #${att.attemptNumber} | Trigger: ${att.triggerReason} | Status: ${att.status} | Duration: ${att.durationMs}ms | Concurrent: ${att.isConcurrent} | Duplicate: ${att.isDuplicateResult}")
                }
            } else {
                sb.appendLine("No OCR diagnostics recorded.")
            }
            sb.appendLine()

            // SECTION 4: LOCAL EXTRACTION DIAGNOSTICS
            sb.appendLine("--- SECTION 4: LOCAL EXTRACTION DIAGNOSTICS ---")
            if (scan != null) {
                sb.appendLine("Extractor Path: ${scan.extractorPath}")
                sb.appendLine("Candidates Found: ${scan.dateCandidateDetails.size}")
                scan.dateCandidateDetails.forEach { cand ->
                    sb.appendLine("  Candidate: ${cand.rawCandidate} | Normalized: ${cand.normalizedDate} | Actionable: ${cand.actionable} | Selected: ${cand.selected} | Reject Reason: ${cand.rejectionReason}")
                }
                sb.appendLine("Selected Date: ${scan.selectedDate}")
                sb.appendLine("Selection Reason: ${scan.dateSelectionReason}")
            } else {
                sb.appendLine("No extraction diagnostics recorded.")
            }
            sb.appendLine()

            // SECTION 5: GEMINI DIAGNOSTICS
            sb.appendLine("--- SECTION 5: GEMINI DIAGNOSTICS ---")
            if (scan != null) {
                sb.appendLine("Gemini: ${scan.geminiStatus}")
                sb.appendLine("Gemini Status: ${scan.geminiStatus}")
                sb.appendLine("Model: ${scan.geminiModel}")
                sb.appendLine("Attempts: ${scan.geminiAttempts.size}")
                scan.geminiAttempts.forEach { att ->
                    sb.appendLine("  Attempt #${att.attemptNumber} | Status: ${att.status} | Duration: ${att.durationMs}ms | Category: ${att.failureCategory} | Msg: ${att.sanitizedMessage}")
                }
            } else {
                sb.appendLine("No Gemini diagnostics recorded.")
            }
            sb.appendLine()

            // SECTION 6: TIMING
            sb.appendLine("--- SECTION 6: TIMING ---")
            if (scan != null) {
                sb.appendLine("OCR Phase: ${scan.ocrDurationMs}ms")
                sb.appendLine("Gemini Phase: ${scan.geminiDurationMs}ms")
                sb.appendLine("Parsing Phase: ${scan.parsingDurationMs}ms")
                sb.appendLine("DB Persistence: ${scan.dbDurationMs}ms")
                sb.appendLine("End-to-End Total: ${scan.totalDurationMs}ms (True Pipeline Duration)")
            } else {
                sb.appendLine("No timing diagnostics recorded.")
            }
            sb.appendLine()

            // SECTION 7: MEMORY / RESOURCE
            sb.appendLine("--- SECTION 7: MEMORY / RESOURCE ---")
            val heap = getMemorySnapshot()
            sb.appendLine("JVM Heap Used: ${heap.usedHeapBytes / 1024 / 1024} MB")
            sb.appendLine("JVM Heap Max:  ${heap.maxHeapBytes / 1024 / 1024} MB")
            sb.appendLine("JVM Heap Free: ${heap.freeHeapBytes / 1024 / 1024} MB")
            sb.appendLine("Memory Pressure High: ${heap.isHighPressure}")
            sb.appendLine()

            // SECTION 8: NETWORK
            sb.appendLine("--- SECTION 8: NETWORK ---")
            val net = getNetworkSnapshot(context)
            sb.appendLine("Connected: ${net.isConnected}")
            sb.appendLine("Transport: ${net.transportType}")
            sb.appendLine("Metered:   ${net.isMetered}")
            sb.appendLine()

            // SECTION 9: USER ACTIONS / STATE TRANSITIONS
            sb.appendLine("--- SECTION 9: USER ACTIONS / STATE TRANSITIONS ---")
            synchronized(bufferLock) {
                if (uiActionHistory.isEmpty()) {
                    sb.appendLine("No user actions recorded.")
                } else {
                    uiActionHistory.forEach { action ->
                        val trans = if (!action.stateTransition.isNullOrBlank()) " -> [State: ${action.stateTransition} (${action.result})]" else ""
                        sb.appendLine("  [#${action.eventId}] ${action.timestamp} | ${action.action}$trans")
                    }
                }
            }
            sb.appendLine()
            
            // Legacy Exception Catch-all (to retain watchdog visibility)
            val evidence = latestFreezeEvidence
            if (evidence != null) {
                sb.appendLine("WATCHDOG/CRASH ALERTS:")
                sb.appendLine("  Event ID: #${evidence.eventId} | Unresponsive: ${evidence.unresponsiveDurationMs}ms")
            }

            sb.appendLine("==================================================")
            sb.toString()
        }.getOrElse { "Failed to generate diagnostic dump: ${it.message}" }
    }
}
