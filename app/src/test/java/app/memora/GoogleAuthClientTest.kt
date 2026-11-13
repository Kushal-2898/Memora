package app.memora

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import app.memora.platform.auth.GoogleAuthClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = MemoraApplication::class)
class GoogleAuthClientTest {

    private val client = GoogleAuthClient()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun isConfigured_returnsTrueWithConfiguredWebClientId() {
        val configured = client.isConfigured(context, "")
        assertTrue("Should be configured when default_web_client_id exists in resources", configured)
        assertEquals(
            "40262037658-vkd5cf8j79dlv1d6c6tfmft7brvfreic.apps.googleusercontent.com",
            client.resolveServerClientId(context, "")
        )
    }

    @Test
    fun isConfigured_returnsTrueWhenExplicitClientIdProvided() {
        val configured = client.isConfigured(context, "custom.apps.googleusercontent.com")
        assertTrue("Should be configured when explicit client ID is provided", configured)
        assertEquals("custom.apps.googleusercontent.com", client.resolveServerClientId(context, "custom.apps.googleusercontent.com"))
    }

    @Test
    fun isConfigured_returnsFalseWhenUnconfiguredContextPassed() {
        val emptyContext = object : ContextWrapper(context) {
            override fun getPackageName(): String = "com.nonexistent.app"
        }
        assertFalse(client.isConfigured(emptyContext, ""))
    }

    @Test
    fun signIn_withUnconfiguredContext_returnsFailureWithDescriptiveError_insteadOfCrashing() = runTest {
        val emptyContext = object : ContextWrapper(context) {
            override fun getPackageName(): String = "com.nonexistent.app"
        }
        val result = client.signIn(emptyContext, "")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalStateException)
        assertTrue(exception?.message?.contains("serverClientId is empty") == true)
    }

    @Test
    fun signIn_withNonActivityContext_returnsFailureRequiringActivityContext() = runTest {
        val result = client.signIn(context, "custom.apps.googleusercontent.com")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalStateException)
        assertTrue(exception?.message?.contains("Activity context required") == true)
    }
}
