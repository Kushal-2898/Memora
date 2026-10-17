package app.memora.platform.notification

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

interface NotificationScheduler {
    suspend fun scheduleReminder(reminderId: String, triggerAt: Instant, title: String): Result<Unit>

    suspend fun scheduleReminder(
        memoryId: String,
        reminderId: String,
        title: String,
        reminderStartDate: LocalDate,
        eventDate: LocalDate,
        timeZoneId: String = ZoneId.systemDefault().id,
        reminderTime: LocalTime = LocalTime.of(9, 0),
    ): Result<Unit>

    suspend fun cancelReminder(reminderId: String): Result<Unit>
    val areNotificationsEnabled: Boolean
}

class Stage1NotificationScheduler : NotificationScheduler {
    override suspend fun scheduleReminder(reminderId: String, triggerAt: Instant, title: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Notification scheduling is enabled in later stages."))

    override suspend fun scheduleReminder(
        memoryId: String,
        reminderId: String,
        title: String,
        reminderStartDate: LocalDate,
        eventDate: LocalDate,
        timeZoneId: String,
        reminderTime: LocalTime,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Notification scheduling is enabled in later stages."))

    override suspend fun cancelReminder(reminderId: String): Result<Unit> = Result.success(Unit)
    override val areNotificationsEnabled: Boolean = false
}
