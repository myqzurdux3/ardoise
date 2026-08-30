package fr.ardoise.tasks.render

import fr.ardoise.tasks.domain.RenderSnapshot
import fr.ardoise.tasks.domain.SnapshotTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class NotificationTextTest {

    private val today = LocalDate.of(2026, 8, 30)
    private val wording = testWording()

    private fun task(title: String, due: LocalDate? = null) = SnapshotTask(
        id = title,
        title = title,
        dueEpochMs = due?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )

    private fun snapshot(vararg tasks: SnapshotTask, stale: Boolean = false) =
        RenderSnapshot("l", "Courses", tasks.toList(), 1_000L, stale)

    @Test
    fun `each task gets its own line`() {
        val body = NotificationText.body(snapshot(task("Bread"), task("Bank")), today, wording)

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
        )
        val lines = body.lines()

        assertTrue(lines[0].contains("overdue"))
        assertTrue(lines[1].contains("today"))
        assertTrue(lines[2].endsWith("Someday"))
    }

    @Test
    fun `the summary counts tasks and overdue ones`() {
        val summary = NotificationText.summary(
            snapshot(task("a", today.minusDays(2)), task("b"), task("c")),
            today,
            wording,
        )

        assertTrue(summary.startsWith("3 tasks"))
        assertTrue(summary.contains("1 overdue"))
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
    fun `an empty list still renders something readable`() {
        assertEquals("Nothing pending.", NotificationText.body(snapshot(), today, wording))
        assertEquals("No tasks right now", NotificationText.summary(null, today, wording))
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
    fun `the collapsed line marks a task due today`() {
        val line = NotificationText.collapsedLine(snapshot(task("Bread", today)), today, wording)

        assertTrue(line.contains("today"))
    }

    @Test
    fun `the collapsed line of an undated task is just its title`() {
        assertEquals("Bread", NotificationText.collapsedLine(snapshot(task("Bread")), today, wording))
    }

    @Test
    fun `the collapsed line handles an empty list`() {
        assertEquals("Nothing pending.", NotificationText.collapsedLine(snapshot(), today, wording))
        assertEquals("Nothing pending.", NotificationText.collapsedLine(null, today, wording))
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

        assertEquals("Rien en attente.", NotificationText.body(snapshot(), today, french))
        assertTrue(
            NotificationText.summary(snapshot(task("a"), task("b")), today, french)
                .startsWith("2 tâches")
        )
    }
}
