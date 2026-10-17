package app.memora

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import app.memora.domain.model.EventType
import app.memora.domain.scheduler.ClockProvider
import app.memora.domain.model.Memory
import app.memora.domain.model.Reminder
import app.memora.platform.notification.BootReceiver
import app.memora.platform.notification.MemoraNotificationScheduler
import app.memora.platform.notification.ReminderAlarmReceiver
import app.memora.platform.notification.ReminderNotificationWorker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = MemoraApplication::class)
class Stage6NotificationTest {

    private lateinit var app: MemoraApplication
    private lateinit var notificationManager: NotificationManager
    private lateinit var alarmManager: AlarmManager

    private class FixedTestClockProvider(private val fixedInstant: Instant) : ClockProvider {
        override fun now(): Instant = fixedInstant
        override fun today(zoneId: ZoneId): LocalDate = fixedInstant.atZone(zoneId).toLocalDate()
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<MemoraApplication>()
        notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    @Test
    fun `channel memora_reminders is created in MemoraApplication`() {
        val channel = notificationManager.getNotificationChannel("memora_reminders")
        assertNotNull("Channel memora_reminders should exist", channel)
        assertEquals("Memora Reminders", channel.name)
    }

    @Test
    fun `MemoraNotificationScheduler schedules alarm with AlarmManager`() = runTest {
        val fixedInstant = Instant.parse("2026-09-05T10:00:00Z")
        val fixedClock = FixedTestClockProvider(fixedInstant)
        val scheduler = MemoraNotificationScheduler(context = app, clockProvider = fixedClock)
        val reminderId = "rem_sched_123"
        val result = scheduler.scheduleReminder(
            memoryId = "mem_123", reminderId = reminderId, title = "Water Bill",
            reminderStartDate = LocalDate.of(2026, 9, 8), eventDate = LocalDate.of(2026, 9, 15), timeZoneId = "UTC",
        )
        assertTrue(result.isSuccess)
        val shadowAlarm = shadowOf(alarmManager)
        assertEquals(1, shadowAlarm.scheduledAlarms.size)
    }

    @Test
    fun `MemoraNotificationScheduler cancelReminder cancels alarm`() = runTest {
        val fixedInstant = Instant.parse("2026-09-05T10:00:00Z")
        val fixedClock = FixedTestClockProvider(fixedInstant)
        val scheduler = MemoraNotificationScheduler(context = app, clockProvider = fixedClock)
        val reminderId = "rem_cancel_123"
        scheduler.scheduleReminder(
            memoryId = "mem_cancel", reminderId = reminderId, title = "Cancel Me",
            reminderStartDate = LocalDate.of(2026, 9, 8), eventDate = LocalDate.of(2026, 9, 10), timeZoneId = "UTC",
        )
        val cancelResult = scheduler.cancelReminder(reminderId)
        assertTrue(cancelResult.isSuccess)
        val shadowAlarm = shadowOf(alarmManager)
        assertEquals(0, shadowAlarm.scheduledAlarms.size)
    }

    @Test
    fun `ReminderAlarmReceiver posts to memora_reminders channel with correct message`() = runTest {
        val today = LocalDate.now(ZoneId.of("UTC"))
        val eventDate = today.plusDays(4)
        val memoryId = app.container.createMemoryUseCase(
            title = "Car Insurance", eventType = EventType.DUE_DATE, eventDate = eventDate,
        ).getOrThrow()
        val reminderId = app.container.createReminderUseCase(
            memoryId = memoryId, reminderStartDate = today.minusDays(1),
        ).getOrThrow()

        val intent = Intent(ReminderAlarmReceiver.ACTION_REMINDER).apply {
            putExtra(ReminderAlarmReceiver.KEY_MEMORY_ID, memoryId)
            putExtra(ReminderAlarmReceiver.KEY_REMINDER_ID, reminderId)
            putExtra(ReminderAlarmReceiver.KEY_TITLE, "Car Insurance")
            putExtra(ReminderAlarmReceiver.KEY_EVENT_DATE, eventDate.toString())
            putExtra(ReminderAlarmReceiver.KEY_TIME_ZONE_ID, "UTC")
        }

        ReminderAlarmReceiver().execute(app, intent)

        val notification = shadowOf(notificationManager).getNotification(reminderId.hashCode())
        assertNotNull("Notification should be posted", notification)
        assertEquals(MemoraApplication.REMINDER_CHANNEL_ID, notification.channelId)
        val shadowNotification = shadowOf(notification)
        assertEquals("Reminder: Car Insurance — due in 4 days", shadowNotification.contentTitle)
    }

