package app.memora

import app.memora.platform.image.DebugCaptureSaver
import app.memora.platform.image.NormalizedRect
import app.memora.platform.image.ReticleCaptureMetadata
import app.memora.platform.image.ReticleToPhotoCoordinateMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlinx.coroutines.runBlocking

class ReticleCoordinateMapperTest {

    private val eps = 0.0001f

    @Test
    fun `test fallbackCentralCrop gives 88 percent width and 82 percent height centered`() {
        val fallback = ReticleToPhotoCoordinateMapper.fallbackCentralCrop()
        assertEquals(0.06f, fallback.left, eps)
        assertEquals(0.09f, fallback.top, eps)
        assertEquals(0.94f, fallback.right, eps)
        assertEquals(0.91f, fallback.bottom, eps)
        assertEquals(0.88f, fallback.width, eps)
        assertEquals(0.82f, fallback.height, eps)
    }

    @Test
    fun `test invalid preview dimensions triggers fallback crop`() {
        val zeroWidth = ReticleToPhotoCoordinateMapper.mapPreviewToOrientedNormalized(
            previewWidth = 0,
            previewHeight = 1920,
            reticle = NormalizedRect(0.1f, 0.2f, 0.9f, 0.8f),
        )
        assertEquals(ReticleToPhotoCoordinateMapper.fallbackCentralCrop(), zeroWidth)

        val negativeHeight = ReticleToPhotoCoordinateMapper.mapPreviewToOrientedNormalized(
            previewWidth = 1080,
            previewHeight = -1,
            reticle = NormalizedRect(0.1f, 0.2f, 0.9f, 0.8f),
        )
        assertEquals(ReticleToPhotoCoordinateMapper.fallbackCentralCrop(), negativeHeight)
    }

    @Test
    fun `test rotation 0 degrees is identity`() {
        val rect = NormalizedRect(0.1f, 0.2f, 0.8f, 0.7f)
        val raw = ReticleToPhotoCoordinateMapper.mapOrientedToRawSensor(rect, 0)
        assertEquals(rect.left, raw.left, eps)
        assertEquals(rect.top, raw.top, eps)
        assertEquals(rect.right, raw.right, eps)
        assertEquals(rect.bottom, raw.bottom, eps)

        val roundTrip = ReticleToPhotoCoordinateMapper.mapRawSensorToOriented(raw, 0)
        assertEquals(rect.left, roundTrip.left, eps)
        assertEquals(rect.top, roundTrip.top, eps)
        assertEquals(rect.right, roundTrip.right, eps)
        assertEquals(rect.bottom, roundTrip.bottom, eps)
    }

    @Test
    fun `test rotation 90 degrees round trip and coordinate transform`() {
        val oriented = NormalizedRect(0.1f, 0.2f, 0.8f, 0.7f)
        val raw = ReticleToPhotoCoordinateMapper.mapOrientedToRawSensor(oriented, 90)

        // oriented_x = 1 - raw_y, oriented_y = raw_x
        // raw_x = oriented_y, raw_y = 1 - oriented_x
        assertEquals(oriented.top, raw.left, eps)
        assertEquals(1f - oriented.right, raw.top, eps)
        assertEquals(oriented.bottom, raw.right, eps)
        assertEquals(1f - oriented.left, raw.bottom, eps)

        val roundTrip = ReticleToPhotoCoordinateMapper.mapRawSensorToOriented(raw, 90)
        assertEquals(oriented.left, roundTrip.left, eps)
        assertEquals(oriented.top, roundTrip.top, eps)
        assertEquals(oriented.right, roundTrip.right, eps)
        assertEquals(oriented.bottom, roundTrip.bottom, eps)
    }

