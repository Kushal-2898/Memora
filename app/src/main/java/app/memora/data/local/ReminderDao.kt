package app.memora.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for reminders.
 */
@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY reminderStartDateEpochDay ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE memoryId = :memoryId ORDER BY reminderStartDateEpochDay ASC")
    fun observeByMemoryId(memoryId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE memoryId = :memoryId ORDER BY reminderStartDateEpochDay ASC")
    suspend fun getByMemoryId(memoryId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReminderEntity)

    @Update
    suspend fun update(entity: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reminders WHERE memoryId = :memoryId")
    suspend fun deleteByMemoryId(memoryId: String)
}
