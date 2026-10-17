package app.memora.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.memora.domain.model.Reminder
import app.memora.domain.model.ReminderStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Room persistence entity for reminder configuration.
 *
 * References its parent [MemoryEntity] via foreign key relationship:
 * Memory (1) ────< (*) Reminder
 *
 * Configured with CASCADE delete so that removing a Memory cleans up its reminder configs.
 * Indexed on memoryId, reminderStartDateEpochDay, and statusCode for fast query execution.
 *
 * Contains ZERO image data, Bitmaps, or permanent image references.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["memoryId"]),
        Index(value = ["reminderStartDateEpochDay"]),
        Index(value = ["statusCode"]),
    ]
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val memoryId: String,
    val reminderStartDateEpochDay: Long,
    val reminderTimeNanoOfDay: Long?,
    val statusCode: String,
    val completedAtEpochMs: Long?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

fun ReminderEntity.toDomain(): Reminder {
    return Reminder(
        id = id,
        memoryId = memoryId,
        reminderStartDate = LocalDate.ofEpochDay(reminderStartDateEpochDay),
        reminderTime = reminderTimeNanoOfDay?.let { LocalTime.ofNanoOfDay(it) },
        status = runCatching { ReminderStatus.valueOf(statusCode) }.getOrDefault(ReminderStatus.UPCOMING),
        completedAt = completedAtEpochMs?.let { Instant.ofEpochMilli(it) },
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
    )
}

fun Reminder.toEntity(): ReminderEntity {
    return ReminderEntity(
        id = id,
        memoryId = memoryId,
        reminderStartDateEpochDay = reminderStartDate.toEpochDay(),
        reminderTimeNanoOfDay = reminderTime?.toNanoOfDay(),
        statusCode = status.name,
        completedAtEpochMs = completedAt?.toEpochMilli(),
        createdAtEpochMs = createdAt.toEpochMilli(),
        updatedAtEpochMs = updatedAt.toEpochMilli(),
    )
}
