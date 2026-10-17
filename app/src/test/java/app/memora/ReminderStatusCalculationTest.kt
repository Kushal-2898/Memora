package app.memora

import app.memora.domain.model.EventType
import app.memora.domain.model.Memory
import app.memora.domain.model.MemoryWithReminders
import app.memora.domain.model.Reminder
import app.memora.domain.model.ReminderStatus
import app.memora.domain.scheduler.ClockProvider
import app.memora.domain.usecase.CalculateReminderStatusUseCase
import app.memora.domain.usecase.IsReminderNotificationActiveUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Deterministic unit tests for Memory & Reminder status calculation and lifecycle transitions.
 *
 * Requirements from Section 9 and Section 14:
 * UPCOMING -> REMINDER_ACTIVE -> DUE_TODAY -> OVERDUE (with COMPLETED at any step).
 */
class ReminderStatusCalculationTest {

    private class FixedClockProvider(
        private val fixedDate: LocalDate,
        private val fixedInstant: Instant,
    ) : ClockProvider {
        override fun now(): Instant = fixedInstant
        override fun today(zoneId: ZoneId): LocalDate = fixedDate
    }

    private val referenceInstant = Instant.parse("2026-09-03T10:00:00Z")
    private val utcZone = ZoneId.of("UTC")

    private fun buildMemory(
        id: String = "mem-1",
        title: String = "Tax Form Submission",
        eventType: EventType = EventType.DUE_DATE,
        eventDate: LocalDate,
        eventTime: LocalTime? = null,
        zoneId: String = "UTC",
    ): Memory {
        return Memory(
            id = id,
            title = title,
            eventType = eventType,
            eventDate = eventDate,
            eventTime = eventTime,
            timeZoneId = zoneId,
            createdAt = referenceInstant,
            updatedAt = referenceInstant,
        )
    }

    private fun buildReminder(
        id: String = "rem-1",
        memoryId: String = "mem-1",
        reminderStartDate: LocalDate,
        reminderTime: LocalTime = LocalTime.of(9, 0),
        completedAt: Instant? = null,
        status: ReminderStatus = if (completedAt != null) ReminderStatus.COMPLETED else ReminderStatus.UPCOMING,
    ): Reminder {
        return Reminder(
            id = id,
            memoryId = memoryId,
            reminderStartDate = reminderStartDate,
            reminderTime = reminderTime,
            status = status,
            completedAt = completedAt,
            createdAt = referenceInstant,
            updatedAt = referenceInstant,
        )
    }

    // Specification Test: Sep 12 EventDate & Sep 10 ReminderStartDate
    @Test
    fun `specification lifecycle - Sep 12 eventDate and Sep 10 reminderStartDate timeline`() {
        val eventDate = LocalDate.of(2026, 9, 12)
        val reminderStartDate = LocalDate.of(2026, 9, 10)
        val memory = buildMemory(eventDate = eventDate)
        val reminder = buildReminder(reminderStartDate = reminderStartDate)

        // Sep 9 -> UPCOMING (notification inactive)
        val clockSep9 = FixedClockProvider(LocalDate.of(2026, 9, 9), referenceInstant)
        val calculateSep9 = CalculateReminderStatusUseCase(clockSep9)
        val notifySep9 = IsReminderNotificationActiveUseCase(calculateSep9)
        assertEquals(ReminderStatus.UPCOMING, calculateSep9(memory, reminder))
        assertFalse(notifySep9(memory, reminder))

        // Sep 10 -> REMINDER_ACTIVE (notification active)
        val clockSep10 = FixedClockProvider(LocalDate.of(2026, 9, 10), referenceInstant)
        val calculateSep10 = CalculateReminderStatusUseCase(clockSep10)
        val notifySep10 = IsReminderNotificationActiveUseCase(calculateSep10)
        assertEquals(ReminderStatus.REMINDER_ACTIVE, calculateSep10(memory, reminder))
        assertTrue(notifySep10(memory, reminder))

        // Sep 11 -> REMINDER_ACTIVE (notification active)
        val clockSep11 = FixedClockProvider(LocalDate.of(2026, 9, 11), referenceInstant)
        val calculateSep11 = CalculateReminderStatusUseCase(clockSep11)
        val notifySep11 = IsReminderNotificationActiveUseCase(calculateSep11)
        assertEquals(ReminderStatus.REMINDER_ACTIVE, calculateSep11(memory, reminder))
        assertTrue(notifySep11(memory, reminder))

        // Sep 12 -> DUE_TODAY (notification active)
        val clockSep12 = FixedClockProvider(LocalDate.of(2026, 9, 12), referenceInstant)
        val calculateSep12 = CalculateReminderStatusUseCase(clockSep12)
        val notifySep12 = IsReminderNotificationActiveUseCase(calculateSep12)
        assertEquals(ReminderStatus.DUE_TODAY, calculateSep12(memory, reminder))
        assertTrue(notifySep12(memory, reminder))

        // Sep 13 -> OVERDUE (notification active)
        val clockSep13 = FixedClockProvider(LocalDate.of(2026, 9, 13), referenceInstant)
        val calculateSep13 = CalculateReminderStatusUseCase(clockSep13)
        val notifySep13 = IsReminderNotificationActiveUseCase(calculateSep13)
        assertEquals(ReminderStatus.OVERDUE, calculateSep13(memory, reminder))
        assertTrue(notifySep13(memory, reminder))

        // Complete on Sep 11 -> COMPLETED and no further reminder activity on Sep 11, Sep 12, Sep 13
        val completedReminder = reminder.copy(
            completedAt = LocalDate.of(2026, 9, 11).atStartOfDay(utcZone).toInstant()
        )
        assertEquals(ReminderStatus.COMPLETED, calculateSep11(memory, completedReminder))
        assertFalse(notifySep11(memory, completedReminder))

        assertEquals(ReminderStatus.COMPLETED, calculateSep12(memory, completedReminder))
        assertFalse(notifySep12(memory, completedReminder))

        assertEquals(ReminderStatus.COMPLETED, calculateSep13(memory, completedReminder))
        assertFalse(notifySep13(memory, completedReminder))
    }