    @Test
    fun `ReminderAlarmReceiver suppresses notification if reminder is completed`() = runTest {
        val today = LocalDate.now(ZoneId.of("UTC"))
        val memoryId = app.container.createMemoryUseCase(
            title = "Completed Bill", eventType = EventType.DUE_DATE, eventDate = today.plusDays(2),
        ).getOrThrow()
        val reminderId = app.container.createReminderUseCase(
            memoryId = memoryId, reminderStartDate = today.minusDays(1),
        ).getOrThrow()
        app.container.completeReminderUseCase(reminderId)

        val intent = Intent(ReminderAlarmReceiver.ACTION_REMINDER).apply {
            putExtra(ReminderAlarmReceiver.KEY_MEMORY_ID, memoryId)
            putExtra(ReminderAlarmReceiver.KEY_REMINDER_ID, reminderId)
            putExtra(ReminderAlarmReceiver.KEY_TITLE, "Completed Bill")
            putExtra(ReminderAlarmReceiver.KEY_EVENT_DATE, today.plusDays(2).toString())
            putExtra(ReminderAlarmReceiver.KEY_TIME_ZONE_ID, "UTC")
        }

        ReminderAlarmReceiver().execute(app, intent)

        val notification = shadowOf(notificationManager).getNotification(reminderId.hashCode())
        assertNull("Completed reminder must NOT post notification", notification)
    }

    @Test
    fun `ReminderAlarmReceiver reschedules next day alarm while active`() = runTest {
        val today = LocalDate.now(ZoneId.of("UTC"))
        val eventDate = today.plusDays(5)
        val memoryId = app.container.createMemoryUseCase(
            title = "Daily Recurring", eventType = EventType.DUE_DATE, eventDate = eventDate,
        ).getOrThrow()
        val reminderId = app.container.createReminderUseCase(
            memoryId = memoryId, reminderStartDate = today.minusDays(1),
        ).getOrThrow()

        val intent = Intent(ReminderAlarmReceiver.ACTION_REMINDER).apply {
            putExtra(ReminderAlarmReceiver.KEY_MEMORY_ID, memoryId)
            putExtra(ReminderAlarmReceiver.KEY_REMINDER_ID, reminderId)
            putExtra(ReminderAlarmReceiver.KEY_TITLE, "Daily Recurring")
            putExtra(ReminderAlarmReceiver.KEY_EVENT_DATE, eventDate.toString())
            putExtra(ReminderAlarmReceiver.KEY_TIME_ZONE_ID, "UTC")
        }

        shadowOf(alarmManager).scheduledAlarms.clear()

        ReminderAlarmReceiver().execute(app, intent)

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertTrue("Daily recurrence should schedule next alarm", alarms.isNotEmpty())
    }

    @Test
    fun `BootReceiver re-arms uncompleted alarms on BOOT_COMPLETED`() = runTest {
        val today = LocalDate.now(ZoneId.of("UTC"))
        val memoryId = app.container.createMemoryUseCase(
            title = "Boot Recovery Task", eventType = EventType.OTHER, eventDate = today.plusDays(3),
        ).getOrThrow()
        val reminderId = app.container.createReminderUseCase(
            memoryId = memoryId, reminderStartDate = today,
        ).getOrThrow()

        shadowOf(alarmManager).scheduledAlarms.clear()

        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        BootReceiver().execute(app, intent)

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertTrue("BootReceiver must re-arm exact alarms for uncompleted reminders", alarms.isNotEmpty())
    }

