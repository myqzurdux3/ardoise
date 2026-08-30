package fr.ardoise.tasks.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.ardoise.tasks.ArdoiseGraph

/**
 * The polling loop.
 *
 * The Google Tasks API offers neither webhooks nor push, so polling is not a
 * shortcut here -- it is the only option the platform allows.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val outcome = ArdoiseGraph.from(applicationContext).repository.sync()
        return when {
            outcome.isSuccess -> Result.success()
            outcome.isRetryable && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            // Missing consent or no list chosen: retrying cannot fix it, and the
            // cached snapshot is already back on screen marked stale.
            else -> Result.success()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
