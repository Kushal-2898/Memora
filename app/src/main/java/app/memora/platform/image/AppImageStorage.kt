package app.memora.platform.image

import android.content.Context
import android.net.Uri
import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.domain.model.MemoraError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * Concrete Android implementation of [ImageStorage].
 *
 * Streamlines image persistence into the app's private files directory (`filesDir/captures`).
 * Guarantees that images selected via Photo Picker remain securely accessible to the application
 * across lifecycles and process recreation without depending on transient Android URI grants.
 */
class AppImageStorage(
    private val context: Context,
) : ImageStorage {

    private val captureDirectory: File by lazy {
        File(context.filesDir, "captures").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    override fun createCaptureFile(prefix: String): File {
        val sanitizedPrefix = prefix.filter { it.isLetterOrDigit() }.ifEmpty { "capture" }
        return File(captureDirectory, "${sanitizedPrefix}_${System.currentTimeMillis()}.jpg")
    }

    override suspend fun importFromUri(uriString: String, source: CaptureSource): Result<CapturedImage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val parsedUri = Uri.parse(uriString)
                    ?: throw MemoraError.Capture.ImportFailed("Invalid URI format: $uriString")

                val targetFile = createCaptureFile(
                    prefix = when (source) {
                        CaptureSource.CAMERA -> "photo"
                        CaptureSource.GALLERY -> "image"
                        CaptureSource.SCREENSHOT -> "screenshot"
                    }
                )

                val mimeType = runCatching {
                    context.contentResolver.getType(parsedUri)
                }.getOrNull() ?: "image/jpeg"

                val inputStream = context.contentResolver.openInputStream(parsedUri)
                    ?: throw MemoraError.Capture.ImportFailed("Unable to open stream for URI: $uriString")

                inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                if (!targetFile.exists() || targetFile.length() == 0L) {
                    targetFile.delete()
                    throw MemoraError.Capture.ImportFailed("Imported file was empty or could not be saved.")
                }

                CapturedImage(
                    uri = targetFile.toURI().toString(),
                    source = source,
                    capturedAt = Instant.now(),
                    mimeType = mimeType,
                    fileSize = targetFile.length(),
                )
            }.recoverCatching { throwable ->
                if (throwable is MemoraError.Capture) {
                    throw throwable
                } else {
                    throw MemoraError.Capture.ImportFailed(throwable.message ?: "Unknown import error", throwable)
                }
            }
        }

    override fun createCapturedImageFromFile(file: File, source: CaptureSource): CapturedImage {
        return CapturedImage(
            uri = file.toURI().toString(),
            source = source,
            capturedAt = Instant.now(),
            mimeType = "image/jpeg",
            fileSize = if (file.exists()) file.length() else 0L,
        )
    }

    override fun deleteImage(uriString: String): Boolean {
        return runCatching {
            val path = try {
                Uri.parse(uriString)?.path
            } catch (e: Throwable) {
                null
            } ?: runCatching { java.net.URI(uriString).path }.getOrNull()
                ?: uriString.removePrefix("file:")
            val file = File(path)
            if (file.exists() && file.canonicalPath.startsWith(captureDirectory.canonicalPath)) {
                file.delete()
            } else {
                false
            }
        }.getOrDefault(false)
    }
}
