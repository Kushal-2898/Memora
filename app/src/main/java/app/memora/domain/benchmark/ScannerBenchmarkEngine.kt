package app.memora.domain.benchmark

import android.os.Build
import app.memora.BuildConfig
import java.util.UUID

enum class OcrEngine {
    ML_KIT,
    RAPIDOCR,
    UNKNOWN
}

data class BenchmarkResult(
    val benchmarkRunId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val inputId: String,
    val appVersion: String = "1.0",
    val buildVariant: String = "debug",
    val deviceModel: String = try { Build.MODEL ?: "Unknown" } catch (_: Throwable) { "Unknown" },
    val androidVersion: String = try { Build.VERSION.RELEASE ?: "Unknown" } catch (_: Throwable) { "Unknown" },
    val apiLevel: Int = try { Build.VERSION.SDK_INT } catch (_: Throwable) { 0 },
    val ocrEngine: OcrEngine,
    
    val scannerStart: Long = 0L,
    val ocrStart: Long = 0L,
    val ocrComplete: Long = 0L,
    val parsingStart: Long = 0L,
    val parsingComplete: Long = 0L,
    val barcodeStart: Long = 0L,
    val barcodeComplete: Long = 0L,
    val finalResult: Long = 0L,
    
    val memoryBeforeMb: Long = 0L,
    val memoryPeakMb: Long = 0L,
    val memoryAfterMb: Long = 0L,
    
    val ocrSuccess: Boolean = false,
    val textBlockCount: Int = 0,
    val lineCount: Int = 0,
    val elementCount: Int = 0,
    val extractedTitleLength: Int = 0,
    val extractedDate: String? = null
) {
    val ocrLatencyMs: Long get() = ocrComplete - ocrStart
    val parsingLatencyMs: Long get() = parsingComplete - parsingStart
    val barcodeLatencyMs: Long get() = barcodeComplete - barcodeStart
    val endToEndLatencyMs: Long get() = finalResult - scannerStart

    override fun toString(): String {
        return """
            --- BENCHMARK RESULT ---
            ID: $benchmarkRunId
            Input: $inputId
            Device: $deviceModel (API $apiLevel)
            Engine: ${ocrEngine.name}
            
            [LATENCY]
            OCR: ${ocrLatencyMs}ms
            Parsing: ${parsingLatencyMs}ms
            Barcode: ${barcodeLatencyMs}ms
            End-to-End: ${endToEndLatencyMs}ms
            
            [RESOURCE]
            Memory Delta: ${memoryAfterMb - memoryBeforeMb} MB (Peak: $memoryPeakMb MB)
            
            [METRICS]
            OCR Success: $ocrSuccess
            Blocks: $textBlockCount | Lines: $lineCount | Elements: $elementCount
            Title Length: $extractedTitleLength
            Extracted Date: ${extractedDate ?: "None"}
            ------------------------
        """.trimIndent()
    }
}

object ScannerBenchmarkEngine {
    private var currentResult: BenchmarkResult? = null
    var activeEngine: OcrEngine = OcrEngine.ML_KIT
    
    fun startRun(inputId: String) {
        val mem = getUsedMemoryMb()
        currentResult = BenchmarkResult(
            inputId = inputId,
            ocrEngine = activeEngine,
            scannerStart = System.currentTimeMillis(),
            memoryBeforeMb = mem,
            memoryPeakMb = mem
        )
    }

    fun markOcrStart() {
        updateMemoryPeak()
        currentResult = currentResult?.copy(ocrStart = System.currentTimeMillis())
    }

    fun markOcrComplete(success: Boolean, textBlockCount: Int, lineCount: Int, elementCount: Int) {
        updateMemoryPeak()
        currentResult = currentResult?.copy(
            ocrComplete = System.currentTimeMillis(),
            ocrSuccess = success,
            textBlockCount = textBlockCount,
            lineCount = lineCount,
            elementCount = elementCount
        )
    }
    
    fun markParsingStart() {
        updateMemoryPeak()
        currentResult = currentResult?.copy(parsingStart = System.currentTimeMillis())
    }
    
    fun markParsingComplete(titleLength: Int, date: String?) {
        updateMemoryPeak()
        currentResult = currentResult?.copy(
            parsingComplete = System.currentTimeMillis(),
            extractedTitleLength = titleLength,
            extractedDate = date
        )
    }
    
    fun markBarcodeStart() {
        updateMemoryPeak()
        currentResult = currentResult?.copy(barcodeStart = System.currentTimeMillis())
    }
    
    fun markBarcodeComplete() {
        updateMemoryPeak()
        currentResult = currentResult?.copy(barcodeComplete = System.currentTimeMillis())
    }

    var lastResult: BenchmarkResult? = null

    fun finishRun(): BenchmarkResult? {
        val result = currentResult?.copy(
            finalResult = System.currentTimeMillis(),
            memoryAfterMb = getUsedMemoryMb()
        )
        updateMemoryPeak()
        lastResult = result
        currentResult = null
        return result
    }

    private fun updateMemoryPeak() {
        currentResult?.let {
            val mem = getUsedMemoryMb()
            if (mem > it.memoryPeakMb) {
                currentResult = it.copy(memoryPeakMb = mem)
            }
        }
    }

    private fun getUsedMemoryMb(): Long {
        return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
    }
}
