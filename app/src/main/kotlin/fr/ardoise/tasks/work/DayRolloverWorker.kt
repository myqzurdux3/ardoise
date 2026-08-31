package fr.ardoise.tasks.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.ardoise.tasks.ArdoiseGraph

/**
 * Repaints both surfaces when the local date changes, then re-arms itself.
 *
 * "Overdue" and "today" are decided against the local calendar day, but the
 * only thing that used to trigger a repaint was a sync -- which carries a
 * network constraint. A task due today therefore kept saying "today", in chalk
 * white rather than ochre, for as long as the device stayed offline past
 * midnight. This redraws from cache alone, so it works in airplane mode.
 */
class DayRolloverWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val graph = ArdoiseGraph.from(applicationContext)
        if (graph.settingsStore.current().isConfigured) {
            graph.repository.refreshSurfacesFromCache()
        }
        // Re-arm for the next local midnight; Doze may run this late, and the
        // next delay is computed from the clock rather than from a fixed period,
        // so lateness does not accumulate.
        SyncScheduler.scheduleDayRollover(applicationContext)
        return Result.success()
    }
}