    @Test
    fun `SnoozeReminderUseCase postpones reminder and reschedules alarm`() = runTest {
        val today = LocalDate.now(ZoneId.of("UTC"))
        val memoryId = app.container.createMemoryUseCase(
            title = "Snooze Test", eventType = EventType.OTHER, eventDate = today.plusDays(5),
            timeZoneId = "UTC",
        ).getOrThrow()
        val reminderId = app.container.createReminderUseCase(
            memoryId = memoryId, reminderStartDate = today,
        ).getOrThrow()

        shadowOf(alarmManager).scheduledAlarms.clear()

        val snoozeUseCase = app.container.snoozeReminderUseCase
        val snoozeResult = snoozeUseCase(reminderId, daysToSnooze = 2L)
        assertTrue(snoozeResult.isSuccess)

        val updated = app.container.reminderRepository.getReminder(reminderId)
        assertNotNull(updated)
        assertEquals(today.plusDays(2), updated!!.reminderStartDate)

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertTrue("Snoozing must reschedule alarm with AlarmManager", alarms.isNotEmpty())
    }

    @Test
    fun `ReminderNotificationWorker when COMPLETED returns success without notifying`() = runTest {
        val memoryId = app.container.createMemoryUseCase(
            title = "Electric Bill", eventType = EventType.DUE_DATE, eventDate = LocalDate.of(2026, 9, 10),
        ).getOrThrow()
        val reminderId = app.container.createReminderUseCase(
            memoryId = memoryId, reminderStartDate = LocalDate.of(2026, 9, 5),
        ).getOrThrow()
        app.container.completeReminderUseCase(reminderId)
        val inputData = workDataOf(
            ReminderNotificationWorker.KEY_MEMORY_ID to memoryId,
            ReminderNotificationWorker.KEY_REMINDER_ID to reminderId,
            ReminderNotificationWorker.KEY_TITLE to "Electric Bill",
            ReminderNotificationWorker.KEY_EVENT_DATE to "2026-09-10",
            ReminderNotificationWorker.KEY_TIME_ZONE_ID to "UTC",
        )
        val worker = TestListenableWorkerBuilder<ReminderNotificationWorker>(app).setInputData(inputData).build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        val notifications = shadowOf(notificationManager).allNotifications
        assertTrue("No notification should be posted for COMPLETED status", notifications.isEmpty())
    }

    @Test
    fun `ReminderNotificationWorker when REMINDER_ACTIVE notifies Reminder title due in X days`() = runTest {
        val today = LocalDate.now(ZoneId.of("UTC"))
        val eventDate = today.plusDays(4)
        val reminderStartDate = today.minusDays(1)
        val memoryId = app.container.createMemoryUseCase(
            title = "Car Insurance", eventType = EventType.DUE_DATE, eventDate = eventDate,
        ).getOrThrow()
        val reminderId = app.container.createReminderUseCase(
            memoryId = memoryId, reminderStartDate = reminderStartDate,
        ).getOrThrow()
        val inputData = workDataOf(
            ReminderNotificationWorker.KEY_MEMORY_ID to memoryId,
            ReminderNotificationWorker.KEY_REMINDER_ID to reminderId,
            ReminderNotificationWorker.KEY_TITLE to "Car Insurance",
            ReminderNotificationWorker.KEY_EVENT_DATE to eventDate.toString(),
            ReminderNotificationWorker.KEY_TIME_ZONE_ID to "UTC",
        )
        val worker = TestListenableWorkerBuilder<ReminderNotificationWorker>(app).setInputData(inputData).build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        val notification = shadowOf(notificationManager).getNotification(reminderId.hashCode())
        assertNotNull("Notification with id reminderId.hashCode() should be posted", notification)
        val shadowNotification = shadowOf(notification)
        assertEquals("Reminder: Car Insurance — due in 4 days", shadowNotification.contentTitle)
    }

