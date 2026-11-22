package app.memora.platform.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
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
        const val ACTION_COMPLETE_REMINDER = "app.memora.ACTION_COMPLETE_REMINDER"
        const val ACTION_SNOOZE_REMINDER = "app.memora.ACTION_SNOOZE_REMINDER"

        var coroutineContext: kotlin.coroutines.CoroutineContext = Dispatchers.IO
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_REMINDER && action != ACTION_COMPLETE_REMINDER && action != ACTION_SNOOZE_REMINDER) return

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
        val action = intent.action
        if (action == ACTION_COMPLETE_REMINDER) {
            val reminderId = intent.getStringExtra(KEY_REMINDER_ID) ?: return
            val app = context.applicationContext as? MemoraApplication
            app?.container?.completeReminderUseCase?.invoke(reminderId)
            app?.container?.notificationScheduler?.cancelReminder(reminderId)
            NotificationManagerCompat.from(context).cancel(reminderId.hashCode())
            return
        }

        if (action == ACTION_SNOOZE_REMINDER) {
            val reminderId = intent.getStringExtra(KEY_REMINDER_ID) ?: return
            val app = context.applicationContext as? MemoraApplication
            app?.container?.snoozeReminderUseCase?.invoke(reminderId, daysToSnooze = 0L, hoursToSnooze = 3L)
            NotificationManagerCompat.from(context).cancel(reminderId.hashCode())
            return
        }

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

            val nowTime = clock.now().atZone(zone).toLocalTime()
            val eveningTime = java.time.LocalTime.of(18, 0)

            val message = when (status) {
                ReminderStatus.REMINDER_ACTIVE -> if (daysUntil == 1L) "Reminder: $effectiveTitle — due tomorrow" else "Reminder: $effectiveTitle — due in $daysUntil days"
                ReminderStatus.DUE_TODAY -> {
                    if (nowTime.hour >= 17) {
                        "Action needed: $effectiveTitle expires today"
                    } else {
                        "Due today: $effectiveTitle"
                    }
                }
                ReminderStatus.OVERDUE -> "Overdue: $effectiveTitle"
                else -> null
            } ?: return

            sendNotification(context, reminderId, message, memory.id)

            // Daily recurrence (DEF-009): Reschedule next day's alarm while uncompleted (capped at 7 days overdue)
            val daysOverdue = ChronoUnit.DAYS.between(memory.eventDate, today)
            val shouldReschedule = status == ReminderStatus.REMINDER_ACTIVE ||
                status == ReminderStatus.DUE_TODAY ||
                (status == ReminderStatus.OVERDUE && daysOverdue <= 7L)

            if (shouldReschedule) {
                // If the item is DUE_TODAY and this is the morning alert (before 18:00),
                // schedule an evening follow-up check for 18:00 today. Otherwise, schedule next morning.
                val (nextDate, nextTime) = if (status == ReminderStatus.DUE_TODAY && nowTime.isBefore(eveningTime)) {
                    today to eveningTime
                } else {
                    val defaultTime = app?.container?.onboardingPreferences?.getDefaultReminderTime() ?: java.time.LocalTime.of(9, 0)
                    today.plusDays(1) to defaultTime
                }

                container.notificationScheduler.scheduleReminder(
                    memoryId = memory.id,
                    reminderId = reminder.id,
                    title = memory.title,
                    reminderStartDate = nextDate,
                    eventDate = memory.eventDate,
                    timeZoneId = memory.timeZoneId,
                    reminderTime = nextTime,
                )
            }
        } else {
            // Fallback when container is null (e.g. isolated test or minimal context)
            val zone = runCatching { ZoneId.of(timeZoneId) }.getOrElse { ZoneId.systemDefault() }
            val now = LocalDate.now(zone)
            val nowTime = java.time.LocalTime.now(zone)
            val eventDate = runCatching { LocalDate.parse(eventDateStr) }.getOrNull() ?: now
            val daysUntil = ChronoUnit.DAYS.between(now, eventDate)

            val message = when {
                daysUntil < 0L -> "Overdue: $title"
                daysUntil == 0L -> if (nowTime.hour >= 17) "Action needed: $title expires today" else "Due today: $title"
                else -> if (daysUntil == 1L) "Reminder: $title — due tomorrow" else "Reminder: $title — due in $daysUntil days"
            }

            sendNotification(context, reminderId, message, memoryId)
        }
    }

    private fun sendNotification(context: Context, reminderId: String, message: String, memoryId: String = "") {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (memoryId.isNotBlank()) putExtra(KEY_MEMORY_ID, memoryId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_COMPLETE_REMINDER
            putExtra(KEY_REMINDER_ID, reminderId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId + "_done").hashCode(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_REMINDER
            putExtra(KEY_REMINDER_ID, reminderId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId + "_snooze").hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(context, MemoraApplication.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message)
            .setContentText("Tap to view or mark done in Memora")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(0, "Mark Done", completePendingIntent)
            .addAction(0, "Remind Later", snoozePendingIntent)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(reminderId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission POST_NOTIFICATIONS not granted
        }
    }
}
