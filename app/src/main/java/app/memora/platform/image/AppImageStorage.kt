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

                val maxImportBytes = 20L * 1024L * 1024L
                var totalCopied = 0L
                inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } >= 0) {
                            totalCopied += read
                            if (totalCopied > maxImportBytes) {
                                output.close()
                                targetFile.delete()
                                throw MemoraError.Capture.ImportFailed("Image exceeds 20 MB maximum size limit.")
                            }
                            output.write(buffer, 0, read)
                        }
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

    override suspend fun compressForEvidence(uriString: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val path = runCatching { Uri.parse(uriString)?.path }.getOrNull()
                ?: runCatching { java.net.URI(uriString).path }.getOrNull()
                ?: uriString.removePrefix("file:")
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return@runCatching null

            val boundsOpts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
            val maxDim = 2560
            var sample = 1
            while (boundsOpts.outWidth / sample > maxDim || boundsOpts.outHeight / sample > maxDim) {
                sample *= 2
            }
            val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                ?: return@runCatching file.readBytes().takeIf { it.size <= 750_000 }?.let { EvidenceHash.gzip(it) }

            val out = java.io.ByteArrayOutputStream()
            val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                android.graphics.Bitmap.CompressFormat.WEBP
            }
            var quality = 85
            bitmap.compress(format, quality, out)
            while (out.size() > 700_000 && quality > 50) {
                out.reset()
                quality -= 10
                bitmap.compress(format, quality, out)
            }
            bitmap.recycle()
            EvidenceHash.gzip(out.toByteArray())
        }.getOrNull()
    }

    fun decodeEvidenceBitmap(compressed: ByteArray): android.graphics.Bitmap? {
        val raw = decompressEvidenceBytes(compressed)
        return android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size)
    }
}
