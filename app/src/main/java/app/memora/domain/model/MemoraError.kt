package app.memora.domain.model

/**
 * Structured error hierarchy for domain, data, and platform layers.
 * Prevents exposing raw exceptions directly to presentation/UI code.
 */
sealed class MemoraError(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    data class NotFound(val id: String) :
        MemoraError("Reminder with ID '$id' was not found.")

    data class Validation(val reason: String) :
        MemoraError("Validation failed: $reason")

    data class Storage(
        override val message: String,
        override val cause: Throwable? = null
    ) : MemoraError(message, cause)

    data class Database(
        override val message: String,
        override val cause: Throwable? = null
    ) : MemoraError(message, cause)

    data class PlatformUnavailable(val featureName: String) :
        MemoraError("Platform feature '$featureName' is not available in this stage.")

    sealed class Capture(override val message: String, override val cause: Throwable? = null) :
        MemoraError(message, cause) {
        data class CameraUnavailable(val reason: String = "Camera hardware is not available on this device.") :
            Capture(reason)
        data class PermissionDenied(val permanently: Boolean = false) :
            Capture("Camera permission is required to capture photos.")
        data class StorageFailed(val detail: String, override val cause: Throwable? = null) :
            Capture("Failed to save captured image: $detail", cause)
        data class ImportFailed(val detail: String, override val cause: Throwable? = null) :
            Capture("Failed to import selected image: $detail", cause)
        data class Timeout(val detail: String = "No document found within the time limit.") : 
            Capture(detail)
        data object SelectionCancelled :
            Capture("Image selection was cancelled.")
    }
}
