package app.memora.platform.ocr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RapidOcrConfig(
    val maxSideLen: Int = 1536,
    val boxScoreThresh: Float = 0.40f,
    val boxThresh: Float = 0.20f,
    val unClipRatio: Float = 2.0f,
    val numThreads: Int = 4,
    val doAngle: Boolean = true,
    val mostAngle: Boolean = false,
    val isTwoStage: Boolean = false,
) {
    companion object {
        val DEFAULT = RapidOcrConfig()
    }
}

data class RapidOcrPassInfo(
    val passName: String,
    val durationMs: Long,
    val rawText: String,
    val hasAnchoredDate: Boolean,
    val extractedText: ExtractedText,
    val decodeCropMs: Long = 0L,
    val detMs: Double = 0.0,
    val recMs: Double = 0.0,
    val boxesDetected: Int = 0,
    val boxesRecognized: Int = 0,
    val msPerBox: Double = 0.0,
)

data class RapidOcrEvaluationData(
    val extractedText: ExtractedText,
    val passes: List<RapidOcrPassInfo>,
    val winningPass: String = "original",
    val totalMs: Long = 0L,
    val isBudgetExceeded: Boolean = false,
)

/**
 * Legacy interface stub delegating to [MlKitTextExtractor].
 * RapidOCR native NCNN dependency (26.3MB) has been removed.
 */
class RapidOcrExtractor(
    private val context: Context,
    val config: RapidOcrConfig = RapidOcrConfig.DEFAULT,
) : TextExtractor {

    companion object {
        const val SCAN_TIME_BUDGET_MS = 8000L
    }

    private val mlKitExtractor by lazy { MlKitTextExtractor(context.applicationContext) }

    override suspend fun extractText(imageUri: String): Result<ExtractedText> =
        mlKitExtractor.extractText(imageUri)

    override suspend fun extractText(imageUri: String, scanId: String?): Result<ExtractedText> =
        mlKitExtractor.extractText(imageUri, scanId)

    override val isAvailable: Boolean
        get() = mlKitExtractor.isAvailable

    suspend fun extractForEvaluation(
        imageUri: String,
        runAllPasses: Boolean = false,
        forceFallbackCrop: Boolean = false,
    ): Result<RapidOcrEvaluationData> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val textResult = mlKitExtractor.extractText(imageUri)
        val duration = System.currentTimeMillis() - start
        if (textResult.isSuccess) {
            val extracted = textResult.getOrThrow()
            Result.success(
                RapidOcrEvaluationData(
                    extractedText = extracted,
                    passes = listOf(
                        RapidOcrPassInfo(
                            passName = "ml_kit",
                            durationMs = duration,
                            rawText = extracted.rawText,
                            hasAnchoredDate = OcrDateDetector.hasAnchoredDate(extracted.rawText),
                            extractedText = extracted,
                        )
                    ),
                    winningPass = "ML_KIT",
                    totalMs = duration,
                    isBudgetExceeded = false,
                )
            )
        } else {
            Result.failure(textResult.exceptionOrNull() ?: Exception("OCR failed"))
        }
    }
}
