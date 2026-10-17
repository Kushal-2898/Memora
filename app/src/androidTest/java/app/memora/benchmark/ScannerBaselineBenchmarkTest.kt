package app.memora.benchmark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.memora.domain.benchmark.ScannerBenchmarkEngine
import app.memora.domain.benchmark.OcrEngine
import app.memora.domain.model.CapturedImage
import app.memora.domain.model.CaptureSource
import app.memora.domain.usecase.ExtractLocallyUseCase
import app.memora.platform.ocr.MlKitBarcodeExtractor
import app.memora.platform.ocr.MlKitTextExtractor
import app.memora.domain.extraction.DeterministicMemoryExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ScannerBaselineBenchmarkTest {

    @Test
    fun runBaselineBenchmark() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        
        // 1. Setup the dummy test image
        val testFile = File(context.cacheDir, "benchmark_target.jpg")
        val bitmap = Bitmap.createBitmap(1024, 768, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 48f
            isAntiAlias = true
        }
        canvas.drawText("PARACETAMOL 500MG", 100f, 100f, paint)
        canvas.drawText("MFG: 01/2024", 100f, 200f, paint)
        canvas.drawText("EXP: 12/2026", 100f, 300f, paint)
        
        FileOutputStream(testFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        val capturedImage = CapturedImage(
            uri = testFile.absolutePath,
            source = CaptureSource.CAMERA,
            capturedAt = Instant.now(),
            mimeType = "image/jpeg",
            fileSize = testFile.length()
        )

        // 2. Setup the dependencies for ML Kit Baseline
        val textExtractor = MlKitTextExtractor(context)
        val barcodeExtractor = MlKitBarcodeExtractor(context)
        val clockProvider = app.memora.domain.scheduler.SystemClockProvider()
        val deterministicExtractor = DeterministicMemoryExtractor(clockProvider)
        
        val extractLocallyUseCase = ExtractLocallyUseCase(
            textExtractor = textExtractor,
            barcodeExtractor = barcodeExtractor,
            deterministicExtractor = deterministicExtractor
        )

        // 3. Configure Benchmark Engine
        ScannerBenchmarkEngine.activeEngine = OcrEngine.ML_KIT
        
        // Warmup Run (ignores first run latency)
        println("Running Warmup...")
        extractLocallyUseCase.invoke(image = capturedImage, scanId = "WARMUP")

        // 4. Run the benchmark
        println("Running Benchmark...")
        val result = extractLocallyUseCase.invoke(image = capturedImage, scanId = "BASELINE_001")
        
        // 5. Output Results
        val benchmarkResult = ScannerBenchmarkEngine.lastResult
        
        // Save to logcat and a local file
        val output = benchmarkResult?.toString() ?: "Benchmark Failed: No result."
        println(output)
        
        // We can assert something basic to ensure it passes
        assert(benchmarkResult != null)
        assert(benchmarkResult!!.ocrSuccess)
        assert(benchmarkResult.extractedDate != null)
    }

    @Test
    fun runRapidOcrBenchmark() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        
        // 1. Setup the dummy test image (Same input)
        val testFile = File(context.cacheDir, "benchmark_target_rapid.jpg")
        val bitmap = Bitmap.createBitmap(1024, 768, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 48f
            isAntiAlias = true
        }
        canvas.drawText("PARACETAMOL 500MG", 100f, 100f, paint)
        canvas.drawText("MFG: 01/2024", 100f, 200f, paint)
        canvas.drawText("EXP: 12/2026", 100f, 300f, paint)
        
        FileOutputStream(testFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        val capturedImage = CapturedImage(
            uri = testFile.absolutePath,
            source = CaptureSource.CAMERA,
            capturedAt = Instant.now(),
            mimeType = "image/jpeg",
            fileSize = testFile.length()
        )

        // 2. Setup the dependencies for RapidOCR Baseline
        val textExtractor = app.memora.platform.ocr.RapidOcrExtractor(context)
        val barcodeExtractor = MlKitBarcodeExtractor(context)
        val clockProvider = app.memora.domain.scheduler.SystemClockProvider()
        val deterministicExtractor = DeterministicMemoryExtractor(clockProvider)
        
        val extractLocallyUseCase = ExtractLocallyUseCase(
            textExtractor = textExtractor,
            barcodeExtractor = barcodeExtractor,
            deterministicExtractor = deterministicExtractor
        )

        // 3. Configure Benchmark Engine
        ScannerBenchmarkEngine.activeEngine = OcrEngine.RAPIDOCR
        
        // Warmup Run (ignores first run latency)
        println("Running RapidOCR Warmup...")
        extractLocallyUseCase.invoke(image = capturedImage, scanId = "WARMUP_RAPIDOCR")

        // 4. Run the benchmark
        println("Running RapidOCR Benchmark...")
        val result = extractLocallyUseCase.invoke(image = capturedImage, scanId = "BASELINE_RAPIDOCR_001")
        
        // 5. Output Results
        val benchmarkResult = ScannerBenchmarkEngine.lastResult
        
        // Save to logcat and a local file
        val output = benchmarkResult?.toString() ?: "Benchmark Failed: No result."
        println(output)
        
        assert(benchmarkResult != null)
        assert(benchmarkResult!!.ocrSuccess)
    }
}
