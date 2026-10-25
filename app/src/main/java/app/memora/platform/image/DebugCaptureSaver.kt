package app.memora.platform.image

import android.content.Context
import app.memora.platform.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

/**
 * Saves in-app captures along with a JSON sidecar recording the on-screen
 * viewfinder reticle bounding box (normalized 0..1), preview dimensions,
 * sensor rotation degrees, and photo dimensions.
 */
object DebugCaptureSaver {

    private const val TAG = "DebugCaptureSaver"
    private const val DEBUG_CAPTURES_DIR_NAME = "debug_captures"

    /**
     * Returns the dedicated directory for storing debug captures and their JSON sidecars.
     */
    fun getDebugCapturesDir(context: Context): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(baseDir, DEBUG_CAPTURES_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Saves the full captured photo and a matching sidecar JSON metadata file
     * to the debug captures directory. Guarantees file operations run on [Dispatchers.IO].
     *
     * @return The saved JSON sidecar file.
     */
    suspend fun saveCapture(
        context: Context,
        sourcePhotoFile: File,
        metadata: ReticleCaptureMetadata,
    ): File? = withContext(Dispatchers.IO) {
        val debugDir = getDebugCapturesDir(context)
        saveCaptureToDir(debugDir, sourcePhotoFile, metadata)
    }

    /**
     * Saves the photo and metadata JSON to a specified directory on [Dispatchers.IO].
     */
    suspend fun saveCaptureToDir(
        debugDir: File,
        sourcePhotoFile: File,
        metadata: ReticleCaptureMetadata,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            AppLog.checkMainThreadViolation("DebugCaptureSaver.saveCaptureToDir")
            if (!debugDir.exists()) {
                debugDir.mkdirs()
            }
            val baseName = sourcePhotoFile.nameWithoutExtension
            val ext = sourcePhotoFile.extension.ifBlank { "jpg" }

            val targetPhotoFile = File(debugDir, "${baseName}.$ext")
            if (sourcePhotoFile.absolutePath != targetPhotoFile.absolutePath) {
                sourcePhotoFile.copyTo(targetPhotoFile, overwrite = true)
            }

            val targetJsonFile = File(debugDir, "${baseName}.json")
            val jsonString = serializeMetadata(metadata)
            targetJsonFile.writeText(jsonString)

            // Also write a sidecar next to the original source file if it was outside debug_captures
            if (sourcePhotoFile.parentFile != null && sourcePhotoFile.parentFile?.absolutePath != debugDir.absolutePath) {
                val companionJson = File(sourcePhotoFile.parentFile, "${baseName}.json")
                runCatching { companionJson.writeText(jsonString) }
            }

            AppLog.i(
                TAG,
                "Saved debug capture:\nPhoto: ${targetPhotoFile.absolutePath}\nJSON: ${targetJsonFile.absolutePath}\nFolder: ${debugDir.absolutePath}"
            )

            targetJsonFile
        }.onFailure { err ->
            AppLog.e(TAG, "Failed to save debug capture: ${err.message}", err)
        }.getOrNull()
    }

    /**
     * Synchronous blocking convenience wrapper for test or non-coroutine contexts.
     */
    fun saveCaptureBlocking(
        context: Context,
        sourcePhotoFile: File,
        metadata: ReticleCaptureMetadata,
    ): File? = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
        saveCapture(context, sourcePhotoFile, metadata)
    }

    /**
     * Serializes [ReticleCaptureMetadata] to a formatted JSON string.
     */
    fun serializeMetadata(metadata: ReticleCaptureMetadata): String {
        return """
        {
          "reticle_rect": {
            "left": ${metadata.normalizedRect.left},
            "top": ${metadata.normalizedRect.top},
            "right": ${metadata.normalizedRect.right},
            "bottom": ${metadata.normalizedRect.bottom}
          },
          "preview_size": {
            "width": ${metadata.previewWidth},
            "height": ${metadata.previewHeight}
          },
          "rotation": ${metadata.rotationDegrees},
          "photo_size": {
            "width": ${metadata.photoWidth},
            "height": ${metadata.photoHeight}
          },
          "timestamp_ms": ${metadata.timestampMs}
        }
        """.trimIndent()
    }

    /**
     * Parses a JSON string into [ReticleCaptureMetadata].
     */
    fun parseMetadata(jsonString: String): ReticleCaptureMetadata? {
        return runCatching {
            fun extractDouble(key: String): Double? {
                val match = Regex(""""$key"\s*:\s*([0-9.]+)""").find(jsonString)
                return match?.groupValues?.get(1)?.toDoubleOrNull()
            }
            fun extractLong(key: String): Long? {
                val match = Regex(""""$key"\s*:\s*([0-9]+)""").find(jsonString)
                return match?.groupValues?.get(1)?.toLongOrNull()
            }

            // reticle_rect check: must exist and have all 4 bounds
            if (!jsonString.contains("reticle_rect")) return null

            val left = extractDouble("left")?.toFloat() ?: return null
            val top = extractDouble("top")?.toFloat() ?: return null
            val right = extractDouble("right")?.toFloat() ?: return null
            val bottom = extractDouble("bottom")?.toFloat() ?: return null

            // preview_size check: must exist and have positive dimensions
            val previewWidth = Regex(""""preview_size"\s*:\s*\{[^}]*"width"\s*:\s*([0-9]+)""").find(jsonString)
                ?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val previewHeight = Regex(""""preview_size"\s*:\s*\{[^}]*"height"\s*:\s*([0-9]+)""").find(jsonString)
                ?.groupValues?.get(1)?.toIntOrNull() ?: return null
            if (previewWidth <= 0 || previewHeight <= 0) return null

            // rotation check: must exist
            val rotation = Regex(""""rotation"\s*:\s*([0-9]+)""").find(jsonString)
                ?.groupValues?.get(1)?.toIntOrNull() ?: return null

            val photoWidth = Regex(""""photo_size"\s*:\s*\{[^}]*"width"\s*:\s*([0-9]+)""").find(jsonString)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val photoHeight = Regex(""""photo_size"\s*:\s*\{[^}]*"height"\s*:\s*([0-9]+)""").find(jsonString)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val timestamp = extractLong("timestamp_ms") ?: System.currentTimeMillis()

            ReticleCaptureMetadata(
                normalizedRect = NormalizedRect(left, top, right, bottom),
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                rotationDegrees = rotation,
                photoWidth = photoWidth,
                photoHeight = photoHeight,
                timestampMs = timestamp,
            )
        }.onFailure { err ->
            AppLog.e(TAG, "Failed to parse reticle JSON: ${err.message}", err)
        }.getOrNull()
    }

    /**
     * Checks if a companion sidecar JSON file exists for the given image file.
     */
    fun findCompanionJsonFile(imageFile: File): File? {
        val baseName = imageFile.nameWithoutExtension
        val parent = imageFile.parentFile ?: return null

        val candidate1 = File(parent, "${baseName}.json")
        if (candidate1.exists() && candidate1.length() > 0) return candidate1

        val candidate2 = File(parent, "${imageFile.name}.json")
        if (candidate2.exists() && candidate2.length() > 0) return candidate2

        return null
    }

    /**
     * Reads companion metadata for an image file if it exists.
     */
    fun readCompanionMetadata(imageFile: File): ReticleCaptureMetadata? {
        val jsonFile = findCompanionJsonFile(imageFile) ?: return null
        return runCatching {
            parseMetadata(jsonFile.readText())
        }.getOrNull()
    }
}
