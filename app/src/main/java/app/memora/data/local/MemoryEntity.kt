package app.memora.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.memora.domain.model.EventType
import app.memora.domain.model.Memory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Room persistence entity for durable memories.
 *
 * Represents the core business object: what is being remembered and its event date.
 * Contains ZERO image data, Bitmaps, or permanent image references.
 */
@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["eventDateEpochDay"]),
    ]
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String?,
    val eventTypeCode: String,
    val eventDateEpochDay: Long,
    val eventTimeNanoOfDay: Long?,
    val timeZoneId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

fun MemoryEntity.toDomain(): Memory {
    return Memory(
        id = id,
        title = title,
        description = description,
        eventType = runCatching { EventType.valueOf(eventTypeCode) }.getOrDefault(EventType.OTHER),
        eventDate = LocalDate.ofEpochDay(eventDateEpochDay),
        eventTime = eventTimeNanoOfDay?.let { LocalTime.ofNanoOfDay(it) },
        timeZoneId = timeZoneId,
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
    )
}

fun Memory.toEntity(): MemoryEntity {
    return MemoryEntity(
        id = id,
        title = title,
        description = description,
        eventTypeCode = eventType.name,
        eventDateEpochDay = eventDate.toEpochDay(),
        eventTimeNanoOfDay = eventTime?.toNanoOfDay(),
        timeZoneId = timeZoneId,
        createdAtEpochMs = createdAt.toEpochMilli(),
        updatedAtEpochMs = updatedAt.toEpochMilli(),
    )
}
