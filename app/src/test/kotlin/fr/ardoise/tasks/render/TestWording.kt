package fr.ardoise.tasks.render

/**
 * A fixed English [Wording] for tests.
 *
 * Stated explicitly rather than loaded from resources so the assertions do not
 * depend on the locale the test host happens to run in -- and so the renderers
 * keep needing no Android runtime at all.
 */
internal fun testWording() = Wording(
    appName = "Ardoise",
    nothingPending = "Nothing pending.",
    noTasks = "No tasks right now",
    suffixOverdue = "overdue",
    suffixToday = "today",
    suffixOffline = "offline",
    countTaskOne = "1 task",
    countTaskMany = "%1\$d tasks",
    countOverdue = "%1\$d overdue",
    countAndMore = "%1\$d more",
    stampAwaitingSync = "waiting for first sync",
    stampSyncedAt = "at %1\$s",
    stampOfflineAt = "offline, %1\$s",
)
