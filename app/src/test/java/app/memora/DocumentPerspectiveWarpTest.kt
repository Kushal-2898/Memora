package app.memora

import android.graphics.PointF
import app.memora.platform.camera.DeviceStabilityDetector
import app.memora.platform.cv.DocumentPerspectiveWarp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentPerspectiveWarpTest {

    @Test
    fun orderCorners_correctlySortsClockwiseFromTopLeft() {
        // Unordered corners of a skewed document
        val unordered = listOf(
            PointF(100f, 20f), // top-right
            PointF(90f, 95f),  // bottom-right
            PointF(15f, 25f),  // top-left
            PointF(20f, 90f),  // bottom-left
        )

        val ordered = DocumentPerspectiveWarp.orderCorners(unordered)

        assertEquals(4, ordered.size)
        // Top-left has smallest (x + y)
        assertEquals(PointF(15f, 25f), ordered[0])
        // Top-right has smallest (y - x)
        assertEquals(PointF(100f, 20f), ordered[1])
        // Bottom-right has largest (x + y)
        assertEquals(PointF(90f, 95f), ordered[2])
        // Bottom-left has largest (y - x)
        assertEquals(PointF(20f, 90f), ordered[3])
    }

    @Test
    fun computeWarpDimensions_calculatesMaxOrthogonalBounds() {
        val corners = listOf(
            PointF(0f, 0f),       // TL
            PointF(200f, 0f),     // TR
            PointF(190f, 100f),   // BR
            PointF(10f, 100f),    // BL
        )

        val dims = DocumentPerspectiveWarp.computeOutputDimensions(corners)

        // Width is max(dist(TL, TR), dist(BL, BR)) = max(200, 180) = 200
        assertEquals(200, dims.width)
        // Height is max(dist(TL, BL), dist(TR, BR)) = max(100.5, 100.5) ~ 100
        assertEquals(100, dims.height)
    }

    @Test
    fun deviceStabilityDetector_transitionsToStableWhenAccelerationVarianceIsLow() {
        val detector = DeviceStabilityDetector(windowSize = 5, varianceThreshold = 0.06f)

        // Initially not stable until enough readings arrive
        assertFalse(detector.isStable)

        // Feed steady gravity readings with minimal noise (stillness)
        repeat(10) {
            detector.onSensorReading(0.01f, 9.80f, 0.02f)
        }

        assertTrue(detector.isStable)
    }

    @Test
    fun deviceStabilityDetector_transitionsToUnstableUnderCameraShake() {
        val detector = DeviceStabilityDetector(windowSize = 5, varianceThreshold = 0.06f)

        // Feed steady readings first
        repeat(10) {
            detector.onSensorReading(0.0f, 9.81f, 0.0f)
        }
        assertTrue(detector.isStable)

        // Now simulate sudden hand jerk or walking motion
        detector.onSensorReading(2.5f, 11.2f, 4.1f)
        detector.onSensorReading(-1.8f, 7.5f, -2.0f)

        assertFalse(detector.isStable)
    }
}
