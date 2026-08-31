package fr.ardoise.tasks.render

import fr.ardoise.tasks.domain.RenderSnapshot
import fr.ardoise.tasks.domain.SnapshotTask
import java.time.LocalDate

/**
 * The wording of the notification, as pure functions.
 *
 * Separated from [NotificationRenderer] so the text can be tested without an
 * Android runtime -- which is where the interesting logic lives anyway. All
 * localised strings arrive as a [Wording] rather than being looked up here.
 *
 * These functions take the **whole** snapshot plus a display limit rather than
 * a pre-trimmed one: the body shows `limit` lines, but the counts have to
 * describe the entire list, or the notification tells the user their twenty-item
 * list holds six things and that nothing is overdue.
 */
object NotificationText {

    private const val BULLET = "•"
    private const val DOT = "·"

    /** Android truncates a notification CharSequence anyway; do it legibly first. */
    private const val MAX_TITLE = 120

    fun body(snapshot: RenderSnapshot?, today: LocalDate, wording: Wording, limit: Int): String {
        if (snapshot == null || snapshot.isEmpty) return emptyLine(snapshot, wording)
        val shown = snapshot.tasks.take(limit)
        val lines = shown.map { "$BULLET  ${title(it)}${suffix(it, today, wording)}" }
        val hidden = snapshot.tasks.size - shown.size
        return if (hidden > 0) {
            (lines + "$BULLET  ${wording.andMore(hidden)}").joinToString("\n")
        } else {
            lines.joinToString("\n")
        }
    }

    fun summary(snapshot: RenderSnapshot?, today: LocalDate, wording: Wording): String {
        if (snapshot == null || snapshot.isEmpty) return emptyLine(snapshot, wording)
        val overdue = snapshot.tasks.count { it.isOverdue(today) }
        val base = wording.taskCount(snapshot.tasks.size)
        val detail = if (overdue > 0) "  $DOT ${wording.overdueCount(overdue)}" else ""
        val offline = if (snapshot.stale) "  $DOT ${wording.suffixOffline}" else ""
        return base + detail + offline
    }

    fun title(snapshot: RenderSnapshot?, wording: Wording): String =
        snapshot?.listTitle?.takeIf { it.isNotBlank() } ?: wording.appName

    /**
     * The one line that shows while the notification is collapsed.
     *
     * Android renders `BigTextStyle` collapsed on the lock screen until the
     * user expands it, so this line has to carry a real task rather than a
     * count -- otherwise the default state of the primary surface says nothing.
     */
    fun collapsedLine(snapshot: RenderSnapshot?, today: LocalDate, wording: Wording): String {
        val next = snapshot?.tasks?.firstOrNull() ?: return emptyLine(snapshot, wording)
        return title(next) + suffix(next, today, wording)
    }

    /**
     * An empty list and a list that has never been fetched look identical on
     * screen unless they are worded differently, so a user whose very first
     * sync failed is not told they have nothing to do.
     */
    private fun emptyLine(snapshot: RenderSnapshot?, wording: Wording): String =
        if (snapshot == null) wording.stampAwaitingSync else wording.nothingPending

    private fun title(task: SnapshotTask): String =
        if (task.title.length <= MAX_TITLE) task.title else task.title.take(MAX_TITLE - 1) + "…"

    private fun suffix(task: SnapshotTask, today: LocalDate, wording: Wording): String = when {
        task.isOverdue(today) -> "  $DOT ${wording.suffixOverdue}"
        task.isDueToday(today) -> "  $DOT ${wording.suffixToday}"
        else -> ""
    }
}
