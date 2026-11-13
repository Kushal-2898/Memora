package app.memora

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.memora.data.local.DefaultOnboardingPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear preferences before each test
        context.getSharedPreferences("memora_user_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun defaultOnboardingState_isFalse() {
        val preferences = DefaultOnboardingPreferences(context)
        assertFalse(preferences.hasCompletedOnboarding())
    }

    @Test
    fun markOnboardingCompleted_persistsTrue() {
        val preferences = DefaultOnboardingPreferences(context)
        preferences.setOnboardingCompleted(true)
        assertTrue(preferences.hasCompletedOnboarding())

        // Recreate instance to ensure persistent backing
        val newPreferencesInstance = DefaultOnboardingPreferences(context)
        assertTrue(newPreferencesInstance.hasCompletedOnboarding())
    }

    @Test
    fun resetOnboarding_persistsFalse() {
        val preferences = DefaultOnboardingPreferences(context)
        preferences.setOnboardingCompleted(true)
        assertTrue(preferences.hasCompletedOnboarding())

        preferences.setOnboardingCompleted(false)
        assertFalse(preferences.hasCompletedOnboarding())
    }

    @Test
    fun categoryPreferences_defaultAndCustomPersistence() {
        val preferences = DefaultOnboardingPreferences(context)
        assertEquals(DefaultOnboardingPreferences.defaultCategories, preferences.getSelectedCategories())

        val customCategories = setOf("medicines", "warranties")
        preferences.setSelectedCategories(customCategories)
        assertEquals(customCategories, preferences.getSelectedCategories())

        val reloadedPreferences = DefaultOnboardingPreferences(context)
        assertEquals(customCategories, reloadedPreferences.getSelectedCategories())
    }

    @Test
    fun defaultReminderTime_is9AM_andPersistsCustomTime() {
        val preferences = DefaultOnboardingPreferences(context)
        assertEquals(java.time.LocalTime.of(9, 0), preferences.getDefaultReminderTime())

        val customTime = java.time.LocalTime.of(8, 30)
        preferences.setDefaultReminderTime(customTime)
        assertEquals(customTime, preferences.getDefaultReminderTime())

        val reloaded = DefaultOnboardingPreferences(context)
        assertEquals(customTime, reloaded.getDefaultReminderTime())
    }

    @Test
    fun monthlyScanCount_startsAtZero_increments_andTracksRemaining() {
        val preferences = DefaultOnboardingPreferences(context)
        assertEquals(0, preferences.getMonthlyScanCount())
        assertEquals(5, preferences.getRemainingMonthlyScans())

        preferences.incrementMonthlyScanCount()
        preferences.incrementMonthlyScanCount()
        assertEquals(2, preferences.getMonthlyScanCount())
        assertEquals(3, preferences.getRemainingMonthlyScans())

        // Recreate instance to verify persistence
        val reloaded = DefaultOnboardingPreferences(context)
        assertEquals(2, reloaded.getMonthlyScanCount())
        assertEquals(3, reloaded.getRemainingMonthlyScans())
    }

    @Test
    fun monthlyScanCount_autoResets_whenMonthChanges() {
        val preferences = DefaultOnboardingPreferences(context)
        preferences.incrementMonthlyScanCount()
        preferences.incrementMonthlyScanCount()
        assertEquals(2, preferences.getMonthlyScanCount())

        // Simulate previous month in preferences
        context.getSharedPreferences("memora_user_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("monthly_scan_month", "2020-01")
            .apply()

        // Accessing getMonthlyScanCount should detect new month and auto-reset to 0
        val reloaded = DefaultOnboardingPreferences(context)
        assertEquals(0, reloaded.getMonthlyScanCount())
        assertEquals(5, reloaded.getRemainingMonthlyScans())
    }

    @Test
    fun proUserStatus_persistsProperly() {
        val preferences = DefaultOnboardingPreferences(context)
        assertFalse(preferences.isProUser())

        preferences.setProUser(true)
        assertTrue(preferences.isProUser())

        val reloaded = DefaultOnboardingPreferences(context)
        assertTrue(reloaded.isProUser())
    }

    @Test
    fun customCategories_persistsProperly() {
        val preferences = DefaultOnboardingPreferences(context)
        assertTrue(preferences.getCustomCategories().isEmpty())

        preferences.addCustomCategory("Cosmetics")
        preferences.addCustomCategory("Vehicle Service")
        assertEquals(setOf("Cosmetics", "Vehicle Service"), preferences.getCustomCategories())

        val reloaded = DefaultOnboardingPreferences(context)
        assertEquals(setOf("Cosmetics", "Vehicle Service"), reloaded.getCustomCategories())
    }

    @Test
    fun authUserPersistence_setAndClearWorks() {
        val preferences = DefaultOnboardingPreferences(context)
        org.junit.Assert.assertNull(preferences.getAuthUid())

        preferences.setAuth(
            uid = "user_123",
            email = "alex@gmail.com",
            displayName = "Alex Johnson",
            photoUrl = "https://example.com/photo.jpg"
        )
        assertEquals("user_123", preferences.getAuthUid())
        assertEquals("alex@gmail.com", preferences.getAuthEmail())
        assertEquals("Alex Johnson", preferences.getAuthDisplayName())
        assertEquals("https://example.com/photo.jpg", preferences.getAuthPhotoUrl())

        // Recreate instance
        val reloaded = DefaultOnboardingPreferences(context)
        assertEquals("user_123", reloaded.getAuthUid())

        preferences.clearAuth()
        org.junit.Assert.assertNull(preferences.getAuthUid())
        org.junit.Assert.assertNull(preferences.getAuthEmail())
    }
}
