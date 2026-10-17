package app.memora.benchmark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.memora.domain.benchmark.OcrEngine
import app.memora.domain.benchmark.ScannerBenchmarkEngine
import app.memora.domain.extraction.DeterministicMemoryExtractor
import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.domain.usecase.ExtractLocallyUseCase
import app.memora.platform.ocr.MlKitBarcodeExtractor
import app.memora.platform.ocr.MlKitTextExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileWriter
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class CorpusExtractionBaselineTest {

    @Test
    fun runCorpusBaselineExtraction() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val benchmarkDir = File(context.filesDir, "benchmark")
        
        val imageFiles = benchmarkDir.listFiles { file ->
            val name = file.name.lowercase()
            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
        }?.sortedBy { it.name } ?: emptyList()

        println("==================================================")
        println("STARTING CORPUS BASELINE: Found ${imageFiles.size} images in ${benchmarkDir.absolutePath}")
        println("==================================================")

        val textExtractor = MlKitTextExtractor(context)
        val barcodeExtractor = MlKitBarcodeExtractor(context)
        val clockProvider = app.memora.domain.scheduler.SystemClockProvider()
        val deterministicExtractor = DeterministicMemoryExtractor(clockProvider)

        val extractLocallyUseCase = ExtractLocallyUseCase(
            textExtractor = textExtractor,
            barcodeExtractor = barcodeExtractor,
            deterministicExtractor = deterministicExtractor
        )

        ScannerBenchmarkEngine.activeEngine = OcrEngine.ML_KIT

        val reportOutputFile = File(context.cacheDir, "baseline_results.tsv")
        val writer = FileWriter(reportOutputFile)
        writer.write("filename\tocr_latency_ms\te2e_latency_ms\textracted_date\textracted_title\traw_ocr_snippet\n")

        var successCount = 0
        var totalCount = 0

        for (imageFile in imageFiles) {
            totalCount++
            val capturedImage = CapturedImage(
                uri = imageFile.absolutePath,
                source = CaptureSource.CAMERA,
                capturedAt = Instant.now(),
                mimeType = "image/jpeg",
                fileSize = imageFile.length()
            )

            val startTime = System.currentTimeMillis()
            val result = extractLocallyUseCase.invoke(capturedImage, scanId = "CORPUS_${imageFile.name}")
            val totalTime = System.currentTimeMillis() - startTime

            val scanOutput = result.getOrNull()
            val localResult = scanOutput?.localResult
            val extractedDate = localResult?.selectedEventDate?.toString() ?: "NONE"
            val extractedTitle = localResult?.title ?: "NONE"
            val rawText = scanOutput?.ocrResult?.rawText?.replace("\n", " [NL] ")?.replace("\t", " ") ?: ""
            val benchmarkResult = ScannerBenchmarkEngine.lastResult
            val ocrLatency = benchmarkResult?.ocrLatencyMs ?: 0L

            if (extractedDate != "NONE") {
                successCount++
            }

            println("[$totalCount/${imageFiles.size}] ${imageFile.name} | Date: $extractedDate | Title: $extractedTitle | OCR: ${ocrLatency}ms")
            writer.write("${imageFile.name}\t$ocrLatency\t$totalTime\t$extractedDate\t$extractedTitle\t$rawText\n")
            writer.flush()
        }

        writer.close()
        println("==================================================")
        println("CORPUS BASELINE COMPLETE: $successCount / $totalCount images extracted a candidate date")
        println("Results written to: ${reportOutputFile.absolutePath}")
        println("==================================================")
    }

    @Test
    fun runExpiryPipelineEvaluationHarness() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val evalDir = app.memora.domain.benchmark.ExpiryEvaluationHarness.getEvaluationDirectory(context)

        println("==================================================")
        println("STARTING ON-DEVICE EXPIRY PIPELINE EVALUATION HARNESS (PRODUCTION EARLY-EXIT)")
        println("Target Directory: ${evalDir.absolutePath}")
        println("==================================================")

        val extractor = app.memora.platform.ocr.RapidOcrExtractor(context)
        val prodResult = app.memora.domain.benchmark.ExpiryEvaluationHarness.runEvaluation(
            context = context,
            inputDir = evalDir,
            runAllPasses = false,
            rapidOcrExtractor = extractor,
        )
        prodResult.fold(
            onSuccess = { summary ->
                println(summary.formattedSummary)
                val exportedResult = app.memora.domain.benchmark.ExpiryEvaluationHarness.exportCsvToDownloads(context, summary.generatedCsvFile)
                exportedResult.fold(
                    onSuccess = { dest -> println("Exported Production CSV to Downloads: ${dest.absolutePath}") },
                    onFailure = { err -> println("Export to Downloads failed: ${err.message}") }
                )
            },
            onFailure = { err ->
                println("Production Evaluation Harness Failed: ${err.message}")
            }
        )
    }

    @Test
    fun runExpiryPipelineEvaluationHarnessAllPasses() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val evalDir = app.memora.domain.benchmark.ExpiryEvaluationHarness.getEvaluationDirectory(context)

        println("==================================================")
        println("STARTING ON-DEVICE EXPIRY PIPELINE EVALUATION HARNESS (ALL PASSES - NO EARLY EXIT)")
        println("Target Directory: ${evalDir.absolutePath}")
        println("==================================================")

        val result = app.memora.domain.benchmark.ExpiryEvaluationHarness.runEvaluation(
            context = context,
            inputDir = evalDir,
            runAllPasses = true,
        )
        result.fold(
            onSuccess = { summary ->
                println(summary.formattedSummary)
                val exportedResult = app.memora.domain.benchmark.ExpiryEvaluationHarness.exportCsvToDownloads(context, summary.generatedCsvFile)
                exportedResult.fold(
                    onSuccess = { dest -> println("Exported All-Passes CSV to Downloads: ${dest.absolutePath}") },
                    onFailure = { err -> println("Export to Downloads failed: ${err.message}") }
                )
            },
            onFailure = { err ->
                println("All-Passes Evaluation Harness Failed: ${err.message}")
            }
        )
    }
}
