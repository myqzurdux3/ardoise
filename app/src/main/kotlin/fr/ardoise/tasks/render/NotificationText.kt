package fr.ardoise.tasks.render

import fr.ardoise.tasks.domain.RenderSnapshot
import java.time.LocalDate

/**
 * The wording of the notification, as pure functions.
 *
 * Separated from [NotificationRenderer] so the text can be tested without an
 * Android runtime -- which is where the interesting logic lives anyway. All
 * localised strings arrive as a [Wording] rather than being looked up here.
 */
object NotificationText {

    private const val BULLET = "•"
    private const val DOT = "·"

    fun body(snapshot: RenderSnapshot?, today: LocalDate, wording: Wording): String {
        if (snapshot == null || snapshot.isEmpty) return wording.nothingPending
        return snapshot.tasks.joinToString("\n") { task ->
            "$BULLET  ${task.title}${suffix(task, today, wording)}"
        }
    }

    fun summary(snapshot: RenderSnapshot?, today: LocalDate, wording: Wording): String {
        if (snapshot == null || snapshot.isEmpty) return wording.noTasks
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
        val next = snapshot?.tasks?.firstOrNull() ?: return wording.nothingPending
        return next.title + suffix(next, today, wording)
    }

    private fun suffix(
        task: fr.ardoise.tasks.domain.SnapshotTask,
        today: LocalDate,
        wording: Wording,
    ): String = when {
        task.isOverdue(today) -> "  $DOT ${wording.suffixOverdue}"
        task.isDueToday(today) -> "  $DOT ${wording.suffixToday}"
        else -> ""
    }
}
