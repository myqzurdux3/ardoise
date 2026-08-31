package fr.ardoise.tasks.render

import fr.ardoise.tasks.domain.RenderSnapshot
import fr.ardoise.tasks.domain.SnapshotTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class NotificationTextTest {

    private val today = LocalDate.of(2026, 8, 30)
    private val wording = testWording()
    private val noLimit = 100

    private fun task(title: String, due: LocalDate? = null) = SnapshotTask(
        id = title,
        title = title,
        dueEpochMs = due?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )

    private fun snapshot(vararg tasks: SnapshotTask, stale: Boolean = false) =
        RenderSnapshot("l", "Courses", tasks.toList(), 1_000L, stale)

    @Test
    fun `each task gets its own line`() {
        val body = NotificationText.body(snapshot(task("Bread"), task("Bank")), today, wording, noLimit)

        assertEquals(2, body.lines().size)
        assertTrue(body.contains("Bread"))
        assertTrue(body.contains("Bank"))
    }

    @Test
    fun `overdue and same-day tasks are marked, others are not`() {
        val body = NotificationText.body(
            snapshot(
                task("Yesterday", today.minusDays(1)),
                task("Today", today),
                task("Someday"),
            ),
            today,
            wording,
            noLimit,
        )
        val lines = body.lines()

        assertTrue(lines[0].contains("overdue"))
        assertTrue(lines[1].contains("today"))
        assertTrue(lines[2].endsWith("Someday"))
    }

    /**
     * The regression this guards: the snapshot used to be trimmed before the
     * summary saw it, so a long list reported only the lines that fitted and
     * claimed nothing was overdue when the overdue items were further down.
     */
    @Test
    fun `the summary counts the whole list, not the visible lines`() {
        val tasks = buildList {
            repeat(5) { add(task("Visible $it")) }
            add(task("Hidden and late", today.minusDays(3)))
            repeat(4) { add(task("Hidden $it")) }
        }
        val snapshot = RenderSnapshot("l", "Courses", tasks, 1_000L)

        val summary = NotificationText.summary(snapshot, today, wording)

        assertTrue(summary.startsWith("10 tasks"))
        assertTrue(summary.contains("1 overdue"))
    }

    @Test
    fun `the body stops at the limit and says how many are left`() {
        val tasks = (1..10).map { task("Task $it") }
        val body = NotificationText.body(RenderSnapshot("l", "L", tasks, 1L), today, wording, 6)
        val lines = body.lines()

        assertEquals(7, lines.size)
        assertTrue(lines.last().contains("4 more"))
        assertFalse(body.contains("Task 8"))
    }

    @Test
    fun `no overflow line when everything fits`() {
        val body = NotificationText.body(snapshot(task("a"), task("b")), today, wording, 6)

        assertFalse(body.contains("more"))
    }

    @Test
    fun `a single task is not pluralised`() {
        assertEquals("1 task", NotificationText.summary(snapshot(task("a")), today, wording))
    }

    @Test
    fun `a stale snapshot says so instead of pretending to be fresh`() {
        val summary = NotificationText.summary(snapshot(task("a"), stale = true), today, wording)

        assertTrue(summary.contains("offline"))
    }

    @Test
    fun `an empty list reads as empty`() {
        assertEquals("Nothing pending.", NotificationText.body(snapshot(), today, wording, noLimit))
        assertEquals("Nothing pending.", NotificationText.summary(snapshot(), today, wording))
    }

    /**
     * A first sync that failed leaves no cache at all. Wording it like an empty
     * list would tell the user they have nothing to do when in fact nothing has
     * been fetched yet.
     */
    @Test
    fun `never having synced is worded differently from having nothing to do`() {
        assertEquals("waiting for first sync", NotificationText.body(null, today, wording, noLimit))
        assertEquals("waiting for first sync", NotificationText.summary(null, today, wording))
        assertEquals("waiting for first sync", NotificationText.collapsedLine(null, today, wording))
    }

    /**
     * The lock screen shows the notification collapsed, so this single line is
     * the default state of Ardoise's primary surface.
     */
    @Test
    fun `the collapsed line carries the next task, not a count`() {
        val line = NotificationText.collapsedLine(
            snapshot(task("Call the bank", today.minusDays(1)), task("Bread")),
            today,
            wording,
        )

        assertTrue(line.startsWith("Call the bank"))
        assertTrue(line.contains("overdue"))
    }

    @Test
    fun `the collapsed line of an undated task is just its title`() {
        assertEquals("Bread", NotificationText.collapsedLine(snapshot(task("Bread")), today, wording))
    }

    @Test
    fun `an absurdly long title is cut rather than pushed at the framework`() {
        val long = "x".repeat(500)

        val line = NotificationText.collapsedLine(snapshot(task(long)), today, wording)

        assertTrue(line.length < 200)
        assertTrue(line.endsWith("…"))
    }

    @Test
    fun `the title falls back to the app name when the list has none`() {
        assertEquals("Courses", NotificationText.title(snapshot(task("a")), wording))
        assertEquals("Ardoise", NotificationText.title(null, wording))
        assertEquals("Ardoise", NotificationText.title(RenderSnapshot.empty(), wording))
    }

    /** French and English differ only through the resources, not the logic. */
    @Test
    fun `wording is what localises the output`() {
        val french = wording.copy(
            nothingPending = "Rien en attente.",
            suffixOverdue = "en retard",
            countTaskMany = "%1\$d tâches",
        )

        assertEquals("Rien en attente.", NotificationText.body(snapshot(), today, french, noLimit))
        assertTrue(
            NotificationText.summary(snapshot(task("a"), task("b")), today, french)
                .startsWith("2 tâches")
        )
    }
}
