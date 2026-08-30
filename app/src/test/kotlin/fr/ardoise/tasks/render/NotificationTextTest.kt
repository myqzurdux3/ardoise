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

    private fun task(title: String, due: LocalDate? = null) = SnapshotTask(
        id = title,
        title = title,
        dueEpochMs = due?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )

    private fun snapshot(vararg tasks: SnapshotTask, stale: Boolean = false) =
        RenderSnapshot("l", "Courses", tasks.toList(), 1_000L, stale)

    @Test
    fun `each task gets its own line`() {
        val body = NotificationText.body(snapshot(task("Pain"), task("Banque")), today)

        assertEquals(2, body.lines().size)
        assertTrue(body.contains("Pain"))
        assertTrue(body.contains("Banque"))
    }

    @Test
    fun `overdue and same-day tasks are marked, others are not`() {
        val body = NotificationText.body(
            snapshot(
                task("Hier", today.minusDays(1)),
                task("Aujourd'hui", today),
                task("Un jour"),
            ),
            today,
        )
        val lines = body.lines()

        assertTrue(lines[0].contains("en retard"))
        assertTrue(lines[1].contains("aujourd'hui"))
        assertTrue(lines[2].endsWith("Un jour"))
    }

    @Test
    fun `the summary counts tasks and overdue ones`() {
        val summary = NotificationText.summary(
            snapshot(task("a", today.minusDays(2)), task("b"), task("c")),
            today,
        )

        assertTrue(summary.startsWith("3 tâches"))
        assertTrue(summary.contains("1 en retard"))
    }

    @Test
    fun `a single task is not pluralised`() {
        assertEquals("1 tâche", NotificationText.summary(snapshot(task("a")), today))
    }

    @Test
    fun `a stale snapshot says so instead of pretending to be fresh`() {
        val summary = NotificationText.summary(snapshot(task("a"), stale = true), today)

        assertTrue(summary.contains("hors ligne"))
    }

    @Test
    fun `an empty list still renders something readable`() {
        assertEquals("Rien en attente.", NotificationText.body(snapshot(), today))
        assertEquals("Aucune tâche en cours", NotificationText.summary(null, today))
    }

    @Test
    fun `the title falls back to the app name when the list has none`() {
        assertEquals("Courses", NotificationText.title(snapshot(task("a"))))
        assertEquals("Ardoise", NotificationText.title(null))
        assertEquals("Ardoise", NotificationText.title(RenderSnapshot.empty()))
    }
}
