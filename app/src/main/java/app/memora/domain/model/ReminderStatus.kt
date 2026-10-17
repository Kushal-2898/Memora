package app.memora.domain.model

/**
 * Authoritative deterministic reminder lifecycle state.
 *
 * Designed to prevent contradictory boolean flags and provide clear timeline progression:
 * UPCOMING -> REMINDER_ACTIVE -> DUE_TODAY -> OVERDUE
 * (with transition to COMPLETED at any point upon user action).
 */
enum class ReminderStatus {
    UPCOMING,
    REMINDER_ACTIVE,
    DUE_TODAY,
    OVERDUE,
    COMPLETED
}
