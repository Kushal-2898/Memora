package app.memora.platform.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import app.memora.domain.model.MemoraError
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Concrete CameraX controller handling lifecycle binding, lens selection,
 * hardware availability checks, torch flash control, live frame analysis,
 * and photo capture to managed storage.
 */
class CameraXManager(
    private val context: Context,
) {
    private var imageCaptureUseCase: ImageCapture? = null
    private var camera: Camera? = null
    private var imageAnalysisUseCase: ImageAnalysis? = null
    private var analyzerExecutor: ExecutorService? = null

    fun isCameraAvailable(): Boolean {
        return runCatching {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ||
                cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        }.getOrDefault(true)
    }

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        imageAnalyzer: ImageAnalysis.Analyzer? = null,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setResolutionSelector(resolutionSelector)
                    .build()

                imageCaptureUseCase = imageCapture

                analyzerExecutor?.shutdown()
                val imageAnalysis = imageAnalyzer?.let { analyzer ->
                    val executor = Executors.newSingleThreadExecutor().also { analyzerExecutor = it }
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor, analyzer)
                        }
                }
                imageAnalysisUseCase = imageAnalysis

                val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    throw MemoraError.Capture.CameraUnavailable("No camera lens available on this device.")
                }

                cameraProvider.unbindAll()
                val boundCamera = if (imageAnalysis != null) {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                        imageAnalysis
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                }
                camera = boundCamera
                onSuccess()
            } catch (exc: Throwable) {
                onError(exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun enableTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun setTorchEnabled(enabled: Boolean) {
        enableTorch(enabled)
    }

    fun isTorchAvailable(): Boolean {
        return camera?.cameraInfo?.hasFlashUnit() == true
    }

    fun focusAt(x: Float, y: Float, previewView: PreviewView) {
        runCatching {
            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            camera?.cameraControl?.startFocusAndMetering(action)
        }
    }

    fun setZoomRatio(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    fun getZoomState() = camera?.cameraInfo?.zoomState

    suspend fun takePicture(outputFile: File): Result<File> =
        suspendCancellableCoroutine { continuation ->
            val imageCapture = imageCaptureUseCase
            if (imageCapture == null) {
                continuation.resume(
                    Result.failure(MemoraError.Capture.CameraUnavailable("Camera is not bound or initialized."))
                )
                return@suspendCancellableCoroutine
            }

            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
            val executor = ContextCompat.getMainExecutor(context)

            imageCapture.takePicture(
                outputFileOptions,
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        continuation.resume(Result.success(outputFile))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resume(
                            Result.failure(
                                MemoraError.Capture.StorageFailed(
                                    exception.message ?: "Failed to save photo from camera",
                                    exception
                                )
                            )
                        )
                    }
                }
            )
        }

    fun unbind() {
        runCatching {
            camera?.cameraControl?.enableTorch(false)
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
        imageCaptureUseCase = null
        imageAnalysisUseCase?.clearAnalyzer()
        imageAnalysisUseCase = null
        analyzerExecutor?.shutdown()
        analyzerExecutor = null
        camera = null
    }
}
