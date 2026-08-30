package fr.ardoise.tasks.render

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.WindowManager
import fr.ardoise.tasks.data.SnapshotStore
import fr.ardoise.tasks.domain.RenderSnapshot
import java.time.LocalDate

/**
 * Paints the snapshot onto the lock screen wallpaper.
 *
 * Two guards matter here. The wallpaper is only rewritten when the rendered
 * content actually changed -- redrawing on every sync makes the lock screen
 * flicker on unlock. And [WallpaperManager.FLAG_LOCK] is not honoured by every
 * manufacturer, so a failure disables the surface quietly instead of crashing
 * a background worker.
 */
class WallpaperRenderer(
    private val context: Context,
    private val snapshotStore: SnapshotStore,
) {

    suspend fun render(snapshot: RenderSnapshot?, today: LocalDate = LocalDate.now()): Boolean {
        val manager = WallpaperManager.getInstance(context)
        if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) return false

        val key = snapshot?.contentKey(today) ?: EMPTY_KEY
        if (snapshotStore.lastWallpaperKey() == key) return true

        val (width, height) = screenSize()
        if (width <= 0 || height <= 0) return false

        return try {
            val bitmap = WallpaperCanvas.render(snapshot, width, height, today)
            manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            bitmap.recycle()
            snapshotStore.setLastWallpaperKey(key)
            true
        } catch (error: Exception) {
            Log.w(TAG, "Lock screen wallpaper refused by the system", error)
            false
        }
    }

    /** Forgets the cached key so the next render repaints unconditionally. */
    suspend fun invalidate() {
        snapshotStore.setLastWallpaperKey("")
    }

    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowManager = context.getSystemService(WindowManager::class.java)
            val bounds = windowManager?.maximumWindowMetrics?.bounds
            if (bounds != null) return bounds.width() to bounds.height()
        }
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    private companion object {
        const val TAG = "ArdoiseWallpaper"
        const val EMPTY_KEY = "empty"
    }
}
