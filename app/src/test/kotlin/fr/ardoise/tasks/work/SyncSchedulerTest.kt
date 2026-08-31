package fr.ardoise.tasks.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * "Overdue" and "today" are decided against the local calendar day, so the
 * repaint has to be aimed at local midnight -- not at a fixed period, which
 * would drift, and not at UTC, which is the wrong midnight for most of the
 * world.
 */
class SyncSchedulerTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    private fun millisAt(local: LocalDateTime, zone: ZoneId = paris) =
        local.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `the delay lands just after the next local midnight`() {
        val now = LocalDateTime.of(2026, 8, 30, 23, 50)

        val delay = SyncScheduler.millisUntilNextMidnight(paris, millisAt(now))
        val firesAt = millisAt(now) + delay

        val fired = java.time.Instant.ofEpochMilli(firesAt).atZone(paris)
        assertEquals(LocalDate.of(2026, 8, 31), fired.toLocalDate())
        assertEquals(0, fired.hour)
        assertEquals(1, fired.minute)
    }

    @Test
    fun `a delay just after midnight waits nearly a whole day`() {
        val delay = SyncScheduler.millisUntilNextMidnight(
            paris,
            millisAt(LocalDateTime.of(2026, 8, 30, 0, 5)),
        )

        assertTrue(delay > 23 * 3_600_000L)
        assertTrue(delay < 25 * 3_600_000L)
    }

    @Test
    fun `the delay is never zero or negative`() {
        val midnightExactly = millisAt(LocalDateTime.of(2026, 8, 30, 0, 0))

        assertTrue(SyncScheduler.millisUntilNextMidnight(paris, midnightExactly) > 0)
    }

    /** The zone matters: UTC midnight is the wrong moment nearly everywhere. */
    @Test
    fun `the delay follows the given zone, not UTC`() {
        val instant = millisAt(LocalDateTime.of(2026, 8, 30, 23, 0), ZoneId.of("UTC"))

        val inParis = SyncScheduler.millisUntilNextMidnight(paris, instant)
        val inUtc = SyncScheduler.millisUntilNextMidnight(ZoneId.of("UTC"), instant)

        assertTrue(inParis != inUtc)
    }

    /**
     * A day that is not 24 hours long: clocks go back an hour in Paris on
     * 2026-10-25, so the night is 25 hours.
     */
    @Test
    fun `a daylight-saving night still resolves to the next local midnight`() {
        val now = LocalDateTime.of(2026, 10, 24, 23, 0)

        val delay = SyncScheduler.millisUntilNextMidnight(paris, millisAt(now))
        val fired = java.time.Instant.ofEpochMilli(millisAt(now) + delay).atZone(paris)

        assertEquals(LocalDate.of(2026, 10, 25), fired.toLocalDate())
        assertEquals(0, fired.hour)
    }
}
