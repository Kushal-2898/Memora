package app.memora.data.repository

import app.memora.data.local.MemoryDao
import app.memora.data.local.ReminderDao
import app.memora.data.local.toDomain
import app.memora.data.local.toEntity
import app.memora.domain.model.MemoraError
import app.memora.domain.model.Reminder
import app.memora.domain.model.ReminderStatus
import app.memora.domain.repository.ReminderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Production implementation of [ReminderRepository] backed by Room SQLite.
 */
class DefaultReminderRepository(
    private val reminderDao: ReminderDao,
    private val memoryDao: MemoryDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ReminderRepository {

    override fun observeReminders(): Flow<List<Reminder>> {
        return reminderDao.observeAll()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override fun observeRemindersForMemory(memoryId: String): Flow<List<Reminder>> {
        return reminderDao.observeByMemoryId(memoryId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getReminder(id: String): Reminder? = withContext(ioDispatcher) {
        reminderDao.getById(id)?.toDomain()
    }

    override suspend fun getRemindersForMemory(memoryId: String): List<Reminder> = withContext(ioDispatcher) {
        reminderDao.getByMemoryId(memoryId).map { it.toDomain() }
    }

    override suspend fun createReminder(reminder: Reminder): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            reminderDao.insert(reminder.toEntity())
        }.recoverCatching { error ->
            throw MemoraError.Database(error.message ?: "Failed to persist reminder to database.")
        }
    }

    override suspend fun updateReminder(reminder: Reminder): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            reminderDao.update(reminder.toEntity())
        }.recoverCatching { error ->
            throw MemoraError.Database(error.message ?: "Failed to update reminder in database.")
        }
    }

    override suspend fun completeReminder(id: String, completedAt: Instant): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val existing = reminderDao.getById(id)
                ?: throw MemoraError.NotFound("Reminder not found for completion: $id")
            val updated = existing.copy(
                statusCode = ReminderStatus.COMPLETED.name,
                completedAtEpochMs = completedAt.toEpochMilli(),
                updatedAtEpochMs = completedAt.toEpochMilli(),
            )
            reminderDao.update(updated)
        }.recoverCatching { error ->
            if (error is MemoraError) throw error
            throw MemoraError.Database(error.message ?: "Failed to complete reminder.")
        }
    }

    override suspend fun deleteReminder(id: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            reminderDao.deleteById(id)
        }.recoverCatching { error ->
            throw MemoraError.Database(error.message ?: "Failed to delete reminder from database.")
        }
    }
}
