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
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // IMPORTANCE_LOW looks like the obvious choice for a silent, permanent
        // notification -- and it defeats the point. Android files LOW under
        // "silent" and collapses it to a bare icon on the lock screen, so none
        // of the task text survives. DEFAULT keeps the notification expanded
        // there; the noise is removed by muting the channel instead, which
        // still leaves no sound, no vibration and no heads-up banner.
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_detail)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        manager.createNotificationChannel(channel)

        // A channel's importance is fixed once created, so the muted LOW
        // channel shipped earlier has to be retired rather than edited.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }

    fun render(snapshot: RenderSnapshot?, today: LocalDate = LocalDate.now()) {
        ensureChannel()
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        // Resolved per render, so a language change applies without a restart.
        val wording = Wording.from(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(NotificationText.title(snapshot, wording))
            // Collapsed on the lock screen: the next task, not a bare count.
            .setContentText(NotificationText.collapsedLine(snapshot, today, wording))
            // In the header line, so the count survives expansion either way.
            .setSubText(NotificationText.summary(snapshot, today, wording))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(NotificationText.title(snapshot, wording))
                    .bigText(NotificationText.body(snapshot, today, wording))
            )
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
                context.getString(R.string.action_complete),
                broadcast(TaskActionReceiver.ACTION_COMPLETE, first.id),
            )
        }
        builder.addAction(
            R.drawable.ic_refresh,
            context.getString(R.string.action_refresh),
            broadcast(TaskActionReceiver.ACTION_REFRESH),
        )

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
        const val CHANNEL_ID = "ardoise_tasks_visible"
        private const val LEGACY_CHANNEL_ID = "ardoise_tasks"
        const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN = 900
    }
}
