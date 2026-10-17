package app.memora.domain.repository

import app.memora.domain.model.Reminder
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Domain boundary for Reminder configuration persistence and observation.
 * Concrete Room implementations reside strictly in the data layer.
 */
interface ReminderRepository {

    /**
     * Observes all reminders across all memories.
     */
    fun observeReminders(): Flow<List<Reminder>>

    /**
     * Observes all reminders associated with a specific memory ID.
     */
    fun observeRemindersForMemory(memoryId: String): Flow<List<Reminder>>

    /**
     * Retrieves a single reminder by its unique identifier.
     */
    suspend fun getReminder(id: String): Reminder?

    /**
     * Retrieves all reminders for a given memory.
     */
    suspend fun getRemindersForMemory(memoryId: String): List<Reminder>

    /**
     * Persists a new reminder into local storage.
     */
    suspend fun createReminder(reminder: Reminder): Result<Unit>

    /**
     * Updates an existing reminder configuration.
     */
    suspend fun updateReminder(reminder: Reminder): Result<Unit>

    /**
     * Marks a reminder completed with the given completion timestamp.
     */
    suspend fun completeReminder(id: String, completedAt: Instant): Result<Unit>

    /**
     * Deletes a reminder from local storage.
     */
    suspend fun deleteReminder(id: String): Result<Unit>
}
