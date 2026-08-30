package fr.ardoise.tasks.render

import fr.ardoise.tasks.data.ArdoiseSettings
import fr.ardoise.tasks.domain.RenderSnapshot
import java.time.LocalDate

/**
 * Fans one snapshot out to every enabled surface.
 *
 * The repository calls this and nothing else, so adding a third surface later
 * touches one file.
 */
class SurfaceRenderer(
    private val notifications: NotificationRenderer,
    private val wallpaper: WallpaperRenderer,
) {

    suspend fun refresh(
        snapshot: RenderSnapshot?,
        settings: ArdoiseSettings,
        today: LocalDate = LocalDate.now(),
    ) {
        val trimmed = snapshot?.take(settings.maxTasks)

        if (settings.notificationEnabled) {
            notifications.render(trimmed, today)
        } else {
            notifications.clear()
        }

        if (settings.wallpaperEnabled) {
            wallpaper.render(trimmed, today)
        }
    }
}
