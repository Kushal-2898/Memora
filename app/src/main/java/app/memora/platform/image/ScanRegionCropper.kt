package app.memora.platform.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import app.memora.platform.logging.AppLog
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Region decoder that extracts and normalizes the viewfinder reticle region
 * from high-resolution captured photos.
 *
 * Implements high-fidelity OCR cropping:
 * - Decodes image downscaled to keep long-side <= 3840 px.
 * - Corrects EXIF rotation right-side up.
 * - Crops the central 88% width x 82% height viewfinder reticle area where the subject is centered.
 * - Floors the shorter side to >= 1000 px to give OCR engines high pixel density for dot-matrix/embossed fonts.
 */
object ScanRegionCropper {

    private const val MAX_LONG_SIDE_PX = 3840
    private const val MIN_SHORT_SIDE_PX = 1000

    fun cropReticleRegion(imagePath: String): Bitmap? {
        val file = File(imagePath)
        if (!file.exists() || file.length() == 0L) return null

        return runCatching {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imagePath, boundsOptions)
            val fullWidth = boundsOptions.outWidth
            val fullHeight = boundsOptions.outHeight
            if (fullWidth <= 0 || fullHeight <= 0) return null

            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            // Calculate sample size to keep long side under MAX_LONG_SIDE_PX
            var sampleSize = 1
            while (max(fullWidth / sampleSize, fullHeight / sampleSize) > MAX_LONG_SIDE_PX) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = BitmapFactory.decodeFile(imagePath, decodeOptions) ?: return null

            // 1. Apply EXIF rotation right-side up
            val oriented = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated != decoded) decoded.recycle()
                rotated
            } else {
                decoded
            }

            // 2. Crop central reticle region (88% width, 82% height)
            val cropWidth = (oriented.width * 0.88f).toInt()
            val cropHeight = (oriented.height * 0.82f).toInt()
            val cropLeft = ((oriented.width - cropWidth) / 2).coerceAtLeast(0)
            val cropTop = ((oriented.height - cropHeight) / 2).coerceAtLeast(0)

            val reticleCrop = Bitmap.createBitmap(oriented, cropLeft, cropTop, cropWidth, cropHeight)
            if (reticleCrop != oriented) {
                oriented.recycle()
            }

            // 3. Floor shorter side to at least MIN_SHORT_SIDE_PX for dot-matrix pixel density
            val currentShortSide = min(reticleCrop.width, reticleCrop.height)
            if (currentShortSide in 1 until MIN_SHORT_SIDE_PX) {
                val scale = MIN_SHORT_SIDE_PX.toFloat() / currentShortSide
                val targetW = (reticleCrop.width * scale).toInt()
                val targetH = (reticleCrop.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(reticleCrop, targetW, targetH, true)
                if (scaled != reticleCrop) reticleCrop.recycle()
                scaled
            } else {
                reticleCrop
            }
        }.onFailure { err ->
            AppLog.e("ScanRegionCropper", "Failed to crop reticle region: ${err.message}", err)
        }.getOrNull()
    }
}
