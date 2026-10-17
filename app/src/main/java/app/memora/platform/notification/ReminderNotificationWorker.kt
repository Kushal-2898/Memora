package app.memora.platform.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.memora.MainActivity
import app.memora.MemoraApplication
import app.memora.R
import app.memora.domain.model.ReminderStatus
import app.memora.domain.scheduler.SystemClockProvider
import app.memora.domain.usecase.CalculateReminderStatusUseCase
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * WorkManager worker executing reminder notification delivery.
 *
 * Re-evaluates reminder status dynamically against current date to ensure:
 * - Completed reminders do not fire notifications.
 * - Active reminders specify remaining days.
 * - Due Today and Overdue reminders produce accurate message copy.
 */
class ReminderNotificationWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_MEMORY_ID = "memoryId"
        const val KEY_REMINDER_ID = "reminderId"
        const val KEY_TITLE = "title"
        const val KEY_EVENT_DATE = "eventDate"
        const val KEY_TIME_ZONE_ID = "timeZoneId"
    }

    override suspend fun doWork(): Result {
        val memoryId = inputData.getString(KEY_MEMORY_ID) ?: ""
        val reminderId = inputData.getString(KEY_REMINDER_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: ""
        val eventDateStr = inputData.getString(KEY_EVENT_DATE) ?: return Result.failure()
        val timeZoneId = inputData.getString(KEY_TIME_ZONE_ID) ?: ZoneId.systemDefault().id

        val eventDate = try {
            LocalDate.parse(eventDateStr)
        } catch (e: Exception) {
            return Result.failure()
        }

        val app = applicationContext as? MemoraApplication
        val container = app?.container

        val calculateStatusUseCase = container?.calculateReminderStatusUseCase
            ?: CalculateReminderStatusUseCase(container?.clockProvider ?: SystemClockProvider())

        val reminder = container?.reminderRepository?.getReminder(reminderId)
        // A reminder deleted from Room must never fall through to a synthetic notification.
        // This also makes deletion safe even if cancellation races with WorkManager delivery.
        if (reminder == null) {
            return Result.success()
        }
        val memory = if (memoryId.isNotBlank()) {
            container?.memoryRepository?.getMemory(memoryId)
        } else {
            reminder?.let { container?.memoryRepository?.getMemory(it.memoryId) }
        }

        val status: ReminderStatus = if (reminder != null && memory != null) {
            calculateStatusUseCase(memory, reminder)
        } else if (reminder != null) {
            calculateStatusUseCase(
                eventDate = eventDate,
                reminderStartDate = reminder.reminderStartDate,
                completedAt = reminder.completedAt,
                timeZoneId = timeZoneId,
            )
        } else {
            calculateStatusUseCase(
                eventDate = eventDate,
                reminderStartDate = eventDate,
                completedAt = null,
                timeZoneId = timeZoneId,
            )
        }

        // COMPLETED → return success, do nothing
        if (status == ReminderStatus.COMPLETED) {
            return Result.success()
        }

        val effectiveTitle = title.ifBlank { memory?.title ?: "Reminder" }
        val zone = runCatching { ZoneId.of(timeZoneId) }.getOrElse { ZoneId.systemDefault() }
        val clock = container?.clockProvider ?: SystemClockProvider()
        val today = clock.today(zone)

        val notificationMessage = when (status) {
            ReminderStatus.REMINDER_ACTIVE -> {
                val daysUntil = ChronoUnit.DAYS.between(today, eventDate)
                "Reminder: $effectiveTitle — due in $daysUntil days"
            }
            ReminderStatus.DUE_TODAY -> "Due today: $effectiveTitle"
            ReminderStatus.OVERDUE -> "Overdue: $effectiveTitle"
            ReminderStatus.UPCOMING -> return Result.success()
            ReminderStatus.COMPLETED -> return Result.success()
        }

        // Schedule the next daily occurrence BEFORE posting today's notification. If scheduling
        // fails, WorkManager retries without duplicating today's notification.
        //
        // Product requirement: reminders continue daily until the Memory is completed.
        // The COMPLETED guard earlier in doWork() is the sole termination condition.
        // Reschedule while uncompleted so future days (including transitions to OVERDUE) fire correctly.
        val shouldReschedule = status == ReminderStatus.REMINDER_ACTIVE ||
            status == ReminderStatus.DUE_TODAY ||
            status == ReminderStatus.OVERDUE
        if (shouldReschedule) {
            val nextDate = today.plusDays(1)
            val nextTrigger = nextDate
                .atTime(reminder.reminderTime ?: java.time.LocalTime.of(9, 0))
                .atZone(zone)
                .toInstant()
            val scheduler = container?.notificationScheduler ?: return Result.retry()
            val nextResult = scheduler.scheduleReminder(
                reminderId = reminderId,
                triggerAt = nextTrigger,
                title = effectiveTitle,
            )
            if (nextResult.isFailure) return Result.retry()
        }

        sendNotification(reminderId = reminderId, message = notificationMessage)

        return Result.success()
    }

    private fun sendNotification(reminderId: String, message: String) {
        val notificationId = reminderId.hashCode()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val channelId = MemoraApplication.REMINDER_CHANNEL_ID
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Notification permission was not granted by the user at runtime
        }
    }
}
