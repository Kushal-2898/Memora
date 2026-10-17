package app.memora.platform.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import app.memora.domain.scheduler.ClockProvider
import app.memora.domain.scheduler.SystemClockProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Production AlarmManager implementation of [NotificationScheduler].
 *
 * Uses EXACT_ALARM permissions on Android 14+ to guarantee reminder
 * delivery exactly at 9:00 AM, circumventing aggressive battery optimizers
 * like Samsung/Xiaomi that otherwise kill inexact WorkManager tasks.
 */
class MemoraNotificationScheduler(
    private val context: Context,
    private val clockProvider: ClockProvider = SystemClockProvider(),
) : NotificationScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override suspend fun scheduleReminder(
        memoryId: String,
        reminderId: String,
        title: String,
        reminderStartDate: LocalDate,
        eventDate: LocalDate,
        timeZoneId: String,
        reminderTime: LocalTime,
    ): Result<Unit> {
        val start = System.currentTimeMillis()
        return runCatching {
            if (!areNotificationsEnabled) {
                app.memora.platform.logging.AppLog.recordNotificationOperation("scheduleReminder", System.currentTimeMillis() - start, false, errorCategory = app.memora.platform.logging.DiagnosticErrorCategory.PERMISSION_ERROR)
            }
            val zone = runCatching { ZoneId.of(timeZoneId) }.getOrElse { ZoneId.systemDefault() }
            val triggerAt = reminderStartDate.atTime(reminderTime).atZone(zone).toInstant()
            
            scheduleExactAlarm(reminderId, title, memoryId, eventDate.toString(), timeZoneId, triggerAt.toEpochMilli())
            Unit
        }.onSuccess {
            app.memora.platform.logging.AppLog.recordNotificationOperation("scheduleReminder", System.currentTimeMillis() - start, true)
        }.onFailure { err ->
            val cat = app.memora.platform.logging.DiagnosticErrorCategory.fromThrowable(err)
            app.memora.platform.logging.AppLog.recordNotificationOperation("scheduleReminder", System.currentTimeMillis() - start, false, errorCategory = cat)
        }
    }

    override suspend fun scheduleReminder(
        reminderId: String,
        triggerAt: Instant,
        title: String,
    ): Result<Unit> {
        val start = System.currentTimeMillis()
        return runCatching {
            val zone = ZoneId.systemDefault()
            val triggerDate = triggerAt.atZone(zone).toLocalDate()
            
            scheduleExactAlarm(reminderId, title, "", triggerDate.toString(), zone.id, triggerAt.toEpochMilli())
            Unit
        }.onSuccess {
            app.memora.platform.logging.AppLog.recordNotificationOperation("scheduleReminderInstant", System.currentTimeMillis() - start, true)
        }.onFailure { err ->
            val cat = app.memora.platform.logging.DiagnosticErrorCategory.fromThrowable(err)
            app.memora.platform.logging.AppLog.recordNotificationOperation("scheduleReminderInstant", System.currentTimeMillis() - start, false, errorCategory = cat)
        }
    }

    override suspend fun cancelReminder(reminderId: String): Result<Unit> {
        val start = System.currentTimeMillis()
        return runCatching {
            val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Unit
        }.onSuccess {
            app.memora.platform.logging.AppLog.recordNotificationOperation("cancelReminder", System.currentTimeMillis() - start, true)
        }.onFailure { err ->
            val cat = app.memora.platform.logging.DiagnosticErrorCategory.fromThrowable(err)
            app.memora.platform.logging.AppLog.recordNotificationOperation("cancelReminder", System.currentTimeMillis() - start, false, errorCategory = cat)
        }
    }

    private fun scheduleExactAlarm(
        reminderId: String,
        title: String,
        memoryId: String,
        eventDate: String,
        timeZoneId: String,
        triggerAtMillis: Long
    ) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REMINDER
            putExtra(ReminderAlarmReceiver.KEY_REMINDER_ID, reminderId)
            putExtra(ReminderAlarmReceiver.KEY_TITLE, title)
            putExtra(ReminderAlarmReceiver.KEY_MEMORY_ID, memoryId)
            putExtra(ReminderAlarmReceiver.KEY_EVENT_DATE, eventDate)
            putExtra(ReminderAlarmReceiver.KEY_TIME_ZONE_ID, timeZoneId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            // Fallback to inexact if permission denied on Android 14+
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    override val areNotificationsEnabled: Boolean
        get() = NotificationManagerCompat.from(context).areNotificationsEnabled()
}
