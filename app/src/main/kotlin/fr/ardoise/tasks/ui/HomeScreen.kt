package fr.ardoise.tasks.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

            SectionCard(title = "Lignes affichées") {
                ChipRow(
                    options = ArdoiseSettings.MAX_TASKS_CHOICES,
                    selected = state.settings.maxTasks,
                    label = { "$it" },
                    onSelect = onMaxTasks,
                )
            }

            SectionCard(title = "Fréquence de synchronisation") {
                ChipRow(
                    options = ArdoiseSettings.SYNC_MINUTES_CHOICES,
                    selected = state.settings.syncIntervalMinutes,
                    label = { "$it min" },
                    onSelect = onSyncInterval,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "L'API Google Tasks n'envoie aucune notification de changement. " +
                        "Ardoise interroge donc le serveur à intervalle régulier.",
                    color = ChalkDim,
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onRefresh, enabled = !state.busy) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Actualiser maintenant")
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
                text = "ARDOISE",
                color = Chalk,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 6.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Vos tâches, sur l'écran de verrouillage",
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

@Composable
private fun SignInCard(onSignIn: () -> Unit) {
    SectionCard(title = "Compte Google") {
        Text(
            text = "Ardoise lit vos listes Google Tasks. Aucun jeton n'est conservé sur " +
                "l'appareil et aucune donnée ne quitte votre téléphone.",
            color = ChalkDim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSignIn) { Text("Connecter Google Tasks") }
    }
}

@Composable
private fun ListCard(
    lists: List<TaskListDto>,
    selectedId: String?,
    onSelect: (TaskListDto) -> Unit,
) {
    SectionCard(title = "Liste affichée") {
        if (lists.isEmpty()) {
            Text("Aucune liste trouvée.", color = ChalkDim, style = MaterialTheme.typography.bodyMedium)
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
    SectionCard(title = "Notifications bloquées") {
        Text(
            text = "Sans autorisation de notification, la surface principale d'Ardoise ne " +
                "peut pas s'afficher.",
            color = ChalkDim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Autoriser") }
    }
}

@Composable
private fun SurfacesCard(
    settings: ArdoiseSettings,
    onNotificationToggle: (Boolean) -> Unit,
    onWallpaperToggle: (Boolean) -> Unit,
) {
    SectionCard(title = "Surfaces") {
        ToggleRow(
            title = "Notification permanente",
            subtitle = "Six à huit lignes, avec un bouton pour cocher sans déverrouiller.",
            checked = settings.notificationEnabled,
            onCheckedChange = onNotificationToggle,
        )
        Spacer(Modifier.height(18.dp))
        ToggleRow(
            title = "Fond d'écran de verrouillage",
            subtitle = "Remplace votre fond d'écran de verrouillage actuel.",
            checked = settings.wallpaperEnabled,
            onCheckedChange = onWallpaperToggle,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Pensez à régler Notifications sur l'écran de verrouillage sur " +
                "« Afficher tout le contenu », sinon le système masque le texte.",
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
            ),
        )
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
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