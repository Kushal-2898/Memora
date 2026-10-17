package app.memora

import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Modifier

class CapturedImageDomainBoundaryTest {

    @Test
    fun `captured image domain model does not depend on android platform classes`() {
        val capturedImageClass = CapturedImage::class.java

        // Verify fields are pure standard JVM types (String, Enum, Instant, Long, Int, etc.)
        capturedImageClass.declaredFields.forEach { field ->
            val fieldType = field.type.name
            assertFalse(
                "Domain model must not reference Android Bitmap: $fieldType",
                fieldType.contains("android.graphics.Bitmap")
            )
            assertFalse(
                "Domain model must not reference Android Activity: $fieldType",
                fieldType.contains("android.app.Activity")
            )
            assertFalse(
                "Domain model must not reference Android View: $fieldType",
                fieldType.contains("android.view.View")
            )
            assertFalse(
                "Domain model must not reference CameraX: $fieldType",
                fieldType.contains("androidx.camera")
            )
            assertFalse(
                "Domain model must not reference Compose UI: $fieldType",
                fieldType.contains("androidx.compose")
            )
        }
    }

    @Test
    fun `all capture sources produce identical domain representation structure`() {
        val cameraImage = CapturedImage(
            uri = "file:///data/user/0/app.memora/files/captures/photo_1.jpg",
            source = CaptureSource.CAMERA,
            mimeType = "image/jpeg",
            fileSize = 1024L,
        )

        val galleryImage = CapturedImage(
            uri = "file:///data/user/0/app.memora/files/captures/image_2.jpg",
            source = CaptureSource.GALLERY,
            mimeType = "image/png",
            fileSize = 2048L,
        )

        val screenshotImage = CapturedImage(
            uri = "file:///data/user/0/app.memora/files/captures/screenshot_3.jpg",
            source = CaptureSource.SCREENSHOT,
            mimeType = "image/png",
            fileSize = 3072L,
        )

        assertEquals(CaptureSource.CAMERA, cameraImage.source)
        assertEquals(CaptureSource.GALLERY, galleryImage.source)
        assertEquals(CaptureSource.SCREENSHOT, screenshotImage.source)

        assertNotNull(cameraImage.uri)
        assertNotNull(galleryImage.uri)
        assertNotNull(screenshotImage.uri)
    }

    @Test
    fun `database entities do not contain any image fields or references`() {
        val memoryEntityClass = app.memora.data.local.MemoryEntity::class.java
        val reminderEntityClass = app.memora.data.local.ReminderEntity::class.java

        val forbiddenFieldNames = listOf("image", "imageUri", "imagePath", "bitmap", "imageBytes", "photo", "picture")

        memoryEntityClass.declaredFields.forEach { field ->
            val nameLower = field.name.lowercase()
            forbiddenFieldNames.forEach { forbidden ->
                assertFalse(
                    "MemoryEntity must not contain image field '$forbidden': found ${field.name}",
                    nameLower.contains(forbidden)
                )
            }
        }

        reminderEntityClass.declaredFields.forEach { field ->
            val nameLower = field.name.lowercase()
            forbiddenFieldNames.forEach { forbidden ->
                assertFalse(
                    "ReminderEntity must not contain image field '$forbidden': found ${field.name}",
                    nameLower.contains(forbidden)
                )
            }
        }
    }
}
