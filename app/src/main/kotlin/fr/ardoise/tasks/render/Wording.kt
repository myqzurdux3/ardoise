package fr.ardoise.tasks.render

import android.content.Context
import fr.ardoise.tasks.R

/**
 * Every localised string the two renderers need, already resolved.
 *
 * [NotificationText] and [WallpaperCanvas] are deliberately plain Kotlin
 * objects with no [Context], which is what keeps them unit-testable without an
 * Android runtime. Handing them a Context to look up resources would throw
 * that away, so the strings are resolved once at the call site and passed in
 * as data instead.
 *
 * The counts are split into a "one" and a "many" form rather than using
 * Android plurals: this type has to stay constructible in a plain JVM test,
 * and the one-versus-rest rule is correct for both languages Ardoise ships.
 */
data class Wording(
    val appName: String,
    val nothingPending: String,
    val noTasks: String,
    val suffixOverdue: String,
    val suffixToday: String,
    val suffixOffline: String,
    val countTaskOne: String,
    val countTaskMany: String,
    val countOverdue: String,
    val stampAwaitingSync: String,
    val stampSyncedAt: String,
    val stampOfflineAt: String,
) {
    fun taskCount(total: Int): String =
        if (total == 1) countTaskOne else countTaskMany.format(total)

    fun overdueCount(total: Int): String = countOverdue.format(total)

    fun syncedAt(time: String): String = stampSyncedAt.format(time)

    fun offlineAt(time: String): String = stampOfflineAt.format(time)

    companion object {
        fun from(context: Context): Wording = Wording(
            appName = context.getString(R.string.app_name),
            nothingPending = context.getString(R.string.nothing_pending),
            noTasks = context.getString(R.string.no_tasks),
            suffixOverdue = context.getString(R.string.suffix_overdue),
            suffixToday = context.getString(R.string.suffix_today),
            suffixOffline = context.getString(R.string.suffix_offline),
            countTaskOne = context.getString(R.string.count_task_one),
            countTaskMany = context.getString(R.string.count_task_many),
            countOverdue = context.getString(R.string.count_overdue),
            stampAwaitingSync = context.getString(R.string.stamp_awaiting_sync),
            stampSyncedAt = context.getString(R.string.stamp_synced_at),
            stampOfflineAt = context.getString(R.string.stamp_offline_at),
        )
    }
}
