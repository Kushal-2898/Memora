package app.memora.platform.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.memora.MainActivity
import app.memora.MemoraApplication
import app.memora.R
import app.memora.platform.logging.AppLog
import app.memora.domain.model.ReminderStatus
import app.memora.domain.scheduler.SystemClockProvider
import app.memora.domain.usecase.CalculateReminderStatusUseCase
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {
    companion object {
        const val KEY_MEMORY_ID = "memoryId"
        const val KEY_REMINDER_ID = "reminderId"
        const val KEY_TITLE = "title"
        const val KEY_EVENT_DATE = "eventDate"
        const val KEY_TIME_ZONE_ID = "timeZoneId"
        const val ACTION_REMINDER = "app.memora.ACTION_REMINDER"

        var coroutineContext: kotlin.coroutines.CoroutineContext = Dispatchers.IO
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return

        val pendingResult = try { goAsync() } catch (e: Exception) { null }

        CoroutineScope(coroutineContext).launch {
            try {
                execute(context, intent)
            } catch (e: Exception) {
                AppLog.e("ReminderAlarmReceiver", "Failed onReceive processing", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    suspend fun execute(context: Context, intent: Intent) {
        val memoryId = intent.getStringExtra(KEY_MEMORY_ID) ?: ""
        val reminderId = intent.getStringExtra(KEY_REMINDER_ID) ?: return
        val title = intent.getStringExtra(KEY_TITLE) ?: "Memora Reminder"
        val eventDateStr = intent.getStringExtra(KEY_EVENT_DATE) ?: return
        val timeZoneId = intent.getStringExtra(KEY_TIME_ZONE_ID) ?: ZoneId.systemDefault().id

        val app = context.applicationContext as? MemoraApplication
        val container = app?.container

        if (container != null) {
            val reminder = container.reminderRepository.getReminder(reminderId)
            // Deleted or completed reminders must not post notifications
            if (reminder == null || reminder.completedAt != null) {
                return
            }

            val memory = if (memoryId.isNotBlank()) {
                container.memoryRepository.getMemory(memoryId)
            } else {
                container.memoryRepository.getMemory(reminder.memoryId)
            }
            if (memory == null) {
                return
            }

            val calculateStatusUseCase = container.calculateReminderStatusUseCase
            val status = calculateStatusUseCase(memory, reminder)

            if (status == ReminderStatus.COMPLETED || status == ReminderStatus.UPCOMING) {
                return
            }

            val effectiveTitle = memory.title.ifBlank { title }
            val zone = runCatching { ZoneId.of(timeZoneId) }.getOrElse { ZoneId.systemDefault() }
            val clock = container.clockProvider
            val today = clock.today(zone)
            val daysUntil = ChronoUnit.DAYS.between(today, memory.eventDate)

            val message = when (status) {
                ReminderStatus.REMINDER_ACTIVE -> "Reminder: $effectiveTitle — due in $daysUntil days"
                ReminderStatus.DUE_TODAY -> "Due today: $effectiveTitle"
                ReminderStatus.OVERDUE -> "Overdue: $effectiveTitle"
                else -> null
            } ?: return

            sendNotification(context, reminderId, message)

            // Daily recurrence (DEF-009): Reschedule next day's alarm while uncompleted
            val nextDate = today.plusDays(1)
            val reminderTime = reminder.reminderTime ?: java.time.LocalTime.of(9, 0)
            container.notificationScheduler.scheduleReminder(
                memoryId = memory.id,
                reminderId = reminder.id,
                title = memory.title,
                reminderStartDate = nextDate,
                eventDate = memory.eventDate,
                timeZoneId = memory.timeZoneId,
                reminderTime = reminderTime,
            )
        } else {
            // Fallback when container is null (e.g. isolated test or minimal context)
            val zone = runCatching { ZoneId.of(timeZoneId) }.getOrElse { ZoneId.systemDefault() }
            val now = LocalDate.now(zone)
            val eventDate = runCatching { LocalDate.parse(eventDateStr) }.getOrNull() ?: now
            val daysUntil = ChronoUnit.DAYS.between(now, eventDate)

            val message = when {
                daysUntil < 0L -> "Overdue: $title"
                daysUntil == 0L -> "Due today: $title"
                else -> "Reminder: $title — due in $daysUntil days"
            }

            sendNotification(context, reminderId, message)
        }
    }

    private fun sendNotification(context: Context, reminderId: String, message: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MemoraApplication.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(reminderId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission POST_NOTIFICATIONS not granted
        }
    }
}
