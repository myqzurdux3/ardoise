package fr.ardoise.tasks

import android.content.Context
import fr.ardoise.tasks.auth.AuthProvider
import fr.ardoise.tasks.data.SettingsStore
import fr.ardoise.tasks.data.SnapshotStore
import fr.ardoise.tasks.data.TasksApi
import fr.ardoise.tasks.domain.TaskRepository
import fr.ardoise.tasks.render.NotificationRenderer
import fr.ardoise.tasks.render.SurfaceRenderer
import fr.ardoise.tasks.render.WallpaperRenderer

/**
 * Hand-rolled object graph.
 *
 * One activity, one worker and two receivers do not justify a dependency
 * injection framework; they justify eleven lines of construction.
 */
class ArdoiseGraph private constructor(context: Context) {

    private val app = context.applicationContext

    val settingsStore = SettingsStore(app)
    val snapshotStore = SnapshotStore(app)
    val auth = AuthProvider(app)
    val notifications = NotificationRenderer(app)

    private val api = TasksApi()
    private val wallpaper = WallpaperRenderer(app, snapshotStore)
    private val surfaces = SurfaceRenderer(notifications, wallpaper)

    val repository = TaskRepository(api, auth, settingsStore, snapshotStore, surfaces)

    suspend fun invalidateWallpaper() = wallpaper.invalidate()

    companion object {
        @Volatile
        private var instance: ArdoiseGraph? = null

        fun from(context: Context): ArdoiseGraph =
            instance ?: synchronized(this) {
                instance ?: ArdoiseGraph(context).also { instance = it }
            }
    }
}