    @Test
    fun `ReminderNotificationWorker when DUE_TODAY notifies Due today title`() = runTest {
        val fixedInstant = Instant.parse("2026-09-10T10:00:00Z")
        val fixedClock = FixedTestClockProvider(fixedInstant)
        app.container.clockProvider = fixedClock
        val today = fixedClock.today(ZoneId.of("UTC"))
        val memoryId = app.container.createMemoryUseCase(
            title = "Project Submission", eventType = EventType.OTHER, eventDate = today,
        ).getOrThrow()
        val reminderId = app.container.createReminderUseCase(
            memoryId = memoryId, reminderStartDate = today.minusDays(2),
        ).getOrThrow()
        val inputData = workDataOf(
            ReminderNotificationWorker.KEY_MEMORY_ID to memoryId,
            ReminderNotificationWorker.KEY_REMINDER_ID to reminderId,
            ReminderNotificationWorker.KEY_TITLE to "Project Submission",
            ReminderNotificationWorker.KEY_EVENT_DATE to today.toString(),
            ReminderNotificationWorker.KEY_TIME_ZONE_ID to "UTC",
        )
        val worker = TestListenableWorkerBuilder<ReminderNotificationWorker>(app).setInputData(inputData).build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        val notification = shadowOf(notificationManager).getNotification(reminderId.hashCode())
        assertNotNull(notification)
        val shadowNotification = shadowOf(notification)
        assertEquals("Due today: Project Submission", shadowNotification.contentTitle)
    }

    @Test
    fun `ReminderNotificationWorker when OVERDUE notifies Overdue title`() = runTest {
        val today = LocalDate.now(ZoneId.of("UTC"))
        val eventDate = today.minusDays(2)
        val memoryId = app.container.createMemoryUseCase(
            title = "Library Book", eventType = EventType.OTHER, eventDate = eventDate,
        ).getOrThrow()
        val reminderId = app.container.createReminderUseCase(
            memoryId = memoryId, reminderStartDate = eventDate.minusDays(5),
        ).getOrThrow()
        val inputData = workDataOf(
            ReminderNotificationWorker.KEY_MEMORY_ID to memoryId,
            ReminderNotificationWorker.KEY_REMINDER_ID to reminderId,
            ReminderNotificationWorker.KEY_TITLE to "Library Book",
            ReminderNotificationWorker.KEY_EVENT_DATE to eventDate.toString(),
            ReminderNotificationWorker.KEY_TIME_ZONE_ID to "UTC",
        )
        val worker = TestListenableWorkerBuilder<ReminderNotificationWorker>(app).setInputData(inputData).build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        val notification = shadowOf(notificationManager).getNotification(reminderId.hashCode())
        assertNotNull(notification)
        val shadowNotification = shadowOf(notification)
        assertEquals("Overdue: Library Book", shadowNotification.contentTitle)
    }

    @Test
    fun `ReminderNotificationWorker re-enqueues next day while reminder is active`() = runTest {
        val fixedInstant = Instant.parse("2026-09-10T10:00:00Z")
        val fixedClock = FixedTestClockProvider(fixedInstant)
        val memoryId = app.container.createMemoryUseCase(
            title = "Daily Reminder", eventType = EventType.DUE_DATE,
            eventDate = LocalDate.of(2026, 9, 15), timeZoneId = "UTC",
        ).getOrThrow()
        val reminderId = app.container.createReminderUseCase(
            memoryId = memoryId, reminderStartDate = LocalDate.of(2026, 9, 10),
        ).getOrThrow()
        val input = workDataOf(
            ReminderNotificationWorker.KEY_MEMORY_ID to memoryId,
            ReminderNotificationWorker.KEY_REMINDER_ID to reminderId,
            ReminderNotificationWorker.KEY_TITLE to "Daily Reminder",
            ReminderNotificationWorker.KEY_EVENT_DATE to "2026-09-15",
            ReminderNotificationWorker.KEY_TIME_ZONE_ID to "UTC",
        )
        shadowOf(alarmManager).scheduledAlarms.clear()
        val worker = TestListenableWorkerBuilder<ReminderNotificationWorker>(app).setInputData(input).build()
        assertEquals(ListenableWorker.Result.success(), worker.startWork().get())
        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertTrue("Alarm should be scheduled for next day", alarms.isNotEmpty())
    }

