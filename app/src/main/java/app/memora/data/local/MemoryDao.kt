package app.memora.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for memories.
 */
@Dao
interface MemoryDao {

    @Transaction
    @Query("SELECT * FROM memories ORDER BY eventDateEpochDay ASC")
    fun observeAllWithReminders(): Flow<List<MemoryWithRemindersRelation>>

    @Query("SELECT * FROM memories ORDER BY eventDateEpochDay ASC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MemoryEntity?

    @Transaction
    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun getByIdWithReminders(id: String): MemoryWithRemindersRelation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(entity: ReminderEntity)

    @Transaction
    suspend fun insertMemoryWithReminder(memory: MemoryEntity, reminder: ReminderEntity) {
        insert(memory)
        insertReminder(reminder)
    }

    @Update
    suspend fun update(entity: MemoryEntity)

    @Update
    suspend fun updateReminders(entities: List<ReminderEntity>)

    @Transaction
    suspend fun updateMemoryWithReminders(memory: MemoryEntity, reminders: List<ReminderEntity>) {
        update(memory)
        if (reminders.isNotEmpty()) {
            updateReminders(reminders)
        }
    }

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: String)
}
