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
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val PERIODIC_WORK = "ardoise-periodic-sync"
    private const val IMMEDIATE_WORK = "ardoise-immediate-sync"
    private const val ACTION_WORK = "ardoise-lockscreen-action"
    private const val ROLLOVER_WORK = "ardoise-day-rollover"

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
        scheduleDayRollover(context)
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

    /**
     * Wakes at the next local midnight so "today" and "overdue" stay true.
     *
     * A worker rather than a manifest receiver: the implicit-broadcast
     * restrictions on Android 8+ make a declared `ACTION_DATE_CHANGED` receiver
     * unreliable, and this needs no network, so it also works offline.
     */
    fun scheduleDayRollover(
        context: Context,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val request = OneTimeWorkRequestBuilder<DayRolloverWorker>()
            .setInitialDelay(millisUntilNextMidnight(zone, nowMillis), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ROLLOVER_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Exposed for testing: the delay is pure clock arithmetic. */
    fun millisUntilNextMidnight(zone: ZoneId, nowMillis: Long): Long {
        val now = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone)
        val nextMidnight = LocalDate.from(now).plusDays(1).atStartOfDay(zone)
        // A minute past, so the worker never races the date change itself.
        return nextMidnight.toInstant().toEpochMilli() - nowMillis + ONE_MINUTE_MS
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

    private const val ONE_MINUTE_MS = 60_000L
}
