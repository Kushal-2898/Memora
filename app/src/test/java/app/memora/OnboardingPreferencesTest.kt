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
}
