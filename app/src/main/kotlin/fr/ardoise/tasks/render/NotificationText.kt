package fr.ardoise.tasks.render

import fr.ardoise.tasks.domain.RenderSnapshot
import java.time.LocalDate

/**
 * The wording of the notification, as pure functions.
 *
 * Separated from [NotificationRenderer] so the text can be tested without an
 * Android runtime -- which is where the interesting logic lives anyway.
 */
object NotificationText {

    private const val BULLET = "•"
    private const val DOT = "·"

    fun body(snapshot: RenderSnapshot?, today: LocalDate): String {
        if (snapshot == null || snapshot.isEmpty) return "Rien en attente."
        return snapshot.tasks.joinToString("\n") { task ->
            val suffix = when {
                task.isOverdue(today) -> "  $DOT en retard"
                task.isDueToday(today) -> "  $DOT aujourd'hui"
                else -> ""
            }
            "$BULLET  ${task.title}$suffix"
        }
    }

    fun summary(snapshot: RenderSnapshot?, today: LocalDate): String {
        if (snapshot == null || snapshot.isEmpty) return "Aucune tâche en cours"
        val total = snapshot.tasks.size
        val overdue = snapshot.tasks.count { it.isOverdue(today) }
        val base = if (total == 1) "1 tâche" else "$total tâches"
        val detail = if (overdue > 0) "  $DOT $overdue en retard" else ""
        val offline = if (snapshot.stale) "  $DOT hors ligne" else ""
        return base + detail + offline
    }

    fun title(snapshot: RenderSnapshot?): String =
        snapshot?.listTitle?.takeIf { it.isNotBlank() } ?: "Ardoise"

    /**
     * The one line that shows while the notification is collapsed.
     *
     * Android renders `BigTextStyle` collapsed on the lock screen until the
     * user expands it, so this line has to carry a real task rather than a
     * count -- otherwise the default state of the primary surface says nothing.
     */
    fun collapsedLine(snapshot: RenderSnapshot?, today: LocalDate): String {
        val next = snapshot?.tasks?.firstOrNull() ?: return "Rien en attente."
        val suffix = when {
            next.isOverdue(today) -> "  $DOT en retard"
            next.isDueToday(today) -> "  $DOT aujourd'hui"
            else -> ""
        }
        return next.title + suffix
    }
}
