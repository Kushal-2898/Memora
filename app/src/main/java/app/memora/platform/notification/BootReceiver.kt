package app.memora.platform.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.memora.MemoraApplication
import app.memora.platform.logging.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Recovers and re-arms exact alarms with AlarmManager upon device reboot (BOOT_COMPLETED)
 * or application package updates (MY_PACKAGE_REPLACED).
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        var coroutineContext: kotlin.coroutines.CoroutineContext = Dispatchers.IO
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val pendingResult = try { goAsync() } catch (e: Exception) { null }

        CoroutineScope(coroutineContext).launch {
            try {
                execute(context, intent)
            } catch (e: Exception) {
                AppLog.e("BootReceiver", "Failed to restore alarms", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    suspend fun execute(context: Context, intent: Intent) {
        val action = intent.action
        val app = context.applicationContext as? MemoraApplication
        val container = app?.container ?: return

        AppLog.i("BootReceiver", "Re-arming alarms following $action")
        val memoriesWithReminders = container.memoryRepository.observeMemories().first()
        val scheduler = container.notificationScheduler

        for (item in memoriesWithReminders) {
            for (reminder in item.reminders) {
                if (reminder.completedAt == null) {
                    scheduler.scheduleReminder(
                        memoryId = item.memory.id,
                        reminderId = reminder.id,
                        title = item.memory.title,
                        reminderStartDate = reminder.reminderStartDate,
                        eventDate = item.memory.eventDate,
                        timeZoneId = item.memory.timeZoneId,
                        reminderTime = reminder.reminderTime ?: java.time.LocalTime.of(9, 0),
                    )
                }
            }
        }
    }
}
