package app.memora

import app.memora.platform.notification.ScanNudgeDecision
import app.memora.platform.notification.ScanNudgeWorker
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanNudgeWorkerTest {

    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-11-22T19:00:00Z") // Sunday 7:00 PM UTC

    @Test
    fun `skips when onboarding not completed`() {
        val decision = ScanNudgeWorker.evaluateScanNudge(
            hasCompletedOnboarding = false,
            isEnabled = true,
            activeReminderCount = 0,
            lastItemAddedAt = null,
            now = now,
        )
        assertEquals(ScanNudgeDecision.Skip, decision)
    }

    @Test
    fun `skips when scan nudge is disabled in settings`() {
        val decision = ScanNudgeWorker.evaluateScanNudge(
            hasCompletedOnboarding = true,
            isEnabled = false,
            activeReminderCount = 0,
            lastItemAddedAt = null,
            now = now,
        )
        assertEquals(ScanNudgeDecision.Skip, decision)
    }

    @Test
    fun `notifies with empty vault copy when activeReminderCount is zero`() {
        val decision = ScanNudgeWorker.evaluateScanNudge(
            hasCompletedOnboarding = true,
            isEnabled = true,
            activeReminderCount = 0,
            lastItemAddedAt = now.minus(Duration.ofHours(12)),
            now = now,
        )
        assertTrue(decision is ScanNudgeDecision.Notify)
        assertEquals("Your Memora vault is empty", (decision as ScanNudgeDecision.Notify).title)
    }

    @Test
    fun `skips when user already scanned an item within last 4 days`() {
        val decision = ScanNudgeWorker.evaluateScanNudge(
            hasCompletedOnboarding = true,
            isEnabled = true,
            activeReminderCount = 3,
            lastItemAddedAt = now.minus(Duration.ofDays(2)),
            now = now,
        )
        assertEquals(ScanNudgeDecision.Skip, decision)
    }

    @Test
    fun `notifies weekly check-in when active items exist and user idle for 5 days`() {
        val decision = ScanNudgeWorker.evaluateScanNudge(
            hasCompletedOnboarding = true,
            isEnabled = true,
            activeReminderCount = 2,
            lastItemAddedAt = now.minus(Duration.ofDays(5)),
            now = now,
        )
        assertTrue(decision is ScanNudgeDecision.Notify)
        assertEquals("Restocked this week?", (decision as ScanNudgeDecision.Notify).title)
    }

    @Test
    fun `computeNextDelayMillis targets next evening at 7 PM for empty vault`() {
        val mondayMorning = ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, utc).toInstant()
        val delayMs = ScanNudgeWorker.computeNextDelayMillis(
            now = mondayMorning,
            zone = utc,
            nextEveningForEmptyVault = true,
        )
        val expectedTarget = ZonedDateTime.of(2026, 11, 24, 19, 0, 0, 0, utc).toInstant()
        assertEquals(Duration.between(mondayMorning, expectedTarget).toMillis(), delayMs)
    }

    @Test
    fun `computeNextDelayMillis targets next Sunday at 7 PM when already at Sunday 7 PM`() {
        val sundayEvening = ZonedDateTime.of(2026, 11, 22, 19, 0, 0, 0, utc).toInstant()
        val delayMs = ScanNudgeWorker.computeNextDelayMillis(
            now = sundayEvening,
            zone = utc,
            nextEveningForEmptyVault = false,
        )
        val nextSundayEvening = ZonedDateTime.of(2026, 11, 29, 19, 0, 0, 0, utc).toInstant()
        assertEquals(Duration.between(sundayEvening, nextSundayEvening).toMillis(), delayMs)
    }
}
