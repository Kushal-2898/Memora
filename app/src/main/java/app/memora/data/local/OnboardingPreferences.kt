package app.memora.data.local

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalTime
import java.time.YearMonth

interface OnboardingPreferences {
    fun hasCompletedOnboarding(): Boolean
    fun setOnboardingCompleted(completed: Boolean = true)
    fun getSelectedCategories(): Set<String>
    fun setSelectedCategories(categories: Set<String>)
    fun getDefaultReminderTime(): LocalTime
    fun setDefaultReminderTime(time: LocalTime)
    fun getMonthlyScanCount(): Int
    fun incrementMonthlyScanCount(): Int
    fun getRemainingMonthlyScans(limit: Int = FREE_MONTHLY_SCAN_LIMIT): Int
    fun isProUser(): Boolean

    companion object {
        const val FREE_MONTHLY_SCAN_LIMIT = 10
    }
    fun setProUser(isPro: Boolean)
    fun getCustomCategories(): Set<String>
    fun addCustomCategory(category: String)
    fun getAuthUid(): String?
    fun getAuthEmail(): String?
    fun getAuthDisplayName(): String?
    fun getAuthPhotoUrl(): String?
    fun setAuth(uid: String, email: String?, displayName: String?, photoUrl: String?)
    fun clearAuth()
    fun clearAllUserData() {
        clearAuth()
    }
    fun hasPromptedNotificationPermission(): Boolean
    fun setPromptedNotificationPermission(prompted: Boolean = true)
    fun isScanNudgeEnabled(): Boolean = true
    fun setScanNudgeEnabled(enabled: Boolean) {}
}

class DefaultOnboardingPreferences(context: Context) : OnboardingPreferences {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    override fun getDefaultReminderTime(): LocalTime {
        val timeStr = prefs.getString(KEY_DEFAULT_REMINDER_TIME, null) ?: return LocalTime.of(9, 0)
        return runCatching { LocalTime.parse(timeStr) }.getOrDefault(LocalTime.of(9, 0))
    }

    override fun setDefaultReminderTime(time: LocalTime) {
        prefs.edit().putString(KEY_DEFAULT_REMINDER_TIME, time.toString()).apply()
    }

    override fun getMonthlyScanCount(): Int {
        val currentMonth = YearMonth.now().toString()
        val savedMonth = prefs.getString(KEY_SCAN_MONTH, null)
        if (savedMonth != currentMonth) {
            prefs.edit().putString(KEY_SCAN_MONTH, currentMonth).putInt(KEY_SCAN_COUNT, 0).apply()
            return 0
        }
        return prefs.getInt(KEY_SCAN_COUNT, 0)
    }

    override fun incrementMonthlyScanCount(): Int {
        val current = getMonthlyScanCount()
        val next = current + 1
        prefs.edit().putInt(KEY_SCAN_COUNT, next).apply()
        return next
    }

    override fun getRemainingMonthlyScans(limit: Int): Int {
        return (limit - getMonthlyScanCount()).coerceAtLeast(0)
    }

    override fun isProUser(): Boolean {
        return prefs.getBoolean(KEY_IS_PRO, false)
    }

    override fun setProUser(isPro: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PRO, isPro).apply()
    }

    override fun getCustomCategories(): Set<String> {
        return prefs.getStringSet(KEY_CUSTOM_CATEGORIES, emptySet()) ?: emptySet()
    }

    override fun addCustomCategory(category: String) {
        val sanitized = category.trim().take(30)
        if (sanitized.isBlank()) return
        val reservedNames = setOf("expiration", "due date", "warranty end", "subscription end", "best before", "other")
        if (reservedNames.contains(sanitized.lowercase())) return
        val current = getCustomCategories().toMutableSet()
        if (current.none { it.equals(sanitized, ignoreCase = true) }) {
            current.add(sanitized)
            prefs.edit().putStringSet(KEY_CUSTOM_CATEGORIES, current).apply()
        }
    }

    override fun getAuthUid(): String? = prefs.getString(KEY_AUTH_UID, null)
    override fun getAuthEmail(): String? = prefs.getString(KEY_AUTH_EMAIL, null)
    override fun getAuthDisplayName(): String? = prefs.getString(KEY_AUTH_NAME, null)
    override fun getAuthPhotoUrl(): String? = prefs.getString(KEY_AUTH_PHOTO, null)

    override fun setAuth(uid: String, email: String?, displayName: String?, photoUrl: String?) {
        prefs.edit()
            .putString(KEY_AUTH_UID, uid)
            .putString(KEY_AUTH_EMAIL, email)
            .putString(KEY_AUTH_NAME, displayName)
            .putString(KEY_AUTH_PHOTO, photoUrl)
            .apply()
    }

    override fun clearAuth() {
        prefs.edit()
            .remove(KEY_AUTH_UID)
            .remove(KEY_AUTH_EMAIL)
            .remove(KEY_AUTH_NAME)
            .remove(KEY_AUTH_PHOTO)
            .apply()
    }

    override fun clearAllUserData() {
        clearAuth()
        prefs.edit()
            .remove(KEY_CUSTOM_CATEGORIES)
            .remove(KEY_SELECTED_CATEGORIES)
            .remove(KEY_DEFAULT_REMINDER_TIME)
            .apply()
        runCatching {
            appContext.getSharedPreferences("memora_barcode_catalog", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    override fun hasPromptedNotificationPermission(): Boolean {
        return prefs.getBoolean(KEY_PROMPTED_NOTIFICATION, false)
    }

    override fun setPromptedNotificationPermission(prompted: Boolean) {
        prefs.edit().putBoolean(KEY_PROMPTED_NOTIFICATION, prompted).apply()
    }

    override fun isScanNudgeEnabled(): Boolean {
        return prefs.getBoolean(KEY_SCAN_NUDGE_ENABLED, true)
    }

    override fun setScanNudgeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCAN_NUDGE_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "memora_user_preferences"
        private const val KEY_COMPLETED = "has_completed_onboarding"
        private const val KEY_SELECTED_CATEGORIES = "selected_categories"
        private const val KEY_DEFAULT_REMINDER_TIME = "default_reminder_time"
        private const val KEY_SCAN_MONTH = "monthly_scan_month"
        private const val KEY_SCAN_COUNT = "monthly_scan_count"
        private const val KEY_IS_PRO = "is_pro_user"
        private const val KEY_CUSTOM_CATEGORIES = "custom_categories"
        private const val KEY_AUTH_UID = "auth_user_uid"
        private const val KEY_AUTH_EMAIL = "auth_user_email"
        private const val KEY_AUTH_NAME = "auth_user_display_name"
        private const val KEY_AUTH_PHOTO = "auth_user_photo_url"
        private const val KEY_PROMPTED_NOTIFICATION = "has_prompted_notification_permission"
        private const val KEY_SCAN_NUDGE_ENABLED = "scan_nudge_enabled"
        val defaultCategories = setOf("groceries", "medicines", "bills")
    }
}