    @Test
    fun `test rotation 180 degrees round trip and coordinate transform`() {
        val oriented = NormalizedRect(0.1f, 0.2f, 0.8f, 0.7f)
        val raw = ReticleToPhotoCoordinateMapper.mapOrientedToRawSensor(oriented, 180)

        assertEquals(1f - oriented.right, raw.left, eps)
        assertEquals(1f - oriented.bottom, raw.top, eps)
        assertEquals(1f - oriented.left, raw.right, eps)
        assertEquals(1f - oriented.top, raw.bottom, eps)

        val roundTrip = ReticleToPhotoCoordinateMapper.mapRawSensorToOriented(raw, 180)
        assertEquals(oriented.left, roundTrip.left, eps)
        assertEquals(oriented.top, roundTrip.top, eps)
        assertEquals(oriented.right, roundTrip.right, eps)
        assertEquals(oriented.bottom, roundTrip.bottom, eps)
    }

    @Test
    fun `test rotation 270 degrees round trip and coordinate transform`() {
        val oriented = NormalizedRect(0.1f, 0.2f, 0.8f, 0.7f)
        val raw = ReticleToPhotoCoordinateMapper.mapOrientedToRawSensor(oriented, 270)

        // raw_x = 1 - oriented_y, raw_y = oriented_x
        assertEquals(1f - oriented.bottom, raw.left, eps)
        assertEquals(oriented.left, raw.top, eps)
        assertEquals(1f - oriented.top, raw.right, eps)
        assertEquals(oriented.right, raw.bottom, eps)

        val roundTrip = ReticleToPhotoCoordinateMapper.mapRawSensorToOriented(raw, 270)
        assertEquals(oriented.left, roundTrip.left, eps)
        assertEquals(oriented.top, roundTrip.top, eps)
        assertEquals(oriented.right, roundTrip.right, eps)
        assertEquals(oriented.bottom, roundTrip.bottom, eps)
    }

    @Test
    fun `test preview fill-center mapping on tall portrait screen with 10 percent padding`() {
        // Typical modern tall smartphone: 1080 x 2400 (aspect 0.45), camera stream 3:4 portrait (0.75)
        // Stream height matches 2400, stream width = 2400 * 0.75 = 1800.
        // Screen width 1080 is center-cropped: dx = (1800 - 1080) / 2 = 360.
        val previewWidth = 1080
        val previewHeight = 2400
        val reticle = NormalizedRect(0.1f, 0.25f, 0.9f, 0.75f)

        // Without padding:
        val noPadding = ReticleToPhotoCoordinateMapper.mapPreviewToOrientedNormalized(
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            reticle = reticle,
            paddingPercent = 0f,
        )

        // screenLeft = 0.1 * 1080 = 108, streamLeft = 108 + 360 = 468. normLeft = 468 / 1800 = 0.26
        // screenRight = 0.9 * 1080 = 972, streamRight = 972 + 360 = 1332. normRight = 1332 / 1800 = 0.74
        // screenTop = 0.25 * 2400 = 600. streamTop = 600. normTop = 600 / 2400 = 0.25
        // screenBottom = 0.75 * 2400 = 1800. streamBottom = 1800. normBottom = 1800 / 2400 = 0.75
        assertEquals(0.26f, noPadding.left, eps)
        assertEquals(0.25f, noPadding.top, eps)
        assertEquals(0.74f, noPadding.right, eps)
        assertEquals(0.75f, noPadding.bottom, eps)

        // With 10% padding (5% each side):
        // width = 0.74 - 0.26 = 0.48. padX = 0.48 * 0.05 = 0.024 => left = 0.236, right = 0.764
        // height = 0.75 - 0.25 = 0.50. padY = 0.50 * 0.05 = 0.025 => top = 0.225, bottom = 0.775
        val withPadding = ReticleToPhotoCoordinateMapper.mapPreviewToOrientedNormalized(
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            reticle = reticle,
            paddingPercent = 0.10f,
        )
        assertEquals(0.236f, withPadding.left, eps)
        assertEquals(0.225f, withPadding.top, eps)
        assertEquals(0.764f, withPadding.right, eps)
        assertEquals(0.775f, withPadding.bottom, eps)
    }

