package app.memora.data.local

import android.content.Context
import android.content.SharedPreferences

interface OnboardingPreferences {
    fun hasCompletedOnboarding(): Boolean
    fun setOnboardingCompleted(completed: Boolean = true)
    fun getSelectedCategories(): Set<String>
    fun setSelectedCategories(categories: Set<String>)
}

class DefaultOnboardingPreferences(context: Context) : OnboardingPreferences {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun hasCompletedOnboarding(): Boolean {
        return prefs.getBoolean(KEY_COMPLETED, false)
    }

    override fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_COMPLETED, completed).apply()
    }

    override fun getSelectedCategories(): Set<String> {
        return prefs.getStringSet(KEY_SELECTED_CATEGORIES, defaultCategories) ?: defaultCategories
    }

    override fun setSelectedCategories(categories: Set<String>) {
        prefs.edit().putStringSet(KEY_SELECTED_CATEGORIES, HashSet(categories)).commit()
    }

    companion object {
        private const val PREFS_NAME = "memora_user_preferences"
        private const val KEY_COMPLETED = "has_completed_onboarding"
        private const val KEY_SELECTED_CATEGORIES = "selected_categories"
        val defaultCategories = setOf("groceries", "medicines", "bills")
    }
}
