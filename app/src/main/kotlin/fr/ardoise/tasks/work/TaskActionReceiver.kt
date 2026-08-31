package fr.ardoise.tasks.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.ardoise.tasks.ArdoiseGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the two notification buttons.
 *
 * Ticking a task off from the lock screen without unlocking the phone is the
 * whole point of using a notification rather than a widget, so the tap has to
 * feel like it did something even with no signal.
 *
 * Completing therefore happens in two halves. The local half runs here and
 * needs no network: the task is dropped from the cache and both surfaces are
 * redrawn, so the line disappears under the user's finger. The half that talks
 * to Google is handed to [SyncWorker], which waits for a connection and retries.
 *
 * Refresh has nothing to acknowledge, so it only enqueues.
 */
class TaskActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_COMPLETE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)?.takeIf { it.isNotBlank() }
                    ?: return
                completeOptimistically(context, taskId)
            }

            ACTION_REFRESH -> SyncScheduler.syncNow(context)
        }
    }

    private fun completeOptimistically(context: Context, taskId: String) {
        val graph = ArdoiseGraph.from(context)
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                graph.repository.acknowledgeCompletion(taskId)
            } catch (error: Exception) {
                // The redraw is a courtesy; losing it must not lose the
                // completion, which is enqueued regardless below.
                Log.w(TAG, "Could not redraw after completing $taskId", error)
            } finally {
                SyncScheduler.completeNow(context, taskId)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "fr.ardoise.tasks.action.COMPLETE"
        const val ACTION_REFRESH = "fr.ardoise.tasks.action.REFRESH"
        const val EXTRA_TASK_ID = "task_id"
        private const val TAG = "ArdoiseAction"
    }
}
