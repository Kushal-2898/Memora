package app.memora.platform.camera

import kotlin.math.sqrt

/**
 * On-device motion stillness gate.
 *
 * Tracks linear acceleration and gravity vectors to ensure that camera captures
 * are triggered only when the device is stable, preventing motion blur and degraded OCR.
 */
class DeviceStabilityDetector(
    private val windowSize: Int = 5,
    private val varianceThreshold: Float = 0.06f,
) {

    private val magnitudeHistory = ArrayDeque<Float>(windowSize)

    var isStable: Boolean = false
        private set

    fun onSensorReading(x: Float, y: Float, z: Float) {
        val magnitude = sqrt(x * x + y * y + z * z)

        if (magnitudeHistory.size >= windowSize) {
            magnitudeHistory.removeFirst()
        }
        magnitudeHistory.addLast(magnitude)

        if (magnitudeHistory.size >= windowSize) {
            val mean = magnitudeHistory.sum() / magnitudeHistory.size
            var sumSquaredDiff = 0f
            for (m in magnitudeHistory) {
                val diff = m - mean
                sumSquaredDiff += diff * diff
            }
            val variance = sumSquaredDiff / magnitudeHistory.size
            isStable = variance < varianceThreshold
        } else {
            isStable = false
        }
    }

    fun reset() {
        magnitudeHistory.clear()
        isStable = false
    }
}
