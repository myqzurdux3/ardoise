package fr.ardoise.tasks.render

import fr.ardoise.tasks.domain.RenderSnapshot
import fr.ardoise.tasks.domain.SnapshotTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WallpaperCanvasTest {

    private val today = LocalDate.of(2026, 8, 30)
    private val wording = testWording()
    private val width = 1080
    private val height = 2400

    private fun snapshot(vararg titles: String) = RenderSnapshot(
        listId = "l",
        listTitle = "Courses",
        tasks = titles.map { SnapshotTask(it, it) },
        syncedAtEpochMs = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )

    @Test
    fun `the bitmap matches the requested screen size`() {
        val bitmap = WallpaperCanvas.render(snapshot("Pain"), width, height, wording, 10, today)

        assertEquals(width, bitmap.width)
        assertEquals(height, bitmap.height)
    }

    @Test
    fun `the top of the screen is left to the system clock`() {
        val bitmap = WallpaperCanvas.render(snapshot("Pain", "Banque"), width, height, wording, 10, today)

        // Nothing but background above 40% of the height.
        val background = bitmap.getPixel(width / 2, (height * 0.05f).toInt())
        val row = (0 until width step 8).map { bitmap.getPixel(it, (height * 0.30f).toInt()) }
        assertTrue(row.all { it == background || isBackgroundish(it, background) })
    }

    @Test
    fun `tasks are actually drawn below the reserved area`() {
        val empty = WallpaperCanvas.render(null, width, height, wording, 10, today)
        val filled = WallpaperCanvas.render(snapshot("Pain", "Banque", "Vélo"), width, height, wording, 10, today)

        assertTrue(differingPixels(empty, filled) > 0)
    }

    /**
     * The footer used to be skipped on the empty path, so a two-day-old
     * "nothing pending" was indistinguishable from a fresh one.
     */
    @Test
    fun `the sync stamp is drawn even when the list is empty`() {
        val empty = RenderSnapshot("l", "Courses", emptyList(), 1_000L)

        val withStamp = WallpaperCanvas.render(empty, width, height, wording, 10, today)
        val blank = WallpaperCanvas.render(
            empty, width, height, wording.copy(stampSyncedAt = " ", stampOfflineAt = " "), 10, today,
        )

        assertTrue(differingPixels(blank, withStamp) > 0)
    }

    @Test
    fun `only the first tasks up to the limit are drawn`() {
        val six = WallpaperCanvas.render(snapshot("a", "b", "c", "d", "e", "f"), width, height, wording, 6, today)
        val three = WallpaperCanvas.render(snapshot("a", "b", "c", "d", "e", "f"), width, height, wording, 3, today)

        assertTrue(differingPixels(three, six) > 0)
    }

    @Test
    fun `a null snapshot still produces a full-size bitmap`() {
        val bitmap = WallpaperCanvas.render(null, width, height, wording, 10, today)

        assertEquals(width, bitmap.width)
        assertEquals(height, bitmap.height)
    }

    private fun isBackgroundish(pixel: Int, reference: Int): Boolean {
        // The radial glow varies the background slightly; chalk text does not.
        fun channel(value: Int, shift: Int) = (value shr shift) and 0xFF
        return listOf(16, 8, 0).all { shift ->
            kotlin.math.abs(channel(pixel, shift) - channel(reference, shift)) < 40
        }
    }

    private fun differingPixels(a: android.graphics.Bitmap, b: android.graphics.Bitmap): Int {
        var count = 0
        for (y in (height / 3) until height step 6) {
            for (x in 0 until width step 6) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) count++
            }
        }
        return count
    }
}
