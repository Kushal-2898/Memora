package app.memora.platform.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * Image preprocessing engine generating multi-exposure/contrast variants for OCR.
 *
 * Implements Phase 2:
 * 1. [Variant.ORIGINAL]: Raw cropped bitmap (zero extra allocations).
 * 2. [Variant.CONTRAST_ENHANCED]: Grayscale conversion with 1.4x contrast stretch to reveal faint ink/stamps.
 * 3. [Variant.INVERTED]: Color inversion to invert white-on-silver/white-on-dark packaging (cans, dark bottles).
 *
 * Uses hardware-accelerated [ColorMatrixColorFilter] via [Canvas] to avoid heavy JVM heap allocations.
 */
object ImagePreprocessor {

    enum class VariantType {
        ORIGINAL,
        CONTRAST_ENHANCED,
        INVERTED,
        DOT_MATRIX,
        GLARE_SEPARATED
    }

    /**
     * Creates a contrast-enhanced grayscale variant of [source].
     * Caller is responsible for recycling the returned bitmap when finished.
     */
    fun createContrastEnhancedVariant(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()

        // 1. Grayscale
        val grayMatrix = ColorMatrix().apply { setSaturation(0f) }

        // 2. High contrast stretch: scale 1.4x, shifted around midpoint 128
        val contrast = 1.4f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )

        contrastMatrix.preConcat(grayMatrix)
        paint.colorFilter = ColorMatrixColorFilter(contrastMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return output
    }

    /**
     * Creates an inverted color variant of [source] (black-on-white conversion for reflective surfaces).
     * Caller is responsible for recycling the returned bitmap when finished.
     */
    fun createInvertedVariant(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()

        // Invert RGB channels and convert to high-contrast monochrome
        val invertMatrix = ColorMatrix(
            floatArrayOf(
                -1.3f, 0f, 0f, 0f, 255f,
                0f, -1.3f, 0f, 0f, 255f,
                0f, 0f, -1.3f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val grayMatrix = ColorMatrix().apply { setSaturation(0f) }
        invertMatrix.preConcat(grayMatrix)

        paint.colorFilter = ColorMatrixColorFilter(invertMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return output
    }

    /**
     * Creates a dot-matrix stroke-connected variant of [source].
     * Blends adjacent micro-dots into continuous character strokes via bilinear down-sampling/up-sampling
     * and high-contrast thresholding.
     * Caller is responsible for recycling the returned bitmap when finished.
     */
    fun createDotMatrixConnectedVariant(source: Bitmap): Bitmap {
        val scale = 0.7f
        val scaledWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val downscaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)

        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        val contrast = 2.2f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val grayMatrix = ColorMatrix().apply { setSaturation(0f) }
        contrastMatrix.preConcat(grayMatrix)
        paint.colorFilter = ColorMatrixColorFilter(contrastMatrix)

        canvas.drawBitmap(downscaled, null, android.graphics.Rect(0, 0, source.width, source.height), paint)
        if (downscaled != source) {
            downscaled.recycle()
        }

        return output
    }

    /**
     * Creates a channel/glare-separated variant of [source].
     * Isolates high-SNR luminance (green channel) and normalizes local contrast to make faint white ink
     * and debossed text legible on dark or reflective surfaces.
     * Caller is responsible for recycling the returned bitmap when finished.
     */
    fun createGlareSeparatedVariant(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()

        // Green-dominant high-contrast luminance matrix with specular glare suppression
        val glareMatrix = ColorMatrix(
            floatArrayOf(
                0.1f, 1.6f, 0.1f, 0f, -30f,
                0.1f, 1.6f, 0.1f, 0f, -30f,
                0.1f, 1.6f, 0.1f, 0f, -30f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(glareMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return output
    }
}
