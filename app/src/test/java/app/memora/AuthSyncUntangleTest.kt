package app.memora

import app.memora.data.repository.DefaultAuthRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSyncUntangleTest {

    @Test
    fun `signOut flushes before-sign-out hook first and runs cleanup on IO dispatcher`() = runTest {
        val events = mutableListOf<String>()
        val ranOffMainThread = AtomicBoolean(false)

        val repo = DefaultAuthRepository(
            onboardingPreferences = null,
            firebaseAuth = null,
            ioDispatcher = Dispatchers.Default,
            onBeforeSignOut = {
                events.add("flush_sync")
            },
            onSignOutCleanup = {
                ranOffMainThread.set(Thread.currentThread().name != "main")
                events.add("cleanup_db_and_alarms")
            },
        )

        val result = withContext(Dispatchers.Unconfined) {
            repo.signOut()
        }

        assertTrue(result.isSuccess)
        assertEquals(listOf("flush_sync", "cleanup_db_and_alarms"), events)
        assertTrue("DB cleanup must run on background dispatcher, never main", ranOffMainThread.get())
    }
}
