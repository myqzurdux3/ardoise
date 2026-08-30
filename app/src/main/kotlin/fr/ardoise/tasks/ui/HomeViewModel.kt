package fr.ardoise.tasks.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.ardoise.tasks.ArdoiseGraph
import fr.ardoise.tasks.auth.AuthProvider
import fr.ardoise.tasks.data.ArdoiseSettings
import fr.ardoise.tasks.data.TaskListDto
import fr.ardoise.tasks.domain.RenderSnapshot
import fr.ardoise.tasks.domain.SyncOutcome
import fr.ardoise.tasks.work.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HomeUiState(
    val settings: ArdoiseSettings = ArdoiseSettings(),
    val snapshot: RenderSnapshot? = null,
    val lists: List<TaskListDto> = emptyList(),
    val authorized: Boolean = false,
    val busy: Boolean = false,
    val notificationsAllowed: Boolean = true,
    val message: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val graph = ArdoiseGraph.from(application)

    private val transient = MutableStateFlow(TransientState())
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _consentRequest = MutableStateFlow<PendingIntent?>(null)
    val consentRequest: StateFlow<PendingIntent?> = _consentRequest.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                graph.settingsStore.settings,
                graph.snapshotStore.snapshot,
                transient,
            ) { settings, snapshot, extra ->
                HomeUiState(
                    settings = settings,
                    snapshot = snapshot,
                    lists = extra.lists,
                    authorized = extra.authorized,
                    busy = extra.busy,
                    notificationsAllowed = notificationsAllowed(),
                    message = extra.message,
                )
            }.collect { _state.value = it }
        }
        refreshAuthorization()
    }

    /** Silent probe on launch: if consent already exists, the UI skips sign-in. */
    private fun refreshAuthorization() = viewModelScope.launch {
        val token = graph.auth.silentToken()
        transient.update { it.copy(authorized = token != null) }
        if (token != null) loadLists()
    }

    fun signIn() = viewModelScope.launch {
        transient.update { it.copy(busy = true, message = null) }
        when (val outcome = graph.auth.authorize()) {
            is AuthProvider.Outcome.Granted -> {
                transient.update { it.copy(authorized = true, busy = false) }
                loadLists()
            }

            is AuthProvider.Outcome.ConsentRequired -> {
                transient.update { it.copy(busy = false) }
                _consentRequest.value = outcome.pendingIntent
            }

            is AuthProvider.Outcome.Failed -> transient.update {
                it.copy(busy = false, message = "Connexion impossible : ${outcome.error.message}")
            }
        }
    }

    fun onConsentResult(data: Intent?) {
        _consentRequest.value = null
        val token = data?.let { graph.auth.tokenFromConsent(it) }
        if (token == null) {
            transient.update { it.copy(message = "Autorisation refusée.") }
            return
        }
        transient.update { it.copy(authorized = true) }
        viewModelScope.launch { loadLists() }
    }

    fun consentLaunchFailed() {
        _consentRequest.value = null
        transient.update { it.copy(message = "Impossible d'ouvrir l'écran de consentement.") }
    }

    private suspend fun loadLists() {
        transient.update { it.copy(busy = true) }
        graph.repository.availableLists()
            .onSuccess { lists ->
                transient.update { it.copy(lists = lists, busy = false, authorized = true) }
                // A single list means there is nothing to choose: pick it.
                val settings = graph.settingsStore.current()
                if (!settings.isConfigured && lists.size == 1) {
                    selectList(lists.first())
                }
            }
            .onFailure { error ->
                transient.update { it.copy(busy = false, message = "Lecture des listes impossible : ${error.message}") }
            }
    }

    fun selectList(list: TaskListDto) = viewModelScope.launch {
        graph.settingsStore.selectList(list.id, list.title)
        graph.invalidateWallpaper()
        rescheduleAndSync()
    }

    fun setMaxTasks(value: Int) = viewModelScope.launch {
        graph.settingsStore.setMaxTasks(value)
        redrawFromCache()
    }

    fun setSyncInterval(minutes: Int) = viewModelScope.launch {
        graph.settingsStore.setSyncIntervalMinutes(minutes)
        SyncScheduler.schedulePeriodic(getApplication(), minutes)
    }

    fun setNotificationEnabled(value: Boolean) = viewModelScope.launch {
        graph.settingsStore.setNotificationEnabled(value)
        redrawFromCache()
    }

    fun setWallpaperEnabled(value: Boolean) = viewModelScope.launch {
        graph.settingsStore.setWallpaperEnabled(value)
        graph.invalidateWallpaper()
        redrawFromCache()
        if (value) {
            transient.update {
                it.copy(message = "Le fond d'écran de verrouillage est désormais géré par Ardoise.")
            }
        }
    }

    fun refreshNow() = viewModelScope.launch {
        transient.update { it.copy(busy = true, message = null) }
        val outcome = graph.repository.sync()
        transient.update { it.copy(busy = false, message = outcome.toMessage()) }
    }

    fun completeTask(taskId: String) = viewModelScope.launch {
        transient.update { it.copy(busy = true) }
        val outcome = graph.repository.completeTask(taskId)
        transient.update { it.copy(busy = false, message = outcome.toMessage()) }
    }

    fun dismissMessage() = transient.update { it.copy(message = null) }

    /**
     * Re-reads the state Android owns: the notification permission, which the
     * user can flip in system settings while the app sits in the background.
     *
     * [MutableStateFlow] conflates equal values, so re-emitting a copy of the
     * same state would be swallowed; the counter forces the emission.
     */
    fun refreshSystemState() {
        transient.update { it.copy(permissionEpoch = it.permissionEpoch + 1) }
        viewModelScope.launch { redrawFromCache() }
    }

    private suspend fun rescheduleAndSync() {
        val settings = graph.settingsStore.current()
        SyncScheduler.schedulePeriodic(getApplication(), settings.syncIntervalMinutes)
        transient.update { it.copy(busy = true) }
        val outcome = graph.repository.sync()
        transient.update { it.copy(busy = false, message = outcome.toMessage()) }
    }

    private suspend fun redrawFromCache() {
        graph.repository.refreshSurfacesFromCache()
    }

    private fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(getApplication()).areNotificationsEnabled()

    private fun SyncOutcome.toMessage(): String? = when (this) {
        is SyncOutcome.Success -> null
        SyncOutcome.NotConfigured -> "Choisissez d'abord une liste."
        SyncOutcome.NeedsSignIn -> "Reconnexion nécessaire."
        is SyncOutcome.Failed -> "Synchronisation impossible : ${error.message}"
    }

    private data class TransientState(
        val lists: List<TaskListDto> = emptyList(),
        val authorized: Boolean = false,
        val busy: Boolean = false,
        val message: String? = null,
        /** Bumped to force a re-read of state Android owns, not this class. */
        val permissionEpoch: Int = 0,
    )

    private fun MutableStateFlow<TransientState>.update(block: (TransientState) -> TransientState) {
        value = block(value)
    }
}
