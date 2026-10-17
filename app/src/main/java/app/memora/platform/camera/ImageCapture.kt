package app.memora.platform.camera

/**
 * Platform abstraction for camera capture.
 *
 * Concrete CameraX implementation will be introduced in Stage 2.
 * Establishing this interface keeps domain logic independent of Android hardware APIs.
 */
interface ImageCapture {
    /**
     * Captures a photo and returns the local file URI upon success.
     */
    suspend fun capturePhoto(): Result<String>

    /**
     * Indicates whether camera hardware and permissions are ready for capture.
     */
    val isAvailable: Boolean
}

/**
 * Concrete CameraX-backed implementation of ImageCapture for Stage 2.
 */
class CameraXImageCapture(
    private val cameraManager: CameraXManager,
    private val imageStorage: app.memora.platform.image.ImageStorage,
) : ImageCapture {
    override suspend fun capturePhoto(): Result<String> {
        val file = imageStorage.createCaptureFile("photo")
        return cameraManager.takePicture(file).map { it.toURI().toString() }
    }

    override val isAvailable: Boolean
        get() = cameraManager.isCameraAvailable()
}

/**
 * Stage 1 platform placeholder.
 */
class Stage1ImageCapture : ImageCapture {
    override suspend fun capturePhoto(): Result<String> {
        return Result.failure(UnsupportedOperationException("Camera capture is enabled in Stage 2."))
    }

    override val isAvailable: Boolean = false
}
