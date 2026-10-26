package app.memora.platform.image

import kotlin.math.max
import kotlin.math.min

/**
 * Normalized 0..1 bounding box coordinates.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    fun clamp(): NormalizedRect {
        return NormalizedRect(
            left = left.coerceIn(0f, 1f),
            top = top.coerceIn(0f, 1f),
            right = right.coerceIn(0f, 1f),
            bottom = bottom.coerceIn(0f, 1f),
        )
    }
}

/**
 * Metadata recorded during camera capture representing the on-screen reticle,
 * viewfinder dimensions, and camera sensor rotation.
 */
data class ReticleCaptureMetadata(
    val normalizedRect: NormalizedRect,
    val previewWidth: Int,
    val previewHeight: Int,
    val rotationDegrees: Int, // 0, 90, 180, 270
    val photoWidth: Int = 0,
    val photoHeight: Int = 0,
    val timestampMs: Long = System.currentTimeMillis(),
)

/**
 * Mathematical coordinate mapper converting viewfinder reticle screen coordinates
 * into precise photo crop pixels and normalized bounding boxes.
 *
 * Handles:
 * 1. CameraX PreviewView.ScaleType.FILL_CENTER geometry (centering offsets & aspect scaling).
 * 2. 4:3 camera capture aspect ratio (3:4 portrait).
 * 3. Sensor orientation / EXIF rotations (0, 90, 180, 270).
 * 4. 10% padding (configurable) to prevent clipping text near reticle edges.
 * 5. Safe fallback to default fixed central crop if coordinates are invalid.
 */
object ReticleToPhotoCoordinateMapper {

    const val DEFAULT_PADDING_PERCENT = 0.10f
    const val DEFAULT_CAMERA_ASPECT_PORTRAIT = 3f / 4f // 0.75

    /**
     * Maps the normalized reticle coordinates (in PreviewView space, 0..1)
     * to normalized coordinates in the upright/oriented photo (0..1).
     *
     * Computes the camera stream's effective aspect ratio dynamically from the photo dimensions,
     * and applies rotation-aware transforms if the screen preview and the photo have opposite
     * primary orientations (e.g. device held in landscape with rotation lock ON).
     */
    fun mapReticleToOrientedPhoto(
        previewWidth: Int,
        previewHeight: Int,
        reticle: NormalizedRect,
        photoWidth: Int,
        photoHeight: Int,
        rotationDegrees: Int = 0,
        paddingPercent: Float = DEFAULT_PADDING_PERCENT,
    ): NormalizedRect {
        if (previewWidth <= 0 || previewHeight <= 0 || photoWidth <= 0 || photoHeight <= 0) {
            return fallbackCentralCrop()
        }

        val photoAspect = photoWidth.toFloat() / photoHeight.toFloat()
        val previewIsLandscape = previewWidth > previewHeight
        val photoIsLandscape = photoWidth > photoHeight

        return if (previewIsLandscape == photoIsLandscape) {
            // Both preview and photo share the same primary orientation
            mapPreviewToOrientedNormalized(
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                reticle = reticle,
                cameraAspect = photoAspect,
                paddingPercent = paddingPercent,
            )
        } else {
            // Screen preview and photo have opposite orientations
            val streamAspectInPreview = 1f / photoAspect
            val mappedInStream = mapPreviewToOrientedNormalized(
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                reticle = reticle,
                cameraAspect = streamAspectInPreview,
                paddingPercent = paddingPercent,
            )
            mapRawSensorToOriented(mappedInStream, rotationDegrees)
        }
    }

