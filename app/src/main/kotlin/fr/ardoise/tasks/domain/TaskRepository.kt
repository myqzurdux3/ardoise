package fr.ardoise.tasks.domain

import fr.ardoise.tasks.auth.AuthProvider
import fr.ardoise.tasks.data.ArdoiseSettings
import fr.ardoise.tasks.data.AuthExpiredException
import fr.ardoise.tasks.data.NotFoundException
import fr.ardoise.tasks.data.SettingsStore
import fr.ardoise.tasks.data.SnapshotStore
import fr.ardoise.tasks.data.TaskListDto
import fr.ardoise.tasks.data.TasksApi
import fr.ardoise.tasks.render.SurfaceRenderer
import kotlinx.coroutines.CancellationException

/**
 * The single place that knows the order of operations: get a token, read the
 * list, cache it, redraw both surfaces.
 *
 * Failures never blank the lock screen. The cached snapshot stays on screen,
 * flagged stale, until a later sync succeeds.
 */
class TaskRepository(
    private val api: TasksApi,
    private val auth: AuthProvider,
    private val settingsStore: SettingsStore,
    private val snapshotStore: SnapshotStore,
    private val surfaces: SurfaceRenderer,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun sync(): SyncOutcome {
        val settings = settingsStore.current()
        val listId = settings.listId
        if (listId.isNullOrBlank()) return SyncOutcome.NotConfigured

        val token = auth.silentToken() ?: return degrade(settings, SyncOutcome.NeedsSignIn)
        return fetchAndRender(settings, listId, token)
    }

    /**
     * Completes a task, then refreshes both surfaces.
     *
     * The token and settings are reused rather than calling [sync], which would
     * repeat the Play-services round trip and the DataStore read that just
     * happened -- doubling the latency of the one interaction that has to feel
     * immediate.
     */
    suspend fun completeTask(taskId: String): SyncOutcome {
        val settings = settingsStore.current()
        val listId = settings.listId
        if (listId.isNullOrBlank()) return SyncOutcome.NotConfigured

        val token = auth.silentToken() ?: return degrade(settings, SyncOutcome.NeedsSignIn)

        return try {
            api.completeTask(token, listId, taskId)
            fetchAndRender(settings, listId, token)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (expired: AuthExpiredException) {
            degrade(settings, SyncOutcome.NeedsSignIn)
        } catch (error: Exception) {
            degrade(settings, SyncOutcome.Failed(error))
        }
    }

    private suspend fun fetchAndRender(
        settings: ArdoiseSettings,
        listId: String,
        token: String,
    ): SyncOutcome = try {
        val dtos = api.tasks(token, listId)
        val snapshot = SnapshotMapper.toSnapshot(listId, settings.listTitle, dtos, now())
        snapshotStore.save(snapshot)
        surfaces.refresh(snapshot, settings)
        SyncOutcome.Success(snapshot)
    } catch (cancelled: CancellationException) {
        // CancellationException extends IllegalStateException, so a plain
        // catch(Exception) swallows it: an interrupted sync would be reported
        // as a real failure and then try to write from a dead coroutine.
        throw cancelled
    } catch (missing: NotFoundException) {
        // The selected list was deleted server-side. Retrying forever would
        // keep a vanished list on the lock screen; clearing the choice sends
        // the user back to the picker instead.
        settingsStore.selectList("", "")
        snapshotStore.clear()
        surfaces.refresh(null, settings)
        SyncOutcome.ListGone
    } catch (expired: AuthExpiredException) {
        degrade(settings, SyncOutcome.NeedsSignIn)
    } catch (error: Exception) {
        degrade(settings, SyncOutcome.Failed(error))
    }

    suspend fun availableLists(): Result<List<TaskListDto>> {
        val token = auth.silentToken() ?: return Result.failure(IllegalStateException("Not authorized"))
        return runCatching { api.lists(token) }
    }

    /**
     * Takes a ticked-off task off the lock screen immediately, before Google
     * has been told.
     *
     * Completing used to only enqueue work behind a network constraint, so on a
     * dead connection the tap produced nothing at all: the task stayed, first in
     * the list, and people tapped it again and again. Removing it from the cache
     * and redrawing needs no network, so the disappearing line *is* the
     * acknowledgement.
     *
     * If the call to Google never lands, the next successful sync brings the
     * task back -- the server is still the source of truth, this only borrows
     * against it.
     */
    suspend fun acknowledgeCompletion(taskId: String) {
        val settings = settingsStore.current()
        surfaces.refresh(snapshotStore.removeTask(taskId), settings)
    }

    /**
     * Redraws from cache alone, for boot, for a new day, and for settings changes.
     *
     * A snapshot left over from a previously selected list is discarded rather
     * than drawn: between choosing a new list and its first sync landing, the
     * lock screen would otherwise show the old list's tasks under the new list's
     * name.
     */
    suspend fun refreshSurfacesFromCache() {
        val settings = settingsStore.current()
        val cached = snapshotStore.current()?.takeIf { it.listId == settings.listId }
        surfaces.refresh(cached, settings)
    }

    /** Restores the system lock screen wallpaper when the surface is switched off. */
    suspend fun releaseWallpaper() = surfaces.releaseWallpaper()

    private suspend fun degrade(settings: ArdoiseSettings, outcome: SyncOutcome): SyncOutcome {
        surfaces.refresh(snapshotStore.markStale(), settings)
        return outcome
    }
}

sealed interface SyncOutcome {
    data class Success(val snapshot: RenderSnapshot) : SyncOutcome
    data object NotConfigured : SyncOutcome
    data object NeedsSignIn : SyncOutcome

    /** The chosen list no longer exists; the selection has been cleared. */
    data object ListGone : SyncOutcome
    data class Failed(val error: Throwable) : SyncOutcome

    val isSuccess: Boolean get() = this is Success

    /** Worth a WorkManager retry; missing consent or a deleted list is not. */
    val isRetryable: Boolean get() = this is Failed
}