    @Test
    fun `test debug capture saver serialization and deserialization`() {
        val metadata = ReticleCaptureMetadata(
            normalizedRect = NormalizedRect(0.08f, 0.22f, 0.92f, 0.78f),
            previewWidth = 1080,
            previewHeight = 2400,
            rotationDegrees = 90,
            photoWidth = 3072,
            photoHeight = 4096,
            timestampMs = 1729419000000L,
        )

        val jsonString = DebugCaptureSaver.serializeMetadata(metadata)
        val deserialized = DebugCaptureSaver.parseMetadata(jsonString)

        assertNotNull(deserialized)
        assertEquals(metadata.normalizedRect.left, deserialized!!.normalizedRect.left, eps)
        assertEquals(metadata.normalizedRect.top, deserialized.normalizedRect.top, eps)
        assertEquals(metadata.normalizedRect.right, deserialized.normalizedRect.right, eps)
        assertEquals(metadata.normalizedRect.bottom, deserialized.normalizedRect.bottom, eps)
        assertEquals(metadata.previewWidth, deserialized.previewWidth)
        assertEquals(metadata.previewHeight, deserialized.previewHeight)
        assertEquals(metadata.rotationDegrees, deserialized.rotationDegrees)
        assertEquals(metadata.photoWidth, deserialized.photoWidth)
        assertEquals(metadata.photoHeight, deserialized.photoHeight)
        assertEquals(metadata.timestampMs, deserialized.timestampMs)
    }

    @Test
    fun `test sidecar rejection on missing required fields`() {
        val missingRotation = """
        {
          "reticle_rect": { "left": 0.1, "top": 0.2, "right": 0.8, "bottom": 0.7 },
          "preview_size": { "width": 1080, "height": 2400 }
        }
        """.trimIndent()
        org.junit.Assert.assertNull(DebugCaptureSaver.parseMetadata(missingRotation))

        val missingPreviewSize = """
        {
          "reticle_rect": { "left": 0.1, "top": 0.2, "right": 0.8, "bottom": 0.7 },
          "rotation": 0
        }
        """.trimIndent()
        org.junit.Assert.assertNull(DebugCaptureSaver.parseMetadata(missingPreviewSize))

        val missingReticleRect = """
        {
          "preview_size": { "width": 1080, "height": 2400 },
          "rotation": 0
        }
        """.trimIndent()
        org.junit.Assert.assertNull(DebugCaptureSaver.parseMetadata(missingReticleRect))
    }

    @Test
    fun `test saveCaptureToDir saves photo and companion sidecar on IO dispatcher`() = runBlocking {
        val tempDir = java.nio.file.Files.createTempDirectory("debug_captures_test").toFile()
        val sourceDir = java.nio.file.Files.createTempDirectory("source_photos_test").toFile()
        try {
            val sourceFile = File(sourceDir, "scan_test_123.jpg").apply {
                writeBytes(byteArrayOf(10, 20, 30, 40, 50))
            }
            val metadata = ReticleCaptureMetadata(
                normalizedRect = NormalizedRect(0.12f, 0.22f, 0.88f, 0.78f),
                previewWidth = 1080,
                previewHeight = 2400,
                rotationDegrees = 90,
                photoWidth = 3000,
                photoHeight = 4000,
            )

            val savedJson = DebugCaptureSaver.saveCaptureToDir(tempDir, sourceFile, metadata)

            assertNotNull("Saved JSON must not be null", savedJson)
            assertTrue("Saved JSON must exist", savedJson!!.exists())

            val targetPhoto = File(tempDir, "scan_test_123.jpg")
            assertTrue("Copied photo must exist in destination dir", targetPhoto.exists())
            assertEquals("Copied photo content length must match", 5L, targetPhoto.length())

            val companionJson = File(sourceDir, "scan_test_123.json")
            assertTrue("Companion JSON next to source photo must exist", companionJson.exists())

            val parsed = DebugCaptureSaver.parseMetadata(savedJson.readText())
            assertNotNull("Written JSON must parse back into metadata", parsed)
            assertEquals(1080, parsed!!.previewWidth)
            assertEquals(2400, parsed.previewHeight)
            assertEquals(90, parsed.rotationDegrees)
        } finally {
            tempDir.deleteRecursively()
            sourceDir.deleteRecursively()
        }
    }
}
