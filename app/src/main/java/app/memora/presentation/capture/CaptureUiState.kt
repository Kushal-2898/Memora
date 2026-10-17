package app.memora.presentation.capture

import app.memora.domain.model.CapturedImage
import app.memora.domain.extraction.Stage3ExtractionResult

/**
 * Mutually exclusive UI states for the memory capture flow.
 *
 * Designed to prevent contradictory boolean state combinations (e.g. loading,
 * error, and preview cannot accidentally be true simultaneously).
 */
sealed interface CaptureUiState {

    /**
     * Initial resting state presenting the primary capture options:
     * 1. Take Photo
     * 2. Choose Image
     * 3. Import Screenshot
     */
    data object Idle : CaptureUiState

    /**
     * Active fullscreen CameraX viewfinder ready to capture a photo.
     */
    data object CameraActive : CaptureUiState

    /**
     * Camera permission is required before the camera viewfinder can be opened.
     *
     * @property permanentlyDenied If true, the system will not show the permission dialog
     * and the user should be directed to the application settings.
     */
    data class PermissionRequired(
        val permanentlyDenied: Boolean = false,
    ) : CaptureUiState

    /**
     * Transient processing state while the selected image or captured photo
     * is securely streamed into application-controlled temporary storage.
     */
    data class Processing(
        val message: String = "Processing image…",
    ) : CaptureUiState

    /**
     * Preview review step presenting the temporary captured image, source indicator,
     * and Retake/Replace and Continue/Confirm actions.
     */
    data class Preview(
        val capturedImage: CapturedImage,
    ) : CaptureUiState

    /**
     * Completed capture state where the [CapturedImage] reference is confirmed
     * and ready to be consumed by the future Stage 3 extraction pipeline.
     */
    data class Confirmed(
        val capturedImage: CapturedImage,
    ) : CaptureUiState

    data class Extracting(
        val capturedImage: CapturedImage,
    ) : CaptureUiState

    /**
     * Local on-device extraction is complete; displays "Here's what Memora found"
     * for user inspection before any Gemini API call is triggered.
     */
    data class LocalExtractionReady(
        val capturedImage: CapturedImage,
        val ocrResult: app.memora.domain.extraction.OcrResult,
        val localResult: app.memora.domain.extraction.LocalExtractionResult,
    ) : CaptureUiState

    /**
     * User has confirmed local review; calling Gemini for refinement.
     */
    data class GeminiProcessing(
        val capturedImage: CapturedImage,
        val message: String = "Analyzing with AI…",
    ) : CaptureUiState

    data class ExtractionReady(
        val capturedImage: CapturedImage,
        val result: Stage3ExtractionResult,
    ) : CaptureUiState

    /**
     * Clean, user-facing error state with contextual retry or dismiss actions.
     */
    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val returnState: CaptureUiState = Idle,
        val canEnterManually: Boolean = false,
        val capturedImage: CapturedImage? = null,
    ) : CaptureUiState
}
