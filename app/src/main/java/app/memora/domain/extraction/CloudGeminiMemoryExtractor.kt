package app.memora.domain.extraction

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import app.memora.domain.model.EventType
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * Cloud-based Gemini AI extractor routing through Firebase Callable Function.
 *
 * Security Guarantee:
 * - ZERO API keys embedded in the client APK.
 * - Authenticated with caller's Firebase Auth token.
 * - GEMINI_API_KEY is resolved strictly server-side via Google Cloud Secret Manager.
 */
class CloudGeminiMemoryExtractor(
    private val context: Context,
    private val functions: FirebaseFunctions? = runCatching { FirebaseFunctions.getInstance() }.getOrNull()
) : MemoryExtractor {

    override suspend fun extractCandidate(
        rawText: String,
        imageUri: Uri?
    ): Result<ExtractedMemoryCandidate> = extractCandidate(rawText, imageUri, null)

    override suspend fun extractCandidate(
        rawText: String,
        imageUri: Uri?,
        scanId: String?
    ): Result<ExtractedMemoryCandidate> = withContext(Dispatchers.IO) {
        val fns = functions ?: return@withContext Result.failure(
            IllegalStateException("Firebase Functions is not available on this device.")
        )

        try {
            val imageBase64 = imageUri?.let { uri ->
                val bitmap = loadAndDownsampleBitmap(uri) ?: return@let null
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val bytes = stream.toByteArray()
                bitmap.recycle()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }

            val payload = hashMapOf<String, Any?>(
                "prompt" to rawText,
                "scanId" to (scanId ?: "N/A"),
            )
            if (imageBase64 != null) {
                payload["imageBase64"] = imageBase64
                payload["mimeType"] = "image/jpeg"
            }

            val callable = fns.getHttpsCallable("extractMemoryWithGemini")
            val taskResult = callable.call(payload).await()

            @Suppress("UNCHECKED_CAST")
            val data = taskResult.data as? Map<String, Any?>
                ?: return@withContext Result.failure(IllegalStateException("Empty response from extractMemoryWithGemini"))

            val title = data["title"] as? String
            val eventTypeStr = data["eventType"] as? String ?: "OTHER"
            val eventType = runCatching { EventType.valueOf(eventTypeStr) }.getOrDefault(EventType.OTHER)
            val dateStr = data["date"] as? String
            val date = dateStr?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val confidence = (data["confidence"] as? Number)?.toDouble() ?: 0.8

            @Suppress("UNCHECKED_CAST")
            val rawAlternatives = data["alternatives"] as? List<Map<String, Any?>> ?: emptyList()
            val alternatives = rawAlternatives.mapNotNull { alt ->
                val altDateStr = alt["date"] as? String ?: return@mapNotNull null
                val altDate = runCatching { LocalDate.parse(altDateStr) }.getOrNull() ?: return@mapNotNull null
                val altContext = alt["context"] as? String ?: "Candidate date"
                ExtractedDateCandidate(
                    eventType = eventType,
                    eventDate = altDate,
                    confidence = confidence.toFloat(),
                    context = altContext,
                    hasExplicitDay = true,
                )
            }

            val candidate = ExtractedMemoryCandidate(
                suggestedTitle = title,
                suggestedEventType = eventType,
                suggestedEventDate = date,
                confidence = confidence.toFloat(),
                rawTextSnippet = rawText,
                requiresConfirmation = true,
                alternatives = alternatives,
                extractionSource = ExtractionSource.GEMINI_AI,
            )

            Result.success(candidate)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadAndDownsampleBitmap(uri: Uri): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            }

            val maxDimension = 1024
            var sampleSize = 1
            if (boundsOptions.outHeight > maxDimension || boundsOptions.outWidth > maxDimension) {
                val halfHeight = boundsOptions.outHeight / 2
                val halfWidth = boundsOptions.outWidth / 2
                while ((halfHeight / sampleSize) >= maxDimension && (halfWidth / sampleSize) >= maxDimension) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }
}
