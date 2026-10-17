package app.memora.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Domain representation of reminder configuration.
 *
 * Represents *how* and *when* to remind the user about an underlying [Memory].
 *
 * Relationship:
 * Memory (1) ────< (*) Reminder
 *
 * References the parent Memory via [memoryId] rather than duplicating the Memory's core business data
 * (such as title, eventType, or eventDate).
 *
 * Contains zero image bytes, Bitmaps, or permanent image URIs.
 */
data class Reminder(
    val id: String,
    val memoryId: String,
    val reminderStartDate: LocalDate,
    val reminderTime: LocalTime? = LocalTime.of(9, 0),
    val status: ReminderStatus = ReminderStatus.UPCOMING,
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
