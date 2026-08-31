package fr.ardoise.tasks.render

import fr.ardoise.tasks.domain.RenderSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * This line used to exist twice -- once for the wallpaper, once for the in-app
 * preview -- and the copies had already drifted: the wallpaper skipped it
 * entirely on an empty list. These tests pin the single implementation.
 */
class SyncStampTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")
    private val wording = testWording()

    private fun at(local: LocalDateTime) = local.atZone(paris).toInstant().toEpochMilli()

    private fun snapshot(syncedAt: Long, stale: Boolean = false) =
        RenderSnapshot("l", "Courses", emptyList(), syncedAt, stale)

    @Test
    fun `a fresh snapshot shows the local time it was synced`() {
        val stamp = SyncStamp.of(snapshot(at(LocalDateTime.of(2026, 8, 30, 9, 32))), wording, paris)

        assertEquals("at 09:32", stamp)
    }

    @Test
    fun `a stale snapshot says it is offline`() {
        val stamp = SyncStamp.of(
            snapshot(at(LocalDateTime.of(2026, 8, 30, 9, 32)), stale = true),
            wording,
            paris,
        )

        assertEquals("offline, 09:32", stamp)
    }

    @Test
    fun `a snapshot that has never synced says so`() {
        assertEquals("waiting for first sync", SyncStamp.of(snapshot(0L), wording, paris))
    }

    @Test
    fun `no snapshot at all says so too`() {
        assertEquals("waiting for first sync", SyncStamp.of(null, wording, paris))
    }

    @Test
    fun `the stamp follows the given zone`() {
        val instant = LocalDateTime.of(2026, 8, 30, 9, 32).atZone(paris).toInstant().toEpochMilli()

        assertEquals("at 07:32", SyncStamp.of(snapshot(instant), wording, ZoneId.of("UTC")))
    }
}
