package app.memora.platform.image

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Platform abstraction for user image selection (Android Photo Picker / Gallery / Screenshot).
 *
 * Provides a clean boundary decoupling platform activity contracts and launchers
 * from domain and view logic.
 */
interface ImagePicker {
    /**
     * Prompts the user to pick an image using modern Android Photo Picker
     * and returns the local content URI.
     */
    suspend fun pickImage(): Result<String?>

    /**
     * Indicates whether the Photo Picker is supported on this platform/API level.
     */
    val isAvailable: Boolean
}

/**
 * Production Android Photo Picker wrapper using modern [ActivityResultContracts.PickVisualMedia].
 *
 * Guarantees:
 * - Zero broad storage permissions requested.
 * - Single image selection filtered to image media types.
 * - Safe fallback for devices with or without Google Play services photo picker module.
 */
class SystemPhotoPicker(
    private val context: Context,
) : ImagePicker {

    private var activeLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var pendingContinuation: ((Result<String?>) -> Unit)? = null

    /**
     * Registers an active [ActivityResultLauncher] from an Activity or Composable.
     */
    fun registerLauncher(launcher: ActivityResultLauncher<PickVisualMediaRequest>) {
        this.activeLauncher = launcher
    }

    /**
     * Unregisters the launcher when lifecycle is destroyed or detached.
     */
    fun unregisterLauncher() {
        this.activeLauncher = null
        pendingContinuation = null
    }

    /**
     * Dispatches the result from [ActivityResultContracts.PickVisualMedia] callback.
     */
    fun onPickerResult(uriString: String?) {
        pendingContinuation?.invoke(Result.success(uriString))
        pendingContinuation = null
    }

    fun createRequest(): PickVisualMediaRequest {
        return PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    }

    fun createIntent(): Intent {
        return ActivityResultContracts.PickVisualMedia().createIntent(context, createRequest())
    }

    override suspend fun pickImage(): Result<String?> {
        val launcher = activeLauncher
        if (launcher == null) {
            return Result.success(null)
        }
        return suspendCancellableCoroutine { continuation ->
            pendingContinuation = { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            continuation.invokeOnCancellation {
                pendingContinuation = null
            }
            try {
                launcher.launch(createRequest())
            } catch (exc: Throwable) {
                if (continuation.isActive) {
                    continuation.resume(Result.failure(exc))
                }
                pendingContinuation = null
            }
        }
    }

    override val isAvailable: Boolean
        get() = ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)
}

/**
 * Stage 1 platform placeholder.
 */
class Stage1ImagePicker : ImagePicker {
    override suspend fun pickImage(): Result<String?> {
        return Result.failure(UnsupportedOperationException("Image picker is enabled in Stage 2."))
    }

    override val isAvailable: Boolean = false
}
