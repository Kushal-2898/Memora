package app.memora.domain.model

/**
 * Composite domain object binding a [Memory] with its configured [Reminder] items.
 *
 * Provides convenient accessors for the primary/active reminder and overall status.
 */
data class MemoryWithReminders(
    val memory: Memory,
    val reminders: List<Reminder> = emptyList(),
) {
    /**
     * Retrieves the primary active reminder for this memory, if any.
     * Prioritizes non-completed reminders.
     */
    val activeReminder: Reminder?
        get() = reminders.firstOrNull { it.completedAt == null && it.status != ReminderStatus.COMPLETED } ?: reminders.firstOrNull()

    /**
     * Evaluates the effective reminder status for UI display.
     */
    val effectiveStatus: ReminderStatus
        get() = activeReminder?.status ?: ReminderStatus.UPCOMING
}