    /**
     * Maps the normalized reticle coordinates (in PreviewView space, 0..1)
     * to normalized coordinates in the upright/oriented photo (0..1).
     */
    fun mapPreviewToOrientedNormalized(
        previewWidth: Int,
        previewHeight: Int,
        reticle: NormalizedRect,
        cameraAspect: Float = DEFAULT_CAMERA_ASPECT_PORTRAIT,
        paddingPercent: Float = DEFAULT_PADDING_PERCENT,
    ): NormalizedRect {
        if (previewWidth <= 0 || previewHeight <= 0) {
            // Safe fallback: 88% width x 82% height centered
            return fallbackCentralCrop()
        }

        val previewAspect = previewWidth.toFloat() / previewHeight.toFloat()
        val scaledWidth: Float
        val scaledHeight: Float
        val dx: Float
        val dy: Float

        if (previewAspect < cameraAspect) {
            // Screen is taller/narrower than camera stream (standard portrait smartphone)
            // Stream height fills preview height, horizontal edges overflow and are center-cropped.
            scaledHeight = previewHeight.toFloat()
            scaledWidth = scaledHeight * cameraAspect
            dx = (scaledWidth - previewWidth.toFloat()) / 2f
            dy = 0f
        } else {
            // Screen is wider than camera stream
            // Stream width fills preview width, vertical edges overflow and are center-cropped.
            scaledWidth = previewWidth.toFloat()
            scaledHeight = scaledWidth / cameraAspect
            dx = 0f
            dy = (scaledHeight - previewHeight.toFloat()) / 2f
        }

        // Screen reticle pixel bounds
        val screenLeft = reticle.left * previewWidth
        val screenTop = reticle.top * previewHeight
        val screenRight = reticle.right * previewWidth
        val screenBottom = reticle.bottom * previewHeight

        // Map screen pixels to uncropped camera stream coordinates (0..scaledWidth, 0..scaledHeight)
        val streamLeft = screenLeft + dx
        val streamTop = screenTop + dy
        val streamRight = screenRight + dx
        val streamBottom = screenBottom + dy

        // Normalize to 0..1 on the camera stream (upright oriented photo space)
        var normLeft = (streamLeft / scaledWidth).coerceIn(0f, 1f)
        var normTop = (streamTop / scaledHeight).coerceIn(0f, 1f)
        var normRight = (streamRight / scaledWidth).coerceIn(0f, 1f)
        var normBottom = (streamBottom / scaledHeight).coerceIn(0f, 1f)

        // Apply 10% padding (5% on each side)
        if (paddingPercent > 0f) {
            val width = normRight - normLeft
            val height = normBottom - normTop
            val padX = width * (paddingPercent / 2f)
            val padY = height * (paddingPercent / 2f)
            normLeft = (normLeft - padX).coerceAtLeast(0f)
            normRight = (normRight + padX).coerceAtMost(1f)
            normTop = (normTop - padY).coerceAtLeast(0f)
            normBottom = (normBottom + padY).coerceAtMost(1f)
        }

        return NormalizedRect(normLeft, normTop, normRight, normBottom)
    }

    /**
     * Maps an oriented upright normalized rect to raw unrotated sensor coordinates (0..1)
     * based on sensor rotation degrees (0, 90, 180, 270).
     */
    fun mapOrientedToRawSensor(
        oriented: NormalizedRect,
        rotationDegrees: Int,
    ): NormalizedRect {
        return when ((rotationDegrees % 360 + 360) % 360) {
            90 -> {
                // 90 deg clockwise rotation (landscape sensor rotated to portrait):
                // oriented_x = 1 - raw_y, oriented_y = raw_x
                // => raw_x = oriented_y, raw_y = 1 - oriented_x
                NormalizedRect(
                    left = oriented.top,
                    top = 1f - oriented.right,
                    right = oriented.bottom,
                    bottom = 1f - oriented.left,
                )
            }
            180 -> {
                NormalizedRect(
                    left = 1f - oriented.right,
                    top = 1f - oriented.bottom,
                    right = 1f - oriented.left,
                    bottom = 1f - oriented.top,
                )
            }
            270 -> {
                // 270 deg clockwise rotation:
                // oriented_x = raw_y, oriented_y = 1 - raw_x
                // => raw_x = 1 - oriented_y, raw_y = oriented_x
                NormalizedRect(
                    left = 1f - oriented.bottom,
                    top = oriented.left,
                    right = 1f - oriented.top,
                    bottom = oriented.right,
                )
            }
            else -> oriented
        }
    }

    /**
     * Maps raw unrotated sensor coordinates (0..1) to oriented upright coordinates (0..1)
     * based on sensor rotation degrees (0, 90, 180, 270).
     */
    fun mapRawSensorToOriented(
        raw: NormalizedRect,
        rotationDegrees: Int,
    ): NormalizedRect {
        return when ((rotationDegrees % 360 + 360) % 360) {
            90 -> {
                NormalizedRect(
                    left = 1f - raw.bottom,
                    top = raw.left,
                    right = 1f - raw.top,
                    bottom = raw.right,
                )
            }
            180 -> {
                NormalizedRect(
                    left = 1f - raw.right,
                    top = 1f - raw.bottom,
                    right = 1f - raw.left,
                    bottom = 1f - raw.top,
                )
            }
            270 -> {
                NormalizedRect(
                    left = raw.top,
                    top = 1f - raw.right,
                    right = raw.bottom,
                    bottom = 1f - raw.left,
                )
            }
            else -> raw
        }
    }

    /**
     * Fallback central crop: 88% width x 82% height centered.
     */
    fun fallbackCentralCrop(): NormalizedRect {
        val width = 0.88f
        val height = 0.82f
        val left = (1f - width) / 2f
        val top = (1f - height) / 2f
        return NormalizedRect(left, top, left + width, top + height)
    }
}
