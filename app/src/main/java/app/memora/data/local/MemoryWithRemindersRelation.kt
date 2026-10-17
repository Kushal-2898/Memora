package app.memora.data.local

import androidx.room.Embedded
import androidx.room.Relation
import app.memora.domain.model.MemoryWithReminders

/**
 * Room relationship class joining [MemoryEntity] with all associated [ReminderEntity] items.
 */
data class MemoryWithRemindersRelation(
    @Embedded val memory: MemoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "memoryId"
    )
    val reminders: List<ReminderEntity>
)

fun MemoryWithRemindersRelation.toDomain(): MemoryWithReminders {
    return MemoryWithReminders(
        memory = memory.toDomain(),
        reminders = reminders.map { it.toDomain() }
    )
}
