package fr.ardoise.tasks.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything both renderers need, and nothing else.
 *
 * This is the only type the notification and wallpaper renderers see. They
 * know nothing about the network, authentication or storage, which keeps them
 * trivially testable and lets them work offline from the cached copy.
 */
@Serializable
data class RenderSnapshot(
    val listId: String,
    val listTitle: String,
    val tasks: List<SnapshotTask>,
    val syncedAtEpochMs: Long,
    /** True when the last sync attempt failed and this is a fallback copy. */
    val stale: Boolean = false,
) {
    val isEmpty: Boolean get() = tasks.isEmpty()

    fun take(limit: Int): RenderSnapshot = copy(tasks = tasks.take(limit))

    /** Identity of the rendered content, used to skip redundant redraws. */
    fun contentKey(today: LocalDate): String =
        buildString {
            append(listTitle).append(SEP)
            append(stale).append(SEP)
            tasks.forEach { task ->
                append(task.id).append(':')
                append(task.title).append(':')
                append(task.isOverdue(today))
                append(SEP)
            }
        }

    companion object {
        private const val SEP = '\u001F'

        fun empty(listId: String = "", listTitle: String = "") =
            RenderSnapshot(listId, listTitle, emptyList(), 0L)
    }
}

@Serializable
data class SnapshotTask(
    val id: String,
    val title: String,
    /** Start of the due day, UTC. Google Tasks due dates carry no time. */
    val dueEpochMs: Long? = null,
) {
    fun dueDate(): LocalDate? =
        dueEpochMs?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate() }

    /**
     * Google Tasks due dates are date-only values pinned to UTC midnight, so
     * they are compared as calendar dates against the user's local today --
     * never as instants, which would shift the day for most time zones.
     */
    fun isOverdue(today: LocalDate): Boolean {
        val due = dueDate() ?: return false
        return due.isBefore(today)
    }

    fun isDueToday(today: LocalDate): Boolean = dueDate() == today
}
