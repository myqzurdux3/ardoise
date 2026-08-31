package fr.ardoise.tasks.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ardoise.tasks.R
import fr.ardoise.tasks.auth.SigningIdentity
import fr.ardoise.tasks.data.ArdoiseSettings
import fr.ardoise.tasks.data.TaskListDto
import fr.ardoise.tasks.ui.theme.Chalk
import fr.ardoise.tasks.ui.theme.ChalkDim
import fr.ardoise.tasks.ui.theme.Ochre

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSignIn: () -> Unit,
    onSelectList: (TaskListDto) -> Unit,
    onMaxTasks: (Int) -> Unit,
    onSyncInterval: (Int) -> Unit,
    onNotificationToggle: (Boolean) -> Unit,
    onWallpaperToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Wordmark(busy = state.busy)

            LockPreview(snapshot = state.snapshot, maxTasks = state.settings.maxTasks)

            if (state.setupRequired) {
                SetupCard()
            }

            if (!state.authorized) {
                SignInCard(onSignIn = onSignIn)
            } else {
                ListCard(
                    lists = state.lists,
                    selectedId = state.settings.listId,
                    onSelect = onSelectList,
                )
            }

            if (!state.notificationsAllowed) {
                PermissionCard(onRequest = onRequestNotificationPermission)
            }

            SurfacesCard(
                settings = state.settings,
                onNotificationToggle = onNotificationToggle,
                onWallpaperToggle = onWallpaperToggle,
            )

            SectionCard(title = stringResource(R.string.section_lines)) {
                ChipRow(
                    options = ArdoiseSettings.MAX_TASKS_CHOICES,
                    selected = state.settings.maxTasks,
                    label = { "$it" },
                    onSelect = onMaxTasks,
                )
            }

            SectionCard(title = stringResource(R.string.section_sync)) {
                ChipRow(
                    options = ArdoiseSettings.SYNC_MINUTES_CHOICES,
                    selected = state.settings.syncIntervalMinutes,
                    label = { stringResource(R.string.minutes_format, it) },
                    onSelect = onSyncInterval,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.sync_polling_note),
                    color = ChalkDim,
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onRefresh, enabled = !state.busy) {
                    Icon(
                        painterResource(R.drawable.ic_refresh),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_refresh_now))
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun Wordmark(busy: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                color = Chalk,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 6.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tagline),
                color = ChalkDim,
                fontSize = 13.sp,
            )
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Ochre,
            )
        }
    }
}

/**
 * Shown when Google rejects the app for lacking an OAuth client.
 *
 * Ardoise cannot ship a working client -- Google binds one to a package name
 * plus a signing certificate, so every install signed with a different key
 * needs its own. The least this screen can do is show the two values the Cloud
 * Console asks for, read from the running build, ready to copy.
 */
@Composable
private fun SetupCard() {
    val context = LocalContext.current
    val packageName = remember(context) { SigningIdentity.packageName(context) }
    val fingerprint = remember(context) { SigningIdentity.sha1(context) }
    val unavailable = stringResource(R.string.setup_sha1_unavailable)

    SectionCard(title = stringResource(R.string.section_setup)) {
        Text(
            text = stringResource(R.string.setup_body),
            color = ChalkDim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(18.dp))
        CopyableValue(
            label = stringResource(R.string.setup_package),
            value = packageName,
            context = context,
        )
        Spacer(Modifier.height(14.dp))
        CopyableValue(
            label = stringResource(R.string.setup_sha1),
            value = fingerprint ?: unavailable,
            context = context,
            enabled = fingerprint != null,
        )
    }
}

@Composable
private fun CopyableValue(
    label: String,
    value: String,
    context: Context,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = ChalkDim, fontSize = 11.sp, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                color = Chalk,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        IconButton(
            onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText(label, value))
            },
            enabled = enabled,
        ) {
            Icon(
                painterResource(R.drawable.ic_copy),
                contentDescription = stringResource(R.string.action_copy),
                tint = Ochre,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SignInCard(onSignIn: () -> Unit) {
    SectionCard(title = stringResource(R.string.section_account)) {
        Text(
            text = stringResource(R.string.account_body),
            color = ChalkDim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSignIn) { Text(stringResource(R.string.action_connect)) }
    }
}

@Composable
private fun ListCard(
    lists: List<TaskListDto>,
    selectedId: String?,
    onSelect: (TaskListDto) -> Unit,
) {
    SectionCard(title = stringResource(R.string.section_list)) {
        if (lists.isEmpty()) {
            Text(
                stringResource(R.string.list_empty),
                color = ChalkDim,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@SectionCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lists.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { list ->
                        FilterChip(
                            selected = list.id == selectedId,
                            onClick = { onSelect(list) },
                            label = { Text(list.title, maxLines = 1) },
                            colors = chipColors(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    SectionCard(title = stringResource(R.string.section_notifications_blocked)) {
        Text(
            text = stringResource(R.string.notifications_blocked_body),
            color = ChalkDim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text(stringResource(R.string.action_allow)) }
    }
}

@Composable
private fun SurfacesCard(
    settings: ArdoiseSettings,
    onNotificationToggle: (Boolean) -> Unit,
    onWallpaperToggle: (Boolean) -> Unit,
) {
    SectionCard(title = stringResource(R.string.section_surfaces)) {
        ToggleRow(
            title = stringResource(R.string.surface_notification),
            subtitle = stringResource(R.string.surface_notification_detail),
            checked = settings.notificationEnabled,
            onCheckedChange = onNotificationToggle,
        )
        Spacer(Modifier.height(18.dp))
        ToggleRow(
            title = stringResource(R.string.surface_wallpaper),
            subtitle = stringResource(R.string.surface_wallpaper_detail),
            checked = settings.wallpaperEnabled,
            onCheckedChange = onWallpaperToggle,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.surfaces_visibility_hint),
            color = ChalkDim,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Chalk, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = ChalkDim, fontSize = 12.sp, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = Ochre,
                checkedBorderColor = Ochre,
                // Left at defaults, an unchecked switch is a pale slab that
                // pulls more attention than an enabled one.
                uncheckedThumbColor = ChalkDim,
                uncheckedTrackColor = MaterialTheme.colorScheme.background,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
private fun ChipRow(
    options: List<Int>,
    selected: Int,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                colors = chipColors(),
            )
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    labelColor = ChalkDim,
    selectedContainerColor = Ochre,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)

/**
 * The card shell every section on this screen uses. It lives here rather than
 * beside the lock screen preview, which is where it happened to be written.
 */
@Composable
fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp)
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
