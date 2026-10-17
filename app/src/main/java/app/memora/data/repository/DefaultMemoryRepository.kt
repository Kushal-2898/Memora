package app.memora.data.repository

import app.memora.data.local.MemoryDao
import app.memora.data.local.ReminderDao
import app.memora.data.local.toDomain
import app.memora.data.local.toEntity
import app.memora.domain.model.MemoraError
import app.memora.domain.model.Memory
import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.model.Reminder
import app.memora.domain.repository.MemoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Production implementation of [MemoryRepository] backed by Room SQLite.
 */
class DefaultMemoryRepository(
    private val memoryDao: MemoryDao,
    private val reminderDao: ReminderDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MemoryRepository {

    override fun observeMemories(): Flow<List<MemoryWithReminders>> {
        return memoryDao.observeAllWithReminders()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getMemory(id: String): Memory? = withContext(ioDispatcher) {
        memoryDao.getById(id)?.toDomain()
    }

    override suspend fun getMemoryWithReminders(id: String): MemoryWithReminders? = withContext(ioDispatcher) {
        memoryDao.getByIdWithReminders(id)?.toDomain()
    }

    override suspend fun createMemory(memory: Memory, initialReminder: Reminder?): Result<Unit> = withContext(ioDispatcher) {
        val start = System.currentTimeMillis()
        app.memora.platform.logging.AppLog.checkMainThreadViolation("DB_CREATE")
        runCatching {
            if (initialReminder != null) {
                memoryDao.insertMemoryWithReminder(memory.toEntity(), initialReminder.toEntity())
            } else {
                memoryDao.insert(memory.toEntity())
            }
        }.onSuccess {
            app.memora.platform.logging.AppLog.recordDatabaseOperation("createMemory", System.currentTimeMillis() - start, true)
        }.recoverCatching { error ->
            val cat = app.memora.platform.logging.DiagnosticErrorCategory.fromThrowable(error)
            app.memora.platform.logging.AppLog.recordDatabaseOperation("createMemory", System.currentTimeMillis() - start, false, cat)
            throw MemoraError.Database(error.message ?: "Failed to persist memory to database.")
        }
    }

    override suspend fun updateMemory(memory: Memory): Result<Unit> = withContext(ioDispatcher) {
        val start = System.currentTimeMillis()
        app.memora.platform.logging.AppLog.checkMainThreadViolation("DB_UPDATE")
        runCatching {
            memoryDao.update(memory.toEntity())
        }.onSuccess {
            app.memora.platform.logging.AppLog.recordDatabaseOperation("updateMemory", System.currentTimeMillis() - start, true)
        }.recoverCatching { error ->
            val cat = app.memora.platform.logging.DiagnosticErrorCategory.fromThrowable(error)
            app.memora.platform.logging.AppLog.recordDatabaseOperation("updateMemory", System.currentTimeMillis() - start, false, cat)
            throw MemoraError.Database(error.message ?: "Failed to update memory in database.")
        }
    }

    override suspend fun updateMemoryWithReminders(memory: Memory, reminders: List<Reminder>): Result<Unit> = withContext(ioDispatcher) {
        val start = System.currentTimeMillis()
        app.memora.platform.logging.AppLog.checkMainThreadViolation("DB_UPDATE_TRANSACTION")
        runCatching {
            memoryDao.updateMemoryWithReminders(
                memory = memory.toEntity(),
                reminders = reminders.map { it.toEntity() }
            )
        }.onSuccess {
            app.memora.platform.logging.AppLog.recordDatabaseOperation("updateMemoryWithReminders", System.currentTimeMillis() - start, true)
        }.recoverCatching { error ->
            val cat = app.memora.platform.logging.DiagnosticErrorCategory.fromThrowable(error)
            app.memora.platform.logging.AppLog.recordDatabaseOperation("updateMemoryWithReminders", System.currentTimeMillis() - start, false, cat)
            throw MemoraError.Database(error.message ?: "Failed to transactionally update memory with reminders in database.")
        }
    }

    override suspend fun deleteMemory(id: String): Result<Unit> = withContext(ioDispatcher) {
        val start = System.currentTimeMillis()
        app.memora.platform.logging.AppLog.checkMainThreadViolation("DB_DELETE")
        runCatching {
            // Room foreign keys are configured with CASCADE; SQLite automatically removes child reminders.
            memoryDao.deleteById(id)
        }.onSuccess {
            app.memora.platform.logging.AppLog.recordDatabaseOperation("deleteMemory", System.currentTimeMillis() - start, true)
        }.recoverCatching { error ->
            val cat = app.memora.platform.logging.DiagnosticErrorCategory.fromThrowable(error)
            app.memora.platform.logging.AppLog.recordDatabaseOperation("deleteMemory", System.currentTimeMillis() - start, false, cat)
            throw MemoraError.Database(error.message ?: "Failed to delete memory from database.")
        }
    }
}
