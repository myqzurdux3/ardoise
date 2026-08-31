package fr.ardoise.tasks.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class RenderSnapshotTest {

    private val today = LocalDate.of(2026, 8, 30)

    private fun taskDue(date: LocalDate?) = SnapshotTask(
        id = "t",
        title = "Task",
        dueEpochMs = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )

    @Test
    fun `a task dated yesterday is overdue`() {
        assertTrue(taskDue(today.minusDays(1)).isOverdue(today))
    }

    @Test
    fun `a task dated today is not overdue`() {
        val task = taskDue(today)
        assertFalse(task.isOverdue(today))
        assertTrue(task.isDueToday(today))
    }

    @Test
    fun `a task without a due date is never overdue`() {
        assertFalse(taskDue(null).isOverdue(today))
    }

    /**
     * The regression this guards: comparing UTC-midnight instants against a
     * local clock marks today's tasks overdue for every zone west of UTC.
     */
    @Test
    fun `due dates are compared as calendar days, not instants`() {
        val task = taskDue(today)
        assertFalse(task.isOverdue(today))
        assertEquals(today, task.dueDate())
    }

    @Test
    fun `take trims the list without touching the rest of the snapshot`() {
        val snapshot = RenderSnapshot(
            listId = "l",
            listTitle = "Courses",
            tasks = (1..10).map { SnapshotTask("id$it", "Task $it") },
            syncedAtEpochMs = 42L,
        )

        val trimmed = snapshot.take(3)

        assertEquals(3, trimmed.tasks.size)
        assertEquals("Courses", trimmed.listTitle)
        assertEquals(42L, trimmed.syncedAtEpochMs)
    }

    /**
     * Used to take a ticked-off task off the lock screen before Google is told,
     * so it must remove exactly one line and leave the rest of the snapshot be.
     */
    @Test
    fun `without removes one task and keeps everything else`() {
        val snapshot = RenderSnapshot(
            listId = "l",
            listTitle = "Courses",
            tasks = listOf(SnapshotTask("a", "Bread"), SnapshotTask("b", "Bank")),
            syncedAtEpochMs = 42L,
            stale = true,
        )

        val reduced = snapshot.without("a")

        assertEquals(listOf("Bank"), reduced.tasks.map { it.title })
        assertEquals("Courses", reduced.listTitle)
        assertEquals(42L, reduced.syncedAtEpochMs)
        assertTrue(reduced.stale)
    }

    /** The caller skips a write and a redraw when nothing changed. */
    @Test
    fun `without returns the same instance for an unknown id`() {
        val snapshot = RenderSnapshot("l", "L", listOf(SnapshotTask("a", "Bread")), 0L)

        assertSame(snapshot, snapshot.without("nope"))
    }

    @Test
    fun `without the last task leaves an empty snapshot, not null`() {
        val snapshot = RenderSnapshot("l", "L", listOf(SnapshotTask("a", "Bread")), 0L)

        val reduced = snapshot.without("a")

        assertTrue(reduced.isEmpty)
        assertEquals("L", reduced.listTitle)
    }

    @Test
    fun `content key ignores sync time but reacts to titles`() {
        val base = RenderSnapshot("l", "Courses", listOf(SnapshotTask("a", "Pain")), 1000L)
        val laterSameContent = base.copy(syncedAtEpochMs = 9999L)
        val renamed = base.copy(tasks = listOf(SnapshotTask("a", "Baguette")))

        assertEquals(base.contentKey(today), laterSameContent.contentKey(today))
        assertNotEquals(base.contentKey(today), renamed.contentKey(today))
    }

    @Test
    fun `content key reacts to a task falling overdue`() {
        val snapshot = RenderSnapshot("l", "L", listOf(taskDue(today)), 0L)

        assertNotEquals(
            snapshot.contentKey(today),
            snapshot.contentKey(today.plusDays(1)),
        )
    }
}
