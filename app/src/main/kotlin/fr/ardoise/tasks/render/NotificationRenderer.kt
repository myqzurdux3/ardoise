package fr.ardoise.tasks.render

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.ardoise.tasks.R
import fr.ardoise.tasks.domain.RenderSnapshot
import fr.ardoise.tasks.ui.MainActivity
import fr.ardoise.tasks.work.TaskActionReceiver
import java.time.LocalDate

/**
 * The primary surface.
 *
 * A lock screen widget is throttled, small and placed where the system decides.
 * An ongoing notification is none of those things: it holds six to eight lines,
 * takes buttons, survives reboot, and Android never collapses it away.
 */
class NotificationRenderer(private val context: Context) {

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tâches sur l'écran de verrouillage",
            // LOW: permanently visible, never a sound or a heads-up banner.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Affiche en continu la liste de tâches choisie."
            setShowBadge(false)
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun render(snapshot: RenderSnapshot?, today: LocalDate = LocalDate.now()) {
        ensureChannel()
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(NotificationText.title(snapshot))
            .setContentText(NotificationText.summary(snapshot, today))
            .setStyle(NotificationCompat.BigTextStyle().bigText(NotificationText.body(snapshot, today)))
            .setColor(ArdoisePalette.OCHRE)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())

        snapshot?.tasks?.firstOrNull()?.let { first ->
            builder.addAction(
                R.drawable.ic_check,
                "Terminer",
                broadcast(TaskActionReceiver.ACTION_COMPLETE, first.id),
            )
        }
        builder.addAction(R.drawable.ic_refresh, "Actualiser", broadcast(TaskActionReceiver.ACTION_REFRESH))

        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (denied: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and the call.
        }
    }

    fun clear() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun broadcast(action: String, taskId: String? = null): PendingIntent {
        val intent = Intent(context, TaskActionReceiver::class.java).setAction(action)
        taskId?.let { intent.putExtra(TaskActionReceiver.EXTRA_TASK_ID, it) }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_ID = "ardoise_tasks"
        const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN = 900
    }
}
