package fr.ardoise.tasks.render

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.WindowManager
import fr.ardoise.tasks.data.SnapshotStore
import fr.ardoise.tasks.domain.RenderSnapshot
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Paints the snapshot onto the lock screen wallpaper.
 *
 * Two guards matter here. The wallpaper is only rewritten when the rendered
 * content actually changed -- redrawing on every sync makes the lock screen
 * flicker on unlock. And [WallpaperManager.FLAG_LOCK] is not honoured by every
 * manufacturer, so a failure is reported rather than crashing a background
 * worker.
 */
class WallpaperRenderer(
    private val context: Context,
    private val snapshotStore: SnapshotStore,
) {

    suspend fun render(
        snapshot: RenderSnapshot?,
        limit: Int,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val manager = WallpaperManager.getInstance(context)
        if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) return false

        val (width, height) = screenSize()
        if (width <= 0 || height <= 0) return false

        val wording = Wording.from(context)
        val key = contentKey(snapshot, wording, width, height, limit, today, zone)
        if (snapshotStore.lastWallpaperKey() == key) return true

        var bitmap: android.graphics.Bitmap? = null
        return try {
            bitmap = WallpaperCanvas.render(snapshot, width, height, wording, limit, today, zone)
            // Without an explicit crop hint the system falls back to
            // getDesiredMinimumWidth(), which is larger than the screen so the
            // home screen can parallax. A screen-sized bitmap then gets scaled
            // to fill it, and the composition breaks. Naming the whole bitmap
            // as the visible region pins it to the screen instead.
            val crop = Rect(0, 0, bitmap.width, bitmap.height)
            manager.setBitmap(bitmap, crop, true, WallpaperManager.FLAG_LOCK)
            snapshotStore.setLastWallpaperKey(key)
            true
        } catch (error: Exception) {
            Log.w(TAG, "Lock screen wallpaper refused by the system", error)
            false
        } finally {
            // Recycling only on the success path leaked a full-screen bitmap on
            // every attempt, on exactly the devices where the call fails.
            bitmap?.recycle()
        }
    }

    /**
     * Hands the lock screen back to the system.
     *
     * Turning the surface off used to just stop repainting, which left
     * Ardoise's last bitmap in place for good; the only way back was the system
     * wallpaper picker. Clearing `FLAG_LOCK` restores whatever the lock screen
     * showed before -- normally the home screen wallpaper.
     */
    suspend fun clear(): Boolean = try {
        WallpaperManager.getInstance(context).clear(WallpaperManager.FLAG_LOCK)
        invalidate()
        true
    } catch (error: Exception) {
        Log.w(TAG, "Could not restore the system lock screen wallpaper", error)
        false
    }

    /** Forgets the cached key so the next render repaints unconditionally. */
    suspend fun invalidate() {
        snapshotStore.setLastWallpaperKey("")
    }

    /**
     * Identity of what would be drawn.
     *
     * The snapshot alone is not enough: the same list on a device that has been
     * unfolded, switched to another language, or shown with a different line
     * limit produces a different picture, and keying on content alone left a
     * stale bitmap on screen in all three cases.
     */
    private fun contentKey(
        snapshot: RenderSnapshot?,
        wording: Wording,
        width: Int,
        height: Int,
        limit: Int,
        today: LocalDate,
        zone: ZoneId,
    ): String = buildString {
        append(width).append('x').append(height).append(SEP)
        append(limit).append(SEP)
        append(Locale.getDefault().toLanguageTag()).append(SEP)
        append(SyncStamp.of(snapshot, wording, zone)).append(SEP)
        append(snapshot?.contentKey(today) ?: EMPTY)
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
        const val EMPTY = "empty"
        const val SEP = '\u001F'
    }
}
