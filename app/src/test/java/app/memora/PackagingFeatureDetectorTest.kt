package app.memora

import app.memora.domain.extraction.DateLocation
import app.memora.domain.extraction.DeterministicMemoryExtractor
import app.memora.domain.extraction.ExtractedMemoryCandidate
import app.memora.domain.extraction.LocalExtractionResult
import app.memora.domain.extraction.MarkingStyle
import app.memora.domain.extraction.MemoryExtractor
import app.memora.domain.extraction.OcrResult
import app.memora.domain.extraction.PackagingType
import app.memora.domain.model.CaptureSource
import app.memora.domain.model.CapturedImage
import app.memora.domain.usecase.ExtractLocallyUseCase
import app.memora.domain.usecase.RefineWithGeminiUseCase
import app.memora.platform.camera.PackagingFeatureDetector
import app.memora.platform.ocr.ExtractedText
import app.memora.platform.ocr.TextExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackagingFeatureDetectorTest {

    @Test
    fun classifyPackaging_crimpDetected_returnsTubeWithEmbossedNoInk() {
        val result = PackagingFeatureDetector.classifyPackaging(
            rawText = "",
            isCrimpPresent = true,
        )

        assertEquals(PackagingType.TUBE, result.packagingType)
        assertEquals(DateLocation.CRIMP_SEAL, result.dateLocation)
        assertEquals(MarkingStyle.EMBOSSED_NO_INK, result.markingStyle)
        assertTrue(result.isCrimpDetected)
        assertNotNull(result.guidanceMessage)
        assertTrue(result.guidanceMessage!!.contains("Crimp seal detected"))
    }

    @Test
    fun classifyPackaging_tubeKeywordsWithoutCrimp_returnsTube() {
        val result = PackagingFeatureDetector.classifyPackaging(
            rawText = "Antiseptic Skin Cream 50g",
            isCrimpPresent = false,
        )

        assertEquals(PackagingType.TUBE, result.packagingType)
        assertEquals(DateLocation.CRIMP_SEAL, result.dateLocation)
    }

    @Test
    fun classifyPackaging_canKeywords_returnsCanAndBottomRim() {
        val result = PackagingFeatureDetector.classifyPackaging(
            rawText = "Cold Soda Beverage Can 330ml",
            isCrimpPresent = false,
        )

        assertEquals(PackagingType.CAN, result.packagingType)
        assertEquals(DateLocation.CAN_BOTTOM, result.dateLocation)
        assertEquals(MarkingStyle.DOT_MATRIX, result.markingStyle)
        assertTrue(result.guidanceMessage!!.contains("bottom rim"))
    }

    @Test
    fun classifyPackaging_blisterKeywords_returnsBlisterPack() {
        val result = PackagingFeatureDetector.classifyPackaging(
            rawText = "Paracetamol Tablets 650mg strip",
            isCrimpPresent = false,
        )

        assertEquals(PackagingType.BLISTER_PACK, result.packagingType)
        assertEquals(DateLocation.CRIMP_SEAL, result.dateLocation)
        assertEquals(MarkingStyle.EMBOSSED_NO_INK, result.markingStyle)
        assertTrue(result.guidanceMessage!!.contains("foil margin"))
    }

    @Test
    fun classifyPackaging_bottleKeywords_returnsBottleAndNeck() {
        val result = PackagingFeatureDetector.classifyPackaging(
            rawText = "Shampoo bottle 400ml",
            isCrimpPresent = false,
        )

        assertEquals(PackagingType.BOTTLE, result.packagingType)
        assertEquals(DateLocation.BOTTLE_NECK, result.dateLocation)
        assertTrue(result.guidanceMessage!!.contains("neck or cap"))
    }

    @Test
    fun classifyPackaging_documentKeywords_returnsDocumentAndLabel() {
        val result = PackagingFeatureDetector.classifyPackaging(
            rawText = "Tax Invoice Bill Total Amount: ₹540",
            isCrimpPresent = false,
        )

        assertEquals(PackagingType.DOCUMENT, result.packagingType)
        assertEquals(DateLocation.LABEL, result.dateLocation)
        assertEquals(MarkingStyle.PRINTED, result.markingStyle)
    }

    @Test
    fun classifyPackaging_cartonBoxKeywords_returnsCartonBox() {
        val result = PackagingFeatureDetector.classifyPackaging(
            rawText = "Pack of 6 soap MRP 120",
            isCrimpPresent = false,
        )

        assertEquals(PackagingType.CARTON_BOX, result.packagingType)
        assertEquals(DateLocation.END_FLAP, result.dateLocation)
        assertEquals(MarkingStyle.PRINTED, result.markingStyle)
    }

    @Test
    fun classifyPackaging_unrecognized_returnsUnknown() {
        val result = PackagingFeatureDetector.classifyPackaging(
            rawText = "Lorem ipsum dolor sit amet",
            isCrimpPresent = false,
        )

        assertEquals(PackagingType.UNKNOWN, result.packagingType)
        assertEquals(DateLocation.UNKNOWN, result.dateLocation)
        assertEquals(MarkingStyle.UNKNOWN, result.markingStyle)
    }

    @Test
    fun extractLocallyUseCase_propagatesPackagingInformation() = runTest {
        val fakeOcr = object : TextExtractor {
            override suspend fun extractText(imageUri: String) =
                Result.success(ExtractedText("Face Wash Gel EXP 12/26"))
            override val isAvailable: Boolean = true
        }

        val useCase = ExtractLocallyUseCase(fakeOcr, DeterministicMemoryExtractor())
        val capturedImage = CapturedImage(
            uri = "file:///fake/path.jpg",
            source = CaptureSource.CAMERA,
        )

        val output = useCase(
            image = capturedImage,
            scanId = "scan-123",
            packagingType = PackagingType.TUBE,
            dateLocation = DateLocation.CRIMP_SEAL,
            markingStyle = MarkingStyle.EMBOSSED_NO_INK,
        ).getOrThrow()

        assertEquals(PackagingType.TUBE, output.localResult.packagingType)
        assertEquals(DateLocation.CRIMP_SEAL, output.localResult.dateLocation)
        assertEquals(MarkingStyle.EMBOSSED_NO_INK, output.localResult.markingStyle)
    }

    @Test
    fun refineWithGeminiUseCase_includesPackagingHintsInPrompt() = runTest {
        var recordedPrompt: String? = null
        val fakeExtractor = object : MemoryExtractor {
            override suspend fun extractCandidate(rawText: String, imageUri: android.net.Uri?): Result<ExtractedMemoryCandidate> {
                recordedPrompt = rawText
                return Result.success(ExtractedMemoryCandidate(suggestedTitle = "Ointment Tube"))
            }
        }

        val useCase = RefineWithGeminiUseCase(fakeExtractor)
        val capturedImage = CapturedImage(
            uri = "file:///fake/tube.jpg",
            source = CaptureSource.CAMERA,
        )

        val localResult = LocalExtractionResult(
            title = "Moisturizing Cream",
            eventType = null,
            selectedEventDate = null,
            dateType = null,
            dateCandidates = emptyList(),
            fields = emptyMap(),
            confidence = 0.5f,
            isAmbiguous = false,
            packagingType = PackagingType.TUBE,
            dateLocation = DateLocation.CRIMP_SEAL,
            markingStyle = MarkingStyle.EMBOSSED_NO_INK,
        )

        useCase(
            image = capturedImage,
            ocrResult = OcrResult("Moisturizing Cream"),
            localResult = localResult,
            scanId = "scan-crimp",
        )

        assertNotNull(recordedPrompt)
        assertTrue(recordedPrompt!!.contains("Packaging Type: Tube"))
        assertTrue(recordedPrompt!!.contains("Expected Date Location: Top Crimp Strip"))
        assertTrue(recordedPrompt!!.contains("Marking Technique: Embossed into plastic (no ink)"))
        assertTrue(recordedPrompt!!.contains("Heat-sealed tube crimp detected"))
        assertTrue(recordedPrompt!!.contains("Inspect the serrated/ribbed crimp texture closely"))
    }
}
