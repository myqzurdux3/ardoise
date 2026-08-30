package fr.ardoise.tasks.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.ardoise.tasks.ArdoiseGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the two notification buttons.
 *
 * Ticking a task off from the lock screen without unlocking the phone is the
 * whole point of using a notification rather than a widget.
 */
class TaskActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val graph = ArdoiseGraph.from(context)
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE -> intent.getStringExtra(EXTRA_TASK_ID)
                        ?.let { graph.repository.completeTask(it) }

                    ACTION_REFRESH -> graph.repository.sync()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "fr.ardoise.tasks.action.COMPLETE"
        const val ACTION_REFRESH = "fr.ardoise.tasks.action.REFRESH"
        const val EXTRA_TASK_ID = "task_id"
    }
}
