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

    @Test
    fun `test mapReticleToOrientedPhoto with standard portrait screen and portrait photo`() {
        val previewWidth = 1080
        val previewHeight = 2400
        val photoWidth = 3000
        val photoHeight = 4000
        val reticle = NormalizedRect(0.1f, 0.25f, 0.9f, 0.75f)

        val result = ReticleToPhotoCoordinateMapper.mapReticleToOrientedPhoto(
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            reticle = reticle,
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            rotationDegrees = 0,
            paddingPercent = 0.10f,
        )

        // Must match mapPreviewToOrientedNormalized with 3:4 aspect
        assertEquals(0.236f, result.left, eps)
        assertEquals(0.225f, result.top, eps)
        assertEquals(0.764f, result.right, eps)
        assertEquals(0.775f, result.bottom, eps)
    }

    @Test
    fun `test mapReticleToOrientedPhoto with native landscape screen and landscape photo`() {
        val previewWidth = 2400
        val previewHeight = 1080
        val photoWidth = 4000
        val photoHeight = 3000
        val reticle = NormalizedRect(0.2f, 0.1f, 0.8f, 0.9f)

        val result = ReticleToPhotoCoordinateMapper.mapReticleToOrientedPhoto(
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            reticle = reticle,
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            rotationDegrees = 0,
            paddingPercent = 0.10f,
        )

        // In landscape: previewAspect = 2400/1080 = 2.222, cameraAspect = 4000/3000 = 1.333
        // Stream width = 2400, scaledHeight = 2400 / (4/3) = 1800.
        // dy = (1800 - 1080) / 2 = 360.
        // screenLeft = 0.2 * 2400 = 480 => normLeft = 480 / 2400 = 0.2
        // screenRight = 0.8 * 2400 = 1920 => normRight = 1920 / 2400 = 0.8
        // screenTop = 0.1 * 1080 = 108 => streamTop = 108 + 360 = 468 => normTop = 468 / 1800 = 0.26
        // screenBottom = 0.9 * 1080 = 972 => streamBottom = 972 + 360 = 1332 => normBottom = 1332 / 1800 = 0.74
        // With 10% padding (5% each side):
        // width = 0.6, padX = 0.03 => left = 0.17, right = 0.83
        // height = 0.48, padY = 0.024 => top = 0.236, bottom = 0.764
        assertEquals(0.17f, result.left, eps)
        assertEquals(0.236f, result.top, eps)
        assertEquals(0.83f, result.right, eps)
        assertEquals(0.764f, result.bottom, eps)
    }

    @Test
    fun `test mapReticleToOrientedPhoto with locked portrait screen and rotated landscape photo 90 and 270 deg`() {
        val previewWidth = 1080
        val previewHeight = 2400
        val photoWidth = 4000
        val photoHeight = 3000
        val reticle = NormalizedRect(0.1f, 0.25f, 0.9f, 0.75f)

        // 90 degrees sensor rotation
        val rot90 = ReticleToPhotoCoordinateMapper.mapReticleToOrientedPhoto(
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            reticle = reticle,
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            rotationDegrees = 90,
            paddingPercent = 0.10f,
        )

        // Before rotation, streamAspect = 1 / (4/3) = 0.75.
        // Result is [0.236, 0.225, 0.764, 0.775]
        // Applying 90 deg rotation:
        // left = 1 - bottom = 1 - 0.775 = 0.225
        // top = left = 0.236
        // right = 1 - top = 1 - 0.225 = 0.775
        // bottom = right = 0.764
        assertEquals(0.225f, rot90.left, eps)
        assertEquals(0.236f, rot90.top, eps)
        assertEquals(0.775f, rot90.right, eps)
        assertEquals(0.764f, rot90.bottom, eps)

        // 270 degrees sensor rotation
        val rot270 = ReticleToPhotoCoordinateMapper.mapReticleToOrientedPhoto(
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            reticle = reticle,
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            rotationDegrees = 270,
            paddingPercent = 0.10f,
        )
        // Applying 270 deg rotation:
        // left = top = 0.225
        // top = 1 - right = 1 - 0.764 = 0.236
        // right = bottom = 0.775
        // bottom = 1 - left = 1 - 0.236 = 0.764
        assertEquals(0.225f, rot270.left, eps)
        assertEquals(0.236f, rot270.top, eps)
        assertEquals(0.775f, rot270.right, eps)
        assertEquals(0.764f, rot270.bottom, eps)
    }

    @Test
    fun `test mapReticleToOrientedPhoto with non-standard 16 to 9 widescreen photo`() {
        val previewWidth = 1080
        val previewHeight = 2400
        val photoWidth = 2160
        val photoHeight = 3840 // 9:16 portrait
        val reticle = NormalizedRect(0.1f, 0.2f, 0.9f, 0.8f)

        val result = ReticleToPhotoCoordinateMapper.mapReticleToOrientedPhoto(
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            reticle = reticle,
            photoWidth = photoWidth,
            photoHeight = photoHeight,
            rotationDegrees = 0,
            paddingPercent = 0f,
        )

        // previewAspect = 1080 / 2400 = 0.45. photoAspect = 2160 / 3840 = 0.5625.
        // previewAspect < photoAspect => scaledHeight = 2400, scaledWidth = 2400 * 0.5625 = 1350.
        // dx = (1350 - 1080) / 2 = 135. dy = 0.
        // screenLeft = 0.1 * 1080 = 108 => streamLeft = 108 + 135 = 243 => normLeft = 243 / 1350 = 0.18
        // screenRight = 0.9 * 1080 = 972 => streamRight = 972 + 135 = 1107 => normRight = 1107 / 1350 = 0.82
        // screenTop = 0.2 * 2400 = 480 => normTop = 480 / 2400 = 0.20
        // screenBottom = 0.8 * 2400 = 1920 => normBottom = 1920 / 2400 = 0.80
        assertEquals(0.18f, result.left, eps)
        assertEquals(0.20f, result.top, eps)
        assertEquals(0.82f, result.right, eps)
        assertEquals(0.80f, result.bottom, eps)
    }
}
