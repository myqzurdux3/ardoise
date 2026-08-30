package fr.ardoise.tasks.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val PERIODIC_WORK = "ardoise-periodic-sync"
    private const val IMMEDIATE_WORK = "ardoise-immediate-sync"
    private const val ACTION_WORK = "ardoise-lockscreen-action"

    private val networkRequired = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(context: Context, intervalMinutes: Int) {
        // WorkManager clamps anything below 15 minutes anyway; be explicit.
        val interval = intervalMinutes.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(networkRequired)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun syncNow(context: Context) {
        enqueueOneShot(context, IMMEDIATE_WORK, ExistingWorkPolicy.REPLACE, Data.EMPTY)
    }

    /**
     * Ticking a task off from the lock screen.
     *
     * APPEND_OR_REPLACE, not REPLACE: two quick taps must both reach Google
     * rather than the second cancelling the first.
     */
    fun completeNow(context: Context, taskId: String) {
        val data = Data.Builder()
            .putString(SyncWorker.KEY_COMPLETE_TASK_ID, taskId)
            .build()
        enqueueOneShot(context, ACTION_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, data)
    }

    private fun enqueueOneShot(
        context: Context,
        name: String,
        policy: ExistingWorkPolicy,
        data: Data,
    ) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkRequired)
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC_WORK)
            cancelUniqueWork(IMMEDIATE_WORK)
        }
    }
}
