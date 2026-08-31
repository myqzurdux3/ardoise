package fr.ardoise.tasks.domain

import fr.ardoise.tasks.data.TaskDto
import java.time.Instant

/** Turns Google's wire format into the one thing the renderers understand. */
object SnapshotMapper {

    /**
     * The notification joins tasks with newlines and the wallpaper draws one
     * line per task with no wrapping, so a title carrying its own newline or a
     * control character breaks both surfaces. Google accepts such titles, so
     * they are flattened here, once, rather than defended against twice.
     */
    private val CONTROL_CHARS = Regex("[\\p{Cntrl}\\u2028\\u2029]+")

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
            .map { SnapshotTask(id = it.id, title = flatten(it.title), dueEpochMs = parseDue(it.due)) }
            .filter { it.title.isNotEmpty() }
            .toList(),
        syncedAtEpochMs = nowEpochMs,
        stale = false,
    )

    fun flatten(title: String): String = CONTROL_CHARS.replace(title, " ").trim()

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
