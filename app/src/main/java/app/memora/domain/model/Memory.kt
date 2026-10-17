package app.memora.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Core durable business object in Memora.
 *
 * Represents the actual thing or date being remembered, completely independent
 * of how, when, or how often the user is reminded of it.
 *
 * CRITICAL ARCHITECTURAL CONSTRAINTS:
 * - No image bytes, Bitmaps, or permanent image URIs are stored in Memory.
 * - Original captured images are transient inputs during extraction, not part of durable application state.
 */
data class Memory(
    val id: String,
    val title: String,
    val description: String? = null,
    val eventType: EventType = EventType.OTHER,
    val eventDate: LocalDate,
    val eventTime: LocalTime? = null,
    val timeZoneId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
