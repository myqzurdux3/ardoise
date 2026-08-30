package fr.ardoise.tasks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ardoise.tasks.R
import fr.ardoise.tasks.domain.RenderSnapshot
import fr.ardoise.tasks.render.Wording
import fr.ardoise.tasks.ui.theme.Chalk
import fr.ardoise.tasks.ui.theme.ChalkDim
import fr.ardoise.tasks.ui.theme.Ochre
import fr.ardoise.tasks.ui.theme.OchreSoft
import fr.ardoise.tasks.ui.theme.SlateDeep
import fr.ardoise.tasks.ui.theme.SlateRaised
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A live rehearsal of the lock screen, inside the app.
 *
 * It mirrors [fr.ardoise.tasks.render.WallpaperCanvas] closely enough that
 * changing a setting shows its effect before the user ever locks the phone --
 * which is the only way to judge a surface you cannot see while configuring it.
 */
@Composable
fun LockPreview(
    snapshot: RenderSnapshot?,
    maxTasks: Int,
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    val tasks = snapshot?.tasks.orEmpty().take(maxTasks)
    val context = LocalContext.current
    val wording = remember(context) { Wording.from(context) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Grows with the list rather than holding a phone's aspect ratio:
            // at six lines a fixed ratio leaves a third of the card empty.
            .heightIn(min = 268.dp)
            .clip(shape)
            .background(
                Brush.radialGradient(
                    colors = listOf(SlateRaised, SlateDeep),
                    radius = 900f,
                )
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 26.dp)
        ) {
            Text(
                text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                color = Chalk,
                fontSize = 46.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
                    .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                color = ChalkDim,
                fontSize = 13.sp,
            )

            Spacer(Modifier.height(30.dp))

            Text(
                text = (snapshot?.listTitle?.takeIf { it.isNotBlank() } ?: wording.appName).uppercase(),
                color = Ochre,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .width(84.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Spacer(Modifier.height(18.dp))

            if (tasks.isEmpty()) {
                Text(stringResource(R.string.nothing_pending), color = ChalkDim, fontSize = 15.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    tasks.forEach { task ->
                        val overdue = task.isOverdue(today)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(if (overdue) Ochre else ChalkDim)
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = task.title,
                                color = if (overdue) OchreSoft else Chalk,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = footerLabel(snapshot, wording),
                color = ChalkDim.copy(alpha = 0.6f),
                fontSize = 11.sp,
            )
        }
    }
}

private fun footerLabel(snapshot: RenderSnapshot?, wording: Wording): String {
    if (snapshot == null || snapshot.syncedAtEpochMs <= 0L) return wording.stampAwaitingSync
    val time = Instant.ofEpochMilli(snapshot.syncedAtEpochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    return if (snapshot.stale) wording.offlineAt(time) else wording.syncedAt(time)
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(PaddingValues(horizontal = 18.dp, vertical = 16.dp))
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        content()
    }
}
