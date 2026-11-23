package app.memora

import app.memora.data.local.OnboardingPreferences
import app.memora.data.repository.DefaultEntitlementRepository
import app.memora.domain.model.EntitlementStatus
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingEntitlementTest {

    private class FakeOnboardingPreferences(
        private var pro: Boolean = false,
        private var scans: Int = 0,
    ) : OnboardingPreferences {
        override fun hasCompletedOnboarding() = true
        override fun setOnboardingCompleted(completed: Boolean) {}
        override fun getSelectedCategories() = emptySet<String>()
        override fun setSelectedCategories(categories: Set<String>) {}
        override fun getDefaultReminderTime(): LocalTime = LocalTime.of(9, 0)
        override fun setDefaultReminderTime(time: LocalTime) {}
        override fun getMonthlyScanCount() = scans
        override fun incrementMonthlyScanCount(): Int = ++scans
        override fun getRemainingMonthlyScans(limit: Int): Int = (limit - scans).coerceAtLeast(0)
        override fun isProUser() = pro
        override fun setProUser(isPro: Boolean) { pro = isPro }
        override fun getCustomCategories() = emptySet<String>()
        override fun addCustomCategory(category: String) {}
        override fun getAuthUid(): String? = null
        override fun getAuthEmail(): String? = null
        override fun getAuthDisplayName(): String? = null
        override fun getAuthPhotoUrl(): String? = null
        override fun setAuth(uid: String, email: String?, displayName: String?, photoUrl: String?) {}
        override fun clearAuth() {}
        override fun hasPromptedNotificationPermission() = true
        override fun setPromptedNotificationPermission(prompted: Boolean) {}
    }

    @Test
    fun `free user can scan up to 5 times per month and is gated on scan 6`() = runTest {
        val prefs = FakeOnboardingPreferences(pro = false, scans = 0)
        val repo = DefaultEntitlementRepository(prefs)

        assertEquals(5, OnboardingPreferences.FREE_MONTHLY_SCAN_LIMIT)
        repeat(5) {
            assertTrue("Scan #${it + 1} must be allowed on Free plan", repo.canScan())
            prefs.incrementMonthlyScanCount()
        }

        assertEquals(0, prefs.getRemainingMonthlyScans())
        assertFalse("Scan #6 must require Memora Pro", repo.canScan())
        assertEquals(EntitlementStatus.FREE, repo.getEntitlement().status)
    }

    @Test
    fun `pro user has unlimited scans even past 10 monthly scans`() = runTest {
        val prefs = FakeOnboardingPreferences(pro = true, scans = 42)
        val repo = DefaultEntitlementRepository(prefs)

        assertTrue("Pro user must always be allowed to scan", repo.canScan())
        assertEquals(EntitlementStatus.PREMIUM, repo.getEntitlement().status)
    }
}
