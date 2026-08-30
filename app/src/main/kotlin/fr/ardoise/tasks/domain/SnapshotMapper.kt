package fr.ardoise.tasks.domain

import fr.ardoise.tasks.data.TaskDto
import java.time.Instant

/** Turns Google's wire format into the one thing the renderers understand. */
object SnapshotMapper {

    fun toSnapshot(
        listId: String,
        listTitle: String,
        dtos: List<TaskDto>,
        nowEpochMs: Long,
    ): RenderSnapshot = RenderSnapshot(
        listId = listId,
        listTitle = listTitle,
        // Subtasks are dropped: a lock screen has room for headlines, not trees.
        tasks = dtos.asSequence()
            .filterNot { it.isCompleted }
            .filterNot { it.isSubtask }
            .filter { it.title.isNotBlank() }
            .map { SnapshotTask(id = it.id, title = it.title.trim(), dueEpochMs = parseDue(it.due)) }
            .toList(),
        syncedAtEpochMs = nowEpochMs,
        stale = false,
    )

    /**
     * Google Tasks sends due dates as RFC 3339 instants pinned to UTC midnight.
     * Anything unparseable is treated as "no due date" rather than failing the
     * whole sync over one malformed row.
     */
    fun parseDue(due: String?): Long? {
        if (due.isNullOrBlank()) return null
        return runCatching { Instant.parse(due).toEpochMilli() }.getOrNull()
    }
}
