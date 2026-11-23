package app.memora.platform.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.memora.MainActivity
import app.memora.MemoraApplication
import app.memora.R
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

sealed class ScanNudgeDecision {
    data object Skip : ScanNudgeDecision()
    data class Notify(val title: String, val body: String) : ScanNudgeDecision()
}

/**
 * Isolated WorkManager worker for gentle habit check-ins to scan newly bought items.
 * Kept completely separate from AlarmManager item expiry reminders so neither can interfere.
 */
class ScanNudgeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MemoraApplication ?: return Result.success()
        val container = app.container
        val prefs = container.onboardingPreferences
        val isEnabled = prefs.isScanNudgeEnabled()
        val hasCompletedOnboarding = prefs.hasCompletedOnboarding()

        if (!isEnabled || !hasCompletedOnboarding) {
            return Result.success()
        }

        val allItems = container.memoryRepository.observeMemories().first()
        val activeReminderCount = allItems.sumOf { item -> item.reminders.count { it.completedAt == null } }
        val lastItemAddedAt = allItems.maxOfOrNull { it.memory.createdAt }
        val now = container.clockProvider.now()

        val decision = evaluateScanNudge(
            hasCompletedOnboarding = hasCompletedOnboarding,
            isEnabled = isEnabled,
            activeReminderCount = activeReminderCount,
            lastItemAddedAt = lastItemAddedAt,
            now = now,
        )

        if (decision is ScanNudgeDecision.Notify) {
            postNotification(applicationContext, decision.title, decision.body)
        }

        // Always fall back to once-weekly Sunday 7:00 PM cadence so empty vaults never nag daily
        ScanNudgeScheduler.scheduleNextWeekly(applicationContext, now = now)
        return Result.success()
    }

    private fun postNotification(context: Context, title: String, body: String) {
        val openScannerIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(KEY_OPEN_SCANNER, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            SCAN_NUDGE_NOTIFICATION_ID,
            openScannerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, MemoraApplication.SCAN_NUDGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(SCAN_NUDGE_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "memora_scan_nudge"
        const val KEY_OPEN_SCANNER = "open_scanner"
        const val SCAN_NUDGE_NOTIFICATION_ID = 900101
        const val INACTIVITY_THRESHOLD_DAYS = 4L
        val NUDGE_TIME: LocalTime = LocalTime.of(19, 0)

        fun evaluateScanNudge(
            hasCompletedOnboarding: Boolean,
            isEnabled: Boolean,
            activeReminderCount: Int,
            lastItemAddedAt: Instant?,
            now: Instant,
        ): ScanNudgeDecision {
            if (!hasCompletedOnboarding || !isEnabled) {
                return ScanNudgeDecision.Skip
            }
            if (activeReminderCount == 0) {
                return ScanNudgeDecision.Notify(
                    title = "Your Memora vault is empty",
                    body = "Scan a medicine strip, grocery pack, or bill in 5 seconds.",
                )
            }
            if (lastItemAddedAt != null && ChronoUnit.DAYS.between(lastItemAddedAt, now) < INACTIVITY_THRESHOLD_DAYS) {
                return ScanNudgeDecision.Skip
            }
            return ScanNudgeDecision.Notify(
                title = "Restocked this week?",
                body = "Scan newly bought groceries or medicines in 5 seconds so nothing expires.",
            )
        }

        fun computeNextDelayMillis(
            now: Instant,
            zone: ZoneId = ZoneId.systemDefault(),
            nextEveningForEmptyVault: Boolean = false,
        ): Long {
            val zonedNow = now.atZone(zone)
            val today = zonedNow.toLocalDate()
            val targetInstant = if (nextEveningForEmptyVault) {
                today.plusDays(1).atTime(NUDGE_TIME).atZone(zone).toInstant()
            } else {
                val thisSunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                    .atTime(NUDGE_TIME)
                    .atZone(zone)
                    .toInstant()
                if (thisSunday.isAfter(now.plusSeconds(300))) {
                    thisSunday
                } else {
                    today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                        .atTime(NUDGE_TIME)
                        .atZone(zone)
                        .toInstant()
                }
            }
            return Duration.between(now, targetInstant).toMillis().coerceAtLeast(60_000L)
        }
    }
}

object ScanNudgeScheduler {
    fun scheduleInitial(
        context: Context,
        isVaultEmpty: Boolean = true,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val delayMs = ScanNudgeWorker.computeNextDelayMillis(
            now = now,
            zone = zone,
            nextEveningForEmptyVault = isVaultEmpty,
        )
        enqueue(context, delayMs, ExistingWorkPolicy.KEEP)
    }

    fun scheduleNextWeekly(
        context: Context,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val delayMs = ScanNudgeWorker.computeNextDelayMillis(
            now = now,
            zone = zone,
            nextEveningForEmptyVault = false,
        )
        enqueue(context, delayMs, ExistingWorkPolicy.REPLACE)
    }

    fun cancel(context: Context) {
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(ScanNudgeWorker.UNIQUE_WORK_NAME)
        }
    }

    private fun enqueue(context: Context, delayMs: Long, policy: ExistingWorkPolicy) {
        runCatching {
            val request = OneTimeWorkRequestBuilder<ScanNudgeWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ScanNudgeWorker.UNIQUE_WORK_NAME,
                policy,
                request,
            )
        }
    }
}
