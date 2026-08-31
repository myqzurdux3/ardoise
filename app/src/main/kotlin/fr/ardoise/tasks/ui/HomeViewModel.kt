package fr.ardoise.tasks.ui

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.ardoise.tasks.ArdoiseGraph
import fr.ardoise.tasks.R
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val settings: ArdoiseSettings = ArdoiseSettings(),
    val snapshot: RenderSnapshot? = null,
    val lists: List<TaskListDto> = emptyList(),
    val authorized: Boolean = false,
    val busy: Boolean = false,
    val notificationsAllowed: Boolean = true,
    /** Google has no OAuth client for this package and signing certificate. */
    val setupRequired: Boolean = false,
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
                graph.settingsStore.setupRequired,
                transient,
            ) { settings, snapshot, setupRequired, extra ->
                HomeUiState(
                    settings = settings,
                    snapshot = snapshot,
                    lists = extra.lists,
                    authorized = extra.authorized,
                    busy = extra.busy,
                    // Read in refreshSystemState, not here: this transform runs
                    // on every emission of four flows, and the check is a
                    // synchronous binder call into system_server.
                    notificationsAllowed = extra.notificationsAllowed,
                    setupRequired = setupRequired,
                    message = extra.message,
                )
            }.collect { _state.value = it }
        }
        refreshSystemState()
        refreshAuthorization()
    }

    /** Silent probe on launch: if consent already exists, the UI skips sign-in. */
    private fun refreshAuthorization() = viewModelScope.launch {
        when (val outcome = graph.auth.authorize()) {
            is AuthProvider.Outcome.Granted -> onAuthorized()

            is AuthProvider.Outcome.Failed ->
                if (outcome.notRegistered) graph.settingsStore.setSetupRequired(true)

            is AuthProvider.Outcome.ConsentRequired -> Unit
        }
    }

    fun signIn() = viewModelScope.launch {
        transient.update { it.copy(busy = true, message = null) }
        when (val outcome = graph.auth.authorize()) {
            is AuthProvider.Outcome.Granted -> onAuthorized()

            is AuthProvider.Outcome.ConsentRequired -> {
                transient.update { it.copy(busy = false) }
                _consentRequest.value = outcome.pendingIntent
            }

            is AuthProvider.Outcome.Failed -> {
                if (outcome.notRegistered) graph.settingsStore.setSetupRequired(true)
                val message = when {
                    outcome.notRegistered -> str(R.string.msg_not_registered)
                    outcome.detail == AuthProvider.NO_TOKEN ->
                        str(R.string.msg_sign_in_failed, str(R.string.msg_no_access_token))

                    else -> str(R.string.msg_sign_in_failed, outcome.detail)
                }
                transient.update { it.copy(busy = false, message = message) }
            }
        }
    }

    /**
     * Handles the return from the consent screen.
     *
     * Reading the token straight out of the returned Intent is the fast path,
     * but it is not the only one that means success: Google Play services can
     * record the grant and still hand back an Intent this call cannot parse.
     * So a failure here is re-checked with a silent authorization before it is
     * reported, and a genuine failure names its cause instead of blaming the
     * user for a refusal they never made.
     */
    fun onConsentResult(resultCode: Int, data: Intent?) {
        _consentRequest.value = null
        viewModelScope.launch {
            val direct = data?.let { graph.auth.tokenFromConsent(it) }
            if (direct?.isSuccess == true || graph.auth.silentToken() != null) {
                onAuthorized()
                return@launch
            }

            val error = direct?.exceptionOrNull()
            val unregistered = error != null && AuthProvider.isUnregistered(error)
            if (unregistered) graph.settingsStore.setSetupRequired(true)

            val message = when {
                unregistered -> str(R.string.msg_not_registered)
                resultCode == Activity.RESULT_CANCELED && data == null ->
                    str(R.string.msg_consent_cancelled)

                error != null -> str(R.string.msg_consent_refused, error.message.orEmpty())
                else -> str(R.string.msg_consent_no_data)
            }
            transient.update { it.copy(message = message) }
        }
    }

    fun consentLaunchFailed() {
        _consentRequest.value = null
        transient.update { it.copy(message = str(R.string.msg_consent_launch_failed)) }
    }

    private suspend fun onAuthorized() {
        graph.settingsStore.setSetupRequired(false)
        transient.update { it.copy(authorized = true, busy = false) }
        loadLists()
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
                transient.update {
                    it.copy(busy = false, message = str(R.string.msg_lists_failed, error.message.orEmpty()))
                }
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
        if (value) {
            graph.invalidateWallpaper()
            redrawFromCache()
            transient.update { it.copy(message = str(R.string.msg_wallpaper_enabled)) }
        } else {
            // Switching the surface off used to leave Ardoise's last bitmap on
            // the lock screen for good.
            graph.repository.releaseWallpaper()
            transient.update { it.copy(message = str(R.string.msg_wallpaper_released)) }
        }
    }

    fun refreshNow() = viewModelScope.launch {
        transient.update { it.copy(busy = true, message = null) }
        val outcome = graph.repository.sync()
        transient.update { it.copy(busy = false, message = outcome.toMessage()) }
    }

    fun dismissMessage() = transient.update { it.copy(message = null) }

    /**
     * Re-reads the state Android owns: whether the notification can actually
     * appear. The user can flip it in system settings, or block the channel
     * from the notification itself, while the app sits in the background.
     */
    fun refreshSystemState() {
        val allowed = !graph.notifications.isBlocked()
        transient.update { it.copy(notificationsAllowed = allowed) }
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

    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private fun SyncOutcome.toMessage(): String? = when (this) {
        is SyncOutcome.Success -> null
        SyncOutcome.NotConfigured -> str(R.string.msg_not_configured)
        SyncOutcome.NeedsSignIn -> str(R.string.msg_sign_in_needed)
        SyncOutcome.ListGone -> str(R.string.msg_list_gone)
        is SyncOutcome.Failed -> str(R.string.msg_sync_failed, error.message.orEmpty())
    }

    private data class TransientState(
        val lists: List<TaskListDto> = emptyList(),
        val authorized: Boolean = false,
        val busy: Boolean = false,
        val notificationsAllowed: Boolean = true,
        val message: String? = null,
    )
}
