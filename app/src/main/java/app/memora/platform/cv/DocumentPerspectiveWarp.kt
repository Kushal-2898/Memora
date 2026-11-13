package app.memora.platform.cv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

data class OutputDimensions(val width: Int, val height: Int)

/**
 * 2D Computer Vision planar perspective deskewing and homography transformation.
 *
 * Straightens angled, tilted, or 45-degree skewed document photographs
 * into flat, high-density orthogonal rectangular bitmaps prior to OCR parsing.
 */
object DocumentPerspectiveWarp {

    /**
     * Orders 4 points clockwise starting from top-left:
     * [0] Top-Left
     * [1] Top-Right
     * [2] Bottom-Right
     * [3] Bottom-Left
     */
    fun orderCorners(corners: List<PointF>): List<PointF> {
        require(corners.size == 4) { "Exactly 4 corners required for perspective transformation" }

        // Top-left has minimum (x + y)
        val topLeft = corners.minByOrNull { it.x + it.y } ?: corners[0]

        // Bottom-right has maximum (x + y)
        val bottomRight = corners.maxByOrNull { it.x + it.y } ?: corners[2]

        val remaining = corners.filter { it !== topLeft && it !== bottomRight }

        // For remaining points:
        // Top-right has minimum (y - x)
        // Bottom-left has maximum (y - x)
        val topRight = remaining.minByOrNull { it.y - it.x } ?: remaining.first()
        val bottomLeft = remaining.maxByOrNull { it.y - it.x } ?: remaining.last()

        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    /**
     * Computes the bounding rectangular dimensions of the straightened output.
     */
    fun computeOutputDimensions(orderedCorners: List<PointF>): OutputDimensions {
        val tl = orderedCorners[0]
        val tr = orderedCorners[1]
        val br = orderedCorners[2]
        val bl = orderedCorners[3]

        val topWidth = hypot(tr.x - tl.x, tr.y - tl.y)
        val bottomWidth = hypot(br.x - bl.x, br.y - bl.y)
        val width = max(topWidth, bottomWidth).roundToInt().coerceAtLeast(1)

        val leftHeight = hypot(bl.x - tl.x, bl.y - tl.y)
        val rightHeight = hypot(br.x - tr.x, br.y - tr.y)
        val height = max(leftHeight, rightHeight).roundToInt().coerceAtLeast(1)

        return OutputDimensions(width = width, height = height)
    }

    /**
     * Performs planar homography warp on [source] using the 4 ordered quadrilateral corners.
     */
    fun warpPerspective(source: Bitmap, corners: List<PointF>): Bitmap? {
        if (source.isRecycled || corners.size != 4) return null

        return runCatching {
            val ordered = orderCorners(corners)
            val dims = computeOutputDimensions(ordered)

            val srcPoints = floatArrayOf(
                ordered[0].x, ordered[0].y, // TL
                ordered[1].x, ordered[1].y, // TR
                ordered[2].x, ordered[2].y, // BR
                ordered[3].x, ordered[3].y  // BL
            )

            val dstPoints = floatArrayOf(
                0f, 0f,
                dims.width.toFloat(), 0f,
                dims.width.toFloat(), dims.height.toFloat(),
                0f, dims.height.toFloat()
            )

            val matrix = Matrix()
            val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)
            if (!success) return null

            val output = Bitmap.createBitmap(dims.width, dims.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(source, matrix, paint)
            output
        }.getOrNull()
    }
}
