package app.memora.platform.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real-time camera frame analyzer powered by Google ML Kit on-device Text Recognition & Barcode Scanning.
 * Includes native Blur Detection (Laplacian Variance equivalent).
 */
class ScanDocumentAnalyzer(
    private val onDocumentDetected: (snippet: String) -> Unit,
    private val onDetectionCleared: () -> Unit = {},
    private val onPackagingDetected: ((PackagingDetectionResult) -> Unit)? = null,
) : ImageAnalysis.Analyzer {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
            .build()
    )
    
    private var lastAnalyzedTimestamp = 0L
    private var consecutiveDetections = 0
    private val isPaused = AtomicBoolean(false)
    private var analysisStartTime = System.currentTimeMillis()

    fun pause() {
        isPaused.set(true)
        consecutiveDetections = 0
    }

    fun resume() {
        isPaused.set(false)
        consecutiveDetections = 0
        analysisStartTime = System.currentTimeMillis()
    }

    fun close() {
        runCatching { textRecognizer.close() }
        runCatching { barcodeScanner.close() }
    }

    /**
     * Calculates image sharpness by taking the absolute variance of adjacent pixels
     * on the Y (luminance) plane of the YUV_420_888 ImageProxy.
     */
    private fun calculateSharpness(imageProxy: ImageProxy): Float {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val width = imageProxy.width
        val height = imageProxy.height
        
        // Sample central 50% region to avoid edge blur & improve performance
        val startX = width / 4
        val endX = startX * 3
        val startY = height / 4
        val endY = startY * 3
        
        var sumOfDiffs = 0L
        var count = 0
        val step = 4
        
        for (y in startY until endY step step) {
            var prevPixel = -1
            for (x in startX until endX step step) {
                val index = y * rowStride + x * plane.pixelStride
                val pixel = buffer.get(index).toInt() and 0xFF
                if (prevPixel != -1) {
                    sumOfDiffs += Math.abs(pixel - prevPixel)
                    count++
                }
                prevPixel = pixel
            }
        }
        
        return if (count > 0) sumOfDiffs.toFloat() / count else 0f
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val closed = AtomicBoolean(false)
        fun safeClose() {
            if (closed.compareAndSet(false, true)) {
                runCatching { imageProxy.close() }
            }
        }

        if (isPaused.get()) {
            safeClose()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalyzedTimestamp < 320L) {
            safeClose()
            return
        }
        lastAnalyzedTimestamp = currentTime

        // 1. Adaptive Blur Detection Filter
        // Starts strict for quick snaps on clean documents, then progressively
        // loosens to allow shiny/curved surfaces (tin cans, blister packs, bottles).
        val sharpness = calculateSharpness(imageProxy)
        val elapsedSeconds = (currentTime - analysisStartTime) / 1000L
        val blurThreshold = when {
            elapsedSeconds < 3 -> 10.0f   // Strict: snap clean documents fast
            elapsedSeconds < 8 -> 7.0f    // Relaxed: allow moderately tricky surfaces
            else -> 5.0f                  // Permissive: last resort for tin cans, foil
        }
        if (sharpness < blurThreshold) {
            if (consecutiveDetections > 0) {
                consecutiveDetections = 0
                onDetectionCleared()
            }
            safeClose()
            return
        }

        val isCrimp = PackagingFeatureDetector.analyzeLuminanceForCrimp(imageProxy)

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            safeClose()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        
        // 2. Run both analyzers concurrently
        val textTask = textRecognizer.process(image)
        val barcodeTask = barcodeScanner.process(image)
        
        Tasks.whenAllComplete(textTask, barcodeTask).addOnCompleteListener {
            if (isPaused.get()) {
                safeClose()
                return@addOnCompleteListener
            }
            
            val visionText = if (textTask.isSuccessful) textTask.result else null
            val barcodes = if (barcodeTask.isSuccessful) barcodeTask.result else null
            
            val rawText = visionText?.text ?: ""
            val lineCount = visionText?.textBlocks?.sumOf { it.lines.size } ?: 0
            val hasBarcode = barcodes?.isNotEmpty() == true

            val isCandidate = hasBarcode || DocumentDetectionFilter.isDocumentCandidate(rawText, lineCount, isCrimp)

            if (isCandidate) {
                consecutiveDetections++
                if (consecutiveDetections >= 2) {
                    val packagingResult = PackagingFeatureDetector.classifyPackaging(rawText, isCrimp)
                    onPackagingDetected?.invoke(packagingResult)
                    
                    val barcodeString = barcodes?.firstOrNull()?.rawValue
                    val snippet = barcodeString?.let { "Barcode: $it" } ?: if (rawText.isNotBlank()) rawText.take(80) else "Detected"
                    onDocumentDetected(snippet)
                }
            } else {
                if (consecutiveDetections > 0) {
                    consecutiveDetections = 0
                    onDetectionCleared()
                }
            }
            
            safeClose()
        }
    }
}
