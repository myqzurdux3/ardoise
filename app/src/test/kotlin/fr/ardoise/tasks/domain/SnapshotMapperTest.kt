package fr.ardoise.tasks.domain

import fr.ardoise.tasks.data.TaskDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SnapshotMapperTest {

    @Test
    fun `completed tasks, subtasks and blank titles are dropped`() {
        val dtos = listOf(
            TaskDto(id = "1", title = "Acheter du pain"),
            TaskDto(id = "2", title = "Déjà fait", status = TaskDto.STATUS_COMPLETED),
            TaskDto(id = "3", title = "Sous-tâche", parent = "1"),
            TaskDto(id = "4", title = "   "),
            TaskDto(id = "5", title = "Appeler la banque"),
        )

        val snapshot = SnapshotMapper.toSnapshot("list", "Courses", dtos, nowEpochMs = 7L)

        assertEquals(listOf("Acheter du pain", "Appeler la banque"), snapshot.tasks.map { it.title })
        assertEquals(7L, snapshot.syncedAtEpochMs)
        assertEquals("Courses", snapshot.listTitle)
    }

    @Test
    fun `titles are trimmed`() {
        val snapshot = SnapshotMapper.toSnapshot(
            "l", "L", listOf(TaskDto(id = "1", title = "  Ranger  ")), 0L,
        )

        assertEquals("Ranger", snapshot.tasks.single().title)
    }

    @Test
    fun `a mapped snapshot is never stale`() {
        val snapshot = SnapshotMapper.toSnapshot("l", "L", emptyList(), 0L)

        assertEquals(false, snapshot.stale)
    }

    @Test
    fun `RFC 3339 due dates are parsed to the right calendar day`() {
        val parsed = requireNotNull(SnapshotMapper.parseDue("2026-08-30T00:00:00.000Z"))

        val day = Instant.ofEpochMilli(parsed).atZone(ZoneOffset.UTC).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 30), day)
    }

    /** One malformed row must not fail the whole sync. */
    @Test
    fun `an unparseable due date degrades to no due date`() {
        assertNull(SnapshotMapper.parseDue("pas une date"))
        assertNull(SnapshotMapper.parseDue(""))
        assertNull(SnapshotMapper.parseDue(null))
    }
}
