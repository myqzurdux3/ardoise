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
 * Puts the surfaces back after a reboot.
 *
 * The cached snapshot is redrawn immediately so the lock screen is populated
 * before the network is even up; the periodic sync is re-armed behind it.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val graph = ArdoiseGraph.from(context)
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = graph.settingsStore.current()
                if (!settings.isConfigured) return@launch

                graph.repository.refreshSurfacesFromCache()
                SyncScheduler.schedulePeriodic(context, settings.syncIntervalMinutes)
                SyncScheduler.syncNow(context)
            } finally {
                pending.finish()
            }
        }
    }
}