    @Test
    fun `reminderStartDate equals eventDate transitions directly from UPCOMING to DUE_TODAY`() {
        val sameDate = LocalDate.of(2026, 9, 12)
        val memory = buildMemory(eventDate = sameDate)
        val reminder = buildReminder(reminderStartDate = sameDate)

        // Day before -> UPCOMING
        val clockBefore = FixedClockProvider(sameDate.minusDays(1), referenceInstant)
        assertEquals(ReminderStatus.UPCOMING, CalculateReminderStatusUseCase(clockBefore)(memory, reminder))

        // On date -> DUE_TODAY
        val clockOn = FixedClockProvider(sameDate, referenceInstant)
        assertEquals(ReminderStatus.DUE_TODAY, CalculateReminderStatusUseCase(clockOn)(memory, reminder))

        // Day after -> OVERDUE
        val clockAfter = FixedClockProvider(sameDate.plusDays(1), referenceInstant)
        assertEquals(ReminderStatus.OVERDUE, CalculateReminderStatusUseCase(clockAfter)(memory, reminder))
    }

    @Test
    fun `completion on due date sets status to COMPLETED`() {
        val date = LocalDate.of(2026, 9, 12)
        val memory = buildMemory(eventDate = date)
        val completedReminder = buildReminder(
            reminderStartDate = date.minusDays(2),
            completedAt = date.atStartOfDay(utcZone).toInstant()
        )
        val clock = FixedClockProvider(date, referenceInstant)
        assertEquals(ReminderStatus.COMPLETED, CalculateReminderStatusUseCase(clock)(memory, completedReminder))
    }

    @Test
    fun `completion while overdue sets status to COMPLETED permanently`() {
        val eventDate = LocalDate.of(2026, 9, 10)
        val currentDate = LocalDate.of(2026, 9, 15)
        val memory = buildMemory(eventDate = eventDate)
        val completedReminder = buildReminder(
            reminderStartDate = eventDate.minusDays(3),
            completedAt = currentDate.atStartOfDay(utcZone).toInstant()
        )
        val clock = FixedClockProvider(currentDate, referenceInstant)
        assertEquals(ReminderStatus.COMPLETED, CalculateReminderStatusUseCase(clock)(memory, completedReminder))
    }

    @Test
    fun `repeated state evaluation is purely deterministic`() {
        val eventDate = LocalDate.of(2026, 9, 20)
        val reminderDate = LocalDate.of(2026, 9, 15)
        val memory = buildMemory(eventDate = eventDate)
        val reminder = buildReminder(reminderStartDate = reminderDate)

        val clock = FixedClockProvider(LocalDate.of(2026, 9, 16), referenceInstant)
        val calculate = CalculateReminderStatusUseCase(clock)

        val firstRun = calculate(memory, reminder)
        val secondRun = calculate(memory, reminder)
        val thirdRun = calculate(memory, reminder)

        assertEquals(ReminderStatus.REMINDER_ACTIVE, firstRun)
        assertEquals(firstRun, secondRun)
        assertEquals(secondRun, thirdRun)
    }

    @Test
    fun `composite MemoryWithReminders evaluates status from active reminder`() {
        val eventDate = LocalDate.of(2026, 9, 25)
        val memory = buildMemory(eventDate = eventDate)
        val completedOldReminder = buildReminder(
            id = "rem-old",
            reminderStartDate = eventDate.minusDays(10),
            completedAt = referenceInstant
        )
        val activeNewReminder = buildReminder(
            id = "rem-active",
            reminderStartDate = eventDate.minusDays(2)
        )
        val composite = MemoryWithReminders(
            memory = memory,
            reminders = listOf(completedOldReminder, activeNewReminder)
        )

        val clock = FixedClockProvider(eventDate.minusDays(1), referenceInstant)
        val calculate = CalculateReminderStatusUseCase(clock)
        val status = calculate(composite)

        assertEquals(ReminderStatus.REMINDER_ACTIVE, status)
    }

    @Test
    fun `eventType variants are preserved correctly across memory models`() {
        EventType.values().forEach { type ->
            val memory = buildMemory(
                eventType = type,
                eventDate = LocalDate.of(2026, 10, 1)
            )
            assertEquals(type, memory.eventType)
        }
    }
}
