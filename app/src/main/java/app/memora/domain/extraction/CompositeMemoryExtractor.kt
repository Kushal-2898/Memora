package app.memora.domain.extraction

import android.net.Uri

class CompositeMemoryExtractor(
    private val primary: MemoryExtractor,
    private val fallback: MemoryExtractor
) : MemoryExtractor {
    override suspend fun extractCandidate(rawText: String, imageUri: Uri?): Result<ExtractedMemoryCandidate> =
        extractCandidate(rawText, imageUri, null)

    override suspend fun extractCandidate(rawText: String, imageUri: Uri?, scanId: String?): Result<ExtractedMemoryCandidate> {
        val id = scanId ?: "N/A"
        val primaryResult = primary.extractCandidate(rawText, imageUri, scanId)
        if (primaryResult.isSuccess) {
            app.memora.platform.logging.AppLog.recordFallback(id, fallbackUsed = false)
            return primaryResult
        }
        val failureReason = primaryResult.exceptionOrNull()?.let {
            app.memora.platform.logging.GeminiFailureReason.fromThrowable(it).name
        } ?: "GEMINI_FAILURE"
        app.memora.platform.logging.AppLog.recordFallback(id, fallbackUsed = true, reason = failureReason)
        return fallback.extractCandidate(rawText, imageUri, scanId)
    }
}
