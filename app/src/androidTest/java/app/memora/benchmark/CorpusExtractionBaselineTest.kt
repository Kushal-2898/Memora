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

        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        val maxSide = args.getString("maxSide")?.toIntOrNull() ?: 1536
        val boxThresh = args.getString("boxThresh")?.toFloatOrNull() ?: 0.20f
        val boxScoreThresh = args.getString("boxScoreThresh")?.toFloatOrNull() ?: 0.40f
        val unclip = args.getString("unclip")?.toFloatOrNull() ?: 2.0f
        val threads = args.getString("threads")?.toIntOrNull() ?: 4
        val doAngle = args.getString("doAngle")?.toBooleanStrictOrNull() ?: true
        val twoStage = args.getString("twoStage")?.toBooleanStrictOrNull() ?: false
        val runTag = args.getString("runTag") ?: "baseline"

        println("==================================================")
        println("SWEEP CONFIG: runTag=$runTag maxSide=$maxSide boxThresh=$boxThresh unclip=$unclip threads=$threads doAngle=$doAngle twoStage=$twoStage")
        println("==================================================")

        val config = app.memora.platform.ocr.RapidOcrConfig(
            maxSideLen = maxSide,
            boxScoreThresh = boxScoreThresh,
            boxThresh = boxThresh,
            unClipRatio = unclip,
            numThreads = threads,
            doAngle = doAngle,
            isTwoStage = twoStage,
        )
        val extractor = app.memora.platform.ocr.RapidOcrExtractor(context, config)
        val prodResult = app.memora.domain.benchmark.ExpiryEvaluationHarness.runEvaluation(
            context = context,
            inputDir = evalDir,
            runAllPasses = false,
            rapidOcrExtractor = extractor,
        )
        prodResult.fold(
            onSuccess = { summary ->
                println(summary.formattedSummary)
                val exportName = "memora_eval_${runTag}.csv"
                val exportedResult = app.memora.domain.benchmark.ExpiryEvaluationHarness.exportCsvToDownloads(
                    context = context,
                    sourceCsvFile = summary.generatedCsvFile,
                    customFilename = exportName,
                )
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

    @Test
    fun exportDebugArtifacts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataDir = File(context.applicationInfo.dataDir)
        try {
            Runtime.getRuntime().exec(arrayOf("chmod", "751", dataDir.absolutePath)).waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val debugSrcDir = File(context.filesDir, "evaluation/debug")
        val outZip = File(context.getExternalFilesDir(null), "debug_artifacts.zip")
        if (debugSrcDir.exists() && debugSrcDir.isDirectory) {
            java.util.zip.ZipOutputStream(outZip.outputStream().buffered()).use { zos ->
                debugSrcDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relPath = debugSrcDir.toURI().relativize(file.toURI()).path
                    val entry = java.util.zip.ZipEntry(relPath)
                    zos.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            println("Successfully exported debug artifacts to: ${outZip.absolutePath} (${outZip.length()} bytes)")
        } else {
            println("Debug directory does not exist: ${debugSrcDir.absolutePath}")
        }
    }

    @Test
    fun runCropComparisonHarness() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val debugCapturesDir = app.memora.platform.image.DebugCaptureSaver.getDebugCapturesDir(context)
        val evalDir = app.memora.domain.benchmark.ExpiryEvaluationHarness.getEvaluationDirectory(context)

        println("==================================================")
        println("STARTING ON-DEVICE CROP COMPARISON HARNESS")
        println("Debug Captures Dir: ${debugCapturesDir.absolutePath}")
        println("==================================================")

        val result = app.memora.domain.benchmark.ExpiryEvaluationHarness.runCropComparison(
            context = context,
            capturesDir = debugCapturesDir,
        )

        result.fold(
            onSuccess = { summary ->
                println(summary.formattedSummary)
            },
            onFailure = { err ->
                println("Crop Comparison Failed: ${err.message}")
            }
        )
    }
}