    @Test
    fun `ReminderNotificationWorker missing reminder does not notify`() = runTest {
        val reminderId = "missing_reminder"
        val input = workDataOf(
            ReminderNotificationWorker.KEY_MEMORY_ID to "missing_memory",
            ReminderNotificationWorker.KEY_REMINDER_ID to reminderId,
            ReminderNotificationWorker.KEY_TITLE to "Should Not Notify",
            ReminderNotificationWorker.KEY_EVENT_DATE to "2026-09-15",
            ReminderNotificationWorker.KEY_TIME_ZONE_ID to "UTC",
        )
        val worker = TestListenableWorkerBuilder<ReminderNotificationWorker>(app).setInputData(input).build()
        assertEquals(ListenableWorker.Result.success(), worker.startWork().get())
        assertNull(shadowOf(notificationManager).getNotification(reminderId.hashCode()))
    }

    @Test
    fun `ReminderNotificationWorker due today reschedules to next day for overdue tracking`() = runTest {
        val fixedInstant = Instant.parse("2026-09-10T10:00:00Z")
        val fixedClock = FixedTestClockProvider(fixedInstant)
        app.container.clockProvider = fixedClock
        val today = fixedClock.today(ZoneId.of("UTC"))
        val memoryId = app.container.createMemoryUseCase(
            title = "Due Today Stop", eventType = EventType.DUE_DATE, eventDate = today,
        ).getOrThrow()
        val reminderId = app.container.createReminderUseCase(
            memoryId = memoryId, reminderStartDate = today.minusDays(2),
        ).getOrThrow()
        val inputData = workDataOf(
            ReminderNotificationWorker.KEY_MEMORY_ID to memoryId,
            ReminderNotificationWorker.KEY_REMINDER_ID to reminderId,
            ReminderNotificationWorker.KEY_TITLE to "Due Today Stop",
            ReminderNotificationWorker.KEY_EVENT_DATE to today.toString(),
            ReminderNotificationWorker.KEY_TIME_ZONE_ID to "UTC",
        )
        shadowOf(alarmManager).scheduledAlarms.clear()
        val worker = TestListenableWorkerBuilder<ReminderNotificationWorker>(app).setInputData(inputData).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertTrue("Uncompleted DUE_TODAY reminder must schedule next day alarm", alarms.isNotEmpty())
    }

    @Test
    fun `ReminderNotificationWorker deleted reminder does not post notification`() = runTest {
        val reminderId = "deleted-reminder"
        val inputData = workDataOf(
            ReminderNotificationWorker.KEY_MEMORY_ID to "deleted-memory",
            ReminderNotificationWorker.KEY_REMINDER_ID to reminderId,
            ReminderNotificationWorker.KEY_TITLE to "Deleted",
            ReminderNotificationWorker.KEY_EVENT_DATE to LocalDate.now(ZoneId.of("UTC")).toString(),
            ReminderNotificationWorker.KEY_TIME_ZONE_ID to "UTC",
        )
        val worker = TestListenableWorkerBuilder<ReminderNotificationWorker>(app).setInputData(inputData).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertNull(shadowOf(notificationManager).getNotification(reminderId.hashCode()))
    }
}
