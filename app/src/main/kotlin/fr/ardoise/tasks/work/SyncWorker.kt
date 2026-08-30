package fr.ardoise.tasks.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.ardoise.tasks.ArdoiseGraph
import fr.ardoise.tasks.domain.SyncOutcome

/**
 * The polling loop, and the place where lock screen actions are carried out.
 *
 * The Google Tasks API offers neither webhooks nor push, so polling is not a
 * shortcut here -- it is the only option the platform allows.
 *
 * Completing a task runs here rather than in [TaskActionReceiver] because a
 * broadcast receiver gets roughly ten seconds even with `goAsync()`, which a
 * slow mobile network can easily exceed.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = ArdoiseGraph.from(applicationContext).repository
        val taskId = inputData.getString(KEY_COMPLETE_TASK_ID)

        val outcome: SyncOutcome =
            if (taskId.isNullOrBlank()) repository.sync() else repository.completeTask(taskId)

        return when {
            outcome.isSuccess -> Result.success()
            outcome.isRetryable && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            // Missing consent or no list chosen: retrying cannot fix it, and the
            // cached snapshot is already back on screen marked stale.
            else -> Result.success()
        }
    }

    companion object {
        const val KEY_COMPLETE_TASK_ID = "complete_task_id"
        private const val MAX_ATTEMPTS = 3
    }
}
