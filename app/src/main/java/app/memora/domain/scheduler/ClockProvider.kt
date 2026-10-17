package app.memora.domain.scheduler

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Clock abstraction allowing deterministic date/time evaluations and unit testing.
 */
interface ClockProvider {
    fun now(): Instant
    fun today(zoneId: ZoneId): LocalDate
}

/**
 * Production implementation backed by the device's system clock.
 */
class SystemClockProvider : ClockProvider {
    override fun now(): Instant = Instant.now()
    override fun today(zoneId: ZoneId): LocalDate = LocalDate.now(zoneId)
}
