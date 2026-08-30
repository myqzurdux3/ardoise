package fr.ardoise.tasks.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the two notification buttons.
 *
 * Ticking a task off from the lock screen without unlocking the phone is the
 * whole point of using a notification rather than a widget.
 *
 * The receiver only enqueues: a broadcast receiver gets roughly ten seconds of
 * runtime, and a call to Google on a poor mobile connection can outlast that.
 * [SyncWorker] does the actual work under WorkManager's retry policy.
 */
class TaskActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_COMPLETE -> intent.getStringExtra(EXTRA_TASK_ID)
                ?.takeIf { it.isNotBlank() }
                ?.let { SyncScheduler.completeNow(context, it) }

            ACTION_REFRESH -> SyncScheduler.syncNow(context)
        }
    }

    companion object {
        const val ACTION_COMPLETE = "fr.ardoise.tasks.action.COMPLETE"
        const val ACTION_REFRESH = "fr.ardoise.tasks.action.REFRESH"
        const val EXTRA_TASK_ID = "task_id"
    }
}
