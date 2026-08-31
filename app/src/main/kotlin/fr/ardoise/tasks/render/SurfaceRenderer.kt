package fr.ardoise.tasks.render

import fr.ardoise.tasks.data.ArdoiseSettings
import fr.ardoise.tasks.domain.RenderSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Fans one snapshot out to every enabled surface.
 *
 * The repository calls this and nothing else, so adding a third surface later
 * touches one file.
 *
 * Both renderers receive the **whole** snapshot plus the display limit rather
 * than a pre-trimmed copy. Trimming first made the notification count its own
 * visible lines, so a twenty-item list with three overdue tasks outside the
 * window announced "6 tasks" and no overdue at all.
 */
class SurfaceRenderer(
    private val notifications: NotificationRenderer,
    private val wallpaper: WallpaperRenderer,
    /**
     * Rendering is off the caller's thread because the wallpaper path allocates
     * a full-screen bitmap and then hands ~10 MB to the system to compress.
     * Called from a ViewModel that is otherwise on the main dispatcher, that is
     * a visibly frozen UI on a single settings tap.
     */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    suspend fun refresh(
        snapshot: RenderSnapshot?,
        settings: ArdoiseSettings,
        today: LocalDate = LocalDate.now(),
    ) = withContext(dispatcher) {
        if (settings.notificationEnabled) {
            notifications.render(snapshot, settings.maxTasks, today)
        } else {
            notifications.clear()
        }

        if (settings.wallpaperEnabled) {
            wallpaper.render(snapshot, settings.maxTasks, today)
        }
    }

    /** Takes both surfaces down, restoring the system lock screen wallpaper. */
    suspend fun releaseWallpaper() = withContext(dispatcher) {
        wallpaper.clear()
    }
}
