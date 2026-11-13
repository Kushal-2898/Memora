package app.memora

import androidx.test.core.app.ApplicationProvider
import app.memora.domain.extraction.CloudGeminiMemoryExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = MemoraApplication::class)
class CloudGeminiMemoryExtractorTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun cloudExtractor_canBeInstantiatedWithoutApiKey() {
        val extractor = CloudGeminiMemoryExtractor(context, null)
        assertNotNull(extractor)
    }

    @Test
    fun cloudExtractor_returnsFailureWhenFunctionsUnavailable() = runTest {
        val extractor = CloudGeminiMemoryExtractor(context, null)
        val result = extractor.extractCandidate("Sample OCR textIndane Gas Bill", null)
        assertTrue("Should return failure gracefully when Functions service is unavailable", result.isFailure)
    }
}
