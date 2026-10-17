package app.memora.domain.repository

import app.memora.domain.model.Memory
import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.model.Reminder
import kotlinx.coroutines.flow.Flow

/**
 * Domain boundary for Memory persistence and observation.
 * Concrete Room implementations reside strictly in the data layer.
 */
interface MemoryRepository {

    /**
     * Observes all memories together with their configured reminders.
     */
    fun observeMemories(): Flow<List<MemoryWithReminders>>

    /**
     * Retrieves a single memory by ID.
     */
    suspend fun getMemory(id: String): Memory?

    /**
     * Retrieves a memory with its associated reminders.
     */
    suspend fun getMemoryWithReminders(id: String): MemoryWithReminders?

    /**
     * Persists a new memory, optionally persisting an initial reminder atomically.
     */
    suspend fun createMemory(memory: Memory, initialReminder: Reminder? = null): Result<Unit>

    /**
     * Updates an existing memory's durable business attributes.
     */
    suspend fun updateMemory(memory: Memory): Result<Unit>

    /**
     * Atomically updates a memory and its associated reminders in a single database transaction.
     */
    suspend fun updateMemoryWithReminders(memory: Memory, reminders: List<Reminder>): Result<Unit> =
        updateMemory(memory)

    /**
     * Deletes a memory and cascades deletion to child reminders.
     */
    suspend fun deleteMemory(id: String): Result<Unit>
}
