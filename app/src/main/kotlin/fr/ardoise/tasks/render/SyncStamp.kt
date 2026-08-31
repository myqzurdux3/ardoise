package fr.ardoise.tasks.render

import fr.ardoise.tasks.domain.RenderSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The "at 09:32" / "offline, 09:32" line, in one place.
 *
 * It used to exist twice -- once in [WallpaperCanvas] and once in the Compose
 * preview -- and the two copies had already drifted apart: the wallpaper
 * skipped the line entirely on an empty list while the preview still drew it.
 * The logic is pure text over types both sides already share, so unlike the
 * colour styling there is no reason not to share it.
 */
object SyncStamp {

    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun of(snapshot: RenderSnapshot?, wording: Wording, zone: ZoneId): String {
        if (snapshot == null || snapshot.syncedAtEpochMs <= 0L) return wording.stampAwaitingSync
        val time = Instant.ofEpochMilli(snapshot.syncedAtEpochMs).atZone(zone).format(CLOCK)
        return if (snapshot.stale) wording.offlineAt(time) else wording.syncedAt(time)
    }
}
