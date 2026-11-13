package app.memora

import android.graphics.Rect
import app.memora.domain.extraction.SpatialExtractedEntity
import app.memora.domain.extraction.SpatialTokenGraphExtractor
import app.memora.domain.model.EventType
import app.memora.platform.ocr.TextBlockInfo
import app.memora.platform.ocr.TextElementInfo
import app.memora.platform.ocr.TextLineInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class SpatialTokenGraphExtractorTest {

    private val extractor = SpatialTokenGraphExtractor()

    @Test
    fun inferTitle_selectsLargestTextInUpperReticleZone() {
        val blocks = listOf(
            // Large brand title at top
            TextBlockInfo(
                text = "ORGANIC ROLLED OATS",
                boundingBox = Rect(100, 80, 500, 160), // height 80px, top 80
                lines = listOf(
                    TextLineInfo(
                        text = "ORGANIC ROLLED OATS",
                        boundingBox = Rect(100, 80, 500, 160)
                    )
                )
            ),
            // Subtitle / category
            TextBlockInfo(
                text = "Gluten Free Whole Grain 500g",
                boundingBox = Rect(120, 180, 480, 220), // height 40px, top 180
                lines = listOf(
                    TextLineInfo(
                        text = "Gluten Free Whole Grain 500g",
                        boundingBox = Rect(120, 180, 480, 220)
                    )
                )
            ),
            // Fine print at bottom
            TextBlockInfo(
                text = "Manufactured by Pure Harvest Foods Ltd, Bangalore",
                boundingBox = Rect(80, 800, 550, 830), // height 30px, top 800
                lines = listOf(
                    TextLineInfo(
                        text = "Manufactured by Pure Harvest Foods Ltd, Bangalore",
                        boundingBox = Rect(80, 800, 550, 830)
                    )
                )
            )
        )

        val title = extractor.inferTitle(blocks, frameHeight = 1000)
        assertEquals("ORGANIC ROLLED OATS", title)
    }

    @Test
    fun horizontalRayCasting_prioritizesDateDirectlyRightOfKeywordOverDiagonal() {
        // Keyword: "EXP:" at (100, 300, 180, 340) -> height 40px
        val keywordElement = TextElementInfo("EXP:", Rect(100, 300, 180, 340))
        
        // Date A: Directly to the right on the same horizontal ray (200, 300, 360, 340)
        val rightDate = TextElementInfo("15/12/2026", Rect(200, 300, 360, 340))
        
        // Date B: Diagonal distance is similar, but vertically misaligned (100, 500, 260, 540)
        val diagonalDate = TextElementInfo("10/05/2025", Rect(100, 500, 260, 540))

        val result = extractor.findBestRayCastedMatch(
            keywords = listOf(keywordElement),
            candidates = listOf(diagonalDate, rightDate)
        )

        assertNotNull(result)
        assertEquals("15/12/2026", result?.text)
        assertTrue("Ray-casted date should have high confidence", (result?.rayConfidence ?: 0f) > 0.85f)
    }

    @Test
    fun verticalRayCasting_matchesDateDirectlyBelowKeyword() {
        // Keyword: "BEST BEFORE" at (100, 200, 300, 240)
        val keywordElement = TextElementInfo("BEST BEFORE", Rect(100, 200, 300, 240))
        
        // Date directly below in the vertical corridor (110, 250, 270, 290)
        val belowDate = TextElementInfo("28/02/2027", Rect(110, 250, 270, 290))

        val result = extractor.findBestRayCastedMatch(
            keywords = listOf(keywordElement),
            candidates = listOf(belowDate)
        )

        assertNotNull(result)
        assertEquals("28/02/2027", result?.text)
        assertTrue((result?.rayConfidence ?: 0f) > 0.80f)
    }
}
